package net.minecraft.sim;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityHost;
import net.minecraft.entity.MovingEntity;

/**
 * The first concrete mapping of the existing {@link EntityHost} onto the
 * seam: every call that is purely an effect for a viewer (sound, particles,
 * break effects, achievements, experience, splashes) becomes a
 * {@link GameEvent} on an {@link EventSink}. What entities need from the
 * world itself (the player, the entity list, spawning, explosions, rules)
 * stays abstract for the simulation to supply.
 */
public abstract class EventEntityHost implements EntityHost {
    private final EventSink sink;

    protected EventEntityHost(EventSink sink) { this.sink = sink; }

    /** The runtime id of an entity, or -1 if it is not in the world. */
    protected abstract int entityId(Entity entity);

    @Override public void playSound(String event, double x, double y, double z, float volume, float pitch) {
        sink.emit(new GameEvent.Sound(event, x, y, z, volume, pitch));
    }
    @Override public void globalSound(String event, double x, double y, double z) { sink.emit(new GameEvent.GlobalSound(event, x, y, z)); }
    @Override public void particle(String kind, double x, double y, double z, double vx, double vy, double vz) {
        sink.emit(new GameEvent.Particle(kind, x, y, z, vx, vy, vz));
    }
    @Override public void itemCrack(String item, double x, double y, double z, double vx, double vy, double vz) {
        sink.emit(new GameEvent.ItemCrack(item, x, y, z, vx, vy, vz));
    }
    @Override public void blockBreakEffects(int x, int y, int z, BlockState state) { sink.emit(new GameEvent.BlockBreakEffects(x, y, z, state)); }
    @Override public void potionSmash(int x, int y, int z, int potionData) { sink.emit(new GameEvent.PotionSmash(x, y, z, potionData)); }
    @Override public void waterSplash(MovingEntity entity) {
        sink.emit(new GameEvent.EntityStatus(entityId(entity), GameEvent.EntityStatus.Status.SPLASH));
    }
    @Override public void achievement(String id) { sink.emit(new GameEvent.AchievementAwarded(id)); }
    @Override public void giveExperience(int amount) { sink.emit(new GameEvent.ExperienceGained(amount)); }
}
