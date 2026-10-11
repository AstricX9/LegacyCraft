package net.minecraft.world.storage;

import net.minecraft.block.BlockRegistry;
import net.minecraft.world.biome.BiomeRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Versioned, atomic world metadata and compressed chunk persistence.
 *
 * <p>Opening a world reads only the index; each chunk is read the first time
 * the game needs it ({@link World.ChunkSource}), with the light it was saved
 * with. Saving writes only chunks that changed since they were loaded or
 * last saved, and keeps the index entries of chunks that were never loaded.
 */
public final class WorldStorage {
    private static final int INDEX_MAGIC = 0x4c434957; // LCIW
    private static final int CHUNK_MAGIC = 0x4c434348; // LCCH
    /**
     * 2 added the per-chunk "populated" flag (format 1 chunks load as
     * populated: they were finished worlds); 3 stores the light after the
     * blocks (older chunks are relit when they load).
     */
    private static final int FORMAT = 3;
    private static final int BLOCK_BYTES = Chunk.SIZE * Chunk.SIZE * Chunk.HEIGHT * 2;
    private static final String INDEX_FILE = "level.lci";
    private static final int TICKS_MAGIC = 0x4c435454; // LCTT
    /** Pending block ticks (vanilla keeps them per chunk as TileTicks); optional, so older saves load. */
    private static final String TICKS_FILE = "ticks.lct";
    private static final int FURNACES_MAGIC = 0x4c434655; // LCFU
    /** Furnace contents and fire (vanilla keeps them per chunk as TileEntities); optional, so older saves load. */
    private static final String FURNACES_FILE = "furnaces.lcf";

    private WorldStorage() {}
    public static boolean exists(Path directory) { return Files.isRegularFile(directory.resolve(INDEX_FILE)); }

    /** The saved chunks of an opened world folder: the index, read once, and lazy chunk reads. */
    public static final class SavedChunks implements World.ChunkSource {
        private final Path directory;
        private final int registrySize;
        /** chunk key → generation index, for every chunk on disk. */
        private final Map<Long, Long> index = new HashMap<>();

        SavedChunks(Path directory, int registrySize) {
            this.directory = directory;
            this.registrySize = registrySize;
        }

        @Override public boolean has(int chunkX, int chunkZ) { return index.containsKey(World.chunkKey(chunkX, chunkZ)); }

        @Override
        public Chunk read(int chunkX, int chunkZ) throws IOException {
            Long generationIndex = index.get(World.chunkKey(chunkX, chunkZ));
            if (generationIndex == null) return null;
            return readChunk(directory.resolve("chunks"), chunkX, chunkZ, generationIndex, registrySize);
        }

        public int size() { return index.size(); }
    }

    public static void save(Path directory, World world) throws IOException {
        Path chunksDirectory = directory.resolve("chunks");
        Files.createDirectories(chunksDirectory);
        SavedChunks saved = world.chunkSource() instanceof SavedChunks existing && existing.directory.equals(directory)
            ? existing : new SavedChunks(directory, Integer.MAX_VALUE);
        Map<Long, long[]> entries = new HashMap<>(); // key → {x, z, generationIndex}
        for (Map.Entry<Long, Long> entry : saved.index.entrySet()) {
            long key = entry.getKey();
            entries.put(key, new long[]{key >> 32, (int) key, entry.getValue()});
        }
        for (Chunk chunk : world.loadedChunks()) {
            if (!chunk.isGenerated()) continue;
            long key = World.chunkKey(chunk.chunkX(), chunk.chunkZ());
            if (chunk.isDirty() || !saved.index.containsKey(key)) {
                writeChunk(chunksDirectory, chunk);
                chunk.markClean();
            }
            entries.put(key, new long[]{chunk.chunkX(), chunk.chunkZ(), chunk.generationIndex()});
            saved.index.put(key, chunk.generationIndex());
        }
        List<long[]> ordered = new ArrayList<>(entries.values());
        ordered.sort(Comparator.comparingLong(entry -> entry[2]));

        writeIndex(directory, world.seed(), ordered);
        if (world.chunkSource() == null) world.setChunkSource(saved);
        writeTicks(directory, world);
        writeFurnaces(directory, world);
        writeSpawners(directory, world);
        writeStructures(directory, world);
        writeSkulls(directory, world);
    }

    /** Writes the chunk index: {x, z, generationIndex} entries in generation order. */
    private static void writeIndex(Path directory, long seed, List<long[]> ordered) throws IOException {
        Path target = directory.resolve(INDEX_FILE);
        Path temporary = directory.resolve(INDEX_FILE + ".tmp");
        try (var output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary)))) {
            output.writeInt(INDEX_MAGIC);
            output.writeInt(FORMAT);
            output.writeLong(seed);
            output.writeInt(ordered.size());
            for (long[] entry : ordered) {
                output.writeInt((int) entry[0]);
                output.writeInt((int) entry[1]);
                output.writeLong(entry[2]);
            }
        }
        moveAtomically(temporary, target);
    }

    /** What {@link #convertAnvil} carried over and what it had to leave behind. */
    public record Conversion(int chunks, int damagedChunks, int unknownBlocks, int tileEntities, int entities) {}

    /**
     * Converts a vanilla 1.8.9 Anvil world in place: every chunk in
     * {@code region/} (and {@code DIM-1/region/} for the Nether) is written as
     * a LegacyCraft chunk with its blocks, metadata, light and population
     * flag, then the index is written last so a failed conversion leaves the
     * world unconverted. The region files are left as they are. Damaged
     * chunks are skipped (the game generates them again); block IDs this game
     * doesn't know become air; tile-entity data (chest contents,
     * sign text...) and entities are not carried over and are only counted.
     * {@code progress} gets 0-100.
     */
    public static Conversion convertAnvil(Path directory, long seed, int registrySize,
                                          java.util.function.IntConsumer progress) throws IOException {
        List<Path> overworld = net.minecraft.world.storage.anvil.RegionFile.list(directory.resolve("region"));
        Path netherDirectory = directory.resolve(net.minecraft.world.Dimension.NETHER.folder());
        List<Path> nether = net.minecraft.world.storage.anvil.RegionFile.list(netherDirectory.resolve("region"));
        int total = Math.max(1, overworld.size() + nether.size());
        int[] done = {0};
        int[] counts = new int[5];
        Runnable step = () -> progress.accept(++done[0] * 100 / total);
        convertDimension(directory, overworld, seed, registrySize, counts, step);
        if (!nether.isEmpty()) convertDimension(netherDirectory, nether, seed, registrySize, counts, step);
        return new Conversion(counts[0], counts[1], counts[2], counts[3], counts[4]);
    }

    private static void convertDimension(Path directory, List<Path> regions, long seed, int registrySize,
                                         int[] counts, Runnable step) throws IOException {
        Path chunksDirectory = directory.resolve("chunks");
        Files.createDirectories(chunksDirectory);
        List<long[]> entries = new ArrayList<>();
        long next = 0;
        for (Path file : regions) {
            // Vanilla leaves empty region files behind; they hold no chunks.
            if (Files.size(file) < 2 * 4096) { step.run(); continue; }
            var region = net.minecraft.world.storage.anvil.RegionFile.open(file);
            int baseX = region.regionX() * net.minecraft.world.storage.anvil.RegionFile.CHUNKS;
            int baseZ = region.regionZ() * net.minecraft.world.storage.anvil.RegionFile.CHUNKS;
            for (int localZ = 0; localZ < net.minecraft.world.storage.anvil.RegionFile.CHUNKS; localZ++) {
                for (int localX = 0; localX < net.minecraft.world.storage.anvil.RegionFile.CHUNKS; localX++) {
                    int chunkX = baseX + localX, chunkZ = baseZ + localZ;
                    net.minecraft.nbt.NbtTag.Compound root;
                    try {
                        root = region.readChunk(chunkX, chunkZ);
                    } catch (IOException | RuntimeException damaged) {
                        System.err.println("Skipping chunk " + chunkX + "," + chunkZ + " in " + file.getFileName() + ": " + damaged);
                        counts[1]++;
                        continue;
                    }
                    if (root == null) continue;
                    Chunk chunk = convertChunk(root, next, registrySize, counts, file, chunkX, chunkZ);
                    if (chunk == null) continue;
                    writeChunk(chunksDirectory, chunk);
                    entries.add(new long[]{chunk.chunkX(), chunk.chunkZ(), next++});
                    counts[0]++;
                }
            }
            step.run();
        }
        writeIndex(directory, seed, entries);
    }

    /** The converted chunk, or null when it is damaged. */
    private static Chunk convertChunk(net.minecraft.nbt.NbtTag.Compound root, long generationIndex, int registrySize,
                                      int[] counts, Path file, int chunkX, int chunkZ) throws IOException {
        var level = root.has("Level") ? root.getCompound("Level") : null;
        require(level != null && level.has("Sections"), "Chunk " + chunkX + "," + chunkZ + " in " + file.getFileName()
            + " has no block sections; is this a 1.8.9 world?");
        var sections = level.getList("Sections");
        for (int i = 0; i < sections.size(); i++)
            require(sections.compound(i).has("Blocks"), "Chunk " + chunkX + "," + chunkZ + " in " + file.getFileName()
                + " uses the block palette format of Minecraft 1.13 or newer, which LegacyCraft can't read");
        net.minecraft.world.storage.anvil.AnvilChunkReader.Imported imported;
        try {
            imported = net.minecraft.world.storage.anvil.AnvilChunkReader.read(root, generationIndex);
            if (imported.chunk().chunkX() != chunkX || imported.chunk().chunkZ() != chunkZ)
                throw new IllegalStateException("it says it is " + imported.chunk().chunkX() + "," + imported.chunk().chunkZ());
        } catch (RuntimeException damaged) {
            System.err.println("Skipping chunk " + chunkX + "," + chunkZ + " in " + file.getFileName() + ": " + damaged);
            counts[1]++;
            return null;
        }
        Chunk chunk = imported.chunk();
        for (int y = 0; y < Chunk.HEIGHT; y++) {
            if (chunk.section(y >> 4) == null) { y |= 15; continue; }
            for (int z = 0; z < Chunk.SIZE; z++)
                for (int x = 0; x < Chunk.SIZE; x++) {
                    int packed = chunk.get(x, y, z);
                    if (packed != 0 && packed >>> BlockState.METADATA_BITS >= registrySize) {
                        chunk.set(x, y, z, 0);
                        counts[2]++;
                    }
                }
        }
        byte[] light = chunk.lightData();
        for (int y = 0; y < Chunk.HEIGHT; y++)
            for (int z = 0; z < Chunk.SIZE; z++)
                for (int x = 0; x < Chunk.SIZE; x++)
                    light[(y << 8) | (z << 4) | x] = (byte) (imported.vanillaSkyLight(x, y, z) << 4 | imported.vanillaBlockLight(x, y, z));
        if (imported.populated()) chunk.markPopulated();
        counts[3] += level.getList("TileEntities").size();
        counts[4] += level.getList("Entities").size();
        return chunk;
    }

    private static final int SPAWNERS_MAGIC = 0x4c435350; // LCSP
    /** Mob spawner entities (vanilla keeps them per chunk as TileEntities); optional, so older saves load. */
    private static final String SPAWNERS_FILE = "spawners.lcs";

    private static void writeSpawners(Path directory, World world) throws IOException {
        Path target = directory.resolve(SPAWNERS_FILE);
        Path temporary = directory.resolve(SPAWNERS_FILE + ".tmp");
        var spawners = world.spawners();
        try (var output = new DataOutputStream(new GZIPOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary))))) {
            output.writeInt(SPAWNERS_MAGIC);
            output.writeInt(spawners.size());
            for (var entry : spawners.entrySet()) {
                output.writeInt(entry.getKey().x());
                output.writeInt(entry.getKey().y());
                output.writeInt(entry.getKey().z());
                output.writeUTF(entry.getValue());
            }
        }
        moveAtomically(temporary, target);
    }

    private static void readSpawners(Path directory, World world) throws IOException {
        Path source = directory.resolve(SPAWNERS_FILE);
        if (!Files.isRegularFile(source)) return;
        try (var input = new DataInputStream(new GZIPInputStream(new BufferedInputStream(Files.newInputStream(source))))) {
            require(input.readInt() == SPAWNERS_MAGIC, "Invalid spawner file");
            int count = input.readInt();
            require(count >= 0, "Negative spawner count");
            for (int i = 0; i < count; i++) {
                int x = input.readInt(), y = input.readInt(), z = input.readInt();
                world.setSpawner(x, y, z, input.readUTF());
            }
        }
    }

    private static final int SKULLS_MAGIC = 0x4c43534b; // LCSK
    /** Mob heads (vanilla keeps them per chunk as TileEntitySkull); optional, so older saves load. */
    private static final String SKULLS_FILE = "skulls.lcs";

    private static void writeSkulls(Path directory, World world) throws IOException {
        Path target = directory.resolve(SKULLS_FILE);
        if (world.skulls().isEmpty() && !Files.exists(target)) return;
        Path temporary = directory.resolve(SKULLS_FILE + ".tmp");
        var skulls = world.skulls();
        try (var output = new DataOutputStream(new GZIPOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary))))) {
            output.writeInt(SKULLS_MAGIC);
            output.writeInt(skulls.size());
            for (var entry : skulls.entrySet()) {
                output.writeInt(entry.getKey().x());
                output.writeInt(entry.getKey().y());
                output.writeInt(entry.getKey().z());
                output.writeByte(entry.getValue().type());
                output.writeByte(entry.getValue().rotation());
            }
        }
        moveAtomically(temporary, target);
    }

    private static void readSkulls(Path directory, World world) throws IOException {
        Path source = directory.resolve(SKULLS_FILE);
        if (!Files.isRegularFile(source)) return;
        try (var input = new DataInputStream(new GZIPInputStream(new BufferedInputStream(Files.newInputStream(source))))) {
            require(input.readInt() == SKULLS_MAGIC, "Invalid skull file");
            int count = input.readInt();
            require(count >= 0, "Negative skull count");
            for (int i = 0; i < count; i++) {
                int x = input.readInt(), y = input.readInt(), z = input.readInt();
                world.setSkull(x, y, z, input.readByte(), input.readByte());
            }
        }
    }

    /** Nether fortresses in vanilla's MapGenStructureData format: data/Fortress.dat, gzipped NBT. */
    private static final String FORTRESS_FILE = "data/Fortress.dat";

    private static void writeStructures(Path directory, World world) throws IOException {
        var generator = world.netherGenerator();
        if (generator == null || !generator.fortresses().hasStarts()) return;
        Path target = directory.resolve(FORTRESS_FILE);
        Files.createDirectories(target.getParent());
        net.minecraft.nbt.NbtIo.writeCompressed(generator.fortresses().writeNbt(), target);
    }

    private static void readStructures(Path directory, World world) throws IOException {
        var generator = world.netherGenerator();
        Path source = directory.resolve(FORTRESS_FILE);
        if (generator == null || !Files.isRegularFile(source)) return;
        generator.fortresses().readNbt(net.minecraft.nbt.NbtIo.readCompressed(source));
    }

    private static void writeFurnaces(Path directory, World world) throws IOException {
        Path target = directory.resolve(FURNACES_FILE);
        Path temporary = directory.resolve(FURNACES_FILE + ".tmp");
        var furnaces = world.furnaces();
        try (var output = new DataOutputStream(new GZIPOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary))))) {
            output.writeInt(FURNACES_MAGIC);
            output.writeInt(furnaces.size());
            for (var entry : furnaces.entrySet()) {
                output.writeInt(entry.getKey().x());
                output.writeInt(entry.getKey().y());
                output.writeInt(entry.getKey().z());
                entry.getValue().write(output);
            }
        }
        moveAtomically(temporary, target);
    }

    /**
     * Reads the furnaces back into {@code world}, resolving item names with the
     * world's item lookup ({@link World#setSmelting}); without one the
     * furnaces load empty.
     */
    private static void readFurnaces(Path directory, World world) throws IOException {
        Path source = directory.resolve(FURNACES_FILE);
        if (!Files.isRegularFile(source)) return;
        try (var input = new DataInputStream(new GZIPInputStream(new BufferedInputStream(Files.newInputStream(source))))) {
            require(input.readInt() == FURNACES_MAGIC, "Invalid furnace file");
            int count = input.readInt();
            require(count >= 0, "Negative furnace count");
            for (int i = 0; i < count; i++) {
                int x = input.readInt(), y = input.readInt(), z = input.readInt();
                world.restoreFurnace(x, y, z, net.minecraft.inventory.Furnace.read(input, world.smelting(), world.itemLookup()));
            }
        }
    }

    private static void writeTicks(Path directory, World world) throws IOException {
        Path target = directory.resolve(TICKS_FILE);
        Path temporary = directory.resolve(TICKS_FILE + ".tmp");
        List<World.ScheduledTick> ticks = world.scheduledTicks();
        try (var output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary)))) {
            output.writeInt(TICKS_MAGIC);
            output.writeInt(ticks.size());
            for (World.ScheduledTick tick : ticks) {
                output.writeInt(tick.x());
                output.writeInt(tick.y());
                output.writeInt(tick.z());
                output.writeInt(tick.block().registryId());
                output.writeInt((int) Math.max(0, Math.min(Integer.MAX_VALUE, tick.due())));
                output.writeInt(tick.priority());
            }
        }
        moveAtomically(temporary, target);
    }

    private static void readTicks(Path directory, World world, BlockRegistry blocks) throws IOException {
        Path source = directory.resolve(TICKS_FILE);
        if (!Files.isRegularFile(source)) return;
        try (var input = new DataInputStream(new BufferedInputStream(Files.newInputStream(source)))) {
            require(input.readInt() == TICKS_MAGIC, "Invalid pending tick file");
            int count = input.readInt();
            require(count >= 0, "Negative pending tick count");
            for (int i = 0; i < count; i++) {
                int x = input.readInt(), y = input.readInt(), z = input.readInt(), id = input.readInt();
                int delay = input.readInt(), priority = input.readInt();
                require(id >= 0 && id < blocks.size(), "Unknown block ID " + id + " in " + source);
                world.scheduleUpdate(x, y, z, blocks.byId(id), delay, priority);
            }
        }
    }

    public static World load(Path directory, BlockRegistry blocks, BiomeRegistry biomes) throws IOException {
        return load(directory, blocks, seed -> new World(blocks, biomes, seed));
    }

    /** Loads chunks into a world built by {@code factory} from the saved seed (lets flat worlds keep their layers). */
    public static World load(Path directory, BlockRegistry blocks, java.util.function.LongFunction<World> factory) throws IOException {
        Path index = directory.resolve(INDEX_FILE);
        try (var input = new DataInputStream(new BufferedInputStream(Files.newInputStream(index)))) {
            require(input.readInt() == INDEX_MAGIC, "Not a LegacyCraft world index");
            int format = input.readInt();
            require(format >= 1 && format <= FORMAT, "Unsupported world index version");
            World world = factory.apply(input.readLong());
            int count = input.readInt();
            require(count >= 0, "Negative chunk count");
            SavedChunks saved = new SavedChunks(directory, blocks.size());
            long next = 0;
            for (int i = 0; i < count; i++) {
                int x = input.readInt(), z = input.readInt();
                long generationIndex = input.readLong();
                saved.index.put(World.chunkKey(x, z), generationIndex);
                next = Math.max(next, generationIndex + 1);
            }
            world.reserveGenerationIndex(next);
            world.setChunkSource(saved);
            readTicks(directory, world, blocks);
            readFurnaces(directory, world);
            readSpawners(directory, world);
            readStructures(directory, world);
            readSkulls(directory, world);
            return world;
        }
    }

    /** Gzip at the fastest level: chunk files are mostly air and stone and still shrink well. */
    private static final class FastGzip extends GZIPOutputStream {
        FastGzip(java.io.OutputStream out) throws IOException {
            super(out, 1 << 16);
            def.setLevel(java.util.zip.Deflater.BEST_SPEED);
        }
    }

    private static void writeChunk(Path directory, Chunk chunk) throws IOException {
        Path target = directory.resolve(fileName(chunk.chunkX(), chunk.chunkZ()));
        Path temporary = directory.resolve(fileName(chunk.chunkX(), chunk.chunkZ()) + ".tmp");
        byte[] blocks = new byte[BLOCK_BYTES];
        int i = 0;
        for (int y = 0; y < Chunk.HEIGHT; y++) {
            net.minecraft.world.chunk.ChunkSection section = chunk.section(y >> 4);
            if (section == null) { i += Chunk.SIZE * Chunk.SIZE * 2; continue; }
            for (int z = 0; z < Chunk.SIZE; z++)
                for (int x = 0; x < Chunk.SIZE; x++) {
                    int packed = chunk.get(x, y, z);
                    blocks[i++] = (byte) (packed >> 8);
                    blocks[i++] = (byte) packed;
                }
        }
        try (var output = new DataOutputStream(new FastGzip(Files.newOutputStream(temporary)))) {
            output.writeInt(CHUNK_MAGIC);
            output.writeInt(FORMAT);
            output.writeInt(chunk.chunkX());
            output.writeInt(chunk.chunkZ());
            output.writeLong(chunk.generationIndex());
            output.writeBoolean(chunk.isPopulated());
            output.write(blocks);
            output.write(chunk.lightData());
        }
        moveAtomically(temporary, target);
    }

    private static Chunk readChunk(Path directory, int expectedX, int expectedZ,
                                   long expectedIndex, int registrySize) throws IOException {
        Path source = directory.resolve(fileName(expectedX, expectedZ));
        try (var input = new DataInputStream(new GZIPInputStream(new BufferedInputStream(Files.newInputStream(source), 1 << 16), 1 << 16))) {
            require(input.readInt() == CHUNK_MAGIC, "Invalid chunk file " + source);
            int format = input.readInt();
            require(format >= 1 && format <= FORMAT, "Unsupported chunk version");
            int x = input.readInt(), z = input.readInt();
            long generationIndex = input.readLong();
            boolean populated = format == 1 || input.readBoolean();
            require(x == expectedX && z == expectedZ && generationIndex == expectedIndex,
                "Chunk index entry does not match " + source);
            byte[] blocks = new byte[BLOCK_BYTES];
            input.readFully(blocks);
            Chunk chunk = new Chunk(x, z);
            int i = 0;
            for (int y = 0; y < Chunk.HEIGHT; y++) {
                for (int localZ = 0; localZ < Chunk.SIZE; localZ++) {
                    for (int localX = 0; localX < Chunk.SIZE; localX++, i += 2) {
                        int packed = (blocks[i] & 0xff) << 8 | blocks[i + 1] & 0xff;
                        if (packed == 0) continue;
                        int id = packed >>> BlockState.METADATA_BITS;
                        require(id < registrySize, "Unknown block ID " + id + " in " + source);
                        chunk.set(localX, y, localZ, packed);
                    }
                }
            }
            if (format >= 3) {
                input.readFully(chunk.lightData());
                chunk.setLightStored(true);
            }
            chunk.markGenerated(generationIndex);
            if (populated) chunk.markPopulated();
            chunk.markClean();
            return chunk;
        }
    }

    private static String fileName(int x, int z) { return "c." + x + "." + z + ".lcc"; }
    private static void require(boolean condition, String message) throws IOException {
        if (!condition) throw new IOException(message);
    }
    private static void moveAtomically(Path source, Path target) throws IOException {
        try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
