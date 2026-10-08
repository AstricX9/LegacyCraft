package net.minecraft.entity;

import net.minecraft.block.BlockState;
import net.minecraft.client.entity.Player;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

/**
 * An arrow (1.8.9 EntityArrow, HYPOTHESIS: recalled). It flies with 0.05
 * gravity and 0.99 drag (0.6 in water), and on hitting a living thing does
 * ceil(speed x damage) (plus a random bonus up to half again when critical),
 * sets it alight if the arrow burns, knocks it back with Punch, and is used
 * up; a hit that does nothing bounces it back. On a block it sticks, shakes
 * for 7 ticks, and stays until its block changes or 1200 ticks pass. Arrows
 * the player shot (not with Infinity) can be picked up off the ground.
 */
public final class EntityArrow extends MovingEntity {
    public double damage = 2.0;
    public int knockbackStrength;
    public boolean critical;
    /** 0 can't be picked up, 1 can, 2 only in Creative (canBePickedUp). */
    public int pickup;
    public boolean shotByPlayer;
    /** The mob that shot it (skeletons), or null. */
    public EntityLiving shooter;
    public boolean inGround;
    public int arrowShake;
    private int ticksInGround, ticksInAir;
    private int tileX, tileY = -1, tileZ;
    private int inState;

    public EntityArrow(World world, double x, double y, double z) {
        super(world, x, y, z);
        width = height = 0.5f;
    }

    /** EntityArrow(world, shooter, target, velocity, inaccuracy): aimed from a mob's eyes at the player. */
    public static EntityArrow aimedAt(World world, EntityLiving shooter, Player target, float velocity, float inaccuracy, java.util.Random random) {
        EntityArrow arrow = new EntityArrow(world, shooter.x(), shooter.y() + shooter.eyeHeight() - 0.1, shooter.z());
        arrow.shooter = shooter;
        double dx = target.x - shooter.x();
        double dy = target.boundingBox().minY() + Player.HEIGHT / 3.0 - arrow.y;
        double dz = target.z - shooter.z();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal >= 1.0E-7) {
            float yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90f;
            float pitch = (float) -(Math.atan2(dy, horizontal) * 180.0 / Math.PI);
            arrow.setPositionAndRotation(shooter.x() + dx / horizontal, arrow.y, shooter.z() + dz / horizontal, yaw, pitch);
            arrow.setHeading(dx, dy + horizontal * 0.2f, dz, velocity, inaccuracy, random);
        }
        return arrow;
    }

    /** EntityArrow(world, player, velocity): shot from the player's eyes along their view. */
    public static EntityArrow shotBy(World world, Player player, float velocity, java.util.Random random) {
        double yaw = Math.toRadians(player.yaw), pitch = Math.toRadians(player.pitch);
        EntityArrow arrow = new EntityArrow(world, player.x - Math.cos(yaw) * 0.16, player.eyeY() - 0.1, player.z - Math.sin(yaw) * 0.16);
        arrow.shotByPlayer = true;
        arrow.pickup = 1;
        double mx = -Math.sin(yaw) * Math.cos(pitch), mz = Math.cos(yaw) * Math.cos(pitch), my = -Math.sin(pitch);
        arrow.setHeading(mx, my, mz, velocity * 1.5f, 1f, random);
        return arrow;
    }

    /** EntityArrow.setThrowableHeading: normalised, a little scatter, times the velocity. */
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
        ticksInGround = 0;
    }

    @Override public String entityId() { return "Arrow"; }
    @Override protected boolean canTriggerWalking() { return false; }
    @Override protected boolean isPushedByWater() { return false; }

    @Override
    public void tick() {
        if (frozen()) return;
        prevX = x; prevY = y; prevZ = z;
        prevRotationYaw = rotationYaw;
        prevRotationPitch = rotationPitch;
        ticksExisted++;
        if (fire > 0) fire--;
        if (arrowShake > 0) arrowShake--;
        if (inGround) {
            BlockState here = tileY >= 0 && tileY < World.HEIGHT ? world.getState(tileX, tileY, tileZ) : null;
            if (here != null && here.packed() == inState && !here.block().isAir()) {
                if (++ticksInGround >= 1200) remove();
            } else {
                inGround = false;
                motionX *= rand.nextFloat() * 0.2f;
                motionY *= rand.nextFloat() * 0.2f;
                motionZ *= rand.nextFloat() * 0.2f;
                ticksInGround = 0;
                ticksInAir = 0;
            }
            return;
        }
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
                if (living == shooter && ticksInAir < 5) continue;
                if (!living.boundingBox().intersects(sweep)) continue;
                double d = entryDistance(living.boundingBox().grow(0.3), speed);
                if (d >= 0 && d < nearest) { nearest = d; hitEntity = living; }
            }
            for (Player player : host.players()) {
                if (player.health <= 0 || player.disableDamage || shotByPlayer && ticksInAir < 5 && player == host.player()
                    || !player.boundingBox().intersects(sweep)) continue;
                double d = entryDistance(player.boundingBox().grow(0.3), speed);
                if (d >= 0 && d < nearest) { nearest = d; hitEntity = player; }
            }
        }
        if (hitEntity != null) {
            hitEntity(hitEntity, speed);
            if (isRemoved()) return;
        } else if (block != null) {
            // Stuck: pulled back 0.05 from where it met the block.
            double hx = x + motionX / speed * reach, hy = y + motionY / speed * reach, hz = z + motionZ / speed * reach;
            tileX = block.x(); tileY = block.y(); tileZ = block.z();
            inState = world.getState(tileX, tileY, tileZ).packed();
            motionX = hx - x;
            motionY = hy - y;
            motionZ = hz - z;
            double length = Math.sqrt(motionX * motionX + motionY * motionY + motionZ * motionZ);
            if (length > 0) setPosition(x + motionX - motionX / length * 0.05, y + motionY - motionY / length * 0.05,
                z + motionZ - motionZ / length * 0.05);
            playSound("random.bowhit", 1f, 1.2f / (rand.nextFloat() * 0.2f + 0.9f));
            inGround = true;
            arrowShake = 7;
            critical = false;
            return;
        }
        if (critical && host != null)
            for (int i = 0; i < 4; i++)
                host.particle("crit", x + motionX * i / 4.0, y + motionY * i / 4.0, z + motionZ * i / 4.0, -motionX, -motionY + 0.2, -motionZ);
        setPosition(x + motionX, y + motionY, z + motionZ);
        float horizontal = (float) Math.sqrt(motionX * motionX + motionZ * motionZ);
        float targetYaw = (float) Math.toDegrees(Math.atan2(motionX, motionZ));
        float targetPitch = (float) Math.toDegrees(Math.atan2(motionY, horizontal));
        while (targetPitch - prevRotationPitch < -180f) prevRotationPitch -= 360f;
        while (targetPitch - prevRotationPitch >= 180f) prevRotationPitch += 360f;
        while (targetYaw - prevRotationYaw < -180f) prevRotationYaw -= 360f;
        while (targetYaw - prevRotationYaw >= 180f) prevRotationYaw += 360f;
        rotationPitch = prevRotationPitch + (targetPitch - prevRotationPitch) * 0.2f;
        rotationYaw = prevRotationYaw + (targetYaw - prevRotationYaw) * 0.2f;
        float drag = 0.99f;
        if (handleWaterMovement()) {
            for (int i = 0; i < 4 && host != null; i++)
                host.particle("bubble", x - motionX * 0.25, y - motionY * 0.25, z - motionZ * 0.25, motionX, motionY, motionZ);
            drag = 0.6f;
            fire = 0;
        }
        motionX *= drag;
        motionY *= drag;
        motionZ *= drag;
        motionY -= 0.05f;
        if (y < -64) remove();
    }

    private void hitEntity(Object target, double speed) {
        int amount = (int) Math.ceil(speed * damage);
        if (critical) amount += rand.nextInt(amount / 2 + 2);
        boolean hurt;
        Player thrower = shotByPlayer && host != null ? host.player() : null;
        String name = shooter != null ? shooter.displayName() : null;
        if (target instanceof Player player) {
            double fx = shooter != null ? shooter.x() : x - motionX, fz = shooter != null ? shooter.z() : z - motionZ;
            if (isBurning()) player.setFire(5);
            hurt = player.attackBy(DamageSource.ARROW, amount, fx, fz, name);
        } else {
            EntityLiving living = (EntityLiving) target;
            if (isBurning()) living.setFire(5);
            hurt = living.attackEntityFrom(DamageSource.ARROW, amount, thrower);
            if (hurt && knockbackStrength > 0) {
                double horizontal = Math.sqrt(motionX * motionX + motionZ * motionZ);
                if (horizontal > 0)
                    living.addVelocity(motionX * knockbackStrength * 0.6 / horizontal, 0.1, motionZ * knockbackStrength * 0.6 / horizontal);
            }
        }
        if (hurt) {
            playSound("random.bowhit", 1f, 1.2f / (rand.nextFloat() * 0.2f + 0.9f));
            remove();
        } else {
            motionX *= -0.1;
            motionY *= -0.1;
            motionZ *= -0.1;
            rotationYaw += 180f;
            prevRotationYaw += 180f;
            ticksInAir = 0;
        }
    }

    private double entryDistance(AxisAlignedBB box, double speed) {
        if (x >= box.minX() && x <= box.maxX() && y >= box.minY() && y <= box.maxY() && z >= box.minZ() && z <= box.maxZ()) return 0;
        AxisAlignedBB.RayHit hit = box.rayEntry(x, y, z, motionX / speed, motionY / speed, motionZ / speed);
        return hit == null || hit.distance() > speed ? -1 : hit.distance();
    }

    private void playSound(String event, float volume, float pitch) {
        if (host != null) host.playSound(event, x, y, z, volume, pitch);
    }

    /** EntityArrow.onCollideWithPlayer: whether the player may pick this arrow up now. */
    public boolean canPickUp(boolean creative) {
        return inGround && arrowShake <= 0 && (pickup == 1 || pickup == 2 && creative);
    }

    @Override
    public void writeNbt(net.minecraft.nbt.NbtTag.Compound tag) {
        super.writeNbt(tag);
        tag.put("xTile", new net.minecraft.nbt.NbtTag.Short((short) tileX));
        tag.put("yTile", new net.minecraft.nbt.NbtTag.Short((short) tileY));
        tag.put("zTile", new net.minecraft.nbt.NbtTag.Short((short) tileZ));
        tag.put("life", new net.minecraft.nbt.NbtTag.Short((short) ticksInGround));
        tag.put("inGround", new net.minecraft.nbt.NbtTag.Byte((byte) (inGround ? 1 : 0)));
        tag.put("pickup", new net.minecraft.nbt.NbtTag.Byte((byte) pickup));
        tag.put("damage", new net.minecraft.nbt.NbtTag.Double(damage));
        tag.put("inState", new net.minecraft.nbt.NbtTag.Int(inState));
    }

    @Override
    public void readNbt(net.minecraft.nbt.NbtTag.Compound tag) {
        super.readNbt(tag);
        if (tag.get("xTile") instanceof net.minecraft.nbt.NbtTag.Short v) tileX = v.value();
        if (tag.get("yTile") instanceof net.minecraft.nbt.NbtTag.Short v) tileY = v.value();
        if (tag.get("zTile") instanceof net.minecraft.nbt.NbtTag.Short v) tileZ = v.value();
        if (tag.get("life") instanceof net.minecraft.nbt.NbtTag.Short v) ticksInGround = v.value();
        if (tag.get("inGround") instanceof net.minecraft.nbt.NbtTag.Byte v) inGround = v.value() != 0;
        if (tag.get("pickup") instanceof net.minecraft.nbt.NbtTag.Byte v) pickup = v.value();
        if (tag.get("damage") instanceof net.minecraft.nbt.NbtTag.Double v) damage = v.value();
        if (tag.get("inState") instanceof net.minecraft.nbt.NbtTag.Int v) inState = v.value();
    }
}
