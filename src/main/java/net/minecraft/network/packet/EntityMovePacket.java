package net.minecraft.network.packet;

import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;

/**
 * PLACEHOLDER, not protocol 47 (HYPOTHESIS: 1.8 sent byte deltas in 1/32 block, and
 * short deltas arrived in a later protocol). Clientbound: an entity moved a small amount. A delta in 1/4096-block fixed
 * point (so a move of up to 8 blocks fits in a short) and angles in 1/256
 * turns: 10 bytes of payload against 29 for absolute doubles and floats.
 * Moves over 8 blocks must be sent as a teleport instead ({@link #fits}).
 */
public record EntityMovePacket(int entityId, short dx, short dy, short dz, byte yaw, byte pitch, boolean onGround) implements Packet {
    public static final double SCALE = 4096.0;
    public static final double MAX_DELTA = Short.MAX_VALUE / SCALE;

    public static boolean fits(double dx, double dy, double dz) {
        return Math.abs(dx) < MAX_DELTA && Math.abs(dy) < MAX_DELTA && Math.abs(dz) < MAX_DELTA;
    }
    public static short fixed(double blocks) { return (short) Math.round(blocks * SCALE); }
    public static byte angle(float degrees) { return (byte) Math.floor(degrees * 256f / 360f); }
    public static float degrees(byte angle) { return angle * 360f / 256f; }

    @Override public void write(PacketBuffer out) {
        out.writeVarInt(entityId).writeShort(dx).writeShort(dy).writeShort(dz).writeByte(yaw).writeByte(pitch).writeBoolean(onGround);
    }
    public static EntityMovePacket read(PacketBuffer in) {
        return new EntityMovePacket(in.readVarInt(), in.readShort(), in.readShort(), in.readShort(), in.readByte(), in.readByte(), in.readBoolean());
    }
}
