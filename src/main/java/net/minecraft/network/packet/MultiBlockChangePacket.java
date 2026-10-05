package net.minecraft.network.packet;

import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.PacketException;

import java.util.Arrays;

/**
 * Several block changes in one chunk, sharing the chunk position. Each entry is
 * a local position packed into a short (4 bits x, 4 bits z, 8 bits y) and a
 * VarInt state id: 3-4 bytes a block against 9-12 for separate packets.
 */
public record MultiBlockChangePacket(int chunkX, int chunkZ, short[] positions, int[] states) implements Packet {
    public static final int MAX_CHANGES = 65536;

    public MultiBlockChangePacket {
        if (positions.length != states.length) throw new IllegalArgumentException("positions and states differ in length");
    }
    public static short pack(int localX, int y, int localZ) { return (short) ((localX & 15) << 12 | (localZ & 15) << 8 | y & 255); }
    public static int localX(short p) { return p >> 12 & 15; }
    public static int localZ(short p) { return p >> 8 & 15; }
    public static int y(short p) { return p & 255; }

    @Override public void write(PacketBuffer out) {
        out.writeInt(chunkX).writeInt(chunkZ).writeVarInt(positions.length);
        for (int i = 0; i < positions.length; i++) out.writeShort(positions[i]).writeVarInt(states[i]);
    }
    public static MultiBlockChangePacket read(PacketBuffer in) {
        int cx = in.readInt(), cz = in.readInt(), n = in.readVarInt();
        // Each entry takes at least 3 bytes, so a count the remaining bytes cannot hold is a lie: reject before allocating.
        if (n < 0 || n > MAX_CHANGES || (long) n * 3 > in.readableBytes()) throw new PacketException("Bad block change count " + n);
        short[] positions = new short[n];
        int[] states = new int[n];
        for (int i = 0; i < n; i++) { positions[i] = in.readShort(); states[i] = in.readVarInt(); }
        return new MultiBlockChangePacket(cx, cz, positions, states);
    }
    @Override public boolean equals(Object o) {
        return o instanceof MultiBlockChangePacket m && m.chunkX == chunkX && m.chunkZ == chunkZ
            && Arrays.equals(m.positions, positions) && Arrays.equals(m.states, states);
    }
    @Override public int hashCode() { return 31 * (31 * chunkX + chunkZ) + Arrays.hashCode(positions) * 7 + Arrays.hashCode(states); }
}
