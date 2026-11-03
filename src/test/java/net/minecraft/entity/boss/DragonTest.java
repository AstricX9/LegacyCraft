package net.minecraft.entity.boss;

import net.minecraft.entity.DamageSource;
import net.minecraft.testing.GameData;
import net.minecraft.world.Dimension;
import net.minecraft.world.EndPodium;
import net.minecraft.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The 1.8.9 ender dragon's damage rules, its parts and the exit portal it leaves (HYPOTHESIS: recalled rules). */
final class DragonTest {
    @Test
    void hitsAwayFromTheHeadDoAQuarterPlusOne() {
        EntityDragon dragon = new EntityDragon(GameData.newWorld(1L), 0, 100, 0);
        assertEquals(200f, dragon.health);
        dragon.hitPart = EntityDragon.BODY;
        assertTrue(dragon.attackEntityFrom(DamageSource.PLAYER, 8f, null));
        assertEquals(200f - (8f / 4f + 1f), dragon.health, 1e-4);
        dragon.hurtResistantTime = 0;
        dragon.hitPart = EntityDragon.HEAD;
        assertTrue(dragon.attackEntityFrom(DamageSource.PLAYER, 8f, null));
        assertEquals(197f - 8f, dragon.health, 1e-4);
    }

    @Test
    void aHitSendsItOffAheadOfItself() {
        EntityDragon dragon = new EntityDragon(GameData.newWorld(1L), 0, 100, 0);
        dragon.rotationYaw = 0f;
        dragon.hitPart = EntityDragon.HEAD;
        dragon.attackEntityFrom(DamageSource.PLAYER, 1f, null);
        assertNull(dragon.target);
        assertEquals(0.0, dragon.targetX, 1.01);
        assertEquals(-5.0, dragon.targetZ, 1.01);
        assertTrue(dragon.targetY >= 101 && dragon.targetY <= 104);
    }

    @Test
    void onlyPlayersAndExplosionsHurtIt() {
        EntityDragon dragon = new EntityDragon(GameData.newWorld(1L), 0, 100, 0);
        dragon.attackEntityFrom(DamageSource.IN_FIRE, 10f, null);
        assertEquals(200f, dragon.health);
        dragon.attackFromExplosion(8f, null, null);
        assertEquals(197f, dragon.health, 1e-4);
    }

    @Test
    void newTargetsStayOverTheIsland() {
        EntityDragon dragon = new EntityDragon(GameData.newWorld(1L), 0, 100, 0);
        for (int i = 0; i < 100; i++) {
            dragon.setNewTarget();
            assertNull(dragon.target, "no player to chase");
            assertTrue(Math.abs(dragon.targetX) <= 60 && Math.abs(dragon.targetZ) <= 60);
            assertTrue(dragon.targetY >= 70 && dragon.targetY <= 120);
        }
    }

    @Test
    void sevenPartsHeadFirst() {
        EntityDragon dragon = new EntityDragon(GameData.newWorld(1L), 0, 100, 0);
        assertEquals(7, dragon.partBoxes().length);
        assertEquals(5.0, dragon.partBox(EntityDragon.BODY).maxX() - dragon.partBox(EntityDragon.BODY).minX(), 1e-6);
    }

    @Test
    void theExitPortalHasItsPillarTorchesAndEgg() {
        World end = new World(GameData.blocks(), GameData.biomes(), 42L, null, Dimension.END);
        EndPodium.generatePortal(end, 10, 64, -5);
        assertEquals("end_portal", end.get(12, 64, -5).name().path());
        assertEquals("bedrock", end.get(13, 64, -5).name().path());
        assertEquals("bedrock", end.get(10, 63, -5).name().path());
        for (int y = 64; y < 68; y++) assertEquals("bedrock", end.get(10, y, -5).name().path());
        assertEquals("torch", end.get(11, 66, -5).name().path());
        assertEquals("dragon_egg", end.get(10, 68, -5).name().path());
        assertTrue(end.get(12, 70, -5).isAir());
    }
}
