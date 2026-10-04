package net.minecraft.entity;

import net.minecraft.world.World;

/**
 * Lit TNT (1.8.9 EntityTNTPrimed, HYPOTHESIS: recalled): a 0.98 block that
 * hops up 0.2 and a little sideways when lit, falls with 0.04 gravity and
 * 0.98 drag (0.7 sideways and bouncing back up half as fast on the ground),
 * smokes, and after its fuse (80 ticks, or 10-29 when another explosion lit
 * it) explodes with strength 4 at a sixteenth of its height.
 */
public final class EntityTNTPrimed extends MovingEntity {
    public int fuse = 80;
    /** Who lit it (the death message's "was blown up by"), or null. */
    public String placedBy;

    public EntityTNTPrimed(World world, double x, double y, double z, String placedBy) {
        super(world, x, y, z);
        width = height = 0.98f;
        float angle = (float) (Math.random() * Math.PI * 2.0);
        motionX = -Math.sin(angle) * 0.02;
        motionY = 0.2;
        motionZ = -Math.cos(angle) * 0.02;
        this.placedBy = placedBy;
    }

    @Override public String entityId() { return "PrimedTnt"; }
    @Override protected boolean canTriggerWalking() { return false; }
    /** Pushed by explosions but never hurt by them. */
    @Override public boolean isImmuneToExplosions() { return false; }

    @Override
    public void tick() {
        if (frozen()) return;
        prevX = x; prevY = y; prevZ = z;
        ticksExisted++;
        motionY -= 0.04;
        move(motionX, motionY, motionZ);
        motionX *= 0.98;
        motionY *= 0.98;
        motionZ *= 0.98;
        if (onGround) {
            motionX *= 0.7;
            motionZ *= 0.7;
            motionY *= -0.5;
        }
        if (fuse-- <= 0) {
            remove();
            if (host != null) host.explode(this, x, y + height / 16f, z, 4f, true, placedBy);
        } else {
            handleWaterMovement();
            if (host != null) host.particle("smoke", x, y + 0.5, z, 0, 0, 0);
        }
        firstUpdate = false;
    }

    /** Explosions only push TNT. */
    @Override
    public boolean attackFromExplosion(float damage, String attacker, MovingEntity exploder) { return false; }

    @Override
    public void writeNbt(net.minecraft.nbt.NbtTag.Compound tag) {
        super.writeNbt(tag);
        tag.put("Fuse", new net.minecraft.nbt.NbtTag.Byte((byte) fuse));
    }

    @Override
    public void readNbt(net.minecraft.nbt.NbtTag.Compound tag) {
        super.readNbt(tag);
        if (tag.get("Fuse") instanceof net.minecraft.nbt.NbtTag.Byte f) fuse = f.value();
    }
}
