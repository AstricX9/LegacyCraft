package net.minecraft.entity.passive;

import net.minecraft.entity.ai.EntityAI;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import java.util.Set;

/**
 * The cow (1.8.9 EntityCow, HYPOTHESIS: recalled): 10 health, speed 0.2,
 * 0.9 x 1.3, quiet (0.4 volume). Panics at 2.0, breeds on and follows
 * wheat (1.25), gives milk to an empty bucket (not in Creative, not
 * babies), and drops 0-2 leather and 1-3 beef (each plus 0-looting), the
 * beef cooked if it died burning.
 */
public final class EntityCow extends EntityAnimal {
    private static final Set<String> FOOD = Set.of("wheat");

    public EntityCow(World world, double x, double y, double z) {
        super(world, x, y, z, 0.9f, 1.3f);
        navigator().setAvoidsWater(true);
        tasks.add(0, new EntityAI.Swimming(this));
        tasks.add(1, new Panic(this, 2.0));
        tasks.add(2, new Mate(this, 1.0));
        tasks.add(3, new Tempt(this, 1.25, FOOD));
        tasks.add(4, new FollowParent(this, 1.25));
        tasks.add(5, new EntityAI.Wander(this, 1.0));
        tasks.add(6, new EntityAI.WatchClosest(this, 6f));
        tasks.add(7, new EntityAI.LookIdle(this));
    }

    @Override public String entityId() { return "Cow"; }
    @Override public float maxHealth() { return 10f; }
    @Override public double movementSpeed() { return 0.2; }
    @Override protected String livingSound() { return "mob.cow.say"; }
    @Override protected String hurtSound() { return "mob.cow.hurt"; }
    @Override protected String deathSound() { return "mob.cow.hurt"; }
    @Override protected String stepSound() { return "mob.cow.step"; }
    @Override protected float soundVolume() { return 0.4f; }
    @Override protected Set<String> breedingItems() { return FOOD; }
    @Override protected EntityAnimal createChild(double x, double y, double z) { return new EntityCow(world, x, y, z); }

    /** EntityCow.interact: an empty bucket fills with milk (survival only, adults only). */
    @Override
    public boolean interact(Interactor game) {
        ItemStack held = game.held();
        if (held != null && held.item().name().path().equals("bucket") && !game.creative() && !isChild()) {
            ItemStack milk = game.stack("milk_bucket");
            if (milk != null) {
                game.exchangeHeld(milk);
                return true;
            }
        }
        return super.interact(game);
    }

    @Override
    protected void dropFewItems(boolean byPlayer, int looting) {
        int leather = rand.nextInt(3) + rand.nextInt(1 + looting);
        for (int i = 0; i < leather; i++) dropItem("leather", 1);
        int beef = rand.nextInt(3) + 1 + rand.nextInt(1 + looting);
        for (int i = 0; i < beef; i++) dropItem(meat("beef", "cooked_beef"), 1);
    }
}
