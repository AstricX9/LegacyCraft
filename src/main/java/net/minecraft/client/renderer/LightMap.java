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

    private static volatile float[][][] table = build(1f, 0f, 0f, false);
    private static float builtSun = 1f, builtGamma = 0f, builtNight = 0f;
    private static boolean builtNether;
    private static int version;

    private LightMap() {}

    /** Recomputes the table for the sun's brightness (0.2 at night to 1 at noon) and the gamma setting (0-1). */
    public static void update(float sunBrightness, float gamma) { update(sunBrightness, gamma, 0f); }

    /**
     * As {@link #update(float, float)} with Night Vision's brightness (0-1,
     * EntityRenderer.updateLightmap): each colour is scaled up towards its
     * brightest channel reaching full, by that much, before the gamma curve.
     */
    public static void update(float sunBrightness, float gamma, float nightVision) { update(sunBrightness, gamma, nightVision, false); }

    /** As above; in the Nether the brightness table has a floor of 0.1 (WorldProviderHell.generateLightBrightnessTable). */
    public static void update(float sunBrightness, float gamma, float nightVision, boolean nether) {
        if (Math.abs(sunBrightness - builtSun) < 1e-4f && Math.abs(gamma - builtGamma) < 1e-4f
            && Math.abs(nightVision - builtNight) < 1e-4f && nether == builtNether) return;
        builtSun = sunBrightness;
        builtGamma = gamma;
        builtNight = nightVision;
        builtNether = nether;
        table = build(sunBrightness, gamma, nightVision, nether);
        version++;
    }

    /** Changes whenever {@link #update} rebuilt the table, so a lightmap texture knows to re-upload. */
    public static int version() { return version; }

    private static float[][][] build(float sun, float gamma, float night, boolean nether) {
        float[][][] result = new float[16][16][];
        float skyScale = sun * 0.95f + 0.05f;
        for (int sky = 0; sky < 16; sky++) {
            for (int block = 0; block < 16; block++) {
                float skyBright = nether ? BRIGHTNESS[sky] * 0.9f + 0.1f : BRIGHTNESS[sky];
                float blockBright = nether ? BRIGHTNESS[block] * 0.9f + 0.1f : BRIGHTNESS[block];
                float skyLight = skyBright * skyScale;
                float torch = blockBright * 1.5f;
                float skyRed = skyLight * (sun * 0.65f + 0.35f);
                float skyGreen = skyLight * (sun * 0.65f + 0.35f);
                float red = skyRed + torch;
                float green = skyGreen + torch * ((torch * .6f + .4f) * .6f + .4f);
                float blue = skyLight + torch * (torch * torch * .6f + .4f);
                red = Math.min(1f, red * .96f + .03f);
                green = Math.min(1f, green * .96f + .03f);
                blue = Math.min(1f, blue * .96f + .03f);
                if (night > 0f) {
                    float scale = 1f / Math.max(red, Math.max(green, blue));
                    red = Math.min(1f, red * (1f - night) + red * scale * night);
                    green = Math.min(1f, green * (1f - night) + green * scale * night);
                    blue = Math.min(1f, blue * (1f - night) + blue * scale * night);
                }
                result[sky][block] = new float[]{finish(red, gamma), finish(green, gamma), finish(blue, gamma)};
            }
        }
        return result;
    }

    private static float finish(float channel, float gamma) {
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
