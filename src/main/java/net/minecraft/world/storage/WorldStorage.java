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
import java.util.Comparator;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Versioned, atomic world metadata and compressed chunk persistence. */
public final class WorldStorage {
    private static final int INDEX_MAGIC = 0x4c434957; // LCIW
    private static final int CHUNK_MAGIC = 0x4c434348; // LCCH
    /** 2 added the per-chunk "populated" flag; format 1 chunks load as populated (they were finished worlds). */
    private static final int FORMAT = 2;
    private static final String INDEX_FILE = "level.lci";

    private WorldStorage() {}
    public static boolean exists(Path directory) { return Files.isRegularFile(directory.resolve(INDEX_FILE)); }

    public static void save(Path directory, World world) throws IOException {
        Path chunksDirectory = directory.resolve("chunks");
        Files.createDirectories(chunksDirectory);
        List<Chunk> chunks = world.loadedChunks().stream()
            .sorted(Comparator.comparingLong(Chunk::generationIndex)).toList();
        for (Chunk chunk : chunks) writeChunk(chunksDirectory, chunk);

        Path target = directory.resolve(INDEX_FILE);
        Path temporary = directory.resolve(INDEX_FILE + ".tmp");
        try (var output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary)))) {
            output.writeInt(INDEX_MAGIC);
            output.writeInt(FORMAT);
            output.writeLong(world.seed());
            output.writeInt(chunks.size());
            for (Chunk chunk : chunks) {
                output.writeInt(chunk.chunkX());
                output.writeInt(chunk.chunkZ());
                output.writeLong(chunk.generationIndex());
            }
        }
        moveAtomically(temporary, target);
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
            require(format == 1 || format == FORMAT, "Unsupported world index version");
            World world = factory.apply(input.readLong());
            int count = input.readInt();
            require(count >= 0, "Negative chunk count");
            for (int i = 0; i < count; i++) {
                int x = input.readInt(), z = input.readInt();
                long generationIndex = input.readLong();
                world.restoreChunk(readChunk(directory.resolve("chunks"), x, z, generationIndex, blocks.size()));
            }
            return world;
        }
    }

    private static void writeChunk(Path directory, Chunk chunk) throws IOException {
        Path target = directory.resolve(fileName(chunk.chunkX(), chunk.chunkZ()));
        Path temporary = directory.resolve(fileName(chunk.chunkX(), chunk.chunkZ()) + ".tmp");
        try (var output = new DataOutputStream(new GZIPOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary))))) {
            output.writeInt(CHUNK_MAGIC);
            output.writeInt(FORMAT);
            output.writeInt(chunk.chunkX());
            output.writeInt(chunk.chunkZ());
            output.writeLong(chunk.generationIndex());
            output.writeBoolean(chunk.isPopulated());
            for (int y = 0; y < Chunk.HEIGHT; y++)
                for (int z = 0; z < Chunk.SIZE; z++)
                    for (int x = 0; x < Chunk.SIZE; x++) output.writeShort(chunk.get(x, y, z));
        }
        moveAtomically(temporary, target);
    }

    private static Chunk readChunk(Path directory, int expectedX, int expectedZ,
                                   long expectedIndex, int registrySize) throws IOException {
        Path source = directory.resolve(fileName(expectedX, expectedZ));
        try (var input = new DataInputStream(new GZIPInputStream(new BufferedInputStream(Files.newInputStream(source))))) {
            require(input.readInt() == CHUNK_MAGIC, "Invalid chunk file " + source);
            int format = input.readInt();
            require(format == 1 || format == FORMAT, "Unsupported chunk version");
            int x = input.readInt(), z = input.readInt();
            long generationIndex = input.readLong();
            boolean populated = format == 1 || input.readBoolean();
            require(x == expectedX && z == expectedZ && generationIndex == expectedIndex,
                "Chunk index entry does not match " + source);
            Chunk chunk = new Chunk(x, z);
            for (int y = 0; y < Chunk.HEIGHT; y++) {
                for (int localZ = 0; localZ < Chunk.SIZE; localZ++) {
                    for (int localX = 0; localX < Chunk.SIZE; localX++) {
                        int packed = input.readUnsignedShort();
                        int id = packed >>> BlockState.METADATA_BITS;
                        require(id < registrySize, "Unknown block ID " + id + " in " + source);
                        chunk.set(localX, y, localZ, packed);
                    }
                }
            }
            chunk.markGenerated(generationIndex);
            if (populated) chunk.markPopulated();
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
