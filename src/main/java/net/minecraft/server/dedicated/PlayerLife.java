package net.minecraft.server.dedicated;

import net.minecraft.entity.EntityItem;
import net.minecraft.inventory.InventoryPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.v47.Clientbound;
import net.minecraft.util.FoodStats;

/**
 * Health and hunger for the server's players: the food bar ticks with the
 * shared {@link FoodStats}, damage has the usual half-second of
 * invulnerability after a hit, death drops everything and shows the death
 * screen, and respawning starts a new life at the world spawn.
 */
final class PlayerLife {
    /** Ticks of invulnerability after taking damage (vanilla's maxHurtResistantTime / 2). */
    private static final int HURT_COOLDOWN = 10;
    /** Eating takes 32 ticks. */
    static final int EAT_TICKS = 32;
    /** Below this y the void hurts. */
    private static final double VOID_Y = -64;

    private final DedicatedServer server;

    PlayerLife(DedicatedServer server) { this.server = server; }

    void tick(ServerPlayer player) {
        if (player.dead) return;
        if (player.hurtCooldown > 0) player.hurtCooldown--;
        if (player.y < VOID_Y) damage(player, 4f, "death.attack.outOfWorld", true);
        if (player.dead) return;
        if (player.isSurvivalLike()) player.food.tick(owner(player));
        if (player.useStartTick >= 0 && server.tick() - player.useStartTick >= EAT_TICKS) finishEating(player);
    }

    private FoodStats.Owner owner(ServerPlayer player) {
        return new FoodStats.Owner() {
            @Override public int difficulty() { return server.properties().difficulty; }
            @Override public boolean naturalRegeneration() { return true; }
            @Override public boolean shouldHeal() { return player.health > 0 && player.health < ServerPlayer.MAX_HEALTH; }
            @Override public float health() { return player.health; }
            @Override public void heal(float amount) { PlayerLife.this.heal(player, amount); }
            @Override public void starve(float amount) { damage(player, amount, "death.attack.starve", true); }
        };
    }

    void heal(ServerPlayer player, float amount) {
        if (!player.dead) player.health = Math.min(ServerPlayer.MAX_HEALTH, player.health + amount);
    }

    void addExhaustion(ServerPlayer player, float amount) {
        if (player.isSurvivalLike()) player.food.addExhaustion(amount);
    }

    /**
     * Hurts a Survival or Adventure player; {@code ignoreCooldown} for damage
     * that ticks on its own clock (starving, the void). Returns whether it hurt.
     */
    boolean damage(ServerPlayer player, float amount, String deathKey, boolean ignoreCooldown) {
        if (player.dead || !player.isSurvivalLike() || amount <= 0) return false;
        if (player.hurtCooldown > 0 && !ignoreCooldown) return false;
        player.hurtCooldown = HURT_COOLDOWN;
        player.health -= amount;
        Clientbound.EntityStatus hurt = new Clientbound.EntityStatus(player.entityId, Clientbound.EntityStatus.HURT);
        player.connection.send(hurt);
        server.sendToTrackers(player, hurt);
        if (player.health <= 0) die(player, deathKey);
        return true;
    }

    /** Health 0: the client shows the death screen; everything carried scatters around the body. */
    private void die(ServerPlayer player, String deathKey) {
        player.dead = true;
        player.deathTick = server.tick();
        player.health = 0;
        player.useStartTick = -1;
        player.digStartTick = -1;
        Clientbound.EntityStatus dead = new Clientbound.EntityStatus(player.entityId, Clientbound.EntityStatus.DEAD);
        player.connection.send(dead);
        server.sendToTrackers(player, dead);
        dropEverything(player);
        DedicatedServer.log(player.name + " died (" + deathKey + ")");
        server.broadcast(DedicatedServer.chat(DedicatedServer.translate(deathKey, null, player.name), 1));
        syncHealth(player);
    }

    private void dropEverything(ServerPlayer player) {
        java.util.function.Consumer<ItemStack> keep = stack -> player.inventory.addItemStackToInventory(stack);
        player.openWindow.close(keep);
        if (player.openWindow != player.playerWindow) player.playerWindow.close(keep);
        player.openWindow = player.playerWindow;
        InventoryPlayer inventory = player.inventory;
        for (ItemStack[] slots : new ItemStack[][]{inventory.mainInventory, inventory.armorInventory})
            for (int i = 0; i < slots.length; i++) {
                if (slots[i] == null) continue;
                server.spawnItem(EntityItem.thrown(server.world, player.x, player.eyeY(), player.z, 0, 0, 0, slots[i], true, server.random));
                slots[i] = null;
            }
    }

    /** The respawn button: a fresh life at the world spawn, in the same dimension. */
    void respawn(ServerPlayer player) {
        if (!player.dead) return;
        player.dead = false;
        player.health = ServerPlayer.MAX_HEALTH;
        player.food.reset();
        player.hurtCooldown = 0;
        player.fallDistance = 0;
        player.shownHealth = -1;
        player.connection.send(new Clientbound.Respawn(0, server.properties().difficulty, player.gameMode,
            server.world.isFlat() ? "flat" : "default"));
        server.teleport(player, server.spawnX + 0.5, server.spawnY, server.spawnZ + 0.5, 0, 0);
        player.connection.send(server.abilities(player));
        player.connection.send(new Clientbound.HeldItemChange(player.inventory.currentItem));
        server.syncInventory(player, true);
        syncHealth(player);
        // Other players see the respawned player as a new entity.
        server.hideFromOthers(player);
    }

    /** Tells the client its health and hunger when either changed. */
    void syncHealth(ServerPlayer player) {
        int food = player.food.getFoodLevel();
        float saturation = player.food.getSaturationLevel();
        if (player.health == player.shownHealth && food == player.shownFood && saturation == player.shownSaturation) return;
        player.shownHealth = player.health;
        player.shownFood = food;
        player.shownSaturation = saturation;
        player.connection.send(new Clientbound.UpdateHealth(Math.max(0, player.health), food, saturation));
    }

    // ---- eating ----------------------------------------------------------------------------

    /** Raising the held food (right click): it is eaten 32 ticks later unless the hand changes first. */
    boolean startEating(ServerPlayer player) {
        ItemStack held = player.heldStack();
        if (held == null || !held.item().isFood() || held.item().useAction() != Item.UseAction.EAT) return false;
        boolean always = held.item().vanillaId().path().equals("golden_apple");
        if (!player.isSurvivalLike() || !(always || player.food.needFood())) return false;
        player.useStartTick = server.tick();
        player.useSlot = player.inventory.currentItem;
        server.sendToTrackers(player, new Clientbound.EntityMetadata(player.entityId, server.metadata(player)));
        return true;
    }

    void stopEating(ServerPlayer player) {
        if (player.useStartTick < 0) return;
        player.useStartTick = -1;
        server.sendToTrackers(player, new Clientbound.EntityMetadata(player.entityId, server.metadata(player)));
    }

    private void finishEating(ServerPlayer player) {
        ItemStack stack = player.inventory.mainInventory[player.useSlot];
        stopEating(player);
        if (player.useSlot != player.inventory.currentItem || stack == null || !stack.item().isFood()) return;
        Item item = stack.item();
        stack.split(1);
        if (stack.isEmpty()) player.inventory.mainInventory[player.useSlot] = null;
        player.food.addStats(item.food(), item.saturation());
        String name = item.name().path();
        if (name.equals("mushroom_stew") || name.equals("rabbit_stew")) {
            ItemStack bowl = new ItemStack(server.itemByName("bowl"));
            if (player.inventory.mainInventory[player.useSlot] == null) player.inventory.mainInventory[player.useSlot] = bowl;
            else if (!player.inventory.addItemStackToInventory(bowl)) server.throwItem(player, bowl);
        }
        player.connection.send(new Clientbound.EntityStatus(player.entityId, Clientbound.EntityStatus.FINISHED_USING));
    }
}
