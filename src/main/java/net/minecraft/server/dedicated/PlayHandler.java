package net.minecraft.server.dedicated;

import net.minecraft.block.Block;
import net.minecraft.block.BlockPlacement;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockUpdates;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityItem;
import net.minecraft.inventory.CraftingGrid;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ToolValues;
import net.minecraft.network.Packet;
import net.minecraft.network.v47.Clientbound;
import net.minecraft.network.v47.Serverbound;
import net.minecraft.util.Direction;
import net.minecraft.world.World;

import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

/**
 * What the server does with each play packet, on the tick thread. The client
 * is trusted for its own movement (checked only for teleport-sized jumps, and
 * used for fall damage and hunger). Everything else is the server's: the
 * inventory, what the held item is, how long a block takes to break and what
 * it drops. Edits the server refuses are undone on the client by resending
 * the real blocks; window clicks are answered with the real window.
 */
final class PlayHandler {
    /** Vanilla's server-side reach limit: the squared distance from the eyes to the block centre. */
    private static final double MAX_REACH_SQ = 6.0 * 6.0;
    private static final double MAX_MOVE = 100.0;
    /** A dig may finish this much sooner than the server's own count (latency between the start and finish packets). */
    private static final double DIG_TOLERANCE = 0.7;
    private static final String[] GAME_MODES = {"survival", "creative", "adventure", "spectator"};

    private final DedicatedServer server;

    PlayHandler(DedicatedServer server) { this.server = server; }

    void handle(ServerPlayer player, Packet packet) {
        if (packet instanceof Serverbound.KeepAlive keepAlive) server.keepAliveAnswered(player, keepAlive.id());
        else if (packet instanceof Serverbound.Move move) move(player, move);
        else if (packet instanceof Serverbound.Chat chat) chat(player, chat.message());
        else if (packet instanceof Serverbound.ClientStatus status) {
            if (status.action() == Serverbound.ClientStatus.RESPAWN) server.life.respawn(player);
        } else if (player.dead) {
            return;                                     // the dead can only chat and respawn
        } else if (packet instanceof Serverbound.Digging digging) dig(player, digging);
        else if (packet instanceof Serverbound.BlockPlacement placement) place(player, placement);
        else if (packet instanceof Serverbound.Animation)
            server.sendToTrackers(player, new Clientbound.Animation(player.entityId, Clientbound.Animation.SWING_ARM));
        else if (packet instanceof Serverbound.EntityAction action) entityAction(player, action);
        else if (packet instanceof Serverbound.UseEntity use) {
            if (use.type() == Serverbound.UseEntity.ATTACK) server.mobs.attack(player, use.target());
        }
        else if (packet instanceof Serverbound.HeldItemChange change) {
            if (change.slot() < 0 || change.slot() > 8) return;
            player.inventory.currentItem = change.slot();
            server.life.stopEating(player);
        } else if (packet instanceof Serverbound.CreativeInventoryAction action) creativeSlot(player, action);
        else if (packet instanceof Serverbound.ClickWindow click) clickWindow(player, click);
        else if (packet instanceof Serverbound.CloseWindow) closeWindow(player);
        else if (packet instanceof Serverbound.ClientSettings settings) {
            player.clientViewDistance = settings.viewDistance();
            if (player.skinParts != settings.skinParts()) {
                player.skinParts = settings.skinParts();
                server.sendToTrackers(player, new Clientbound.EntityMetadata(player.entityId, server.metadata(player)));
            }
        }
        // Not acted on yet: use entity (combat comes with mobs), abilities, and the ignored ids.
    }

    // ---- movement --------------------------------------------------------------------------

    private void move(ServerPlayer player, Serverbound.Move move) {
        if (player.dead) return;
        if (move.hasLook()) {
            player.yaw = move.yaw();
            player.pitch = move.pitch();
        }
        boolean wasOnGround = player.onGround;
        player.onGround = move.onGround();
        if (!move.hasPosition()) { landed(player, 0); return; }
        if (!Double.isFinite(move.x()) || !Double.isFinite(move.y()) || !Double.isFinite(move.z())) {
            player.connection.kick("Illegal position");
            return;
        }
        if (player.awaitingTeleport) {
            // Moves sent before the client saw the teleport still describe the old place.
            double dx = move.x() - player.teleportX, dy = move.y() - player.teleportY, dz = move.z() - player.teleportZ;
            if (dx * dx + dy * dy + dz * dz > 0.01) return;
            player.awaitingTeleport = false;
        }
        double dx = move.x() - player.x, dy = move.y() - player.y, dz = move.z() - player.z;
        if (dx * dx + dy * dy + dz * dz > MAX_MOVE * MAX_MOVE) {
            DedicatedServer.log(player.name + " moved too quickly");
            server.teleport(player, player.x, player.y, player.z, player.yaw, player.pitch);
            return;
        }
        player.x = move.x();
        player.y = move.y();
        player.z = move.z();
        if (!player.isSurvivalLike()) return;
        exhaustion(player, dx, dy, dz, wasOnGround);
        landed(player, dy);
    }

    /** EntityPlayer.addMovementStat and the jump's cost, from the reported move. */
    private void exhaustion(ServerPlayer player, double dx, double dy, double dz, boolean wasOnGround) {
        String feet = blockAt(player.x, player.y, player.z).material();
        String head = blockAt(player.x, player.eyeY(), player.z).material();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (head.equals("water")) server.life.addExhaustion(player, 0.015f * (float) Math.sqrt(dx * dx + dy * dy + dz * dz));
        else if (feet.equals("water")) server.life.addExhaustion(player, 0.015f * (float) horizontal);
        else if (player.onGround) server.life.addExhaustion(player, (player.sprinting ? 0.1f : 0.01f) * (float) horizontal);
        if (wasOnGround && !player.onGround && dy > 0) server.life.addExhaustion(player, player.sprinting ? 0.8f : 0.2f);
    }

    /** Falls add up while going down; landing hurts one point per block past three. Water, ladders and vines break a fall. */
    private void landed(ServerPlayer player, double dy) {
        if (!player.isSurvivalLike()) { player.fallDistance = 0; return; }
        Block feet = blockAt(player.x, player.y, player.z);
        String name = feet.name().path();
        if (feet.material().equals("water") || name.equals("ladder") || name.equals("vine") || name.equals("web")) {
            player.fallDistance = 0;
            return;
        }
        if (player.onGround) {
            float fall = player.fallDistance;
            player.fallDistance = 0;
            int damage = (int) Math.ceil(fall - 3f);
            if (damage > 0) server.life.damage(player, damage, "death.attack.fall", false);
        } else if (dy < 0) {
            player.fallDistance -= (float) dy;
        }
    }

    private Block blockAt(double x, double y, double z) {
        int by = (int) Math.floor(y);
        if (by < 0 || by >= World.HEIGHT) return server.blocks.air();
        return server.world.get((int) Math.floor(x), by, (int) Math.floor(z));
    }

    private void entityAction(ServerPlayer player, Serverbound.EntityAction action) {
        switch (action.action()) {
            case Serverbound.EntityAction.START_SNEAK -> player.sneaking = true;
            case Serverbound.EntityAction.STOP_SNEAK -> player.sneaking = false;
            case Serverbound.EntityAction.START_SPRINT -> player.sprinting = true;
            case Serverbound.EntityAction.STOP_SPRINT -> player.sprinting = false;
            default -> { return; }
        }
        server.sendToTrackers(player, new Clientbound.EntityMetadata(player.entityId, server.metadata(player)));
    }

    // ---- digging ---------------------------------------------------------------------------

    private boolean inReach(ServerPlayer player, int x, int y, int z) {
        double dx = x + 0.5 - player.x, dy = y + 0.5 - player.eyeY(), dz = z + 0.5 - player.z;
        return dx * dx + dy * dy + dz * dz <= MAX_REACH_SQ;
    }

    private void dig(ServerPlayer player, Serverbound.Digging digging) {
        switch (digging.status()) {
            case Serverbound.Digging.DROP_ITEM, Serverbound.Digging.DROP_STACK -> {
                ItemStack held = player.heldStack();
                if (held == null || player.gameMode == 3) return;
                boolean all = digging.status() == Serverbound.Digging.DROP_STACK;
                ItemStack thrown = all ? held : held.split(1);
                if (all || held.isEmpty()) player.inventory.mainInventory[player.inventory.currentItem] = null;
                server.throwItem(player, thrown);
                return;
            }
            case Serverbound.Digging.RELEASE_USE -> { server.life.stopEating(player); return; }
            case Serverbound.Digging.CANCEL -> { player.digStartTick = -1; return; }
            default -> {}
        }
        int x = digging.x(), y = digging.y(), z = digging.z();
        World world = server.world;
        BlockState state = world.getState(x, y, z);
        Block block = state.block();
        boolean allowed = inReach(player, x, y, z) && y >= 0 && y < World.HEIGHT && player.gameMode != 2 && player.gameMode != 3
            && !block.isAir();
        if (player.isCreative()) {
            ItemStack held = player.heldStack();
            boolean sword = held != null && ToolValues.type(held.item()) == ToolValues.Type.SWORD;
            if (digging.status() != Serverbound.Digging.START) return;
            if (!allowed || sword) { server.resendBlock(player, x, y, z); return; }
            server.suppressDrops = true;
            try {
                world.setStateAndUpdate(x, y, z, BlockState.of(server.blocks.air(), 0));
            } finally {
                server.suppressDrops = false;
            }
            return;
        }
        if (!allowed || block.hardness() < 0) { server.resendBlock(player, x, y, z); return; }
        float progress = progressPerTick(player, block);
        if (digging.status() == Serverbound.Digging.START) {
            if (progress >= 1f) { breakBlock(player, x, y, z); return; }
            player.digX = x;
            player.digY = y;
            player.digZ = z;
            player.digStartTick = server.tick();
            player.digStartMillis = System.currentTimeMillis();
            return;
        }
        // FINISH: only for the block being dug, and not much faster than the tool allows.
        boolean same = player.digStartTick >= 0 && player.digX == x && player.digY == y && player.digZ == z;
        // Counted in ticks, or in wall-clock time when the server has been lagging behind the client.
        long elapsed = same ? Math.max(server.tick() - player.digStartTick + 1,
            (System.currentTimeMillis() - player.digStartMillis) / 50 + 1) : 0;
        player.digStartTick = -1;
        double needed = progress <= 0 ? Double.POSITIVE_INFINITY : Math.ceil(1.0 / progress);
        if (!same || elapsed < needed * DIG_TOLERANCE) {
            server.resendBlock(player, x, y, z);
            return;
        }
        breakBlock(player, x, y, z);
    }

    /** The fastest the held item could break this block (on the ground, head out of water): the client's own timing. */
    private float progressPerTick(ServerPlayer player, Block block) {
        ItemStack held = player.heldStack();
        int efficiency = EnchantmentHelper.level(Enchantment.EFFICIENCY, held);
        return ToolValues.breakProgressPerTick(held == null ? null : held.item(), block.name().path(), block.material(),
            block.hardness(), true, false, efficiency, true);
    }

    /** A survival break: drops, exhaustion, tool wear, then the block goes (ice leaves water over a solid floor). */
    private void breakBlock(ServerPlayer player, int x, int y, int z) {
        World world = server.world;
        BlockState state = world.getState(x, y, z);
        Block block = state.block();
        String name = block.name().path();
        ItemStack held = player.heldStack();
        Item tool = held == null ? null : held.item();
        if (ToolValues.canHarvest(tool, name, block.material())) server.life.addExhaustion(player, 0.025f);
        int meta = state.metadata();
        if (name.equals("double_plant") && (meta & 8) != 0 && y > 0 && world.get(x, y - 1, z) == block)
            meta = world.getState(x, y - 1, z).metadata();
        int fortune = EnchantmentHelper.level(Enchantment.FORTUNE, held);
        boolean silk = EnchantmentHelper.level(Enchantment.SILK_TOUCH, held) > 0;
        for (ItemStack drop : server.drops.drops(name, block.material(), meta, tool, fortune, silk, block.isFullCube(), server.random))
            server.spawnItem(EntityItem.fromBlock(world, x, y, z, drop, server.random));
        BlockState replacement = BlockState.of(server.blocks.air(), 0);
        if (name.equals("ice") && y > 0) {
            Block below = world.get(x, y - 1, z);
            if (below.isSolid() || below.material().equals("water") || below.material().equals("lava"))
                replacement = BlockState.of(server.blocks.get("minecraft:flowing_water"), 0);
        }
        if (held != null) {
            int wear = ToolValues.damageForBreak(tool, name, block.material(), block.hardness());
            if (EnchantmentHelper.damageItem(held, wear, server.random))
                player.inventory.mainInventory[player.inventory.currentItem] = null;
        }
        for (var viewer : server.players())
            if (viewer != player && viewer.loadedChunks.contains(World.chunkKey(x >> 4, z >> 4)))
                viewer.connection.send(new Clientbound.Effect(Clientbound.Effect.BLOCK_BREAK, x, y, z,
                    block.registryId() | state.metadata() << 12, false));
        world.setStateAndUpdate(x, y, z, replacement);
    }

    // ---- right click -----------------------------------------------------------------------

    private void place(ServerPlayer player, Serverbound.BlockPlacement placement) {
        if (placement.face() == Serverbound.BlockPlacement.USE_ITEM) { useItem(player); return; }
        if (placement.face() > 5) return;
        int x = placement.x(), y = placement.y(), z = placement.z();
        Direction face = Direction.values()[placement.face()];
        if (!inReach(player, x, y, z) || player.gameMode == 3) { undoPlacement(player, x, y, z, face); return; }
        World world = server.world;
        ItemStack held = player.heldStack();
        if (!(player.sneaking && held != null) && activate(player, x, y, z)) return;
        Item item = held == null ? null : held.item();
        if (item == null || item.placedBlock() == null || player.gameMode == 2) {
            undoPlacement(player, x, y, z, face);
            return;
        }
        double yawRad = Math.toRadians(player.yaw);
        var context = new BlockPlacement.Context(x, y, z, face,
            placement.cursorX() / 16f, placement.cursorY() / 16f, placement.cursorZ() / 16f,
            Direction.nearest(-Math.sin(yawRad), 0, Math.cos(yawRad)), player.x, player.eyeY(), player.z);
        List<BlockPlacement.Placement> placements = BlockPlacement.place(world, server.blocks, item.placedBlock(), item.placedMetadata(), context);
        for (BlockPlacement.Placement p : placements) {
            if (p.y() < 0 || p.y() >= World.HEIGHT || (p.state().block().isSolid() && occupied(p.x(), p.y(), p.z()))) {
                placements = List.of();
                break;
            }
        }
        if (placements.isEmpty()) {
            undoPlacement(player, x, y, z, face);
            return;
        }
        for (BlockPlacement.Placement p : placements) world.setState(p.x(), p.y(), p.z(), p.state());
        for (BlockPlacement.Placement p : placements) BlockUpdates.afterChange(world, p.x(), p.y(), p.z());
        if (!player.isCreative()) player.inventory.consumeCurrentItem();
    }

    /** Right click on a block that does something: the crafting table opens, a cake gets a bite. */
    private boolean activate(ServerPlayer player, int x, int y, int z) {
        World world = server.world;
        BlockState state = world.getState(x, y, z);
        String name = state.block().name().path();
        if (name.equals("crafting_table")) {
            openCraftingTable(player);
            return true;
        }
        if (name.equals("cake")) {
            if (player.isSurvivalLike() && player.food.needFood()) {
                player.food.addStats(2, 2 * 0.1f * 2f);
                int bites = state.metadata();
                world.setStateAndUpdate(x, y, z, bites < 6 ? BlockState.of(state.block(), bites + 1) : BlockState.of(server.blocks.air(), 0));
            }
            return true;
        }
        return false;
    }

    /** Right click in the air: raise food, or put on the armour piece held. */
    private void useItem(ServerPlayer player) {
        if (player.gameMode == 3) return;
        if (server.life.startEating(player)) return;
        player.inventory.equipHeldArmor();
    }

    /** The client already drew its guess; put both the clicked block and the one beside it back. */
    private void undoPlacement(ServerPlayer player, int x, int y, int z, Direction face) {
        server.resendBlock(player, x, y, z);
        server.resendBlock(player, x + face.xOffset, y + face.yOffset, z + face.zOffset);
    }

    /** Whether any living player's 0.6 x 1.8 box overlaps this block. */
    private boolean occupied(int x, int y, int z) {
        for (ServerPlayer other : server.players()) {
            if (other.gameMode == 3 || other.dead) continue;
            if (other.x + 0.3 > x && other.x - 0.3 < x + 1 && other.y + 1.8 > y && other.y < y + 1
                && other.z + 0.3 > z && other.z - 0.3 < z + 1) return true;
        }
        return false;
    }

    // ---- windows ---------------------------------------------------------------------------

    private void openCraftingTable(ServerPlayer player) {
        if (player.openWindow != player.playerWindow) closeWindow(player);
        int id = player.nextWindowId;
        player.nextWindowId = player.nextWindowId % 100 + 1;
        player.openWindow = ServerWindow.craftingTable(id, player.inventory, new CraftingGrid(3, 3, server.crafting, server::itemByName));
        player.connection.send(new Clientbound.OpenWindow(id, player.openWindow.type,
            DedicatedServer.translate("container.crafting", null).toString(), 0));
        server.syncInventory(player, true);
    }

    private void closeWindow(ServerPlayer player) {
        player.openWindow.close(stack -> server.throwItem(player, stack));
        player.openWindow = player.playerWindow;
    }

    private void clickWindow(ServerPlayer player, Serverbound.ClickWindow click) {
        if (click.windowId() != player.openWindow.id || player.gameMode == 3) {
            server.syncInventory(player, true);
            return;
        }
        player.openWindow.click(click, player.isCreative(), stack -> server.throwItem(player, stack));
        player.connection.send(new Clientbound.ConfirmTransaction(click.windowId(), click.action(), true));
        server.syncInventory(player, true);
    }

    /** Creative sets slots outright: any item into any slot of the player window, or slot -1 to throw it. */
    private void creativeSlot(ServerPlayer player, Serverbound.CreativeInventoryAction action) {
        if (!player.isCreative()) return;
        ItemStack stack = server.codec.fromSlot(action.item());
        if (action.slot() == -1) { server.throwItem(player, stack); return; }
        if (action.slot() < 1 || action.slot() >= player.playerWindow.size()) return;
        player.playerWindow.set(action.slot(), stack);
    }

    // ---- chat ------------------------------------------------------------------------------

    private void chat(ServerPlayer player, String message) {
        for (int i = 0; i < message.length(); i++) {
            char c = message.charAt(i);
            if (c == '§' || c < ' ' || c == 127) {
                player.connection.kick("Illegal characters in chat");
                return;
            }
        }
        message = message.trim();
        if (message.isEmpty()) return;
        if (message.startsWith("/")) {
            command(player, message.substring(1));
            return;
        }
        DedicatedServer.log("<" + player.name + "> " + message);
        server.broadcast(DedicatedServer.chat(DedicatedServer.translate("chat.type.text", null, player.name, message), 0));
    }

    private void command(ServerPlayer player, String line) {
        DedicatedServer.log(player.name + " issued server command: /" + line);
        String[] args = line.split(" +");
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "list" -> {
                StringJoiner names = new StringJoiner(", ");
                for (ServerPlayer other : server.players()) names.add(other.name);
                reply(player, "There are " + server.playerCount() + "/" + server.properties().maxPlayers + " players online: " + names, null);
            }
            case "seed" -> reply(player, "Seed: " + server.world.seed(), null);
            case "gamemode", "gm" -> {
                int mode = args.length > 1 ? gameMode(args[1]) : -1;
                if (mode < 0) { reply(player, "Usage: /gamemode <survival|creative|adventure|spectator>", "red"); return; }
                player.gameMode = mode;
                player.connection.send(new Clientbound.ChangeGameState(Clientbound.ChangeGameState.GAME_MODE, mode));
                player.connection.send(server.abilities(player));
                reply(player, "Your game mode has been updated to " + GAME_MODES[mode], null);
            }
            case "spawn" -> server.teleport(player, server.spawnX + 0.5, server.spawnY, server.spawnZ + 0.5, player.yaw, player.pitch);
            case "kill" -> server.life.damage(player, Float.MAX_VALUE, "death.attack.generic", true);
            case "help", "?" -> reply(player, "Commands: /list, /seed, /gamemode <mode>, /spawn, /kill", null);
            default -> reply(player, "Unknown command. Try /help for a list of commands", "red");
        }
    }

    private static int gameMode(String arg) {
        String a = arg.toLowerCase(Locale.ROOT);
        for (int i = 0; i < GAME_MODES.length; i++)
            if (a.equals(Integer.toString(i)) || a.equals(GAME_MODES[i]) || a.equals(GAME_MODES[i].substring(0, 1))
                || (i == 3 && a.equals("sp"))) return i;
        return -1;
    }

    private static void reply(ServerPlayer player, String text, String color) {
        player.connection.send(DedicatedServer.chat(DedicatedServer.text(text, color), 1));
    }
}
