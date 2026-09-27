package net.minecraft.world.gen;

import java.util.Random;

/**
 * One octave of Perlin's improved noise, seeded from a {@link Random} and
 * sampled into a whole array at a time.
 *
 * <p>HYPOTHESIS (source_policy tier 3/4): the gradient table, the permutation
 * shuffle and the array sampling order are the documented 1.8 algorithm.
 * Everything downstream depends on this being exact, so it is checked
 * indirectly by VanillaTerrainTest against a real 1.8.9 world.
 */
public final class NoiseGeneratorImproved {
    /** Perlin's 12 gradient directions, with the last four repeated to make the table 16 long. */
    private static final double[] GRADIENT_X = {1, -1, 1, -1, 1, -1, 1, -1, 0, 0, 0, 0, 1, 0, -1, 0};
    private static final double[] GRADIENT_Y = {1, 1, -1, -1, 0, 0, 0, 0, 1, -1, 1, -1, 1, -1, 1, -1};
    private static final double[] GRADIENT_Z = {0, 0, 0, 0, 1, 1, -1, -1, 1, 1, -1, -1, 0, 1, 0, -1};

    private final int[] permutations = new int[512];
    /** The octave's own origin, so octaves do not line up with each other. */
    private final double originX, originY, originZ;

    public NoiseGeneratorImproved(Random random) {
        originX = random.nextDouble() * 256.0;
        originY = random.nextDouble() * 256.0;
        originZ = random.nextDouble() * 256.0;
        for (int i = 0; i < 256; i++) permutations[i] = i;
        for (int i = 0; i < 256; i++) {
            int swap = random.nextInt(256 - i) + i;
            int held = permutations[i];
            permutations[i] = permutations[swap];
            permutations[swap] = held;
            permutations[i + 256] = permutations[i];
        }
    }

    /**
     * Adds this octave's contribution to {@code out}, which is indexed with x
     * outermost, then z, then y — the order the terrain density grid expects.
     * {@code octaveScale} is the octave's frequency multiplier; the
     * contribution is scaled by its reciprocal, so the coarser octaves count
     * for more (which is why vanilla divides the totals by numbers like 512).
     */
    public void populate(double[] out, double xOffset, double yOffset, double zOffset,
                         int xSize, int ySize, int zSize, double xScale, double yScale, double zScale,
                         double octaveScale) {
        double amplitude = 1.0 / octaveScale;
        int index = 0;
        if (ySize == 1) {
            for (int xi = 0; xi < xSize; xi++) {
                double sampleX = xOffset + xi * xScale + originX;
                int cellX = floor(sampleX);
                int hashX = cellX & 255;
                sampleX -= cellX;
                double fadeX = fade(sampleX);
                for (int zi = 0; zi < zSize; zi++) {
                    double sampleZ = zOffset + zi * zScale + originZ;
                    int cellZ = floor(sampleZ);
                    int hashZ = cellZ & 255;
                    sampleZ -= cellZ;
                    double fadeZ = fade(sampleZ);
                    int west = permutations[permutations[hashX]] + hashZ;
                    int east = permutations[permutations[hashX + 1]] + hashZ;
                    double near = lerp(fadeX, flatGradient(permutations[west], sampleX, sampleZ),
                        flatGradient(permutations[east], sampleX - 1.0, sampleZ));
                    double far = lerp(fadeX, flatGradient(permutations[west + 1], sampleX, sampleZ - 1.0),
                        flatGradient(permutations[east + 1], sampleX - 1.0, sampleZ - 1.0));
                    out[index++] += lerp(fadeZ, near, far) * amplitude;
                }
            }
            return;
        }
        // Vanilla caches the four x-interpolated corner values, not just the hashes, and only
        // recomputes them when the sample enters a new y lattice cell. Later samples in the same
        // cell reuse gradients taken at the first sample's y fraction. That quirk is part of
        // 1.8.9's terrain shape, so it is reproduced here.
        int cachedCellY = -1;
        double lowerNorth = 0, upperNorth = 0, lowerSouth = 0, upperSouth = 0;
        for (int xi = 0; xi < xSize; xi++) {
            double sampleX = xOffset + xi * xScale + originX;
            int cellX = floor(sampleX);
            int hashX = cellX & 255;
            sampleX -= cellX;
            double fadeX = fade(sampleX);
            for (int zi = 0; zi < zSize; zi++) {
                double sampleZ = zOffset + zi * zScale + originZ;
                int cellZ = floor(sampleZ);
                int hashZ = cellZ & 255;
                sampleZ -= cellZ;
                double fadeZ = fade(sampleZ);
                for (int yi = 0; yi < ySize; yi++) {
                    double sampleY = yOffset + yi * yScale + originY;
                    int cellY = floor(sampleY);
                    int hashY = cellY & 255;
                    sampleY -= cellY;
                    double fadeY = fade(sampleY);
                    if (yi == 0 || hashY != cachedCellY) {
                        cachedCellY = hashY;
                        int west = permutations[hashX] + hashY;
                        int east = permutations[hashX + 1] + hashY;
                        int lowerWest = permutations[west] + hashZ, upperWest = permutations[west + 1] + hashZ;
                        int lowerEast = permutations[east] + hashZ, upperEast = permutations[east + 1] + hashZ;
                        lowerNorth = lerp(fadeX, gradient(permutations[lowerWest], sampleX, sampleY, sampleZ),
                            gradient(permutations[lowerEast], sampleX - 1.0, sampleY, sampleZ));
                        upperNorth = lerp(fadeX, gradient(permutations[upperWest], sampleX, sampleY - 1.0, sampleZ),
                            gradient(permutations[upperEast], sampleX - 1.0, sampleY - 1.0, sampleZ));
                        lowerSouth = lerp(fadeX, gradient(permutations[lowerWest + 1], sampleX, sampleY, sampleZ - 1.0),
                            gradient(permutations[lowerEast + 1], sampleX - 1.0, sampleY, sampleZ - 1.0));
                        upperSouth = lerp(fadeX, gradient(permutations[upperWest + 1], sampleX, sampleY - 1.0, sampleZ - 1.0),
                            gradient(permutations[upperEast + 1], sampleX - 1.0, sampleY - 1.0, sampleZ - 1.0));
                    }
                    double north = lerp(fadeY, lowerNorth, upperNorth);
                    double south = lerp(fadeY, lowerSouth, upperSouth);
                    out[index++] += lerp(fadeZ, north, south) * amplitude;
                }
            }
        }
    }

    /** Perlin's 6t^5 - 15t^4 + 10t^3 smoothstep. */
    private static double fade(double t) { return t * t * t * (t * (t * 6.0 - 15.0) + 10.0); }
    private static double lerp(double t, double a, double b) { return a + t * (b - a); }

    private static double gradient(int hash, double x, double y, double z) {
        int i = hash & 15;
        return GRADIENT_X[i] * x + GRADIENT_Y[i] * y + GRADIENT_Z[i] * z;
    }

    /** The 3D gradient with y = 0, which is what the flat (ySize == 1) case needs. */
    private static double flatGradient(int hash, double x, double z) {
        int i = hash & 15;
        return GRADIENT_X[i] * x + GRADIENT_Z[i] * z;
    }

    private static int floor(double value) {
        int whole = (int) value;
        return value < whole ? whole - 1 : whole;
    }
}
