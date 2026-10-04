package net.minecraft.entity;

import net.minecraft.entity.passive.EntityChicken;
import net.minecraft.world.World;

/**
 * A thrown egg (1.8.9 EntityEgg, HYPOTHESIS: recalled): it does no damage
 * but makes what it hits flinch; one in 8 hatches a chick where it broke
 * (one in 32 of those hatches four), then it breaks into eight crumbs.
 */
public final class EntityEgg extends EntityThrowable {
    public EntityEgg(World world, double x, double y, double z) { super(world, x, y, z); }

    @Override public String entityId() { return "ThrownEgg"; }
    @Override public String itemName() { return "egg"; }

    @Override
    protected void onImpact(Object hit) {
        if (hit != null) hitWith(hit, 0f);
        if (host != null) {
            if (rand.nextInt(8) == 0) {
                int count = rand.nextInt(32) == 0 ? 4 : 1;
                for (int i = 0; i < count; i++) {
                    EntityChicken chick = new EntityChicken(world, x, y, z);
                    chick.setGrowingAge(-24000);
                    chick.setPositionAndRotation(x, y, z, rotationYaw, 0f);
                    host.spawn(chick);
                }
            }
            for (int i = 0; i < 8; i++)
                host.itemCrack("egg", x, y, z, (rand.nextFloat() - 0.5) * 0.08, (rand.nextFloat() - 0.5) * 0.08, (rand.nextFloat() - 0.5) * 0.08);
        }
        remove();
    }
}
