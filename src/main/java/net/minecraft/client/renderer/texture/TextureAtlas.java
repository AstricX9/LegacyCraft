package net.minecraft.client.renderer.texture;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;

/**
 * The block texture atlas. Each sprite keeps its own size (the 32x32 flowing
 * water and lava sprites stay 32x32, as in vanilla's TextureMap, which the
 * fluid renderer relies on: it reads only half of them per block). A sprite
 * with a {@code .png.mcmeta} "animation" section is a vertical strip of
 * square frames; the atlas steps through them once per client tick
 * (TextureAtlasSprite.updateAnimation, VERIFIED_ASSET for the frame lists,
 * HYPOTHESIS for the timing rules: "frametime" ticks per frame, default 1;
 * "frames" entries are indices or {"index", "time"}; "interpolate" blends
 * towards the next frame on the ticks in between).
 *
 * <p>Mipmaps (Video Settings "Mipmap Levels", 0-4) follow TextureMap: the
 * level is lowered until every sprite halves evenly down to one pixel, each
 * sprite gets its own mip chain ({@link Mipmaps}), slots are aligned to
 * 2^levels so no level samples a neighbour, and animated sprites re-upload
 * every level of the frame they switch to. The atlas grows past 512 pixels
 * wide when a high-resolution resource pack needs the room.
 */
public final class TextureAtlas {
    public record Tile(float u0, float v0, float u1, float v1) {}

    /** One frame of an animation: which strip frame to show and for how many ticks. */
    public record Frame(int index, int time) {}

    /** An animated sprite's schedule; {@link #tick} says which frame to show next. */
    public static final class Animation {
        final List<Frame> frames;
        final boolean interpolate;
        int frame, ticks;

        public Animation(List<Frame> frames, boolean interpolate) {
            this.frames = frames;
            this.interpolate = interpolate;
        }

        /** The strip frame on screen now. */
        public int current() { return frames.get(frame).index(); }

        /**
         * Advances one tick; true when the frame shown changes (a frame
         * listed twice in a row is not re-uploaded).
         */
        public boolean tick() {
            ticks++;
            if (ticks < frames.get(frame).time()) return false;
            int before = current();
            frame = (frame + 1) % frames.size();
            ticks = 0;
            return current() != before;
        }

        /** How far towards the next frame an interpolated animation has blended (0 on a frame change). */
        public double blend() { return (double) ticks / frames.get(frame).time(); }
        public int next() { return frames.get((frame + 1) % frames.size()).index(); }

        /** Parses a {@code .mcmeta} file's "animation" section for a strip of {@code frameCount} frames, or null. */
        public static Animation parse(JsonObject meta, int frameCount) {
            if (meta == null || !meta.has("animation")) return null;
            JsonObject animation = meta.getAsJsonObject("animation");
            int frameTime = animation.has("frametime") ? animation.get("frametime").getAsInt() : 1;
            boolean interpolate = animation.has("interpolate") && animation.get("interpolate").getAsBoolean();
            List<Frame> frames = new ArrayList<>();
            if (animation.has("frames")) {
                JsonArray list = animation.getAsJsonArray("frames");
                for (JsonElement entry : list) {
                    if (entry.isJsonObject()) {
                        JsonObject object = entry.getAsJsonObject();
                        frames.add(new Frame(object.get("index").getAsInt(),
                            object.has("time") ? object.get("time").getAsInt() : frameTime));
                    } else {
                        frames.add(new Frame(entry.getAsInt(), frameTime));
                    }
                }
            } else {
                for (int i = 0; i < frameCount; i++) frames.add(new Frame(i, frameTime));
            }
            frames.removeIf(frame -> frame.index() < 0 || frame.index() >= frameCount);
            return frames.isEmpty() ? null : new Animation(frames, interpolate);
        }
    }

    /** Where a sprite sits in the atlas, in pixels. */
    public record Slot(int x, int y, int width, int height) {}

    private record Animated(Slot slot, BufferedImage strip, Animation animation, Map<Integer, int[][]> frameMips) {}

    private static final int ATLAS_WIDTH = 512;
    private int requestedLevels = 4;
    private int levels;

    /** Replaced whole on (re)load: chunk meshes read it from worker threads. */
    private volatile Map<ResourceLocation, Tile> tiles = new HashMap<>();
    private final List<Animated> animated = new ArrayList<>();
    private final IResourceManager resources;
    private int textureId;
    private int widgetsTextureId;
    private int iconsTextureId;

    public TextureAtlas(IResourceManager resources) {
        this.resources = resources;
    }

    /**
     * Shelf packing in listing order: sprites fill a row left to right, a row
     * is as tall as its tallest sprite. Returns the slots and, as the last
     * element, the atlas size (x = width, y = height).
     */
    public static List<Slot> pack(List<int[]> sizes) { return pack(sizes, ATLAS_WIDTH); }

    /** {@link #pack(List)} into an atlas {@code atlasWidth} pixels wide. */
    public static List<Slot> pack(List<int[]> sizes, int atlasWidth) {
        List<Slot> slots = new ArrayList<>();
        int x = 0, y = 0, rowHeight = 0;
        for (int[] size : sizes) {
            if (x + size[0] > atlasWidth) {
                x = 0;
                y += rowHeight;
                rowHeight = 0;
            }
            slots.add(new Slot(x, y, size[0], size[1]));
            x += size[0];
            rowHeight = Math.max(rowHeight, size[1]);
        }
        int height = Integer.highestOneBit(Math.max(1, y + rowHeight));
        if (height < y + rowHeight) height <<= 1;
        slots.add(new Slot(atlasWidth, height, 0, 0));
        return slots;
    }

    /** Sets the wanted mipmap level (0 = off); takes effect on the next {@link #load} or {@link #reload}. */
    public void setMipmapLevels(int requested) { this.requestedLevels = Math.max(0, Math.min(4, requested)); }

    /** The mipmap level actually in use (may be lower than requested, see the class comment). */
    public int mipmapLevels() { return levels; }

    /** Throws the textures away and builds the atlas again from the current resource packs. */
    public void reload() {
        destroy();
        textureId = widgetsTextureId = iconsTextureId = 0;
        animated.clear();
        load();
    }

    public void load() {
        List<ResourceLocation> ordered;
        try {
            ordered = new ArrayList<>(resources.list(
                new ResourceLocation("minecraft:textures/blocks"), ".png"));
        } catch (IOException error) {
            throw new IllegalStateException("Could not enumerate block textures", error);
        }
        if (ordered.isEmpty()) throw new IllegalStateException("The 1.8.9 block texture pack is empty");
        List<BufferedImage> images = new ArrayList<>();
        List<Animation> animations = new ArrayList<>();
        List<int[]> frameSizes = new ArrayList<>();
        int levels = requestedLevels;
        for (ResourceLocation name : ordered) {
            BufferedImage image = read(name);
            int width = image.getWidth(), height = image.getHeight();
            Animation animation = null;
            if (height > width && height % width == 0) animation = Animation.parse(readMeta(name), height / width);
            images.add(image);
            animations.add(animation);
            int frameHeight = animation != null ? width : height;
            frameSizes.add(new int[]{width, frameHeight});
            int limit = Mipmaps.maxLevels(width, frameHeight, levels);
            if (limit < levels) {
                System.err.println("Texture " + name + " with size " + width + "x" + frameHeight
                    + " limits mip level from " + levels + " to " + limit);
                levels = limit;
            }
        }
        this.levels = levels;
        int align = 1 << levels;
        List<int[]> sizes = new ArrayList<>();
        long area = 0;
        for (int[] frame : frameSizes) {
            int w = (frame[0] + align - 1) / align * align, h = (frame[1] + align - 1) / align * align;
            sizes.add(new int[]{w, h});
            area += (long) w * h;
        }
        int maxSize = Math.max(ATLAS_WIDTH, glGetInteger(GL_MAX_TEXTURE_SIZE));
        int atlasWidth = ATLAS_WIDTH;
        while (atlasWidth < maxSize && (long) atlasWidth * atlasWidth < area) atlasWidth <<= 1;
        List<Slot> slots = pack(sizes, atlasWidth);
        while (atlasWidth < maxSize && slots.get(slots.size() - 1).y() > atlasWidth) slots = pack(sizes, atlasWidth <<= 1);
        Slot size = slots.get(slots.size() - 1);
        int width = size.x(), height = size.y();
        if (height > maxSize) System.err.println("Block atlas " + width + "x" + height + " exceeds the GPU's " + maxSize + " limit");

        Map<ResourceLocation, Tile> tiles = new HashMap<>();
        int[][] atlasLevels = new int[levels + 1][];
        for (int level = 0; level <= levels; level++) atlasLevels[level] = new int[(width >> level) * (height >> level)];
        for (int i = 0; i < ordered.size(); i++) {
            Slot packed = slots.get(i);
            int[] frame = frameSizes.get(i);
            Slot slot = new Slot(packed.x(), packed.y(), frame[0], frame[1]);
            BufferedImage image = images.get(i);
            Animation animation = animations.get(i);
            int[][] mips = Mipmaps.generate(framePixels(image, animation != null ? animation.current() : 0, slot),
                slot.width(), levels);
            for (int level = 0; level <= levels; level++) {
                int w = slot.width() >> level, h = slot.height() >> level, atlasW = width >> level;
                int ox = slot.x() >> level, oy = slot.y() >> level;
                for (int py = 0; py < h; py++)
                    System.arraycopy(mips[level], py * w, atlasLevels[level], (oy + py) * atlasW + ox, w);
            }
            float inset = 0.02f;
            tiles.put(ordered.get(i), new Tile(
                (slot.x() + inset) / width, (slot.y() + inset) / height,
                (slot.x() + slot.width() - inset) / width, (slot.y() + slot.height() - inset) / height));
            if (animation != null) animated.add(new Animated(slot, image, animation, new HashMap<>()));
        }

        textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);
        Mipmaps.setFiltering(levels);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP);
        Mipmaps.upload(atlasLevels, width, height);
        this.tiles = tiles;
        widgetsTextureId = loadGuiTexture(new ResourceLocation("minecraft:textures/gui/widgets.png"));
        iconsTextureId = loadGuiTexture(new ResourceLocation("minecraft:textures/gui/icons.png"));
    }

    /** One client tick of every animated sprite (vanilla TextureMap.updateAnimations); needs the GL context. */
    public void tickAnimations() {
        if (textureId == 0 || animated.isEmpty()) return;
        boolean bound = false;
        for (Animated sprite : animated) {
            Animation animation = sprite.animation();
            boolean changed = animation.tick();
            if (!changed && !(animation.interpolate && animation.ticks > 0)) continue;
            if (!bound) {
                glBindTexture(GL_TEXTURE_2D, textureId);
                bound = true;
            }
            upload(sprite, animation);
        }
    }

    private void upload(Animated sprite, Animation animation) {
        Slot slot = sprite.slot();
        int w = slot.width();
        double blend = animation.interpolate ? animation.blend() : 0;
        int[][] mips;
        if (blend > 0) {
            int[] pixels = framePixels(sprite.strip(), animation.current(), slot);
            int[] next = framePixels(sprite.strip(), animation.next(), slot);
            for (int i = 0; i < pixels.length; i++) pixels[i] = mix(pixels[i], next[i], blend);
            mips = Mipmaps.generate(pixels, w, levels);
        } else {
            mips = sprite.frameMips().computeIfAbsent(animation.current(),
                frame -> Mipmaps.generate(framePixels(sprite.strip(), frame, slot), w, levels));
        }
        Mipmaps.uploadSub(mips, slot.x(), slot.y(), w, slot.height());
    }

    /** One frame of a sprite strip as ARGB pixels, row by row. */
    private static int[] framePixels(BufferedImage strip, int frame, Slot slot) {
        int w = slot.width(), h = slot.height();
        int[] pixels = new int[w * h];
        strip.getRGB(0, frame * h, w, h, pixels, 0, w);
        return pixels;
    }

    /** Colour channels blended towards {@code next}; alpha stays the current frame's (TextureAtlasSprite.interpolateColor). */
    static int mix(int argb, int next, double blend) {
        int result = argb & 0xff000000;
        for (int shift = 0; shift <= 16; shift += 8) {
            int a = argb >> shift & 255, b = next >> shift & 255;
            result |= (int) ((1 - blend) * a + blend * b) << shift;
        }
        return result;
    }

    private static void put(ByteBuffer pixels, int argb) {
        pixels.put((byte) ((argb >> 16) & 0xff));
        pixels.put((byte) ((argb >> 8) & 0xff));
        pixels.put((byte) (argb & 0xff));
        pixels.put((byte) ((argb >> 24) & 0xff));
    }

    private JsonObject readMeta(ResourceLocation texture) {
        try (InputStream stream = resources.openMetadata(texture)) {
            if (stream == null) return null;
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException | RuntimeException error) {
            return null;
        }
    }

    private int loadGuiTexture(ResourceLocation location) {
        BufferedImage image = read(location);
        int width = image.getWidth(), height = image.getHeight();
        ByteBuffer pixels = BufferUtils.createByteBuffer(width * height * 4);
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++) put(pixels, image.getRGB(x, y));
        pixels.flip();
        int id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, id);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        return id;
    }

    private BufferedImage read(ResourceLocation location) {
        try (InputStream stream = resources.open(location)) {
            return ImageIO.read(stream);
        } catch (IOException error) {
            throw new IllegalStateException("Could not read texture " + location, error);
        }
    }

    public void bind() { glBindTexture(GL_TEXTURE_2D, textureId); }
    public void bindWidgets() { glBindTexture(GL_TEXTURE_2D, widgetsTextureId); }
    public void bindIcons() { glBindTexture(GL_TEXTURE_2D, iconsTextureId); }
    public Tile tile(ResourceLocation name) {
        Tile tile = tiles.get(name);
        if (tile == null) tile = tiles.get(new ResourceLocation("minecraft:textures/blocks/stone.png"));
        return tile;
    }
    public void destroy() {
        if (textureId != 0) glDeleteTextures(textureId);
        if (widgetsTextureId != 0) glDeleteTextures(widgetsTextureId);
        if (iconsTextureId != 0) glDeleteTextures(iconsTextureId);
    }
}
