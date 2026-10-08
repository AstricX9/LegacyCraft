package net.minecraft.entity.monster;

import net.minecraft.entity.DamageSource;
import net.minecraft.entity.ai.EntityAI;
import net.minecraft.world.World;

/**
 * The zombie (1.8.9 EntityZombie, HYPOTHESIS: recalled): 20 health, 2
 * armour, speed 0.23, 3 attack, 0.6 x 1.95. It hunts a visible player
 * within 35 blocks (16 to notice) and hits on contact, burns in daylight
 * under the open sky, and drops 0-2 rotten flesh (plus 0-looting) and
 * rarely an iron ingot, carrot or potato. One in 20 is a baby (half size,
 * half as fast again). Not yet: villager zombies, reinforcements, breaking
 * doors, picking up items and the random armour.
 */
public final class EntityZombie extends EntityMob {
    private boolean child;

    public EntityZombie(World world, double x, double y, double z) {
        super(world, x, y, z);
        width = 0.6f;
        height = 1.95f;
        tasks.add(0, new EntityAI.Swimming(this));
        tasks.add(2, new EntityAI.AttackOnCollide(this, 1.0));
        tasks.add(7, new EntityAI.Wander(this, 1.0));
        tasks.add(8, new EntityAI.WatchClosest(this, 8f));
        tasks.add(8, new EntityAI.LookIdle(this));
        targetTasks.add(1, new EntityAI.HurtByTarget(this));
        targetTasks.add(2, new EntityAI.NearestAttackableTarget(this));
    }

    @Override public String entityId() { return "Zombie"; }
    @Override public float maxHealth() { return 20f; }
    @Override public double movementSpeed() { return child ? 0.23 * 1.5 : 0.23; }
    @Override public float followRange() { return 35f; }
    @Override protected float attackDamage() { return 3f; }
    @Override protected String livingSound() { return "mob.zombie.say"; }
    @Override protected String hurtSound() { return "mob.zombie.hurt"; }
    @Override protected String deathSound() { return "mob.zombie.death"; }
    @Override protected int experiencePoints() { return child ? 12 : 5; }
    @Override public boolean isChild() { return child; }
    @Override public float eyeHeight() { return child ? 0.93f : 1.74f; }
    @Override protected float soundPitch() { return (rand.nextFloat() - rand.nextFloat()) * 0.2f + (child ? 1.5f : 1f); }

    @Override
    protected void playStepSound(int bx, int by, int bz, net.minecraft.block.Block block) {
        playSound("mob.zombie.step", 0.15f, 1f);
    }

    /** EntityZombie.onInitialSpawn: one in 20 is a baby. */
    @Override
    public void onInitialSpawn() {
        if (rand.nextFloat() < 0.05f) setChild(true);
    }

    public void setChild(boolean child) {
        this.child = child;
        width = child ? 0.3f : 0.6f;
        height = child ? 0.975f : 1.95f;
    }

    /**
     * EntityZombie.onLivingUpdate: an adult in daylight, bright and under the
     * open sky, catches fire for 8 seconds now and then.
     */
    @Override
    protected void livingUpdate() {
        if (!child && host != null && !world.isNether()
            && net.minecraft.world.MobSpawner.skylightSubtracted(world, host.worldTime()) < 4) {
            float brightness = brightness();
            int bx = floor(x), by = (int) Math.round(y), bz = floor(z);
            if (brightness > 0.5f && rand.nextFloat() * 30f < (brightness - 0.4f) * 2f && by >= 0 && by < World.HEIGHT
                && world.getSkyLight(bx, by, bz) >= 15 && !isInWater()) setFire(8);
        }
        super.livingUpdate();
    }

    /** EntityZombie.attackEntityAsMob: a burning zombie sets what it hits alight (more often on Hard). */
    @Override
    public boolean attackEntityAsMob(net.minecraft.client.entity.Player target) {
        boolean hit = super.attackEntityAsMob(target);
        if (hit && isBurning() && host != null && rand.nextFloat() < host.difficulty() * 0.3f) target.setFire(2 * host.difficulty());
        return hit;
    }

    @Override
    protected void dropFewItems(boolean byPlayer, int looting) {
        int count = rand.nextInt(3);
        if (looting > 0) count += rand.nextInt(looting + 1);
        for (int i = 0; i < count; i++) dropItem("rotten_flesh", 1);
    }

    @Override
    protected void addRareDrop(int bonus) {
        switch (rand.nextInt(3)) {
            case 0 -> dropItem("iron_ingot", 1);
            case 1 -> dropItem("carrot", 1);
            default -> dropItem("potato", 1);
        }
    }

    /** Zombies wear 2 armour points (the armour attribute), which takes damage down by 8%. */
    @Override
    protected void damageEntity(DamageSource source, float amount) {
        if (!source.isUnblockable()) amount = amount * (25 - 2) / 25f;
        super.damageEntity(source, amount);
    }

    @Override
    public void writeNbt(net.minecraft.nbt.NbtTag.Compound tag) {
        super.writeNbt(tag);
        tag.put("IsBaby", new net.minecraft.nbt.NbtTag.Byte((byte) (child ? 1 : 0)));
    }

    @Override
    public void readNbt(net.minecraft.nbt.NbtTag.Compound tag) {
        super.readNbt(tag);
        if (tag.get("IsBaby") instanceof net.minecraft.nbt.NbtTag.Byte baby) setChild(baby.value() != 0);
    }
}
