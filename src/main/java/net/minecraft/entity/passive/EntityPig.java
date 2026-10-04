package net.minecraft.entity.passive;

import net.minecraft.entity.ai.EntityAI;
import net.minecraft.world.World;

import java.util.Set;

/**
 * The pig (1.8.9 EntityPig, HYPOTHESIS: recalled): 10 health, speed 0.25,
 * 0.9 x 0.9. Panics at 1.25, breeds on carrots, follows carrots and carrots
 * on a stick at 1.2, and drops 1-3 porkchops (plus 0-looting), cooked if it
 * died burning. Saddles and riding aren't modelled.
 */
public final class EntityPig extends EntityAnimal {
    private static final Set<String> FOOD = Set.of("carrot");
    private static final Set<String> TEMPT = Set.of("carrot", "carrot_on_a_stick");

    public EntityPig(World world, double x, double y, double z) {
        super(world, x, y, z, 0.9f, 0.9f);
        navigator().setAvoidsWater(true);
        tasks.add(0, new EntityAI.Swimming(this));
        tasks.add(1, new Panic(this, 1.25));
        tasks.add(3, new Mate(this, 1.0));
        tasks.add(4, new Tempt(this, 1.2, TEMPT));
        tasks.add(5, new FollowParent(this, 1.1));
        tasks.add(6, new EntityAI.Wander(this, 1.0));
        tasks.add(7, new EntityAI.WatchClosest(this, 6f));
        tasks.add(8, new EntityAI.LookIdle(this));
    }

    @Override public String entityId() { return "Pig"; }
    @Override public float maxHealth() { return 10f; }
    @Override public double movementSpeed() { return 0.25; }
    @Override protected String livingSound() { return "mob.pig.say"; }
    @Override protected String hurtSound() { return "mob.pig.say"; }
    @Override protected String deathSound() { return "mob.pig.death"; }
    @Override protected String stepSound() { return "mob.pig.step"; }
    @Override protected Set<String> breedingItems() { return FOOD; }
    @Override protected EntityAnimal createChild(double x, double y, double z) { return new EntityPig(world, x, y, z); }

    @Override
    protected void dropFewItems(boolean byPlayer, int looting) {
        int count = rand.nextInt(3) + 1 + rand.nextInt(1 + looting);
        for (int i = 0; i < count; i++) dropItem(meat("porkchop", "cooked_porkchop"), 1);
    }

    @Override
    public void writeNbt(net.minecraft.nbt.NbtTag.Compound tag) {
        super.writeNbt(tag);
        tag.put("Saddle", new net.minecraft.nbt.NbtTag.Byte((byte) 0));
    }
}
