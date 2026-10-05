package net.minecraft.entity.passive;

import net.minecraft.entity.DamageSource;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.ai.EntityAI;
import net.minecraft.entity.monster.IMob;
import net.minecraft.world.World;

/**
 * The iron golem (1.8.9 EntityIronGolem, HYPOTHESIS: behaviour recalled from
 * play, see DEV-039): 100 health, 1.4 x 2.9, slow (0.25), never takes fall
 * damage and barely moves when hit. It hunts the nearest hostile mob within
 * 16 blocks and hits it for 7-21, flinging it upwards. Golems the player
 * builds never turn on the player (LegacyCraft has no villages, so every
 * golem here is player-built). Drops 3-5 iron ingots and 0-2 poppies.
 */
public final class EntityIronGolem extends EntityLiving {
    /** Ticks left of the arm swing after an attack (attackTimer), for the renderer. */
    public int attackTimer;
    /** Built from iron blocks and a pumpkin (PlayerCreated). */
    public boolean playerCreated = true;
    private EntityLiving target;
    private int attackCooldown, retargetDelay;

    public EntityIronGolem(World world, double x, double y, double z) {
        super(world, x, y, z);
        width = 1.4f;
        height = 2.9f;
        tasks.add(5, new EntityAI.Wander(this, 0.6));
        tasks.add(7, new EntityAI.WatchClosest(this, 6f));
        tasks.add(8, new EntityAI.LookIdle(this));
    }

    @Override public String entityId() { return "VillagerGolem"; }
    @Override public float maxHealth() { return 100f; }
    @Override public double movementSpeed() { return 0.25; }
    @Override public float followRange() { return 16f; }
    @Override protected float attackDamage() { return 15f; }
    @Override protected String hurtSound() { return "mob.irongolem.hit"; }
    @Override protected String deathSound() { return "mob.irongolem.death"; }
    @Override protected void fall(float distance, float multiplier) {}
    public EntityLiving target() { return target; }

    @Override
    protected void playStepSound(int bx, int by, int bz, net.minecraft.block.Block block) {
        playSound("mob.irongolem.walk", 1f, 1f);
    }

    /** Barely budges: a tenth of the usual knockback. */
    @Override
    public void knockBack(double dx, double dz) {
        double mx = motionX, my = motionY, mz = motionZ;
        super.knockBack(dx, dz);
        motionX = mx + (motionX - mx) * 0.1;
        motionY = my + (motionY - my) * 0.1;
        motionZ = mz + (motionZ - mz) * 0.1;
    }

    @Override
    protected void livingUpdate() {
        if (attackTimer > 0) attackTimer--;
        if (attackCooldown > 0) attackCooldown--;
        super.livingUpdate();
        huntMobs();
    }

    /** Picks the nearest hostile mob in range every so often, walks to it and hits it when close. */
    private void huntMobs() {
        if (host == null) return;
        if (target != null && (target.isRemoved() || !target.isAlive() || target.distanceSqTo(x, y, z) > 32 * 32)) target = null;
        if (target == null && --retargetDelay <= 0) {
            retargetDelay = 10;
            double best = followRange() * followRange();
            for (Entity entity : host.entities()) {
                if (!(entity instanceof EntityLiving mob) || !(entity instanceof IMob) || entity.isRemoved() || !mob.isAlive()) continue;
                double d = mob.distanceSqTo(x, y, z);
                if (d < best && Math.abs(mob.y() - y) < 8) {
                    best = d;
                    target = mob;
                }
            }
        }
        if (target == null) return;
        double reach = width * 2f * width * 2f + target.width;
        double dx = target.x() - x, dz = target.z() - z;
        rotationYawHead = (float) Math.toDegrees(Math.atan2(-dx, dz));
        if (dx * dx + dz * dz <= reach && Math.abs(target.y() - y) < 3) {
            if (attackCooldown == 0) attack(target);
        } else if (ticksExisted % 10 == 0) {
            navigator().tryMoveTo(target.x(), target.y(), target.z(), 1.0);
        }
    }

    /** Swings both arms: 7-21 damage and the target thrown 0.4 up. */
    public boolean attack(EntityLiving victim) {
        attackCooldown = 20;
        attackTimer = 10;
        float damage = 7f + rand.nextInt(15);
        boolean hit = victim.attackEntityFrom(DamageSource.MOB, damage, null);
        if (hit) victim.motionY += 0.4;
        playSound("mob.irongolem.throw", 1f, 1f);
        return hit;
    }

    @Override
    protected void dropFewItems(boolean byPlayer, int looting) {
        int poppies = rand.nextInt(3);
        for (int i = 0; i < poppies; i++) dropItem("red_flower", 1);
        int iron = 3 + rand.nextInt(3);
        for (int i = 0; i < iron; i++) dropItem("iron_ingot", 1);
    }

    @Override
    public void writeNbt(net.minecraft.nbt.NbtTag.Compound tag) {
        super.writeNbt(tag);
        tag.put("PlayerCreated", new net.minecraft.nbt.NbtTag.Byte((byte) (playerCreated ? 1 : 0)));
    }

    @Override
    public void readNbt(net.minecraft.nbt.NbtTag.Compound tag) {
        super.readNbt(tag);
        if (tag.get("PlayerCreated") instanceof net.minecraft.nbt.NbtTag.Byte b) playerCreated = b.value() != 0;
    }
}
