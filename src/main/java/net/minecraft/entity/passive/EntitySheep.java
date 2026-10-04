package net.minecraft.entity.passive;

import net.minecraft.entity.EntityItem;
import net.minecraft.entity.ai.EntityAI;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import java.util.Random;
import java.util.Set;

/**
 * The sheep (1.8.9 EntitySheep, HYPOTHESIS: recalled): 8 health, speed
 * 0.23, 0.9 x 1.3, eyes at 95% of its height. Panics at 1.25, breeds on and
 * follows wheat (1.1). Now and then (1 in 1000 checks, babies 1 in 50) it
 * lowers its head for 40 ticks and eats the tall grass it stands in or the
 * grass block under it (turning it to dirt, if mobGriefing allows), which
 * regrows its wool and speeds a baby up by a minute. Shears take 1-3 wool of
 * its colour; dye recolours it. Natural sheep are white 81.836% of the
 * time, black, grey and light grey 5% each, brown 3% and pink 0.164%;
 * lambs take the colour their parents' dyes would craft, else one parent's.
 * It drops its wool (unless sheared) and 1-2 mutton (plus 0-looting),
 * cooked if it died burning.
 */
public final class EntitySheep extends EntityAnimal {
    private static final Set<String> FOOD = Set.of("wheat");
    /** Wool colour names by metadata (EnumDyeColor), for the drops. */
    public static final String[] WOOL = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
        "silver", "cyan", "purple", "blue", "brown", "green", "red", "black"};
    /** The fleece tint by wool metadata (EntitySheep.DYE_TO_RGB). */
    public static final float[][] FLEECE = {
        {1f, 1f, 1f}, {0.85f, 0.5f, 0.2f}, {0.7f, 0.3f, 0.85f}, {0.4f, 0.6f, 0.85f}, {0.9f, 0.9f, 0.2f},
        {0.5f, 0.8f, 0.1f}, {0.95f, 0.5f, 0.65f}, {0.3f, 0.3f, 0.3f}, {0.6f, 0.6f, 0.6f}, {0.3f, 0.5f, 0.6f},
        {0.5f, 0.25f, 0.7f}, {0.2f, 0.3f, 0.7f}, {0.4f, 0.3f, 0.2f}, {0.4f, 0.5f, 0.2f}, {0.6f, 0.2f, 0.2f},
        {0.1f, 0.1f, 0.1f}};

    /** The wool metadata (0 white .. 15 black). */
    public int fleeceColor;
    public boolean sheared;
    /** Ticks left of the head-down grazing animation (sheepTimer). */
    public int sheepTimer;
    private final EatGrass eatGrass = new EatGrass(this);

    public EntitySheep(World world, double x, double y, double z) {
        super(world, x, y, z, 0.9f, 1.3f);
        navigator().setAvoidsWater(true);
        tasks.add(0, new EntityAI.Swimming(this));
        tasks.add(1, new Panic(this, 1.25));
        tasks.add(2, new Mate(this, 1.0));
        tasks.add(3, new Tempt(this, 1.1, FOOD));
        tasks.add(4, new FollowParent(this, 1.1));
        tasks.add(5, eatGrass);
        tasks.add(6, new EntityAI.Wander(this, 1.0));
        tasks.add(7, new EntityAI.WatchClosest(this, 6f));
        tasks.add(8, new EntityAI.LookIdle(this));
    }

    @Override public String entityId() { return "Sheep"; }
    @Override public float maxHealth() { return 8f; }
    @Override public double movementSpeed() { return 0.23; }
    @Override public float eyeHeight() { return 0.95f * height; }
    @Override protected String livingSound() { return "mob.sheep.say"; }
    @Override protected String hurtSound() { return "mob.sheep.say"; }
    @Override protected String deathSound() { return "mob.sheep.say"; }
    @Override protected String stepSound() { return "mob.sheep.step"; }
    @Override protected Set<String> breedingItems() { return FOOD; }
    @Override protected EntityAnimal createChild(double x, double y, double z) { return new EntitySheep(world, x, y, z); }

    @Override public void onInitialSpawn() { fleeceColor = randomColor(rand); }

    /** EntitySheep.getRandomSheepColor. */
    public static int randomColor(Random random) {
        int i = random.nextInt(100);
        if (i < 5) return 15;
        if (i < 10) return 7;
        if (i < 15) return 8;
        if (i < 18) return 12;
        return random.nextInt(500) == 0 ? 6 : 0;
    }

    /**
     * EntitySheep.getDyeColorMixFromParents: the colour two dyes of the
     * parents' colours craft into, or either parent's at random.
     */
    public static int mixedColor(int a, int b, Random random) {
        int lo = Math.min(a, b), hi = Math.max(a, b);
        int mixed = switch (lo * 16 + hi) {
            case 0 * 16 + 14 -> 6;   // white + red: pink
            case 0 * 16 + 11 -> 3;   // white + blue: light blue
            case 0 * 16 + 13 -> 5;   // white + green: lime
            case 0 * 16 + 15 -> 7;   // white + black: grey
            case 0 * 16 + 7 -> 8;    // white + grey: light grey
            case 4 * 16 + 14 -> 1;   // yellow + red: orange
            case 11 * 16 + 13 -> 9;  // blue + green: cyan
            case 11 * 16 + 14 -> 10; // blue + red: purple
            case 6 * 16 + 10 -> 2;   // pink + purple: magenta
            default -> -1;
        };
        if (mixed >= 0) return mixed;
        return random.nextBoolean() ? a : b;
    }

    @Override
    protected void livingUpdate() {
        sheepTimer = eatGrass.timer;
        super.livingUpdate();
    }

    /** EntitySheep.getHeadRotationPointY: how far the head has dropped (0 up, 1 down) while grazing. */
    public float headRotationPointY(float partialTicks) {
        if (sheepTimer <= 0) return 0f;
        if (sheepTimer >= 4 && sheepTimer <= 36) return 1f;
        return sheepTimer < 4 ? (sheepTimer - partialTicks) / 4f : -(sheepTimer - 40 - partialTicks) / 4f;
    }

    /** EntitySheep.getHeadRotationAngleX: the head nods while it chews, otherwise it follows the pitch. */
    public float headRotationAngleX(float partialTicks, float pitchRadians) {
        if (sheepTimer > 4 && sheepTimer <= 36) {
            float f = (sheepTimer - 4 - partialTicks) / 32f;
            return (float) Math.PI / 5f + (float) Math.PI * 7f / 100f * (float) Math.sin(f * 28.7f);
        }
        return sheepTimer > 0 ? (float) Math.PI / 5f : pitchRadians;
    }

    /** EntitySheep.eatGrassBonus: the wool grows back and a lamb grows a minute. */
    void eatGrassBonus() {
        sheared = false;
        if (isChild()) ageUp(60, false);
    }

    /**
     * EntitySheep.interact: shears take 1-3 wool (popping up from 1 block
     * up) from an unsheared adult and wear by 1; ItemDye recolours it.
     */
    @Override
    public boolean interact(Interactor game) {
        ItemStack held = game.held();
        if (held != null && held.item().name().path().equals("shears") && !sheared && !isChild()) {
            sheared = true;
            int count = 1 + rand.nextInt(3);
            for (int i = 0; i < count; i++) {
                EntityItem drop = dropItem(WOOL[fleeceColor] + "_wool", 1, 1f);
                if (drop != null) {
                    drop.motionY += rand.nextFloat() * 0.05f;
                    drop.motionX += (rand.nextFloat() - rand.nextFloat()) * 0.1f;
                    drop.motionZ += (rand.nextFloat() - rand.nextFloat()) * 0.1f;
                }
            }
            game.damageHeld(1);
            playSound("mob.sheep.shear", 1f, 1f);
            return true;
        }
        if (held != null && held.item().vanillaId().path().equals("dye")) {
            // ItemDye.itemInteractionForEntity: wool metadata is 15 minus the dye's damage.
            int color = 15 - (held.item().vanillaData() & 15);
            if (!sheared && fleeceColor != color) {
                fleeceColor = color;
                game.consumeHeld();
                return true;
            }
        }
        return super.interact(game);
    }

    @Override
    protected void dropFewItems(boolean byPlayer, int looting) {
        if (!sheared) dropItem(WOOL[fleeceColor] + "_wool", 1);
        int mutton = rand.nextInt(2) + 1 + rand.nextInt(1 + looting);
        for (int i = 0; i < mutton; i++) dropItem(meat("mutton", "cooked_mutton"), 1);
    }

    @Override
    public void writeNbt(net.minecraft.nbt.NbtTag.Compound tag) {
        super.writeNbt(tag);
        tag.put("Sheared", new net.minecraft.nbt.NbtTag.Byte((byte) (sheared ? 1 : 0)));
        tag.put("Color", new net.minecraft.nbt.NbtTag.Byte((byte) fleeceColor));
    }

    @Override
    public void readNbt(net.minecraft.nbt.NbtTag.Compound tag) {
        super.readNbt(tag);
        if (tag.get("Sheared") instanceof net.minecraft.nbt.NbtTag.Byte s) sheared = s.value() != 0;
        if (tag.get("Color") instanceof net.minecraft.nbt.NbtTag.Byte c) fleeceColor = c.value() & 15;
    }

    /**
     * EntityAIEatGrass: 1 check in 1000 (lambs 50), standing in tall grass
     * or on a grass block, stop for 40 ticks; at tick 4 from the end eat it:
     * the tall grass breaks or the grass block turns to dirt with the break
     * effect (only if mobGriefing allows), and the wool regrows.
     */
    static final class EatGrass extends EntityAI {
        private final EntitySheep sheep;
        int timer;
        EatGrass(EntitySheep sheep) {
            this.sheep = sheep;
            mutexBits = 7;
        }
        @Override public boolean shouldExecute() {
            if (sheep.getRNG().nextInt(sheep.isChild() ? 50 : 1000) != 0) return false;
            int bx = floor(sheep.x()), by = floor(sheep.y()), bz = floor(sheep.z());
            return isTallGrass(bx, by, bz) || isGrass(bx, by - 1, bz);
        }
        @Override public void startExecuting() {
            timer = 40;
            sheep.navigator().clearPath();
        }
        @Override public void resetTask() { timer = 0; }
        @Override public boolean continueExecuting() { return timer > 0; }
        @Override public void updateTask() {
            timer = Math.max(0, timer - 1);
            if (timer != 4) return;
            int bx = floor(sheep.x()), by = floor(sheep.y()), bz = floor(sheep.z());
            boolean griefing = sheep.host() != null && sheep.host().gameRule("mobGriefing");
            World world = sheep.world();
            if (isTallGrass(bx, by, bz)) {
                if (griefing && sheep.host() != null) sheep.host().breakBlock(bx, by, bz, false);
                sheep.eatGrassBonus();
            } else if (isGrass(bx, by - 1, bz)) {
                if (griefing && sheep.host() != null) {
                    sheep.host().blockBreakEffects(bx, by - 1, bz, world.getState(bx, by - 1, bz));
                    sheep.host().setBlock(bx, by - 1, bz, "dirt", 0);
                }
                sheep.eatGrassBonus();
            }
        }
        private boolean isTallGrass(int bx, int by, int bz) {
            if (by < 0 || by >= World.HEIGHT) return false;
            var state = sheep.world().getState(bx, by, bz);
            return state.block().name().path().equals("tallgrass") && state.metadata() == 1;
        }
        private boolean isGrass(int bx, int by, int bz) {
            return by >= 0 && by < World.HEIGHT && sheep.world().get(bx, by, bz).name().path().equals("grass");
        }
        private static int floor(double value) { return (int) Math.floor(value); }
    }
}
