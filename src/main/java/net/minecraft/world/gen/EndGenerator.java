package net.minecraft.world.gen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The End's terrain (1.9 ChunkProviderEnd, HYPOTHESIS: recalled): a 3x33x3
 * density field from three octave noises, lifted by an island height that
 * falls off with distance from the centre, with outer islands from simplex
 * noise beyond 1024 blocks; interpolated into end stone over the void.
 * Population builds the ten obsidian spikes (WorldGenSpikes), small floating
 * islands (WorldGenEndIsland) and chorus plants on the outer islands.
 */
public final class EndGenerator {
    private final GenBlocks b;
    private final long seed;
    private final Random rand;
    private final NoiseGeneratorOctaves lperlin1, lperlin2, perlin;
    private final NoiseGeneratorSimplex islandNoise;
    private final int endStone, obsidian, bedrock, ironBars, chorusPlant, chorusFlower;
    private double[] buffer, pnr, ar, br;

    /** One obsidian spike: centre, radius and height; guarded ones have an iron bar cage around their crystal. */
    public record Spike(int x, int z, int radius, int height, boolean guarded) {}

    private final List<Spike> spikes;

    public EndGenerator(GenBlocks blocks, long seed) {
        this.b = blocks;
        this.seed = seed;
        rand = new Random(seed);
        lperlin1 = new NoiseGeneratorOctaves(rand, 16);
        lperlin2 = new NoiseGeneratorOctaves(rand, 16);
        perlin = new NoiseGeneratorOctaves(rand, 8);
        new NoiseGeneratorOctaves(rand, 10); // noiseGen5, unused but consumes the seed's randoms
        new NoiseGeneratorOctaves(rand, 16); // noiseGen6
        islandNoise = new NoiseGeneratorSimplex(rand);
        endStone = blocks.state("end_stone", 0);
        obsidian = blocks.state("obsidian", 0);
        bedrock = blocks.BEDROCK;
        ironBars = blocks.state("iron_bars", 0);
        chorusPlant = blocks.state("chorus_plant", 0);
        chorusFlower = blocks.state("chorus_flower", 0);
        spikes = spikes(seed);
    }

    /** WorldGenSpikes positions (BiomeEndDecorator.SpikeCacheLoader): ten in a ring of radius 42, sizes shuffled by the seed. */
    static List<Spike> spikes(long seed) {
        List<Integer> sizes = new ArrayList<>();
        for (int i = 0; i < 10; i++) sizes.add(i);
        Collections.shuffle(sizes, new Random(seed));
        List<Spike> result = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            int x = (int) (42.0 * Math.cos(2.0 * (-Math.PI + Math.PI / 10.0 * i)));
            int z = (int) (42.0 * Math.sin(2.0 * (-Math.PI + Math.PI / 10.0 * i)));
            int size = sizes.get(i);
            result.add(new Spike(x, z, 2 + size / 3, 76 + size * 3, size == 1 || size == 2));
        }
        return result;
    }

    public List<Spike> spikes() { return spikes; }

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

    /** ChunkProviderEnd.getIslandHeightValue: the main island's dome, raised by any outer island nearby. */
    public float islandHeight(int chunkX, int chunkZ, int offsetX, int offsetZ) {
        float fx = chunkX * 2 + offsetX, fz = chunkZ * 2 + offsetZ;
        float height = clamp(100f - (float) Math.sqrt(fx * fx + fz * fz) * 8f);
        for (int dx = -12; dx <= 12; dx++)
            for (int dz = -12; dz <= 12; dz++) {
                long ix = chunkX + dx, iz = chunkZ + dz;
                if (ix * ix + iz * iz <= 4096L || islandNoise.getValue(ix, iz) >= -0.9) continue;
                float falloff = (Math.abs(ix) * 3439f + Math.abs(iz) * 147f) % 13f + 9f;
                fx = offsetX - dx * 2;
                fz = offsetZ - dz * 2;
                height = Math.max(height, clamp(100f - (float) Math.sqrt(fx * fx + fz * fz) * falloff));
            }
        return height;
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

    public void populate(GenWorld w, int chunkX, int chunkZ) {
        int x0 = chunkX * 16, z0 = chunkZ * 16;
        popRand.setSeed(seed);
        long a = popRand.nextLong() / 2L * 2L + 1L, c = popRand.nextLong() / 2L * 2L + 1L;
        popRand.setSeed((long) chunkX * a + (long) chunkZ * c ^ seed);
        for (Spike spike : spikes)
            if (spike.x() >> 4 == chunkX && spike.z() >> 4 == chunkZ) buildSpike(w, spike);
        long distance = (long) chunkX * chunkX + (long) chunkZ * chunkZ;
        if (distance <= 4096L) return;
        if (islandHeight(chunkX, chunkZ, 1, 1) < -20f && popRand.nextInt(14) == 0) {
            smallIsland(w, x0 + popRand.nextInt(16) + 8, 55 + popRand.nextInt(16), z0 + popRand.nextInt(16) + 8);
            if (popRand.nextInt(4) == 0) smallIsland(w, x0 + popRand.nextInt(16) + 8, 55 + popRand.nextInt(16), z0 + popRand.nextInt(16) + 8);
        }
        if (islandHeight(chunkX, chunkZ, 1, 1) > 40f) {
            int count = popRand.nextInt(5);
            for (int i = 0; i < count; i++) {
                int x = x0 + popRand.nextInt(16) + 8, z = z0 + popRand.nextInt(16) + 8;
                int y = top(w, x, z);
                if (y > 0 && w.get(x, y - 1, z) == endStone) chorusTree(w, x, y, z, 8);
            }
        }
    }

    /** One above the highest non-air block, or 0 over the void. */
    private int top(GenWorld w, int x, int z) {
        for (int y = 255; y > 0; y--) if (!b.isAir(w.get(x, y - 1, z))) return y;
        return 0;
    }

    /** WorldGenSpikes.generate: an obsidian column cleared above, bedrock under its crystal, caged if guarded. */
    void buildSpike(GenWorld w, Spike spike) {
        int r = spike.radius();
        for (int x = spike.x() - r; x <= spike.x() + r; x++)
            for (int z = spike.z() - r; z <= spike.z() + r; z++) {
                int dx = x - spike.x(), dz = z - spike.z();
                if (dx * dx + dz * dz > r * r + 1) continue;
                for (int y = 0; y < 256; y++) {
                    if (y < spike.height()) w.set(x, y, z, obsidian);
                    else if (y > 65) w.set(x, y, z, 0);
                }
            }
        if (spike.guarded()) {
            for (int dx = -2; dx <= 2; dx++)
                for (int dz = -2; dz <= 2; dz++)
                    for (int dy = 0; dy <= 3; dy++)
                        if (Math.abs(dx) == 2 || Math.abs(dz) == 2 || dy == 3)
                            w.set(spike.x() + dx, spike.height() + dy, spike.z() + dz, ironBars);
        }
        w.set(spike.x(), spike.height(), spike.z(), bedrock);
        w.spawnCreature("EnderCrystal", spike.x() + 0.5, spike.height() + 1, spike.z() + 0.5, 0f);
    }

    /** WorldGenEndIsland: a shrinking disc of end stone hanging down from y. */
    void smallIsland(GenWorld w, int x, int y, int z) {
        float size = popRand.nextInt(3) + 4;
        for (int dy = 0; size > 0.5f; dy--) {
            for (int dx = (int) Math.floor(-size); dx <= Math.ceil(size); dx++)
                for (int dz = (int) Math.floor(-size); dz <= Math.ceil(size); dz++)
                    if (dx * dx + dz * dz <= (size + 1f) * (size + 1f)) w.set(x + dx, y + dy, z + dz, endStone);
            size -= popRand.nextInt(2) + 0.5f;
        }
    }

    /**
     * BlockChorusFlower.generatePlant: a stem that climbs and branches out
     * (at most {@code reach} blocks from where it started), ending in flowers.
     */
    void chorusTree(GenWorld w, int x, int y, int z, int reach) {
        w.set(x, y, z, chorusPlant);
        growChorus(w, x, y, z, x, z, reach, 0);
    }

    private void growChorus(GenWorld w, int x, int y, int z, int rootX, int rootZ, int reach, int depth) {
        int height = popRand.nextInt(4) + 1;
        if (depth == 0) height++;
        for (int i = 0; i < height; i++) {
            if (!b.isAir(w.get(x, y + i + 1, z)) || !onlyAirAround(w, x, y + i + 1, z, x, z)) return;
            w.set(x, y + i + 1, z, chorusPlant);
        }
        boolean branched = false;
        if (depth < 4) {
            int branches = popRand.nextInt(4);
            if (depth == 0) branches++;
            int[][] sides = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
            for (int i = 0; i < branches; i++) {
                int[] side = sides[popRand.nextInt(4)];
                int bx = x + side[0], by = y + height, bz = z + side[1];
                if (Math.abs(bx - rootX) < reach && Math.abs(bz - rootZ) < reach && b.isAir(w.get(bx, by, bz))
                    && b.isAir(w.get(bx, by - 1, bz)) && onlyAirAround(w, bx, by, bz, x, z)) {
                    branched = true;
                    w.set(bx, by, bz, chorusPlant);
                    growChorus(w, bx, by, bz, rootX, rootZ, reach, depth + 1);
                }
            }
        }
        if (!branched) w.set(x, y + height, z, chorusFlower | 5);
    }

    /** BlockChorusFlower.areAllNeighborsEmpty: the four horizontal neighbours are air, except the column (skipX, skipZ) it grew from. */
    private boolean onlyAirAround(GenWorld w, int x, int y, int z, int skipX, int skipZ) {
        int[][] sides = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
        for (int[] side : sides) {
            int nx = x + side[0], nz = z + side[1];
            if (nx == skipX && nz == skipZ) continue;
            if (!b.isAir(w.get(nx, y, nz))) return false;
        }
        return true;
    }
}
