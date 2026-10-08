package net.minecraft.network.v47;

import net.minecraft.network.PacketRegistry;

import static net.minecraft.network.PacketRegistry.Direction.CLIENTBOUND;
import static net.minecraft.network.PacketRegistry.Direction.SERVERBOUND;
import static net.minecraft.network.ProtocolState.*;

/**
 * Minecraft 1.8.x (protocol version 47) packet ids. Every serverbound play id
 * a 1.8.9 client can send is registered, those not handled yet as ignored, so
 * a vanilla client never trips an unknown-id error.
 */
public final class Protocol47 {
    public static final int VERSION = 47;
    public static final String VERSION_NAME = "1.8.9";
    private Protocol47() {}

    private static final PacketRegistry REGISTRY = build();

    /** The shared, read-only registry. */
    public static PacketRegistry registry() { return REGISTRY; }

    private static PacketRegistry build() {
        PacketRegistry r = new PacketRegistry()
            .register(HANDSHAKE, SERVERBOUND, 0x00, Handshake.class, Handshake::read)

            .register(STATUS, SERVERBOUND, 0x00, StatusPackets.Request.class, StatusPackets.Request::read)
            .register(STATUS, SERVERBOUND, 0x01, StatusPackets.Ping.class, StatusPackets.Ping::read)
            .register(STATUS, CLIENTBOUND, 0x00, StatusPackets.Response.class, StatusPackets.Response::read)
            .register(STATUS, CLIENTBOUND, 0x01, StatusPackets.Ping.class, StatusPackets.Ping::read)

            .register(LOGIN, SERVERBOUND, 0x00, LoginPackets.Start.class, LoginPackets.Start::read)
            .ignore(LOGIN, SERVERBOUND, 0x01)                                   // encryption response (online mode)
            .register(LOGIN, CLIENTBOUND, 0x00, LoginPackets.Disconnect.class, LoginPackets.Disconnect::read)
            .register(LOGIN, CLIENTBOUND, 0x02, LoginPackets.Success.class, LoginPackets.Success::read)
            .register(LOGIN, CLIENTBOUND, 0x03, LoginPackets.SetCompression.class, LoginPackets.SetCompression::read)

            .register(PLAY, SERVERBOUND, 0x00, Serverbound.KeepAlive.class, Serverbound.KeepAlive::read)
            .register(PLAY, SERVERBOUND, 0x01, Serverbound.Chat.class, Serverbound.Chat::read)
            .register(PLAY, SERVERBOUND, 0x02, Serverbound.UseEntity.class, Serverbound.UseEntity::read)
            .decodeOnly(PLAY, SERVERBOUND, 0x03, Serverbound.Move::readGround)
            .decodeOnly(PLAY, SERVERBOUND, 0x04, Serverbound.Move::readPosition)
            .decodeOnly(PLAY, SERVERBOUND, 0x05, Serverbound.Move::readLook)
            .register(PLAY, SERVERBOUND, 0x06, Serverbound.Move.class, Serverbound.Move::readPositionLook)
            .register(PLAY, SERVERBOUND, 0x07, Serverbound.Digging.class, Serverbound.Digging::read)
            .register(PLAY, SERVERBOUND, 0x08, Serverbound.BlockPlacement.class, Serverbound.BlockPlacement::read)
            .register(PLAY, SERVERBOUND, 0x09, Serverbound.HeldItemChange.class, Serverbound.HeldItemChange::read)
            .register(PLAY, SERVERBOUND, 0x0A, Serverbound.Animation.class, Serverbound.Animation::read)
            .register(PLAY, SERVERBOUND, 0x0B, Serverbound.EntityAction.class, Serverbound.EntityAction::read)
            .register(PLAY, SERVERBOUND, 0x0D, Serverbound.CloseWindow.class, Serverbound.CloseWindow::read)
            .register(PLAY, SERVERBOUND, 0x0E, Serverbound.ClickWindow.class, Serverbound.ClickWindow::read)
            .register(PLAY, SERVERBOUND, 0x10, Serverbound.CreativeInventoryAction.class, Serverbound.CreativeInventoryAction::read)
            .register(PLAY, SERVERBOUND, 0x13, Serverbound.PlayerAbilities.class, Serverbound.PlayerAbilities::read)
            .register(PLAY, SERVERBOUND, 0x15, Serverbound.ClientSettings.class, Serverbound.ClientSettings::read)
            .register(PLAY, SERVERBOUND, 0x16, Serverbound.ClientStatus.class, Serverbound.ClientStatus::read);
        // Not handled yet: steer vehicle, confirm transaction, enchant item, update sign,
        // tab complete, plugin message, spectate, resource pack status.
        for (int id : new int[]{0x0C, 0x0F, 0x11, 0x12, 0x14, 0x17, 0x18, 0x19}) r.ignore(PLAY, SERVERBOUND, id);

        return r
            .register(PLAY, CLIENTBOUND, 0x00, Clientbound.KeepAlive.class, Clientbound.KeepAlive::read)
            .register(PLAY, CLIENTBOUND, 0x01, Clientbound.JoinGame.class, Clientbound.JoinGame::read)
            .register(PLAY, CLIENTBOUND, 0x02, Clientbound.Chat.class, Clientbound.Chat::read)
            .register(PLAY, CLIENTBOUND, 0x03, Clientbound.TimeUpdate.class, Clientbound.TimeUpdate::read)
            .register(PLAY, CLIENTBOUND, 0x04, Clientbound.EntityEquipment.class, Clientbound.EntityEquipment::read)
            .register(PLAY, CLIENTBOUND, 0x05, Clientbound.SpawnPosition.class, Clientbound.SpawnPosition::read)
            .register(PLAY, CLIENTBOUND, 0x06, Clientbound.UpdateHealth.class, Clientbound.UpdateHealth::read)
            .register(PLAY, CLIENTBOUND, 0x07, Clientbound.Respawn.class, Clientbound.Respawn::read)
            .register(PLAY, CLIENTBOUND, 0x08, Clientbound.PlayerPositionLook.class, Clientbound.PlayerPositionLook::read)
            .register(PLAY, CLIENTBOUND, 0x09, Clientbound.HeldItemChange.class, Clientbound.HeldItemChange::read)
            .register(PLAY, CLIENTBOUND, 0x0B, Clientbound.Animation.class, Clientbound.Animation::read)
            .register(PLAY, CLIENTBOUND, 0x0C, Clientbound.SpawnPlayer.class, Clientbound.SpawnPlayer::read)
            .register(PLAY, CLIENTBOUND, 0x0D, Clientbound.CollectItem.class, Clientbound.CollectItem::read)
            .register(PLAY, CLIENTBOUND, 0x0E, Clientbound.SpawnObject.class, Clientbound.SpawnObject::read)
            .register(PLAY, CLIENTBOUND, 0x0F, Clientbound.SpawnMob.class, Clientbound.SpawnMob::read)
            .register(PLAY, CLIENTBOUND, 0x12, Clientbound.EntityVelocity.class, Clientbound.EntityVelocity::read)
            .register(PLAY, CLIENTBOUND, 0x13, Clientbound.DestroyEntities.class, Clientbound.DestroyEntities::read)
            .register(PLAY, CLIENTBOUND, 0x15, Clientbound.EntityRelativeMove.class, Clientbound.EntityRelativeMove::read)
            .register(PLAY, CLIENTBOUND, 0x16, Clientbound.EntityLook.class, Clientbound.EntityLook::read)
            .register(PLAY, CLIENTBOUND, 0x17, Clientbound.EntityLookAndRelativeMove.class, Clientbound.EntityLookAndRelativeMove::read)
            .register(PLAY, CLIENTBOUND, 0x18, Clientbound.EntityTeleport.class, Clientbound.EntityTeleport::read)
            .register(PLAY, CLIENTBOUND, 0x19, Clientbound.EntityHeadLook.class, Clientbound.EntityHeadLook::read)
            .register(PLAY, CLIENTBOUND, 0x1A, Clientbound.EntityStatus.class, Clientbound.EntityStatus::read)
            .register(PLAY, CLIENTBOUND, 0x1C, Clientbound.EntityMetadata.class, Clientbound.EntityMetadata::read)
            .register(PLAY, CLIENTBOUND, 0x21, Clientbound.ChunkData.class, Clientbound.ChunkData::read)
            .register(PLAY, CLIENTBOUND, 0x22, Clientbound.MultiBlockChange.class, Clientbound.MultiBlockChange::read)
            .register(PLAY, CLIENTBOUND, 0x23, Clientbound.BlockChange.class, Clientbound.BlockChange::read)
            .register(PLAY, CLIENTBOUND, 0x27, Clientbound.Explosion.class, Clientbound.Explosion::read)
            .register(PLAY, CLIENTBOUND, 0x28, Clientbound.Effect.class, Clientbound.Effect::read)
            .register(PLAY, CLIENTBOUND, 0x29, Clientbound.SoundEffect.class, Clientbound.SoundEffect::read)
            .register(PLAY, CLIENTBOUND, 0x2B, Clientbound.ChangeGameState.class, Clientbound.ChangeGameState::read)
            .register(PLAY, CLIENTBOUND, 0x2D, Clientbound.OpenWindow.class, Clientbound.OpenWindow::read)
            .register(PLAY, CLIENTBOUND, 0x2E, Clientbound.CloseWindow.class, Clientbound.CloseWindow::read)
            .register(PLAY, CLIENTBOUND, 0x2F, Clientbound.SetSlot.class, Clientbound.SetSlot::read)
            .register(PLAY, CLIENTBOUND, 0x30, Clientbound.WindowItems.class, Clientbound.WindowItems::read)
            .register(PLAY, CLIENTBOUND, 0x32, Clientbound.ConfirmTransaction.class, Clientbound.ConfirmTransaction::read)
            .register(PLAY, CLIENTBOUND, 0x38, Clientbound.PlayerListItem.class, Clientbound.PlayerListItem::read)
            .register(PLAY, CLIENTBOUND, 0x39, Clientbound.PlayerAbilities.class, Clientbound.PlayerAbilities::read)
            .register(PLAY, CLIENTBOUND, 0x40, Clientbound.Disconnect.class, Clientbound.Disconnect::read)
            .register(PLAY, CLIENTBOUND, 0x41, Clientbound.ServerDifficulty.class, Clientbound.ServerDifficulty::read);
    }
}
