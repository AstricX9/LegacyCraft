package net.minecraft.entity.boss;

import net.minecraft.entity.DamageSource;
import net.minecraft.entity.EntityEnderCrystal;
import net.minecraft.testing.GameData;
import net.minecraft.world.DragonFight;
import net.minecraft.world.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The 1.9 ender dragon, its crystals and the fight's saved state (HYPOTHESIS: recalled rules). */
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
    void fatalHitInFlightStartsTheDeathPhase() {
        EntityDragon dragon = new EntityDragon(GameData.newWorld(1L), 0, 100, 0);
        dragon.health = 2f;
        dragon.hitPart = EntityDragon.HEAD;
        dragon.attackEntityFrom(DamageSource.PLAYER, 50f, null);
        assertEquals(EntityDragon.Phase.DYING, dragon.phase);
        assertEquals(1f, dragon.health, 1e-4);
    }

    @Test
    void onlyPlayerArrowAndExplosionDamageCounts() {
        EntityDragon dragon = new EntityDragon(GameData.newWorld(1L), 0, 100, 0);
        assertFalse(dragon.attackEntityFrom(DamageSource.IN_FIRE, 10f, null));
        assertEquals(200f, dragon.health);
    }

    @Test
    void breakingTheHealingCrystalHurtsTheDragon() {
        World world = GameData.newWorld(1L);
        EntityDragon dragon = new EntityDragon(world, 0, 100, 0);
        EntityEnderCrystal crystal = new EntityEnderCrystal(world, 0, 90, 0);
        dragon.healingCrystal = crystal;
        dragon.crystalDestroyed(crystal);
        assertNull(dragon.healingCrystal);
        assertTrue(dragon.health < 200f);
    }

    @Test
    void partsCoverTheBodyWingsAndHead() {
        EntityDragon dragon = new EntityDragon(GameData.newWorld(1L), 0, 100, 0);
        assertEquals(8, dragon.partBoxes().length);
        assertEquals(8.0, dragon.partBox(EntityDragon.BODY).maxX() - dragon.partBox(EntityDragon.BODY).minX(), 1e-6);
    }

    @Test
    void fightStateSurvivesASave(@TempDir Path dir) throws Exception {
        DragonFight fight = new DragonFight(42L);
        fight.dragonKilled = true;
        fight.previouslyKilled = true;
        fight.gateways.remove(0);
        fight.save(dir);
        DragonFight loaded = DragonFight.load(dir, 42L);
        assertTrue(loaded.dragonKilled);
        assertTrue(loaded.previouslyKilled);
        assertEquals(fight.gateways, loaded.gateways);
        assertEquals(new DragonFight(42L).gateways, new DragonFight(42L).gateways);
    }

    @Test
    void twentyGatewaySlotsRingTheIsland() {
        Set<String> seen = new HashSet<>();
        for (int slot = 0; slot < 20; slot++) {
            int[] p = DragonFight.gatewayPosition(slot);
            assertEquals(75, p[1]);
            assertEquals(96.0, Math.sqrt(p[0] * p[0] + p[2] * p[2]), 1.5);
            seen.add(p[0] + "," + p[2]);
        }
        assertEquals(20, seen.size());
    }
}
