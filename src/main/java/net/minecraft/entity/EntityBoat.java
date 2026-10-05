package net.minecraft.entity;

import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

/**
 * A rowing boat (1.8.9 EntityBoat, HYPOTHESIS: behaviour recalled from play,
 * constants tuned by eye; see DEV-038). A 1.5 x 0.6 box that floats with
 * half its height under water, sinks on land like a dropped block, and is
 * pushed along the rider's facing while they hold forward (backwards at half
 * strength). It turns to face where it is going, crawls on land, breaks lily
 * pads and snow layers it runs into, and smashes into 3 planks and 2 sticks
 * when it hits a wall fast. Hits wobble it; enough of them (or one in
 * Creative) knock it loose as a boat item.
 */
public final class EntityBoat extends MovingEntity {
    /** Fastest it goes on water, in blocks per tick (about 8 m/s). */
    public static final double MAX_SPEED = 0.4;
    /** Pushed this much per tick along the rider's facing while forward is held. */
    static final double PADDLE = 0.04;
    /** Faster than this into a wall and it breaks apart. */
    static final double CRASH_SPEED = 0.2;
    /** Damage that knocks it loose (each player hit adds 10, and it heals 1 a tick). */
    static final float BREAK_DAMAGE = 40f;

    /** Damage taken so far, which wears off. */
    public float damage;
    /** Ticks of wobble left after a hit, and which way it wobbles (+1 or -1). */
    public int timeSinceHit;
    public int forwardDirection = 1;
    /** Whether someone sits in it, and what they are pressing (-1 back .. 1 forward) and facing (degrees). */
    public boolean ridden;
    private float riderForward, riderYaw;
    /** Fraction of the box under water last tick (0-1), for the rider's view and tests. */
    public double submerged;

    public EntityBoat(World world, double x, double y, double z) {
        super(world, x, y, z);
        width = 1.5f;
        height = 0.6f;
    }

    @Override public String entityId() { return "Boat"; }
    @Override protected boolean canTriggerWalking() { return false; }
    @Override protected boolean isPushedByWater() { return false; }

    /** The rider's controls for the next tick: forward -1..1, and the way they look (vanilla yaw). */
    public void setRiderInput(float forward, float yaw) {
        riderForward = Math.max(-1f, Math.min(1f, forward));
        riderYaw = yaw;
    }

    /** Where a rider's feet sit (they sink into the hull). */
    public double riderY() { return y - 0.3; }

    public double horizontalSpeed() { return Math.sqrt(motionX * motionX + motionZ * motionZ); }

    @Override
    public void tick() {
        if (frozen()) return;
        prevX = x; prevY = y; prevZ = z;
        prevRotationYaw = rotationYaw;
        ticksExisted++;
        if (timeSinceHit > 0) timeSinceHit--;
        if (damage > 0) damage = Math.max(0f, damage - 1f);

        handleWaterMovement();   // the splash when it lands in water (it isn't carried by currents)
        submerged = waterFraction();
        boolean onWater = submerged > 0;
        // Floats half under: lifted while deeper than half its height, pulled down while higher.
        motionY += onWater ? (submerged - 0.5) * 0.08 : -0.04;

        if (ridden && riderForward != 0f) {
            double rad = Math.toRadians(riderYaw);
            double push = PADDLE * (riderForward > 0 ? riderForward : riderForward * 0.5f);
            motionX += -Math.sin(rad) * push;
            motionZ += Math.cos(rad) * push;
        }
        double speed = horizontalSpeed();
        double cap = onWater ? MAX_SPEED : MAX_SPEED * 0.25;
        if (speed > cap) {
            motionX *= cap / speed;
            motionZ *= cap / speed;
            speed = cap;
        }

        double before = speed;
        move(motionX, motionY, motionZ);
        breakSoftBlocks();
        if (collidedHorizontally && before > CRASH_SPEED) {
            smash();
            return;
        }

        if (onWater) {
            motionX *= ridden ? 0.97 : 0.95;
            motionZ *= ridden ? 0.97 : 0.95;
            motionY *= 0.9;
        } else {
            motionX *= onGround ? 0.5 : 0.98;
            motionZ *= onGround ? 0.5 : 0.98;
            motionY *= 0.98;
        }
        if (Math.abs(motionX) < 1e-4) motionX = 0;
        if (Math.abs(motionZ) < 1e-4) motionZ = 0;

        // Swings round to face the way it moves, at most 20 degrees a tick.
        if (horizontalSpeed() > 0.001) {
            float heading = (float) Math.toDegrees(Math.atan2(-motionX, motionZ));
            float turn = wrapDegrees(heading - rotationYaw);
            if (turn > 90f) turn -= 180f;      // reversing keeps the bow where it was
            else if (turn < -90f) turn += 180f;
            rotationYaw += Math.max(-20f, Math.min(20f, turn));
        }
    }

    /** How much of the box's height has water in it, sampled in five slices. */
    private double waterFraction() {
        AxisAlignedBB box = boundingBox();
        int slices = 5, wet = 0;
        double step = (box.maxY() - box.minY()) / slices;
        for (int i = 0; i < slices; i++) {
            double bottom = box.minY() + step * i;
            AxisAlignedBB slice = new AxisAlignedBB(box.minX(), bottom, box.minZ(), box.maxX(), bottom + step, box.maxZ());
            if (world.isMaterialInBox(slice, "water")) wet++;
        }
        return (double) wet / slices;
    }

    /** Lily pads and snow layers inside the box (grown 0.1 sideways) break and drop. */
    private void breakSoftBlocks() {
        if (host == null) return;
        AxisAlignedBB box = boundingBox().grow(0.1, 0, 0.1);
        for (int bx = floor(box.minX()); bx <= floor(box.maxX()); bx++)
            for (int by = Math.max(0, floor(box.minY())); by <= Math.min(World.HEIGHT - 1, floor(box.maxY())); by++)
                for (int bz = floor(box.minZ()); bz <= floor(box.maxZ()); bz++) {
                    String name = world.get(bx, by, bz).name().path();
                    if (name.equals("waterlily") || name.equals("snow_layer")) host.breakBlock(bx, by, bz, true);
                }
    }

    /** Crashed: gone, leaving 3 planks and 2 sticks. */
    private void smash() {
        remove();
        if (host == null || !host.gameRule("doEntityDrops")) return;
        dropItem("planks", 3);
        dropItem("stick", 2);
    }

    /**
     * Punched by the player: it wobbles and takes 10 damage, and past 40
     * (or at once in Creative) comes loose as a boat item (Creative drops none).
     * Returns whether it broke.
     */
    public boolean hitByPlayer(boolean creative) {
        if (isRemoved()) return false;
        forwardDirection = -forwardDirection;
        timeSinceHit = 10;
        damage += 10f;
        if (!creative && damage <= BREAK_DAMAGE) return false;
        remove();
        if (!creative && host != null && host.gameRule("doEntityDrops")) dropItem("boat", 1);
        return true;
    }

    @Override
    public boolean attackFromExplosion(float amount, String attacker, MovingEntity exploder) {
        forwardDirection = -forwardDirection;
        timeSinceHit = 10;
        damage += amount * 10f;
        if (damage <= BREAK_DAMAGE) return true;
        remove();
        if (host != null && host.gameRule("doEntityDrops")) dropItem("boat", 1);
        return true;
    }

    private void dropItem(String name, int count) {
        java.util.function.Function<String, net.minecraft.item.Item> items = world.itemLookup();
        net.minecraft.item.Item item = items == null ? null : items.apply(name);
        if (item == null || host == null) return;
        for (int i = 0; i < count; i++) {
            EntityItem drop = new EntityItem(world, x, y, z, new net.minecraft.item.ItemStack(item, 1));
            drop.pickupDelay = 10;
            host.spawn(drop);
        }
    }
}
