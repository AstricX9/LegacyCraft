package net.minecraft.world.gen;

import java.util.Arrays;
import java.util.Random;

/**
 * A stack of {@link NoiseGeneratorImproved} octaves. Each octave halves the
 * frequency and doubles the weight, so the totals are large and the callers
 * divide them down (by 512 for the limit fields, 8000 for the depth field).
 *
 * <p>The octaves draw from one shared {@link Random} in construction order,
 * which is why the generators in {@link TerrainGenerator} must be built in
 * vanilla's exact order.
 */
public final class NoiseGeneratorOctaves {
    private final NoiseGeneratorImproved[] octaves;

    public NoiseGeneratorOctaves(Random random, int count) {
        octaves = new NoiseGeneratorImproved[count];
        for (int i = 0; i < count; i++) octaves[i] = new NoiseGeneratorImproved(random);
    }

    public double[] generate(double[] out, int x, int y, int z, int xSize, int ySize, int zSize,
                             double xScale, double yScale, double zScale) {
        if (out == null) out = new double[xSize * ySize * zSize];
        else Arrays.fill(out, 0.0);
        double octaveScale = 1.0;
        for (NoiseGeneratorImproved octave : octaves) {
            double offsetX = x * octaveScale * xScale;
            double offsetY = y * octaveScale * yScale;
            double offsetZ = z * octaveScale * zScale;
            // Keep the sample coordinates small so precision does not drift far from the origin.
            long wholeX = floor(offsetX), wholeZ = floor(offsetZ);
            offsetX = offsetX - wholeX + wholeX % 16777216L;
            offsetZ = offsetZ - wholeZ + wholeZ % 16777216L;
            octave.populate(out, offsetX, offsetY, offsetZ, xSize, ySize, zSize,
                xScale * octaveScale, yScale * octaveScale, zScale * octaveScale, octaveScale);
            octaveScale /= 2.0;
        }
        return out;
    }

    /** The flat version; vanilla feeds it a fixed y offset of 10 and ignores the y scale. */
    public double[] generate(double[] out, int x, int z, int xSize, int zSize, double xScale, double zScale) {
        return generate(out, x, 10, z, xSize, 1, zSize, xScale, 1.0, zScale);
    }

    private static long floor(double value) {
        long whole = (long) value;
        return value < whole ? whole - 1 : whole;
    }
}
