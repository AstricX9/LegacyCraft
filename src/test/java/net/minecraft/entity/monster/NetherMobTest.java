package net.minecraft.entity.monster;

import net.minecraft.block.BlockState;
import net.minecraft.client.entity.Player;
import net.minecraft.entity.DamageSource;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityHost;
import net.minecraft.entity.MovingEntity;
import net.minecraft.testing.GameData;
import net.minecraft.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Zombie pigmen, magma cubes, skeletons, blazes and ghasts on a platform (HYPOTHESIS: recalled 1.8.9 rules, DEV-036). */
final class NetherMobTest {
    private static final int FLOOR = 200;
    private World world;
    private FakeHost host;

    private static final class FakeHost implements EntityHost {
        final List<Entity> entities = new ArrayList<>();
        final List<String> sounds = new ArrayList<>();
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
        @Override public long worldTime() { return 18000; }
    }

    @BeforeEach
    void platform() {
        world = GameData.newWorld(5L);
        BlockState stone = BlockState.defaultOf(GameData.blocks().get("minecraft:stone"));
        for (int x = -16; x <= 16; x++) for (int z = -16; z <= 16; z++) world.setState(x, FLOOR, z, stone);
        host = new FakeHost();
    }

    @Test
    void hittingAPigmanAngersTheOnesNearby() {
        EntityPigZombie hit = new EntityPigZombie(world, 0.5, FLOOR + 1, 0.5);
        EntityPigZombie near = new EntityPigZombie(world, 10.5, FLOOR + 1, 0.5);
        host.spawn(hit);
        host.spawn(near);
        assertFalse(hit.isAngry());
        Player player = new Player(world);
        hit.attackEntityFrom(DamageSource.PLAYER, 1f, player);
        assertTrue(hit.isAngry());
        assertTrue(near.isAngry());
        assertTrue(hit.angerLevel >= 400 && hit.angerLevel < 800);
    }

    @Test
    void pigmenArePeacefulUntilHitAndFireproof() {
        EntityPigZombie pigman = new EntityPigZombie(world, 0.5, FLOOR + 1, 0.5);
        assertTrue(pigman.isImmuneToFire());
        assertEquals(20f, pigman.maxHealth());
        assertFalse(pigman.isAngry());
    }

    @Test
    void magmaCubeSizesSetHealthAndBox() {
        EntityMagmaCube cube = new EntityMagmaCube(world, 0.5, FLOOR + 1, 0.5);
        cube.setSize(4);
        assertEquals(16f, cube.maxHealth());
        assertEquals(0.51f * 4, cube.width, 1e-3);
        assertEquals(4, cube.size());
        assertEquals("LavaSlime", cube.entityId());
    }

    @Test
    void bigMagmaCubeSplitsIntoHalves() {
        EntityMagmaCube cube = new EntityMagmaCube(world, 0.5, FLOOR + 1, 0.5);
        cube.setSize(4);
        host.spawn(cube);
        cube.attackEntityFrom(DamageSource.GENERIC, 100f, null);
        for (int i = 0; i < 20; i++) cube.tick();
        assertTrue(cube.isRemoved());
        long children = host.entities.stream().filter(e -> e instanceof EntityMagmaCube c && c != cube && c.size() == 2).count();
        assertTrue(children >= 2 && children <= 4, "2-4 cubes of size 2, got " + children);
    }

    @Test
    void witherSkeletonIsBiggerAndFireproof() {
        EntitySkeleton skeleton = new EntitySkeleton(world, 0.5, FLOOR + 1, 0.5);
        assertFalse(skeleton.isImmuneToFire());
        skeleton.setSkeletonType(1);
        assertTrue(skeleton.isImmuneToFire());
        assertEquals(0.72f, skeleton.width, 1e-6);
        assertEquals(2.535f, skeleton.height, 1e-6);
    }

    @Test
    void blazeAndGhastBasics() {
        EntityBlaze blaze = new EntityBlaze(world, 0.5, FLOOR + 1, 0.5);
        EntityGhast ghast = new EntityGhast(world, 0.5, FLOOR + 10, 0.5);
        assertTrue(blaze.isImmuneToFire());
        assertTrue(ghast.isImmuneToFire());
        assertEquals(10f, ghast.maxHealth());
        assertEquals(4f, ghast.width);
        assertTrue(ghast instanceof IMob);
    }
}
