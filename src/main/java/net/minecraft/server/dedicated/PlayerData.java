package net.minecraft.server.dedicated;

import net.minecraft.inventory.InventoryPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtTag;
import net.minecraft.util.ResourceLocation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Each player's position, game mode, health, hunger and inventory, in
 * {@code <world>/playerdata/<uuid>.dat} with the tag names of a 1.8.9 player
 * compound. Items are saved by vanilla id and data value. The server's clock
 * goes in {@code <world>/server.dat}.
 */
final class PlayerData {
    private final Path directory;
    private final DedicatedServer server;

    PlayerData(Path directory, DedicatedServer server) {
        this.directory = directory;
        this.server = server;
    }

    private Path file(ServerPlayer player) { return directory.resolve(player.uuid + ".dat"); }

    void save(ServerPlayer player) {
        NbtTag.Compound root = new NbtTag.Compound();
        root.put("Pos", new NbtTag.NbtList(NbtTag.DOUBLE,
            List.of(new NbtTag.Double(player.x), new NbtTag.Double(player.y), new NbtTag.Double(player.z))));
        root.put("Rotation", new NbtTag.NbtList(NbtTag.FLOAT, List.of(new NbtTag.Float(player.yaw), new NbtTag.Float(player.pitch))));
        root.put("OnGround", new NbtTag.Byte((byte) (player.onGround ? 1 : 0)));
        root.put("playerGameType", new NbtTag.Int(player.gameMode));
        root.put("HealF", new NbtTag.Float(player.dead ? 0f : player.health));
        root.put("foodLevel", new NbtTag.Int(player.food.getFoodLevel()));
        root.put("foodSaturationLevel", new NbtTag.Float(player.food.getSaturationLevel()));
        root.put("foodExhaustionLevel", new NbtTag.Float(player.food.getExhaustionLevel()));
        root.put("foodTickTimer", new NbtTag.Int(player.food.getFoodTimer()));
        root.put("FallDistance", new NbtTag.Float(player.fallDistance));
        root.put("SelectedItemSlot", new NbtTag.Int(player.inventory.currentItem));
        List<NbtTag> stacks = new ArrayList<>();
        InventoryPlayer inventory = player.inventory;
        for (int i = 0; i < InventoryPlayer.MAIN_SIZE; i++) addStack(stacks, i, inventory.mainInventory[i]);
        for (int i = 0; i < InventoryPlayer.ARMOR_SIZE; i++) addStack(stacks, 100 + i, inventory.armorInventory[i]);
        root.put("Inventory", new NbtTag.NbtList(stacks.isEmpty() ? NbtTag.END : NbtTag.COMPOUND, stacks));
        try {
            Files.createDirectories(directory);
            Path temp = directory.resolve(player.uuid + ".dat.tmp");
            NbtIo.writeCompressed(root, temp);
            Files.move(temp, file(player), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.err.println("[Server] Couldn't save " + player.name + ": " + e.getMessage());
        }
    }

    private static void addStack(List<NbtTag> stacks, int slot, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        Item item = stack.item();
        NbtTag.Compound tag = new NbtTag.Compound();
        tag.put("Slot", new NbtTag.Byte((byte) slot));
        tag.put("id", new NbtTag.Str(item.vanillaId().toString()));
        tag.put("Count", new NbtTag.Byte((byte) stack.count()));
        tag.put("Damage", new NbtTag.Short((short) (item.isDamageable() ? stack.damage() : item.vanillaData())));
        NbtTag.Compound extra = stack.writeTag();
        if (extra != null) tag.put("tag", extra);
        stacks.add(tag);
    }

    /** Fills in a joining player from their file; a new player keeps the spawn defaults. */
    void load(ServerPlayer player) {
        Path file = file(player);
        if (!Files.isRegularFile(file)) return;
        NbtTag.Compound root;
        try {
            root = NbtIo.readCompressed(file);
        } catch (IOException | RuntimeException e) {
            System.err.println("[Server] Couldn't read " + player.name + "'s data, starting fresh: " + e.getMessage());
            return;
        }
        try {
            if (root.get("Pos") instanceof NbtTag.NbtList pos && pos.size() == 3) {
                player.x = ((NbtTag.Double) pos.values().get(0)).value();
                player.y = ((NbtTag.Double) pos.values().get(1)).value();
                player.z = ((NbtTag.Double) pos.values().get(2)).value();
            }
            if (root.get("Rotation") instanceof NbtTag.NbtList rotation && rotation.size() == 2) {
                player.yaw = ((NbtTag.Float) rotation.values().get(0)).value();
                player.pitch = ((NbtTag.Float) rotation.values().get(1)).value();
            }
            if (root.get("playerGameType") instanceof NbtTag.Int mode) player.gameMode = Math.max(0, Math.min(3, mode.value()));
            if (root.get("HealF") instanceof NbtTag.Float health) player.health = Math.min(ServerPlayer.MAX_HEALTH, health.value());
            if (root.get("FallDistance") instanceof NbtTag.Float fall) player.fallDistance = fall.value();
            player.food.load(intOr(root, "foodLevel", 20), floatOr(root, "foodSaturationLevel", 5f),
                floatOr(root, "foodExhaustionLevel", 0f), intOr(root, "foodTickTimer", 0));
            player.inventory.currentItem = Math.max(0, Math.min(8, intOr(root, "SelectedItemSlot", 0)));
            if (root.get("Inventory") instanceof NbtTag.NbtList list)
                for (NbtTag element : list.values()) {
                    if (!(element instanceof NbtTag.Compound tag) || !(tag.get("id") instanceof NbtTag.Str id)) continue;
                    int slot = tag.get("Slot") instanceof NbtTag.Byte b ? b.value() & 0xFF : -1;
                    int count = tag.get("Count") instanceof NbtTag.Byte c ? c.value() : 1;
                    int damage = tag.get("Damage") instanceof NbtTag.Short d ? d.value() : 0;
                    Item item = server.items.fromVanilla(new ResourceLocation(id.value()), damage);
                    if (item == null || count <= 0) continue;
                    ItemStack stack = new ItemStack(item, count, item.isDamageable() ? damage : 0);
                    if (tag.get("tag") instanceof NbtTag.Compound extra) stack.readTag(extra);
                    player.inventory.setBySaveSlot(slot, stack);
                }
        } catch (RuntimeException e) {
            System.err.println("[Server] Part of " + player.name + "'s data couldn't be read: " + e.getMessage());
        }
        // Logged out on the death screen: back at spawn with a fresh life.
        if (player.health <= 0) {
            player.health = ServerPlayer.MAX_HEALTH;
            player.food.reset();
            player.x = server.spawnX + 0.5;
            player.y = server.spawnY;
            player.z = server.spawnZ + 0.5;
        }
    }

    long loadTime() {
        Path file = directory.getParent().resolve("server.dat");
        try {
            if (Files.isRegularFile(file) && NbtIo.readCompressed(file).get("DayTime") instanceof NbtTag.Long time) return time.value();
        } catch (IOException | RuntimeException e) {
            System.err.println("[Server] Couldn't read the time of day: " + e.getMessage());
        }
        return 0;
    }

    void saveTime(long timeOfDay) {
        NbtTag.Compound root = new NbtTag.Compound();
        root.put("DayTime", new NbtTag.Long(timeOfDay));
        try {
            Files.createDirectories(directory.getParent());
            NbtIo.writeCompressed(root, directory.getParent().resolve("server.dat"));
        } catch (IOException e) {
            System.err.println("[Server] Couldn't save the time of day: " + e.getMessage());
        }
    }

    private static int intOr(NbtTag.Compound tag, String name, int fallback) {
        return tag.get(name) instanceof NbtTag.Int value ? value.value() : fallback;
    }

    private static float floatOr(NbtTag.Compound tag, String name, float fallback) {
        return tag.get(name) instanceof NbtTag.Float value ? value.value() : fallback;
    }
}
