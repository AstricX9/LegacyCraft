package net.minecraft.network;

/** A message on the wire. Decoding lives in the {@link PacketRegistry}, not on the instance, so packets stay immutable values. */
public interface Packet {
    void write(PacketBuffer out);
}
