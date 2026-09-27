package net.minecraft.client.renderer;

/**
 * Turns (sky, block) light levels into a colour multiplier, like 1.8's 16x16
 * lightmap texture (EntityRenderer.updateLightmap): a non-linear brightness
 * curve per level, sky light scaled by the sun's brightness for the time of
 * day (keeping a blue tint at night), block light tinted warm (red first,
 * blue last), the gamma slider, and a small floor so darkness is never pure
 * black. Colours between whole levels are bilinear, as the texture's linear
 * filtering gives them. HYPOTHESIS: recalled formula, without torch flicker,
 * lightning flashes, night vision or rain.
 */
public final class LightMap {
    private static final float[] BRIGHTNESS = new float[16];
    static {
        for (int level = 0; level < 16; level++) {
            float darkness = 1f - level / 15f;
            BRIGHTNESS[level] = (1f - darkness) / (darkness * 3f + 1f);
        }
    }

    private static volatile float[][][] table = build(1f, 0f);
    private static float builtSun = 1f, builtGamma = 0f;
    private static int version;

    private LightMap() {}

    /** Recomputes the table for the sun's brightness (0.2 at night to 1 at noon) and the gamma setting (0-1). */
    public static void update(float sunBrightness, float gamma) {
        if (Math.abs(sunBrightness - builtSun) < 1e-4f && Math.abs(gamma - builtGamma) < 1e-4f) return;
        builtSun = sunBrightness;
        builtGamma = gamma;
        table = build(sunBrightness, gamma);
        version++;
    }

    /** Changes whenever {@link #update} rebuilt the table, so a lightmap texture knows to re-upload. */
    public static int version() { return version; }

    private static float[][][] build(float sun, float gamma) {
        float[][][] result = new float[16][16][];
        float skyScale = sun * 0.95f + 0.05f;
        for (int sky = 0; sky < 16; sky++) {
            for (int block = 0; block < 16; block++) {
                float skyLight = BRIGHTNESS[sky] * skyScale;
                float torch = BRIGHTNESS[block] * 1.5f;
                float skyRed = skyLight * (sun * 0.65f + 0.35f);
                float skyGreen = skyLight * (sun * 0.65f + 0.35f);
                float red = skyRed + torch;
                float green = skyGreen + torch * ((torch * .6f + .4f) * .6f + .4f);
                float blue = skyLight + torch * (torch * torch * .6f + .4f);
                result[sky][block] = new float[]{finish(red, gamma), finish(green, gamma), finish(blue, gamma)};
            }
        }
        return result;
    }

    private static float finish(float channel, float gamma) {
        channel = Math.min(1f, channel * .96f + .03f);
        float inverse = 1f - channel;
        float bright = 1f - inverse * inverse * inverse * inverse;
        channel = channel * (1f - gamma) + bright * gamma;
        return Math.max(0f, Math.min(1f, channel * .96f + .03f));
    }

    /** RGB multiplier for fractional (averaged) light levels, 0-15 each. */
    public static float[] color(float sky, float block) {
        float[][][] current = table;
        sky = Math.max(0, Math.min(15, sky));
        block = Math.max(0, Math.min(15, block));
        int s0 = (int) sky, b0 = (int) block;
        int s1 = Math.min(15, s0 + 1), b1 = Math.min(15, b0 + 1);
        float ts = sky - s0, tb = block - b0;
        float[] result = new float[3];
        for (int c = 0; c < 3; c++) {
            float low = current[s0][b0][c] + (current[s0][b1][c] - current[s0][b0][c]) * tb;
            float high = current[s1][b0][c] + (current[s1][b1][c] - current[s1][b0][c]) * tb;
            result[c] = low + (high - low) * ts;
        }
        return result;
    }
}
