package net.minecraft.world.chunk;

/** Dense 16-cubed storage unit matching the vertical subdivision used by the legacy format. */
public final class ChunkSection {
    public static final int SIZE = 16;
    private final short[] blockIds = new short[SIZE * SIZE * SIZE];
    private int nonAirCount;

    private int index(int x, int y, int z) { return (y << 8) | (z << 4) | x; }
    public int get(int x, int y, int z) { return blockIds[index(x,y,z)] & 0xffff; }

    public void set(int x, int y, int z, int blockId) {
        int index = index(x,y,z);
        int old = blockIds[index] & 0xffff;
        if (old == blockId) return;
        if (old == 0 && blockId != 0) nonAirCount++;
        else if (old != 0 && blockId == 0) nonAirCount--;
        blockIds[index] = (short) blockId;
    }

    public boolean isEmpty() { return nonAirCount == 0; }
    public int nonAirCount() { return nonAirCount; }
}
