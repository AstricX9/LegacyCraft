package net.minecraft.sim;

import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;

/**
 * Something that happened in the simulation, simulation to client. An event
 * is a snapshot: whatever it carries (a {@link BlockState}, an
 * {@link ItemStack}) must not be changed by the emitter afterwards, so a
 * sink may keep it, batch it, or send it later.
 *
 * <p>Events describe <em>effects and changes a viewer needs</em>, not the
 * simulation's internals. A local client also reads the shared world and
 * entities directly, so it needs few of the state events; a remote client
 * builds its picture from them. Which events a given viewer receives is the
 * interest manager's decision, not the emitter's.
 */
public sealed interface GameEvent {

    // ---- world --------------------------------------------------------------------------------

    /** A block changed. The network layer batches these per chunk. */
    record BlockChanged(int x, int y, int z, BlockState state) implements GameEvent {}
    /** The break sound and particles of a block, without changing it (World.playAuxSFX 2001). */
    record BlockBreakEffects(int x, int y, int z, BlockState state) implements GameEvent {}
    /** Glass shards, spell ring and smash of a splash potion (World.playAuxSFX 2002). */
    record PotionSmash(int x, int y, int z, int potionData) implements GameEvent {}
    /** An explosion's flash and smoke; the blocks it destroyed arrive as {@link BlockChanged}. */
    record Explosion(double x, double y, double z, float size, boolean smoking) implements GameEvent {}
    /** The clock, for the sky and sun: world age and time of day, in ticks. */
    record TimeUpdate(long worldTime, long dayTime) implements GameEvent {}

    // ---- sound and particles ------------------------------------------------------------------

    record Sound(String event, double x, double y, double z, float volume, float pitch) implements GameEvent {}
    /** Heard by everyone whatever the distance (the wither's spawn roar). */
    record GlobalSound(String event, double x, double y, double z) implements GameEvent {}
    /** A world particle by vanilla name ("smoke", "crit", "heart"...). */
    record Particle(String kind, double x, double y, double z, double vx, double vy, double vz) implements GameEvent {}
    /** Crumbs of an item by item name. */
    record ItemCrack(String item, double x, double y, double z, double vx, double vy, double vz) implements GameEvent {}

    // ---- entities -----------------------------------------------------------------------------

    record EntitySpawned(int id, String type, double x, double y, double z, float yaw, float pitch) implements GameEvent {}
    record EntityRemoved(int id) implements GameEvent {}
    /** Absolute position; the network layer sends small moves as deltas ({@code EntityMovePacket}) and big ones as teleports. */
    record EntityMoved(int id, double x, double y, double z, float yaw, float pitch, boolean onGround) implements GameEvent {}
    /** A one-off animation or state change on an entity (hurt flash, death, golem arm swing, creeper fuse, splash). */
    record EntityStatus(int id, Status status) implements GameEvent {
        public enum Status { HURT, DIED, ATTACK_SWING, EXPLODING, SPLASH, IGNITED }
    }

    // ---- the player ---------------------------------------------------------------------------

    /** The simulation moved or corrected the player (respawn, teleport, a rejected {@link PlayerCommand.Move}). */
    record PlayerPosition(double x, double y, double z, float yaw, float pitch) implements GameEvent {}
    record PlayerVitals(float health, int food, float saturation) implements GameEvent {}
    /** One inventory slot changed (0-35 main, 36-39 armour); {@code stack} is null for empty. */
    record InventorySlot(int slot, ItemStack stack) implements GameEvent {}
    record ExperienceGained(int amount) implements GameEvent {}
    record ExperienceState(float progress, int level, int total) implements GameEvent {}
    record AchievementAwarded(String id) implements GameEvent {}
    /** The player is now riding this entity (or -1: dismounted). */
    record Riding(int entityId) implements GameEvent {}

    // ---- text ---------------------------------------------------------------------------------

    /** A chat line or system message for the player. */
    record Message(String text) implements GameEvent {}
}
