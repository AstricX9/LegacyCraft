package net.minecraft.world;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockUpdates;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeRegistry;
import net.minecraft.world.biome.BiomeSource;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkGenerator;
import net.minecraft.world.gen.ChunkPopulator;
import net.minecraft.world.gen.ChunkPrimer;
import net.minecraft.world.gen.FlatGeneratorInfo;
import net.minecraft.world.gen.GenBlocks;
import net.minecraft.world.gen.GenWorld;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Infinite horizontal world backed by lazily generated 16x16x16 chunk sections. */
public final class World implements IBlockWorld {
    public static final int HEIGHT = Chunk.HEIGHT;
    public static final int SEA_LEVEL = 63;
    private final Map<Long, Chunk> chunks = new HashMap<>();
    private final BlockRegistry registry;
    private final long seed;
    private long nextGenerationIndex;
    private final BiomeSource biomes;
    private final List<Listener> listeners = new ArrayList<>();

    /** Told about every block written through {@link #setState} and every chunk whose light changed (the renderer rebuilds meshes). */
    public interface Listener {
        void blockChanged(int x, int y, int z);
        default void lightChanged(int chunkX, int chunkZ) {}
        /** Blocks in a chunk changed without per-block notices (population writes them in bulk). */
        default void chunkChanged(int chunkX, int chunkZ) {}
    }
    private final LightEngine light;

    private final Block air, grass, bedrock;
    private final GenBlocks genBlocks;
    private final ChunkGenerator generator;
    private final ChunkPopulator populator;
    private final GenAccess genAccess = new GenAccess();
    private boolean populationEnabled = true, populating;
    private final ArrayDeque<int[]> pendingPopulation = new ArrayDeque<>();
    /** Chunks generated or changed by population since lighting last ran. */
    private final Set<Chunk> needsLight = new LinkedHashSet<>();
    /** Chunks population wrote into since listeners were last told. */
    private final Set<Chunk> populated = new LinkedHashSet<>();

    /** Superflat layers, or null for normal terrain. */
    private final FlatGeneratorInfo flat;
    private final Biome flatBiome;

    public World(BlockRegistry registry, BiomeRegistry biomeRegistry, long seed) {
        this(registry, biomeRegistry, seed, null);
    }

    /**
     * @param flat Superflat layers (the FLAT world type); null generates normal
     *             terrain. Flat worlds place their layers and biome only —
     *             no structures or decoration yet (DEV-011).
     */
    public World(BlockRegistry registry, BiomeRegistry biomeRegistry, long seed, FlatGeneratorInfo flat) {
        this.flat = flat;
        Biome chosen = flat == null ? null : biomeRegistry.byId(flat.biome());
        this.flatBiome = flat == null ? null : chosen != null ? chosen : biomeRegistry.get("minecraft:plains");
        this.registry = registry;
        this.seed = seed;
        air = registry.air();
        grass = registry.get("minecraft:grass");
        bedrock = registry.get("minecraft:bedrock");
        biomes = new BiomeSource(biomeRegistry, seed);
        genBlocks = new GenBlocks(registry);
        generator = flat == null ? new ChunkGenerator(genBlocks, biomeRegistry, biomes.layers(), seed) : null;
        populator = new ChunkPopulator(genBlocks, seed);
        light = new LightEngine(this, registry);
    }

    @Override
    public BlockRegistry registry() { return registry; }
    public void addListener(Listener listener) { listeners.add(listener); }
    public boolean inBounds(int x, int y, int z) { return y >= 0 && y < HEIGHT; }

    public Block get(int x, int y, int z) {
        if (y < 0) return bedrock;
        if (y >= HEIGHT) return air;
        Chunk chunk = getChunk(floorDiv(x, Chunk.SIZE), floorDiv(z, Chunk.SIZE));
        return registry.byId(unpackBlockId(chunk.get(floorMod(x, Chunk.SIZE), y, floorMod(z, Chunk.SIZE))));
    }

    /**
     * Full (block, metadata) state at a position — see {@link BlockState}.
     * Out-of-world positions report the same synthetic bedrock/air {@link
     * #get} does, always at metadata 0.
     */
    @Override
    public BlockState getState(int x, int y, int z) {
        if (y < 0) return BlockState.defaultOf(bedrock);
        if (y >= HEIGHT) return BlockState.defaultOf(air);
        Chunk chunk = getChunk(floorDiv(x, Chunk.SIZE), floorDiv(z, Chunk.SIZE));
        return BlockState.unpack(registry, chunk.get(floorMod(x, Chunk.SIZE), y, floorMod(z, Chunk.SIZE)));
    }

    /** Convenience for callers that don't care about metadata; sets metadata 0. */
    public void set(int x, int y, int z, Block block) { setState(x, y, z, BlockState.defaultOf(block)); }

    @Override
    public void setState(int x, int y, int z, BlockState state) {
        if (!inBounds(x, y, z)) return;
        Chunk chunk = getChunk(floorDiv(x, Chunk.SIZE), floorDiv(z, Chunk.SIZE));
        int localX = floorMod(x, Chunk.SIZE), localZ = floorMod(z, Chunk.SIZE);
        Block old = registry.byId(unpackBlockId(chunk.get(localX, y, localZ)));
        chunk.set(localX, y, localZ, state.packed());
        Block now = state.block();
        if (old.lightOpacity() != now.lightOpacity() || old.lightLevel() != now.lightLevel()) light.blockChanged(x, y, z);
        for (Listener listener : listeners) listener.blockChanged(x, y, z);
    }

    /** Sky light (0-15) at a position; the sky above the world is fully lit. */
    public int getSkyLight(int x, int y, int z) {
        if (y >= HEIGHT) return 15;
        if (y < 0) return 0;
        getChunk(floorDiv(x, Chunk.SIZE), floorDiv(z, Chunk.SIZE));
        return light.get(LightEngine.Type.SKY, x, y, z);
    }

    /** Block light (0-15) from torches, lava, glowstone and other light sources. */
    public int getBlockLight(int x, int y, int z) {
        if (y < 0 || y >= HEIGHT) return 0;
        getChunk(floorDiv(x, Chunk.SIZE), floorDiv(z, Chunk.SIZE));
        return light.get(LightEngine.Type.BLOCK, x, y, z);
    }

    /** A generated chunk, or null; never generates anything (renderers use this to wait for terrain). */
    public Chunk generatedChunk(int chunkX, int chunkZ) { return loadedChunk(chunkX, chunkZ); }

    /** A chunk that already exists and is generated, without generating anything. */
    Chunk loadedChunk(int chunkX, int chunkZ) {
        Chunk chunk = chunks.get(chunkKey(chunkX, chunkZ));
        return chunk != null && chunk.isGenerated() ? chunk : null;
    }

    /** The raw block ID at a position in a loaded chunk. */
    int rawBlockId(int x, int y, int z) {
        return unpackBlockId(loadedChunk(floorDiv(x, Chunk.SIZE), floorDiv(z, Chunk.SIZE))
            .get(floorMod(x, Chunk.SIZE), y, floorMod(z, Chunk.SIZE)));
    }

    void notifyLightChanged(int chunkX, int chunkZ) {
        for (Listener listener : listeners) listener.lightChanged(chunkX, chunkZ);
    }

    /** A gameplay edit: writes the state, then lets neighbours react (see {@link BlockUpdates}). */
    public void setStateAndUpdate(int x, int y, int z, BlockState state) {
        if (!inBounds(x, y, z)) return;
        setState(x, y, z, state);
        BlockUpdates.afterChange(this, x, y, z);
    }

    public Chunk getChunk(int chunkX, int chunkZ) {
        long key = chunkKey(chunkX, chunkZ);
        Chunk chunk = chunks.computeIfAbsent(key, ignored -> new Chunk(chunkX, chunkZ));
        if (!chunk.isGenerated()) generate(chunk);
        return chunk;
    }

    public Collection<Chunk> loadedChunks() { return chunks.values(); }
    public int loadedChunkCount() { return chunks.size(); }
    public long seed() { return seed; }
    public Biome biomeAt(int x, int z) { return flatBiome != null ? flatBiome : biomes.sample(x, z); }
    public boolean isFlat() { return flat != null; }

    /** Installs a persisted chunk and advances the generation sequence past it. */
    public void restoreChunk(Chunk chunk) {
        if (!chunk.isGenerated()) throw new IllegalArgumentException("Restored chunk is not generated");
        long key = chunkKey(chunk.chunkX(), chunk.chunkZ());
        if (chunks.putIfAbsent(key, chunk) != null)
            throw new IllegalStateException("Chunk is already loaded: " + chunk.chunkX() + "," + chunk.chunkZ());
        nextGenerationIndex = Math.max(nextGenerationIndex, chunk.generationIndex() + 1);
        light.lightChunk(chunk);
    }

    public int surfaceHeight(int x, int z) {
        Chunk chunk = getChunk(floorDiv(x, Chunk.SIZE), floorDiv(z, Chunk.SIZE));
        int localX = floorMod(x, Chunk.SIZE), localZ = floorMod(z, Chunk.SIZE);
        for (int y = HEIGHT - 1; y >= 0; y--) {
            Block block = registry.byId(unpackBlockId(chunk.get(localX, y, localZ)));
            // Trees don't count: the surface is the ground they stand on.
            if (block.isSolid() && !block.material().equals("leaves") && !block.name().path().startsWith("log")) return y;
        }
        return 0;
    }

    /**
     * Turns population off for this world (tests and tools that want the raw
     * terrain stage, which unlike population does not depend on the order
     * chunks are generated in). Must be called before any chunk is generated.
     */
    public void disablePopulation() {
        if (!chunks.isEmpty()) throw new IllegalStateException("Chunks already generated");
        populationEnabled = false;
    }

    /**
     * Vanilla's spawn point search (WorldServer.createSpawnPosition): a random
     * spawn-friendly biome (forest, plains, taiga, their hills, jungle) within
     * 256 blocks of the origin on the 1:4 biome map, then random steps of up
     * to 64 blocks until the ground at sea level is grass, giving up after
     * 1000 tries. Returns {x, z}; flat worlds spawn at the origin.
     */
    public int[] findSpawn() {
        if (flat != null) return new int[]{0, 0};
        Random random = new Random(seed);
        int minX = -256 >> 2, minZ = -256 >> 2, maxX = 256 >> 2, maxZ = 256 >> 2;
        int width = maxX - minX + 1, depth = maxZ - minZ + 1;
        int[] ids = biomes.layers().coarse().getInts(minX, minZ, width, depth);
        int x = 0, z = 0, found = 0;
        boolean any = false;
        for (int i = 0; i < width * depth; i++) {
            int id = ids[i];
            boolean allowed = id == 4 || id == 1 || id == 5 || id == 19 || id == 18 || id == 21 || id == 22;
            if (allowed && (!any || random.nextInt(found + 1) == 0)) {
                x = minX + i % width << 2;
                z = minZ + i / width << 2;
                any = true;
                found++;
            }
        }
        for (int tries = 0; groundAboveSeaLevel(x, z) != grass; ) {
            x += random.nextInt(64) - random.nextInt(64);
            z += random.nextInt(64) - random.nextInt(64);
            if (++tries == 1000) break;
        }
        return new int[]{x, z};
    }

    private Block groundAboveSeaLevel(int x, int z) {
        int y = SEA_LEVEL;
        while (y < HEIGHT - 1 && !get(x, y + 1, z).isAir()) y++;
        return get(x, y, z);
    }

    private void generate(Chunk chunk) {
        // Mark first: population reaching into this chunk must see it as existing.
        chunk.markGenerated(nextGenerationIndex++);
        if (flat != null) {
            generateFlat(chunk);
            return;
        }
        ChunkPrimer primer = generator.generate(chunk.chunkX(), chunk.chunkZ());
        for (int x = 0; x < Chunk.SIZE; x++)
            for (int z = 0; z < Chunk.SIZE; z++)
                for (int y = 0; y < HEIGHT; y++) {
                    int state = primer.get(x, y, z);
                    if (state != 0) chunk.set(x, y, z, state);
                }
        needsLight.add(chunk);
        if (populationEnabled) {
            // Vanilla Chunk.populateChunk: this chunk may complete the 2x2 group of itself or of
            // its west, north or north-west neighbour, and each complete group populates its corner.
            int cx = chunk.chunkX(), cz = chunk.chunkZ();
            pendingPopulation.add(new int[]{cx, cz});
            pendingPopulation.add(new int[]{cx - 1, cz});
            pendingPopulation.add(new int[]{cx, cz - 1});
            pendingPopulation.add(new int[]{cx - 1, cz - 1});
        }
        if (!populating) finishGeneration();
    }

    /**
     * Runs the populations that became possible, then lights everything that
     * was generated or changed. A population that reaches into a chunk that
     * does not exist yet generates it on the spot; any population that chunk
     * enables waits in the queue until the current one is done.
     */
    private void finishGeneration() {
        populating = true;
        try {
            while (!pendingPopulation.isEmpty()) {
                int[] next = pendingPopulation.poll();
                Chunk corner = chunks.get(chunkKey(next[0], next[1]));
                if (corner == null || !corner.isGenerated() || corner.isPopulated()) continue;
                if (!exists(next[0] + 1, next[1]) || !exists(next[0], next[1] + 1) || !exists(next[0] + 1, next[1] + 1)) continue;
                corner.markPopulated();
                populator.populate(genAccess, next[0], next[1]);
            }
        } finally {
            populating = false;
        }
        List<Chunk> toLight = new ArrayList<>(needsLight);
        needsLight.clear();
        for (Chunk chunk : toLight) light.lightChunk(chunk);
        List<Chunk> changed = new ArrayList<>(populated);
        populated.clear();
        for (Chunk chunk : changed)
            for (Listener listener : listeners) listener.chunkChanged(chunk.chunkX(), chunk.chunkZ());
    }

    private boolean exists(int chunkX, int chunkZ) {
        Chunk chunk = chunks.get(chunkKey(chunkX, chunkZ));
        return chunk != null && chunk.isGenerated();
    }

    /** Population's view of the world: raw writes (relit afterwards), vanilla heightmap and light estimates. */
    private final class GenAccess implements GenWorld {
        private Chunk last;

        /** The chunk holding a block column, generating it if needed; features work locally, so the last one is kept. */
        private Chunk chunk(int x, int z) {
            Chunk cached = last;
            if (cached != null && cached.chunkX() == x >> 4 && cached.chunkZ() == z >> 4) return cached;
            return last = getChunk(x >> 4, z >> 4);
        }

        @Override
        public int get(int x, int y, int z) {
            if (y < 0 || y >= HEIGHT) return 0;
            return chunk(x, z).get(x & 15, y, z & 15);
        }

        @Override
        public void set(int x, int y, int z, int packed) {
            if (y < 0 || y >= HEIGHT) return;
            Chunk chunk = chunk(x, z);
            int localX = x & 15, localZ = z & 15;
            int old = chunk.get(localX, y, localZ);
            chunk.set(localX, y, localZ, packed);
            populated.add(chunk);
            // Only changes that dim or emit light make the chunk need relighting (ores in stone, plants on grass don't).
            if (genBlocks.opacity(old) != genBlocks.opacity(packed) || genBlocks.emission(old) != genBlocks.emission(packed))
                needsLight.add(chunk);
        }

        @Override public Biome biome(int x, int z) { return biomeAt(x, z); }

        @Override
        public int height(int x, int z) {
            Chunk chunk = chunk(x, z);
            int localX = x & 15, localZ = z & 15;
            for (int section = HEIGHT / 16 - 1; section >= 0; section--) {
                if (chunk.section(section) == null) continue;
                for (int y = section * 16 + 15; y >= section * 16; y--)
                    if (genBlocks.opacity(chunk.get(localX, y, localZ)) > 0) return y + 1;
            }
            return 0;
        }

        /** Sky light straight down a column, the way a fresh chunk is lit (full until something dims it). */
        private int columnSky(int x, int y, int z) {
            int top = height(x, z);
            if (y >= top) return 15;
            int level = 15;
            for (int by = top - 1; by >= y && level > 0; by--)
                level = Math.max(0, level - Math.max(1, genBlocks.opacity(get(x, by, z))));
            return level;
        }

        @Override
        public int light(int x, int y, int z) {
            if (y < 0 || y >= HEIGHT) return 15;
            int sky = columnSky(x, y, z);
            if (sky < 15) {
                for (int side = 0; side < 4; side++) {
                    int nx = x + (side == 1 ? 1 : side == 3 ? -1 : 0), nz = z + (side == 0 ? -1 : side == 2 ? 1 : 0);
                    if (genBlocks.opacity(get(nx, y, nz)) < 15) sky = Math.max(sky, columnSky(nx, y, nz) - 1);
                }
            }
            return Math.max(sky, blockLight(x, y, z));
        }

        @Override
        public int blockLight(int x, int y, int z) {
            if (y < 0 || y >= HEIGHT) return 0;
            return light.get(LightEngine.Type.BLOCK, x, y, z);
        }

        @Override public boolean seesSky(int x, int y, int z) { return y >= height(x, z); }
        @Override public GenBlocks blocks() { return genBlocks; }
    }

    private void generateFlat(Chunk chunk) {
        int height = Math.min(HEIGHT, flat.height());
        for (int y = 0; y < height; y++) {
            BlockState state = flat.stateAt(y);
            if (state == null || state.block() == air) continue;
            int packed = state.packed();
            for (int z = 0; z < Chunk.SIZE; z++)
                for (int x = 0; x < Chunk.SIZE; x++) chunk.set(x, y, z, packed);
        }
        light.lightChunk(chunk);
    }

    public static int floorDiv(int value, int divisor) { return Math.floorDiv(value, divisor); }
    public static int floorMod(int value, int divisor) { return Math.floorMod(value, divisor); }
    public static long chunkKey(int x, int z) { return ((long) x << 32) ^ (z & 0xffffffffL); }

    private static int unpackBlockId(int packedState) { return packedState >>> BlockState.METADATA_BITS; }
}
