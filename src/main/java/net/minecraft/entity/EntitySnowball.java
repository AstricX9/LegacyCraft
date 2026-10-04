package net.minecraft.entity;

import net.minecraft.world.World;

/**
 * A thrown snowball (1.8.9 EntitySnowball, HYPOTHESIS: recalled): it does
 * no damage (blazes aren't in LegacyCraft) but still makes what it hits
 * flinch and pushes it away from the thrower, then bursts into eight
 * crumbs of snow.
 */
public final class EntitySnowball extends EntityThrowable {
    public EntitySnowball(World world, double x, double y, double z) { super(world, x, y, z); }

    @Override public String entityId() { return "Snowball"; }
    @Override public String itemName() { return "snowball"; }

    @Override
    protected void onImpact(Object hit) {
        if (hit != null) hitWith(hit, 0f);
        if (host != null)
            for (int i = 0; i < 8; i++) host.itemCrack("snowball", x, y, z, 0, 0, 0);
        remove();
    }
}
