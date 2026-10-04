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
 * An offline player skin supplied by the launcher.  It has no account,
 * network, or launcher implementation dependency: the process boundary is
 * simply {@code --skin=<absolute png path>}.
 */
public final class LocalSkin {
    private final Path file;
    private int textureId;

    public LocalSkin(Path file) { this.file = file; }

    /** Must be called once a GL context exists. Invalid files fall back to Steve. */
    public void load() {
        if (file == null) return;
        try {
            if (!Files.isRegularFile(file)) throw new IOException("File does not exist");
            BufferedImage image = ImageIO.read(file.toFile());
            if (image == null || image.getWidth() != 64 || image.getHeight() != 64)
                throw new IOException("Expected a 64x64 PNG skin");
            textureId = GuiTextures.upload(image, false);
        } catch (IOException error) {
            System.err.println("Couldn't load launcher skin " + file + ": " + error.getMessage());
        }
    }

    public void bind(GuiTextures textures) {
        if (textureId != 0) glBindTexture(GL_TEXTURE_2D, textureId);
        else textures.bind(FirstPersonRenderer.STEVE);
    }

    public void destroy() {
        if (textureId != 0) glDeleteTextures(textureId);
        textureId = 0;
    }
}
