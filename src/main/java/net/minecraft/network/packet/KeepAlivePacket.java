package net.minecraft.network.packet;

import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;

/** Sent by the server now and then; the client echoes the id, which also gives the round-trip time. */
public record KeepAlivePacket(long id) implements Packet {
    @Override public void write(PacketBuffer out) { out.writeLong(id); }
    public static KeepAlivePacket read(PacketBuffer in) { return new KeepAlivePacket(in.readLong()); }
}
