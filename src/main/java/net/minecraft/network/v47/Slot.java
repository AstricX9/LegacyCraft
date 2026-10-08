package net.minecraft.network.v47;

import net.minecraft.network.PacketBuffer;
import net.minecraft.network.PacketException;

/**
 * An item stack on the wire: item id (-1 for an empty slot), count, damage,
 * then an NBT compound or a single 0 byte for none. The NBT is kept as raw
 * bytes; {@link #skipNbt} walks it only to find where it ends.
 */
public record Slot(int itemId, int count, int damage, byte[] nbt) {
    public static final Slot EMPTY = new Slot(-1, 0, 0, null);
    private static final int MAX_NBT_DEPTH = 64;

    public boolean isEmpty() { return itemId < 0; }

    public void write(PacketBuffer out) {
        out.writeShort(itemId);
        if (itemId < 0) return;
        out.writeByte(count).writeShort(damage);
        if (nbt == null) out.writeByte(0);
        else out.writeBytes(nbt, 0, nbt.length);
    }

    public static Slot read(PacketBuffer in) {
        int id = in.readShort();
        if (id < 0) return EMPTY;
        int count = in.readByte(), damage = in.readShort();
        int start = in.readerIndex();
        int type = in.readUnsignedByte();
        if (type == 0) return new Slot(id, count, damage, null);
        if (type != 10) throw new PacketException("Item NBT must be a compound, found type " + type);
        in.readBytes(in.readUnsignedShort());          // the root's name
        skipNbt(in, 10, 0);
        int length = in.readerIndex() - start;
        byte[] nbt = java.util.Arrays.copyOfRange(in.array(), start, start + length);
        return new Slot(id, count, damage, nbt);
    }

    /** Steps over one tag payload of {@code type}, checking every length against the bytes actually left. */
    static void skipNbt(PacketBuffer in, int type, int depth) {
        if (depth > MAX_NBT_DEPTH) throw new PacketException("NBT nested too deeply");
        switch (type) {
            case 1 -> in.readBytes(1);
            case 2 -> in.readBytes(2);
            case 3, 5 -> in.readBytes(4);
            case 4, 6 -> in.readBytes(8);
            case 7 -> in.readBytes(length(in.readInt(), 1, in));
            case 8 -> in.readBytes(in.readUnsignedShort());
            case 9 -> {
                int element = in.readUnsignedByte();
                int n = length(in.readInt(), 1, in);
                if (n > 0 && (element == 0 || element > 11)) throw new PacketException("Bad NBT list type " + element);
                for (int i = 0; i < n; i++) skipNbt(in, element, depth + 1);
            }
            case 10 -> {
                while (true) {
                    int child = in.readUnsignedByte();
                    if (child == 0) return;
                    if (child > 11) throw new PacketException("Bad NBT tag type " + child);
                    in.readBytes(in.readUnsignedShort());
                    skipNbt(in, child, depth + 1);
                }
            }
            case 11 -> in.readBytes(length(in.readInt(), 4, in) * 4);
            default -> throw new PacketException("Bad NBT tag type " + type);
        }
    }

    /** A count from the wire, rejected when even the smallest elements could not fit in what is left. */
    private static int length(int n, int minBytes, PacketBuffer in) {
        if (n < 0 || (long) n * minBytes > in.readableBytes()) throw new PacketException("Bad NBT length " + n);
        return n;
    }
}
