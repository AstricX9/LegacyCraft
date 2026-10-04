package net.minecraft.client.entity;

import net.minecraft.entity.DamageSource;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.testing.GameData;
import net.minecraft.util.FoodStats;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Hunger, saturation and effects (HYPOTHESIS: recalled 1.8.9 FoodStats, EntityLivingBase and Potion). */
final class PlayerFoodTest {
    private static Player player() { return new Player(GameData.newWorld(1L)); }

    @Test
    void freshBarIsFullWithFiveSaturation() {
        FoodStats food = new FoodStats();
        assertEquals(20, food.getFoodLevel());
        assertEquals(5f, food.getSaturationLevel());
        assertFalse(food.needFood());
    }

    @Test
    void eatingCapsFoodAtTwentyAndSaturationAtTheFoodLevel() {
        FoodStats food = new FoodStats();
        food.load(10, 0f, 0f, 0);
        food.addStats(8, 12.8f); // steak
        assertEquals(18, food.getFoodLevel());
        assertEquals(12.8f, food.getSaturationLevel(), 1e-6);
        food.addStats(4, 9.6f); // golden apple
        assertEquals(20, food.getFoodLevel());
        assertEquals(20f, food.getSaturationLevel(), 1e-6);
    }

    @Test
    void exhaustionEatsSaturationBeforeFood() {
        Player player = player();
        player.difficulty = 2;
        player.foodStats.load(20, 1f, 0f, 0);
        player.addExhaustion(4.5f);
        player.tick(Player.Input.NONE);
        assertEquals(0f, player.foodStats.getSaturationLevel(), 1e-6);
        assertEquals(20, player.foodLevel());
        player.addExhaustion(4.5f);
        player.tick(Player.Input.NONE);
        assertEquals(19, player.foodLevel());
    }

    @Test
    void creativePlayersNeitherTireNorEat() {
        Player player = player();
        player.disableDamage = true;
        player.addExhaustion(10f);
        assertEquals(0f, player.foodStats.getExhaustionLevel());
        player.foodStats.setFoodLevel(5);
        assertFalse(player.canEat(true));
    }

    @Test
    void sprintJumpingCostsPointEight() {
        Player player = player();
        player.difficulty = 2;
        player.foodStats.load(20, 5f, 0f, 0);
        player.addExhaustion(0.8f);
        assertEquals(0.8f, player.foodStats.getExhaustionLevel(), 1e-6);
    }

    @Test
    void starvationStopsAtHalfHealthOnEasyAndOneOnNormal() {
        for (int difficulty : new int[] {1, 2, 3}) {
            Player player = player();
            player.difficulty = difficulty;
            player.foodStats.load(0, 0f, 0f, 0);
            for (int i = 0; i < 80 * 40; i++) {
                player.tick(Player.Input.NONE);
                player.hurtResistantTime = 0;
            }
            float expected = difficulty == 1 ? 10f : difficulty == 2 ? 1f : 0f;
            assertEquals(expected, player.health, 1e-6, "difficulty " + difficulty);
        }
    }

    @Test
    void fullFoodHealsEveryFourSecondsForThreeExhaustion() {
        Player player = player();
        player.difficulty = 2;
        player.health = 10f;
        player.foodStats.load(20, 5f, 0f, 0);
        for (int i = 0; i < 80; i++) player.tick(Player.Input.NONE);
        assertEquals(11f, player.health, 1e-6);
        assertEquals(3f, player.foodStats.getExhaustionLevel(), 1e-6);
    }

    @Test
    void regenerationTwoHealsFourOverFiveSeconds() {
        Player player = player();
        player.difficulty = 2;
        player.naturalRegeneration = false;
        player.health = 10f;
        player.addPotionEffect(new PotionEffect(Potion.REGENERATION, 100, 1));
        for (int i = 0; i < 100; i++) player.tick(Player.Input.NONE);
        assertEquals(14f, player.health, 1e-6);
        assertFalse(player.isPotionActive(Potion.REGENERATION));
    }

    @Test
    void poisonNeverKills() {
        Player player = player();
        player.difficulty = 2;
        player.naturalRegeneration = false;
        player.health = 3f;
        player.addPotionEffect(new PotionEffect(Potion.POISON, 1200, 3));
        for (int i = 0; i < 1200; i++) player.tick(Player.Input.NONE);
        assertEquals(1f, player.health, 1e-6);
        assertFalse(player.dead);
    }

    @Test
    void absorptionSoaksDamageAndRefillsOnASecondApple() {
        Player player = player();
        player.addPotionEffect(new PotionEffect(Potion.ABSORPTION, 2400, 0));
        assertEquals(4f, player.absorptionAmount);
        player.attack(DamageSource.GENERIC, 3f);
        assertEquals(1f, player.absorptionAmount, 1e-6);
        assertEquals(20f, player.health, 1e-6);
        player.addPotionEffect(new PotionEffect(Potion.ABSORPTION, 2400, 0));
        assertEquals(4f, player.absorptionAmount, 1e-6);
        player.clearActivePotions();
        assertEquals(0f, player.absorptionAmount);
    }

    @Test
    void fireResistanceBlocksFireAndResistanceTakesAFifthPerLevel() {
        Player player = player();
        player.addPotionEffect(new PotionEffect(Potion.FIRE_RESISTANCE, 6000, 0));
        assertFalse(player.attack(DamageSource.LAVA, 4f));
        player.addPotionEffect(new PotionEffect(Potion.RESISTANCE, 6000, 0));
        player.attack(DamageSource.FALL, 10f);
        assertEquals(12f, player.health, 1e-6);
    }

    @Test
    void strongerEffectReplacesWeakerOneAndEqualOneOnlyExtends() {
        PotionEffect effect = new PotionEffect(Potion.HUNGER, 600, 0);
        effect.combine(new PotionEffect(Potion.HUNGER, 300, 0));
        assertEquals(600, effect.duration());
        effect.combine(new PotionEffect(Potion.HUNGER, 300, 2));
        assertEquals(2, effect.amplifier());
        assertEquals(300, effect.duration());
        assertEquals("0:15", Potion.durationString(effect));
    }
}
