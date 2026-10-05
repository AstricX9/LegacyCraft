package net.minecraft.network;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Which packets exist in each protocol state and direction, by wire id.
 * Decoding is an array lookup by id; encoding is a lookup by class. Built once
 * at start-up and then read-only, so it is safe to share between connections.
 */
public final class PacketRegistry {
    /** Serverbound = client to server. */
    public enum Direction { SERVERBOUND, CLIENTBOUND;
        public Direction opposite() { return this == SERVERBOUND ? CLIENTBOUND : SERVERBOUND; } }

    private static final int MAX_ID = 256;
    @SuppressWarnings("unchecked")
    private final Function<PacketBuffer, ? extends Packet>[][] decoders = new Function[ProtocolState.values().length * 2][MAX_ID];
    @SuppressWarnings("unchecked")
    private final Map<Class<? extends Packet>, Integer>[] ids = new Map[ProtocolState.values().length * 2];

    public PacketRegistry() { for (int i = 0; i < ids.length; i++) ids[i] = new HashMap<>(); }

    private static int slot(ProtocolState state, Direction direction) { return state.ordinal() * 2 + direction.ordinal(); }

    public <P extends Packet> PacketRegistry register(ProtocolState state, Direction direction, int id, Class<P> type,
                                                       Function<PacketBuffer, P> decoder) {
        if (id < 0 || id >= MAX_ID) throw new IllegalArgumentException("Packet id out of range: " + id);
        int s = slot(state, direction);
        if (decoders[s][id] != null) throw new IllegalStateException("Duplicate packet id " + id + " in " + state + "/" + direction);
        if (ids[s].putIfAbsent(type, id) != null) throw new IllegalStateException(type.getSimpleName() + " registered twice in " + state + "/" + direction);
        decoders[s][id] = decoder;
        return this;
    }

    /** The decoder for {@code id}, or null if no such packet exists in that state. */
    Function<PacketBuffer, ? extends Packet> decoder(ProtocolState state, Direction direction, int id) {
        return id < 0 || id >= MAX_ID ? null : decoders[slot(state, direction)][id];
    }

    int idOf(ProtocolState state, Direction direction, Packet packet) {
        Integer id = ids[slot(state, direction)].get(packet.getClass());
        if (id == null) throw new IllegalArgumentException(packet.getClass().getSimpleName() + " cannot be sent in " + state + "/" + direction);
        return id;
    }
}
