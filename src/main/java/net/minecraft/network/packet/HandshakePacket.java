package net.minecraft.network.packet;

import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.PacketException;
import net.minecraft.network.ProtocolState;

/** First packet from a client: the protocol version it speaks and which phase it wants next (LOGIN or STATUS). */
public record HandshakePacket(int protocolVersion, ProtocolState next) implements Packet {
    public static final int PROTOCOL_VERSION = 1;
    @Override public void write(PacketBuffer out) { out.writeVarInt(protocolVersion).writeVarInt(next.ordinal()); }
    public static HandshakePacket read(PacketBuffer in) {
        int version = in.readVarInt(), next = in.readVarInt();
        ProtocolState[] all = ProtocolState.values();
        if (next < 0 || next >= all.length) throw new PacketException("Bad next state " + next);
        return new HandshakePacket(version, all[next]);
    }
}
