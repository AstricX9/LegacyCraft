package net.minecraft.client.gui;

import static org.lwjgl.opengl.GL11.*;

/**
 * Immediate-mode 2D drawing in scaled GUI coordinates (master context §63).
 * Colours are ARGB ints as in vanilla; textured rects assume the 256x256
 * sheets the 1.8.9 GUI uses.
 */
public class Gui {
    protected static final float SHEET = 1f / 256f;

    /** Solid rectangle; corners may be given in either order. */
    public static void drawRect(int left, int top, int right, int bottom, int color) {
        if (left > right) { int swap = left; left = right; right = swap; }
        if (top > bottom) { int swap = top; top = bottom; bottom = swap; }
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        color(color);
        glBegin(GL_QUADS);
        glVertex2f(left, bottom); glVertex2f(right, bottom); glVertex2f(right, top); glVertex2f(left, top);
        glEnd();
        glEnable(GL_TEXTURE_2D);
        glColor4f(1, 1, 1, 1);
    }

    /** Vertical gradient from {@code topColor} to {@code bottomColor}. */
    public static void drawGradientRect(int left, int top, int right, int bottom, int topColor, int bottomColor) {
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glDisable(GL_ALPHA_TEST);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glShadeModel(GL_SMOOTH);
        glBegin(GL_QUADS);
        color(topColor); glVertex2f(right, top); glVertex2f(left, top);
        color(bottomColor); glVertex2f(left, bottom); glVertex2f(right, bottom);
        glEnd();
        glShadeModel(GL_FLAT);
        glEnable(GL_ALPHA_TEST);
        glEnable(GL_TEXTURE_2D);
        glColor4f(1, 1, 1, 1);
    }

    /** A {@code width}x{@code height} region of the bound 256x256 sheet at (u, v). */
    public static void drawTexturedModalRect(int x, int y, int u, int v, int width, int height) {
        glBegin(GL_QUADS);
        glTexCoord2f(u * SHEET, (v + height) * SHEET); glVertex2f(x, y + height);
        glTexCoord2f((u + width) * SHEET, (v + height) * SHEET); glVertex2f(x + width, y + height);
        glTexCoord2f((u + width) * SHEET, v * SHEET); glVertex2f(x + width, y);
        glTexCoord2f(u * SHEET, v * SHEET); glVertex2f(x, y);
        glEnd();
    }

    /** Draws a region of a texture whose size is not 256x256. */
    public static void drawModalRectWithCustomSizedTexture(int x, int y, float u, float v, int width, int height,
                                                           float textureWidth, float textureHeight) {
        drawScaledCustomSizeModalRect(x, y, u, v, width, height, width, height, textureWidth, textureHeight);
    }

    public static void drawScaledCustomSizeModalRect(int x, int y, float u, float v, int uWidth, int vHeight,
                                                     int width, int height, float textureWidth, float textureHeight) {
        float fu = 1f / textureWidth, fv = 1f / textureHeight;
        glBegin(GL_QUADS);
        glTexCoord2f(u * fu, (v + vHeight) * fv); glVertex2f(x, y + height);
        glTexCoord2f((u + uWidth) * fu, (v + vHeight) * fv); glVertex2f(x + width, y + height);
        glTexCoord2f((u + uWidth) * fu, v * fv); glVertex2f(x + width, y);
        glTexCoord2f(u * fu, v * fv); glVertex2f(x, y);
        glEnd();
    }

    public static void drawCenteredString(FontRenderer font, String text, int x, int y, int color) {
        font.drawStringWithShadow(text, x - font.getStringWidth(text) / 2, y, color);
    }

    public static void drawString(FontRenderer font, String text, int x, int y, int color) {
        font.drawStringWithShadow(text, x, y, color);
    }

    public static void drawHorizontalLine(int startX, int endX, int y, int color) {
        if (endX < startX) { int swap = startX; startX = endX; endX = swap; }
        drawRect(startX, y, endX + 1, y + 1, color);
    }

    public static void drawVerticalLine(int x, int startY, int endY, int color) {
        if (endY < startY) { int swap = startY; startY = endY; endY = swap; }
        drawRect(x, startY + 1, x + 1, endY, color);
    }

    static void color(int argb) {
        glColor4f((argb >> 16 & 255) / 255f, (argb >> 8 & 255) / 255f, (argb & 255) / 255f, (argb >>> 24) / 255f);
    }
}
