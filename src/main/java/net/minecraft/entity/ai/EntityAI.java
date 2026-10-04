package net.minecraft.entity.ai;

import net.minecraft.client.entity.Player;
import net.minecraft.entity.EntityLiving;

import java.util.ArrayList;
import java.util.List;

/**
 * Mob goals (1.8.9 EntityAIBase and EntityAITasks, HYPOTHESIS: recalled).
 * Tasks are kept in priority order; every third tick each task is checked
 * (a running one keeps going while it can, an idle one starts if it should
 * and no running task of equal or higher priority shares a mutex bit), and
 * on the other ticks running tasks only check whether to continue. Running
 * tasks update every tick.
 */
public abstract class EntityAI {
    /** Bits that can't run together: 1 movement, 2 looking, 4 jumping (swimming). */
    protected int mutexBits;

    public abstract boolean shouldExecute();
    public boolean continueExecuting() { return shouldExecute(); }
    public boolean isInterruptible() { return true; }
    public void startExecuting() {}
    public void resetTask() {}
    public void updateTask() {}

    /** A prioritised task list. */
    public static final class Tasks {
        private record Entry(int priority, EntityAI action) {}
        private final List<Entry> entries = new ArrayList<>();
        private final List<Entry> executing = new ArrayList<>();
        private int tickCount;

        public void add(int priority, EntityAI action) { entries.add(new Entry(priority, action)); }

        public void tick() {
            if (tickCount++ % 3 == 0) {
                for (Entry entry : entries) {
                    boolean running = executing.contains(entry);
                    if (running) {
                        if (canUse(entry) && entry.action.continueExecuting()) continue;
                        entry.action.resetTask();
                        executing.remove(entry);
                    }
                    if (canUse(entry) && entry.action.shouldExecute()) {
                        entry.action.startExecuting();
                        executing.add(entry);
                    }
                }
            } else {
                for (Entry entry : new ArrayList<>(executing)) {
                    if (!entry.action.continueExecuting()) {
                        entry.action.resetTask();
                        executing.remove(entry);
                    }
                }
            }
            for (Entry entry : new ArrayList<>(executing)) entry.action.updateTask();
        }

        private boolean canUse(Entry entry) {
            for (Entry other : entries) {
                if (other == entry) continue;
                if (entry.priority >= other.priority) {
                    if (executing.contains(other) && (entry.action.mutexBits & other.action.mutexBits) != 0) return false;
                } else if (executing.contains(other) && !other.action.isInterruptible()) {
                    return false;
                }
            }
            return true;
        }
    }

    // ---- shared goals ------------------------------------------------------------------------

    /** EntityAISwimming: in water or lava, jump 80% of ticks to stay up. */
    public static final class Swimming extends EntityAI {
        private final EntityLiving entity;
        public Swimming(EntityLiving entity) {
            this.entity = entity;
            mutexBits = 4;
            entity.navigator().setCanSwim(true);
        }
        @Override public boolean shouldExecute() { return entity.isInWater() || entity.isInLava(); }
        @Override public void updateTask() { if (entity.getRNG().nextFloat() < 0.8f) entity.setJumping(true); }
    }

    /**
     * EntityAIWander: 1 tick in 120 while not idle 100+ ticks, walk to the
     * best of 10 random spots within 10 blocks across and 7 up or down.
     */
    public static final class Wander extends EntityAI {
        private final EntityLiving entity;
        private final double speed;
        private double x, y, z;
        public Wander(EntityLiving entity, double speed) {
            this.entity = entity;
            this.speed = speed;
            mutexBits = 1;
        }
        @Override public boolean shouldExecute() {
            if (entity.idleAge() >= 100) return false;
            if (entity.getRNG().nextInt(120) != 0) return false;
            double[] target = randomTarget(entity, 10, 7);
            if (target == null) return false;
            x = target[0]; y = target[1]; z = target[2];
            return true;
        }
        @Override public boolean continueExecuting() { return !entity.navigator().noPath(); }
        @Override public void startExecuting() { entity.navigator().tryMoveTo(x, y, z, speed); }
    }

    /** RandomPositionGenerator.findRandomTarget: the best-weighted of 10 random offsets, or null. */
    public static double[] randomTarget(EntityLiving entity, int horizontal, int vertical) {
        var random = entity.getRNG();
        boolean found = false;
        int bestX = 0, bestY = 0, bestZ = 0;
        float best = -99999f;
        for (int i = 0; i < 10; i++) {
            int dx = random.nextInt(2 * horizontal + 1) - horizontal;
            int dy = random.nextInt(2 * vertical + 1) - vertical;
            int dz = random.nextInt(2 * horizontal + 1) - horizontal;
            int bx = (int) Math.floor(dx + entity.x()), by = (int) Math.floor(dy + entity.y()), bz = (int) Math.floor(dz + entity.z());
            float weight = entity.blockPathWeight(bx, by, bz);
            if (weight > best) {
                best = weight;
                bestX = dx; bestY = dy; bestZ = dz;
                found = true;
            }
        }
        return found ? new double[] {bestX + entity.x(), bestY + entity.y(), bestZ + entity.z()} : null;
    }

    /** EntityAIWatchClosest: 2% of checks, look at the player within range for 40-79 ticks. */
    public static final class WatchClosest extends EntityAI {
        private final EntityLiving entity;
        private final float range;
        private Player watched;
        private int lookTime;
        public WatchClosest(EntityLiving entity, float range) {
            this.entity = entity;
            this.range = range;
            mutexBits = 2;
        }
        @Override public boolean shouldExecute() {
            if (entity.getRNG().nextFloat() >= 0.02f) return false;
            Player player = entity.host() == null ? null : entity.host().player();
            if (player == null || player.dead) return false;
            if (entity.distanceSqTo(player.x, player.y, player.z) > range * range) return false;
            watched = player;
            return true;
        }
        @Override public boolean continueExecuting() {
            return watched != null && !watched.dead && entity.distanceSqTo(watched.x, watched.y, watched.z) <= range * range && lookTime > 0;
        }
        @Override public void startExecuting() { lookTime = 40 + entity.getRNG().nextInt(40); }
        @Override public void resetTask() { watched = null; }
        @Override public void updateTask() {
            entity.lookHelper().setLookPosition(watched.x, watched.y + watched.eyeHeight(), watched.z, 10f, 40f);
            lookTime--;
        }
    }

    /** EntityAILookIdle: 2% of checks, look around in a random direction for 20-39 ticks. */
    public static final class LookIdle extends EntityAI {
        private final EntityLiving entity;
        private double lookX, lookZ;
        private int idleTime;
        public LookIdle(EntityLiving entity) {
            this.entity = entity;
            mutexBits = 3;
        }
        @Override public boolean shouldExecute() { return entity.getRNG().nextFloat() < 0.02f; }
        @Override public boolean continueExecuting() { return idleTime >= 0; }
        @Override public void startExecuting() {
            double angle = Math.PI * 2 * entity.getRNG().nextDouble();
            lookX = Math.cos(angle);
            lookZ = Math.sin(angle);
            idleTime = 20 + entity.getRNG().nextInt(20);
        }
        @Override public void updateTask() {
            idleTime--;
            entity.lookHelper().setLookPosition(entity.x() + lookX, entity.y() + entity.eyeHeight(), entity.z() + lookZ, 10f, 40f);
        }
    }

    /**
     * EntityAIAttackOnCollide (without long memory): chase the target along
     * a path repathed every 4-10 ticks (longer when far), and hit it every
     * 20 ticks when within {@code (2 x width)^2 + target width}.
     */
    public static final class AttackOnCollide extends EntityAI {
        private final EntityLiving entity;
        private final double speed;
        private PathFinder.Path path;
        private int attackTick, delayCounter;
        private double targetX, targetY, targetZ;
        public AttackOnCollide(EntityLiving entity, double speed) {
            this.entity = entity;
            this.speed = speed;
            mutexBits = 3;
        }
        @Override public boolean shouldExecute() {
            Player target = entity.attackTarget();
            if (target == null || target.dead) return false;
            path = entity.navigator().pathToEntity(target.x, target.y, target.z);
            return path != null;
        }
        @Override public boolean continueExecuting() {
            Player target = entity.attackTarget();
            return target != null && !target.dead && !entity.navigator().noPath();
        }
        @Override public void startExecuting() {
            entity.navigator().setPath(path, speed);
            delayCounter = 0;
        }
        @Override public void resetTask() { entity.navigator().clearPath(); }
        @Override public void updateTask() {
            Player target = entity.attackTarget();
            if (target == null) return;
            entity.lookHelper().setLookPosition(target.x, target.y + target.eyeHeight(), target.z, 30f, 30f);
            double distance = entity.distanceSqTo(target.x, target.y, target.z);
            double reach = entity.width * 2f * entity.width * 2f + Player.WIDTH;
            delayCounter--;
            double moved = (target.x - targetX) * (target.x - targetX) + (target.y - targetY) * (target.y - targetY)
                + (target.z - targetZ) * (target.z - targetZ);
            if (entity.canSee(target) && delayCounter <= 0
                && (targetX == 0 && targetY == 0 && targetZ == 0 || moved >= 1.0 || entity.getRNG().nextFloat() < 0.05f)) {
                targetX = target.x; targetY = target.y; targetZ = target.z;
                delayCounter = 4 + entity.getRNG().nextInt(7);
                if (distance > 1024) delayCounter += 10;
                else if (distance > 256) delayCounter += 5;
                if (!entity.navigator().tryMoveTo(target.x, target.y, target.z, speed)) delayCounter += 15;
            }
            attackTick = Math.max(attackTick - 1, 0);
            if (distance <= reach && attackTick <= 0) {
                attackTick = 20;
                entity.attackEntityAsMob(target);
            }
        }
    }

    // ---- targeting -----------------------------------------------------------------------------

    /**
     * EntityAINearestAttackableTarget (players, checking sight): 1 check in
     * 10, target the player within the follow range (x 0.8 while sneaking)
     * who can be seen and isn't in Creative or Spectator; keep the target
     * while within range and not unseen for more than 60 ticks.
     */
    public static final class NearestAttackableTarget extends EntityAI {
        private final EntityLiving entity;
        private int unseenTicks;
        public NearestAttackableTarget(EntityLiving entity) {
            this.entity = entity;
            mutexBits = 1;
        }
        @Override public boolean shouldExecute() {
            if (entity.getRNG().nextInt(10) != 0) return false;
            Player player = entity.host() == null ? null : entity.host().player();
            if (!isSuitable(entity, player)) return false;
            double range = entity.followRange();
            if (player.sneaking) range *= 0.8;
            double dx = player.x - entity.x(), dz = player.z - entity.z(), dy = player.y - entity.y();
            if (Math.abs(dy) > 4 + range || Math.abs(dx) > range + 1 || Math.abs(dz) > range + 1) return false;
            return Math.sqrt(dx * dx + dy * dy + dz * dz) <= range;
        }
        @Override public void startExecuting() {
            entity.setAttackTarget(entity.host().player());
            unseenTicks = 0;
        }
        @Override public boolean continueExecuting() { return keepTarget(entity, this); }
        @Override public void resetTask() { entity.setAttackTarget(null); }
    }

    /** EntityAIHurtByTarget: whoever last hurt the mob (here only ever the player) becomes the target. */
    public static final class HurtByTarget extends EntityAI {
        private final EntityLiving entity;
        private int lastRevenge;
        private int unseenTicks;
        public HurtByTarget(EntityLiving entity) {
            this.entity = entity;
            mutexBits = 1;
        }
        @Override public boolean shouldExecute() {
            return entity.revengeTimer() != lastRevenge && entity.revengeTarget() != null
                && isSuitable(entity, entity.revengeTarget());
        }
        @Override public void startExecuting() {
            entity.setAttackTarget(entity.revengeTarget());
            lastRevenge = entity.revengeTimer();
            unseenTicks = 0;
        }
        @Override public boolean continueExecuting() { return keepTarget(entity, this); }
        @Override public void resetTask() { entity.setAttackTarget(null); }
    }

    /** EntityAITarget.isSuitableTarget with sight checked: alive, attackable and visible. */
    static boolean isSuitable(EntityLiving entity, Player player) {
        return player != null && !player.dead && !player.disableDamage && !player.noClip && entity.canSee(player);
    }

    /** EntityAITarget.continueExecuting. */
    private static boolean keepTarget(EntityLiving entity, EntityAI task) {
        Player target = entity.attackTarget();
        if (target == null || target.dead || target.disableDamage || target.noClip) return false;
        double range = entity.followRange();
        if (entity.distanceSqTo(target.x, target.y, target.z) > range * range) return false;
        int unseen = task instanceof NearestAttackableTarget n ? n.unseenTicks : ((HurtByTarget) task).unseenTicks;
        if (entity.canSee(target)) unseen = 0;
        else if (++unseen > 60) return false;
        if (task instanceof NearestAttackableTarget n) n.unseenTicks = unseen;
        else ((HurtByTarget) task).unseenTicks = unseen;
        return true;
    }
}
