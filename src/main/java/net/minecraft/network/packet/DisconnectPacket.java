package net.minecraft.network.packet;

import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;

public record DisconnectPacket(String reason) implements Packet {
    @Override public void write(PacketBuffer out) { out.writeString(reason); }
    public static DisconnectPacket read(PacketBuffer in) { return new DisconnectPacket(in.readString(256)); }
}
