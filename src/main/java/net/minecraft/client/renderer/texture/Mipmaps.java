package net.minecraft.client.renderer.texture;

import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_TEXTURE_MAX_LEVEL;
import static org.lwjgl.opengl.GL12.GL_TEXTURE_MAX_LOD;
import static org.lwjgl.opengl.GL12.GL_TEXTURE_MIN_LOD;
import static org.lwjgl.opengl.GL14.GL_TEXTURE_LOD_BIAS;

/**
 * Mipmap generation as 1.8.9's TextureUtil.generateMipmapData does it
 * (HYPOTHESIS: recalled). Each level halves the previous one by blending
 * 2x2 blocks in linear light (channels raised to 2.2, averaged, raised back
 * to 1/2.2). If any pixel of the image is fully transparent, the blend only
 * counts the non-transparent pixels' colours (still dividing by four) and a
 * result whose alpha falls below 96 becomes fully transparent, which keeps
 * leaves and plants from turning into grey haze in the distance.
 */
public final class Mipmaps {
    private static final float[] LINEAR = new float[256];

    static {
        for (int i = 0; i < 256; i++) LINEAR[i] = (float) Math.pow(i / 255.0F, 2.2D);
    }

    private Mipmaps() {}

    /** Level 0 (the input) and {@code levels} smaller levels of a {@code width}-wide ARGB image. */
    public static int[][] generate(int[] pixels, int width, int levels) {
        int[][] out = new int[levels + 1][];
        out[0] = pixels;
        if (levels <= 0) return out;
        boolean transparent = false;
        for (int argb : pixels) {
            if (argb >> 24 == 0) {
                transparent = true;
                break;
            }
        }
        for (int level = 1; level <= levels; level++) {
            int[] previous = out[level - 1];
            int w = width >> level;
            if (w <= 0) {
                out[level] = new int[0];
                continue;
            }
            int[] current = new int[previous.length >> 2];
            int h = current.length / w;
            int previousWidth = w << 1;
            for (int x = 0; x < w; x++)
                for (int y = 0; y < h; y++) {
                    int i = 2 * (x + y * previousWidth);
                    current[x + y * w] = blend(previous[i], previous[i + 1], previous[i + previousWidth],
                        previous[i + 1 + previousWidth], transparent);
                }
            out[level] = current;
        }
        return out;
    }

    private static int blend(int a, int b, int c, int d, boolean transparent) {
        if (!transparent) {
            return component(a, b, c, d, 24) << 24 | component(a, b, c, d, 16) << 16
                | component(a, b, c, d, 8) << 8 | component(a, b, c, d, 0);
        }
        float alpha = 0, red = 0, green = 0, blue = 0;
        for (int argb : new int[] {a, b, c, d}) {
            if (argb >> 24 == 0) continue;
            alpha += LINEAR[argb >>> 24];
            red += LINEAR[argb >> 16 & 255];
            green += LINEAR[argb >> 8 & 255];
            blue += LINEAR[argb & 255];
        }
        int outAlpha = (int) (Math.pow(alpha / 4, 0.45454545D) * 255.0D);
        int outRed = (int) (Math.pow(red / 4, 0.45454545D) * 255.0D);
        int outGreen = (int) (Math.pow(green / 4, 0.45454545D) * 255.0D);
        int outBlue = (int) (Math.pow(blue / 4, 0.45454545D) * 255.0D);
        if (outAlpha < 96) outAlpha = 0;
        return outAlpha << 24 | outRed << 16 | outGreen << 8 | outBlue;
    }

    private static int component(int a, int b, int c, int d, int shift) {
        float sum = LINEAR[a >> shift & 255] + LINEAR[b >> shift & 255] + LINEAR[c >> shift & 255] + LINEAR[d >> shift & 255];
        return (int) (Math.pow(sum * 0.25D, 0.45454545D) * 255.0D);
    }

    /**
     * The highest level an image can have so its smallest mip is still one
     * pixel and every level halves evenly (TextureMap's limit for a sprite).
     */
    public static int maxLevels(int width, int height, int wanted) {
        int limit = Math.min(Integer.lowestOneBit(width), Integer.lowestOneBit(height));
        int levels = 0;
        while (levels < wanted && (1 << (levels + 1)) <= limit) levels++;
        return levels;
    }

    /**
     * Sets the bound texture's filtering: nearest when magnified, nearest
     * within a level and linear between levels when minified
     * (setBlurMipmapDirect(false, true)), levels 0..{@code levels}.
     */
    public static void setFiltering(int levels) {
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, levels > 0 ? GL_NEAREST_MIPMAP_LINEAR : GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, levels);
        glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MIN_LOD, 0f);
        glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MAX_LOD, levels);
        glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_LOD_BIAS, 0f);
    }

    /** Uploads every level of {@code mips} ({@code width} x {@code height} at level 0) to the bound texture. */
    public static void upload(int[][] mips, int width, int height) {
        for (int level = 0; level < mips.length; level++) {
            int w = Math.max(1, width >> level), h = Math.max(1, height >> level);
            glTexImage2D(GL_TEXTURE_2D, level, GL_RGBA8, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer(mips[level], w * h));
        }
    }

    /** Uploads every level of a sub-image at ({@code x}, {@code y}) of level 0. */
    public static void uploadSub(int[][] mips, int x, int y, int width, int height) {
        for (int level = 0; level < mips.length; level++) {
            int w = width >> level, h = height >> level;
            if (w <= 0 || h <= 0) break;
            glTexSubImage2D(GL_TEXTURE_2D, level, x >> level, y >> level, w, h, GL_RGBA, GL_UNSIGNED_BYTE,
                buffer(mips[level], w * h));
        }
    }

    static ByteBuffer buffer(int[] argb, int count) {
        ByteBuffer pixels = BufferUtils.createByteBuffer(count * 4);
        for (int i = 0; i < count; i++) {
            int c = argb[i];
            pixels.put((byte) (c >> 16)).put((byte) (c >> 8)).put((byte) c).put((byte) (c >>> 24));
        }
        pixels.flip();
        return pixels;
    }
}
