package net.minecraft.world.gen;

import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeRegistry;
import net.minecraft.world.gen.layer.BiomeLayers;

import java.util.Random;

/**
 * Vanilla 1.8.9 overworld terrain shape (master context §13): a coarse density
 * field of 5x33x5 values per chunk, built from three octave stacks and steered
 * by a distance-weighted average of the surrounding biomes' depth and scale,
 * then interpolated down to one value per block. Positive density is stone,
 * negative is air (or water below sea level).
 *
 * <p>The biome weighting is the part our placeholder generator lacked: a
 * column's height depends on a 5x5 neighbourhood of biomes at 1:4 scale, not
 * on its own biome, which is what makes vanilla coastlines slope instead of
 * forming cliffs.
 *
 * <p>HYPOTHESIS (source_policy tier 3/4) for every constant here until
 * VanillaTerrainTest scores it against the oracle world.
 */
public final class TerrainGenerator {
    public static final int SEA_LEVEL = 63;
    /** The density grid is 5x5 columns per chunk (one per 4 blocks, plus an edge) by 33 levels (one per 8). */
    private static final int GRID_XZ = 5, GRID_Y = 33;

    // Vanilla keeps these settings as floats (ChunkProviderSettings) and widens them to double
    // only when sampling, dividing in float first for the main noise. 684.412f is not 684.412,
    // and the difference grows with distance from the origin, so the float values are used.
    private static final double COORDINATE_SCALE = 684.412f, HEIGHT_SCALE = 684.412f;
    private static final double UPPER_LIMIT_SCALE = 512.0, LOWER_LIMIT_SCALE = 512.0;
    private static final double DEPTH_NOISE_SCALE_XZ = 200.0;
    private static final double MAIN_NOISE_STEP_XZ = 684.412f / 80.0f, MAIN_NOISE_STEP_Y = 684.412f / 160.0f;
    private static final double BASE_SIZE = 8.5, STRETCH_Y = 12.0;
    /** How much the depth field shifts a column's centre height. Confirmed as a local optimum against the oracle. */
    private static final double DEPTH_WEIGHT = 0.2;

    /** Weight of each neighbour in the 5x5 biome kernel: closer biomes count for more. */
    private static final float[] PARABOLIC_FIELD = new float[25];
    static {
        for (int x = -2; x <= 2; x++)
            for (int z = -2; z <= 2; z++)
                PARABOLIC_FIELD[x + 2 + (z + 2) * 5] = 10.0f / (float) Math.sqrt((float) (x * x + z * z) + 0.2f);
    }

    private final BiomeLayers layers;
    private final BiomeRegistry biomes;
    private final NoiseGeneratorOctaves minLimitNoise, maxLimitNoise, mainNoise, depthNoise;
    private final NoiseGeneratorPerlin surfaceNoise;

    private double[] depthRegion, mainRegion, minLimitRegion, maxLimitRegion;
    private final double[] density = new double[GRID_XZ * GRID_XZ * GRID_Y];

    public TerrainGenerator(long seed, BiomeLayers layers, BiomeRegistry biomes) {
        this.layers = layers;
        this.biomes = biomes;
        Random random = new Random(seed);
        // Construction order matters: every generator draws from this one Random.
        minLimitNoise = new NoiseGeneratorOctaves(random, 16);
        maxLimitNoise = new NoiseGeneratorOctaves(random, 16);
        mainNoise = new NoiseGeneratorOctaves(random, 8);
        // The surface builder's noise (4 simplex octaves) comes next; the scale field (10)
        // is unused, but both take their draws here, before the depth field.
        surfaceNoise = new NoiseGeneratorPerlin(random, 4);
        new NoiseGeneratorOctaves(random, 10);
        depthNoise = new NoiseGeneratorOctaves(random, 16);
    }

    /** The surface builder's depth noise ("stone noise"), seeded in its vanilla place in this sequence. */
    public NoiseGeneratorPerlin surfaceNoise() { return surfaceNoise; }

    /**
     * The top solid block of every column of a chunk, indexed {@code z * 16 + x},
     * or -1 for a column that is air all the way down. Water is not counted.
     */
    public int[] terrainHeights(int chunkX, int chunkZ) {
        buildDensityField(chunkX, chunkZ);
        int[] heights = new int[256];
        java.util.Arrays.fill(heights, -1);
        // Each grid cell covers 4 blocks in x and z and 8 in y; interpolate across it.
        for (int gridX = 0; gridX < GRID_XZ - 1; gridX++) {
            for (int gridZ = 0; gridZ < GRID_XZ - 1; gridZ++) {
                int northWest = (gridX * GRID_XZ + gridZ) * GRID_Y;
                int southWest = (gridX * GRID_XZ + gridZ + 1) * GRID_Y;
                int northEast = ((gridX + 1) * GRID_XZ + gridZ) * GRID_Y;
                int southEast = ((gridX + 1) * GRID_XZ + gridZ + 1) * GRID_Y;
                for (int gridY = 0; gridY < GRID_Y - 1; gridY++) {
                    double lowNorthWest = density[northWest + gridY], lowSouthWest = density[southWest + gridY];
                    double lowNorthEast = density[northEast + gridY], lowSouthEast = density[southEast + gridY];
                    double stepNorthWest = (density[northWest + gridY + 1] - lowNorthWest) * 0.125;
                    double stepSouthWest = (density[southWest + gridY + 1] - lowSouthWest) * 0.125;
                    double stepNorthEast = (density[northEast + gridY + 1] - lowNorthEast) * 0.125;
                    double stepSouthEast = (density[southEast + gridY + 1] - lowSouthEast) * 0.125;
                    for (int subY = 0; subY < 8; subY++) {
                        double west = lowNorthWest, southWard = lowSouthWest;
                        double westStep = (lowNorthEast - lowNorthWest) * 0.25;
                        double southStep = (lowSouthEast - lowSouthWest) * 0.25;
                        for (int subX = 0; subX < 4; subX++) {
                            double alongZ = (southWard - west) * 0.25;
                            double value = west - alongZ;
                            for (int subZ = 0; subZ < 4; subZ++) {
                                if ((value += alongZ) > 0.0) {
                                    int blockX = gridX * 4 + subX, blockZ = gridZ * 4 + subZ;
                                    int y = gridY * 8 + subY;
                                    int index = blockZ * 16 + blockX;
                                    if (y > heights[index]) heights[index] = y;
                                }
                            }
                            west += westStep;
                            southWard += southStep;
                        }
                        lowNorthWest += stepNorthWest;
                        lowSouthWest += stepSouthWest;
                        lowNorthEast += stepNorthEast;
                        lowSouthEast += stepSouthEast;
                    }
                }
            }
        }
        return heights;
    }

    /**
     * The chunk's raw shape (vanilla setBlocksInChunk): stone wherever the
     * interpolated density is positive, water below sea level elsewhere, air above.
     */
    public void fillChunk(int chunkX, int chunkZ, ChunkPrimer primer, int stone, int water) {
        buildDensityField(chunkX, chunkZ);
        for (int gridX = 0; gridX < GRID_XZ - 1; gridX++) {
            for (int gridZ = 0; gridZ < GRID_XZ - 1; gridZ++) {
                int northWest = (gridX * GRID_XZ + gridZ) * GRID_Y;
                int southWest = (gridX * GRID_XZ + gridZ + 1) * GRID_Y;
                int northEast = ((gridX + 1) * GRID_XZ + gridZ) * GRID_Y;
                int southEast = ((gridX + 1) * GRID_XZ + gridZ + 1) * GRID_Y;
                for (int gridY = 0; gridY < GRID_Y - 1; gridY++) {
                    double lowNorthWest = density[northWest + gridY], lowSouthWest = density[southWest + gridY];
                    double lowNorthEast = density[northEast + gridY], lowSouthEast = density[southEast + gridY];
                    double stepNorthWest = (density[northWest + gridY + 1] - lowNorthWest) * 0.125;
                    double stepSouthWest = (density[southWest + gridY + 1] - lowSouthWest) * 0.125;
                    double stepNorthEast = (density[northEast + gridY + 1] - lowNorthEast) * 0.125;
                    double stepSouthEast = (density[southEast + gridY + 1] - lowSouthEast) * 0.125;
                    for (int subY = 0; subY < 8; subY++) {
                        double west = lowNorthWest, southWard = lowSouthWest;
                        double westStep = (lowNorthEast - lowNorthWest) * 0.25;
                        double southStep = (lowSouthEast - lowSouthWest) * 0.25;
                        int y = gridY * 8 + subY;
                        for (int subX = 0; subX < 4; subX++) {
                            double alongZ = (southWard - west) * 0.25;
                            double value = west - alongZ;
                            for (int subZ = 0; subZ < 4; subZ++) {
                                int blockX = gridX * 4 + subX, blockZ = gridZ * 4 + subZ;
                                if ((value += alongZ) > 0.0) primer.set(blockX, y, blockZ, stone);
                                else if (y < SEA_LEVEL) primer.set(blockX, y, blockZ, water);
                            }
                            west += westStep;
                            southWard += southStep;
                        }
                        lowNorthWest += stepNorthWest;
                        lowSouthWest += stepSouthWest;
                        lowNorthEast += stepNorthEast;
                        lowSouthEast += stepSouthEast;
                    }
                }
            }
        }
    }

    /** Fills {@link #density} for one chunk. */
    private void buildDensityField(int chunkX, int chunkZ) {
        // The kernel needs two extra biome cells on every side of the chunk's 5x5 grid.
        int[] biomeGrid = layers.coarse().getInts(chunkX * 4 - 2, chunkZ * 4 - 2, 10, 10);
        int noiseX = chunkX * 4, noiseZ = chunkZ * 4;
        depthRegion = depthNoise.generate(depthRegion, noiseX, noiseZ, GRID_XZ, GRID_XZ,
            DEPTH_NOISE_SCALE_XZ, DEPTH_NOISE_SCALE_XZ);
        mainRegion = mainNoise.generate(mainRegion, noiseX, 0, noiseZ, GRID_XZ, GRID_Y, GRID_XZ,
            MAIN_NOISE_STEP_XZ, MAIN_NOISE_STEP_Y, MAIN_NOISE_STEP_XZ);
        minLimitRegion = minLimitNoise.generate(minLimitRegion, noiseX, 0, noiseZ, GRID_XZ, GRID_Y, GRID_XZ,
            COORDINATE_SCALE, HEIGHT_SCALE, COORDINATE_SCALE);
        maxLimitRegion = maxLimitNoise.generate(maxLimitRegion, noiseX, 0, noiseZ, GRID_XZ, GRID_Y, GRID_XZ,
            COORDINATE_SCALE, HEIGHT_SCALE, COORDINATE_SCALE);

        int flatIndex = 0, columnIndex = 0;
        for (int gridX = 0; gridX < GRID_XZ; gridX++) {
            for (int gridZ = 0; gridZ < GRID_XZ; gridZ++) {
                // Vanilla accumulates the kernel in float; the rounding shows up in block heights.
                float scaleSum = 0f, depthSum = 0f, weightSum = 0f;
                Biome centre = biomeAt(biomeGrid, gridX + 2, gridZ + 2);
                float centreDepth = (float) centre.depth();
                for (int offsetX = -2; offsetX <= 2; offsetX++) {
                    for (int offsetZ = -2; offsetZ <= 2; offsetZ++) {
                        Biome neighbour = biomeAt(biomeGrid, gridX + offsetX + 2, gridZ + offsetZ + 2);
                        float depth = (float) neighbour.depth();
                        float scale = (float) neighbour.scale();
                        float weight = PARABOLIC_FIELD[offsetX + 2 + (offsetZ + 2) * 5] / (depth + 2.0f);
                        // A neighbour higher than the centre counts for half, so hills do not bleed downhill.
                        if (depth > centreDepth) weight /= 2.0f;
                        scaleSum += scale * weight;
                        depthSum += depth * weight;
                        weightSum += weight;
                    }
                }
                scaleSum /= weightSum;
                depthSum /= weightSum;
                double blendedScale = scaleSum * 0.9f + 0.1f;
                double blendedDepth = (depthSum * 4.0f - 1.0f) / 8.0f;

                double depthValue = depthRegion[columnIndex++] / 8000.0;
                if (depthValue < 0.0) depthValue = -depthValue * 0.3;
                depthValue = depthValue * 3.0 - 2.0;
                if (depthValue < 0.0) {
                    depthValue /= 2.0;
                    if (depthValue < -1.0) depthValue = -1.0;
                    depthValue = depthValue / 1.4 / 2.0;
                } else {
                    if (depthValue > 1.0) depthValue = 1.0;
                    depthValue /= 8.0;
                }
                blendedDepth += depthValue * DEPTH_WEIGHT;
                blendedDepth = blendedDepth * BASE_SIZE / 8.0;
                double centreLevel = BASE_SIZE + blendedDepth * 4.0;

                for (int gridY = 0; gridY < GRID_Y; gridY++) {
                    // How far this level sits from the column's centre height, stretched by the blended scale.
                    double offset = (gridY - centreLevel) * STRETCH_Y * 128.0 / 256.0 / blendedScale;
                    if (offset < 0.0) offset *= 4.0;
                    double low = minLimitRegion[flatIndex] / LOWER_LIMIT_SCALE;
                    double high = maxLimitRegion[flatIndex] / UPPER_LIMIT_SCALE;
                    double blend = (mainRegion[flatIndex] / 10.0 + 1.0) / 2.0;
                    double value = clampedBlend(low, high, blend) - offset;
                    // The top four levels fade to air so the world always ends in sky.
                    if (gridY > 29) {
                        double fade = (gridY - 29) / 3.0f;
                        value = value * (1.0 - fade) + -10.0 * fade;
                    }
                    density[flatIndex++] = value;
                }
            }
        }
    }

    private Biome biomeAt(int[] grid, int x, int z) {
        Biome biome = biomes.byId(grid[x + z * 10]);
        return biome != null ? biome : biomes.get("minecraft:ocean");
    }

    private static double clampedBlend(double low, double high, double blend) {
        if (blend < 0.0) return low;
        if (blend > 1.0) return high;
        return low + (high - low) * blend;
    }
}
