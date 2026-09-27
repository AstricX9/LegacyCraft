package net.minecraft.world.gen;

/**
 * A chunk's blocks while it is being generated: packed (id << 4 | metadata)
 * states indexed {@code x << 12 | z << 8 | y}, like vanilla's ChunkPrimer.
 * Generation stages read and write this before the chunk is published.
 */
public final class ChunkPrimer {
    private final int[] states = new int[16 * 16 * 256];

    private static int index(int x, int y, int z) { return x << 12 | z << 8 | y; }

    public int get(int x, int y, int z) {
        if (y < 0 || y >= 256) return 0;
        return states[index(x, y, z)];
    }

    public void set(int x, int y, int z, int packed) {
        if (y < 0 || y >= 256) return;
        states[index(x, y, z)] = packed;
    }

    /** The block ID part of a packed state. */
    public int block(int x, int y, int z) { return get(x, y, z) >>> 4; }
}
