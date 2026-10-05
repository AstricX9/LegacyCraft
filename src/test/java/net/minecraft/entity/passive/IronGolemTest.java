package net.minecraft.entity.passive;

import net.minecraft.block.BlockState;
import net.minecraft.client.entity.Player;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityHost;
import net.minecraft.entity.MovingEntity;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.testing.GameData;
import net.minecraft.world.GolemPattern;
import net.minecraft.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Building iron golems and what they do (HYPOTHESIS: recalled, DEV-039). */
final class IronGolemTest {
    private static final int FLOOR = 200;
    private World world;
    private final List<Entity> entities = new ArrayList<>();
    private final EntityHost host = new EntityHost() {
        @Override public Player player() { return null; }
        @Override public List<Entity> entities() { return new ArrayList<>(entities); }
        @Override public void spawn(Entity entity) {
            if (entity instanceof MovingEntity moving) moving.setHost(this);
            entities.add(entity);
        }
        @Override public void playSound(String event, double x, double y, double z, float volume, float pitch) {}
        @Override public void particle(String kind, double x, double y, double z, double vx, double vy, double vz) {}
        @Override public void explode(Entity exploder, double x, double y, double z, float size, boolean smoking, String attacker) {}
        @Override public int difficulty() { return 2; }
        @Override public boolean gameRule(String name) { return true; }
        @Override public long worldTime() { return 6000; }
    };

    @BeforeEach
    void platform() {
        world = GameData.newWorld(5L);
        BlockState stone = BlockState.defaultOf(GameData.blocks().get("minecraft:stone"));
        for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++) world.setState(x, FLOOR, z, stone);
        world.setSmelting(null, name -> GameData.items().get(name));
    }

    private void set(int x, int y, int z, String block) {
        world.setState(x, y, z, BlockState.defaultOf(GameData.blocks().get("minecraft:" + block)));
    }

    private void buildT(boolean alongX, String head) {
        int ax = alongX ? 1 : 0, az = alongX ? 0 : 1;
        set(0, FLOOR + 1, 0, "iron_block");
        set(0, FLOOR + 2, 0, "iron_block");
        set(-ax, FLOOR + 2, -az, "iron_block");
        set(ax, FLOOR + 2, az, "iron_block");
        set(0, FLOOR + 3, 0, head);
    }

    @Test
    void aPumpkinOnAnIronTMakesAGolemEitherWayRound() {
        buildT(true, "pumpkin");
        GolemPattern.Match match = GolemPattern.find(world, 0, FLOOR + 3, 0);
        assertNotNull(match);
        assertTrue(match.alongX());
        assertArrayEquals(new int[] {0, FLOOR + 1, 0}, match.feet());
        platform();
        buildT(false, "lit_pumpkin");
        assertNotNull(GolemPattern.find(world, 0, FLOOR + 3, 0));
    }

    @Test
    void noGolemWithAnArmMissingOrSomethingBesideTheLegs() {
        buildT(true, "pumpkin");
        set(1, FLOOR + 2, 0, "stone");
        assertNull(GolemPattern.find(world, 0, FLOOR + 3, 0));
        platform();
        buildT(true, "pumpkin");
        set(1, FLOOR + 1, 0, "dirt");
        assertNull(GolemPattern.find(world, 0, FLOOR + 3, 0));
    }

    @Test
    void huntsAndHitsTheNearestHostileMob() {
        EntityIronGolem golem = new EntityIronGolem(world, 0.5, FLOOR + 1, 0.5);
        EntityCreeper creeper = new EntityCreeper(world, 1.5, FLOOR + 1, 0.5);
        host.spawn(golem);
        host.spawn(creeper);
        float before = creeper.health;
        for (int i = 0; i < 40; i++) golem.tick();
        assertSame(creeper, golem.target());
        assertTrue(creeper.health < before, "the creeper was hit");
    }

    @Test
    void takesNoFallDamageAndDropsIronAndPoppies() {
        EntityIronGolem golem = new EntityIronGolem(world, 0.5, FLOOR + 30, 0.5);
        host.spawn(golem);
        for (int i = 0; i < 100; i++) golem.tick();
        assertEquals(100f, golem.health, 1e-6);
        golem.attackEntityFrom(net.minecraft.entity.DamageSource.GENERIC, 1000f, null);
        long iron = 0, poppies = 0;
        for (Entity e : entities)
            if (e instanceof net.minecraft.entity.EntityItem item) {
                String name = item.stack().item().name().path();
                if (name.equals("iron_ingot")) iron += item.stack().count();
                if (name.equals("red_flower")) poppies += item.stack().count();
            }
        assertTrue(iron >= 3 && iron <= 5, "iron: " + iron);
        assertTrue(poppies <= 2);
    }
}
