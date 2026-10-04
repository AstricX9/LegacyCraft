package net.legacycraft.launcher;

import javax.imageio.ImageIO;
import javax.swing.JViewport;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

/**
 * The news page's background tile (the theme's bg_main): the dirt texture,
 * doubled to 32x32 and turned into a dark, slightly mottled grey on the
 * page's #222 base. Also used behind the skin preview.
 */
final class Textures {
    private static BufferedImage darkTile;

    private Textures() {}

    static void init(Path dirtFile) {
        BufferedImage img = null;
        try { if (dirtFile != null) img = ImageIO.read(dirtFile.toFile()); } catch (Exception ignored) {}
        if (img == null || img.getWidth() < 16) img = fallbackDirt();
        darkTile = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 32; y++)
            for (int x = 0; x < 32; x++) {
                int rgb = img.getRGB(x / 2 * img.getWidth() / 16, y / 2 * img.getHeight() / 16);
                int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
                double lum = 0.299 * r + 0.587 * g + 0.114 * b;
                int v = (int) Math.round(16 + lum * 0.17);
                darkTile.setRGB(x, y, (v << 16) | (v << 8) | v);
            }
    }

    static BufferedImage darkTile() { return darkTile; }

    /** A dirt-like noise tile, used only when the game's dirt.png can't be found. */
    private static BufferedImage fallbackDirt() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        int[] palette = {0x593D29, 0x79553A, 0x966C4A, 0x6C4D36, 0x4F3624, 0x8A6545};
        long seed = 1189L;
        for (int y = 0; y < 16; y++)
            for (int x = 0; x < 16; x++) {
                seed = seed * 6364136223846793005L + 1442695040888963407L;
                img.setRGB(x, y, palette[(int) ((seed >>> 33) % palette.length)]);
            }
        return img;
    }

    static void tile(Graphics g, BufferedImage tile, int offX, int offY, int w, int h) {
        int tw = tile.getWidth(), th = tile.getHeight();
        int startX = Math.floorMod(offX, tw) - tw, startY = Math.floorMod(offY, th) - th;
        for (int y = startY; y < h; y += th)
            for (int x = startX; x < w; x += tw) g.drawImage(tile, x, y, null);
    }

    /** A scroll viewport whose background is the dark tile, scrolling with the content like a page background. */
    static final class TiledViewport extends JViewport {
        TiledViewport() { setOpaque(true); setScrollMode(SIMPLE_SCROLL_MODE); }

        @Override protected void paintComponent(Graphics g) {
            Point p = getViewPosition();
            tile(g, darkTile, -p.x, -p.y, getWidth(), getHeight());
        }
    }
}
