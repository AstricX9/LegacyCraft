package net.minecraft.world.gen;

import java.util.Random;

/**
 * Nether terrain (1.8.9 ChunkProviderHell.provideChunk, HYPOTHESIS:
 * recalled): a 5x17x5 density field from three blended octave noises,
 * pushed solid near the bedrock floor and roof and shaped by a cosine over
 * height, interpolated into netherrack with a lava sea below y=32; then the
 * surface pass (soul sand and gravel patches near y=64, lava filling hollows
 * below 64, ragged bedrock at both ends); then the Nether's caves and the
 * fortresses' starts ({@link NetherFortress}).
 */
public final class NetherGenerator {
    private final GenBlocks b;
    private final long seed;
    private final Random rand;
    private final NoiseGeneratorOctaves noise1, noise2, noise3, slowsandGravel, exclusivity, noise6, noise7;
    private final MapGenCavesHell caves;
    private final NetherFortress fortresses;
    private double[] field, data1, data2, data3, data4, data5, slowsand, gravel, depth;

    public NetherGenerator(GenBlocks blocks, long seed) {
        this.b = blocks;
        this.seed = seed;
        rand = new Random(seed);
        noise1 = new NoiseGeneratorOctaves(rand, 16);
        noise2 = new NoiseGeneratorOctaves(rand, 16);
        noise3 = new NoiseGeneratorOctaves(rand, 8);
        slowsandGravel = new NoiseGeneratorOctaves(rand, 4);
        exclusivity = new NoiseGeneratorOctaves(rand, 4);
        noise6 = new NoiseGeneratorOctaves(rand, 10);
        noise7 = new NoiseGeneratorOctaves(rand, 16);
        caves = new MapGenCavesHell(blocks, seed);
        fortresses = new NetherFortress(blocks, seed);
    }

    public NetherFortress fortresses() { return fortresses; }

    public synchronized ChunkPrimer generate(int chunkX, int chunkZ) {
        rand.setSeed((long) chunkX * 341873128712L + (long) chunkZ * 132897987541L);
        ChunkPrimer primer = new ChunkPrimer();
        prepareHeights(chunkX, chunkZ, primer);
        replaceBlocks(chunkX, chunkZ, primer);
        caves.generate(chunkX, chunkZ, primer);
        return primer;
    }

    /** ChunkProviderHell.func_180515_a: the density field into netherrack, lava below y=32. */
    private void prepareHeights(int chunkX, int chunkZ, ChunkPrimer primer) {
        int cells = 4, seaLevel = 32, xSize = 5, ySize = 17, zSize = 5;
        field = densityField(field, chunkX * cells, 0, chunkZ * cells, xSize, ySize, zSize);
        for (int cx = 0; cx < cells; cx++)
            for (int cz = 0; cz < cells; cz++)
                for (int cy = 0; cy < 16; cy++) {
                    double d1 = field[((cx) * zSize + cz) * ySize + cy];
                    double d2 = field[((cx) * zSize + cz + 1) * ySize + cy];
                    double d3 = field[((cx + 1) * zSize + cz) * ySize + cy];
                    double d4 = field[((cx + 1) * zSize + cz + 1) * ySize + cy];
                    double d5 = (field[((cx) * zSize + cz) * ySize + cy + 1] - d1) * 0.125;
                    double d6 = (field[((cx) * zSize + cz + 1) * ySize + cy + 1] - d2) * 0.125;
                    double d7 = (field[((cx + 1) * zSize + cz) * ySize + cy + 1] - d3) * 0.125;
                    double d8 = (field[((cx + 1) * zSize + cz + 1) * ySize + cy + 1] - d4) * 0.125;
                    for (int iy = 0; iy < 8; iy++) {
                        double d10 = d1, d11 = d2;
                        double d12 = (d3 - d1) * 0.25, d13 = (d4 - d2) * 0.25;
                        for (int ix = 0; ix < 4; ix++) {
                            double d15 = d10, d16 = (d11 - d10) * 0.25;
                            for (int iz = 0; iz < 4; iz++) {
                                int state = 0;
                                if (cy * 8 + iy < seaLevel) state = b.LAVA;
                                if (d15 > 0.0) state = b.NETHERRACK;
                                primer.set(ix + cx * 4, iy + cy * 8, iz + cz * 4, state);
                                d15 += d16;
                            }
                            d10 += d12;
                            d11 += d13;
                        }
                        d1 += d5; d2 += d6; d3 += d7; d4 += d8;
                    }
                }
    }

    /** ChunkProviderHell.initializeNoiseField. */
    private double[] densityField(double[] out, int x, int y, int z, int xSize, int ySize, int zSize) {
        if (out == null) out = new double[xSize * ySize * zSize];
        double horizontal = 684.412, vertical = 2053.236;
        data4 = noise6.generate(data4, x, y, z, xSize, 1, zSize, 1.0, 0.0, 1.0);
        data5 = noise7.generate(data5, x, y, z, xSize, 1, zSize, 100.0, 0.0, 100.0);
        data1 = noise3.generate(data1, x, y, z, xSize, ySize, zSize, horizontal / 80.0, vertical / 60.0, horizontal / 80.0);
        data2 = noise1.generate(data2, x, y, z, xSize, ySize, zSize, horizontal, vertical, horizontal);
        data3 = noise2.generate(data3, x, y, z, xSize, ySize, zSize, horizontal, vertical, horizontal);
        double[] shape = new double[ySize];
        for (int j = 0; j < ySize; j++) {
            shape[j] = Math.cos(j * Math.PI * 6.0 / ySize) * 2.0;
            double d2 = j;
            if (j > ySize / 2) d2 = ySize - 1 - j;
            if (d2 < 4.0) {
                d2 = 4.0 - d2;
                shape[j] -= d2 * d2 * d2 * 10.0;
            }
        }
        int i = 0;
        for (int l = 0; l < xSize; l++)
            for (int i1 = 0; i1 < zSize; i1++) {
                double d3 = 0.0;
                for (int k = 0; k < ySize; k++) {
                    double d4;
                    double d5 = shape[k];
                    double d6 = data2[i] / 512.0, d7 = data3[i] / 512.0;
                    double d8 = (data1[i] / 10.0 + 1.0) / 2.0;
                    if (d8 < 0.0) d4 = d6;
                    else if (d8 > 1.0) d4 = d7;
                    else d4 = d6 + (d7 - d6) * d8;
                    d4 -= d5;
                    if (k > ySize - 4) {
                        double d9 = (float) (k - (ySize - 4)) / 3.0F;
                        d4 = d4 * (1.0 - d9) + -10.0 * d9;
                    }
                    if (k < d3) {
                        double d10 = Math.max(0.0, Math.min(1.0, (d3 - k) / 4.0));
                        d4 = d4 * (1.0 - d10) + -10.0 * d10;
                    }
                    out[i++] = d4;
                }
            }
        return out;
    }

    /** ChunkProviderHell.func_180516_b: soul sand, gravel and lava at the shore line, bedrock floor and roof. */
    private void replaceBlocks(int chunkX, int chunkZ, ChunkPrimer primer) {
        int shore = 64;
        double scale = 0.03125;
        slowsand = slowsandGravel.generate(slowsand, chunkX * 16, chunkZ * 16, 0, 16, 16, 1, scale, scale, 1.0);
        gravel = slowsandGravel.generate(gravel, chunkX * 16, 109, chunkZ * 16, 16, 1, 16, scale, 1.0, scale);
        depth = exclusivity.generate(depth, chunkX * 16, chunkZ * 16, 0, 16, 16, 1, scale * 2.0, scale * 2.0, scale * 2.0);
        for (int j = 0; j < 16; j++)
            for (int k = 0; k < 16; k++) {
                boolean soulSand = slowsand[j + k * 16] + rand.nextDouble() * 0.2 > 0.0;
                boolean gravelHere = gravel[j + k * 16] + rand.nextDouble() * 0.2 > 0.0;
                int l = (int) (depth[j + k * 16] / 3.0 + 3.0 + rand.nextDouble() * 0.25);
                int run = -1;
                int top = b.NETHERRACK, filler = b.NETHERRACK;
                for (int y = 127; y >= 0; y--) {
                    if (y < 127 - rand.nextInt(5) && y > rand.nextInt(5)) {
                        int here = primer.get(k, y, j);
                        if (here != 0 && !b.isAir(here)) {
                            if (GenBlocks.id(here) == b.netherrackId) {
                                if (run == -1) {
                                    if (l <= 0) {
                                        top = 0;
                                        filler = b.NETHERRACK;
                                    } else if (y >= shore - 4 && y <= shore + 1) {
                                        top = b.NETHERRACK;
                                        filler = b.NETHERRACK;
                                        if (gravelHere) {
                                            top = b.GRAVEL;
                                            filler = b.NETHERRACK;
                                        }
                                        if (soulSand) {
                                            top = b.SOUL_SAND;
                                            filler = b.SOUL_SAND;
                                        }
                                    }
                                    if (y < shore && (top == 0 || b.isAir(top))) top = b.LAVA;
                                    run = l;
                                    primer.set(k, y, j, y >= shore - 1 ? top : filler);
                                } else if (run > 0) {
                                    run--;
                                    primer.set(k, y, j, filler);
                                }
                            }
                        } else {
                            run = -1;
                        }
                    } else {
                        primer.set(k, y, j, b.BEDROCK);
                    }
                }
            }
    }

    long seed() { return seed; }
}
