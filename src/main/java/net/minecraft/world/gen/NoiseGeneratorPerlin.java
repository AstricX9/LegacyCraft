package net.minecraft.world.gen;

import java.util.Arrays;
import java.util.Random;

/**
 * 1.8.9's "NoiseGeneratorPerlin": despite the name, a stack of
 * {@link NoiseGeneratorSimplex} octaves. Used for the surface depth noise,
 * the swamp and plains flower noise, the temperature variation above y=64 and
 * the mesa clay bands.
 */
public final class NoiseGeneratorPerlin {
    private final NoiseGeneratorSimplex[] octaves;

    public NoiseGeneratorPerlin(Random random, int count) {
        octaves = new NoiseGeneratorSimplex[count];
        for (int i = 0; i < count; i++) octaves[i] = new NoiseGeneratorSimplex(random);
    }

    /** One point: each octave at twice the scale and half the weight of the one before (func_151601_a). */
    public double getValue(double x, double z) {
        double total = 0.0, scale = 1.0;
        for (NoiseGeneratorSimplex octave : octaves) {
            total += octave.getValue(x * scale, z * scale) / scale;
            scale /= 2.0;
        }
        return total;
    }

    /** A grid of samples (func_151599_a with the 0.5 lacunarity): octave k samples at scale/2^k with weight 0.55*2^k. */
    public double[] getRegion(double[] out, double x, double z, int width, int height, double xScale, double zScale, double frequencyFactor) {
        if (out != null && out.length >= width * height) Arrays.fill(out, 0.0);
        else out = new double[width * height];
        double lacunarity = 1.0, frequency = 1.0;
        for (NoiseGeneratorSimplex octave : octaves) {
            octave.add(out, x, z, width, height, xScale * frequency * lacunarity, zScale * frequency * lacunarity, 0.55 / lacunarity);
            frequency *= frequencyFactor;
            lacunarity *= 0.5;
        }
        return out;
    }
}
