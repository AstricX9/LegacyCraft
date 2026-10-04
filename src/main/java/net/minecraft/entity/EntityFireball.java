package net.minecraft.entity;

import net.minecraft.client.entity.Player;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

/**
 * A fireball (1.8.9 EntityFireball, HYPOTHESIS: recalled): no gravity; it
 * gains a fixed push (0.1 a tick along its aim, set once with a little
 * scatter) while losing 5% of its speed each tick (20% in water), trails
 * smoke, ignores its shooter for 25 ticks and stops at the first block or
 * living thing in its way. It vanishes if its shooter dies.
 */
public abstract class EntityFireball extends MovingEntity {
    public double accelerationX, accelerationY, accelerationZ;
    /** The mob that shot it, or null after the player batted it back (then {@link #deflectedByPlayer}). */
    public EntityLiving shooter;
    public boolean deflectedByPlayer;
    protected int ticksInAir;

    protected EntityFireball(World world, double x, double y, double z, float size) {
        super(world, x, y, z);
        width = height = size;
    }

    /** EntityFireball(world, shooter, accelX, accelY, accelZ): aimed with gaussian scatter 0.4, normalised to 0.1. */
    protected void aim(EntityLiving shooter, double ax, double ay, double az, java.util.Random random) {
        this.shooter = shooter;
        ax += random.nextGaussian() * 0.4;
        ay += random.nextGaussian() * 0.4;
        az += random.nextGaussian() * 0.4;
        double length = Math.sqrt(ax * ax + ay * ay + az * az);
        accelerationX = ax / length * 0.1;
        accelerationY = ay / length * 0.1;
        accelerationZ = az / length * 0.1;
    }

    @Override protected boolean canTriggerWalking() { return false; }
    @Override protected boolean isPushedByWater() { return false; }
    @Override public boolean isImmuneToFire() { return true; }

    /** Whatever it hit: a mob, the player, or null for a block {@code (bx, by, bz)} entered through {@code face}. */
    protected abstract void onImpact(Object hit, WorldRenderer.Hit block);

    /** The item it shows as (fire charges). */
    public String itemName() { return "fire_charge"; }
    /** The render scale (RenderFireball: 2 for ghasts', 0.5 for blazes'). */
    public abstract float renderScale();

    @Override
    public void tick() {
        if (frozen()) return;
        if (shooter != null && shooter.isRemoved()) {
            remove();
            return;
        }
        prevX = x; prevY = y; prevZ = z;
        prevRotationYaw = rotationYaw;
        prevRotationPitch = rotationPitch;
        ticksExisted++;
        ticksInAir++;
        double speed = Math.sqrt(motionX * motionX + motionY * motionY + motionZ * motionZ);
        double reach = speed;
        WorldRenderer.Hit block = null;
        if (speed > 1.0E-7) {
            block = WorldRenderer.raycast(world, x, y, z, motionX / speed, motionY / speed, motionZ / speed, speed);
            if (block != null) {
                AxisAlignedBB.RayHit entry = block.box().offset(block.x(), block.y(), block.z())
                    .rayEntry(x, y, z, motionX / speed, motionY / speed, motionZ / speed);
                reach = entry == null ? 0 : entry.distance();
            }
        }
        Object hitEntity = null;
        double nearest = reach;
        if (speed > 1.0E-7 && host != null) {
            AxisAlignedBB sweep = boundingBox().addCoord(motionX, motionY, motionZ).grow(1.0);
            for (Entity entity : host.entities()) {
                if (!(entity instanceof EntityLiving living) || !living.isAlive()) continue;
                if (living == shooter && ticksInAir < 25) continue;
                if (!living.boundingBox().intersects(sweep)) continue;
                double d = entryDistance(living.boundingBox().grow(0.3), speed);
                if (d >= 0 && d < nearest) { nearest = d; hitEntity = living; }
            }
            Player player = host.player();
            if (player != null && player.health > 0 && !player.noClip && (!deflectedByPlayer || ticksInAir >= 25)
                && player.boundingBox().intersects(sweep)) {
                double d = entryDistance(player.boundingBox().grow(0.3), speed);
                if (d >= 0 && d < nearest) { nearest = d; hitEntity = player; }
            }
        }
        if (hitEntity != null || block != null) {
            if (speed > 0) setPosition(x + motionX / speed * nearest, y + motionY / speed * nearest, z + motionZ / speed * nearest);
            onImpact(hitEntity, hitEntity != null ? null : block);
            if (isRemoved()) return;
        }
        setPosition(x + motionX, y + motionY, z + motionZ);
        float horizontal = (float) Math.sqrt(motionX * motionX + motionZ * motionZ);
        rotationYaw = (float) Math.toDegrees(Math.atan2(motionZ, motionX)) + 90f;
        rotationPitch = (float) Math.toDegrees(Math.atan2(horizontal, motionY)) - 90f;
        float factor = motionFactor();
        if (handleWaterMovement()) {
            for (int i = 0; i < 4 && host != null; i++)
                host.particle("bubble", x - motionX * 0.25, y - motionY * 0.25, z - motionZ * 0.25, motionX, motionY, motionZ);
            factor = 0.8f;
        }
        motionX += accelerationX;
        motionY += accelerationY;
        motionZ += accelerationZ;
        motionX *= factor;
        motionY *= factor;
        motionZ *= factor;
        if (host != null) host.particle("smoke", x, y + 0.5, z, 0, 0, 0);
        if (ticksExisted > 1200 || y < -64) remove();
    }

    private double entryDistance(AxisAlignedBB box, double speed) {
        if (x >= box.minX() && x <= box.maxX() && y >= box.minY() && y <= box.maxY() && z >= box.minZ() && z <= box.maxZ()) return 0;
        AxisAlignedBB.RayHit hit = box.rayEntry(x, y, z, motionX / speed, motionY / speed, motionZ / speed);
        return hit == null || hit.distance() > speed ? -1 : hit.distance();
    }

    /** EntityFireball.getMotionFactor: the share of its speed kept each tick. */
    protected float motionFactor() { return 0.95f; }

    /** Whether a hit can bat it back (EntityFireball.canBeCollidedWith; small fireballs can't). */
    public boolean deflectable() { return true; }

    /** EntityFireball.attackEntityFrom by the player: it flies off along their view. */
    public void deflect(Player player) {
        double lx = player.dirX(), ly = player.dirY(), lz = player.dirZ();
        motionX = lx;
        motionY = ly;
        motionZ = lz;
        accelerationX = motionX * 0.1;
        accelerationY = motionY * 0.1;
        accelerationZ = motionZ * 0.1;
        shooter = null;
        deflectedByPlayer = true;
        ticksInAir = 0;
    }

    /** The fireball's damage to what it hit; a ghast hit by its own deflected fireball dies (Return to Sender). */
    protected void damage(Object target, float amount) {
        String name = shooter != null ? shooter.displayName() : null;
        if (target instanceof Player player) {
            double fx = shooter != null ? shooter.x() : x - motionX, fz = shooter != null ? shooter.z() : z - motionZ;
            player.attackBy(DamageSource.FIREBALL, amount, fx, fz, name);
        } else if (target instanceof EntityLiving living) {
            Player thrower = deflectedByPlayer && host != null ? host.player() : null;
            if (living.entityId().equals("Ghast") && thrower != null) {
                if (living.attackEntityFrom(DamageSource.FIREBALL, 1000f, thrower) && host != null) host.achievement("ghast");
            } else {
                living.attackEntityFrom(DamageSource.FIREBALL, amount, thrower);
            }
        }
    }

    @Override
    public void writeNbt(net.minecraft.nbt.NbtTag.Compound tag) {
        super.writeNbt(tag);
        tag.put("direction", doubles(motionX, motionY, motionZ));
        tag.put("power", doubles(accelerationX, accelerationY, accelerationZ));
    }

    @Override
    public void readNbt(net.minecraft.nbt.NbtTag.Compound tag) {
        super.readNbt(tag);
        double[] power = doubles(tag, "power");
        if (power != null) {
            accelerationX = power[0];
            accelerationY = power[1];
            accelerationZ = power[2];
        }
        double[] direction = doubles(tag, "direction");
        if (direction != null) {
            motionX = direction[0];
            motionY = direction[1];
            motionZ = direction[2];
        }
    }
}
