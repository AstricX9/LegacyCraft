package net.minecraft.entity;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Liquids;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The parts of 1.8.9's Entity that mobs, primed TNT and experience orbs
 * share (HYPOTHESIS: recalled): a box {@link #width} wide and {@link #height}
 * tall centred on the feet, motion in blocks per tick, collision with block
 * boxes one axis at a time (Y, X, Z) with an optional step up, falling,
 * water (with its current) and lava, and burning.
 */
public abstract class MovingEntity extends Entity {
    protected static final Random RANDOM = new Random();
    protected final Random rand = new Random();

    public double prevX, prevY, prevZ;
    public double motionX, motionY, motionZ;
    /** Vanilla rotation: yaw 0 faces south (+z), 90 west; pitch down is positive. */
    public float rotationYaw, rotationPitch, prevRotationYaw, prevRotationPitch;
    public float width = 0.6f, height = 1.8f;
    public float stepHeight;
    public boolean onGround, collidedHorizontally, collidedVertically, isAirBorne;
    public float fallDistance;
    public int ticksExisted;
    /** Ticks left burning (Entity.fire); negative is a short grace after leaving fire. */
    public int fire;
    protected boolean inWater, firstUpdate = true;
    /** Distance walked for footsteps (distanceWalkedModified) and when the next step sounds. */
    protected float distanceWalkedModified;
    private int nextStepDistance = 1;
    protected EntityHost host;

    protected MovingEntity(World world, double x, double y, double z) {
        super(world, x, y, z);
        prevX = x; prevY = y; prevZ = z;
    }

    public void setHost(EntityHost host) { this.host = host; }

    /** Entities only update where the world is loaded (World.updateEntityWithOptionalForce's area check). */
    protected boolean frozen() {
        return host == null || world.generatedChunk(floor(x) >> 4, floor(z) >> 4) == null;
    }
    public EntityHost host() { return host; }

    public AxisAlignedBB boundingBox() {
        double half = width / 2.0, bottom = y - yOffset();
        return new AxisAlignedBB(x - half, bottom, z - half, x + half, bottom + height, z + half);
    }

    /** How far the position sits above the box's bottom (Entity.yOffset: half the height for orbs). */
    protected double yOffset() { return 0; }

    public void setPositionAndRotation(double x, double y, double z, float yaw, float pitch) {
        setPosition(x, y, z);
        prevX = x; prevY = y; prevZ = z;
        rotationYaw = prevRotationYaw = yaw;
        rotationPitch = prevRotationPitch = pitch;
    }

    public double renderX(float partialTicks) { return prevX + (x - prevX) * partialTicks; }
    public double renderY(float partialTicks) { return prevY + (y - prevY) * partialTicks; }
    public double renderZ(float partialTicks) { return prevZ + (z - prevZ) * partialTicks; }

    public float eyeHeight() { return height * 0.85f; }

    public double distanceSqTo(double px, double py, double pz) {
        double dx = x - px, dy = y - py, dz = z - pz;
        return dx * dx + dy * dy + dz * dz;
    }

    public boolean isBurning() { return fire > 0; }
    public boolean isInWater() { return inWater; }
    /** Immune to fire and lava damage (none of the mobs here are). */
    public boolean isImmuneToFire() { return false; }

    public void addVelocity(double dx, double dy, double dz) {
        motionX += dx;
        motionY += dy;
        motionZ += dz;
        isAirBorne = true;
    }

    /** Called by explosions (Entity.attackEntityFrom with an explosion source); false if it can't be hurt. */
    public boolean attackFromExplosion(float damage, String attacker, MovingEntity exploder) { return false; }

    /** Whether explosions ignore it (primed TNT is pushed but not hurt). */
    public boolean isImmuneToExplosions() { return false; }

    // ---- collision -----------------------------------------------------------------------------

    protected static int floor(double value) { return (int) Math.floor(value); }

    protected List<AxisAlignedBB> collisionBoxes(AxisAlignedBB area) {
        List<AxisAlignedBB> result = new ArrayList<>();
        int minX = floor(area.minX()), maxX = floor(area.maxX());
        int minY = Math.max(0, floor(area.minY()) - 1), maxY = Math.min(World.HEIGHT - 1, floor(area.maxY()));
        int minZ = floor(area.minZ()), maxZ = floor(area.maxZ());
        for (int by = minY; by <= maxY; by++)
            for (int bz = minZ; bz <= maxZ; bz++)
                for (int bx = minX; bx <= maxX; bx++) {
                    if (world.generatedChunk(bx >> 4, bz >> 4) == null) {
                        result.add(new AxisAlignedBB(bx, by, bz, bx + 1, by + 1, bz + 1));
                        continue;
                    }
                    for (AxisAlignedBB box : Block.collisionBoxesAt(world, bx, by, bz))
                        if (box.intersects(area)) result.add(box);
                }
        // World.getCollidingBoundingBoxes: boats are solid to every other entity.
        if (host != null)
            for (Entity entity : host.entities())
                if (entity != this && entity instanceof EntityBoat boat && !boat.isRemoved()) {
                    AxisAlignedBB box = boat.boundingBox();
                    if (box.intersects(area)) result.add(box);
                }
        return result;
    }

    /** No block box inside this one (World.getCollidingBoundingBoxes(...).isEmpty()). */
    public boolean isFreeOfBlocks(AxisAlignedBB area) {
        for (AxisAlignedBB box : collisionBoxes(area)) if (box.intersects(area)) return false;
        return true;
    }

    /**
     * Entity.moveEntity: clips the move against block boxes (Y, X, Z),
     * stepping up ledges up to {@link #stepHeight} when a horizontal move is
     * blocked on the ground; then the landing, footsteps and the blocks
     * touched.
     */
    public void move(double dx, double dy, double dz) {
        double startX = x, startZ = z;
        double wantedX = dx, wantedY = dy, wantedZ = dz;
        AxisAlignedBB start = boundingBox();
        List<AxisAlignedBB> boxes = collisionBoxes(start.addCoord(dx, dy, dz));
        AxisAlignedBB box = start;
        for (AxisAlignedBB block : boxes) dy = block.clipY(box, dy);
        box = box.offset(0, dy, 0);
        for (AxisAlignedBB block : boxes) dx = block.clipX(box, dx);
        box = box.offset(dx, 0, 0);
        for (AxisAlignedBB block : boxes) dz = block.clipZ(box, dz);
        box = box.offset(0, 0, dz);
        boolean landed = onGround || wantedY != dy && wantedY < 0;
        if (stepHeight > 0 && landed && (wantedX != dx || wantedZ != dz)) {
            double stepX = wantedX, stepY = stepHeight, stepZ = wantedZ;
            List<AxisAlignedBB> stepBoxes = collisionBoxes(start.addCoord(wantedX, stepHeight, wantedZ));
            AxisAlignedBB stepped = start;
            for (AxisAlignedBB block : stepBoxes) stepY = block.clipY(stepped, stepY);
            stepped = stepped.offset(0, stepY, 0);
            for (AxisAlignedBB block : stepBoxes) stepX = block.clipX(stepped, stepX);
            stepped = stepped.offset(stepX, 0, 0);
            for (AxisAlignedBB block : stepBoxes) stepZ = block.clipZ(stepped, stepZ);
            stepped = stepped.offset(0, 0, stepZ);
            double down = -stepY;
            for (AxisAlignedBB block : stepBoxes) down = block.clipY(stepped, down);
            stepped = stepped.offset(0, down, 0);
            if (stepX * stepX + stepZ * stepZ > dx * dx + dz * dz) {
                box = stepped;
                dx = stepX; dz = stepZ; dy = stepY + down;
            }
        }
        setPosition((box.minX() + box.maxX()) / 2, box.minY() + yOffset(), (box.minZ() + box.maxZ()) / 2);
        collidedHorizontally = wantedX != dx || wantedZ != dz;
        collidedVertically = wantedY != dy;
        onGround = collidedVertically && wantedY < 0;
        updateFallState(dy, onGround);
        if (wantedX != dx) motionX = 0;
        if (wantedZ != dz) motionZ = 0;
        if (wantedY != dy) motionY = 0;
        if (canTriggerWalking() && onGround) {
            double mx = x - startX, mz = z - startZ;
            distanceWalkedModified += (float) (Math.sqrt(mx * mx + mz * mz) * 0.6);
            int bx = floor(x), by = floor(y - 0.2), bz = floor(z);
            Block below = by >= 0 ? world.get(bx, by, bz) : null;
            if (below != null && !below.isAir() && distanceWalkedModified > nextStepDistance) {
                nextStepDistance = (int) distanceWalkedModified + 1;
                playStepSound(bx, by, bz, below);
            }
        }
        touchBlocks();
    }

    /** Entity.updateFallState: falling builds up distance; landing calls {@link #fall}. */
    protected void updateFallState(double dy, boolean landed) {
        if (landed) {
            if (fallDistance > 0) fall(fallDistance, 1f);
            fallDistance = 0;
        } else if (dy < 0) {
            fallDistance -= (float) dy;
        }
    }

    protected void fall(float distance, float multiplier) {}

    /** Whether moving plays footsteps (Entity.canTriggerWalking). */
    protected boolean canTriggerWalking() { return true; }

    /** The block's step sound at 0.15 x its volume (Entity.playStepSound), snow on top winning. */
    protected void playStepSound(int bx, int by, int bz, Block block) {
        if (host == null) return;
        Block above = by + 1 < World.HEIGHT ? world.get(bx, by + 1, bz) : null;
        if (above != null && above.name().path().equals("snow_layer")) block = above;
        else if (Liquids.isLiquid(block)) return;
        net.minecraft.block.BlockSoundType type = net.minecraft.block.BlockSoundType.of(block);
        host.playSound(type.stepSound(), x, y, z, type.volume() * 0.15f, type.frequency());
    }

    /** What the blocks inside the box do (Entity.doBlockCollisions): cobwebs, soul sand, cactus. */
    protected void touchBlocks() {}

    // ---- liquids and fire ----------------------------------------------------------------------

    /**
     * Entity.handleWaterMovement: water in the box shrunk 0.4 top and
     * bottom; the current pushes 0.014 a tick. Entering stops falling and
     * puts out fire.
     */
    protected boolean handleWaterMovement() {
        AxisAlignedBB box = boundingBox();
        AxisAlignedBB water = new AxisAlignedBB(box.minX() + 0.001, box.minY() + 0.4 + 0.001, box.minZ() + 0.001,
            box.maxX() - 0.001, box.maxY() - 0.4 - 0.001, box.maxZ() - 0.001);
        double[] current = world.liquidCurrent(water, "water");
        if (current != null) {
            if (isPushedByWater()) {
                double[] push = Liquids.normalize(current[0], current[1], current[2]);
                motionX += push[0] * 0.014;
                motionY += push[1] * 0.014;
                motionZ += push[2] * 0.014;
            }
            if (!inWater && !firstUpdate) splash();
            fallDistance = 0;
            inWater = true;
            fire = 0;
        } else {
            inWater = false;
        }
        return inWater;
    }

    protected boolean isPushedByWater() { return true; }

    /** Entering water (Entity.resetHeight's splash): the sound; the client adds the droplets. */
    protected void splash() {
        if (host == null) return;
        float volume = (float) Math.min(1.0, Math.sqrt(motionX * motionX * 0.2 + motionY * motionY + motionZ * motionZ * 0.2) * 0.2);
        host.playSound(splashSound(), x, y, z, volume, 1f + (rand.nextFloat() - rand.nextFloat()) * 0.4f);
        host.waterSplash(this);
    }

    protected String splashSound() { return "game.neutral.swim.splash"; }

    /** Lava in the box shrunk 0.1 sideways and 0.4 top and bottom (Entity.isInLava). */
    public boolean isInLava() {
        AxisAlignedBB box = boundingBox();
        return world.isMaterialInBox(new AxisAlignedBB(box.minX() + 0.1, box.minY() + 0.4, box.minZ() + 0.1,
            box.maxX() - 0.1, box.maxY() - 0.4, box.maxZ() - 0.1), "lava");
    }

    /** Whether the box moved this far touches no block and no liquid (Entity.isOffsetPositionInLiquid's inverse). */
    protected boolean offsetIsFree(double dx, double dy, double dz) {
        AxisAlignedBB moved = boundingBox().offset(dx, dy, dz);
        return isFreeOfBlocks(moved) && !world.isMaterialInBox(moved, "water") && !world.isMaterialInBox(moved, "lava");
    }

    /** A fire block inside the box (shrunk 0.001: World.isFlammableWithin). */
    protected boolean touchesFire() {
        AxisAlignedBB box = boundingBox();
        int minX = floor(box.minX() + 0.001), maxX = floor(box.maxX() - 0.001);
        int minY = floor(box.minY() + 0.001), maxY = floor(box.maxY() - 0.001);
        int minZ = floor(box.minZ() + 0.001), maxZ = floor(box.maxZ() - 0.001);
        for (int bx = minX; bx <= maxX; bx++)
            for (int by = Math.max(0, minY); by <= Math.min(World.HEIGHT - 1, maxY); by++)
                for (int bz = minZ; bz <= maxZ; bz++) {
                    BlockState state = world.getState(bx, by, bz);
                    String name = state.block().name().path();
                    if (name.equals("fire") || state.block().material().equals("lava")) return true;
                }
        return false;
    }

    /** Sets the entity alight for {@code seconds} unless it is burning longer already (Entity.setFire). */
    public void setFire(int seconds) {
        int ticks = seconds * 20;
        if (fire < ticks) fire = ticks;
    }

    protected void extinguish() { fire = 0; }

    /** The light at the entity's eyes, sky then block (for spawning, despawning and drawing). */
    public int[] lightAt(double px, double py, double pz) {
        int bx = floor(px), by = floor(py), bz = floor(pz);
        if (by < 0) return new int[] {0, 0};
        if (by >= World.HEIGHT) return new int[] {15, 0};
        return new int[] {world.getSkyLight(bx, by, bz), world.getBlockLight(bx, by, bz)};
    }

    // ---- saving -------------------------------------------------------------------------------

    /** The vanilla entity id ("Creeper", "PrimedTnt", "XPOrb"). */
    public abstract String entityId();

    /** Entity.writeToNBT: Pos, Motion, Rotation, FallDistance, Fire, OnGround. */
    public void writeNbt(net.minecraft.nbt.NbtTag.Compound tag) {
        tag.put("id", new net.minecraft.nbt.NbtTag.Str(entityId()));
        tag.put("Pos", doubles(x, y, z));
        tag.put("Motion", doubles(motionX, motionY, motionZ));
        List<net.minecraft.nbt.NbtTag> rotation = new ArrayList<>();
        rotation.add(new net.minecraft.nbt.NbtTag.Float(rotationYaw));
        rotation.add(new net.minecraft.nbt.NbtTag.Float(rotationPitch));
        tag.put("Rotation", new net.minecraft.nbt.NbtTag.NbtList(net.minecraft.nbt.NbtTag.FLOAT, rotation));
        tag.put("FallDistance", new net.minecraft.nbt.NbtTag.Float(fallDistance));
        tag.put("Fire", new net.minecraft.nbt.NbtTag.Short((short) fire));
        tag.put("OnGround", new net.minecraft.nbt.NbtTag.Byte((byte) (onGround ? 1 : 0)));
    }

    public void readNbt(net.minecraft.nbt.NbtTag.Compound tag) {
        double[] pos = doubles(tag, "Pos"), motion = doubles(tag, "Motion");
        float yaw = 0, pitch = 0;
        if (tag.get("Rotation") instanceof net.minecraft.nbt.NbtTag.NbtList list && list.size() == 2) {
            yaw = ((net.minecraft.nbt.NbtTag.Float) list.values().get(0)).value();
            pitch = ((net.minecraft.nbt.NbtTag.Float) list.values().get(1)).value();
        }
        if (pos != null) setPositionAndRotation(pos[0], pos[1], pos[2], yaw, pitch);
        if (motion != null) {
            motionX = Math.abs(motion[0]) > 10 ? 0 : motion[0];
            motionY = Math.abs(motion[1]) > 10 ? 0 : motion[1];
            motionZ = Math.abs(motion[2]) > 10 ? 0 : motion[2];
        }
        if (tag.get("FallDistance") instanceof net.minecraft.nbt.NbtTag.Float fall) fallDistance = fall.value();
        if (tag.get("Fire") instanceof net.minecraft.nbt.NbtTag.Short burning) fire = burning.value();
        if (tag.get("OnGround") instanceof net.minecraft.nbt.NbtTag.Byte ground) onGround = ground.value() != 0;
    }

    protected static net.minecraft.nbt.NbtTag.NbtList doubles(double... values) {
        List<net.minecraft.nbt.NbtTag> list = new ArrayList<>();
        for (double value : values) list.add(new net.minecraft.nbt.NbtTag.Double(value));
        return new net.minecraft.nbt.NbtTag.NbtList(net.minecraft.nbt.NbtTag.DOUBLE, list);
    }

    protected static double[] doubles(net.minecraft.nbt.NbtTag.Compound tag, String name) {
        if (!(tag.get(name) instanceof net.minecraft.nbt.NbtTag.NbtList list) || list.size() != 3) return null;
        double[] out = new double[3];
        for (int i = 0; i < 3; i++) {
            if (!(list.values().get(i) instanceof net.minecraft.nbt.NbtTag.Double value)) return null;
            out[i] = value.value();
        }
        return out;
    }

    protected static float wrapDegrees(float angle) {
        angle %= 360f;
        if (angle >= 180f) angle -= 360f;
        if (angle < -180f) angle += 360f;
        return angle;
    }
}
