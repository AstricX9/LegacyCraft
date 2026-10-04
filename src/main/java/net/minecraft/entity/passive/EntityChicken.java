package net.minecraft.entity.passive;

import net.minecraft.entity.ai.EntityAI;
import net.minecraft.world.World;

import java.util.Set;

/**
 * The chicken (1.8.9 EntityChicken, HYPOTHESIS: recalled): 4 health, speed
 * 0.25, 0.4 x 0.7, eyes at the top. Panics at 1.4, breeds on any seeds,
 * follows wheat seeds, flaps its wings and falls slowly (60% of its falling
 * speed each tick, so it never takes fall damage), lays an egg every
 * 6000-11999 ticks as an adult, and drops 0-2 feathers (plus 0-looting) and
 * a raw chicken, cooked if it died burning.
 */
public final class EntityChicken extends EntityAnimal {
    private static final Set<String> FOOD = Set.of("wheat_seeds", "pumpkin_seeds", "melon_seeds", "nether_wart");
    private static final Set<String> TEMPT = Set.of("wheat_seeds");

    public float wingRotation, destPos, prevDestPos, prevWingRotation, wingRotDelta = 1f;
    public int timeUntilNextEgg;

    public EntityChicken(World world, double x, double y, double z) {
        super(world, x, y, z, 0.4f, 0.7f);
        timeUntilNextEgg = rand.nextInt(6000) + 6000;
        tasks.add(0, new EntityAI.Swimming(this));
        tasks.add(1, new Panic(this, 1.4));
        tasks.add(2, new Mate(this, 1.0));
        tasks.add(3, new Tempt(this, 1.0, TEMPT));
        tasks.add(4, new FollowParent(this, 1.1));
        tasks.add(5, new EntityAI.Wander(this, 1.0));
        tasks.add(6, new EntityAI.WatchClosest(this, 6f));
        tasks.add(7, new EntityAI.LookIdle(this));
    }

    @Override public String entityId() { return "Chicken"; }
    @Override public float maxHealth() { return 4f; }
    @Override public double movementSpeed() { return 0.25; }
    @Override public float eyeHeight() { return height; }
    @Override protected String livingSound() { return "mob.chicken.say"; }
    @Override protected String hurtSound() { return "mob.chicken.hurt"; }
    @Override protected String deathSound() { return "mob.chicken.hurt"; }
    @Override protected String stepSound() { return "mob.chicken.step"; }
    @Override protected Set<String> breedingItems() { return FOOD; }
    @Override protected EntityAnimal createChild(double x, double y, double z) { return new EntityChicken(world, x, y, z); }

    /** EntityChicken.onLivingUpdate: the wings flap in the air, falling slows, and adults lay eggs. */
    @Override
    protected void livingUpdate() {
        super.livingUpdate();
        prevWingRotation = wingRotation;
        prevDestPos = destPos;
        destPos += (onGround ? -1f : 4f) * 0.3f;
        destPos = Math.max(0f, Math.min(1f, destPos));
        if (!onGround && wingRotDelta < 1f) wingRotDelta = 1f;
        wingRotDelta *= 0.9f;
        if (!onGround && motionY < 0) motionY *= 0.6;
        wingRotation += wingRotDelta * 2f;
        if (!isChild() && isAlive() && --timeUntilNextEgg <= 0) {
            playSound("mob.chicken.plop", 1f, (rand.nextFloat() - rand.nextFloat()) * 0.2f + 1f);
            dropItem("egg", 1);
            timeUntilNextEgg = rand.nextInt(6000) + 6000;
        }
    }

    /** Chickens flutter down and never take fall damage. */
    @Override protected void fall(float distance, float multiplier) {}

    @Override
    protected void dropFewItems(boolean byPlayer, int looting) {
        int feathers = rand.nextInt(3) + rand.nextInt(1 + looting);
        for (int i = 0; i < feathers; i++) dropItem("feather", 1);
        dropItem(meat("chicken", "cooked_chicken"), 1);
    }

    @Override
    public void writeNbt(net.minecraft.nbt.NbtTag.Compound tag) {
        super.writeNbt(tag);
        tag.put("IsChickenJockey", new net.minecraft.nbt.NbtTag.Byte((byte) 0));
        tag.put("EggLayTime", new net.minecraft.nbt.NbtTag.Int(timeUntilNextEgg));
    }

    @Override
    public void readNbt(net.minecraft.nbt.NbtTag.Compound tag) {
        super.readNbt(tag);
        if (tag.get("EggLayTime") instanceof net.minecraft.nbt.NbtTag.Int time) timeUntilNextEgg = time.value();
    }
}
