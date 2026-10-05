package net.minecraft.network.packet;

import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;

public record LoginStartPacket(String name) implements Packet {
    public static final int MAX_NAME = 16;
    @Override public void write(PacketBuffer out) { out.writeString(name); }
    public static LoginStartPacket read(PacketBuffer in) { return new LoginStartPacket(in.readString(MAX_NAME)); }
}
