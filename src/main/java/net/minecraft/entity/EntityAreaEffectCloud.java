package net.minecraft.entity;

import net.minecraft.client.entity.Player;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A lingering cloud (1.9 EntityAreaEffectCloud, HYPOTHESIS: recalled): a
 * disc of particles that waits {@code waitTime} ticks, then for
 * {@code duration} ticks gives its effects to every living thing whose feet
 * are inside it, each at most once per {@code reapplicationDelay}; its
 * radius changes by {@code radiusPerTick} and by {@code radiusOnUse} each
 * time it touches someone. The dragon's breath is one (with its own purple
 * particles); a glass bottle scoops it up as dragon's breath.
 */
public final class EntityAreaEffectCloud extends MovingEntity {
    public float radius = 3f, radiusOnUse, radiusPerTick;
    public int duration = 600, waitTime = 20, reapplicationDelay = 20;
    public final List<PotionEffect> effects = new ArrayList<>();
    /** The dragon's breath (EnumParticleTypes.DRAGON_BREATH) rather than a potion swirl. */
    public boolean dragonBreath;
    /** The potion swirl's colour (red, green, blue) for non-breath clouds. */
    public float red = 1f, green = 1f, blue = 1f;
    /** Who made it (never hurt by it): the dragon, for its breath. */
    public MovingEntity owner;
    private final Map<Object, Integer> reapplication = new HashMap<>();

    public EntityAreaEffectCloud(World world, double x, double y, double z) {
        super(world, x, y, z);
        height = 0.5f;
    }

    @Override public String entityId() { return "AreaEffectCloud"; }
    @Override protected boolean canTriggerWalking() { return false; }
    @Override public boolean isImmuneToFire() { return true; }

    @Override
    public void tick() {
        if (frozen()) return;
        prevX = x; prevY = y; prevZ = z;
        ticksExisted++;
        width = radius * 2f;
        boolean waiting = ticksExisted < waitTime;
        if (host != null) particles(waiting);
        if (ticksExisted >= waitTime + duration) {
            remove();
            return;
        }
        if (waiting) return;
        if (radiusPerTick != 0f) {
            radius += radiusPerTick;
            if (radius < 0.5f) {
                remove();
                return;
            }
        }
        if (ticksExisted % 5 != 0 || host == null) return;
        reapplication.values().removeIf(until -> ticksExisted >= until);
        List<Object> inside = new ArrayList<>();
        for (Entity entity : host.entities())
            if (entity instanceof EntityLiving living && living != owner && living.isAlive() && inCloud(living.x, living.y, living.z))
                inside.add(living);
        for (Player player : host.players())
            if (!player.dead && !player.noClip && inCloud(player.x, player.y, player.z)) inside.add(player);
        for (Object target : inside) {
            if (reapplication.containsKey(target)) continue;
            reapplication.put(target, ticksExisted + reapplicationDelay);
            for (PotionEffect effect : effects) {
                if (effect.potion().isInstant()) EntityPotion.instant(target, effect.potion(), effect.amplifier(), 0.5, null);
                else if (target instanceof Player player) player.addPotionEffect(new PotionEffect(effect.potion(), effect.duration() / 4, effect.amplifier()));
            }
            if (radiusOnUse != 0f) {
                radius += radiusOnUse;
                if (radius < 0.5f) {
                    remove();
                    return;
                }
            }
        }
    }

    /** Feet within the radius across and no more than the cloud's half-block height below or above it. */
    private boolean inCloud(double px, double py, double pz) {
        double dx = px - x, dz = pz - z;
        return dx * dx + dz * dz <= radius * radius && py >= y - 0.5 && py <= y + height + 0.5;
    }

    private void particles(boolean waiting) {
        if (waiting) {
            if (rand.nextBoolean()) return;
            double angle = rand.nextFloat() * Math.PI * 2, r = Math.sqrt(rand.nextFloat()) * 0.2f;
            spawnParticle(x + Math.cos(angle) * r, z + Math.sin(angle) * r, 0, 0);
            return;
        }
        int count = (int) (Math.PI * radius * radius);
        for (int i = 0; i < count; i++) {
            double angle = rand.nextFloat() * Math.PI * 2, r = Math.sqrt(rand.nextFloat()) * radius;
            double px = x + Math.cos(angle) * r, pz = z + Math.sin(angle) * r;
            if (dragonBreath) spawnParticle(px, pz, (0.5 - rand.nextDouble()) * 0.15, (0.5 - rand.nextDouble()) * 0.15);
            else spawnParticle(px, pz, 0, 0);
        }
    }

    private void spawnParticle(double px, double pz, double vx, double vz) {
        if (dragonBreath) host.particle("dragonbreath", px, y, pz, vx, 0.01, vz);
        else host.particle("mobSpell", px, y, pz, red, green, blue);
    }

    @Override
    public void writeNbt(net.minecraft.nbt.NbtTag.Compound tag) {
        super.writeNbt(tag);
        tag.put("Age", new net.minecraft.nbt.NbtTag.Int(ticksExisted));
        tag.put("Duration", new net.minecraft.nbt.NbtTag.Int(duration));
        tag.put("WaitTime", new net.minecraft.nbt.NbtTag.Int(waitTime));
        tag.put("ReapplicationDelay", new net.minecraft.nbt.NbtTag.Int(reapplicationDelay));
        tag.put("Radius", new net.minecraft.nbt.NbtTag.Float(radius));
        tag.put("RadiusOnUse", new net.minecraft.nbt.NbtTag.Float(radiusOnUse));
        tag.put("RadiusPerTick", new net.minecraft.nbt.NbtTag.Float(radiusPerTick));
        tag.put("Particle", new net.minecraft.nbt.NbtTag.Str(dragonBreath ? "dragonbreath" : "mobSpell"));
        List<net.minecraft.nbt.NbtTag> list = new ArrayList<>();
        for (PotionEffect effect : effects) {
            net.minecraft.nbt.NbtTag.Compound e = new net.minecraft.nbt.NbtTag.Compound();
            e.put("Id", new net.minecraft.nbt.NbtTag.Byte((byte) effect.potion().id));
            e.put("Amplifier", new net.minecraft.nbt.NbtTag.Byte((byte) effect.amplifier()));
            e.put("Duration", new net.minecraft.nbt.NbtTag.Int(effect.duration()));
            list.add(e);
        }
        tag.put("Effects", new net.minecraft.nbt.NbtTag.NbtList(net.minecraft.nbt.NbtTag.COMPOUND, list));
    }

    @Override
    public void readNbt(net.minecraft.nbt.NbtTag.Compound tag) {
        super.readNbt(tag);
        if (tag.get("Age") instanceof net.minecraft.nbt.NbtTag.Int v) ticksExisted = v.value();
        if (tag.get("Duration") instanceof net.minecraft.nbt.NbtTag.Int v) duration = v.value();
        if (tag.get("WaitTime") instanceof net.minecraft.nbt.NbtTag.Int v) waitTime = v.value();
        if (tag.get("ReapplicationDelay") instanceof net.minecraft.nbt.NbtTag.Int v) reapplicationDelay = v.value();
        if (tag.get("Radius") instanceof net.minecraft.nbt.NbtTag.Float v) radius = v.value();
        if (tag.get("RadiusOnUse") instanceof net.minecraft.nbt.NbtTag.Float v) radiusOnUse = v.value();
        if (tag.get("RadiusPerTick") instanceof net.minecraft.nbt.NbtTag.Float v) radiusPerTick = v.value();
        if (tag.get("Particle") instanceof net.minecraft.nbt.NbtTag.Str v) dragonBreath = v.value().equals("dragonbreath");
        if (tag.get("Effects") instanceof net.minecraft.nbt.NbtTag.NbtList list)
            for (net.minecraft.nbt.NbtTag value : list.values())
                if (value instanceof net.minecraft.nbt.NbtTag.Compound e && e.get("Id") instanceof net.minecraft.nbt.NbtTag.Byte id) {
                    Potion potion = Potion.byId(id.value());
                    int amp = e.get("Amplifier") instanceof net.minecraft.nbt.NbtTag.Byte a ? a.value() : 0;
                    int dur = e.get("Duration") instanceof net.minecraft.nbt.NbtTag.Int d ? d.value() : 1;
                    if (potion != null) effects.add(new PotionEffect(potion, dur, amp));
                }
    }
}
