package net.minecraft.client.renderer.texture;

import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;

public final class TextureAtlas {
    public record Tile(float u0, float v0, float u1, float v1) {}

    private final Map<ResourceLocation, Tile> tiles = new HashMap<>();
    private final IResourceManager resources;
    private int textureId;
    private int widgetsTextureId;
    private int iconsTextureId;

    public TextureAtlas(IResourceManager resources) {
        this.resources = resources;
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
        int columns = 32;
        int rows = (ordered.size() + columns - 1) / columns;
        int width = columns * 16;
        int height = rows * 16;
        BufferedImage atlas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = atlas.createGraphics();

        for (int i = 0; i < ordered.size(); i++) {
            ResourceLocation name = ordered.get(i);
            BufferedImage source = read(name);
            int x = (i % columns) * 16;
            int y = (i / columns) * 16;
            graphics.drawImage(source, x, y, x + 16, y + 16, 0, 0, 16, 16, null);
            float inset = 0.02f;
            tiles.put(name, new Tile(
                (x + inset) / width, (y + inset) / height,
                (x + 16 - inset) / width, (y + 16 - inset) / height));
        }
        graphics.dispose();

        ByteBuffer pixels = BufferUtils.createByteBuffer(width * height * 4);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = atlas.getRGB(x, y);
                pixels.put((byte) ((argb >> 16) & 0xff));
                pixels.put((byte) ((argb >> 8) & 0xff));
                pixels.put((byte) (argb & 0xff));
                pixels.put((byte) ((argb >> 24) & 0xff));
            }
        }
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

    private int loadGuiTexture(ResourceLocation location) {
        BufferedImage image = read(location);
        int width = image.getWidth(), height = image.getHeight();
        ByteBuffer pixels = BufferUtils.createByteBuffer(width * height * 4);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = image.getRGB(x, y);
                pixels.put((byte) ((argb >> 16) & 0xff));
                pixels.put((byte) ((argb >> 8) & 0xff));
                pixels.put((byte) (argb & 0xff));
                pixels.put((byte) ((argb >> 24) & 0xff));
            }
        }
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
