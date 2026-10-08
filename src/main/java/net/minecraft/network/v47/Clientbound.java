package net.minecraft.network.v47;

import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.PacketException;

import java.util.List;
import java.util.UUID;

/**
 * Play-state packets the server sends. Ids are listed in {@link Protocol47}.
 * Entity positions in spawn and teleport packets are fixed point (block * 32),
 * relative moves are bytes in the same 1/32 unit, and angles are bytes
 * (256 steps per turn).
 */
public final class Clientbound {
    private Clientbound() {}

    public static int fixed(double blocks) { return (int) Math.floor(blocks * 32.0); }
    public static int angle(float degrees) { return (int) Math.floor(degrees * 256f / 360f) & 0xFF; }

    private static void writeUuid(PacketBuffer out, UUID uuid) { out.writeLong(uuid.getMostSignificantBits()).writeLong(uuid.getLeastSignificantBits()); }
    private static UUID readUuid(PacketBuffer in) { return new UUID(in.readLong(), in.readLong()); }

    /** 0x00: the client must echo the id; a connection silent for 30 seconds is dropped. */
    public record KeepAlive(int id) implements Packet {
        @Override public void write(PacketBuffer out) { out.writeVarInt(id); }
        public static KeepAlive read(PacketBuffer in) { return new KeepAlive(in.readVarInt()); }
    }

    /** 0x01. Game mode 0-3 (bit 3 hardcore); dimension -1 nether, 0 overworld, 1 end; level type "default", "flat"... */
    public record JoinGame(int entityId, int gameMode, int dimension, int difficulty, int maxPlayers, String levelType,
                           boolean reducedDebugInfo) implements Packet {
        @Override public void write(PacketBuffer out) {
            out.writeInt(entityId).writeByte(gameMode).writeByte(dimension).writeByte(difficulty).writeByte(maxPlayers)
                .writeString(levelType).writeBoolean(reducedDebugInfo);
        }
        public static JoinGame read(PacketBuffer in) {
            return new JoinGame(in.readInt(), in.readUnsignedByte(), in.readByte(), in.readUnsignedByte(), in.readUnsignedByte(),
                in.readString(16), in.readBoolean());
        }
    }

    /** 0x02: a chat component (JSON); position 0 chat, 1 system message, 2 above the hotbar. */
    public record Chat(String json, int position) implements Packet {
        @Override public void write(PacketBuffer out) { out.writeString(json).writeByte(position); }
        public static Chat read(PacketBuffer in) { return new Chat(in.readString(32767), in.readByte()); }
    }

    /** 0x03: world age, and time of day (negative stops the client's clock). */
    public record TimeUpdate(long worldAge, long timeOfDay) implements Packet {
        @Override public void write(PacketBuffer out) { out.writeLong(worldAge).writeLong(timeOfDay); }
        public static TimeUpdate read(PacketBuffer in) { return new TimeUpdate(in.readLong(), in.readLong()); }
    }

    /** 0x04: slot 0 held item, 1-4 boots to helmet. */
    public record EntityEquipment(int entityId, int slot, Slot item) implements Packet {
        @Override public void write(PacketBuffer out) { out.writeVarInt(entityId).writeShort(slot); item.write(out); }
        public static EntityEquipment read(PacketBuffer in) { return new EntityEquipment(in.readVarInt(), in.readShort(), Slot.read(in)); }
    }

    /** 0x05: where the compass points. */
    public record SpawnPosition(int x, int y, int z) implements Packet {
        @Override public void write(PacketBuffer out) { out.writeBlockPos(x, y, z); }
        public static SpawnPosition read(PacketBuffer in) {
            long p = in.readLong();
            return new SpawnPosition(PacketBuffer.unpackX(p), PacketBuffer.unpackY(p), PacketBuffer.unpackZ(p));
        }
    }

    /** 0x06. */
    public record UpdateHealth(float health, int food, float saturation) implements Packet {
        @Override public void write(PacketBuffer out) { out.writeFloat(health).writeVarInt(food).writeFloat(saturation); }
        public static UpdateHealth read(PacketBuffer in) { return new UpdateHealth(in.readFloat(), in.readVarInt(), in.readFloat()); }
    }

    /** 0x07. */
    public record Respawn(int dimension, int difficulty, int gameMode, String levelType) implements Packet {
        @Override public void write(PacketBuffer out) { out.writeInt(dimension).writeByte(difficulty).writeByte(gameMode).writeString(levelType); }
        public static Respawn read(PacketBuffer in) {
            return new Respawn(in.readInt(), in.readUnsignedByte(), in.readUnsignedByte(), in.readString(16));
        }
    }

    /**
     * 0x08: moves the client's own player. Each flag bit (x 0x01, y 0x02,
     * z 0x04, yaw 0x08, pitch 0x10) makes that field relative; 0 is absolute.
     * Y is the feet.
     */
    public record PlayerPositionLook(double x, double y, double z, float yaw, float pitch, int flags) implements Packet {
        @Override public void write(PacketBuffer out) {
            out.writeDouble(x).writeDouble(y).writeDouble(z).writeFloat(yaw).writeFloat(pitch).writeByte(flags);
        }
        public static PlayerPositionLook read(PacketBuffer in) {
            return new PlayerPositionLook(in.readDouble(), in.readDouble(), in.readDouble(), in.readFloat(), in.readFloat(), in.readByte());
        }
    }

    /** 0x09. */
    public record HeldItemChange(int slot) implements Packet {
        @Override public void write(PacketBuffer out) { out.writeByte(slot); }
        public static HeldItemChange read(PacketBuffer in) { return new HeldItemChange(in.readByte()); }
    }

    /** 0x0B: 0 swing arm, 1 take damage, 2 leave bed, 3 eat, 4 critical effect, 5 magic critical effect. */
    public record Animation(int entityId, int animation) implements Packet {
        public static final int SWING_ARM = 0, TAKE_DAMAGE = 1;
        @Override public void write(PacketBuffer out) { out.writeVarInt(entityId).writeByte(animation); }
        public static Animation read(PacketBuffer in) { return new Animation(in.readVarInt(), in.readUnsignedByte()); }
    }

    /** 0x0C: another player comes into view. The client looks the UUID up in the player list for the name and skin. */
    public record SpawnPlayer(int entityId, UUID uuid, int x, int y, int z, int yaw, int pitch, int currentItem,
                              byte[] metadata) implements Packet {
        @Override public void write(PacketBuffer out) {
            out.writeVarInt(entityId);
            writeUuid(out, uuid);
            out.writeInt(x).writeInt(y).writeInt(z).writeByte(yaw).writeByte(pitch).writeShort(currentItem);
            out.writeBytes(metadata, 0, metadata.length);
        }
        public static SpawnPlayer read(PacketBuffer in) {
            return new SpawnPlayer(in.readVarInt(), readUuid(in), in.readInt(), in.readInt(), in.readInt(),
                in.readUnsignedByte(), in.readUnsignedByte(), in.readShort(), Metadata.read(in));
        }
    }

    /** 0x0D: the item flies to the collector; the item entity is destroyed separately. */
    public record CollectItem(int collectedId, int collectorId) implements Packet {
        @Override public void write(PacketBuffer out) { out.writeVarInt(collectedId).writeVarInt(collectorId); }
        public static CollectItem read(PacketBuffer in) { return new CollectItem(in.readVarInt(), in.readVarInt()); }
    }

    /**
     * 0x0E: a non-living entity (type 2 item stack, 60 arrow...). When data is
     * non-zero a velocity follows, in 1/8000 blocks per tick.
     */
    public record SpawnObject(int entityId, int type, int x, int y, int z, int pitch, int yaw, int data,
                              int velocityX, int velocityY, int velocityZ) implements Packet {
        public static final int ITEM = 2;
        @Override public void write(PacketBuffer out) {
            out.writeVarInt(entityId).writeByte(type).writeInt(x).writeInt(y).writeInt(z).writeByte(pitch).writeByte(yaw).writeInt(data);
            if (data != 0) out.writeShort(velocityX).writeShort(velocityY).writeShort(velocityZ);
        }
        public static SpawnObject read(PacketBuffer in) {
            int id = in.readVarInt(), type = in.readByte(), x = in.readInt(), y = in.readInt(), z = in.readInt();
            int pitch = in.readUnsignedByte(), yaw = in.readUnsignedByte(), data = in.readInt();
            if (data == 0) return new SpawnObject(id, type, x, y, z, pitch, yaw, 0, 0, 0, 0);
            return new SpawnObject(id, type, x, y, z, pitch, yaw, data, in.readShort(), in.readShort(), in.readShort());
        }
        /** Blocks per tick to the wire unit, clamped as vanilla does to 3.9. */
        public static int velocity(double blocksPerTick) { return (int) (Math.max(-3.9, Math.min(3.9, blocksPerTick)) * 8000.0); }
    }

    /** 0x13. */
    public record DestroyEntities(int[] entityIds) implements Packet {
        @Override public void write(PacketBuffer out) {
            out.writeVarInt(entityIds.length);
            for (int id : entityIds) out.writeVarInt(id);
        }
        public static DestroyEntities read(PacketBuffer in) {
            int n = in.readVarInt();
            if (n < 0 || n > in.readableBytes()) throw new PacketException("Bad entity count " + n);
            int[] ids = new int[n];
            for (int i = 0; i < n; i++) ids[i] = in.readVarInt();
            return new DestroyEntities(ids);
        }
    }

    /** 0x15: a move of at most 4 blocks per axis, in 1/32 block steps. */
    public record EntityRelativeMove(int entityId, int dx, int dy, int dz, boolean onGround) implements Packet {
        @Override public void write(PacketBuffer out) { out.writeVarInt(entityId).writeByte(dx).writeByte(dy).writeByte(dz).writeBoolean(onGround); }
        public static EntityRelativeMove read(PacketBuffer in) {
            return new EntityRelativeMove(in.readVarInt(), in.readByte(), in.readByte(), in.readByte(), in.readBoolean());
        }
    }

    /** 0x16. */
    public record EntityLook(int entityId, int yaw, int pitch, boolean onGround) implements Packet {
        @Override public void write(PacketBuffer out) { out.writeVarInt(entityId).writeByte(yaw).writeByte(pitch).writeBoolean(onGround); }
        public static EntityLook read(PacketBuffer in) {
            return new EntityLook(in.readVarInt(), in.readUnsignedByte(), in.readUnsignedByte(), in.readBoolean());
        }
    }

    /** 0x17. */
    public record EntityLookAndRelativeMove(int entityId, int dx, int dy, int dz, int yaw, int pitch, boolean onGround) implements Packet {
        @Override public void write(PacketBuffer out) {
            out.writeVarInt(entityId).writeByte(dx).writeByte(dy).writeByte(dz).writeByte(yaw).writeByte(pitch).writeBoolean(onGround);
        }
        public static EntityLookAndRelativeMove read(PacketBuffer in) {
            return new EntityLookAndRelativeMove(in.readVarInt(), in.readByte(), in.readByte(), in.readByte(),
                in.readUnsignedByte(), in.readUnsignedByte(), in.readBoolean());
        }
    }

    /** 0x18: an absolute position, for moves too long for a relative one. */
    public record EntityTeleport(int entityId, int x, int y, int z, int yaw, int pitch, boolean onGround) implements Packet {
        @Override public void write(PacketBuffer out) {
            out.writeVarInt(entityId).writeInt(x).writeInt(y).writeInt(z).writeByte(yaw).writeByte(pitch).writeBoolean(onGround);
        }
        public static EntityTeleport read(PacketBuffer in) {
            return new EntityTeleport(in.readVarInt(), in.readInt(), in.readInt(), in.readInt(),
                in.readUnsignedByte(), in.readUnsignedByte(), in.readBoolean());
        }
    }

    /** 0x19. */
    public record EntityHeadLook(int entityId, int headYaw) implements Packet {
        @Override public void write(PacketBuffer out) { out.writeVarInt(entityId).writeByte(headYaw); }
        public static EntityHeadLook read(PacketBuffer in) { return new EntityHeadLook(in.readVarInt(), in.readUnsignedByte()); }
    }

    /** 0x1A: 2 hurt, 3 dead, 9 finished eating (to the eater). */
    public record EntityStatus(int entityId, int status) implements Packet {
        public static final int HURT = 2, DEAD = 3, FINISHED_USING = 9;
        @Override public void write(PacketBuffer out) { out.writeInt(entityId).writeByte(status); }
        public static EntityStatus read(PacketBuffer in) { return new EntityStatus(in.readInt(), in.readByte()); }
    }

    /** 0x1C. */
    public record EntityMetadata(int entityId, byte[] metadata) implements Packet {
        @Override public void write(PacketBuffer out) { out.writeVarInt(entityId).writeBytes(metadata, 0, metadata.length); }
        public static EntityMetadata read(PacketBuffer in) { return new EntityMetadata(in.readVarInt(), Metadata.read(in)); }
    }

    /**
     * 0x21: one chunk column. With ground-up continuous set and an empty
     * section mask it unloads the column on the client.
     */
    public record ChunkData(int chunkX, int chunkZ, boolean groundUp, int sectionMask, byte[] data) implements Packet {
        public static final int MAX_DATA = 16 * (16 * 16 * 16 * 3) + 256;
        @Override public void write(PacketBuffer out) {
            out.writeInt(chunkX).writeInt(chunkZ).writeBoolean(groundUp).writeShort(sectionMask).writeByteArray(data);
        }
        public static ChunkData read(PacketBuffer in) {
            return new ChunkData(in.readInt(), in.readInt(), in.readBoolean(), in.readUnsignedShort(), in.readByteArray(MAX_DATA));
        }
        public static ChunkData unload(int chunkX, int chunkZ) { return new ChunkData(chunkX, chunkZ, true, 0, new byte[0]); }
    }

    /** 0x22: block changes within one chunk; each position is {@code (x << 12 | z << 8 | y)} with x and z local. */
    public record MultiBlockChange(int chunkX, int chunkZ, short[] positions, int[] states) implements Packet {
        @Override public void write(PacketBuffer out) {
            out.writeInt(chunkX).writeInt(chunkZ).writeVarInt(positions.length);
            for (int i = 0; i < positions.length; i++) out.writeShort(positions[i]).writeVarInt(states[i]);
        }
        public static MultiBlockChange read(PacketBuffer in) {
            int cx = in.readInt(), cz = in.readInt(), n = in.readVarInt();
            if (n < 0 || (long) n * 3 > in.readableBytes()) throw new PacketException("Bad block change count " + n);
            short[] positions = new short[n];
            int[] states = new int[n];
            for (int i = 0; i < n; i++) { positions[i] = in.readShort(); states[i] = in.readVarInt(); }
            return new MultiBlockChange(cx, cz, positions, states);
        }
    }

    /** 0x23: state is {@code id << 4 | metadata}. */
    public record BlockChange(int x, int y, int z, int state) implements Packet {
        @Override public void write(PacketBuffer out) { out.writeBlockPos(x, y, z).writeVarInt(state); }
        public static BlockChange read(PacketBuffer in) {
            long p = in.readLong();
            return new BlockChange(PacketBuffer.unpackX(p), PacketBuffer.unpackY(p), PacketBuffer.unpackZ(p), in.readVarInt());
        }
    }

    /** 0x28: a world effect; 2001 is a block breaking, with the block's {@code id | metadata << 12} as data. */
    public record Effect(int effectId, int x, int y, int z, int data, boolean global) implements Packet {
        public static final int BLOCK_BREAK = 2001;
        @Override public void write(PacketBuffer out) { out.writeInt(effectId).writeBlockPos(x, y, z).writeInt(data).writeBoolean(global); }
        public static Effect read(PacketBuffer in) {
            int id = in.readInt();
            long p = in.readLong();
            return new Effect(id, PacketBuffer.unpackX(p), PacketBuffer.unpackY(p), PacketBuffer.unpackZ(p), in.readInt(), in.readBoolean());
        }
    }

    /** 0x2B: 1 rain starts, 2 rain stops, 3 game mode changes (value is the mode)... */
    public record ChangeGameState(int reason, float value) implements Packet {
        public static final int BEGIN_RAIN = 1, END_RAIN = 2, GAME_MODE = 3;
        @Override public void write(PacketBuffer out) { out.writeByte(reason).writeFloat(value); }
        public static ChangeGameState read(PacketBuffer in) { return new ChangeGameState(in.readUnsignedByte(), in.readFloat()); }
    }

    /** 0x2D: type is e.g. "minecraft:crafting_table"; the slot count leaves out the player's own 36 slots. */
    public record OpenWindow(int windowId, String type, String titleJson, int slotCount) implements Packet {
        @Override public void write(PacketBuffer out) { out.writeByte(windowId).writeString(type).writeString(titleJson).writeByte(slotCount); }
        public static OpenWindow read(PacketBuffer in) {
            return new OpenWindow(in.readUnsignedByte(), in.readString(32), in.readString(32767), in.readUnsignedByte());
        }
    }

    /** 0x2E. */
    public record CloseWindow(int windowId) implements Packet {
        @Override public void write(PacketBuffer out) { out.writeByte(windowId); }
        public static CloseWindow read(PacketBuffer in) { return new CloseWindow(in.readUnsignedByte()); }
    }

    /** 0x2F: window 0 is the player's inventory (slots 36-44 the hotbar). */
    public record SetSlot(int windowId, int slot, Slot item) implements Packet {
        @Override public void write(PacketBuffer out) { out.writeByte(windowId).writeShort(slot); item.write(out); }
        public static SetSlot read(PacketBuffer in) { return new SetSlot(in.readByte(), in.readShort(), Slot.read(in)); }
    }

    /** 0x30: every slot of a window, in window order. */
    public record WindowItems(int windowId, Slot[] items) implements Packet {
        @Override public void write(PacketBuffer out) {
            out.writeByte(windowId).writeShort(items.length);
            for (Slot item : items) item.write(out);
        }
        public static WindowItems read(PacketBuffer in) {
            int window = in.readUnsignedByte(), n = in.readShort();
            if (n < 0 || n * 2 > in.readableBytes()) throw new PacketException("Bad slot count " + n);
            Slot[] items = new Slot[n];
            for (int i = 0; i < n; i++) items[i] = Slot.read(in);
            return new WindowItems(window, items);
        }
    }

    /** 0x32: whether the click with this action number was accepted. */
    public record ConfirmTransaction(int windowId, int action, boolean accepted) implements Packet {
        @Override public void write(PacketBuffer out) { out.writeByte(windowId).writeShort(action).writeBoolean(accepted); }
        public static ConfirmTransaction read(PacketBuffer in) { return new ConfirmTransaction(in.readByte(), in.readShort(), in.readBoolean()); }
    }

    /**
     * 0x38: the tab list. Only "add player" (0) and "remove player" (4) are
     * modelled; an added player carries name, game mode and ping and no skin
     * properties (offline mode).
     */
    public record PlayerListItem(int action, List<Entry> entries) implements Packet {
        public static final int ADD = 0, REMOVE = 4;
        public record Entry(UUID uuid, String name, int gameMode, int ping) {}

        @Override public void write(PacketBuffer out) {
            out.writeVarInt(action).writeVarInt(entries.size());
            for (Entry entry : entries) {
                writeUuid(out, entry.uuid());
                if (action == ADD) {
                    out.writeString(entry.name()).writeVarInt(0).writeVarInt(entry.gameMode()).writeVarInt(entry.ping()).writeBoolean(false);
                }
            }
        }
        public static PlayerListItem read(PacketBuffer in) {
            int action = in.readVarInt(), n = in.readVarInt();
            if (action != ADD && action != REMOVE) throw new PacketException("Unsupported player list action " + action);
            if (n < 0 || (long) n * 16 > in.readableBytes()) throw new PacketException("Bad player list count " + n);
            List<Entry> entries = new java.util.ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                UUID uuid = readUuid(in);
                if (action == REMOVE) { entries.add(new Entry(uuid, null, 0, 0)); continue; }
                String name = in.readString(LoginPackets.MAX_NAME);
                int properties = in.readVarInt();
                for (int p = 0; p < properties; p++) {
                    in.readString(32767);
                    in.readString(32767);
                    if (in.readBoolean()) in.readString(32767);
                }
                int mode = in.readVarInt(), ping = in.readVarInt();
                if (in.readBoolean()) in.readString(32767);
                entries.add(new Entry(uuid, name, mode, ping));
            }
            return new PlayerListItem(action, entries);
        }
    }

    /** 0x39: flags 0x01 invulnerable, 0x02 flying, 0x04 may fly, 0x08 creative (instant break). */
    public record PlayerAbilities(int flags, float flyingSpeed, float fieldOfView) implements Packet {
        public static final int INVULNERABLE = 1, FLYING = 2, ALLOW_FLYING = 4, CREATIVE = 8;
        @Override public void write(PacketBuffer out) { out.writeByte(flags).writeFloat(flyingSpeed).writeFloat(fieldOfView); }
        public static PlayerAbilities read(PacketBuffer in) { return new PlayerAbilities(in.readByte(), in.readFloat(), in.readFloat()); }
    }

    /** 0x40: a chat component (JSON). */
    public record Disconnect(String reason) implements Packet {
        @Override public void write(PacketBuffer out) { out.writeString(reason); }
        public static Disconnect read(PacketBuffer in) { return new Disconnect(in.readString(32767)); }
    }

    /** 0x41. */
    public record ServerDifficulty(int difficulty) implements Packet {
        @Override public void write(PacketBuffer out) { out.writeByte(difficulty); }
        public static ServerDifficulty read(PacketBuffer in) { return new ServerDifficulty(in.readUnsignedByte()); }
    }
}
