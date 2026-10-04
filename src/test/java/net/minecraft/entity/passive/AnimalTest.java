package net.minecraft.entity.passive;

import net.minecraft.block.BlockState;
import net.minecraft.client.entity.Player;
import net.minecraft.entity.DamageSource;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityHost;
import net.minecraft.entity.EntityItem;
import net.minecraft.entity.EntityXPOrb;
import net.minecraft.entity.MovingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.testing.GameData;
import net.minecraft.world.MobSpawner;
import net.minecraft.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Farm animals on a grass platform high above the terrain (HYPOTHESIS: recalled 1.8.9 rules). */
final class AnimalTest {
    private static final int FLOOR = 200;
    private World world;
    private FakeHost host;

    private static final class FakeHost implements EntityHost {
        final List<Entity> entities = new ArrayList<>();
        final List<String> sounds = new ArrayList<>();
        final List<String> achievements = new ArrayList<>();
        Player player;
        @Override public Player player() { return player; }
        @Override public List<Entity> entities() { return new ArrayList<>(entities); }
        @Override public void spawn(Entity entity) {
            if (entity instanceof MovingEntity moving) moving.setHost(this);
            entities.add(entity);
        }
        @Override public void playSound(String event, double x, double y, double z, float volume, float pitch) { sounds.add(event); }
        @Override public void particle(String kind, double x, double y, double z, double vx, double vy, double vz) {}
        @Override public void explode(Entity exploder, double x, double y, double z, float size, boolean smoking, String attacker) {}
        @Override public int difficulty() { return 2; }
        @Override public boolean gameRule(String name) { return true; }
        @Override public long worldTime() { return 6000; }
        @Override public void achievement(String id) { achievements.add(id); }

        List<String> drops() {
            List<String> names = new ArrayList<>();
            for (Entity entity : entities)
                if (entity instanceof EntityItem item)
                    for (int i = 0; i < item.stack().count(); i++) names.add(item.stack().item().name().path());
            return names;
        }
    }

    /** A hand holding one stack, as the game would offer it. */
    private static final class Hand implements EntityAnimal.Interactor {
        ItemStack held;
        boolean creative;
        Hand(ItemStack held) { this.held = held; }
        @Override public ItemStack held() { return held; }
        @Override public boolean creative() { return creative; }
        @Override public void consumeHeld() {
            if (creative) return;
            held.split(1);
            if (held.isEmpty()) held = null;
        }
        @Override public void exchangeHeld(ItemStack result) { held = result; }
        @Override public void damageHeld(int amount) {}
        @Override public ItemStack stack(String name) { return new ItemStack(GameData.items().get("minecraft:" + name)); }
        @Override public void awardAchievement(String id) {}
    }

    private static ItemStack stack(String name, int count) { return new ItemStack(GameData.items().get("minecraft:" + name), count); }

    @BeforeEach
    void platform() {
        world = GameData.newWorld(9L);
        world.setSmelting(null, name -> GameData.items().get(new net.minecraft.util.ResourceLocation("minecraft", name)));
        BlockState grass = BlockState.defaultOf(GameData.blocks().get("minecraft:grass"));
        for (int x = -10; x <= 10; x++) for (int z = -10; z <= 10; z++) world.setState(x, FLOOR, z, grass);
        host = new FakeHost();
    }

    private <T extends EntityAnimal> T add(T animal) {
        host.spawn(animal);
        return animal;
    }

    @Test
    void attributesMatchVanilla() {
        EntityPig pig = new EntityPig(world, 0.5, FLOOR + 1, 0.5);
        EntityCow cow = new EntityCow(world, 0.5, FLOOR + 1, 0.5);
        EntitySheep sheep = new EntitySheep(world, 0.5, FLOOR + 1, 0.5);
        EntityChicken chicken = new EntityChicken(world, 0.5, FLOOR + 1, 0.5);
        assertEquals(10f, pig.health);
        assertEquals(10f, cow.health);
        assertEquals(8f, sheep.health);
        assertEquals(4f, chicken.health);
        assertEquals(0.9f, cow.width, 1e-6);
        assertEquals(1.3f, cow.height, 1e-6);
        assertEquals(0.7f, chicken.eyeHeight(), 1e-6);
    }

    @Test
    void killedAdultsDropMeatCookedWhenBurning() {
        EntityPig pig = add(new EntityPig(world, 0.5, FLOOR + 1, 0.5));
        pig.attackEntityFrom(DamageSource.GENERIC, 100f, null);
        long chops = host.drops().stream().filter("porkchop"::equals).count();
        assertTrue(chops >= 1 && chops <= 3, "1-3 porkchops, got " + chops);
        host.entities.clear();
        EntityCow cow = add(new EntityCow(world, 0.5, FLOOR + 1, 0.5));
        cow.setFire(5);
        cow.attackEntityFrom(DamageSource.GENERIC, 100f, null);
        assertTrue(host.drops().contains("cooked_beef"));
        assertFalse(host.drops().contains("beef"));
    }

    @Test
    void babiesDropNothingAndAreHalfSize() {
        EntitySheep lamb = add(new EntitySheep(world, 0.5, FLOOR + 1, 0.5));
        lamb.setGrowingAge(-24000);
        assertTrue(lamb.isChild());
        assertEquals(0.45f, lamb.width, 1e-6);
        lamb.attackEntityFrom(DamageSource.GENERIC, 100f, null);
        assertTrue(host.drops().isEmpty());
    }

    @Test
    void feedingTwoAdultsMakesABaby() {
        EntityCow a = add(new EntityCow(world, 0.5, FLOOR + 1, 0.5));
        EntityCow b = add(new EntityCow(world, 1.5, FLOOR + 1, 0.5));
        Hand hand = new Hand(stack("wheat", 2));
        assertTrue(a.interact(hand));
        assertTrue(b.interact(hand));
        assertNull(hand.held, "both wheat used up");
        assertTrue(a.isInLove() && b.isInLove());
        for (int i = 0; i < 400 && host.entities.stream().noneMatch(e -> e instanceof EntityCow cow && cow.isChild()); i++) {
            a.tick();
            b.tick();
        }
        EntityCow calf = (EntityCow) host.entities.stream().filter(e -> e instanceof EntityCow cow && cow.isChild()).findFirst().orElseThrow();
        assertEquals(-24000, calf.growingAge());
        assertEquals(6000, Math.max(a.growingAge(), b.growingAge()), 1);
        assertTrue(host.entities.stream().anyMatch(e -> e instanceof EntityXPOrb));
        assertTrue(host.achievements.contains("breedCow"));
    }

    @Test
    void feedingABabySpeedsItUpByATenth() {
        EntityPig piglet = add(new EntityPig(world, 0.5, FLOOR + 1, 0.5));
        piglet.setGrowingAge(-24000);
        assertTrue(piglet.interact(new Hand(stack("carrot", 1))));
        assertEquals(-24000 + 120 * 20, piglet.growingAge());
    }

    @Test
    void creativeFeedingKeepsTheFood() {
        EntityChicken chicken = add(new EntityChicken(world, 0.5, FLOOR + 1, 0.5));
        Hand hand = new Hand(stack("wheat_seeds", 1));
        hand.creative = true;
        assertTrue(chicken.interact(hand));
        assertEquals(1, hand.held.count());
    }

    @Test
    void cowsGiveMilkOnlyInSurvival() {
        EntityCow cow = add(new EntityCow(world, 0.5, FLOOR + 1, 0.5));
        Hand hand = new Hand(stack("bucket", 1));
        assertTrue(cow.interact(hand));
        assertEquals("milk_bucket", hand.held.item().name().path());
        Hand creative = new Hand(stack("bucket", 1));
        creative.creative = true;
        assertFalse(cow.interact(creative));
    }

    @Test
    void shearingTakesWoolOnceAndDyeRecolours() {
        EntitySheep sheep = add(new EntitySheep(world, 0.5, FLOOR + 1, 0.5));
        sheep.fleeceColor = 0;
        assertTrue(sheep.interact(new Hand(stack("dye_red", 1))));
        assertEquals(14, sheep.fleeceColor);
        assertTrue(sheep.interact(new Hand(stack("shears", 1))));
        assertTrue(sheep.sheared);
        long wool = host.drops().stream().filter("red_wool"::equals).count();
        assertTrue(wool >= 1 && wool <= 3);
        assertFalse(sheep.interact(new Hand(stack("shears", 1))), "no wool left");
        assertTrue(host.sounds.contains("mob.sheep.shear"));
    }

    @Test
    void lambColoursMixLikeDyes() {
        Random random = new Random(1);
        assertEquals(6, EntitySheep.mixedColor(0, 14, random), "white + red = pink");
        assertEquals(1, EntitySheep.mixedColor(14, 4, random), "red + yellow = orange");
        int mixed = EntitySheep.mixedColor(12, 15, random);
        assertTrue(mixed == 12 || mixed == 15, "no recipe: one parent");
    }

    @Test
    void chickensFallSlowlyWithoutDamage() {
        EntityChicken chicken = add(new EntityChicken(world, 0.5, FLOOR + 30, 0.5));
        for (int i = 0; i < 400 && !chicken.onGround; i++) chicken.tick();
        assertTrue(chicken.onGround);
        assertEquals(4f, chicken.health, 1e-6);
    }

    @Test
    void adultChickensLayEggs() {
        EntityChicken chicken = add(new EntityChicken(world, 0.5, FLOOR + 1, 0.5));
        chicken.timeUntilNextEgg = 2;
        chicken.tick();
        chicken.tick();
        assertTrue(host.drops().contains("egg"));
        assertTrue(host.sounds.contains("mob.chicken.plop"));
    }

    @Test
    void biomeCreatureLists() {
        assertTrue(MobSpawner.creatures(0).isEmpty(), "ocean");
        assertTrue(MobSpawner.creatures(2).isEmpty(), "desert");
        assertEquals(List.of("Sheep", "Pig", "Chicken", "Cow", "EntityHorse"),
            MobSpawner.creatures(1).stream().map(MobSpawner.SpawnEntry::entity).toList());
        assertEquals(20, MobSpawner.creatures(21).stream().filter(e -> e.entity().equals("Chicken")).mapToInt(MobSpawner.SpawnEntry::weight).sum());
    }

    @Test
    void naturalFleeceIsMostlyWhite() {
        Random random = new Random(3);
        int white = 0;
        for (int i = 0; i < 10000; i++) if (EntitySheep.randomColor(random) == 0) white++;
        assertTrue(white > 7900 && white < 8450, "about 81.8% white, got " + white);
    }

    @Test
    void savedAnimalsKeepTheirState() {
        EntitySheep sheep = new EntitySheep(world, 0.5, FLOOR + 1, 0.5);
        sheep.fleeceColor = 11;
        sheep.sheared = true;
        sheep.setGrowingAge(-100);
        net.minecraft.nbt.NbtTag.Compound tag = new net.minecraft.nbt.NbtTag.Compound();
        sheep.writeNbt(tag);
        EntitySheep loaded = (EntitySheep) net.minecraft.entity.EntityList.fromNbt(tag, world);
        assertEquals(11, loaded.fleeceColor);
        assertTrue(loaded.sheared);
        assertEquals(-100, loaded.growingAge());
        assertTrue(loaded.isChild());
    }
}
