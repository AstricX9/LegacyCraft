package net.minecraft.entity.monster;

import net.minecraft.client.entity.Player;
import net.minecraft.entity.EntityArrow;
import net.minecraft.entity.ai.EntityAI;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/**
 * The skeleton (1.8.9 EntitySkeleton, HYPOTHESIS: recalled): 20 health,
 * speed 0.25. Normal skeletons carry a bow and shoot the player from up to
 * 15 blocks every 1-3 seconds (more often up close), burn in daylight
 * unless shaded and run for shade while burning; they drop 0-2 arrows and
 * 0-2 bones. Wither skeletons (type 1, the Nether's: four in five
 * skeletons spawned there) are 1.2x the size (0.72 x 2.535), carry a stone
 * sword (4 + 5 attack), never burn, wither what they hit for 10 seconds
 * and drop 0-1 coal, 0-2 bones and rarely a wither skeleton skull.
 */
public final class EntitySkeleton extends EntityMob {
    /** 0 normal, 1 wither (SkeletonType). */
    public int skeletonType;
    private final ArrowAttack arrowAttack = new ArrowAttack(this);
    private final EntityAI.AttackOnCollide meleeAttack = new EntityAI.AttackOnCollide(this, 1.2);
    private boolean combatSet;

    public EntitySkeleton(World world, double x, double y, double z) {
        super(world, x, y, z);
        width = 0.6f;
        height = 1.95f;
        tasks.add(1, new EntityAI.Swimming(this));
        tasks.add(3, new FleeSun(this, 1.0));
        tasks.add(4, new CombatTask(this));
        tasks.add(5, new EntityAI.Wander(this, 1.0));
        tasks.add(6, new EntityAI.WatchClosest(this, 8f));
        tasks.add(6, new EntityAI.LookIdle(this));
        targetTasks.add(1, new EntityAI.HurtByTarget(this));
        targetTasks.add(2, new EntityAI.NearestAttackableTarget(this));
    }

    @Override public String entityId() { return "Skeleton"; }
    @Override public float maxHealth() { return 20f; }
    @Override public double movementSpeed() { return 0.25; }
    @Override protected float attackDamage() { return skeletonType == 1 ? 4f : 2f; }
    @Override public boolean isImmuneToFire() { return skeletonType == 1; }
    @Override protected String livingSound() { return "mob.skeleton.say"; }
    @Override protected String hurtSound() { return "mob.skeleton.hurt"; }
    @Override protected String deathSound() { return "mob.skeleton.death"; }
    @Override protected int experiencePoints() { return 5 + (heldItem != null ? 1 + rand.nextInt(3) : 0); }
    @Override public float eyeHeight() { return skeletonType == 1 ? 2.1f : 1.74f; }

    @Override
    protected void playStepSound(int bx, int by, int bz, net.minecraft.block.Block block) {
        playSound("mob.skeleton.step", 0.15f, 1f);
    }

    /** EntitySkeleton.setSkeletonType: the wither skeleton is bigger and hits harder. */
    public void setSkeletonType(int type) {
        skeletonType = type;
        width = type == 1 ? 0.72f : 0.6f;
        height = type == 1 ? 2.535f : 1.95f;
    }

    /** EntitySkeleton.onInitialSpawn: in the Nether, four in five are wither skeletons with stone swords; others get bows. */
    @Override
    public void onInitialSpawn() {
        if (world.isNether() && rand.nextInt(5) > 0) {
            setSkeletonType(1);
            heldItem = stack("stone_sword");
        } else {
            setSkeletonType(0);
            heldItem = stack("bow");
        }
        combatSet = false;
    }

    private ItemStack stack(String name) {
        var items = world.itemLookup();
        Item item = items == null ? null : items.apply(name);
        return item == null ? null : new ItemStack(item);
    }

    private boolean hasBow() { return heldItem != null && heldItem.item().name().path().equals("bow"); }

    /**
     * EntitySkeleton.onLivingUpdate: in daylight, bright and under the open
     * sky, a normal skeleton catches fire for 8 seconds now and then.
     */
    @Override
    protected void livingUpdate() {
        if (skeletonType == 0 && host != null && !world.isNether()) {
            int sub = net.minecraft.world.MobSpawner.skylightSubtracted(world, host.worldTime());
            if (sub < 4) {
                float brightness = brightness();
                int bx = floor(x), by = (int) Math.round(y), bz = floor(z);
                if (brightness > 0.5f && rand.nextFloat() * 30f < (brightness - 0.4f) * 2f && by >= 0 && by < World.HEIGHT
                    && world.getSkyLight(bx, by, bz) >= 15 && !isInWater()) setFire(8);
            }
        }
        super.livingUpdate();
    }

    /** EntitySkeleton.attackEntityAsMob: a wither skeleton's hit withers for 10 seconds. */
    @Override
    public boolean attackEntityAsMob(Player target) {
        boolean hit = super.attackEntityAsMob(target);
        if (hit && skeletonType == 1)
            target.addPotionEffect(new net.minecraft.potion.PotionEffect(net.minecraft.potion.Potion.WITHER, 200, 0));
        return hit;
    }

    /**
     * EntitySkeleton.attackEntityWithRangedAttack: an arrow at 1.6 with
     * scatter 14 - 4 x difficulty, damage 2 x the distance factor plus a
     * little, and the bow sound.
     */
    void shootAt(Player target, float distanceFactor) {
        if (host == null) return;
        int difficulty = host.difficulty();
        EntityArrow arrow = EntityArrow.aimedAt(world, this, target, 1.6f, 14 - difficulty * 4, rand);
        arrow.damage = distanceFactor * 2.0f + rand.nextGaussian() * 0.25 + difficulty * 0.11f;
        int power = net.minecraft.enchantment.EnchantmentHelper.level(net.minecraft.enchantment.Enchantment.POWER, heldItem);
        int punch = net.minecraft.enchantment.EnchantmentHelper.level(net.minecraft.enchantment.Enchantment.PUNCH, heldItem);
        if (power > 0) arrow.damage += power * 0.5 + 0.5;
        if (punch > 0) arrow.knockbackStrength = punch;
        if (net.minecraft.enchantment.EnchantmentHelper.level(net.minecraft.enchantment.Enchantment.FLAME, heldItem) > 0 || skeletonType == 1)
            arrow.setFire(100);
        playSound("random.bow", 1f, 1f / (rand.nextFloat() * 0.4f + 0.8f));
        host.spawn(arrow);
    }

    @Override
    protected void dropFewItems(boolean byPlayer, int looting) {
        if (skeletonType == 1) {
            int coal = rand.nextInt(3 + looting) - 1;
            for (int i = 0; i < coal; i++) dropItem("coal", 1);
        } else {
            int arrows = rand.nextInt(3 + looting);
            for (int i = 0; i < arrows; i++) dropItem("arrow", 1);
        }
        int bones = rand.nextInt(3 + looting);
        for (int i = 0; i < bones; i++) dropItem("bone", 1);
    }

    @Override protected void addRareDrop(int bonus) { if (skeletonType == 1) dropItem("skull_wither", 1); }

    @Override
    public void writeNbt(net.minecraft.nbt.NbtTag.Compound tag) {
        super.writeNbt(tag);
        tag.put("SkeletonType", new net.minecraft.nbt.NbtTag.Byte((byte) skeletonType));
    }

    @Override
    public void readNbt(net.minecraft.nbt.NbtTag.Compound tag) {
        super.readNbt(tag);
        if (tag.get("SkeletonType") instanceof net.minecraft.nbt.NbtTag.Byte type) setSkeletonType(type.value());
        heldItem = stack(skeletonType == 1 ? "stone_sword" : "bow");
    }

    /** EntitySkeleton.setCombatTask: the bow's arrow attack, or the sword's melee charge at 1.2. */
    private static final class CombatTask extends EntityAI {
        private final EntitySkeleton skeleton;
        private EntityAI active;
        CombatTask(EntitySkeleton skeleton) {
            this.skeleton = skeleton;
            mutexBits = 3;
        }
        private EntityAI pick() { return skeleton.hasBow() ? skeleton.arrowAttack : skeleton.meleeAttack; }
        @Override public boolean shouldExecute() {
            active = pick();
            return active.shouldExecute();
        }
        @Override public boolean continueExecuting() { return active != null && active.continueExecuting(); }
        @Override public void startExecuting() { if (active != null) active.startExecuting(); }
        @Override public void resetTask() { if (active != null) active.resetTask(); }
        @Override public void updateTask() { if (active != null) active.updateTask(); }
    }

    /**
     * EntityAIArrowAttack(1.0, 20, 60, 15): walk toward the target until it
     * has been in sight for a second within 15 blocks, then stand and shoot
     * every 20-60 ticks (scaled by distance).
     */
    static final class ArrowAttack extends EntityAI {
        private final EntitySkeleton skeleton;
        private Player target;
        private int rangedAttackTime = -1, seeTime;
        ArrowAttack(EntitySkeleton skeleton) {
            this.skeleton = skeleton;
            mutexBits = 3;
        }
        @Override public boolean shouldExecute() {
            Player t = skeleton.attackTarget();
            if (t == null || t.dead) return false;
            target = t;
            return true;
        }
        @Override public boolean continueExecuting() { return shouldExecute() || !skeleton.navigator().noPath(); }
        @Override public void resetTask() {
            target = null;
            seeTime = 0;
            rangedAttackTime = -1;
        }
        @Override public void updateTask() {
            if (target == null) return;
            double distance = skeleton.distanceSqTo(target.x, target.boundingBox().minY(), target.z);
            boolean sees = skeleton.canSee(target);
            if (sees) seeTime++;
            else seeTime = 0;
            if (distance <= 225.0 && seeTime >= 20) skeleton.navigator().clearPath();
            else skeleton.navigator().tryMoveTo(target.x, target.y, target.z, 1.0);
            skeleton.lookHelper().setLookPosition(target.x, target.y + target.eyeHeight(), target.z, 30f, 30f);
            if (--rangedAttackTime == 0) {
                if (distance > 225.0 || !sees) return;
                float f = (float) Math.sqrt(distance) / 15f;
                skeleton.shootAt(target, Math.max(0.1f, Math.min(1f, f)));
                rangedAttackTime = (int) Math.floor(f * 40f + 20f);
            } else if (rangedAttackTime < 0) {
                float f = (float) Math.sqrt(distance) / 15f;
                rangedAttackTime = (int) Math.floor(f * 40f + 20f);
            }
        }
    }

    /** EntityAIFleeSun: burning in daylight under the sky, walk to a shaded spot within 10 blocks. */
    private static final class FleeSun extends EntityAI {
        private final EntitySkeleton skeleton;
        private final double speed;
        private double tx, ty, tz;
        FleeSun(EntitySkeleton skeleton, double speed) {
            this.skeleton = skeleton;
            this.speed = speed;
            mutexBits = 1;
        }
        @Override public boolean shouldExecute() {
            if (skeleton.host() == null || skeleton.world().isNether() || !skeleton.isBurning()) return false;
            if (net.minecraft.world.MobSpawner.skylightSubtracted(skeleton.world(), skeleton.host().worldTime()) >= 4) return false;
            World w = skeleton.world();
            int bx = (int) Math.floor(skeleton.x()), by = (int) Math.floor(skeleton.boundingBox().minY()), bz = (int) Math.floor(skeleton.z());
            if (by < 0 || by >= World.HEIGHT || w.getSkyLight(bx, by, bz) < 15) return false;
            var random = skeleton.getRNG();
            for (int i = 0; i < 10; i++) {
                int x = bx + random.nextInt(20) - 10, y = by + random.nextInt(6) - 3, z = bz + random.nextInt(20) - 10;
                if (y < 0 || y >= World.HEIGHT) continue;
                if (w.getSkyLight(x, y, z) < 15 && skeleton.blockPathWeight(x, y, z) < 0f) {
                    tx = x; ty = y; tz = z;
                    return true;
                }
            }
            return false;
        }
        @Override public boolean continueExecuting() { return !skeleton.navigator().noPath(); }
        @Override public void startExecuting() { skeleton.navigator().tryMoveTo(tx, ty, tz, speed); }
    }
}
