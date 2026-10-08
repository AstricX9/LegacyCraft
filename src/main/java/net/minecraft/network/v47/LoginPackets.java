package net.minecraft.network.v47;

import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;

/** Login state. Offline mode only: no encryption request (0x01) is ever sent. */
public final class LoginPackets {
    private LoginPackets() {}
    public static final int MAX_NAME = 16;

    /** Serverbound 0x00. */
    public record Start(String name) implements Packet {
        @Override public void write(PacketBuffer out) { out.writeString(name); }
        public static Start read(PacketBuffer in) { return new Start(in.readString(MAX_NAME)); }
    }

    /** Clientbound 0x00: a chat component (JSON). */
    public record Disconnect(String reason) implements Packet {
        @Override public void write(PacketBuffer out) { out.writeString(reason); }
        public static Disconnect read(PacketBuffer in) { return new Disconnect(in.readString(32767)); }
    }

    /** Clientbound 0x02: the UUID as hyphenated text, then the name. */
    public record Success(String uuid, String name) implements Packet {
        @Override public void write(PacketBuffer out) { out.writeString(uuid).writeString(name); }
        public static Success read(PacketBuffer in) { return new Success(in.readString(36), in.readString(MAX_NAME)); }
    }

    /** Clientbound 0x03: packets this big or bigger are deflated from the next packet on, in both directions. */
    public record SetCompression(int threshold) implements Packet {
        @Override public void write(PacketBuffer out) { out.writeVarInt(threshold); }
        public static SetCompression read(PacketBuffer in) { return new SetCompression(in.readVarInt()); }
    }
}
