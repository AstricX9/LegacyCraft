package net.minecraft.entity;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A dropped item stack (1.8.9 EntityItem, master context §17): a 0.25-block
 * box that falls with gravity 0.04 blocks/tick², drag 0.98 (0.588 along the
 * ground, where it also bounces with half its vertical speed), merges with
 * matching stacks close by, can be picked up once its pickup delay (10 ticks
 * when a block breaks, 40 when thrown) has run out, and despawns after 6000
 * ticks (5 minutes). HYPOTHESIS constants; see docs/compat/unknowns.yml.
 *
 * <p>Liquids (1.8.9 Entity.onEntityUpdate and EntityItem.onUpdate): falling
 * into water splashes (the client shows it, see {@link #takeSplash}) and puts
 * out fire; in lava the item hops (0.2 up, a little sideways), catches fire
 * for 15 seconds and takes 4 damage, so with its 5 health it is gone on the
 * second tick it counts as in lava. Fire blocks set it alight too, and a
 * burning item takes 1 damage a second.
 */
public final class EntityItem extends Entity {
    public static final double SIZE = 0.25;
    public static final int LIFESPAN = 6000;
    private static final Random RANDOM = new Random();

    private ItemStack stack;
    public double motionX, motionY, motionZ;
    public double prevX, prevY, prevZ;
    public boolean onGround;
    public int age, pickupDelay;
    /** Random phase of the bob and spin, radians. */
    public final float hoverStart;
    /** Ticks left burning (0 = not on fire). */
    public int fire;
    private int health = 5;
    private boolean inWater, firstUpdate = true, splashPending;

    public EntityItem(World world, double x, double y, double z, ItemStack stack) {
        super(world, x, y, z);
        this.stack = stack;
        prevX = x; prevY = y; prevZ = z;
        hoverStart = (float) (RANDOM.nextDouble() * Math.PI * 2.0);
        // A new item pops up and sideways at random.
        motionX = RANDOM.nextDouble() * 0.2 - 0.1;
        motionY = 0.2;
        motionZ = RANDOM.nextDouble() * 0.2 - 0.1;
    }

    /** An item dropped by a broken block at a random point in its middle half, pickup after 10 ticks. */
    public static EntityItem fromBlock(World world, int x, int y, int z, ItemStack stack, Random random) {
        EntityItem item = new EntityItem(world, x + random.nextFloat() * 0.5 + 0.25, y + random.nextFloat() * 0.5 + 0.25,
            z + random.nextFloat() * 0.5 + 0.25, stack);
        item.pickupDelay = 10;
        return item;
    }

    /**
     * An item thrown by a player from eye height minus 0.3, 0.3 blocks/tick
     * along the look direction (plus 0.1 up and a little random spread), or
     * scattered around the feet when {@code scatter} (death).
     */
    public static EntityItem thrown(World world, double x, double eyeY, double z, double lookX, double lookY, double lookZ,
                                    ItemStack stack, boolean scatter, Random random) {
        EntityItem item = new EntityItem(world, x, eyeY - 0.3, z, stack);
        item.pickupDelay = 40;
        if (scatter) {
            float speed = random.nextFloat() * 0.5f, angle = random.nextFloat() * (float) Math.PI * 2f;
            item.motionX = -Math.sin(angle) * speed;
            item.motionZ = Math.cos(angle) * speed;
            item.motionY = 0.2;
        } else {
            double speed = 0.3;
            item.motionX = lookX * speed;
            item.motionZ = lookZ * speed;
            item.motionY = lookY * speed + 0.1;
            double angle = random.nextFloat() * Math.PI * 2.0, spread = 0.02 * random.nextFloat();
            item.motionX += Math.cos(angle) * spread;
            item.motionY += (random.nextFloat() - random.nextFloat()) * 0.1f;
            item.motionZ += Math.sin(angle) * spread;
        }
        return item;
    }

    public ItemStack stack() { return stack; }
    public void setStack(ItemStack stack) { this.stack = stack; if (stack == null || stack.isEmpty()) remove(); }

    public AxisAlignedBB boundingBox() {
        double half = SIZE / 2;
        return new AxisAlignedBB(x - half, y, z - half, x + half, y + SIZE, z + half);
    }

    /** A sound the item makes at its position this tick (splashing, fizzing in lava). */
    public record Sound(String event, float volume, float pitch) {}
    private final java.util.List<Sound> sounds = new java.util.ArrayList<>();

    /** The sounds made since the last call (cleared by the call). */
    public java.util.List<Sound> takeSounds() {
        if (sounds.isEmpty()) return java.util.List.of();
        java.util.List<Sound> result = java.util.List.copyOf(sounds);
        sounds.clear();
        return result;
    }

    /** Whether the item has just fallen into water (cleared by the call): the client splashes. */
    public boolean takeSplash() {
        boolean splash = splashPending;
        splashPending = false;
        return splash;
    }

    public boolean isBurning() { return fire > 0; }

    /** The water/fire/lava part of the entity update, before the item moves. */
    private void updateLiquids() {
        AxisAlignedBB box = boundingBox();
        AxisAlignedBB waterBox = new AxisAlignedBB(box.minX() + 0.001, box.minY() + 0.4 + 0.001, box.minZ() + 0.001,
            box.maxX() - 0.001, box.maxY() - 0.4 - 0.001, box.maxZ() - 0.001);
        double[] current = world.liquidCurrent(waterBox, "water");
        if (current != null) {
            double[] push = net.minecraft.block.Liquids.normalize(current[0], current[1], current[2]);
            motionX += push[0] * 0.014;
            motionY += push[1] * 0.014;
            motionZ += push[2] * 0.014;
            if (!inWater && !firstUpdate) {
                splashPending = true;
                float volume = (float) Math.min(1.0, Math.sqrt(motionX * motionX * 0.2 + motionY * motionY + motionZ * motionZ * 0.2) * 0.2);
                sounds.add(new Sound("game.neutral.swim.splash", volume, 1f + (RANDOM.nextFloat() - RANDOM.nextFloat()) * 0.4f));
            }
            inWater = true;
            if (fire > 0) sounds.add(new Sound("random.fizz", 0.7f, 1.6f + (RANDOM.nextFloat() - RANDOM.nextFloat()) * 0.4f));
            fire = 0;
        } else {
            inWater = false;
        }
        if (fire > 0) {
            if (fire % 20 == 0) damage(1);
            fire--;
        }
        AxisAlignedBB lavaBox = new AxisAlignedBB(box.minX() + 0.1, box.minY() + 0.4, box.minZ() + 0.1,
            box.maxX() - 0.1, box.maxY() - 0.4, box.maxZ() - 0.1);
        if (world.isMaterialInBox(lavaBox, "lava")) {
            damage(4);
            fire = Math.max(fire, 15 * 20);
        }
        firstUpdate = false;
    }

    private void damage(int amount) {
        health -= amount;
        if (health <= 0) remove();
    }

    @Override
    public void tick() {
        updateLiquids();
        if (isRemoved()) return;
        prevX = x; prevY = y; prevZ = z;
        if (pickupDelay > 0 && pickupDelay != Short.MAX_VALUE) pickupDelay--;
        motionY -= 0.04;
        int bx = (int) Math.floor(x), by = (int) Math.floor(y), bz = (int) Math.floor(z);
        if (by >= 0 && by < World.HEIGHT && world.generatedChunk(bx >> 4, bz >> 4) != null
            && world.get(bx, by, bz).material().equals("lava")) {
            motionY = 0.2;
            motionX = (RANDOM.nextFloat() - RANDOM.nextFloat()) * 0.2f;
            motionZ = (RANDOM.nextFloat() - RANDOM.nextFloat()) * 0.2f;
            sounds.add(new Sound("random.fizz", 0.4f, 2.0f + RANDOM.nextFloat() * 0.4f));
        }
        move(motionX, motionY, motionZ);
        if (touchesFire()) {
            damage(1);
            if (isRemoved()) return;
            if (!inWater) {
                fire++;
                if (fire == 0) fire = 8 * 20;
            }
        } else if (fire <= 0) {
            fire = -1;
        }
        double friction = 0.98;
        if (onGround) friction = slipperiness() * 0.98;
        motionX *= friction;
        motionY *= 0.98;
        motionZ *= friction;
        if (onGround) motionY *= -0.5;
        age++;
        if (age >= LIFESPAN || y < -64) remove();
    }

    /** Fire or lava anywhere in the item's box, shrunk by 0.001 (World.isFlammableWithin). */
    private boolean touchesFire() {
        AxisAlignedBB box = boundingBox();
        AxisAlignedBB inner = new AxisAlignedBB(box.minX() + 0.001, box.minY() + 0.001, box.minZ() + 0.001,
            box.maxX() - 0.001, box.maxY() - 0.001, box.maxZ() - 0.001);
        int minX = (int) Math.floor(inner.minX()), maxX = (int) Math.floor(inner.maxX() + 1.0);
        int minY = (int) Math.floor(inner.minY()), maxY = (int) Math.floor(inner.maxY() + 1.0);
        int minZ = (int) Math.floor(inner.minZ()), maxZ = (int) Math.floor(inner.maxZ() + 1.0);
        for (int bx = minX; bx < maxX; bx++)
            for (int by = Math.max(0, minY); by < Math.min(World.HEIGHT, maxY); by++)
                for (int bz = minZ; bz < maxZ; bz++) {
                    if (world.generatedChunk(bx >> 4, bz >> 4) == null) continue;
                    String name = world.get(bx, by, bz).name().path();
                    if (name.equals("fire") || name.equals("lava") || name.equals("flowing_lava")) return true;
                }
        return false;
    }

    /** Block slipperiness under the item: 0.6, ice and packed ice 0.98, slime 0.8. */
    private double slipperiness() {
        String below = world.get((int) Math.floor(x), (int) Math.floor(y) - 1, (int) Math.floor(z)).name().path();
        return switch (below) {
            case "ice", "packed_ice" -> 0.98;
            case "slime" -> 0.8;
            default -> 0.6;
        };
    }

    /** Moves by clipping against block collision boxes, Y then X then Z. */
    private void move(double dx, double dy, double dz) {
        double wantedX = dx, wantedY = dy, wantedZ = dz;
        AxisAlignedBB box = boundingBox();
        List<AxisAlignedBB> boxes = collisionBoxes(box.addCoord(dx, dy, dz));
        for (AxisAlignedBB block : boxes) dy = block.clipY(box, dy);
        box = box.offset(0, dy, 0);
        for (AxisAlignedBB block : boxes) dx = block.clipX(box, dx);
        box = box.offset(dx, 0, 0);
        for (AxisAlignedBB block : boxes) dz = block.clipZ(box, dz);
        box = box.offset(0, 0, dz);
        x = (box.minX() + box.maxX()) / 2;
        y = box.minY();
        z = (box.minZ() + box.maxZ()) / 2;
        onGround = wantedY != dy && wantedY < 0;
        if (wantedX != dx) motionX = 0;
        if (wantedY != dy) motionY = 0;
        if (wantedZ != dz) motionZ = 0;
    }

    private List<AxisAlignedBB> collisionBoxes(AxisAlignedBB area) {
        List<AxisAlignedBB> result = new ArrayList<>();
        int minX = (int) Math.floor(area.minX()), maxX = (int) Math.floor(area.maxX());
        int minY = (int) Math.floor(area.minY()) - 1, maxY = (int) Math.floor(area.maxY());
        int minZ = (int) Math.floor(area.minZ()), maxZ = (int) Math.floor(area.maxZ());
        for (int by = Math.max(0, minY); by <= Math.min(World.HEIGHT - 1, maxY); by++)
            for (int bz = minZ; bz <= maxZ; bz++)
                for (int bx = minX; bx <= maxX; bx++)
                    for (AxisAlignedBB box : Block.collisionBoxesAt(world, bx, by, bz))
                        if (box.intersects(area)) result.add(box);
        return result;
    }

    /**
     * Folds a nearby matching stack into this one (vanilla combineItems): the
     * bigger stack absorbs the smaller while the total fits one stack; the
     * merged item keeps the longer pickup delay and the younger age.
     */
    public boolean tryMerge(EntityItem other) {
        if (other == this || isRemoved() || other.isRemoved()) return false;
        if (!stack.canMerge(other.stack) || stack.count() + other.stack.count() > stack.item().maxStackSize()) return false;
        if (other.stack.count() > stack.count()) return other.tryMerge(this);
        stack.mergeFrom(other.stack);
        pickupDelay = Math.max(pickupDelay, other.pickupDelay);
        age = Math.min(age, other.age);
        other.remove();
        return true;
    }

    /** Whether this item is within merging distance (0.5 horizontally, touching vertically) of another. */
    public boolean isNear(EntityItem other) {
        AxisAlignedBB self = boundingBox(), them = other.boundingBox();
        return self.maxX() + 0.5 > them.minX() && self.minX() - 0.5 < them.maxX()
            && self.maxY() >= them.minY() && self.minY() <= them.maxY()
            && self.maxZ() + 0.5 > them.minZ() && self.minZ() - 0.5 < them.maxZ();
    }
}
