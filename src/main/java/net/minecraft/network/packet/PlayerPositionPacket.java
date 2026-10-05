package net.minecraft.network.packet;

import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;

/** Serverbound: where the client says the player is (the server still validates it). */
public record PlayerPositionPacket(double x, double y, double z, float yaw, float pitch, boolean onGround) implements Packet {
    @Override public void write(PacketBuffer out) {
        out.writeDouble(x).writeDouble(y).writeDouble(z).writeFloat(yaw).writeFloat(pitch).writeBoolean(onGround);
    }
    public static PlayerPositionPacket read(PacketBuffer in) {
        double x = in.readDouble(), y = in.readDouble(), z = in.readDouble();
        float yaw = in.readFloat(), pitch = in.readFloat();
        boolean ground = in.readBoolean();
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Float.isFinite(yaw) || !Float.isFinite(pitch))
            throw new net.minecraft.network.PacketException("Non-finite position");
        return new PlayerPositionPacket(x, y, z, yaw, pitch, ground);
    }
}
