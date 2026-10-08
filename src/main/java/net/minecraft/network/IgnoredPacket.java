package net.minecraft.network;

/** A packet received under an id registered with {@link PacketRegistry#ignore}: its id and unparsed payload. */
public record IgnoredPacket(int id, byte[] payload) implements Packet {
    @Override public void write(PacketBuffer out) { out.writeBytes(payload, 0, payload.length); }
}
