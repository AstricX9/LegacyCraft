package net.minecraft.world.gen.layer;

/**
 * One step of the 1.8.9 biome map pipeline (master context §14). Each layer
 * produces a rectangle of biome (or intermediate category) IDs at its own
 * scale, reading from its parent layer; the chain starts as a coarse
 * land/ocean sketch and is zoomed, eroded and refined until one value per
 * block remains.
 *
 * <p>The per-cell randomness is position-seeded, so any rectangle can be
 * generated independently and always gives the same answer. Each layer mixes
 * its own base seed with the world seed once ({@link #initWorldGenSeed}), then
 * mixes in the cell coordinates before each decision
 * ({@link #initChunkSeed}). HYPOTHESIS: the mixing constants and order are
 * the documented 1.8 layer algorithm; the whole chain is checked against a
 * real 1.8.9 world by VanillaBiomeMapTest.
 */
public abstract class GenLayer {
    private static final long MULTIPLIER = 6364136223846793005L;
    private static final long INCREMENT = 1442695040888963407L;

    protected GenLayer parent;
    private final long baseSeed;
    private long worldGenSeed;
    private long chunkSeed;

    protected GenLayer(long baseSeed) {
        long seed = baseSeed;
        seed = mix(seed, baseSeed);
        seed = mix(seed, baseSeed);
        seed = mix(seed, baseSeed);
        this.baseSeed = seed;
    }

    private static long mix(long seed, long addend) {
        return seed * (seed * MULTIPLIER + INCREMENT) + addend;
    }

    /** Ties the whole chain to one world seed. */
    public void initWorldGenSeed(long worldSeed) {
        worldGenSeed = worldSeed;
        if (parent != null) parent.initWorldGenSeed(worldSeed);
        worldGenSeed = mix(worldGenSeed, baseSeed);
        worldGenSeed = mix(worldGenSeed, baseSeed);
        worldGenSeed = mix(worldGenSeed, baseSeed);
    }

    /** Seeds the randomness for one cell of this layer's grid. */
    protected void initChunkSeed(long x, long z) {
        chunkSeed = worldGenSeed;
        chunkSeed = mix(chunkSeed, x);
        chunkSeed = mix(chunkSeed, z);
        chunkSeed = mix(chunkSeed, x);
        chunkSeed = mix(chunkSeed, z);
    }

    protected int nextInt(int bound) {
        int value = (int) ((chunkSeed >> 24) % bound);
        if (value < 0) value += bound;
        chunkSeed = mix(chunkSeed, worldGenSeed);
        return value;
    }

    /** IDs for a rectangle of this layer's grid, row-major, {@code width * height} values. */
    public abstract int[] getInts(int areaX, int areaZ, int width, int height);

    protected int selectRandom(int... choices) { return choices[nextInt(choices.length)]; }

    /** The value that appears at least twice among the four corners, else a random one. */
    protected int selectModeOrRandom(int a, int b, int c, int d) {
        if (b == c && c == d) return b;
        if (a == b && a == c) return a;
        if (a == b && a == d) return a;
        if (a == c && a == d) return a;
        if (a == b && c != d) return a;
        if (a == c && b != d) return a;
        if (a == d && b != c) return a;
        if (b == c && a != d) return b;
        if (b == d && a != c) return b;
        if (c == d && a != b) return c;
        return selectRandom(a, b, c, d);
    }

    /** Ocean biomes are handled specially by several layers. */
    protected static boolean isOcean(int id) { return id == Biomes.OCEAN || id == Biomes.DEEP_OCEAN || id == Biomes.FROZEN_OCEAN; }

    /** Legacy biome IDs and the intermediate climate categories the early layers use. */
    public static final class Biomes {
        public static final int OCEAN = 0, PLAINS = 1, DESERT = 2, EXTREME_HILLS = 3, FOREST = 4, TAIGA = 5,
            SWAMPLAND = 6, RIVER = 7, FROZEN_OCEAN = 10, FROZEN_RIVER = 11, ICE_PLAINS = 12, ICE_MOUNTAINS = 13,
            MUSHROOM_ISLAND = 14, MUSHROOM_ISLAND_SHORE = 15, BEACH = 16, DESERT_HILLS = 17, FOREST_HILLS = 18,
            TAIGA_HILLS = 19, EXTREME_HILLS_EDGE = 20, JUNGLE = 21, JUNGLE_HILLS = 22, JUNGLE_EDGE = 23,
            DEEP_OCEAN = 24, STONE_BEACH = 25, COLD_BEACH = 26, BIRCH_FOREST = 27, BIRCH_FOREST_HILLS = 28,
            ROOFED_FOREST = 29, COLD_TAIGA = 30, COLD_TAIGA_HILLS = 31, MEGA_TAIGA = 32, MEGA_TAIGA_HILLS = 33,
            EXTREME_HILLS_PLUS = 34, SAVANNA = 35, SAVANNA_PLATEAU = 36, MESA = 37, MESA_PLATEAU_F = 38,
            MESA_PLATEAU = 39;
        /** Climate categories used between the sketch and the biome layer. */
        public static final int WARM = 1, MEDIUM = 2, COLD = 3, ICY = 4;
        private Biomes() {}
    }
}
