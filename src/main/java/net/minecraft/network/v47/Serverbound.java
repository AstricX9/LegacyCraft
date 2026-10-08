package net.minecraft.network.v47;

import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;

/** Play-state packets the client sends. Ids are listed in {@link Protocol47}. */
public final class Serverbound {
    private Serverbound() {}

    /** 0x00: echoes the id of the server's keep-alive. */
    public record KeepAlive(int id) implements Packet {
        @Override public void write(PacketBuffer out) { out.writeVarInt(id); }
        public static KeepAlive read(PacketBuffer in) { return new KeepAlive(in.readVarInt()); }
    }

    /** 0x01: a line typed in chat, a command when it starts with a slash. */
    public record Chat(String message) implements Packet {
        public static final int MAX_LENGTH = 100;
        @Override public void write(PacketBuffer out) { out.writeString(message); }
        public static Chat read(PacketBuffer in) { return new Chat(in.readString(MAX_LENGTH)); }
    }

    /** 0x02: type 0 interact, 1 attack, 2 interact at (with the hit point on the entity). */
    public record UseEntity(int target, int type, float x, float y, float z) implements Packet {
        public static final int INTERACT = 0, ATTACK = 1, INTERACT_AT = 2;
        @Override public void write(PacketBuffer out) {
            out.writeVarInt(target).writeVarInt(type);
            if (type == INTERACT_AT) out.writeFloat(x).writeFloat(y).writeFloat(z);
        }
        public static UseEntity read(PacketBuffer in) {
            int target = in.readVarInt(), type = in.readVarInt();
            if (type != INTERACT_AT) return new UseEntity(target, type, 0, 0, 0);
            return new UseEntity(target, type, in.readFloat(), in.readFloat(), in.readFloat());
        }
    }

    /**
     * 0x03-0x06 folded into one shape: the client sends position, look, both or
     * neither (0x03 only carries on-ground). {@code hasPosition} / {@code hasLook}
     * say which fields are real. Y is the feet. Only 0x06 is registered for
     * encoding, so a client sending this always sends the full position and look.
     */
    public record Move(boolean hasPosition, boolean hasLook, double x, double y, double z, float yaw, float pitch,
                       boolean onGround) implements Packet {
        /** Always sent as 0x06, position and look: the fields left out by a partial move are zero. */
        @Override public void write(PacketBuffer out) {
            out.writeDouble(x).writeDouble(y).writeDouble(z).writeFloat(yaw).writeFloat(pitch).writeBoolean(onGround);
        }
        public static Move readGround(PacketBuffer in) { return new Move(false, false, 0, 0, 0, 0, 0, in.readBoolean()); }
        public static Move readPosition(PacketBuffer in) {
            double x = in.readDouble(), y = in.readDouble(), z = in.readDouble();
            return new Move(true, false, x, y, z, 0, 0, in.readBoolean());
        }
        public static Move readLook(PacketBuffer in) {
            float yaw = in.readFloat(), pitch = in.readFloat();
            return new Move(false, true, 0, 0, 0, yaw, pitch, in.readBoolean());
        }
        public static Move readPositionLook(PacketBuffer in) {
            double x = in.readDouble(), y = in.readDouble(), z = in.readDouble();
            float yaw = in.readFloat(), pitch = in.readFloat();
            return new Move(true, true, x, y, z, yaw, pitch, in.readBoolean());
        }
    }

    /**
     * 0x07: 0 started, 1 cancelled, 2 finished digging; 3 drop stack, 4 drop
     * item, 5 finish using the held item (bow, food). Face 0-5 is down, up,
     * north, south, west, east.
     */
    public record Digging(int status, int x, int y, int z, int face) implements Packet {
        public static final int START = 0, CANCEL = 1, FINISH = 2, DROP_STACK = 3, DROP_ITEM = 4, RELEASE_USE = 5;
        @Override public void write(PacketBuffer out) { out.writeByte(status).writeBlockPos(x, y, z).writeByte(face); }
        public static Digging read(PacketBuffer in) {
            int status = in.readByte();
            long p = in.readLong();
            return new Digging(status, PacketBuffer.unpackX(p), PacketBuffer.unpackY(p), PacketBuffer.unpackZ(p), in.readByte());
        }
    }

    /**
     * 0x08: right click on a block face (0-5), or face 255 with position -1
     * for using the held item in the air. Cursor values are the hit point in
     * sixteenths of the block.
     */
    public record BlockPlacement(int x, int y, int z, int face, Slot held, int cursorX, int cursorY, int cursorZ) implements Packet {
        public static final int USE_ITEM = 255;
        @Override public void write(PacketBuffer out) {
            out.writeBlockPos(x, y, z).writeByte(face);
            held.write(out);
            out.writeByte(cursorX).writeByte(cursorY).writeByte(cursorZ);
        }
        public static BlockPlacement read(PacketBuffer in) {
            long p = in.readLong();
            int face = in.readUnsignedByte();
            Slot held = Slot.read(in);
            return new BlockPlacement(PacketBuffer.unpackX(p), PacketBuffer.unpackY(p), PacketBuffer.unpackZ(p), face, held,
                in.readUnsignedByte(), in.readUnsignedByte(), in.readUnsignedByte());
        }
    }

    /** 0x09: the selected hotbar slot, 0-8. */
    public record HeldItemChange(int slot) implements Packet {
        @Override public void write(PacketBuffer out) { out.writeShort(slot); }
        public static HeldItemChange read(PacketBuffer in) { return new HeldItemChange(in.readShort()); }
    }

    /** 0x0A: the player swung their arm. */
    public record Animation() implements Packet {
        @Override public void write(PacketBuffer out) {}
        public static Animation read(PacketBuffer in) { return new Animation(); }
    }

    /** 0x0B: 0 start sneaking, 1 stop sneaking, 2 leave bed, 3 start sprinting, 4 stop sprinting, 5 horse jump, 6 open horse inventory. */
    public record EntityAction(int entityId, int action, int jumpBoost) implements Packet {
        public static final int START_SNEAK = 0, STOP_SNEAK = 1, LEAVE_BED = 2, START_SPRINT = 3, STOP_SPRINT = 4;
        @Override public void write(PacketBuffer out) { out.writeVarInt(entityId).writeVarInt(action).writeVarInt(jumpBoost); }
        public static EntityAction read(PacketBuffer in) { return new EntityAction(in.readVarInt(), in.readVarInt(), in.readVarInt()); }
    }

    /** 0x0D. */
    public record CloseWindow(int windowId) implements Packet {
        @Override public void write(PacketBuffer out) { out.writeByte(windowId); }
        public static CloseWindow read(PacketBuffer in) { return new CloseWindow(in.readUnsignedByte()); }
    }

    /**
     * 0x0E: a click in a window. Mode 0 click (button 0 left, 1 right), 1
     * shift-click, 2 number key (button is the hotbar slot), 3 middle click,
     * 4 drop key (button 1 with Ctrl), 5 drag, 6 double click. Slot -999 is
     * outside the window. The item is what the client thinks was in the slot.
     */
    public record ClickWindow(int windowId, int slot, int button, int action, int mode, Slot item) implements Packet {
        public static final int OUTSIDE = -999;
        @Override public void write(PacketBuffer out) {
            out.writeByte(windowId).writeShort(slot).writeByte(button).writeShort(action).writeByte(mode);
            item.write(out);
        }
        public static ClickWindow read(PacketBuffer in) {
            return new ClickWindow(in.readUnsignedByte(), in.readShort(), in.readByte(), in.readShort(), in.readByte(), Slot.read(in));
        }
    }

    /** 0x10: in Creative the client sets an inventory slot outright (slot -1 drops the item). */
    public record CreativeInventoryAction(int slot, Slot item) implements Packet {
        @Override public void write(PacketBuffer out) { out.writeShort(slot); item.write(out); }
        public static CreativeInventoryAction read(PacketBuffer in) { return new CreativeInventoryAction(in.readShort(), Slot.read(in)); }
    }

    /** 0x13: flag 0x02 is "flying". */
    public record PlayerAbilities(int flags, float flyingSpeed, float walkingSpeed) implements Packet {
        @Override public void write(PacketBuffer out) { out.writeByte(flags).writeFloat(flyingSpeed).writeFloat(walkingSpeed); }
        public static PlayerAbilities read(PacketBuffer in) { return new PlayerAbilities(in.readByte(), in.readFloat(), in.readFloat()); }
    }

    /** 0x15. View distance is in chunks; skin parts is a bit set of the visible layers. */
    public record ClientSettings(String locale, int viewDistance, int chatMode, boolean chatColors, int skinParts) implements Packet {
        @Override public void write(PacketBuffer out) {
            out.writeString(locale).writeByte(viewDistance).writeByte(chatMode).writeBoolean(chatColors).writeByte(skinParts);
        }
        public static ClientSettings read(PacketBuffer in) {
            return new ClientSettings(in.readString(16), in.readByte(), in.readByte(), in.readBoolean(), in.readUnsignedByte());
        }
    }

    /** 0x16: 0 respawn, 1 request stats, 2 opened inventory (achievement). */
    public record ClientStatus(int action) implements Packet {
        public static final int RESPAWN = 0;
        @Override public void write(PacketBuffer out) { out.writeVarInt(action); }
        public static ClientStatus read(PacketBuffer in) { return new ClientStatus(in.readVarInt()); }
    }
}
