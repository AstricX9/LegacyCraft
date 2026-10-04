package net.minecraft.entity;

import net.minecraft.client.entity.Player;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

/**
 * Something thrown (1.8.9 EntityThrowable, HYPOTHESIS: recalled): it leaves
 * from just below the thrower's eyes along their view (tilted by
 * {@link #pitchOffset}), at {@link #velocity} blocks a tick with a little
 * scatter, falls by {@link #gravity} a tick with 0.99 drag (0.8 in water,
 * bubbling), ignores its thrower for the first 5 ticks and stops at the
 * first block or living thing it meets (boxes grown by 0.3).
 */
public abstract class EntityThrowable extends MovingEntity {
    /** Whether the player threw it (ignored for the first ticks, blamed for hits). */
    public boolean thrownByPlayer;
    protected int ticksInAir;

    protected EntityThrowable(World world, double x, double y, double z) {
        super(world, x, y, z);
        width = height = 0.25f;
    }

    protected float gravity() { return 0.03f; }
    protected float velocity() { return 1.5f; }
    protected float pitchOffset() { return 0f; }

    /** What happens when it hits {@code hit} (a mob, the player, or null for a block); it should remove itself. */
    protected abstract void onImpact(Object hit);

    /** The item it shows as in flight (RenderSnowball), by item name. */
    public abstract String itemName();

    /** EntityThrowable(world, thrower): placed at the player's eyes and aimed along their view. */
    public <T extends EntityThrowable> T aimFrom(Player player, java.util.Random random) {
        double yaw = Math.toRadians(player.yaw), pitch = Math.toRadians(player.pitch);
        setPosition(player.x - Math.cos(yaw) * 0.16, player.eyeY() - 0.1, player.z - Math.sin(yaw) * 0.16);
        prevX = x; prevY = y; prevZ = z;
        thrownByPlayer = true;
        rotationYaw = player.yaw;
        rotationPitch = player.pitch;
        double mx = -Math.sin(yaw) * Math.cos(pitch) * 0.4;
        double mz = Math.cos(yaw) * Math.cos(pitch) * 0.4;
        double my = -Math.sin(pitch + Math.toRadians(pitchOffset())) * 0.4;
        setHeading(mx, my, mz, velocity(), 1f, random);
        @SuppressWarnings("unchecked") T self = (T) this;
        return self;
    }

    /** EntityThrowable.setThrowableHeading: normalised, a little scatter, times the velocity. */
    public void setHeading(double mx, double my, double mz, float velocity, float inaccuracy, java.util.Random random) {
        double length = Math.sqrt(mx * mx + my * my + mz * mz);
        mx /= length; my /= length; mz /= length;
        mx += random.nextGaussian() * (random.nextBoolean() ? -1 : 1) * 0.0075 * inaccuracy;
        my += random.nextGaussian() * (random.nextBoolean() ? -1 : 1) * 0.0075 * inaccuracy;
        mz += random.nextGaussian() * (random.nextBoolean() ? -1 : 1) * 0.0075 * inaccuracy;
        motionX = mx * velocity;
        motionY = my * velocity;
        motionZ = mz * velocity;
        float horizontal = (float) Math.sqrt(motionX * motionX + motionZ * motionZ);
        prevRotationYaw = rotationYaw = (float) Math.toDegrees(Math.atan2(motionX, motionZ));
        prevRotationPitch = rotationPitch = (float) Math.toDegrees(Math.atan2(motionY, horizontal));
    }

    @Override protected boolean canTriggerWalking() { return false; }

    @Override
    public void tick() {
        if (frozen()) return;
        prevX = x; prevY = y; prevZ = z;
        prevRotationYaw = rotationYaw;
        prevRotationPitch = rotationPitch;
        ticksExisted++;
        ticksInAir++;
        // The first block along this tick's motion...
        double speed = Math.sqrt(motionX * motionX + motionY * motionY + motionZ * motionZ);
        double reach = speed;
        boolean hitBlock = false;
        if (speed > 1.0E-7) {
            WorldRenderer.Hit block = WorldRenderer.raycast(world, x, y, z, motionX / speed, motionY / speed, motionZ / speed, speed);
            if (block != null) {
                AxisAlignedBB.RayHit entry = block.box().offset(block.x(), block.y(), block.z())
                    .rayEntry(x, y, z, motionX / speed, motionY / speed, motionZ / speed);
                reach = entry == null ? 0 : entry.distance();
                hitBlock = true;
            }
        }
        // ...unless a living thing is nearer (boxes grown by 0.3).
        Object hitEntity = null;
        double nearest = reach;
        if (speed > 1.0E-7) {
            AxisAlignedBB sweep = boundingBox().addCoord(motionX, motionY, motionZ).grow(1.0);
            if (host != null) {
                for (Entity entity : host.entities()) {
                    if (!(entity instanceof EntityLiving living) || !living.isAlive()) continue;
                    AxisAlignedBB box = living.boundingBox();
                    if (!box.intersects(sweep)) continue;
                    double d = entryDistance(box.grow(0.3), speed);
                    if (d >= 0 && d < nearest) { nearest = d; hitEntity = living; }
                }
                Player player = host.player();
                if (player != null && player.health > 0 && (!thrownByPlayer || ticksInAir >= 5) && player.boundingBox().intersects(sweep)) {
                    double d = entryDistance(player.boundingBox().grow(0.3), speed);
                    if (d >= 0 && d < nearest) { nearest = d; hitEntity = player; }
                }
            }
        }
        if (hitEntity != null || hitBlock) {
            setPosition(x + motionX / speed * nearest, y + motionY / speed * nearest, z + motionZ / speed * nearest);
            onImpact(hitEntity);
            return;
        }
        setPosition(x + motionX, y + motionY, z + motionZ);
        float horizontal = (float) Math.sqrt(motionX * motionX + motionZ * motionZ);
        rotationYaw = (float) Math.toDegrees(Math.atan2(motionX, motionZ));
        rotationPitch = (float) Math.toDegrees(Math.atan2(motionY, horizontal));
        float drag = 0.99f;
        if (handleWaterMovement()) {
            for (int i = 0; i < 4 && host != null; i++)
                host.particle("bubble", x - motionX * 0.25, y - motionY * 0.25, z - motionZ * 0.25, motionX, motionY, motionZ);
            drag = 0.8f;
        }
        motionX *= drag;
        motionY *= drag;
        motionZ *= drag;
        motionY -= gravity();
        firstUpdate = false;
        if (y < -64) remove();
    }

    @Override protected boolean isPushedByWater() { return false; }

    /** How far along this tick's motion the ray from the centre enters {@code box}, or -1; inside counts as 0. */
    private double entryDistance(AxisAlignedBB box, double speed) {
        if (x >= box.minX() && x <= box.maxX() && y >= box.minY() && y <= box.maxY() && z >= box.minZ() && z <= box.maxZ()) return 0;
        AxisAlignedBB.RayHit hit = box.rayEntry(x, y, z, motionX / speed, motionY / speed, motionZ / speed);
        return hit == null || hit.distance() > speed ? -1 : hit.distance();
    }

    /** The player who threw it, or null. */
    protected Player thrower() { return thrownByPlayer && host != null ? host.player() : null; }

    /** EntitySnowball/EntityEgg: a thrown hit doing {@code damage} (0 still flinches and knocks back). */
    protected void hitWith(Object target, float damage) {
        if (target instanceof EntityLiving living) living.attackEntityFrom(DamageSource.THROWN, damage, thrower());
        else if (target instanceof Player player) player.attack(DamageSource.THROWN, damage);
    }
}
