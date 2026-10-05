package net.minecraft.network.packet;

import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;

/** One block changed. Bursts of changes in a chunk should use {@link MultiBlockChangePacket} instead. */
public record BlockChangePacket(int x, int y, int z, int stateId) implements Packet {
    @Override public void write(PacketBuffer out) { out.writeBlockPos(x, y, z).writeVarInt(stateId); }
    public static BlockChangePacket read(PacketBuffer in) {
        long p = in.readLong();
        return new BlockChangePacket(PacketBuffer.unpackX(p), PacketBuffer.unpackY(p), PacketBuffer.unpackZ(p), in.readVarInt());
    }
}
