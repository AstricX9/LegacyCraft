package net.minecraft.network.v47;

import net.minecraft.network.PacketBuffer;
import net.minecraft.network.PacketException;

/**
 * Entity metadata on the wire: entries of {@code (type << 5 | index)} then
 * the value, ended by 0x7F. Types: 0 byte, 1 short, 2 int, 3 float, 4 string,
 * 5 slot, 6 three ints, 7 three floats. Kept as encoded bytes; this builds
 * them and finds where a received list ends.
 */
public final class Metadata {
    private static final int END = 0x7F;
    private final PacketBuffer out = PacketBuffer.writing(32);

    public Metadata putByte(int index, int value) { out.writeByte(index).writeByte(value); return this; }
    public Metadata putShort(int index, int value) { out.writeByte(1 << 5 | index).writeShort(value); return this; }
    public Metadata putInt(int index, int value) { out.writeByte(2 << 5 | index).writeInt(value); return this; }
    public Metadata putFloat(int index, float value) { out.writeByte(3 << 5 | index).writeFloat(value); return this; }
    public Metadata putString(int index, String value) { out.writeByte(4 << 5 | index).writeString(value); return this; }
    public Metadata putSlot(int index, Slot value) { out.writeByte(5 << 5 | index); value.write(out); return this; }

    /** The entries followed by the end marker. */
    public byte[] build() {
        PacketBuffer done = PacketBuffer.writing(out.writerIndex() + 1);
        done.writeBytes(out);
        done.writeByte(END);
        return done.toByteArray();
    }

    /** Reads one metadata list, end marker included, as raw bytes. */
    public static byte[] read(PacketBuffer in) {
        int start = in.readerIndex();
        for (int entries = 0; ; entries++) {
            if (entries > 64) throw new PacketException("Too many metadata entries");
            int key = in.readUnsignedByte();
            if (key == END) break;
            switch (key >> 5) {
                case 0 -> in.readBytes(1);
                case 1 -> in.readBytes(2);
                case 2, 3 -> in.readBytes(4);
                case 4 -> in.readString(32767);
                case 5 -> Slot.read(in);
                case 6, 7 -> in.readBytes(12);
                default -> throw new PacketException("Bad metadata type " + (key >> 5));
            }
        }
        return java.util.Arrays.copyOfRange(in.array(), start, in.readerIndex());
    }
}
