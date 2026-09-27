package net.minecraft.world.storage.anvil;

import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtTag;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/**
 * Reads a vanilla Anvil region file ({@code r.X.Z.mca}, master context §12):
 * an 8 KiB header of 1024 chunk locations and timestamps, then each chunk as a
 * length-prefixed, compressed NBT compound in 4 KiB sectors.
 */
public final class RegionFile implements AutoCloseable {
    public static final int CHUNKS = 32;
    private static final int SECTOR = 4096;

    private final byte[] data;
    private final int regionX, regionZ;

    private RegionFile(byte[] data, int regionX, int regionZ) {
        this.data = data;
        this.regionX = regionX;
        this.regionZ = regionZ;
    }

    public static RegionFile open(Path file) throws IOException {
        String[] parts = file.getFileName().toString().split("\\.");
        if (parts.length != 4 || !parts[0].equals("r") || !parts[3].equals("mca"))
            throw new IOException("Not a region file: " + file.getFileName());
        return new RegionFile(Files.readAllBytes(file), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
    }

    public int regionX() { return regionX; }
    public int regionZ() { return regionZ; }

    /** Whether this region holds the chunk at the given world chunk coordinates. */
    public boolean contains(int chunkX, int chunkZ) {
        return Math.floorDiv(chunkX, CHUNKS) == regionX && Math.floorDiv(chunkZ, CHUNKS) == regionZ;
    }

    /** The chunk's root NBT compound, or {@code null} if that chunk was never saved. */
    public NbtTag.Compound readChunk(int chunkX, int chunkZ) throws IOException {
        int index = (Math.floorMod(chunkX, CHUNKS) + Math.floorMod(chunkZ, CHUNKS) * CHUNKS) * 4;
        int offset = (data[index] & 0xff) << 16 | (data[index + 1] & 0xff) << 8 | (data[index + 2] & 0xff);
        int sectors = data[index + 3] & 0xff;
        if (offset == 0 || sectors == 0) return null;
        int start = offset * SECTOR;
        if (start + 5 > data.length) throw new IOException("Chunk " + chunkX + "," + chunkZ + " points outside the region file");
        int length = (data[start] & 0xff) << 24 | (data[start + 1] & 0xff) << 16
            | (data[start + 2] & 0xff) << 8 | (data[start + 3] & 0xff);
        int compression = data[start + 4] & 0xff;
        InputStream payload = new ByteArrayInputStream(data, start + 5, length - 1);
        try (InputStream in = switch (compression) {
            case 1 -> new GZIPInputStream(payload);
            case 2 -> new InflaterInputStream(payload);
            default -> throw new IOException("Unknown chunk compression " + compression);
        }) {
            return NbtIo.read(in);
        }
    }

    @Override public void close() {}
}
