package net.minecraft.network.v47;

import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.PacketException;
import net.minecraft.network.ProtocolState;

/** Handshaking 0x00: protocol version, the address and port the client typed, and the next state (1 status, 2 login). */
public record Handshake(int protocolVersion, String host, int port, ProtocolState next) implements Packet {
    @Override public void write(PacketBuffer out) {
        out.writeVarInt(protocolVersion).writeString(host).writeShort(port).writeVarInt(next == ProtocolState.STATUS ? 1 : 2);
    }
    public static Handshake read(PacketBuffer in) {
        int version = in.readVarInt();
        String host = in.readString(255);
        int port = in.readUnsignedShort();
        int next = in.readVarInt();
        ProtocolState state = switch (next) {
            case 1 -> ProtocolState.STATUS;
            case 2 -> ProtocolState.LOGIN;
            default -> throw new PacketException("Bad next state " + next);
        };
        return new Handshake(version, host, port, state);
    }
}
