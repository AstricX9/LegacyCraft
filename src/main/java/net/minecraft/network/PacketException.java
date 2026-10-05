package net.minecraft.network;

/** A malformed, oversized or truncated packet or frame. Safe to throw on untrusted input; the connection should be dropped. */
public final class PacketException extends IllegalArgumentException {
    public PacketException(String message) { super(message); }
    public PacketException(String message, Throwable cause) { super(message, cause); }
}
