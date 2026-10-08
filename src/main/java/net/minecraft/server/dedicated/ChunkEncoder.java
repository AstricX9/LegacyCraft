package net.minecraft.server.dedicated;

import net.minecraft.network.v47.Clientbound;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;

/**
 * A chunk column as the 1.8 Chunk Data packet carries it: for each non-empty
 * section from the bottom, 4096 block states as little-endian shorts
 * ({@code id << 4 | metadata}, which is how sections already store them);
 * then every sent section's block light, then its sky light (overworld only),
 * as nibble arrays with the even index in the low nibble; then 256 biome ids,
 * row by row along x. Indexes run {@code y << 8 | z << 4 | x}.
 */
public final class ChunkEncoder {
    private static final int BLOCKS = 16 * 16 * 16;

    private ChunkEncoder() {}

    public static Clientbound.ChunkData encode(World world, Chunk chunk, boolean skyLight) {
        int mask = 0;
        for (int s = 0; s < 16; s++) {
            ChunkSection section = chunk.section(s);
            if (section != null && !section.isEmpty()) mask |= 1 << s;
        }
        int sections = Integer.bitCount(mask);
        byte[] data = new byte[sections * (BLOCKS * 2 + BLOCKS / 2 + (skyLight ? BLOCKS / 2 : 0)) + 256];
        int p = 0;
        for (int s = 0; s < 16; s++) {
            if ((mask & 1 << s) == 0) continue;
            ChunkSection section = chunk.section(s);
            for (int y = 0; y < 16; y++)
                for (int z = 0; z < 16; z++)
                    for (int x = 0; x < 16; x++) {
                        int state = section.get(x, y, z);
                        data[p++] = (byte) state;
                        data[p++] = (byte) (state >> 8);
                    }
        }
        p = nibbles(chunk, mask, data, p, false);
        if (skyLight) p = nibbles(chunk, mask, data, p, true);
        int baseX = chunk.chunkX() * 16, baseZ = chunk.chunkZ() * 16;
        for (int z = 0; z < 16; z++)
            for (int x = 0; x < 16; x++) data[p++] = (byte) world.biomeAt(baseX + x, baseZ + z).id();
        return new Clientbound.ChunkData(chunk.chunkX(), chunk.chunkZ(), true, mask, data);
    }

    private static int nibbles(Chunk chunk, int mask, byte[] data, int p, boolean sky) {
        for (int s = 0; s < 16; s++) {
            if ((mask & 1 << s) == 0) continue;
            for (int y = s * 16; y < s * 16 + 16; y++)
                for (int z = 0; z < 16; z++)
                    for (int x = 0; x < 16; x += 2) {
                        int low = sky ? chunk.skyLight(x, y, z) : chunk.blockLight(x, y, z);
                        int high = sky ? chunk.skyLight(x + 1, y, z) : chunk.blockLight(x + 1, y, z);
                        data[p++] = (byte) (low | high << 4);
                    }
        }
        return p;
    }
}
