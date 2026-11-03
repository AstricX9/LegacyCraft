package net.minecraft.entity;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/**
 * A thrown eye of ender (1.8.9 EntityEnderEye, HYPOTHESIS: recalled): it
 * flies at most 12 blocks toward the nearest stronghold, rising 8, easing
 * its speed toward the remaining distance and slowing when it gets there,
 * trailing portal particles. After 80 ticks it drops back as an item (four
 * times in five) or shatters (World.playAuxSFX 2003).
 */
public final class EntityEnderEye extends EntityThrowable {
    public double targetX, targetY, targetZ;
    public int despawnTimer;
    public boolean shatterOrDrop;

    public EntityEnderEye(World world, double x, double y, double z) { super(world, x, y, z); }

    @Override public String entityId() { return "EyeOfEnderSignal"; }
    @Override public String itemName() { return "ender_eye"; }
    @Override protected void onImpact(Object hit) { }

    /** EntityEnderEye.moveTowards. */
    public void moveTowards(double tx, int ty, double tz) {
        double dx = tx - x, dz = tz - z;
        float distance = (float) Math.sqrt(dx * dx + dz * dz);
        if (distance > 12f) {
            targetX = x + dx / distance * 12.0;
            targetZ = z + dz / distance * 12.0;
            targetY = y + 8.0;
        } else {
            targetX = tx;
            targetY = ty;
            targetZ = tz;
        }
        despawnTimer = 0;
        shatterOrDrop = rand.nextInt(5) > 0;
    }

    @Override
    public void tick() {
        if (frozen()) return;
        prevX = x; prevY = y; prevZ = z;
        prevRotationYaw = rotationYaw;
        prevRotationPitch = rotationPitch;
        ticksExisted++;
        setPosition(x + motionX, y + motionY, z + motionZ);
        float speed = (float) Math.sqrt(motionX * motionX + motionZ * motionZ);
        rotationYaw = (float) Math.toDegrees(Math.atan2(motionX, motionZ));
        rotationPitch = (float) Math.toDegrees(Math.atan2(motionY, speed));
        double dx = targetX - x, dz = targetZ - z;
        float left = (float) Math.sqrt(dx * dx + dz * dz);
        float angle = (float) Math.atan2(dz, dx);
        double horizontal = speed + (left - speed) * 0.0025;
        if (left < 1f) {
            horizontal *= 0.8;
            motionY *= 0.8;
        }
        motionX = Math.cos(angle) * horizontal;
        motionZ = Math.sin(angle) * horizontal;
        motionY += ((y < targetY ? 1.0 : -1.0) - motionY) * 0.014999999664723873;
        if (host != null)
            host.particle(isInWater() ? "bubble" : "portal", x - motionX * 0.25 + rand.nextDouble() * 0.6 - 0.3,
                y - motionY * 0.25 - 0.5, z - motionZ * 0.25 + rand.nextDouble() * 0.6 - 0.3, motionX, motionY, motionZ);
        if (++despawnTimer > 80) {
            remove();
            if (host == null) return;
            if (shatterOrDrop) {
                java.util.function.Function<String, Item> items = world.itemLookup();
                Item item = items == null ? null : items.apply("ender_eye");
                if (item != null) host.spawn(new EntityItem(world, x, y, z, new ItemStack(item)));
            } else shatter();
        }
    }

    /** RenderGlobal.playAuxSFX 2003: eight crumbs of the eye and two rings of portal particles closing in. */
    private void shatter() {
        double cx = Math.floor(x) + 0.5, cy = Math.floor(y), cz = Math.floor(z) + 0.5;
        for (int i = 0; i < 8; i++)
            host.itemCrack("ender_eye", cx, cy, cz, rand.nextGaussian() * 0.15, rand.nextDouble() * 0.2, rand.nextGaussian() * 0.15);
        for (double a = 0; a < Math.PI * 2; a += Math.PI / 20) {
            double c = Math.cos(a), s = Math.sin(a);
            host.particle("portal", cx + c * 5, cy - 0.4, cz + s * 5, c * -5, 0, s * -5);
            host.particle("portal", cx + c * 5, cy - 0.4, cz + s * 5, c * -7, 0, s * -7);
        }
    }
}
