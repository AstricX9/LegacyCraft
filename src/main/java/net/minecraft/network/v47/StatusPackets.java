package net.minecraft.network.v47;

import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;

/** The server-list ping: request and JSON response (0x00), then ping and pong carrying the same long (0x01). */
public final class StatusPackets {
    private StatusPackets() {}

    public record Request() implements Packet {
        @Override public void write(PacketBuffer out) {}
        public static Request read(PacketBuffer in) { return new Request(); }
    }

    /** {@code {"version":{...},"players":{...},"description":{...}}}. */
    public record Response(String json) implements Packet {
        @Override public void write(PacketBuffer out) { out.writeString(json); }
        public static Response read(PacketBuffer in) { return new Response(in.readString(32767)); }
    }

    public record Ping(long payload) implements Packet {
        @Override public void write(PacketBuffer out) { out.writeLong(payload); }
        public static Ping read(PacketBuffer in) { return new Ping(in.readLong()); }
    }
}
