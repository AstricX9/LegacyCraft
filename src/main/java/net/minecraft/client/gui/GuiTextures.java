package net.minecraft.client.gui;

import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;

/**
 * Whole-image GUI textures ({@code textures/gui/widgets.png},
 * {@code options_background.png}, the title panorama...) loaded on demand
 * from the resource pack and kept until {@link #destroy()}. Sampling is
 * nearest-neighbour and repeating, which is what 1.8.9 uses for these sheets
 * (the tiled dirt background depends on the repeat).
 *
 * <p>Item and block sprites bound one by one through here (held and dropped
 * items) get the same mipmaps as the block atlas, since in vanilla they share
 * that atlas; GUI sheets never do.
 */
public final class GuiTextures {
    public static final ResourceLocation WIDGETS = new ResourceLocation("minecraft:textures/gui/widgets.png");
    public static final ResourceLocation OPTIONS_BACKGROUND = new ResourceLocation("minecraft:textures/gui/options_background.png");
    public static final ResourceLocation ICONS = new ResourceLocation("minecraft:textures/gui/icons.png");

    private final IResourceManager resources;
    private final Map<ResourceLocation, Integer> textures = new HashMap<>();
    private final Map<ResourceLocation, int[]> sizes = new HashMap<>();
    private int missing;
    private int spriteMipmapLevels;

    public GuiTextures(IResourceManager resources) { this.resources = resources; }

    public void bind(ResourceLocation location) {
        Integer id = textures.get(location);
        if (id == null) {
            id = load(location);
            textures.put(location, id);
        }
        glBindTexture(GL_TEXTURE_2D, id);
    }

    /** {width, height} of a loaded texture, or 256x256 before it has been bound. */
    public int[] size(ResourceLocation location) { return sizes.getOrDefault(location, new int[]{256, 256}); }

    public boolean exists(ResourceLocation location) { return resources.exists(location); }

    /** Mipmap level for item/block sprites; call {@link #reload()} afterwards for it to apply to loaded ones. */
    public void setSpriteMipmapLevels(int levels) { this.spriteMipmapLevels = Math.max(0, levels); }

    /** Drops every cached texture so the next bind reloads it (F3+T). */
    public void reload() {
        for (int id : textures.values()) if (id != missing) glDeleteTextures(id);
        textures.clear();
        sizes.clear();
    }

    private int load(ResourceLocation location) {
        BufferedImage image;
        try (InputStream stream = resources.open(location)) {
            image = ImageIO.read(stream);
        } catch (IOException error) {
            image = null;
        }
        if (image == null) {
            System.err.println("Missing GUI texture " + location);
            return missingTexture();
        }
        sizes.put(location, new int[]{image.getWidth(), image.getHeight()});
        String path = location.path();
        boolean sprite = path.startsWith("textures/items/") || path.startsWith("textures/blocks/");
        int levels = sprite ? net.minecraft.client.renderer.texture.Mipmaps.maxLevels(image.getWidth(), image.getHeight(),
            spriteMipmapLevels) : 0;
        if (levels <= 0) return upload(image, !clamped(location));
        int width = image.getWidth(), height = image.getHeight();
        int[] pixels = new int[width * height];
        image.getRGB(0, 0, width, height, pixels, 0, width);
        int id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, id);
        net.minecraft.client.renderer.texture.Mipmaps.setFiltering(levels);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        net.minecraft.client.renderer.texture.Mipmaps.upload(
            net.minecraft.client.renderer.texture.Mipmaps.generate(pixels, width, levels), width, height);
        return id;
    }

    /**
     * Whether a texture is clamped instead of repeating (SimpleTexture with
     * TextureMetadataSection: "texture": {"clamp": true} in its .mcmeta).
     * The entity shadow is always clamped, so the circle is drawn once
     * across the blocks under it instead of tiling on each one.
     */
    private boolean clamped(ResourceLocation location) {
        if (location.path().equals("textures/misc/shadow.png")) return true;
        ResourceLocation meta = new ResourceLocation(location.namespace(), location.path() + ".mcmeta");
        try {
            if (!resources.exists(meta)) return false;
            try (var reader = new java.io.InputStreamReader(resources.open(meta), java.nio.charset.StandardCharsets.UTF_8)) {
                var root = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                var texture = root.has("texture") ? root.getAsJsonObject("texture") : null;
                return texture != null && texture.has("clamp") && texture.get("clamp").getAsBoolean();
            }
        } catch (IOException | RuntimeException error) {
            return false;
        }
    }

    /** Uploads an image as a new nearest-filtered texture. */
    public static int upload(BufferedImage image, boolean repeat) {
        int width = image.getWidth(), height = image.getHeight();
        ByteBuffer pixels = BufferUtils.createByteBuffer(width * height * 4);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = image.getRGB(x, y);
                pixels.put((byte) (argb >> 16)).put((byte) (argb >> 8)).put((byte) argb).put((byte) (argb >>> 24));
            }
        }
        pixels.flip();
        int id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, id);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, repeat ? GL_REPEAT : GL_CLAMP);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, repeat ? GL_REPEAT : GL_CLAMP);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        return id;
    }

    /** The magenta/black checkerboard vanilla shows for a missing texture. */
    private int missingTexture() {
        if (missing != 0) return missing;
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++)
            for (int x = 0; x < 16; x++)
                image.setRGB(x, y, (x < 8) == (y < 8) ? 0xFFF800F8 : 0xFF000000);
        missing = upload(image, true);
        return missing;
    }

    public void destroy() {
        reload();
        if (missing != 0) glDeleteTextures(missing);
        missing = 0;
    }
}
