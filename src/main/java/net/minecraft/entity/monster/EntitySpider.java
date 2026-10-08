package net.minecraft.entity.monster;

import net.minecraft.client.entity.Player;
import net.minecraft.entity.ai.EntityAI;
import net.minecraft.world.World;

/**
 * The spider (1.8.9 EntitySpider, HYPOTHESIS: recalled): 16 health, speed
 * 0.3, 2 attack, 1.4 x 0.9. It climbs any wall it walks into, leaps at its
 * target from 2-4 blocks away, and only goes after a player in the dark; in
 * the light it ignores players unless hurt, and now and then gives up a
 * chase. Drops 0-2 string (plus 0-looting) and, when the player killed it,
 * a spider eye one time in three. Not yet: spider jockeys and the Hard
 * difficulty potion effects.
 */
public final class EntitySpider extends EntityMob {
    /** Touching a wall last tick: it climbs (isBesideClimbableBlock, metadata 16). */
    private boolean climbing;

    public EntitySpider(World world, double x, double y, double z) {
        super(world, x, y, z);
        width = 1.4f;
        height = 0.9f;
        tasks.add(1, new EntityAI.Swimming(this));
        tasks.add(3, new LeapAtTarget(this, 0.4f));
        tasks.add(4, new SpiderAttack(this));
        tasks.add(5, new EntityAI.Wander(this, 0.8));
        tasks.add(6, new EntityAI.WatchClosest(this, 8f));
        tasks.add(6, new EntityAI.LookIdle(this));
        targetTasks.add(1, new EntityAI.HurtByTarget(this));
        targetTasks.add(2, new SpiderTarget(this));
    }

    @Override public String entityId() { return "Spider"; }
    @Override public float maxHealth() { return 16f; }
    @Override public double movementSpeed() { return 0.30000001192092896; }
    @Override protected float attackDamage() { return 2f; }
    @Override public float eyeHeight() { return 0.65f; }
    @Override protected String livingSound() { return "mob.spider.say"; }
    @Override protected String hurtSound() { return "mob.spider.say"; }
    @Override protected String deathSound() { return "mob.spider.death"; }
    @Override protected int experiencePoints() { return 5; }

    @Override
    protected void playStepSound(int bx, int by, int bz, net.minecraft.block.Block block) {
        playSound("mob.spider.step", 0.15f, 1f);
    }

    public boolean isBesideClimbableBlock() { return climbing; }

    /** EntitySpider.onUpdate: whether it is up against a wall, for climbing next tick. */
    @Override
    public void tick() {
        super.tick();
        climbing = collidedHorizontally;
    }

    @Override protected boolean onLadder() { return climbing || super.onLadder(); }

    @Override
    protected void dropFewItems(boolean byPlayer, int looting) {
        int count = rand.nextInt(3);
        if (looting > 0) count += rand.nextInt(looting + 1);
        for (int i = 0; i < count; i++) dropItem("string", 1);
        if (byPlayer && (rand.nextInt(3) == 0 || rand.nextInt(1 + looting) > 0)) dropItem("spider_eye", 1);
    }

    /** EntityAILeapAtTarget: on the ground, 2-4 blocks from the target, one check in 5 jumps at it. */
    private static final class LeapAtTarget extends EntityAI {
        private final EntitySpider spider;
        private final float leapY;
        LeapAtTarget(EntitySpider spider, float leapY) {
            this.spider = spider;
            this.leapY = leapY;
            mutexBits = 5;
        }
        @Override public boolean shouldExecute() {
            Player target = spider.attackTarget();
            if (target == null || !spider.onGround) return false;
            double distance = spider.distanceSqTo(target.x, target.y, target.z);
            return distance >= 4.0 && distance <= 16.0 && spider.getRNG().nextInt(5) == 0;
        }
        @Override public boolean continueExecuting() { return !spider.onGround; }
        @Override public void startExecuting() {
            Player target = spider.attackTarget();
            double dx = target.x - spider.x(), dz = target.z - spider.z();
            float length = (float) Math.sqrt(dx * dx + dz * dz);
            if (length < 1.0E-4f) return;
            spider.motionX += dx / length * 0.5 * 0.8 + spider.motionX * 0.2;
            spider.motionZ += dz / length * 0.5 * 0.8 + spider.motionZ * 0.2;
            spider.motionY = leapY;
        }
    }

    /** EntitySpider.AISpiderAttack: attack on contact, but one check in 100 in the light it gives up. */
    private static final class SpiderAttack extends EntityAI {
        private final EntitySpider spider;
        private final EntityAI.AttackOnCollide attack;
        SpiderAttack(EntitySpider spider) {
            this.spider = spider;
            this.attack = new EntityAI.AttackOnCollide(spider, 1.0);
            mutexBits = 3;
        }
        @Override public boolean shouldExecute() { return attack.shouldExecute(); }
        @Override public boolean continueExecuting() {
            if (spider.brightness() >= 0.5f && spider.getRNG().nextInt(100) == 0) {
                spider.setAttackTarget(null);
                return false;
            }
            return attack.continueExecuting();
        }
        @Override public void startExecuting() { attack.startExecuting(); }
        @Override public void resetTask() { attack.resetTask(); }
        @Override public void updateTask() { attack.updateTask(); }
    }

    /** EntitySpider.AISpiderTarget: only looks for a player while it stands in the dark. */
    private static final class SpiderTarget extends EntityAI {
        private final EntitySpider spider;
        private final EntityAI nearest;
        SpiderTarget(EntitySpider spider) {
            this.spider = spider;
            this.nearest = new EntityAI.NearestAttackableTarget(spider);
            mutexBits = 1;
        }
        @Override public boolean shouldExecute() { return spider.brightness() < 0.5f && nearest.shouldExecute(); }
        @Override public boolean continueExecuting() { return nearest.continueExecuting(); }
        @Override public void startExecuting() { nearest.startExecuting(); }
        @Override public void resetTask() { nearest.resetTask(); }
    }
}
