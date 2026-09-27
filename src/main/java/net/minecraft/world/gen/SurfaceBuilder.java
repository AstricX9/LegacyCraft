package net.minecraft.world.gen;

import net.minecraft.world.biome.Biome;

import java.util.Arrays;
import java.util.Random;

/**
 * Replaces a chunk's raw stone with each biome's surface: bedrock at the
 * bottom, the top block and a few layers of filler under open sky, gravel
 * on deep sea floors, sandstone under sand, plus the per-biome variations
 * (extreme hills stone and gravel, mega taiga podzol, savanna M stone and
 * coarse dirt, swamp puddles, mesa clay bands and bryce spires).
 *
 * <p>Vanilla quirks kept on purpose (HYPOTHESIS, recalled from 1.8.9's
 * generateBiomeTerrain and the biome overrides): the column loop runs local
 * z in the outer loop, and the biome hook receives the column's x and z
 * swapped (so the swamp and mesa noises sample transposed coordinates),
 * while every block write lands on the right column.
 */
public final class SurfaceBuilder {
    private static final int SEA_LEVEL = 63;
    /** BiomeGenBase's shared noises, seeded with fixed constants rather than the world seed. */
    public static final NoiseGeneratorPerlin TEMPERATURE_NOISE = new NoiseGeneratorPerlin(new Random(1234L), 1);
    public static final NoiseGeneratorPerlin GRASS_COLOR_NOISE = new NoiseGeneratorPerlin(new Random(2345L), 1);

    private final GenBlocks b;
    private final NoiseGeneratorPerlin stoneNoise;
    private double[] noise = new double[256];

    // Mesa state: the clay band table and its offset noise come from the world seed; the bryce
    // pillar noises are seeded with 0 (vanilla seeds them with the previously stored seed, which is
    // still 0 the first time a mesa is generated in a session).
    private final int[] clayBands = new int[64];
    private final NoiseGeneratorPerlin clayBandNoise, pillarNoise, pillarRoofNoise;

    /** @param stoneNoiseGenerator the 4-octave surface noise, built in its place in the terrain noise sequence */
    public SurfaceBuilder(GenBlocks blocks, long seed, NoiseGeneratorPerlin stoneNoiseGenerator) {
        this.b = blocks;
        this.stoneNoise = stoneNoiseGenerator;
        Random bandRandom = new Random(seed);
        Arrays.fill(clayBands, b.HARDENED_CLAY);
        clayBandNoise = new NoiseGeneratorPerlin(bandRandom, 1);
        buildClayBands(bandRandom);
        Random pillarRandom = new Random(0L);
        pillarNoise = new NoiseGeneratorPerlin(pillarRandom, 4);
        pillarRoofNoise = new NoiseGeneratorPerlin(pillarRandom, 1);
    }

    /**
     * @param rand   the chunk Random, already used by the density fill
     * @param biomes the chunk's biomes, index {@code z * 16 + x}
     */
    public void build(int chunkX, int chunkZ, ChunkPrimer primer, Random rand, Biome[] biomes) {
        noise = stoneNoise.getRegion(noise, chunkX * 16, chunkZ * 16, 16, 16, 0.0625, 0.0625, 1.0);
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                Biome biome = biomes[j + i * 16];
                // (x, z) as vanilla passes them to the biome: swapped relative to the column (j, i).
                buildColumn(biome, primer, rand, chunkX * 16 + i, chunkZ * 16 + j, noise[j + i * 16]);
            }
        }
    }

    private void buildColumn(Biome biome, ChunkPrimer primer, Random rand, int x, int z, double noise) {
        int id = biome.id();
        int top = biome.topBlock().packed(), filler = biome.fillerBlock().packed();
        switch (id) {
            case 3, 20, 34, 131, 162 -> { // extreme hills: 0 plain, 1 with trees (edge, plus), 2 mutated
                int type = id == 3 ? 0 : id == 20 || id == 34 ? 1 : 2;
                top = b.GRASS; filler = b.DIRT;
                if ((noise < -1.0 || noise > 2.0) && type == 2) top = filler = b.GRAVEL;
                else if (noise > 1.0 && type != 1) top = filler = b.STONE;
            }
            case 32, 33, 160, 161 -> { // mega taiga
                top = b.GRASS; filler = b.DIRT;
                if (noise > 1.75) top = b.COARSE_DIRT;
                else if (noise > -0.95) top = b.PODZOL;
            }
            case 163, 164 -> { // savanna M
                top = b.GRASS; filler = b.DIRT;
                if (noise > 1.75) top = filler = b.STONE;
                else if (noise > -0.5) top = b.COARSE_DIRT;
            }
            case 6, 134 -> swampPuddle(primer, x, z);
            case 37, 38, 39, 165, 166, 167 -> {
                mesa(id, primer, rand, x, z, noise);
                return;
            }
            default -> { }
        }
        generateBiomeTerrain(biome, top, filler, primer, rand, x, z, noise);
    }

    /** Swamps turn some dry y=62 ground into shallow water, with the odd lily pad on it. */
    private void swampPuddle(ChunkPrimer primer, int x, int z) {
        double value = GRASS_COLOR_NOISE.getValue(x * 0.25, z * 0.25);
        if (value <= 0.0) return;
        int localX = z & 15, localZ = x & 15;
        for (int y = 255; y >= 0; y--) {
            int state = primer.get(localX, y, localZ);
            if (!b.isAir(state)) {
                if (y == 62 && GenBlocks.id(state) != b.waterId) {
                    primer.set(localX, y, localZ, b.WATER);
                    if (value < 0.12) primer.set(localX, y + 1, localZ, b.WATERLILY);
                }
                break;
            }
        }
    }

    /** The shared surface rule (vanilla generateBiomeTerrain). {@code top} of -1 means none. */
    private void generateBiomeTerrain(Biome biome, int biomeTop, int biomeFiller, ChunkPrimer primer, Random rand,
                                      int x, int z, double noise) {
        int top = biomeTop, filler = biomeFiller;
        int depth = -1;
        int surfaceDepth = (int) (noise / 3.0 + 3.0 + rand.nextDouble() * 0.25);
        int localX = z & 15, localZ = x & 15;
        for (int y = 255; y >= 0; y--) {
            if (y <= rand.nextInt(5)) {
                primer.set(localX, y, localZ, b.BEDROCK);
                continue;
            }
            int state = primer.get(localX, y, localZ);
            if (b.isAir(state)) {
                depth = -1;
            } else if (GenBlocks.id(state) == b.stoneId) {
                if (depth == -1) {
                    if (surfaceDepth <= 0) {
                        top = -1;
                        filler = b.STONE;
                    } else if (y >= SEA_LEVEL - 4 && y <= SEA_LEVEL + 1) {
                        top = biomeTop;
                        filler = biomeFiller;
                    }
                    if (y < SEA_LEVEL && (top == -1 || b.isAir(top)))
                        top = biome.temperature() < 0.15f ? b.ICE : b.WATER;
                    depth = surfaceDepth;
                    if (y >= SEA_LEVEL - 1) {
                        primer.set(localX, y, localZ, top == -1 ? b.AIR : top);
                    } else if (y < SEA_LEVEL - 7 - surfaceDepth) {
                        top = -1;
                        filler = b.STONE;
                        primer.set(localX, y, localZ, b.GRAVEL);
                    } else {
                        primer.set(localX, y, localZ, filler);
                    }
                } else if (depth > 0) {
                    depth--;
                    primer.set(localX, y, localZ, filler);
                    if (depth == 0 && GenBlocks.id(filler) == b.sandId) {
                        depth = rand.nextInt(4) + Math.max(0, y - SEA_LEVEL);
                        filler = GenBlocks.meta(filler) == 1 ? b.RED_SANDSTONE : b.SANDSTONE;
                    }
                }
            }
        }
    }

    // ---- Mesa ------------------------------------------------------------------------------------

    private void buildClayBands(Random random) {
        for (int i = 0; i < 64; i++) {
            i += random.nextInt(5) + 1;
            if (i < 64) clayBands[i] = b.ORANGE_CLAY;
        }
        paintBands(random, b.YELLOW_CLAY, 1);
        paintBands(random, b.BROWN_CLAY, 2);
        paintBands(random, b.RED_CLAY, 1);
        int whiteBands = random.nextInt(3) + 3;
        int position = 0;
        for (int i = 0; i < whiteBands; i++) {
            position += random.nextInt(16) + 4;
            for (int k = 0; position + k < 64 && k < 1; k++) {
                clayBands[position + k] = b.WHITE_CLAY;
                if (position + k > 1 && random.nextBoolean()) clayBands[position + k - 1] = b.SILVER_CLAY;
                if (position + k < 63 && random.nextBoolean()) clayBands[position + k + 1] = b.SILVER_CLAY;
            }
        }
    }

    private void paintBands(Random random, int clay, int minWidth) {
        int count = random.nextInt(4) + 2;
        for (int i = 0; i < count; i++) {
            int width = random.nextInt(3) + minWidth;
            int start = random.nextInt(64);
            for (int k = 0; start + k < 64 && k < width; k++) clayBands[start + k] = clay;
        }
    }

    /** The clay band at a height; the offset noise samples (x, x), a vanilla slip that makes bands vary along x only. */
    private int clayBand(int x, int y) {
        int offset = (int) Math.round(clayBandNoise.getValue(x / 512.0, x / 512.0) * 2.0);
        return clayBands[(y + offset + 64) % 64];
    }

    private void mesa(int id, ChunkPrimer primer, Random rand, int x, int z, double noise) {
        boolean bryce = id == 165;
        boolean forest = id == 38 || id == 166;
        double pillarTop = 0.0;
        if (bryce) {
            int px = (x & -16) + (z & 15), pz = (z & -16) + (x & 15);
            double pillar = Math.min(Math.abs(noise), pillarNoise.getValue(px * 0.25, pz * 0.25));
            if (pillar > 0.0) {
                double roof = Math.abs(pillarRoofNoise.getValue(px * 0.001953125, pz * 0.001953125));
                pillarTop = pillar * pillar * 2.5;
                double cap = Math.ceil(roof * 50.0) + 14.0;
                if (pillarTop > cap) pillarTop = cap;
                pillarTop += 64.0;
            }
        }
        int localX = z & 15, localZ = x & 15;
        int top = b.WHITE_CLAY, filler = b.WHITE_CLAY;
        int surfaceDepth = (int) (noise / 3.0 + 3.0 + rand.nextDouble() * 0.25);
        boolean hardened = Math.cos(noise / 3.0 * Math.PI) > 0.0;
        int depth = -1;
        boolean onSand = false;
        for (int y = 255; y >= 0; y--) {
            if (b.isAir(primer.get(localX, y, localZ)) && y < (int) pillarTop) primer.set(localX, y, localZ, b.STONE);
            if (y <= rand.nextInt(5)) {
                primer.set(localX, y, localZ, b.BEDROCK);
                continue;
            }
            int state = primer.get(localX, y, localZ);
            if (b.isAir(state)) {
                depth = -1;
            } else if (GenBlocks.id(state) == b.stoneId) {
                if (depth == -1) {
                    onSand = false;
                    if (surfaceDepth <= 0) {
                        top = -1;
                        filler = b.STONE;
                    } else if (y >= SEA_LEVEL - 4 && y <= SEA_LEVEL + 1) {
                        top = b.WHITE_CLAY;
                        filler = b.WHITE_CLAY;
                    }
                    if (y < SEA_LEVEL && (top == -1 || b.isAir(top))) top = b.WATER;
                    depth = surfaceDepth + Math.max(0, y - SEA_LEVEL);
                    if (y >= SEA_LEVEL - 1) {
                        if (forest && y > 86 + surfaceDepth * 2) {
                            primer.set(localX, y, localZ, hardened ? b.COARSE_DIRT : b.GRASS);
                        } else if (y > SEA_LEVEL + 3 + surfaceDepth) {
                            int clay = y >= 64 && y <= 127 ? hardened ? b.HARDENED_CLAY : clayBand(x, y) : b.ORANGE_CLAY;
                            primer.set(localX, y, localZ, clay);
                        } else {
                            primer.set(localX, y, localZ, b.RED_SAND);
                            onSand = true;
                        }
                    } else {
                        primer.set(localX, y, localZ, filler == b.WHITE_CLAY ? b.ORANGE_CLAY : filler);
                    }
                } else if (depth > 0) {
                    depth--;
                    primer.set(localX, y, localZ, onSand ? b.ORANGE_CLAY : clayBand(x, y));
                }
            }
        }
    }
}
