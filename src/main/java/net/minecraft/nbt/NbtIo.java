package net.minecraft.nbt;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Reads and writes the Named Binary Tag format vanilla saves use (big-endian, optionally gzipped). */
public final class NbtIo {
    private NbtIo() {}

    /** Reads a gzipped NBT file such as {@code level.dat}; returns its root compound. */
    public static NbtTag.Compound readCompressed(Path file) throws IOException {
        try (InputStream in = new GZIPInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            return readRoot(new DataInputStream(in));
        }
    }

    /** Reads an uncompressed NBT stream (region chunks are already decompressed). */
    public static NbtTag.Compound read(InputStream stream) throws IOException {
        return readRoot(new DataInputStream(stream));
    }

    private static NbtTag.Compound readRoot(DataInput input) throws IOException {
        int type = input.readUnsignedByte();
        if (type != NbtTag.COMPOUND) throw new IOException("NBT root must be a compound, found type " + type);
        input.readUTF(); // the root's name, conventionally empty
        return (NbtTag.Compound) readPayload(input, NbtTag.COMPOUND);
    }

    private static NbtTag readPayload(DataInput input, int type) throws IOException {
        return switch (type) {
            case NbtTag.BYTE -> new NbtTag.Byte(input.readByte());
            case NbtTag.SHORT -> new NbtTag.Short(input.readShort());
            case NbtTag.INT -> new NbtTag.Int(input.readInt());
            case NbtTag.LONG -> new NbtTag.Long(input.readLong());
            case NbtTag.FLOAT -> new NbtTag.Float(input.readFloat());
            case NbtTag.DOUBLE -> new NbtTag.Double(input.readDouble());
            case NbtTag.BYTE_ARRAY -> {
                byte[] bytes = new byte[requireLength(input.readInt())];
                input.readFully(bytes);
                yield new NbtTag.ByteArray(bytes);
            }
            case NbtTag.STRING -> new NbtTag.Str(input.readUTF());
            case NbtTag.LIST -> {
                int elementType = input.readUnsignedByte();
                int length = requireLength(input.readInt());
                List<NbtTag> values = new ArrayList<>(Math.min(length, 1024));
                for (int i = 0; i < length; i++) values.add(readPayload(input, elementType));
                yield new NbtTag.NbtList(elementType, values);
            }
            case NbtTag.COMPOUND -> {
                NbtTag.Compound compound = new NbtTag.Compound();
                while (true) {
                    int entryType = input.readUnsignedByte();
                    if (entryType == NbtTag.END) break;
                    compound.put(input.readUTF(), readPayload(input, entryType));
                }
                yield compound;
            }
            case NbtTag.INT_ARRAY -> {
                int[] values = new int[requireLength(input.readInt())];
                for (int i = 0; i < values.length; i++) values[i] = input.readInt();
                yield new NbtTag.IntArray(values);
            }
            default -> throw new IOException("Unknown NBT tag type " + type);
        };
    }

    private static int requireLength(int length) throws IOException {
        if (length < 0) throw new IOException("Negative NBT array length " + length);
        return length;
    }

    /** Writes a gzipped NBT file (level.dat) atomically: to a temporary file first, then moved over the target. */
    public static void writeCompressed(NbtTag.Compound root, Path file) throws IOException {
        Path temporary = file.resolveSibling(file.getFileName() + "_new");
        try (OutputStream out = new GZIPOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary)))) {
            write(root, out);
        }
        try {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Writes an uncompressed NBT stream (servers.dat) with an empty root name. */
    public static void write(NbtTag.Compound root, OutputStream stream) throws IOException {
        DataOutputStream output = new DataOutputStream(stream);
        output.writeByte(NbtTag.COMPOUND);
        output.writeUTF("");
        writePayload(output, root);
        output.flush();
    }

    /** Reads an uncompressed NBT file, or null when it does not exist. */
    public static NbtTag.Compound readUncompressed(Path file) throws IOException {
        if (!Files.isRegularFile(file)) return null;
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
            return read(in);
        }
    }

    private static void writePayload(DataOutput output, NbtTag tag) throws IOException {
        // Java 17 compatible (the toolchain version): no pattern matching in switch.
        if (tag instanceof NbtTag.Byte value) output.writeByte(value.value());
        else if (tag instanceof NbtTag.Short value) output.writeShort(value.value());
        else if (tag instanceof NbtTag.Int value) output.writeInt(value.value());
        else if (tag instanceof NbtTag.Long value) output.writeLong(value.value());
        else if (tag instanceof NbtTag.Float value) output.writeFloat(value.value());
        else if (tag instanceof NbtTag.Double value) output.writeDouble(value.value());
        else if (tag instanceof NbtTag.ByteArray value) { output.writeInt(value.value().length); output.write(value.value()); }
        else if (tag instanceof NbtTag.Str value) output.writeUTF(value.value());
        else if (tag instanceof NbtTag.NbtList list) {
            output.writeByte(list.values().isEmpty() ? list.elementType() : list.values().get(0).type());
            output.writeInt(list.values().size());
            for (NbtTag element : list.values()) writePayload(output, element);
        } else if (tag instanceof NbtTag.Compound compound) {
            for (Map.Entry<String, NbtTag> entry : compound.entries().entrySet()) {
                output.writeByte(entry.getValue().type());
                output.writeUTF(entry.getKey());
                writePayload(output, entry.getValue());
            }
            output.writeByte(NbtTag.END);
        } else if (tag instanceof NbtTag.IntArray value) {
            output.writeInt(value.value().length);
            for (int element : value.value()) output.writeInt(element);
        } else {
            throw new IOException("Unknown NBT tag " + tag);
        }
    }
}
