package net.minecraft.world.chunk;

public final class Chunk {
    public static final int SIZE = 16;
    public static final int HEIGHT = 256;
    private final int chunkX, chunkZ;
    private final ChunkSection[] sections = new ChunkSection[HEIGHT / ChunkSection.SIZE];
    private boolean generated;

    public Chunk(int chunkX, int chunkZ) { this.chunkX = chunkX; this.chunkZ = chunkZ; }
    public int chunkX() { return chunkX; }
    public int chunkZ() { return chunkZ; }
    public boolean isGenerated() { return generated; }
    public void markGenerated() { generated = true; }

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
}
