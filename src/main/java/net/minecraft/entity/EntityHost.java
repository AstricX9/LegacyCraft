package net.minecraft.entity;

import net.minecraft.client.entity.Player;

import java.util.List;

/**
 * What a ticking entity may ask of the game around it (the parts of 1.8.9's
 * World and WorldServer that entities call): the player, the other
 * entities, sounds, particles, explosions and rules. The client implements
 * it; tests can supply a fake.
 */
public interface EntityHost {
    /** The player, or null while there is none (dead players still count, so mobs can stop targeting them). */
    Player player();
    /** Every entity in the world, including the caller. */
    List<Entity> entities();
    void spawn(Entity entity);
    void playSound(String event, double x, double y, double z, float volume, float pitch);
    /** A world particle by vanilla name ("explode", "largeexplode", "hugeexplosion", "smoke", "crit", "magicCrit"). */
    void particle(String kind, double x, double y, double z, double vx, double vy, double vz);
    /**
     * World.newExplosion: {@code exploder} (may be null) is excluded from the
     * damage, {@code smoking} breaks blocks, {@code attacker} names whoever caused it
     * in the death message (null for none).
     */
    void explode(Entity exploder, double x, double y, double z, float size, boolean smoking, String attacker);
    /** World.newExplosion with fire: a flaming explosion also sets a third of the blasted air above full blocks alight. */
    default void explode(Entity exploder, double x, double y, double z, float size, boolean flaming, boolean smoking, String attacker) {
        explode(exploder, x, y, z, size, smoking, attacker);
    }
    /** 0 Peaceful .. 3 Hard. */
    int difficulty();
    boolean gameRule(String name);
    /** The time of day (DayTime), for the sky light subtracted at night. */
    long worldTime();
    /** The droplets and bubbles of something falling into water (Entity.resetHeight). */
    default void waterSplash(MovingEntity entity) {}
    /**
     * A splash potion broke at this block (World.playAuxSFX 2002): glass
     * shards, a ring of spell particles in the potion's colour and the smash.
     */
    default void potionSmash(int x, int y, int z, int potionData) {}
    /** Awards the player an achievement by id ("breedCow"). */
    default void achievement(String id) {}
    /** World.destroyBlock: breaks a block with its break effects, dropping it if {@code drop}. */
    default void breakBlock(int x, int y, int z, boolean drop) {}
    /** World.playAuxSFX 2001: a block's break sound and particles without changing it. */
    default void blockBreakEffects(int x, int y, int z, net.minecraft.block.BlockState state) {}
    /** Sets a block (with block updates) by name and metadata. */
    default void setBlock(int x, int y, int z, String block, int metadata) {}
    /** Crumbs of an item (EnumParticleTypes.ITEM_CRACK) by item name. */
    default void itemCrack(String item, double x, double y, double z, double vx, double vy, double vz) {}
    /**
     * World.playBroadcastSound (the wither's spawn roar, 1013): heard by
     * everyone whatever the distance, from 2 blocks away in its direction.
     */
    default void globalSound(String event, double x, double y, double z) { playSound(event, x, y, z, 1f, 1f); }
    /** Experience earned straight away (the orb reached the player). */
    default void giveExperience(int amount) {}
}
