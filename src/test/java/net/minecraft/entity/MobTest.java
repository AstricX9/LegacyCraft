package net.minecraft.entity;

import net.minecraft.block.BlockState;
import net.minecraft.client.entity.Player;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.testing.GameData;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Creepers, lit TNT, explosions and orbs on a stone platform high above the terrain (HYPOTHESIS: recalled 1.8.9 rules). */
final class MobTest {
    private static final int FLOOR = 200;
    private World world;
    private FakeHost host;

    /** Records what entities ask of the game. */
    private static final class FakeHost implements EntityHost {
        final List<Entity> entities = new ArrayList<>();
        final List<Object[]> explosions = new ArrayList<>();
        final List<String> sounds = new ArrayList<>();
        Player player;
        int difficulty = 2;
        @Override public Player player() { return player; }
        @Override public List<Entity> entities() { return new ArrayList<>(entities); }
        @Override public void spawn(Entity entity) {
            if (entity instanceof MovingEntity moving) moving.setHost(this);
            entities.add(entity);
        }
        @Override public void playSound(String event, double x, double y, double z, float volume, float pitch) { sounds.add(event); }
        @Override public void particle(String kind, double x, double y, double z, double vx, double vy, double vz) {}
        @Override public void explode(Entity exploder, double x, double y, double z, float size, boolean smoking, String attacker) {
            explosions.add(new Object[] {exploder, x, y, z, size, smoking, attacker});
        }
        @Override public int difficulty() { return difficulty; }
        @Override public boolean gameRule(String name) { return true; }
        @Override public long worldTime() { return 18000; }
    }

    @BeforeEach
    void platform() {
        world = GameData.newWorld(5L);
        BlockState stone = BlockState.defaultOf(GameData.blocks().get("minecraft:stone"));
        for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++) world.setState(x, FLOOR, z, stone);
        host = new FakeHost();
    }

    @Test
    void explosionResistanceIsTheLargerOfHardnessAndSetResistance() {
        assertEquals(6f, Explosion.resistance(GameData.blocks().get("minecraft:stone")), 1e-4);
        assertEquals(1200f, Explosion.resistance(GameData.blocks().get("minecraft:obsidian")), 1e-2);
        assertEquals(0.5f, Explosion.resistance(GameData.blocks().get("minecraft:dirt")), 1e-4);
        assertEquals(60f, Explosion.resistance(GameData.blocks().get("minecraft:water")), 1e-4);
    }

    @Test
    void tntBreaksStoneButNotObsidian() {
        BlockState obsidian = BlockState.defaultOf(GameData.blocks().get("minecraft:obsidian"));
        world.setState(2, FLOOR + 1, 0, obsidian);
        Explosion explosion = new Explosion(0.5, FLOOR + 1.06, 0.5, 4f);
        explosion.computeAffectedBlocks(world, new java.util.Random(1));
        boolean stone = false, obsidianHit = false;
        for (int[] pos : explosion.affectedBlocks()) {
            if (pos[0] == 0 && pos[1] == FLOOR && pos[2] == 0) stone = true;
            if (pos[0] == 2 && pos[1] == FLOOR + 1 && pos[2] == 0) obsidianHit = true;
        }
        assertTrue(stone, "the stone under the TNT goes");
        assertFalse(obsidianHit, "obsidian survives");
    }

    @Test
    void litTntExplodesAfterEightyTicksWithStrengthFour() {
        EntityTNTPrimed tnt = new EntityTNTPrimed(world, 0.5, FLOOR + 1, 0.5, null);
        host.spawn(tnt);
        for (int i = 0; i < 80; i++) tnt.tick();
        assertTrue(host.explosions.isEmpty());
        tnt.tick();
        assertEquals(1, host.explosions.size());
        assertEquals(4f, (float) host.explosions.get(0)[4]);
        assertTrue((boolean) host.explosions.get(0)[5]);
        assertTrue(tnt.isRemoved());
    }

    @Test
    void ignitedCreeperBlowsUpAfterThirtyTicks() {
        EntityCreeper creeper = new EntityCreeper(world, 0.5, FLOOR + 1, 0.5);
        host.spawn(creeper);
        creeper.ignite();
        int ticks = 0;
        while (!creeper.isRemoved() && ticks < 100) {
            creeper.tick();
            ticks++;
        }
        assertEquals(1, host.explosions.size());
        assertEquals(3f, (float) host.explosions.get(0)[4]);
        assertEquals("entity.Creeper.name", host.explosions.get(0)[6]);
        assertTrue(ticks >= 30 && ticks <= 32, "fuse of 30 ticks, took " + ticks);
        assertTrue(host.sounds.contains("creeper.primed"));
    }

    @Test
    void creepersVanishInPeaceful() {
        host.difficulty = 0;
        EntityCreeper creeper = new EntityCreeper(world, 0.5, FLOOR + 1, 0.5);
        host.spawn(creeper);
        creeper.tick();
        assertTrue(creeper.isRemoved());
    }

    @Test
    void hitsFlashRedAndOnlyBiggerHitsGetThroughTheProtection() {
        EntityCreeper creeper = new EntityCreeper(world, 0.5, FLOOR + 1, 0.5);
        host.spawn(creeper);
        assertTrue(creeper.attackEntityFrom(DamageSource.GENERIC, 5f, null));
        assertEquals(15f, creeper.health, 1e-6);
        assertEquals(10, creeper.hurtTime);
        assertFalse(creeper.attackEntityFrom(DamageSource.GENERIC, 4f, null));
        assertTrue(creeper.attackEntityFrom(DamageSource.GENERIC, 7f, null));
        assertEquals(13f, creeper.health, 1e-6, "only the 2 above the last hit");
    }

    @Test
    void deadCreeperDisappearsAfterTwentyTicks() {
        EntityCreeper creeper = new EntityCreeper(world, 0.5, FLOOR + 1, 0.5);
        host.spawn(creeper);
        creeper.attackEntityFrom(DamageSource.GENERIC, 100f, null);
        assertEquals(0f, creeper.health);
        for (int i = 0; i < 19; i++) creeper.tick();
        assertFalse(creeper.isRemoved());
        creeper.tick();
        assertTrue(creeper.isRemoved());
        assertTrue(host.sounds.contains("mob.creeper.death"));
    }

    @Test
    void creeperFallsAndLandsOnThePlatform() {
        EntityCreeper creeper = new EntityCreeper(world, 0.5, FLOOR + 3, 0.5);
        host.spawn(creeper);
        for (int i = 0; i < 60; i++) creeper.tick();
        assertEquals(FLOOR + 1, creeper.y(), 1e-6);
        assertTrue(creeper.onGround);
        assertEquals(20f, creeper.health, 1e-6, "a 2-block fall does no damage");
    }

    @Test
    void experienceSplitsIntoVanillaOrbSizes() {
        assertEquals(3, EntityXPOrb.split(5));
        assertEquals(1, EntityXPOrb.split(2));
        assertEquals(2477, EntityXPOrb.split(5000));
    }
}
