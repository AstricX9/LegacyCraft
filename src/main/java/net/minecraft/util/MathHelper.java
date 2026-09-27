package net.minecraft.util;

/**
 * The 1.8.9 math helpers world generation depends on bit-for-bit: sine and
 * cosine come from a 65536-entry table (not {@link Math#sin}), and flooring
 * works on the int cast. Caves, ravines, ore veins and several trees steer
 * with these, so using the exact table keeps their shapes vanilla.
 */
public final class MathHelper {
    private static final float[] SIN_TABLE = new float[65536];
    static {
        for (int i = 0; i < 65536; i++) SIN_TABLE[i] = (float) Math.sin(i * Math.PI * 2.0 / 65536.0);
    }

    private MathHelper() {}

    public static float sin(float value) { return SIN_TABLE[(int) (value * 10430.378F) & 65535]; }
    public static float cos(float value) { return SIN_TABLE[(int) (value * 10430.378F + 16384.0F) & 65535]; }
    public static float sqrt(float value) { return (float) Math.sqrt(value); }

    public static int floor(double value) {
        int whole = (int) value;
        return value < whole ? whole - 1 : whole;
    }

    public static int floor(float value) {
        int whole = (int) value;
        return value < whole ? whole - 1 : whole;
    }

    public static int ceil(float value) {
        int whole = (int) value;
        return value > whole ? whole + 1 : whole;
    }

    public static double clamp(double value, double min, double max) { return value < min ? min : Math.min(value, max); }
}
