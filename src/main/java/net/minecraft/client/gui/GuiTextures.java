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
 */
public final class GuiTextures {
    public static final ResourceLocation WIDGETS = new ResourceLocation("minecraft:textures/gui/widgets.png");
    public static final ResourceLocation OPTIONS_BACKGROUND = new ResourceLocation("minecraft:textures/gui/options_background.png");
    public static final ResourceLocation ICONS = new ResourceLocation("minecraft:textures/gui/icons.png");

    private final IResourceManager resources;
    private final Map<ResourceLocation, Integer> textures = new HashMap<>();
    private final Map<ResourceLocation, int[]> sizes = new HashMap<>();
    private int missing;

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
        return upload(image, true);
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
