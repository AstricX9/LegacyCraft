package net.minecraft.world.chunk;

public final class Chunk {
    public static final int SIZE = 16;
    public static final int HEIGHT = 256;
    private final int chunkX, chunkZ;
    private final ChunkSection[] sections = new ChunkSection[HEIGHT / ChunkSection.SIZE];
    /** Sky light (high nibble) and block light (low nibble) per position, indexed like block storage. */
    private final byte[] light = new byte[SIZE * SIZE * HEIGHT];
    private boolean generated;
    private boolean populated;
    private long generationIndex = -1;

    public Chunk(int chunkX, int chunkZ) { this.chunkX = chunkX; this.chunkZ = chunkZ; }
    public int chunkX() { return chunkX; }
    public int chunkZ() { return chunkZ; }
    public boolean isGenerated() { return generated; }
    public long generationIndex() { return generationIndex; }
    public void markGenerated(long index) {
        if (index < 0) throw new IllegalArgumentException("Generation index must be non-negative");
        if (generated) throw new IllegalStateException("Chunk is already generated");
        generated = true;
        generationIndex = index;
    }

    /** Whether population (lakes, ores, trees, plants, snow...) has run for this chunk. */
    public boolean isPopulated() { return populated; }
    public void markPopulated() { populated = true; }

    public int get(int x, int y, int z) {
        if (y < 0 || y >= HEIGHT) return 0;
        ChunkSection section = sections[y >> 4];
        return section == null ? 0 : section.get(x, y & 15, z);
    }

    public void set(int x, int y, int z, int blockId) {
        if (y < 0 || y >= HEIGHT) return;
        int sectionIndex = y >> 4;
        ChunkSection section = sections[sectionIndex];
        if (section == null) {
            if (blockId == 0) return;
            section = sections[sectionIndex] = new ChunkSection();
        }
        section.set(x, y & 15, z, blockId);
    }

    public ChunkSection section(int index) { return sections[index]; }

    private static int lightIndex(int x, int y, int z) { return (y << 8) | (z << 4) | x; }

    public int skyLight(int x, int y, int z) { return (light[lightIndex(x, y, z)] >> 4) & 15; }
    public int blockLight(int x, int y, int z) { return light[lightIndex(x, y, z)] & 15; }

    public void setSkyLight(int x, int y, int z, int level) {
        int index = lightIndex(x, y, z);
        light[index] = (byte) ((level << 4) | (light[index] & 15));
    }

    public void setBlockLight(int x, int y, int z, int level) {
        int index = lightIndex(x, y, z);
        light[index] = (byte) ((light[index] & 0xf0) | level);
    }
}
