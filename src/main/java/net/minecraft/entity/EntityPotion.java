package net.minecraft.entity;

import net.minecraft.client.entity.Player;
import net.minecraft.item.Potions;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

import java.util.List;

/**
 * A thrown splash potion (1.8.9 EntityPotion and EntityThrowable,
 * HYPOTHESIS: recalled). It leaves from just below the thrower's eyes at
 * 0.5 blocks a tick, aimed 20 degrees above where they look, falls with
 * 0.05 gravity and 0.99 drag (0.8 in water), ignores its thrower for the
 * first 5 ticks and breaks on the first block or living thing it meets.
 * Everything living within 4 blocks gets the potion's effects, weaker the
 * further away (full strength for the one it hit); mobs only feel instant
 * Healing and Harming, since LegacyCraft mobs have no other effects.
 */
public final class EntityPotion extends EntityThrowable {
    /** The potion's data value (the item damage). */
    public int potionData;

    public EntityPotion(World world, double x, double y, double z, int potionData) {
        super(world, x, y, z);
        this.potionData = potionData;
    }

    /** EntityThrowable(world, thrower) for ItemPotion: 0.5 blocks a tick, 20 degrees up, 0.05 gravity. */
    public static EntityPotion thrownBy(World world, Player player, int potionData, java.util.Random random) {
        return new EntityPotion(world, player.x, player.y, player.z, potionData).aimFrom(player, random);
    }

    @Override protected float gravity() { return 0.05f; }
    @Override protected float velocity() { return 0.5f; }
    @Override protected float pitchOffset() { return -20f; }
    @Override public String entityId() { return "ThrownPotion"; }
    @Override public String itemName() { return "potion"; }

    /** EntityPotion.onImpact: the effects around, then the smash (World.playAuxSFX 2002). */
    @Override
    protected void onImpact(Object direct) {
        remove();
        if (host == null) return;
        List<PotionEffect> effects = Potions.effects(potionData);
        if (!effects.isEmpty()) {
            AxisAlignedBB area = boundingBox().grow(4.0, 2.0, 4.0);
            Player player = host.player();
            if (player != null && player.health > 0 && player.boundingBox().intersects(area))
                affect(player, player.x, player.y, player.z, direct == player, effects);
            for (Entity entity : host.entities())
                if (entity instanceof EntityLiving living && living.isAlive() && living.boundingBox().intersects(area))
                    affect(living, living.x(), living.y(), living.z(), direct == living, effects);
        }
        host.potionSmash(floor(x), floor(y), floor(z), potionData);
    }

    private void affect(Object target, double tx, double ty, double tz, boolean direct, List<PotionEffect> effects) {
        double distSq = (tx - x) * (tx - x) + (ty - y) * (ty - y) + (tz - z) * (tz - z);
        if (distSq >= 16.0) return;
        double scale = direct ? 1.0 : 1.0 - Math.sqrt(distSq) / 4.0;
        Player thrower = thrownByPlayer && host != null ? host.player() : null;
        for (PotionEffect effect : effects) {
            Potion potion = effect.potion();
            if (potion.isInstant()) {
                instant(target, potion, effect.amplifier(), scale, thrower);
            } else if (target instanceof Player player) {
                int ticks = (int) (scale * effect.duration() + 0.5);
                if (ticks > 20) player.addPotionEffect(new PotionEffect(potion, ticks, effect.amplifier()));
            }
        }
    }

    /** Potion.affectEntity: Healing restores 4 per level, Harming does 6 per level of magic damage, scaled by nearness. */
    public static void instant(Object target, Potion potion, int amplifier, double scale, Player thrower) {
        if (potion == Potion.HEAL) {
            float amount = (float) (Math.max(4 << amplifier, 0) * scale);
            if (target instanceof Player player) player.heal(amount);
            else if (target instanceof EntityLiving living) living.heal(amount);
        } else if (potion == Potion.HARM) {
            float amount = (float) ((6 << amplifier) * scale);
            if (target instanceof Player player) player.attack(DamageSource.MAGIC, amount);
            else if (target instanceof EntityLiving living) living.attackEntityFrom(DamageSource.MAGIC, amount, thrower);
        }
    }

    @Override
    public void writeNbt(net.minecraft.nbt.NbtTag.Compound tag) {
        super.writeNbt(tag);
        net.minecraft.nbt.NbtTag.Compound potion = new net.minecraft.nbt.NbtTag.Compound();
        potion.put("id", new net.minecraft.nbt.NbtTag.Short((short) 373));
        potion.put("Damage", new net.minecraft.nbt.NbtTag.Short((short) potionData));
        potion.put("Count", new net.minecraft.nbt.NbtTag.Byte((byte) 1));
        tag.put("Potion", potion);
    }

    @Override
    public void readNbt(net.minecraft.nbt.NbtTag.Compound tag) {
        super.readNbt(tag);
        if (tag.get("Potion") instanceof net.minecraft.nbt.NbtTag.Compound potion
            && potion.get("Damage") instanceof net.minecraft.nbt.NbtTag.Short damage) potionData = damage.value() & 0xFFFF;
    }
}
