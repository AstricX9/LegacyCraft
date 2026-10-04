package net.minecraft.world;

import net.minecraft.block.Block;
import net.minecraft.block.BlockFurnace;
import net.minecraft.block.BlockRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockUpdates;
import net.minecraft.inventory.Furnace;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.util.AxisAlignedBB;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

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

    /**
     * What world-side block logic asks the game to do: drop what liquid washed
     * away, show lava hardening, drop a block that lost its support.
     */
    public interface Events {
        /** Liquid flowed over a block (BlockDynamicLiquid dropBlockAsItem). */
        default void washedAway(int x, int y, int z, BlockState previous) {}
        /** Lava met water here (BlockLiquid.triggerMixEffects: fizz and large smoke). */
        default void liquidsMixed(int x, int y, int z) {}
        /** A block broke because a gameplay change took its support. */
        default void popped(int x, int y, int z, BlockState previous) {}
        /** A block went out with its break effects but no drops (a portal whose frame broke). */
        default void shattered(int x, int y, int z, BlockState previous) {}
        /** A block that held items is gone; its contents spill here (InventoryHelper.dropInventoryItems). */
        default void containerBroken(int x, int y, int z, List<ItemStack> contents) {}
        /** Fire burnt a TNT block away and lit it (BlockFire.catchOnFire priming TNT). */
        default void tntIgnited(int x, int y, int z) {}
    }
    private Events events = new Events() {};

    /** An animal world generation placed (SpawnerAnimals.performWorldGenSpawning), waiting for the game to create it. */
    public record CreatureSpawn(String entity, double x, double y, double z, float yaw) {}
    private final List<CreatureSpawn> pendingCreatures = new ArrayList<>();

    /** The animals generation placed since the last call (chunks can generate before the game has an entity list). */
    public List<CreatureSpawn> takeCreatureSpawns() {
        if (pendingCreatures.isEmpty()) return List.of();
        List<CreatureSpawn> taken = new ArrayList<>(pendingCreatures);
        pendingCreatures.clear();
        return taken;
    }

    /** Mob spawners by position and the entity they make (TileEntityMobSpawner); the game runs them. */
    private final Map<BlockPos, String> spawners = new LinkedHashMap<>();
    public Map<BlockPos, String> spawners() { return spawners; }
    public void setSpawner(int x, int y, int z, String entity) { spawners.put(new BlockPos(x, y, z), entity); }

    /** A furnace's position (the key of its tile entity). */
    public record BlockPos(int x, int y, int z) {}

    /** A mob head's tile entity (TileEntitySkull): SkullType 0 skeleton, 1 wither, 2 zombie, 3 player, 4 creeper; Rot 0-15 on the floor. */
    public record Skull(int type, int rotation) {}
    private final Map<BlockPos, Skull> skulls = new LinkedHashMap<>();
    /** Mob heads by position; entries whose block has since gone are dropped when found. */
    public Map<BlockPos, Skull> skulls() { return skulls; }
    public void setSkull(int x, int y, int z, int type, int rotation) { skulls.put(new BlockPos(x, y, z), new Skull(type, rotation)); }
    /** The head at this position, or null. */
    public Skull skull(int x, int y, int z) { return skulls.get(new BlockPos(x, y, z)); }
    /** The furnaces' contents and fire, by position (vanilla keeps them as tile entities in their chunk). */
    private final Map<BlockPos, Furnace> furnaces = new LinkedHashMap<>();
    private FurnaceRecipes smelting;
    private java.util.function.Function<String, Item> items;

    /**
     * A pending block tick (NextTickListEntry): due at a world tick, ordered
     * by due time, then priority, then scheduling order; one per position and
     * block at a time.
     */
    public record ScheduledTick(int x, int y, int z, Block block, long due, int priority, long order)
        implements Comparable<ScheduledTick> {
        @Override public int compareTo(ScheduledTick other) {
            if (due != other.due) return Long.compare(due, other.due);
            if (priority != other.priority) return Integer.compare(priority, other.priority);
            return Long.compare(order, other.order);
        }
        TickKey key() { return new TickKey(x, y, z, block.registryId()); }
    }
    /** Most scheduled ticks run per world tick (WorldServer.tickUpdates). */
    public static final int MAX_TICKS_PER_TICK = 1000;
    private final TreeSet<ScheduledTick> scheduled = new TreeSet<>();
    private record TickKey(int x, int y, int z, int block) {}
    private final Set<TickKey> scheduledKeys = new java.util.HashSet<>();
    private long tickTime, nextTickOrder;
    private final Random tickRandom = new Random();
    private final LiquidFlow liquids;
    private final FireSpread fire;
    private final net.minecraft.block.Block portal;
    /** Where enchanting tables stand (their tile entities, which animate the book above them). */
    private final java.util.Set<BlockPos> enchantingTables = new java.util.LinkedHashSet<>();
    private final Block enchantingTable;
    /** The game settings world logic reads (the client copies them in from level.dat each tick). */
    private int difficulty = 2;
    private boolean doFireTick = true, raining;

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

    private final Dimension dimension;
    private final net.minecraft.world.gen.NetherGenerator netherGenerator;
    private final net.minecraft.world.gen.NetherPopulator netherPopulator;
    /** The one biome of the Nether (Hell, id 8). */
    private final Biome hellBiome;

    public Dimension dimension() { return dimension; }
    public boolean isNether() { return dimension == Dimension.NETHER; }
    /** The Nether's generator (for its fortresses), or null. */
    public net.minecraft.world.gen.NetherGenerator netherGenerator() { return netherGenerator; }

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
        this(registry, biomeRegistry, seed, flat, Dimension.OVERWORLD);
    }

    /** A world of the given dimension (the Nether ignores {@code flat}). */
    public World(BlockRegistry registry, BiomeRegistry biomeRegistry, long seed, FlatGeneratorInfo flat, Dimension dimension) {
        this.dimension = dimension;
        if (dimension == Dimension.NETHER) flat = null;
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
        boolean nether = dimension == Dimension.NETHER;
        generator = flat == null && !nether ? new ChunkGenerator(genBlocks, biomeRegistry, biomes.layers(), seed) : null;
        populator = nether ? null : new ChunkPopulator(genBlocks, seed);
        Biome hell = biomeRegistry.byId(8);
        netherGenerator = nether ? new net.minecraft.world.gen.NetherGenerator(genBlocks, seed) : null;
        netherPopulator = nether ? new net.minecraft.world.gen.NetherPopulator(genBlocks, seed) : null;
        hellBiome = nether ? (hell != null ? hell : biomeRegistry.get("minecraft:plains")) : null;
        light = new LightEngine(this, registry);
        liquids = new LiquidFlow(this, registry);
        fire = new FireSpread(this);
        portal = registry.get("minecraft:portal");
        enchantingTable = registry.get("minecraft:enchanting_table");
    }

    /** Every enchanting table in the loaded chunks (for their book animation). */
    public java.util.Set<BlockPos> enchantingTables() { return java.util.Collections.unmodifiableSet(enchantingTables); }

    /** Finds the enchanting tables in a chunk that was loaded rather than generated. */
    private void findEnchantingTables(Chunk chunk) {
        if (enchantingTable == null) return;
        int id = enchantingTable.registryId();
        for (int s = 0; s < Chunk.HEIGHT / 16; s++) {
            var section = chunk.section(s);
            if (section == null || section.isEmpty()) continue;
            for (int y = 0; y < 16; y++)
                for (int z = 0; z < 16; z++)
                    for (int x = 0; x < 16; x++)
                        if (unpackBlockId(section.get(x, y, z)) == id)
                            enchantingTables.add(new BlockPos(chunk.chunkX() * Chunk.SIZE + x, s * 16 + y, chunk.chunkZ() * Chunk.SIZE + z));
        }
    }

    @Override
    public BlockRegistry registry() { return registry; }
    public void addListener(Listener listener) { listeners.add(listener); }
    public void removeListener(Listener listener) { listeners.remove(listener); }
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
        // Block.onBlockAdded: liquids and fire react so far.
        if (old != now) {
            liquids.added(x, y, z, state);
            if (fire.isFire(now)) fire.added(x, y, z, tickRandom);
        }
        if (old != now && enchantingTable != null) {
            if (now == enchantingTable) enchantingTables.add(new BlockPos(x, y, z));
            else if (old == enchantingTable) enchantingTables.remove(new BlockPos(x, y, z));
        }
        // BlockFurnace.breakBlock: the contents spill unless it only swapped between lit and unlit.
        if (old instanceof BlockFurnace && !(now instanceof BlockFurnace)) {
            Furnace furnace = furnaces.remove(new BlockPos(x, y, z));
            if (furnace != null) {
                List<ItemStack> contents = furnace.takeContents();
                if (!contents.isEmpty()) events.containerBroken(x, y, z, contents);
            }
        }
    }

    @Override
    public void neighbourChanged(int x, int y, int z, BlockState state) {
        liquids.neighbourChanged(x, y, z, state);
        if (fire.isFire(state.block())) fire.neighbourChanged(x, y, z);
        else if (state.block() == portal) PortalShape.neighbourChanged(this, x, y, z, state);
    }

    public void setDifficulty(int difficulty) { this.difficulty = difficulty; }
    public int difficulty() { return difficulty; }
    public void setDoFireTick(boolean doFireTick) { this.doFireTick = doFireTick; }
    public boolean doFireTick() { return doFireTick; }
    public void setRaining(boolean raining) { this.raining = raining; }
    public boolean isRaining() { return raining; }

    /** Lights a nether portal in an empty obsidian frame around the spot (what fire lit on obsidian does). */
    public boolean trySpawnPortal(int x, int y, int z) { return PortalShape.trySpawn(this, x, y, z); }

    /** Lets the six neighbours react to a change here (notifyNeighborsOfStateChange). */
    public void notifyNeighbours(int x, int y, int z) {
        BlockUpdates.afterChange(this, x, y, z, events::popped);
    }

    public void setEvents(Events events) { this.events = events == null ? new Events() {} : events; }
    public Events events() { return events; }

    /**
     * Asks for {@code block} at the position to tick after {@code delay}
     * world ticks, unless that position already waits for the same block
     * (World.scheduleUpdate).
     */
    public void scheduleUpdate(int x, int y, int z, Block block, int delay) { scheduleUpdate(x, y, z, block, delay, 0); }

    public void scheduleUpdate(int x, int y, int z, Block block, int delay, int priority) {
        if (!inBounds(x, y, z)) return;
        ScheduledTick tick = new ScheduledTick(x, y, z, block, tickTime + delay, priority, nextTickOrder++);
        if (scheduledKeys.add(tick.key())) scheduled.add(tick);
    }

    public boolean isScheduled(int x, int y, int z, Block block) {
        return scheduledKeys.contains(new TickKey(x, y, z, block.registryId()));
    }

    /** Pending ticks in due order, with their due time relative to now (for saving). */
    public List<ScheduledTick> scheduledTicks() {
        List<ScheduledTick> result = new ArrayList<>();
        for (ScheduledTick tick : scheduled)
            result.add(new ScheduledTick(tick.x(), tick.y(), tick.z(), tick.block(), tick.due() - tickTime, tick.priority(), tick.order()));
        return result;
    }

    /** World ticks run so far this session (the clock scheduled ticks are due on). */
    public long tickTime() { return tickTime; }

    /**
     * One world tick: the clock advances, then up to 1000 due scheduled ticks
     * run, oldest first, each only if its block is still there and its chunk
     * is generated. Ticks they schedule wait for a later tick.
     */
    public void tick() {
        tickTime++;
        List<ScheduledTick> due = new ArrayList<>();
        while (!scheduled.isEmpty() && due.size() < MAX_TICKS_PER_TICK && scheduled.first().due() <= tickTime) {
            ScheduledTick tick = scheduled.pollFirst();
            scheduledKeys.remove(tick.key());
            due.add(tick);
        }
        for (ScheduledTick tick : due) {
            if (generatedChunk(floorDiv(tick.x(), Chunk.SIZE), floorDiv(tick.z(), Chunk.SIZE)) == null) continue;
            BlockState state = getState(tick.x(), tick.y(), tick.z());
            if (state.block() != tick.block() || state.block().isAir()) continue;
            if (liquids.isFlowing(state.block())) liquids.tick(tick.x(), tick.y(), tick.z(), state, tickRandom);
            else if (fire.isFire(state.block())) fire.tick(tick.x(), tick.y(), tick.z(), state, tickRandom);
        }
        tickFurnaces();
    }

    // ---- furnaces (TileEntityFurnace) ------------------------------------------------------

    /** The recipes and item names furnaces use; set once the item registry is loaded (without it nothing smelts). */
    public void setSmelting(FurnaceRecipes recipes, java.util.function.Function<String, Item> items) {
        this.smelting = recipes;
        this.items = items;
    }

    public FurnaceRecipes smelting() { return smelting; }
    public java.util.function.Function<String, Item> itemLookup() { return items; }

    /**
     * The furnace at a position, made empty the first time it is asked for
     * (BlockFurnace.createNewTileEntity), or null when no furnace block is there.
     */
    public Furnace furnaceAt(int x, int y, int z) {
        if (!(get(x, y, z) instanceof BlockFurnace)) return null;
        return furnaces.computeIfAbsent(new BlockPos(x, y, z), key -> new Furnace(smelting, items));
    }

    /** Every furnace with its position, for saving. */
    public Map<BlockPos, Furnace> furnaces() { return java.util.Collections.unmodifiableMap(furnaces); }

    /** Installs a saved furnace (loading). */
    public void restoreFurnace(int x, int y, int z, Furnace furnace) { furnaces.put(new BlockPos(x, y, z), furnace); }

    /**
     * Each furnace burns and cooks for one tick; when its fire is lit or goes
     * out the block swaps between furnace and lit_furnace, keeping its facing
     * and contents (BlockFurnace.setState). A furnace whose block is gone is forgotten.
     */
    private void tickFurnaces() {
        if (furnaces.isEmpty()) return;
        for (Map.Entry<BlockPos, Furnace> entry : List.copyOf(furnaces.entrySet())) {
            BlockPos pos = entry.getKey();
            if (generatedChunk(floorDiv(pos.x(), Chunk.SIZE), floorDiv(pos.z(), Chunk.SIZE)) == null) continue;
            BlockState state = getState(pos.x(), pos.y(), pos.z());
            if (!(state.block() instanceof BlockFurnace)) {
                furnaces.remove(pos);
                continue;
            }
            Furnace furnace = entry.getValue();
            if (furnace.update()) {
                Block target = registry.get(furnace.isBurning() ? "minecraft:lit_furnace" : "minecraft:furnace");
                if (target != null && target != state.block())
                    setStateAndUpdate(pos.x(), pos.y(), pos.z(), BlockState.of(target, state.metadata()));
            }
        }
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
        BlockUpdates.afterChange(this, x, y, z, events::popped);
    }

    public Chunk getChunk(int chunkX, int chunkZ) {
        long key = chunkKey(chunkX, chunkZ);
        Chunk chunk = chunks.get(key);
        if (chunk == null) {
            chunk = loadSaved(chunkX, chunkZ);
            if (chunk == null) {
                chunk = new Chunk(chunkX, chunkZ);
                chunks.put(key, chunk);
            }
        }
        if (!chunk.isGenerated()) generate(chunk);
        return chunk;
    }

    /** Saved chunks the world reads on demand (a world folder); null for a world that was never saved. */
    public interface ChunkSource {
        boolean has(int chunkX, int chunkZ);
        Chunk read(int chunkX, int chunkZ) throws java.io.IOException;
    }

    private ChunkSource chunkSource;

    /**
     * Saved chunks are now read when the game first needs them (as vanilla's
     * ChunkProviderServer does) instead of all at once when the world opens.
     */
    public void setChunkSource(ChunkSource source) { this.chunkSource = source; }
    public ChunkSource chunkSource() { return chunkSource; }

    /** Makes new chunks continue the saved generation sequence. */
    public void reserveGenerationIndex(long next) { nextGenerationIndex = Math.max(nextGenerationIndex, next); }

    /** Reads a saved chunk into the world, or returns null when there is none (or it can't be read). */
    private Chunk loadSaved(int chunkX, int chunkZ) {
        if (chunkSource == null || !chunkSource.has(chunkX, chunkZ)) return null;
        Chunk chunk;
        try {
            chunk = chunkSource.read(chunkX, chunkZ);
        } catch (java.io.IOException | RuntimeException error) {
            System.err.println("Couldn't load chunk " + chunkX + "," + chunkZ + "; it will be regenerated: " + error.getMessage());
            return null;
        }
        if (chunk == null || !chunk.isGenerated()) return null;
        chunks.put(chunkKey(chunkX, chunkZ), chunk);
        nextGenerationIndex = Math.max(nextGenerationIndex, chunk.generationIndex() + 1);
        if (!chunk.isLightStored()) light.lightChunk(chunk);
        findEnchantingTables(chunk);
        chunk.markClean();
        if (populationEnabled && !chunk.isPopulated()) {
            // An unfinished edge of a saved world: population may now be possible (Chunk.populateChunk on load).
            pendingPopulation.add(new int[]{chunkX, chunkZ});
            pendingPopulation.add(new int[]{chunkX - 1, chunkZ});
            pendingPopulation.add(new int[]{chunkX, chunkZ - 1});
            pendingPopulation.add(new int[]{chunkX - 1, chunkZ - 1});
            if (!populating) finishGeneration();
        }
        for (Listener listener : listeners) listener.chunkChanged(chunkX, chunkZ);
        return chunk;
    }

    public Collection<Chunk> loadedChunks() { return chunks.values(); }
    public int loadedChunkCount() { return chunks.size(); }
    public long seed() { return seed; }
    public Biome biomeAt(int x, int z) { return hellBiome != null ? hellBiome : flatBiome != null ? flatBiome : biomes.sample(x, z); }
    public boolean isFlat() { return flat != null; }

    /** Installs a persisted chunk and advances the generation sequence past it. */
    public void restoreChunk(Chunk chunk) {
        if (!chunk.isGenerated()) throw new IllegalArgumentException("Restored chunk is not generated");
        long key = chunkKey(chunk.chunkX(), chunk.chunkZ());
        if (chunks.putIfAbsent(key, chunk) != null)
            throw new IllegalStateException("Chunk is already loaded: " + chunk.chunkX() + "," + chunk.chunkZ());
        nextGenerationIndex = Math.max(nextGenerationIndex, chunk.generationIndex() + 1);
        light.lightChunk(chunk);
        findEnchantingTables(chunk);
    }

    /**
     * Whether any generated block of the material ("water", "lava", "fire",
     * ...) lies in the cells the box covers, counted the way 1.8.9's
     * isMaterialInBB/handleMaterialAcceleration count them: from
     * floor(min) up to but not including floor(max + 1) on each axis, so
     * boxes that callers shrink until they are inside-out reach only some
     * rows (HYPOTHESIS; this quirk decides when small entities are "in"
     * water or lava).
     */
    public boolean isMaterialInBox(AxisAlignedBB box, String material) {
        int minX = floorInt(box.minX()), maxX = floorInt(box.maxX() + 1.0);
        int minY = floorInt(box.minY()), maxY = floorInt(box.maxY() + 1.0);
        int minZ = floorInt(box.minZ()), maxZ = floorInt(box.maxZ() + 1.0);
        for (int x = minX; x < maxX; x++)
            for (int y = Math.max(0, minY); y < Math.min(HEIGHT, maxY); y++)
                for (int z = minZ; z < maxZ; z++) {
                    if (generatedChunk(floorDiv(x, Chunk.SIZE), floorDiv(z, Chunk.SIZE)) == null) continue;
                    if (get(x, y, z).material().equals(material)) return true;
                }
        return false;
    }

    /**
     * World.handleMaterialAcceleration: whether the box touches the liquid (as
     * {@link #isMaterialInBox}) and the sum of the flow vectors of the liquid
     * blocks it touches, or null when it touches none. Entities pushed by
     * water add the normalised sum times 0.014 to their motion each tick.
     */
    public double[] liquidCurrent(AxisAlignedBB box, String material) {
        int minX = floorInt(box.minX()), maxX = floorInt(box.maxX() + 1.0);
        int minY = floorInt(box.minY()), maxY = floorInt(box.maxY() + 1.0);
        int minZ = floorInt(box.minZ()), maxZ = floorInt(box.maxZ() + 1.0);
        boolean inside = false;
        double[] sum = new double[3];
        for (int x = minX; x < maxX; x++)
            for (int y = Math.max(0, minY); y < Math.min(HEIGHT, maxY); y++)
                for (int z = minZ; z < maxZ; z++) {
                    if (generatedChunk(floorDiv(x, Chunk.SIZE), floorDiv(z, Chunk.SIZE)) == null) continue;
                    BlockState state = getState(x, y, z);
                    if (!state.block().material().equals(material)) continue;
                    if (maxY < y + 1 - net.minecraft.block.Liquids.heightPercent(state.metadata())) continue;
                    inside = true;
                    double[] flow = net.minecraft.block.Liquids.flowVector(this, x, y, z, material);
                    sum[0] += flow[0];
                    sum[1] += flow[1];
                    sum[2] += flow[2];
                }
        return inside ? sum : null;
    }

    private static int floorInt(double value) { return (int) Math.floor(value); }

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
        ChunkPrimer primer = netherGenerator != null ? netherGenerator.generate(chunk.chunkX(), chunk.chunkZ())
            : generator.generate(chunk.chunkX(), chunk.chunkZ());
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
                if (netherPopulator != null) netherPopulator.populate(genAccess, netherGenerator, next[0], next[1]);
                else populator.populate(genAccess, next[0], next[1]);
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
        if (chunk != null) return chunk.isGenerated();
        return loadSaved(chunkX, chunkZ) != null;
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
        @Override public void scheduleBlockTick(int x, int y, int z) {
            Block block = World.this.get(x, y, z);
            if (!block.isAir()) scheduleUpdate(x, y, z, block, liquids.tickRate(block.material()));
        }
        @Override public void setSpawner(int x, int y, int z, String entity) { spawners.put(new BlockPos(x, y, z), entity); }
        @Override public void spawnCreature(String entity, double x, double y, double z, float yaw) {
            pendingCreatures.add(new CreatureSpawn(entity, x, y, z, yaw));
        }

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
