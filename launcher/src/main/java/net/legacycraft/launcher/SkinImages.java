package net.legacycraft.launcher;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Skin loading the way the 1.8 client prepares a downloaded skin
 * (ImageBufferDownload): legacy 64x32 skins gain the 1.8 left-arm/left-leg
 * areas by mirroring the right limbs, the base layer is forced opaque, and a
 * legacy hat layer that is completely opaque is cleared. LegacyCraft only
 * accepts 64x64 files, so the launcher hands it the converted image.
 */
final class SkinImages {
    record Result(BufferedImage image, boolean legacy, boolean slim) {}

    private SkinImages() {}

    static Result read(Path file) throws IOException {
        BufferedImage raw;
        try { raw = Files.isRegularFile(file) ? ImageIO.read(file.toFile()) : null; }
        catch (IOException e) { raw = null; }
        if (raw == null) throw new IOException("That file isn't a PNG image.");
        if (raw.getWidth() != 64 || (raw.getHeight() != 64 && raw.getHeight() != 32))
            throw new IOException("Skins must be 64x64 or 64x32 pixels (this one is " + raw.getWidth() + "x" + raw.getHeight() + ").");
        boolean legacy = raw.getHeight() == 32;
        return new Result(process(raw), legacy, !legacy && looksSlim(raw));
    }

    static BufferedImage process(BufferedImage raw) {
        boolean legacy = raw.getHeight() == 32;
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < raw.getHeight(); y++)
            for (int x = 0; x < 64; x++) img.setRGB(x, y, raw.getRGB(x, y));
        if (legacy) {
            mirrorLimb(img, 0, 16, 16, 48);   // right leg -> left leg
            mirrorLimb(img, 40, 16, 32, 48);  // right arm -> left arm
        }
        setAreaOpaque(img, 0, 0, 32, 16);
        if (legacy) setAreaTransparent(img, 32, 0, 64, 32);
        setAreaOpaque(img, 0, 16, 64, 32);
        setAreaOpaque(img, 16, 48, 48, 64);
        return img;
    }

    /** Copies a 4x12x4 limb, mirrored, the way 1.8 builds the new left limbs. */
    private static void mirrorLimb(BufferedImage img, int sx, int sy, int dx, int dy) {
        copyFlipped(img, sx + 4, sy, dx + 4, dy, 4, 4);        // top
        copyFlipped(img, sx + 8, sy, dx + 8, dy, 4, 4);        // bottom
        copyFlipped(img, sx + 8, sy + 4, dx, dy + 4, 4, 12);   // inner side -> outer side
        copyFlipped(img, sx + 4, sy + 4, dx + 4, dy + 4, 4, 12); // front
        copyFlipped(img, sx, sy + 4, dx + 8, dy + 4, 4, 12);   // outer side -> inner side
        copyFlipped(img, sx + 12, sy + 4, dx + 12, dy + 4, 4, 12); // back
    }

    private static void copyFlipped(BufferedImage img, int sx, int sy, int dx, int dy, int w, int h) {
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) img.setRGB(dx + w - 1 - x, dy + y, img.getRGB(sx + x, sy + y));
    }

    private static void setAreaOpaque(BufferedImage img, int x0, int y0, int x1, int y1) {
        for (int y = y0; y < y1; y++)
            for (int x = x0; x < x1; x++) img.setRGB(x, y, img.getRGB(x, y) | 0xFF000000);
    }

    private static void setAreaTransparent(BufferedImage img, int x0, int y0, int x1, int y1) {
        for (int y = y0; y < y1; y++)
            for (int x = x0; x < x1; x++) if ((img.getRGB(x, y) >>> 24) < 128) return;
        for (int y = y0; y < y1; y++)
            for (int x = x0; x < x1; x++) img.setRGB(x, y, img.getRGB(x, y) & 0x00FFFFFF);
    }

    /** Slim (Alex) skins leave the unused 2px column after the right arm's back empty (checked before processing). */
    static boolean looksSlim(BufferedImage img) {
        for (int y = 20; y < 32; y++)
            if ((img.getRGB(54, y) >>> 24) != 0 || (img.getRGB(55, y) >>> 24) != 0) return false;
        return true;
    }

    /**
     * A cape: the 64x32 layout (cape at the top left, the rest unused in 1.8.9)
     * or any 2:1 multiple of it for HD capes. A bare 22x17 cape is padded
     * into a 64x32 image.
     */
    static BufferedImage readCape(Path file) throws IOException {
        BufferedImage raw;
        try { raw = Files.isRegularFile(file) ? ImageIO.read(file.toFile()) : null; }
        catch (IOException e) { raw = null; }
        if (raw == null) throw new IOException("That file isn't a PNG image.");
        int w = raw.getWidth(), h = raw.getHeight();
        BufferedImage img;
        if (w == 22 && h == 17) {
            img = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
        } else if (w >= 64 && w % 64 == 0 && w == h * 2) {
            img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        } else {
            throw new IOException("Capes must be 64x32 pixels (or a bigger 2:1 multiple like 128x64); this one is " + w + "x" + h + ".");
        }
        java.awt.Graphics g = img.getGraphics();
        g.drawImage(raw, 0, 0, null);
        g.dispose();
        return img;
    }

    static void write(BufferedImage img, Path target) throws IOException {
        Files.createDirectories(target.toAbsolutePath().getParent());
        if (!ImageIO.write(img, "png", target.toFile())) throw new IOException("Couldn't write " + target);
    }
}
