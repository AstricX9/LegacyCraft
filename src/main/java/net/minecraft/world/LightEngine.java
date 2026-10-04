package net.minecraft.world;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.util.Direction;
import net.minecraft.world.chunk.Chunk;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Sky and block light (master context §18). Both are 0-15 per position and
 * spread to neighbours losing max(1, opacity) per step; sky light also falls
 * straight down through transparent blocks at full strength. Light only
 * spreads through loaded chunks: a newly generated chunk is lit from its own
 * columns and sources, then exchanges light with the loaded chunks around it,
 * so the result does not depend on generation order. Block edits relight
 * incrementally (remove what depended on the old value, then re-spread).
 *
 * <p>HYPOTHESIS: this computes the ideal light field. Vanilla 1.8.9's
 * incremental relighting has known glitches (dark spots after edits, light
 * missed across unloaded borders) that are not reproduced (deviations.yml).
 */
final class LightEngine {
    enum Type { SKY, BLOCK }

    private final World world;
    private final int[] opacity, emission;
    private final Set<Long> changedChunks = new HashSet<>();
    private Chunk lastChunk;
    private long lastMarked;

    /** False in the Nether: no sky light anywhere (WorldProvider.getHasNoSky). */
    private final boolean hasSky;

    LightEngine(World world, BlockRegistry registry) {
        this.world = world;
        this.hasSky = world.dimension().hasSky;
        opacity = new int[registry.size()];
        emission = new int[registry.size()];
        for (Block block : registry.values()) {
            opacity[block.registryId()] = block.lightOpacity();
            emission[block.registryId()] = block.lightLevel();
        }
    }

    /** Lights a freshly generated or loaded chunk and exchanges light with its loaded neighbours. */
    void lightChunk(Chunk chunk) {
        int baseX = chunk.chunkX() * Chunk.SIZE, baseZ = chunk.chunkZ() * Chunk.SIZE;
        ArrayDeque<Long> sky = new ArrayDeque<>(), block = new ArrayDeque<>();
        for (int z = 0; z < Chunk.SIZE; z++) {
            for (int x = 0; x < Chunk.SIZE; x++) {
                // VERIFIED against 1.8.9 saves: sky light falls straight down at full strength
                // while it is still 15; once anything has dimmed it, every further block costs
                // at least one level even if transparent (same rule as spread() going down).
                int level = hasSky ? 15 : 0;
                for (int y = Chunk.HEIGHT - 1; y >= 0; y--) {
                    int id = chunk.get(x, y, z) >>> BlockState.METADATA_BITS;
                    if (level < 15 || opacity[id] > 0) level = Math.max(0, level - Math.max(1, opacity[id]));
                    chunk.setSkyLight(x, y, z, level);
                    chunk.setBlockLight(x, y, z, emission[id]);
                    if (emission[id] > 1) block.add(pack(baseX + x, y, baseZ + z));
                }
            }
        }
        // Only cells that can light something sideways need to spread: edges of the chunk and next to darker columns.
        for (int y = 0; y < Chunk.HEIGHT; y++) {
            for (int z = 0; z < Chunk.SIZE; z++) {
                for (int x = 0; x < Chunk.SIZE; x++) {
                    int level = chunk.skyLight(x, y, z);
                    if (level <= 1) continue;
                    boolean edge = x == 0 || z == 0 || x == Chunk.SIZE - 1 || z == Chunk.SIZE - 1;
                    if (edge || chunk.skyLight(x - 1, y, z) < level - 1 || chunk.skyLight(x + 1, y, z) < level - 1
                        || chunk.skyLight(x, y, z - 1) < level - 1 || chunk.skyLight(x, y, z + 1) < level - 1)
                        sky.add(pack(baseX + x, y, baseZ + z));
                }
            }
        }
        // Light already in the neighbours flows back across the shared borders.
        for (Direction side : Direction.HORIZONTALS) {
            Chunk neighbour = world.loadedChunk(chunk.chunkX() + side.xOffset, chunk.chunkZ() + side.zOffset);
            if (neighbour == null) continue;
            for (int y = 0; y < Chunk.HEIGHT; y++) {
                for (int i = 0; i < Chunk.SIZE; i++) {
                    int lx = side == Direction.WEST ? 15 : side == Direction.EAST ? 0 : i;
                    int lz = side == Direction.NORTH ? 15 : side == Direction.SOUTH ? 0 : i;
                    long position = pack(neighbour.chunkX() * Chunk.SIZE + lx, y, neighbour.chunkZ() * Chunk.SIZE + lz);
                    if (neighbour.skyLight(lx, y, lz) > 1) sky.add(position);
                    if (neighbour.blockLight(lx, y, lz) > 1) block.add(position);
                }
            }
        }
        markChanged(chunk.chunkX(), chunk.chunkZ());
        spread(Type.SKY, sky);
        spread(Type.BLOCK, block);
        flush();
    }

    /** Relights around a position whose block changed. */
    void blockChanged(int x, int y, int z) {
        for (Type type : Type.values()) if (hasSky || type == Type.BLOCK) relight(type, x, y, z);
        flush();
    }

    private void relight(Type type, int x, int y, int z) {
        ArrayDeque<Long> sources = new ArrayDeque<>();
        ArrayDeque<long[]> removal = new ArrayDeque<>();
        int old = get(type, x, y, z);
        if (old > 0) {
            set(type, x, y, z, 0);
            removal.add(new long[]{pack(x, y, z), old});
        }
        while (!removal.isEmpty()) {
            long[] entry = removal.poll();
            int px = unpackX(entry[0]), py = unpackY(entry[0]), pz = unpackZ(entry[0]);
            int level = (int) entry[1];
            for (Direction side : Direction.values()) {
                int nx = px + side.xOffset, ny = py + side.yOffset, nz = pz + side.zOffset;
                if (!isLoaded(nx, ny, nz)) continue;
                int neighbour = get(type, nx, ny, nz);
                if (neighbour == 0) continue;
                boolean dependent = neighbour < level
                    || type == Type.SKY && side == Direction.DOWN && level == 15 && neighbour == 15;
                if (dependent) {
                    set(type, nx, ny, nz, 0);
                    removal.add(new long[]{pack(nx, ny, nz), neighbour});
                } else {
                    sources.add(pack(nx, ny, nz));
                }
            }
        }
        // The changed position's own contribution: emitted light, or open sky at the top of the world.
        int id = world.rawBlockId(x, y, z);
        int own = type == Type.BLOCK ? emission[id] : y == Chunk.HEIGHT - 1 ? Math.max(0, 15 - opacity[id]) : 0;
        if (own > get(type, x, y, z)) set(type, x, y, z, own);
        if (get(type, x, y, z) > 0) sources.add(pack(x, y, z));
        for (Direction side : Direction.values()) {
            int nx = x + side.xOffset, ny = y + side.yOffset, nz = z + side.zOffset;
            if (isLoaded(nx, ny, nz) && get(type, nx, ny, nz) > 0) sources.add(pack(nx, ny, nz));
        }
        spread(type, sources);
    }

    /** Breadth-first spread from the queued positions into loaded neighbours that are darker than they could be. */
    private void spread(Type type, ArrayDeque<Long> queue) {
        while (!queue.isEmpty()) {
            long position = queue.poll();
            int x = unpackX(position), y = unpackY(position), z = unpackZ(position);
            int level = get(type, x, y, z);
            if (level <= 1) continue;
            for (Direction side : Direction.values()) {
                int nx = x + side.xOffset, ny = y + side.yOffset, nz = z + side.zOffset;
                if (!isLoaded(nx, ny, nz)) continue;
                int neighbourOpacity = opacity[chunkAt(nx >> 4, nz >> 4).get(nx & 15, ny, nz & 15) >>> BlockState.METADATA_BITS];
                int next = level - Math.max(1, neighbourOpacity);
                // Full-strength sky light keeps falling straight down through transparent blocks.
                if (type == Type.SKY && side == Direction.DOWN && level == 15 && neighbourOpacity == 0) next = 15;
                if (next > get(type, nx, ny, nz)) {
                    set(type, nx, ny, nz, next);
                    queue.add(pack(nx, ny, nz));
                }
            }
        }
    }

    /**
     * The generated chunk at chunk coordinates, or null. Light work touches the
     * same chunk many times in a row, so the last one found is kept (a chunk
     * never stops being generated, so a remembered one stays valid).
     */
    private Chunk chunkAt(int chunkX, int chunkZ) {
        Chunk cached = lastChunk;
        if (cached != null && cached.chunkX() == chunkX && cached.chunkZ() == chunkZ) return cached;
        Chunk chunk = world.loadedChunk(chunkX, chunkZ);
        if (chunk != null) lastChunk = chunk;
        return chunk;
    }

    int get(Type type, int x, int y, int z) {
        Chunk chunk = chunkAt(x >> 4, z >> 4);
        if (chunk == null || y < 0 || y >= Chunk.HEIGHT) return 0;
        int lx = x & 15, lz = z & 15;
        return type == Type.SKY ? chunk.skyLight(lx, y, lz) : chunk.blockLight(lx, y, lz);
    }

    private void set(Type type, int x, int y, int z, int level) {
        int chunkX = x >> 4, chunkZ = z >> 4;
        Chunk chunk = chunkAt(chunkX, chunkZ);
        int lx = x & 15, lz = z & 15;
        if (type == Type.SKY) chunk.setSkyLight(lx, y, lz, level);
        else chunk.setBlockLight(lx, y, lz, level);
        markChanged(chunkX, chunkZ);
    }

    private boolean isLoaded(int x, int y, int z) {
        return y >= 0 && y < Chunk.HEIGHT && chunkAt(x >> 4, z >> 4) != null;
    }

    private void markChanged(int chunkX, int chunkZ) {
        long key = World.chunkKey(chunkX, chunkZ);
        if (key == lastMarked && !changedChunks.isEmpty()) return;
        lastMarked = key;
        changedChunks.add(key);
    }

    private void flush() {
        for (long key : changedChunks) world.notifyLightChanged((int) (key >> 32), (int) key);
        changedChunks.clear();
    }

    private static long pack(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | (y & 0xFFFL);
    }
    private static int unpackX(long p) { return (int) (p >> 38); }
    private static int unpackZ(long p) { return (int) (p << 26 >> 38); }
    private static int unpackY(long p) { return (int) (p & 0xFFFL); }
}
