package net.minecraft.world.gen;

import java.util.Random;

/**
 * The End's terrain (1.8.9 ChunkProviderEnd, HYPOTHESIS: recalled): a 3x33x3
 * density field from three octave noises, lifted by an island height that
 * falls off with distance from the centre, interpolated into end stone over
 * the void: one island and nothing else. Population (BiomeEndDecorator)
 * raises an obsidian spike with a crystal in one chunk in five, and puts
 * the ender dragon over the centre when chunk 0,0 is populated.
 */
public final class EndGenerator {
    private final GenBlocks b;
    private final long seed;
    private final Random rand;
    private final NoiseGeneratorOctaves lperlin1, lperlin2, perlin;
    private final int endStone, obsidian, bedrock;
    private double[] buffer, pnr, ar, br;

    public EndGenerator(GenBlocks blocks, long seed) {
        this.b = blocks;
        this.seed = seed;
        rand = new Random(seed);
        lperlin1 = new NoiseGeneratorOctaves(rand, 16);
        lperlin2 = new NoiseGeneratorOctaves(rand, 16);
        perlin = new NoiseGeneratorOctaves(rand, 8);
        endStone = blocks.state("end_stone", 0);
        obsidian = blocks.state("obsidian", 0);
        bedrock = blocks.BEDROCK;
    }

    public synchronized ChunkPrimer generate(int chunkX, int chunkZ) {
        rand.setSeed((long) chunkX * 341873128712L + (long) chunkZ * 132897987541L);
        ChunkPrimer primer = new ChunkPrimer();
        buffer = heights(buffer, chunkX * 2, 0, chunkZ * 2, 3, 33, 3);
        for (int cx = 0; cx < 2; cx++)
            for (int cz = 0; cz < 2; cz++)
                for (int cy = 0; cy < 32; cy++) {
                    double d1 = buffer[(cx * 3 + cz) * 33 + cy];
                    double d2 = buffer[(cx * 3 + cz + 1) * 33 + cy];
                    double d3 = buffer[((cx + 1) * 3 + cz) * 33 + cy];
                    double d4 = buffer[((cx + 1) * 3 + cz + 1) * 33 + cy];
                    double d5 = (buffer[(cx * 3 + cz) * 33 + cy + 1] - d1) * 0.25;
                    double d6 = (buffer[(cx * 3 + cz + 1) * 33 + cy + 1] - d2) * 0.25;
                    double d7 = (buffer[((cx + 1) * 3 + cz) * 33 + cy + 1] - d3) * 0.25;
                    double d8 = (buffer[((cx + 1) * 3 + cz + 1) * 33 + cy + 1] - d4) * 0.25;
                    for (int iy = 0; iy < 4; iy++) {
                        double d10 = d1, d11 = d2, d12 = (d3 - d1) * 0.125, d13 = (d4 - d2) * 0.125;
                        for (int ix = 0; ix < 8; ix++) {
                            double d15 = d10, d16 = (d11 - d10) * 0.125;
                            for (int iz = 0; iz < 8; iz++) {
                                if (d15 > 0.0) primer.set(ix + cx * 8, iy + cy * 4, iz + cz * 8, endStone);
                                d15 += d16;
                            }
                            d10 += d12;
                            d11 += d13;
                        }
                        d1 += d5; d2 += d6; d3 += d7; d4 += d8;
                    }
                }
        return primer;
    }

    /** ChunkProviderEnd.initializeNoiseField's island height: a dome falling off with distance from 0,0. */
    public float islandHeight(int chunkX, int chunkZ, int offsetX, int offsetZ) {
        float fx = chunkX * 2 + offsetX, fz = chunkZ * 2 + offsetZ;
        return clamp(100f - (float) Math.sqrt(fx * fx + fz * fz) * 8f);
    }

    private static float clamp(float value) { return Math.max(-100f, Math.min(80f, value)); }

    private double[] heights(double[] out, int x, int y, int z, int sx, int sy, int sz) {
        if (out == null) out = new double[sx * sy * sz];
        double d0 = 684.412 * 2.0, d1 = 684.412;
        pnr = perlin.generate(pnr, x, y, z, sx, sy, sz, d0 / 80.0, d1 / 160.0, d0 / 80.0);
        ar = lperlin1.generate(ar, x, y, z, sx, sy, sz, d0, d1, d0);
        br = lperlin2.generate(br, x, y, z, sx, sy, sz, d0, d1, d0);
        int chunkX = x / 2, chunkZ = z / 2, i = 0;
        for (int ix = 0; ix < sx; ix++)
            for (int iz = 0; iz < sz; iz++) {
                float island = islandHeight(chunkX, chunkZ, ix, iz);
                for (int iy = 0; iy < sy; iy++) {
                    double low = ar[i] / 512.0, high = br[i] / 512.0, mix = (pnr[i] / 10.0 + 1.0) / 2.0;
                    double d = mix < 0 ? low : mix > 1 ? high : low + (high - low) * mix;
                    d = d - 8.0 + island;
                    if (iy > sy / 2 - 2) {
                        double t = Math.max(0, Math.min(1, (iy - (sy / 2 - 2)) / 64.0));
                        d = d * (1.0 - t) - 3000.0 * t;
                    }
                    if (iy < 8) {
                        double t = (8 - iy) / 7.0;
                        d = d * (1.0 - t) - 30.0 * t;
                    }
                    out[i++] = d;
                }
            }
        return out;
    }

    // ---- population --------------------------------------------------------------------------

    private final Random popRand = new Random();

    /** BiomeEndDecorator.genDecorations: a spike in one chunk in five, and the dragon in chunk 0,0. */
    public void populate(GenWorld w, int chunkX, int chunkZ) {
        int x0 = chunkX * 16, z0 = chunkZ * 16;
        popRand.setSeed(seed);
        long a = popRand.nextLong() / 2L * 2L + 1L, c = popRand.nextLong() / 2L * 2L + 1L;
        popRand.setSeed((long) chunkX * a + (long) chunkZ * c ^ seed);
        if (popRand.nextInt(5) == 0) {
            int x = x0 + popRand.nextInt(16) + 8, z = z0 + popRand.nextInt(16) + 8;
            spike(w, x, top(w, x, z), z);
        }
        if (chunkX == 0 && chunkZ == 0) w.spawnCreature("EnderDragon", 0, 128, 0, popRand.nextFloat() * 360f);
    }

    /** One above the highest non-air block, or 0 over the void (getTopSolidOrLiquidBlock). */
    private int top(GenWorld w, int x, int z) {
        for (int y = 255; y > 0; y--) if (!b.isAir(w.get(x, y - 1, z))) return y;
        return 0;
    }

    /**
     * WorldGenSpikes.generate: on end stone, a 6-37 high obsidian column of
     * radius 1-4 (only if end stone lies under all of it), bedrock on top
     * and an ender crystal standing on that.
     */
    boolean spike(GenWorld w, int x, int y, int z) {
        if (y <= 0 || !b.isAir(w.get(x, y, z)) || w.get(x, y - 1, z) != endStone) return false;
        int height = popRand.nextInt(32) + 6, r = popRand.nextInt(4) + 1;
        for (int sx = x - r; sx <= x + r; sx++)
            for (int sz = z - r; sz <= z + r; sz++) {
                int dx = sx - x, dz = sz - z;
                if (dx * dx + dz * dz <= r * r + 1 && w.get(sx, y - 1, sz) != endStone) return false;
            }
        for (int sy = y; sy < y + height && sy < 256; sy++)
            for (int sx = x - r; sx <= x + r; sx++)
                for (int sz = z - r; sz <= z + r; sz++) {
                    int dx = sx - x, dz = sz - z;
                    if (dx * dx + dz * dz <= r * r + 1) w.set(sx, sy, sz, obsidian);
                }
        w.set(x, y + height, z, bedrock);
        w.spawnCreature("EnderCrystal", x + 0.5, y + height + 1, z + 0.5, popRand.nextFloat() * 360f);
        return true;
    }
}
