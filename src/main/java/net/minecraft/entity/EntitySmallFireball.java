package net.minecraft.entity;

import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.world.World;

/**
 * A blaze's fireball (1.8.9 EntitySmallFireball, HYPOTHESIS: recalled):
 * 0.3125 wide; it does 5 fire damage and sets what it hits alight for 5
 * seconds, or lights the air in front of the block it hits (if mobGriefing
 * allows). It can't be batted back.
 */
public final class EntitySmallFireball extends EntityFireball {
    public EntitySmallFireball(World world, double x, double y, double z) { super(world, x, y, z, 0.3125f); }

    public static EntitySmallFireball shotBy(World world, EntityLiving shooter, double ax, double ay, double az, java.util.Random random) {
        EntitySmallFireball fireball = new EntitySmallFireball(world, shooter.x(), shooter.y(), shooter.z());
        fireball.aim(shooter, ax, ay, az, random);
        return fireball;
    }

    @Override public String entityId() { return "SmallFireball"; }
    @Override public float renderScale() { return 0.5f; }
    @Override public boolean deflectable() { return false; }

    @Override
    protected void onImpact(Object hit, WorldRenderer.Hit block) {
        if (hit instanceof net.minecraft.client.entity.Player player) {
            damage(player, 5f);
            player.setFire(5);
        } else if (hit instanceof EntityLiving living) {
            boolean immune = living.isImmuneToFire();
            damage(living, 5f);
            if (!immune) living.setFire(5);
        } else if (block != null && host != null && (shooter == null || host.gameRule("mobGriefing"))) {
            int fx = block.placeX(), fy = block.placeY(), fz = block.placeZ();
            if (fy >= 0 && fy < World.HEIGHT && world.get(fx, fy, fz).isAir()) host.setBlock(fx, fy, fz, "fire", 0);
        }
        remove();
    }
}
