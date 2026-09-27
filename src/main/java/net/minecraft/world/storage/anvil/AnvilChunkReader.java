package net.minecraft.world.storage.anvil;

import net.minecraft.nbt.NbtTag;
import net.minecraft.world.chunk.Chunk;

/**
 * Converts a vanilla Anvil chunk into our {@link Chunk}: the per-section
 * block IDs and metadata become the same {@code (id << 4) | metadata} values
 * we store, and vanilla's own sky/block light and biome bytes come along so
 * they can be compared against what we compute.
 */
public final class AnvilChunkReader {
    /** A chunk as vanilla saved it. */
    public record Imported(Chunk chunk, byte[] skyLight, byte[] blockLight, byte[] biomes, boolean populated, boolean lit) {
        public int vanillaSkyLight(int x, int y, int z) { return skyLight[index(x, y, z)]; }
        public int vanillaBlockLight(int x, int y, int z) { return blockLight[index(x, y, z)]; }
        /** Legacy biome ID of a column, as vanilla stored it. */
        public int biome(int x, int z) { return biomes[z * Chunk.SIZE + x] & 0xff; }
        private static int index(int x, int y, int z) { return (y << 8) | (z << 4) | x; }
    }

    private AnvilChunkReader() {}

    public static Imported read(NbtTag.Compound root, long generationIndex) {
        NbtTag.Compound level = root.getCompound("Level");
        Chunk chunk = new Chunk(level.getInt("xPos"), level.getInt("zPos"));
        byte[] sky = new byte[Chunk.SIZE * Chunk.SIZE * Chunk.HEIGHT];
        byte[] block = new byte[sky.length];
        NbtTag.NbtList sections = level.getList("Sections");
        for (int i = 0; i < sections.size(); i++) {
            NbtTag.Compound section = sections.compound(i);
            int sectionY = section.getByte("Y") & 0xff;
            byte[] blocks = section.getByteArray("Blocks");
            byte[] data = section.getByteArray("Data");
            byte[] add = section.has("Add") ? section.getByteArray("Add") : null;
            byte[] skyLight = section.has("SkyLight") ? section.getByteArray("SkyLight") : null;
            byte[] blockLight = section.has("BlockLight") ? section.getByteArray("BlockLight") : null;
            for (int index = 0; index < 4096; index++) {
                int x = index & 15, z = index >> 4 & 15, y = sectionY * 16 + (index >> 8);
                int id = blocks[index] & 0xff;
                if (add != null) id |= nibble(add, index) << 8;
                chunk.set(x, y, z, id << 4 | nibble(data, index));
                if (skyLight != null) sky[Imported.index(x, y, z)] = (byte) nibble(skyLight, index);
                if (blockLight != null) block[Imported.index(x, y, z)] = (byte) nibble(blockLight, index);
            }
        }
        // Sky light above the highest section is full daylight; vanilla stores no section there.
        for (int y = topSection(sections) * 16; y < Chunk.HEIGHT; y++)
            for (int z = 0; z < Chunk.SIZE; z++)
                for (int x = 0; x < Chunk.SIZE; x++) sky[Imported.index(x, y, z)] = 15;
        chunk.markGenerated(generationIndex);
        byte[] biomes = level.has("Biomes") ? level.getByteArray("Biomes") : new byte[256];
        boolean populated = level.has("TerrainPopulated") && level.getByte("TerrainPopulated") != 0;
        // Vanilla only finishes a chunk's lighting once; unfinished chunks are relit when loaded.
        boolean lit = level.has("LightPopulated") && level.getByte("LightPopulated") != 0;
        return new Imported(chunk, sky, block, biomes, populated, lit);
    }

    private static int topSection(NbtTag.NbtList sections) {
        int top = 0;
        for (int i = 0; i < sections.size(); i++) top = Math.max(top, (sections.compound(i).getByte("Y") & 0xff) + 1);
        return top;
    }

    private static int nibble(byte[] array, int index) {
        return index % 2 == 0 ? array[index / 2] & 15 : (array[index / 2] >> 4) & 15;
    }
}
