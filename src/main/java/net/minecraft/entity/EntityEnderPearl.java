package net.minecraft.entity;

import net.minecraft.client.entity.Player;
import net.minecraft.world.World;

/**
 * A thrown ender pearl (1.8.9 EntityEnderPearl, HYPOTHESIS: recalled):
 * where it lands it bursts into 32 portal particles and the player who
 * threw it is teleported there, taking 5 fall damage.
 * shortcut: no 5% endermite, since there are no endermites yet.
 */
public final class EntityEnderPearl extends EntityThrowable {
    public EntityEnderPearl(World world, double x, double y, double z) { super(world, x, y, z); }

    @Override public String entityId() { return "ThrownEnderpearl"; }
    @Override public String itemName() { return "ender_pearl"; }

    @Override
    protected void onImpact(Object hit) {
        if (hit != null) hitWith(hit, 0f);
        if (host != null)
            for (int i = 0; i < 32; i++)
                host.particle("portal", x, y + rand.nextDouble() * 2.0, z, rand.nextGaussian(), 0, rand.nextGaussian());
        Player player = thrower();
        if (player != null && !player.dead && player.world() == world) {
            player.setPositionAndRotation(x, y, z, player.yaw, player.pitch);
            player.prevX = x; player.prevY = y; player.prevZ = z;
            player.motionX = player.motionY = player.motionZ = 0;
            player.fallDistance = 0;
            player.attack(DamageSource.FALL, 5f);
        }
        remove();
    }
}
