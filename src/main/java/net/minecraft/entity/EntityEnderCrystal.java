package net.minecraft.entity;

import net.minecraft.world.World;

/**
 * An end crystal (1.9 EntityEnderCrystal, HYPOTHESIS: recalled): 2 x 2,
 * spinning over a bedrock base (hidden for crystals placed by hand on the
 * exit portal), keeping a fire lit under itself in the End. Any hit but the
 * dragon's breaks it in an explosion of strength 6; it can point a beam at
 * a block (while the dragon is being brought back).
 */
public final class EntityEnderCrystal extends MovingEntity {
    /** EntityEnderCrystal.innerRotation: where its spin starts. */
    public int innerRotation;
    public boolean showBottom = true;
    /** The block its beam points at, or null. */
    public int[] beamTarget;

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
            if (world.inBounds(bx, by, bz) && world.get(bx, by, bz).isAir()) host.setBlock(bx, by, bz, "fire", 0);
        }
    }

    /** EntityEnderCrystal.attackEntityFrom: anything but the dragon breaks it, exploding unless an explosion did it. */
    public boolean hit(boolean explosion) {
        if (isRemoved()) return false;
        remove();
        if (host != null && !explosion) host.explode(this, x, y, z, 6f, false, true, null);
        // DragonFightManager.onCrystalDestroyed: the dragon hears of it.
        if (host != null)
            for (Entity entity : host.entities())
                if (entity instanceof net.minecraft.entity.boss.EntityDragon dragon) dragon.crystalDestroyed(this);
        return true;
    }

    @Override
    public boolean attackFromExplosion(float damage, String attacker, MovingEntity exploder) {
        if (exploder instanceof net.minecraft.entity.boss.EntityDragon) return false;
        return hit(true);
    }

    @Override
    public void writeNbt(net.minecraft.nbt.NbtTag.Compound tag) {
        super.writeNbt(tag);
        tag.put("ShowBottom", new net.minecraft.nbt.NbtTag.Byte((byte) (showBottom ? 1 : 0)));
        if (beamTarget != null) {
            net.minecraft.nbt.NbtTag.Compound beam = new net.minecraft.nbt.NbtTag.Compound();
            beam.put("X", new net.minecraft.nbt.NbtTag.Int(beamTarget[0]));
            beam.put("Y", new net.minecraft.nbt.NbtTag.Int(beamTarget[1]));
            beam.put("Z", new net.minecraft.nbt.NbtTag.Int(beamTarget[2]));
            tag.put("BeamTarget", beam);
        }
    }

    @Override
    public void readNbt(net.minecraft.nbt.NbtTag.Compound tag) {
        super.readNbt(tag);
        if (tag.get("ShowBottom") instanceof net.minecraft.nbt.NbtTag.Byte b) showBottom = b.value() != 0;
        if (tag.get("BeamTarget") instanceof net.minecraft.nbt.NbtTag.Compound beam
            && beam.get("X") instanceof net.minecraft.nbt.NbtTag.Int bx && beam.get("Y") instanceof net.minecraft.nbt.NbtTag.Int by
            && beam.get("Z") instanceof net.minecraft.nbt.NbtTag.Int bz)
            beamTarget = new int[]{bx.value(), by.value(), bz.value()};
    }
}
