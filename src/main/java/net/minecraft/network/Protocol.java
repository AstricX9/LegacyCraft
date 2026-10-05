package net.minecraft.network;

import net.minecraft.network.PacketRegistry.Direction;
import net.minecraft.network.packet.*;

import static net.minecraft.network.PacketRegistry.Direction.CLIENTBOUND;
import static net.minecraft.network.PacketRegistry.Direction.SERVERBOUND;

/**
 * PLACEHOLDER packet set (HYPOTHESIS, see docs/SIMULATION_SEAM.md): the ids and
 * payloads below were designed here to exercise the codec and are NOT the
 * protocol-47 wire format the project targets (master context 1.2, 49, Milestone 8).
 * They are to be replaced by a registry built from a version-47 reference table
 * or a vanilla 1.8.9 capture. Framing, VarInt and compression in
 * {@link PacketCodec} follow the 1.8 scheme as recalled and still need checking
 * against a capture.
 */
public final class Protocol {
    private Protocol() {}

    public static PacketRegistry standard() {
        return new PacketRegistry()
            .register(ProtocolState.HANDSHAKE, SERVERBOUND, 0, HandshakePacket.class, HandshakePacket::read)
            .register(ProtocolState.LOGIN, SERVERBOUND, 0, LoginStartPacket.class, LoginStartPacket::read)
            .register(ProtocolState.LOGIN, CLIENTBOUND, 0, DisconnectPacket.class, DisconnectPacket::read)
            .register(ProtocolState.LOGIN, CLIENTBOUND, 1, LoginSuccessPacket.class, LoginSuccessPacket::read)
            .register(ProtocolState.PLAY, CLIENTBOUND, 0, KeepAlivePacket.class, KeepAlivePacket::read)
            .register(ProtocolState.PLAY, SERVERBOUND, 0, KeepAlivePacket.class, KeepAlivePacket::read)
            .register(ProtocolState.PLAY, CLIENTBOUND, 1, DisconnectPacket.class, DisconnectPacket::read)
            .register(ProtocolState.PLAY, SERVERBOUND, 1, PlayerPositionPacket.class, PlayerPositionPacket::read)
            .register(ProtocolState.PLAY, CLIENTBOUND, 2, EntityMovePacket.class, EntityMovePacket::read)
            .register(ProtocolState.PLAY, CLIENTBOUND, 3, BlockChangePacket.class, BlockChangePacket::read)
            .register(ProtocolState.PLAY, CLIENTBOUND, 4, MultiBlockChangePacket.class, MultiBlockChangePacket::read);
    }
}
