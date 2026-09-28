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

    private record Animated(Slot slot, BufferedImage strip, Animation animation) {}

    private static final int ATLAS_WIDTH = 512;

    private final Map<ResourceLocation, Tile> tiles = new HashMap<>();
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
    public static List<Slot> pack(List<int[]> sizes) {
        List<Slot> slots = new ArrayList<>();
        int x = 0, y = 0, rowHeight = 0;
        for (int[] size : sizes) {
            if (x + size[0] > ATLAS_WIDTH) {
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
        slots.add(new Slot(ATLAS_WIDTH, height, 0, 0));
        return slots;
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
        List<int[]> sizes = new ArrayList<>();
        for (ResourceLocation name : ordered) {
            BufferedImage image = read(name);
            int width = image.getWidth(), height = image.getHeight();
            Animation animation = null;
            if (height > width && height % width == 0) animation = Animation.parse(readMeta(name), height / width);
            images.add(image);
            animations.add(animation);
            sizes.add(new int[]{width, animation != null ? width : height});
        }
        List<Slot> slots = pack(sizes);
        Slot size = slots.get(slots.size() - 1);
        int width = size.x(), height = size.y();
        BufferedImage atlas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < ordered.size(); i++) {
            Slot slot = slots.get(i);
            BufferedImage image = images.get(i);
            Animation animation = animations.get(i);
            int frameY = animation != null ? animation.current() * slot.height() : 0;
            for (int py = 0; py < slot.height(); py++)
                for (int px = 0; px < slot.width(); px++)
                    atlas.setRGB(slot.x() + px, slot.y() + py, image.getRGB(px, frameY + py));
            float inset = 0.02f;
            tiles.put(ordered.get(i), new Tile(
                (slot.x() + inset) / width, (slot.y() + inset) / height,
                (slot.x() + slot.width() - inset) / width, (slot.y() + slot.height() - inset) / height));
            if (animation != null) animated.add(new Animated(slot, image, animation));
        }

        ByteBuffer pixels = BufferUtils.createByteBuffer(width * height * 4);
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++) put(pixels, atlas.getRGB(x, y));
        pixels.flip();

        textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
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

    private static void upload(Animated sprite, Animation animation) {
        Slot slot = sprite.slot();
        int w = slot.width(), h = slot.height();
        int from = animation.current() * h, to = animation.next() * h;
        double blend = animation.interpolate ? animation.blend() : 0;
        ByteBuffer pixels = BufferUtils.createByteBuffer(w * h * 4);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int argb = sprite.strip().getRGB(x, from + y);
                if (blend > 0) argb = mix(argb, sprite.strip().getRGB(x, to + y), blend);
                put(pixels, argb);
            }
        pixels.flip();
        glTexSubImage2D(GL_TEXTURE_2D, 0, slot.x(), slot.y(), w, h, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
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
        ResourceLocation meta = new ResourceLocation(texture.toString() + ".mcmeta");
        if (!resources.exists(meta)) return null;
        try (InputStream stream = resources.open(meta)) {
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
