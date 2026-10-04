package net.minecraft.entity.monster;

import net.minecraft.client.entity.Player;
import net.minecraft.entity.DamageSource;
import net.minecraft.entity.EntitySmallFireball;
import net.minecraft.entity.ai.EntityAI;
import net.minecraft.world.World;

/**
 * The blaze (1.8.9 EntityBlaze, HYPOTHESIS: recalled): 20 health, 6
 * attack, speed 0.23, follows from 48 blocks, 0.6 x 1.8, never burns,
 * always lit, crackling and smoking. It drifts down slowly and rises
 * toward a target above it (to a random height around it every 5 seconds),
 * is hurt by water, hits up close, and from up to 16 blocks charges for 3
 * seconds (alight) then fires three fireballs 6 ticks apart and rests 5
 * seconds. Drops 0-1 blaze rods (plus looting) when the player kills it;
 * 10 experience.
 */
public final class EntityBlaze extends EntityMob {
    private float heightOffset = 0.5f;
    private int heightOffsetUpdateTime;
    /** Charging its fireballs: drawn burning (EntityBlaze.setOnFire flag). */
    public boolean charged;

    public EntityBlaze(World world, double x, double y, double z) {
        super(world, x, y, z);
        width = 0.6f;
        height = 1.8f;
        tasks.add(4, new FireballAttack(this));
        tasks.add(7, new EntityAI.Wander(this, 1.0));
        tasks.add(8, new EntityAI.WatchClosest(this, 8f));
        tasks.add(8, new EntityAI.LookIdle(this));
        targetTasks.add(1, new EntityAI.HurtByTarget(this));
        targetTasks.add(2, new EntityAI.NearestAttackableTarget(this));
    }

    @Override public String entityId() { return "Blaze"; }
    @Override public float maxHealth() { return 20f; }
    @Override public double movementSpeed() { return 0.23; }
    @Override public float followRange() { return 48f; }
    @Override protected float attackDamage() { return 6f; }
    @Override public boolean isImmuneToFire() { return true; }
    @Override public boolean isBurning() { return charged; }
    @Override public boolean fullBright() { return true; }
    @Override protected String livingSound() { return "mob.blaze.breathe"; }
    @Override protected String hurtSound() { return "mob.blaze.hit"; }
    @Override protected String deathSound() { return "mob.blaze.death"; }
    @Override protected int experiencePoints() { return 10; }
    @Override protected void fall(float distance, float multiplier) {}

    /** EntityBlaze.onLivingUpdate: falling slowed to 60%, the crackle and two puffs of smoke. */
    @Override
    protected void livingUpdate() {
        if (!onGround && motionY < 0) motionY *= 0.6;
        if (host != null) {
            if (rand.nextInt(24) == 0)
                host.playSound("fire.fire", x + 0.5, y + 0.5, z + 0.5, 1f + rand.nextFloat(), rand.nextFloat() * 0.7f + 0.3f);
            for (int i = 0; i < 2; i++)
                host.particle("largesmoke", x + (rand.nextDouble() - 0.5) * width, y + rand.nextDouble() * height,
                    z + (rand.nextDouble() - 0.5) * width, 0, 0, 0);
        }
        super.livingUpdate();
    }

    /** EntityBlaze.updateAITasks: water hurts; it rises toward a target above its chosen height. */
    @Override
    protected void updateEntityActionState() {
        if (isInWater()) attackEntityFrom(DamageSource.DROWN, 1f, null);
        if (--heightOffsetUpdateTime <= 0) {
            heightOffsetUpdateTime = 100;
            heightOffset = 0.5f + (float) rand.nextGaussian() * 3f;
        }
        Player target = attackTarget();
        if (target != null && target.y + target.eyeHeight() > y + eyeHeight() + heightOffset) {
            motionY += (0.30000001192092896 - motionY) * 0.30000001192092896;
            isAirBorne = true;
        }
        super.updateEntityActionState();
    }

    @Override
    protected void dropFewItems(boolean byPlayer, int looting) {
        if (!byPlayer) return;
        int rods = rand.nextInt(2 + looting);
        for (int i = 0; i < rods; i++) dropItem("blaze_rod", 1);
    }

    /** EntityBlaze.AIFireballAttack. */
    private static final class FireballAttack extends EntityAI {
        private final EntityBlaze blaze;
        private int attackStep, attackTime;
        FireballAttack(EntityBlaze blaze) {
            this.blaze = blaze;
            mutexBits = 3;
        }
        @Override public boolean shouldExecute() {
            Player target = blaze.attackTarget();
            return target != null && !target.dead;
        }
        @Override public void startExecuting() { attackStep = 0; }
        @Override public void resetTask() { blaze.charged = false; }
        @Override public void updateTask() {
            attackTime--;
            Player target = blaze.attackTarget();
            if (target == null) return;
            double distance = blaze.distanceSqTo(target.x, target.y, target.z);
            if (distance < 4.0) {
                if (attackTime <= 0) {
                    attackTime = 20;
                    blaze.attackEntityAsMob(target);
                }
                blaze.moveHelper().setMoveTo(target.x, target.y, target.z, 1.0);
            } else if (distance < 256.0) {
                double dx = target.x - blaze.x();
                double dy = target.boundingBox().minY() + Player.HEIGHT / 2.0 - (blaze.y() + blaze.height / 2.0);
                double dz = target.z - blaze.z();
                if (attackTime <= 0) {
                    attackStep++;
                    if (attackStep == 1) {
                        attackTime = 60;
                        blaze.charged = true;
                    } else if (attackStep <= 4) {
                        attackTime = 6;
                    } else {
                        attackTime = 100;
                        attackStep = 0;
                        blaze.charged = false;
                    }
                    if (attackStep > 1 && blaze.host() != null) {
                        float spread = (float) Math.sqrt(Math.sqrt(distance)) * 0.5f;
                        blaze.host().playSound("mob.ghast.fireball", blaze.x(), blaze.y(), blaze.z(), 10f,
                            (blaze.getRNG().nextFloat() - blaze.getRNG().nextFloat()) * 0.2f + 1f);
                        var random = blaze.getRNG();
                        EntitySmallFireball fireball = EntitySmallFireball.shotBy(blaze.world(), blaze,
                            dx + random.nextGaussian() * spread, dy, dz + random.nextGaussian() * spread, random);
                        fireball.setPosition(blaze.x(), blaze.y() + blaze.height / 2.0 + 0.5, blaze.z());
                        fireball.prevX = fireball.x(); fireball.prevY = fireball.y(); fireball.prevZ = fireball.z();
                        blaze.host().spawn(fireball);
                    }
                }
                blaze.lookHelper().setLookPosition(target.x, target.y + target.eyeHeight(), target.z, 10f, 10f);
            } else {
                blaze.navigator().clearPath();
                blaze.moveHelper().setMoveTo(target.x, target.y, target.z, 1.0);
            }
        }
    }
}
