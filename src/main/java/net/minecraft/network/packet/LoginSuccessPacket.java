package net.minecraft.network.packet;

import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;

/** Login accepted: the player's identity, and the connection moves to PLAY. */
public record LoginSuccessPacket(long uuidMost, long uuidLeast, String name) implements Packet {
    @Override public void write(PacketBuffer out) { out.writeLong(uuidMost).writeLong(uuidLeast).writeString(name); }
    public static LoginSuccessPacket read(PacketBuffer in) {
        return new LoginSuccessPacket(in.readLong(), in.readLong(), in.readString(LoginStartPacket.MAX_NAME));
    }
}
