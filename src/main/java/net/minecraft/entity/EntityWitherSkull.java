package net.minecraft.entity;

import net.minecraft.client.entity.Player;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.world.World;

/**
 * A wither's skull (1.8.9 EntityWitherSkull, HYPOTHESIS: recalled): a
 * fireball 0.3125 wide that never burns and can't be batted back. It does
 * 8 damage to what it hits (5 magic damage with no shooter), healing the
 * wither 5 when that kills, withers the player for 10 seconds on Normal or
 * 40 on Hard (Wither II), then explodes with strength 1, breaking blocks
 * if mobGriefing allows. A blue ("dangerous") skull is slower (keeps 73%
 * of its speed a tick) and blasts through anything but bedrock, end
 * portals and command blocks as if it resisted at most 0.8.
 */
public final class EntityWitherSkull extends EntityFireball {
    /** The blue skull (EntityWitherSkull.isInvulnerable). */
    public boolean invulnerable;

    public EntityWitherSkull(World world, double x, double y, double z) { super(world, x, y, z, 0.3125f); }

    public static EntityWitherSkull shotBy(World world, EntityLiving shooter, double ax, double ay, double az, java.util.Random random) {
        EntityWitherSkull skull = new EntityWitherSkull(world, shooter.x(), shooter.y(), shooter.z());
        skull.aim(shooter, ax, ay, az, random);
        return skull;
    }

    @Override public String entityId() { return "WitherSkull"; }
    @Override public float renderScale() { return 1f; }
    @Override public boolean deflectable() { return false; }
    @Override protected float motionFactor() { return invulnerable ? 0.73f : super.motionFactor(); }

    @Override
    protected void onImpact(Object hit, WorldRenderer.Hit block) {
        if (host == null) {
            remove();
            return;
        }
        String attacker = shooter != null ? shooter.displayName() : null;
        if (hit instanceof Player player) {
            boolean hurt;
            if (shooter != null) {
                hurt = player.attackBy(DamageSource.MOB, 8f, shooter.x(), shooter.z(), attacker);
                if (hurt && player.health <= 0) shooter.heal(5f);
            } else {
                player.attackBy(DamageSource.MAGIC, 5f, x - motionX, z - motionZ, null);
            }
            int seconds = host.difficulty() == 2 ? 10 : host.difficulty() == 3 ? 40 : 0;
            if (seconds > 0)
                player.addPotionEffect(new net.minecraft.potion.PotionEffect(net.minecraft.potion.Potion.WITHER, 20 * seconds, 1));
        } else if (hit instanceof EntityLiving living) {
            // EntityWither.attackEntityFrom: a wither ignores another wither's skulls.
            boolean witherOnWither = living instanceof net.minecraft.entity.boss.EntityWither
                && shooter instanceof net.minecraft.entity.boss.EntityWither;
            if (!witherOnWither) {
                if (shooter != null) {
                    if (living.attackEntityFrom(DamageSource.MOB, 8f, null) && !living.isAlive()) shooter.heal(5f);
                } else {
                    living.attackEntityFrom(DamageSource.MAGIC, 5f, null);
                }
            }
        }
        remove();
        host.explode(this, x, y, z, 1f, false, host.gameRule("mobGriefing"), attacker);
    }

    @Override
    public void writeNbt(net.minecraft.nbt.NbtTag.Compound tag) {
        super.writeNbt(tag);
        tag.put("Invulnerable", new net.minecraft.nbt.NbtTag.Byte((byte) (invulnerable ? 1 : 0)));
    }

    @Override
    public void readNbt(net.minecraft.nbt.NbtTag.Compound tag) {
        super.readNbt(tag);
        if (tag.get("Invulnerable") instanceof net.minecraft.nbt.NbtTag.Byte b) invulnerable = b.value() != 0;
    }
}
