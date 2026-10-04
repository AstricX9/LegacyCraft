package net.minecraft.entity.monster;

import net.minecraft.client.entity.Player;
import net.minecraft.entity.DamageSource;
import net.minecraft.entity.EntityLargeFireball;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.ai.EntityAI;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

/**
 * The ghast (1.8.9 EntityGhast + EntityFlying, HYPOTHESIS: recalled): 10
 * health, 4 x 4, never burns, flies without gravity (0.91 drag) toward a
 * random point within 16 blocks whenever it gets close to the last one or
 * strays 60 away (giving up on routes that hit blocks), faces where it
 * flies or the player within 64 blocks. It notices the player within 100
 * blocks across (4 up or down); with them in sight within 64 blocks it
 * charges for half a second (opening its eyes, shooting face) and spits an
 * explosive fireball, then waits 2 seconds. Its cries carry far (volume
 * 10). A fireball batted back into it kills it outright. Drops 0-1 ghast
 * tears and 0-2 gunpowder (plus looting); 5 experience.
 */
public final class EntityGhast extends EntityLiving implements IMob {
    /** Charging a fireball: the shooting face (EntityGhast.isAttacking). */
    public boolean attacking;
    private double moveX, moveY, moveZ;
    private boolean moving;
    private int courseChangeCooldown;

    public EntityGhast(World world, double x, double y, double z) {
        super(world, x, y, z);
        width = height = 4f;
        tasks.add(5, new RandomFly(this));
        tasks.add(7, new LookAround(this));
        tasks.add(7, new FireballAttack(this));
        targetTasks.add(1, new FindNearestPlayer(this));
    }

    @Override public String entityId() { return "Ghast"; }
    @Override public float maxHealth() { return 10f; }
    @Override public double movementSpeed() { return 0.7; }
    @Override public float followRange() { return 100f; }
    @Override public boolean isImmuneToFire() { return true; }
    @Override public float eyeHeight() { return 2.6f; }
    @Override protected String livingSound() { return "mob.ghast.moan"; }
    @Override protected String hurtSound() { return "mob.ghast.scream"; }
    @Override protected String deathSound() { return "mob.ghast.death"; }
    @Override protected float soundVolume() { return 10f; }
    @Override protected int experiencePoints() { return 5; }
    @Override protected void fall(float distance, float multiplier) {}
    @Override protected boolean canTriggerWalking() { return false; }

    @Override
    public void tick() {
        if (host != null && host.difficulty() == 0) {
            remove();
            return;
        }
        super.tick();
    }

    /** EntityFlying.moveEntityWithHeading: flight with 0.91 drag (0.8 in water, 0.5 in lava), no gravity. */
    @Override
    protected void moveEntityWithHeading(float strafe, float forward) {
        if (isInWater()) {
            moveFlying(strafe, forward, 0.02f);
            move(motionX, motionY, motionZ);
            motionX *= 0.8; motionY *= 0.8; motionZ *= 0.8;
        } else if (isInLava()) {
            moveFlying(strafe, forward, 0.02f);
            move(motionX, motionY, motionZ);
            motionX *= 0.5; motionY *= 0.5; motionZ *= 0.5;
        } else {
            moveFlying(strafe, forward, onGround ? 0.1f * 0.16277136f / (0.546f * 0.546f * 0.546f) : 0.02f);
            move(motionX, motionY, motionZ);
            motionX *= 0.91; motionY *= 0.91; motionZ *= 0.91;
        }
        prevLimbSwingAmount = limbSwingAmount;
        double dx = x - prevX, dz = z - prevZ;
        float moved = Math.min(1f, (float) Math.sqrt(dx * dx + dz * dz) * 4f);
        limbSwingAmount += (moved - limbSwingAmount) * 0.4f;
        limbSwing += limbSwingAmount;
    }

    /** The AI, then EntityGhast.GhastMoveHelper. */
    @Override
    protected void updateEntityActionState() {
        super.updateEntityActionState();
        if (!moving) return;
        double dx = moveX - x, dy = moveY - y, dz = moveZ - z;
        double distance = dx * dx + dy * dy + dz * dz;
        if (courseChangeCooldown-- <= 0) {
            courseChangeCooldown += rand.nextInt(5) + 2;
            distance = Math.sqrt(distance);
            if (routeClear(dx / distance, dy / distance, dz / distance, distance)) {
                motionX += dx / distance * 0.1;
                motionY += dy / distance * 0.1;
                motionZ += dz / distance * 0.1;
            } else {
                moving = false;
            }
        }
    }

    /** GhastMoveHelper.isNotColliding: its box stepped along the route meets no block. */
    private boolean routeClear(double sx, double sy, double sz, double distance) {
        AxisAlignedBB box = boundingBox();
        for (int i = 1; i < distance; i++) {
            box = box.offset(sx, sy, sz);
            if (!isFreeOfBlocks(box)) return false;
        }
        return true;
    }

    /** EntityLivingBase.getLook(1.0): the direction the head faces. */
    private double[] look() {
        double yaw = Math.toRadians(rotationYawHead), pitch = Math.toRadians(rotationPitch);
        double f = Math.cos(-yaw - Math.PI), f1 = Math.sin(-yaw - Math.PI), f2 = -Math.cos(-pitch), f3 = Math.sin(-pitch);
        return new double[] {f1 * f2, f3, f * f2};
    }

    @Override
    protected void dropFewItems(boolean byPlayer, int looting) {
        int tears = rand.nextInt(2 + looting);
        for (int i = 0; i < tears; i++) dropItem("ghast_tear", 1);
        int powder = rand.nextInt(3 + looting);
        for (int i = 0; i < powder; i++) dropItem("gunpowder", 1);
    }

    /** EntityGhast.AIRandomFly: a new random point within 16 blocks when near the last one or far from it. */
    private static final class RandomFly extends EntityAI {
        private final EntityGhast ghast;
        RandomFly(EntityGhast ghast) {
            this.ghast = ghast;
            mutexBits = 1;
        }
        @Override public boolean shouldExecute() {
            if (!ghast.moving) return true;
            double dx = ghast.moveX - ghast.x(), dy = ghast.moveY - ghast.y(), dz = ghast.moveZ - ghast.z();
            double distance = dx * dx + dy * dy + dz * dz;
            return distance < 1.0 || distance > 3600.0;
        }
        @Override public boolean continueExecuting() { return false; }
        @Override public void startExecuting() {
            var random = ghast.getRNG();
            ghast.moveX = ghast.x() + (random.nextFloat() * 2f - 1f) * 16f;
            ghast.moveY = ghast.y() + (random.nextFloat() * 2f - 1f) * 16f;
            ghast.moveZ = ghast.z() + (random.nextFloat() * 2f - 1f) * 16f;
            ghast.moving = true;
        }
    }

    /** EntityGhast.AILookAround: face the way it flies, or the target within 64 blocks. */
    private static final class LookAround extends EntityAI {
        private final EntityGhast ghast;
        LookAround(EntityGhast ghast) {
            this.ghast = ghast;
            mutexBits = 2;
        }
        @Override public boolean shouldExecute() { return true; }
        @Override public void updateTask() {
            Player target = ghast.attackTarget();
            if (target == null) {
                ghast.renderYawOffset = ghast.rotationYaw = -((float) Math.atan2(ghast.motionX, ghast.motionZ)) * 180f / (float) Math.PI;
            } else if (ghast.distanceSqTo(target.x, target.y, target.z) < 4096.0) {
                double dx = target.x - ghast.x(), dz = target.z - ghast.z();
                ghast.renderYawOffset = ghast.rotationYaw = -((float) Math.atan2(dx, dz)) * 180f / (float) Math.PI;
            }
            ghast.rotationYawHead = ghast.rotationYaw;
        }
    }

    /** EntityGhast.AIFireballAttack. */
    private static final class FireballAttack extends EntityAI {
        private final EntityGhast ghast;
        private int attackTimer;
        FireballAttack(EntityGhast ghast) { this.ghast = ghast; }
        @Override public boolean shouldExecute() { return ghast.attackTarget() != null; }
        @Override public void startExecuting() { attackTimer = 0; }
        @Override public void resetTask() { ghast.attacking = false; }
        @Override public void updateTask() {
            Player target = ghast.attackTarget();
            if (target == null) return;
            if (ghast.distanceSqTo(target.x, target.y, target.z) < 4096.0 && ghast.canSee(target)) {
                attackTimer++;
                var random = ghast.getRNG();
                if (attackTimer == 10 && ghast.host() != null)
                    ghast.host().playSound("mob.ghast.charge", ghast.x(), ghast.y(), ghast.z(), 10f, (random.nextFloat() - random.nextFloat()) * 0.2f + 1f);
                if (attackTimer == 20 && ghast.host() != null) {
                    double[] look = ghast.look();
                    double dx = target.x - (ghast.x() + look[0] * 4.0);
                    double dy = target.boundingBox().minY() + Player.HEIGHT / 2.0 - (0.5 + ghast.y() + ghast.height / 2.0);
                    double dz = target.z - (ghast.z() + look[2] * 4.0);
                    ghast.host().playSound("mob.ghast.fireball", ghast.x(), ghast.y(), ghast.z(), 10f, (random.nextFloat() - random.nextFloat()) * 0.2f + 1f);
                    EntityLargeFireball fireball = EntityLargeFireball.shotBy(ghast.world(), ghast, dx, dy, dz, random);
                    fireball.explosionPower = 1;
                    fireball.setPosition(ghast.x() + look[0] * 4.0, ghast.y() + ghast.height / 2.0 + 0.5, ghast.z() + look[2] * 4.0);
                    fireball.prevX = fireball.x(); fireball.prevY = fireball.y(); fireball.prevZ = fireball.z();
                    ghast.host().spawn(fireball);
                    attackTimer = -40;
                }
            } else if (attackTimer > 0) {
                attackTimer--;
            }
            ghast.attacking = attackTimer > 10;
        }
    }

    /** EntityAIFindEntityNearestPlayer: the player within 100 blocks across and 4 up or down; no sight needed. */
    private static final class FindNearestPlayer extends EntityAI {
        private final EntityGhast ghast;
        FindNearestPlayer(EntityGhast ghast) {
            this.ghast = ghast;
            mutexBits = 1;
        }
        @Override public boolean shouldExecute() {
            Player player = ghast.host() == null ? null : ghast.host().player();
            if (player == null || player.dead || player.disableDamage || player.noClip) return false;
            double range = ghast.followRange() * (player.sneaking ? 0.8 : 1.0);
            AxisAlignedBB area = ghast.boundingBox().grow(range, 4.0, range);
            if (!player.boundingBox().intersects(area)) return false;
            if (ghast.distanceSqTo(player.x, player.y, player.z) > range * range) return false;
            ghast.setAttackTarget(player);
            return true;
        }
        @Override public boolean continueExecuting() {
            Player target = ghast.attackTarget();
            if (target == null || target.dead || target.disableDamage || target.noClip) return false;
            double range = ghast.followRange();
            return ghast.distanceSqTo(target.x, target.y, target.z) <= range * range;
        }
        @Override public void resetTask() { ghast.setAttackTarget(null); }
    }
}
