package net.minecraft.server.dedicated;

import net.minecraft.inventory.CraftingGrid;
import net.minecraft.inventory.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.v47.Slot;
import net.minecraft.util.FoodStats;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A connected player as the server sees them. Position and look are what the
 * client last reported (y is the feet); the server only checks moves for
 * sanity. The inventory, health and hunger are the server's own.
 */
public final class ServerPlayer {
    public static final float MAX_HEALTH = 20f;

    public final ServerConnection connection;
    public final String name;
    public final UUID uuid;
    public final int entityId;

    public double x, y, z;
    public float yaw, pitch;
    public boolean onGround;
    public int gameMode;
    public boolean sneaking, sprinting;
    public int skinParts = 0x7F;
    public int clientViewDistance = Integer.MAX_VALUE;

    public final InventoryPlayer inventory = new InventoryPlayer();
    final ServerWindow playerWindow;
    /** The window open now: the player's own unless a crafting table is open. */
    ServerWindow openWindow;
    int nextWindowId = 1;
    /** What the client was last told each slot of {@link #syncedWindow} holds, and its cursor. */
    Slot[] syncedSlots = new Slot[0];
    ServerWindow syncedWindow;
    Slot syncedCursor = Slot.EMPTY;
    /** Equipment last shown to other players: held item, boots, leggings, chestplate, helmet. */
    final Slot[] shownEquipment = {Slot.EMPTY, Slot.EMPTY, Slot.EMPTY, Slot.EMPTY, Slot.EMPTY};

    public float health = MAX_HEALTH;
    public final FoodStats food = new FoodStats();
    float fallDistance;
    int hurtCooldown;
    boolean dead;
    long deathTick;
    /** Health and hunger the client last saw, to send updates only on change. */
    float shownHealth = -1;
    int shownFood = -1;
    float shownSaturation = -1;

    /** Mining: the block being dug and the tick it started (-1 when not digging). */
    int digX, digY, digZ;
    long digStartTick = -1;
    long digStartMillis;
    /** Eating: the tick the held food was raised (-1 when not eating) and the hotbar slot. */
    long useStartTick = -1;
    int useSlot;

    /** Columns the client has been sent, as chunk keys. */
    final Set<Long> loadedChunks = new HashSet<>();
    /** Entities this player can see, by entity id, with the last position and look sent for each. */
    final Map<Integer, Tracked> tracked = new HashMap<>();

    /** A teleport the client hasn't caught up with yet: moves are ignored until it reports this position. */
    boolean awaitingTeleport;
    double teleportX, teleportY, teleportZ;

    int keepAliveId;
    long keepAliveSentTick = -1;
    long lastKeepAliveReplyTick;

    ServerPlayer(ServerConnection connection, String name, UUID uuid, int entityId, CraftingGrid grid2x2) {
        this.connection = connection;
        this.name = name;
        this.uuid = uuid;
        this.entityId = entityId;
        this.playerWindow = ServerWindow.player(inventory, grid2x2);
        this.openWindow = playerWindow;
    }

    public ItemStack heldStack() { return inventory.getCurrentItem(); }
    public boolean isCreative() { return gameMode == 1; }
    /** Survival or Adventure: takes damage, gets hungry, uses up items. */
    public boolean isSurvivalLike() { return gameMode == 0 || gameMode == 2; }
    public double eyeY() { return y + 1.62; }
    public int chunkX() { return (int) Math.floor(x) >> 4; }
    public int chunkZ() { return (int) Math.floor(z) >> 4; }

    /** Look direction (vanilla yaw: 0 faces +z, 90 faces -x). */
    public double lookX() { return -Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)); }
    public double lookY() { return -Math.sin(Math.toRadians(pitch)); }
    public double lookZ() { return Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)); }

    /** Where an entity was last shown to this player, in wire units (fixed point, angle bytes). */
    static final class Tracked {
        int x, y, z, yaw, pitch;
        Tracked(int x, int y, int z, int yaw, int pitch) { this.x = x; this.y = y; this.z = z; this.yaw = yaw; this.pitch = pitch; }
    }
}
