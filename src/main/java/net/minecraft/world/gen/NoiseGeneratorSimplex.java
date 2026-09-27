package net.minecraft.world.gen;

import java.util.Random;

/**
 * One octave of 2D simplex noise as 1.8.9 samples it for the surface
 * ("stone noise") and biome details: Gustavson's simplex algorithm with a
 * Random-shuffled permutation and a random origin. The construction draws
 * three doubles and 256 ints, the same as {@link NoiseGeneratorImproved},
 * which keeps every generator after it in step. HYPOTHESIS until compared.
 */
public final class NoiseGeneratorSimplex {
    private static final int[][] GRADIENTS = {{1, 1, 0}, {-1, 1, 0}, {1, -1, 0}, {-1, -1, 0}, {1, 0, 1}, {-1, 0, 1},
        {1, 0, -1}, {-1, 0, -1}, {0, 1, 1}, {0, -1, 1}, {0, 1, -1}, {0, -1, -1}};
    private static final double SQRT_3 = Math.sqrt(3.0);
    private static final double F2 = 0.5 * (SQRT_3 - 1.0);
    private static final double G2 = (3.0 - SQRT_3) / 6.0;

    private final int[] permutations = new int[512];
    private final double originX, originY;

    public NoiseGeneratorSimplex(Random random) {
        originX = random.nextDouble() * 256.0;
        originY = random.nextDouble() * 256.0;
        random.nextDouble(); // the unused z origin
        for (int i = 0; i < 256; i++) permutations[i] = i;
        for (int i = 0; i < 256; i++) {
            int swap = random.nextInt(256 - i) + i;
            int held = permutations[i];
            permutations[i] = permutations[swap];
            permutations[swap] = held;
            permutations[i + 256] = permutations[i];
        }
    }

    private static int fastFloor(double value) { return value > 0.0 ? (int) value : (int) value - 1; }
    private static double dot(int[] gradient, double x, double y) { return gradient[0] * x + gradient[1] * y; }

    /** Noise at one point, without the octave's origin (vanilla func_151605_a). */
    public double getValue(double x, double y) {
        double skew = (x + y) * F2;
        int i = fastFloor(x + skew), j = fastFloor(y + skew);
        double unskew = (i + j) * G2;
        double x0 = x - (i - unskew), y0 = y - (j - unskew);
        int i1, j1;
        if (x0 > y0) { i1 = 1; j1 = 0; } else { i1 = 0; j1 = 1; }
        double x1 = x0 - i1 + G2, y1 = y0 - j1 + G2;
        double x2 = x0 - 1.0 + 2.0 * G2, y2 = y0 - 1.0 + 2.0 * G2;
        int ii = i & 255, jj = j & 255;
        return 70.0 * (corner(ii, jj, 0, 0, x0, y0) + corner(ii, jj, i1, j1, x1, y1) + corner(ii, jj, 1, 1, x2, y2));
    }

    private double corner(int ii, int jj, int di, int dj, double x, double y) {
        double t = 0.5 - x * x - y * y;
        if (t < 0.0) return 0.0;
        int gradient = permutations[ii + di + permutations[jj + dj]] % 12;
        t *= t;
        return t * t * dot(GRADIENTS[gradient], x, y);
    }

    /**
     * Adds a {@code width} x {@code height} grid of samples to {@code out}
     * (index {@code row * width + column}), sampled at
     * {@code ((x + column) * xScale + originX, (y + row) * yScale + originY)}.
     */
    public void add(double[] out, double x, double y, int width, int height, double xScale, double yScale, double amplitude) {
        int index = 0;
        for (int row = 0; row < height; row++) {
            double sampleY = (y + row) * yScale + originY;
            for (int column = 0; column < width; column++) {
                double sampleX = (x + column) * xScale + originX;
                out[index++] += getValue(sampleX, sampleY) * amplitude;
            }
        }
    }
}
