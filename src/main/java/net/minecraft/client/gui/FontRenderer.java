package net.minecraft.client.gui;

import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;

/**
 * Legacy bitmap font renderer compatible with Minecraft formatting codes.
 * Glyphs come from the supplied 1.8.9 ascii.png rather than an operating-system font.
 */
public final class FontRenderer {
    public static final char FORMAT_CHAR = '\u00a7';
    public static final int FONT_HEIGHT = 9;
    private static final String FORMAT_CODES = "0123456789abcdefklmnor";
    private final int[] colorCode = new int[32];
    private final int[] charWidth = new int[256];
    private final IResourceManager resources;
    private int textureId;

    public FontRenderer(IResourceManager resources) {
        this.resources = resources;
        buildColorTable();
        for (int i = 0; i < charWidth.length; i++) charWidth[i] = i == 32 ? 4 : 6;
    }

    public void load() {
        BufferedImage image;
        ResourceLocation font = new ResourceLocation("minecraft:textures/font/ascii.png");
        try (InputStream stream = resources.open(font)) {
            image = ImageIO.read(stream);
        } catch (IOException error) {
            throw new IllegalStateException("Could not load the Minecraft bitmap font", error);
        }
        calculateCharacterWidths(image);
        textureId = upload(image);
    }

    private void buildColorTable() {
        for (int index = 0; index < 32; index++) {
            int darkOffset = (index >> 3 & 1) * 85;
            int red = (index >> 2 & 1) * 170 + darkOffset;
            int green = (index >> 1 & 1) * 170 + darkOffset;
            int blue = (index & 1) * 170 + darkOffset;
            if (index == 6) red += 85;
            if (index >= 16) { red /= 4; green /= 4; blue /= 4; }
            colorCode[index] = (red << 16) | (green << 8) | blue;
        }
    }

    private void calculateCharacterWidths(BufferedImage image) {
        int cellWidth = image.getWidth() / 16;
        int cellHeight = image.getHeight() / 16;
        for (int character = 0; character < 256; character++) {
            if (character == 32) { charWidth[character] = 4; continue; }
            int cellX = character % 16 * cellWidth;
            int cellY = character / 16 * cellHeight;
            int right = cellWidth - 1;
            while (right >= 0 && columnIsTransparent(image, cellX + right, cellY, cellHeight)) right--;
            charWidth[character] = Math.max(2, Math.min(8, right + 2));
        }
    }

    private boolean columnIsTransparent(BufferedImage image, int x, int y, int height) {
        for (int row = 0; row < height; row++)
            if (((image.getRGB(x, y + row) >>> 24) & 0xff) > 16) return false;
        return true;
    }

    private int upload(BufferedImage image) {
        ByteBuffer pixels = BufferUtils.createByteBuffer(image.getWidth() * image.getHeight() * 4);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                pixels.put((byte) 255).put((byte) 255).put((byte) 255)
                    .put((byte) ((argb >>> 24) & 0xff));
            }
        }
        pixels.flip();
        int id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, id);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, image.getWidth(), image.getHeight(),
            0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        return id;
    }

    public int drawStringWithShadow(String text, float x, float y, int color) {
        if (text == null) return 0;
        drawString(text, x + 1, y + 1, color, true);
        return drawString(text, x, y, color, false);
    }

    public int drawString(String text, float x, float y, int color) {
        return drawString(text, x, y, color, false);
    }

    private int drawString(String text, float startX, float y, int baseColor, boolean shadow) {
        if (text == null || text.isEmpty()) return 0;
        int alpha = (baseColor >>> 24) & 0xff;
        if (alpha == 0) alpha = 255;
        int initialRgb = baseColor & 0xffffff;
        if (shadow) initialRgb = darken(initialRgb);
        int rgb = initialRgb;
        boolean obfuscated = false, bold = false, strikethrough = false, underline = false, italic = false;
        float x = startX;
        glEnable(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D, textureId);

        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == FORMAT_CHAR && index + 1 < text.length()) {
                int code = FORMAT_CODES.indexOf(Character.toLowerCase(text.charAt(++index)));
                if (code >= 0 && code < 16) {
                    obfuscated = bold = strikethrough = underline = italic = false;
                    rgb = colorCode[code + (shadow ? 16 : 0)];
                } else if (code == 16) obfuscated = true;
                else if (code == 17) bold = true;
                else if (code == 18) strikethrough = true;
                else if (code == 19) underline = true;
                else if (code == 20) italic = true;
                else if (code == 21) {
                    obfuscated = bold = strikethrough = underline = italic = false;
                    rgb = initialRgb;
                }
                continue;
            }
            int glyph = character <= 255 ? character : '?';
            if (obfuscated && glyph != 32)
                glyph = 33 + Math.floorMod(glyph * 31 + (int)(System.nanoTime() / 50_000_000L), 94);
            int width = charWidth[glyph];
            setColor(rgb, alpha);
            drawGlyph(glyph, x, y, italic);
            if (bold) drawGlyph(glyph, x + 1, y, italic);
            if (strikethrough) drawRule(x, y + 4, width + (bold ? 1 : 0), rgb, alpha);
            if (underline) drawRule(x, y + 8, width + (bold ? 1 : 0), rgb, alpha);
            x += width + (bold ? 1 : 0);
        }
        glColor4f(1, 1, 1, 1);
        return Math.round(x - startX);
    }

    private void drawGlyph(int glyph, float x, float y, boolean italic) {
        float u0 = (glyph % 16) / 16f, v0 = (glyph / 16) / 16f;
        float u1 = u0 + 1f/16f, v1 = v0 + 1f/16f;
        float skewTop = italic ? 1f : 0f, skewBottom = italic ? -1f : 0f;
        glBindTexture(GL_TEXTURE_2D, textureId);
        glBegin(GL_QUADS);
        glTexCoord2f(u0,v0); glVertex2f(x + skewTop, y);
        glTexCoord2f(u1,v0); glVertex2f(x + 8 + skewTop, y);
        glTexCoord2f(u1,v1); glVertex2f(x + 8 + skewBottom, y + 8);
        glTexCoord2f(u0,v1); glVertex2f(x + skewBottom, y + 8);
        glEnd();
    }

    private void drawRule(float x, float y, float width, int rgb, int alpha) {
        glDisable(GL_TEXTURE_2D);
        setColor(rgb, alpha);
        glBegin(GL_QUADS);
        glVertex2f(x, y); glVertex2f(x + width, y); glVertex2f(x + width, y + 1); glVertex2f(x, y + 1);
        glEnd();
        glEnable(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D, textureId);
    }

    public int getStringWidth(String text) {
        if (text == null) return 0;
        int width = 0;
        boolean bold = false;
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (character == FORMAT_CHAR && i + 1 < text.length()) {
                int code = FORMAT_CODES.indexOf(Character.toLowerCase(text.charAt(++i)));
                if (code >= 0 && code < 16 || code == 21) bold = false;
                else if (code == 17) bold = true;
                continue;
            }
            int glyph = character <= 255 ? character : '?';
            width += charWidth[glyph] + (bold ? 1 : 0);
        }
        return width;
    }

    public String trimStringToWidth(String text, int width) {
        if (text == null) return "";
        int used = 0;
        boolean bold = false;
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (character == FORMAT_CHAR && i + 1 < text.length()) {
                char format = text.charAt(++i);
                result.append(character).append(format);
                int code = FORMAT_CODES.indexOf(Character.toLowerCase(format));
                if (code >= 0 && code < 16 || code == 21) bold = false;
                else if (code == 17) bold = true;
                continue;
            }
            int advance = charWidth[character <= 255 ? character : '?'] + (bold ? 1 : 0);
            if (used + advance > width) break;
            used += advance;
            result.append(character);
        }
        return result.toString();
    }

    public List<String> listFormattedStringToWidth(String text, int width) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) return lines;
        String remaining = text;
        String activeFormat = "";
        while (!remaining.isEmpty()) {
            String candidate = trimStringToWidth(remaining, width);
            if (candidate.isEmpty()) candidate = remaining.substring(0, 1);
            int newline = candidate.indexOf('\n');
            if (newline >= 0) candidate = candidate.substring(0, newline);
            else if (candidate.length() < remaining.length()) {
                int space = candidate.lastIndexOf(' ');
                if (space > 0) candidate = candidate.substring(0, space);
            }
            lines.add(activeFormat + candidate);
            int consumed = candidate.length();
            while (consumed < remaining.length() && (remaining.charAt(consumed) == ' ' || remaining.charAt(consumed) == '\n')) consumed++;
            activeFormat = getFormatFromString(activeFormat + candidate);
            remaining = remaining.substring(consumed);
        }
        return lines;
    }

    public static String getFormatFromString(String text) {
        String color = "", styles = "";
        if (text == null) return "";
        for (int i = 0; i + 1 < text.length(); i++) {
            if (text.charAt(i) != FORMAT_CHAR) continue;
            char code = Character.toLowerCase(text.charAt(++i));
            if ("0123456789abcdef".indexOf(code) >= 0) { color = "" + FORMAT_CHAR + code; styles = ""; }
            else if ("klmno".indexOf(code) >= 0) styles += "" + FORMAT_CHAR + code;
            else if (code == 'r') { color = ""; styles = ""; }
        }
        return color + styles;
    }

    public static String stripFormattingCodes(String text) {
        return text == null ? null : text.replaceAll("(?i)" + FORMAT_CHAR + "[0-9A-FK-OR]", "");
    }

    private static int darken(int rgb) { return ((rgb & 0xfcfcfc) >> 2); }
    private static void setColor(int rgb, int alpha) {
        glColor4f(((rgb >> 16) & 0xff) / 255f, ((rgb >> 8) & 0xff) / 255f,
            (rgb & 0xff) / 255f, alpha / 255f);
    }
    public void destroy() { if (textureId != 0) glDeleteTextures(textureId); }
}
