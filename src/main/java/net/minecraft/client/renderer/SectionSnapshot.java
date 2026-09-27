package net.minecraft.client.renderer;

import net.minecraft.block.BlockRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;

/**
 * A copy of one 16x16x16 section plus a two-block border (block states,
 * light and column biomes), taken on the main thread so a worker can build
 * the section's mesh without touching the world. Two blocks is enough for
 * every neighbour a block's shape, face culling or smooth lighting reads.
 */
final class SectionSnapshot implements RenderView {
    static final int MARGIN = 2, SIZE = 16 + 2 * MARGIN;
    private final BlockRegistry registry;
    final int originX, originY, originZ;
    private final int[] states = new int[SIZE * SIZE * SIZE];
    private final byte[] sky = new byte[SIZE * SIZE * SIZE], block = new byte[SIZE * SIZE * SIZE];
    private final Biome[] biomes = new Biome[SIZE * SIZE];
    private final int bedrock;

    /** Requires the section's chunk and its eight neighbours to be generated. */
    SectionSnapshot(World world, int sectionX, int sectionY, int sectionZ) {
        registry = world.registry();
        bedrock = registry.get("minecraft:bedrock").registryId() << BlockState.METADATA_BITS;
        originX = sectionX * 16 - MARGIN;
        originY = sectionY * 16 - MARGIN;
        originZ = sectionZ * 16 - MARGIN;
        for (int dz = 0; dz < SIZE; dz++) {
            for (int dx = 0; dx < SIZE; dx++) {
                int x = originX + dx, z = originZ + dz;
                Chunk chunk = world.generatedChunk(x >> 4, z >> 4);
                biomes[dz * SIZE + dx] = world.biomeAt(x, z);
                for (int dy = 0; dy < SIZE; dy++) {
                    int y = originY + dy, index = index(dx, dy, dz);
                    if (y < 0) {
                        states[index] = bedrock;
                    } else if (y >= Chunk.HEIGHT || chunk == null) {
                        sky[index] = 15;
                    } else {
                        states[index] = chunk.get(x & 15, y, z & 15);
                        sky[index] = (byte) chunk.skyLight(x & 15, y, z & 15);
                        block[index] = (byte) chunk.blockLight(x & 15, y, z & 15);
                    }
                }
            }
        }
    }

    private static int index(int dx, int dy, int dz) { return (dy * SIZE + dz) * SIZE + dx; }

    private int indexOf(int x, int y, int z) {
        int dx = x - originX, dy = y - originY, dz = z - originZ;
        if (dx < 0 || dy < 0 || dz < 0 || dx >= SIZE || dy >= SIZE || dz >= SIZE) return -1;
        return index(dx, dy, dz);
    }

    /** The packed (id << 4 | metadata) state; outside the copied box, air. */
    int packed(int x, int y, int z) {
        int index = indexOf(x, y, z);
        return index < 0 ? (y < 0 ? bedrock : 0) : states[index];
    }

    @Override public BlockState getState(int x, int y, int z) { return BlockState.unpack(registry, packed(x, y, z)); }
    @Override public BlockRegistry registry() { return registry; }

    @Override
    public int skyLight(int x, int y, int z) {
        int index = indexOf(x, y, z);
        return index < 0 ? (y < 0 ? 0 : 15) : sky[index];
    }

    @Override
    public int blockLight(int x, int y, int z) {
        int index = indexOf(x, y, z);
        return index < 0 ? 0 : block[index];
    }

    @Override
    public Biome biomeAt(int x, int z) {
        int dx = Math.max(0, Math.min(SIZE - 1, x - originX)), dz = Math.max(0, Math.min(SIZE - 1, z - originZ));
        return biomes[dz * SIZE + dx];
    }
}
