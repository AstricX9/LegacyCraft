package net.minecraft.entity;

import net.minecraft.client.entity.Player;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

/**
 * An experience orb (1.8.9 EntityXPOrb, HYPOTHESIS: recalled): pops out at
 * random, falls with 0.03 gravity and bounces, is drawn toward a player
 * within 8 blocks (harder the closer), is picked up when it touches them
 * (every 2 ticks at most, with the orb sound) and vanishes after 5 minutes.
 */
public final class EntityXPOrb extends MovingEntity {
    public int xpValue, xpColor, xpOrbAge, delayBeforeCanPickup;
    private int health = 5;

    public EntityXPOrb(World world, double x, double y, double z, int value) {
        super(world, x, y, z);
        width = height = 0.5f;
        rotationYaw = (float) (Math.random() * 360.0);
        motionX = (Math.random() * 0.2 - 0.1) * 2.0;
        motionY = Math.random() * 0.2 * 2.0;
        motionZ = (Math.random() * 0.2 - 0.1) * 2.0;
        xpValue = value;
    }

    @Override public String entityId() { return "XPOrb"; }

    @Override protected boolean canTriggerWalking() { return false; }


    @Override
    public void tick() {
        if (frozen()) return;
        prevX = x; prevY = y; prevZ = z;
        ticksExisted++;
        handleWaterMovement();
        if (delayBeforeCanPickup > 0) delayBeforeCanPickup--;
        motionY -= 0.03;
        if (world.isMaterialInBox(boundingBox(), "lava")) {
            motionY = 0.2;
            motionX = (rand.nextFloat() - rand.nextFloat()) * 0.2f;
            motionZ = (rand.nextFloat() - rand.nextFloat()) * 0.2f;
            if (host != null) host.playSound("random.fizz", x, y, z, 0.4f, 2f + rand.nextFloat() * 0.4f);
        }
        Player player = host == null ? null : host.player();
        if (player != null && !player.dead && !player.noClip) {
            double dx = (player.x - x) / 8.0, dy = (player.y + player.eyeHeight() / 2.0 - y) / 8.0, dz = (player.z - z) / 8.0;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double pull = 1.0 - distance;
            if (pull > 0.0) {
                pull *= pull;
                motionX += dx / distance * pull * 0.1;
                motionY += dy / distance * pull * 0.1;
                motionZ += dz / distance * pull * 0.1;
            }
        }
        move(motionX, motionY, motionZ);
        float friction = 0.98f;
        if (onGround) friction = slipperiness() * 0.98f;
        motionX *= friction;
        motionY *= 0.98;
        motionZ *= friction;
        if (onGround) motionY *= -0.9;
        xpColor++;
        xpOrbAge++;
        if (xpOrbAge >= 6000) remove();
        if (player != null && !player.dead && !player.noClip) collideWithPlayer(player);
        firstUpdate = false;
    }

    /** EntityXPOrb.onCollideWithPlayer, reached through the player's pickup box (1 block out, 0.5 up and down). */
    private void collideWithPlayer(Player player) {
        AxisAlignedBB box = player.boundingBox();
        AxisAlignedBB reach = new AxisAlignedBB(box.minX() - 1, box.minY() - 0.5, box.minZ() - 1, box.maxX() + 1, box.maxY() + 0.5, box.maxZ() + 1);
        if (delayBeforeCanPickup != 0 || player.xpCooldown != 0 || !reach.intersects(boundingBox())) return;
        player.xpCooldown = 2;
        host.playSound("random.orb", player.x, player.y, player.z, 0.1f, 0.5f * ((rand.nextFloat() - rand.nextFloat()) * 0.7f + 1.8f));
        host.giveExperience(xpValue);
        remove();
    }

    private float slipperiness() {
        int bx = floor(x), by = floor(boundingBox().minY()) - 1, bz = floor(z);
        if (by < 0) return 0.6f;
        return switch (world.get(bx, by, bz).name().path()) {
            case "ice", "packed_ice" -> 0.98f;
            case "slime" -> 0.8f;
            default -> 0.6f;
        };
    }

    /** Explosions (and fire) destroy orbs: they have 5 health. */
    @Override
    public boolean attackFromExplosion(float damage, String attacker, MovingEntity exploder) {
        health -= (int) damage;
        if (health <= 0) remove();
        return true;
    }

    /** Which of the 11 orb sizes it draws as (getXpOrbTextureIndex). */
    public int textureIndex() {
        int v = xpValue;
        return v >= 2477 ? 10 : v >= 1237 ? 9 : v >= 617 ? 8 : v >= 307 ? 7 : v >= 149 ? 6 : v >= 73 ? 5 : v >= 37 ? 4
            : v >= 17 ? 3 : v >= 7 ? 2 : v >= 3 ? 1 : 0;
    }

    /** EntityXPOrb.getXPSplit: the biggest orb value not above {@code amount}. */
    public static int split(int amount) {
        int[] sizes = {2477, 1237, 617, 307, 149, 73, 37, 17, 7, 3};
        for (int size : sizes) if (amount >= size) return size;
        return 1;
    }

    @Override
    public void writeNbt(net.minecraft.nbt.NbtTag.Compound tag) {
        super.writeNbt(tag);
        tag.put("Health", new net.minecraft.nbt.NbtTag.Short((short) health));
        tag.put("Age", new net.minecraft.nbt.NbtTag.Short((short) xpOrbAge));
        tag.put("Value", new net.minecraft.nbt.NbtTag.Short((short) xpValue));
    }

    @Override
    public void readNbt(net.minecraft.nbt.NbtTag.Compound tag) {
        super.readNbt(tag);
        if (tag.get("Health") instanceof net.minecraft.nbt.NbtTag.Short h) health = h.value() & 255;
        if (tag.get("Age") instanceof net.minecraft.nbt.NbtTag.Short a) xpOrbAge = a.value();
        if (tag.get("Value") instanceof net.minecraft.nbt.NbtTag.Short v) xpValue = v.value();
    }
}
