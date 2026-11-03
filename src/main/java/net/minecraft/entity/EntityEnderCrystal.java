package net.minecraft.entity;

import net.minecraft.world.World;

/**
 * An ender crystal (1.8.9 EntityEnderCrystal, HYPOTHESIS: recalled): 2 x 2,
 * spinning over its bedrock plate, keeping a fire lit where it stands in the
 * End. Any damage breaks it in an explosion of strength 6, so crystals near
 * each other go off together.
 */
public final class EntityEnderCrystal extends MovingEntity {
    /** EntityEnderCrystal.innerRotation: where its spin starts. */
    public int innerRotation;

    public EntityEnderCrystal(World world, double x, double y, double z) {
        super(world, x, y, z);
        width = height = 2f;
        innerRotation = rand.nextInt(100000);
    }

    @Override public String entityId() { return "EnderCrystal"; }
    @Override protected boolean canTriggerWalking() { return false; }
    @Override public boolean isImmuneToFire() { return true; }

    @Override
    public void tick() {
        if (frozen()) return;
        prevX = x; prevY = y; prevZ = z;
        ticksExisted++;
        innerRotation++;
        if (world.isEnd() && host != null) {
            int bx = floor(x), by = floor(y), bz = floor(z);
            if (world.inBounds(bx, by, bz) && !world.get(bx, by, bz).name().path().equals("fire")) host.setBlock(bx, by, bz, "fire", 0);
        }
    }

    /** EntityEnderCrystal.attackEntityFrom: any hit breaks it with an explosion. */
    public boolean hit() {
        if (isRemoved()) return false;
        remove();
        if (host != null) host.explode(null, x, y, z, 6f, true, null);
        return true;
    }

    @Override
    public boolean attackFromExplosion(float damage, String attacker, MovingEntity exploder) {
        return hit();
    }
}
