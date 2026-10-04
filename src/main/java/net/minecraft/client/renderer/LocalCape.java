package net.minecraft.client.renderer;

import net.minecraft.client.gui.GuiTextures;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glDeleteTextures;

/**
 * An offline cape supplied by the launcher with {@code --cape=<png>}: the
 * 64x32 cape layout (or a larger image of the same 2:1 shape). Without one
 * the player has no cape, as for any account without a cape in 1.8.9.
 */
public final class LocalCape {
    private final Path file;
    private int textureId;

    public LocalCape(Path file) { this.file = file; }

    /** Must be called once a GL context exists. Invalid files mean no cape. */
    public void load() {
        if (file == null) return;
        try {
            if (!Files.isRegularFile(file)) throw new IOException("File does not exist");
            BufferedImage image = ImageIO.read(file.toFile());
            if (image == null || image.getWidth() < 64 || image.getWidth() != image.getHeight() * 2 || image.getWidth() % 64 != 0)
                throw new IOException("Expected a 64x32 PNG cape (or a larger 2:1 multiple)");
            textureId = GuiTextures.upload(image, false);
        } catch (IOException error) {
            System.err.println("Couldn't load launcher cape " + file + ": " + error.getMessage());
        }
    }

    public boolean present() { return textureId != 0; }

    public void bind() { if (textureId != 0) glBindTexture(GL_TEXTURE_2D, textureId); }

    public void destroy() {
        if (textureId != 0) glDeleteTextures(textureId);
        textureId = 0;
    }
}
