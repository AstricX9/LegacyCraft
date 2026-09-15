package net.minecraft.util;

import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;

/**
 * Deterministic state hashing for differential/golden testing (see
 * {@code docs/compat/} and master context §84 "State hashing").
 *
 * <p>This hashes raw block IDs only, in a fixed, documented traversal order
 * (Y, then Z, then X — matching the classic Anvil section byte-array layout
 * used by {@link ChunkSection}). It intentionally does <b>not</b> hash
 * anything about the JVM's internal object layout, hash codes, or iteration
 * order of any collection, so two independently constructed simulations that
 * produced the same observable block state always produce the same hash,
 * regardless of process, heap layout, or run order.
 *
 * <p>A passing hash comparison proves <i>self-consistency</i> (the
 * simulation reproduces itself bit-for-bit for the same inputs) — it does
 * NOT by itself prove vanilla fidelity. Matching against a real vanilla
 * 1.8.9 capture requires a separate golden fixture; see
 * {@code docs/compat/unknowns.yml} for what vanilla data is still missing.
 */
public final class StateHash {
    private StateHash() {}

    /** FNV-1a 64-bit offset basis / prime, chosen only for stable, well-mixed output — not a compatibility surface. */
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    /** Hashes every block ID in a single generated chunk, Y-major then Z then X. */
    public static long chunkHash(Chunk chunk) {
        long hash = FNV_OFFSET_BASIS;
        hash = mix(hash, chunk.chunkX());
        hash = mix(hash, chunk.chunkZ());
        for (int y = 0; y < Chunk.HEIGHT; y++) {
            for (int z = 0; z < Chunk.SIZE; z++) {
                for (int x = 0; x < Chunk.SIZE; x++) {
                    hash = mix(hash, chunk.get(x, y, z));
                }
            }
        }
        return hash;
    }

    /**
     * Hashes a square region of chunks around the origin into one combined
     * value, ordered by (chunkX, chunkZ) so the result does not depend on
     * whatever order the caller happened to generate/request them in.
     */
    public static long regionHash(net.minecraft.world.World world, int chunkRadius) {
        long hash = FNV_OFFSET_BASIS;
        for (int cx = -chunkRadius; cx <= chunkRadius; cx++) {
            for (int cz = -chunkRadius; cz <= chunkRadius; cz++) {
                hash = mixLong(hash, chunkHash(world.getChunk(cx, cz)));
            }
        }
        return hash;
    }

    private static long mix(long hash, int value) {
        for (int shift = 0; shift < 32; shift += 8) {
            hash ^= (value >>> shift) & 0xff;
            hash *= FNV_PRIME;
        }
        return hash;
    }

    private static long mixLong(long hash, long value) {
        for (int shift = 0; shift < 64; shift += 8) {
            hash ^= (value >>> shift) & 0xff;
            hash *= FNV_PRIME;
        }
        return hash;
    }
}
