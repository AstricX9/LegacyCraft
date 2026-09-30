package net.minecraft.client.entity;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.item.TestItems;
import net.minecraft.testing.GameData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Taking damage through armour and protection enchantments (HYPOTHESIS: recalled 1.8.9 EntityLivingBase.attackEntityFrom). */
final class PlayerDamageTest {
    private static Player player() { return new Player(GameData.newWorld(1L)); }

    private static ItemStack piece(String name, int maxDamage) { return new ItemStack(TestItems.item(name, 1, maxDamage)); }

    @Test
    void fallDamageIgnoresArmourPoints() {
        Player player = player();
        player.armor[0] = piece("diamond_boots", 429);
        player.armor[2] = piece("diamond_chestplate", 528);
        assertTrue(player.attack(DamageSource.FALL, 10f));
        assertEquals(10f, player.health, 1e-6);
        assertEquals(0, player.armor[0].damage(), "falls don't wear armour");
    }

    @Test
    void featherFallingTakesOffBetweenAThirdAndThreeQuartersOfAFall() {
        for (int i = 0; i < 50; i++) {
            Player player = player();
            player.armor[0] = piece("diamond_boots", 429);
            player.armor[0].addEnchantment(Enchantment.FEATHER_FALLING.id, 4);
            player.attack(DamageSource.FALL, 10f);
            float taken = 20f - player.health;
            assertTrue(taken >= 10f * 7 / 25 - 1e-4 && taken <= 10f * 16 / 25 + 1e-4, "9-18 points of 25: " + taken);
        }
    }

    @Test
    void armourPointsReduceBlockableDamageAndWearTheArmour() {
        Player player = player();
        player.armor[0] = piece("diamond_boots", 429);
        player.armor[1] = piece("diamond_leggings", 495);
        player.armor[2] = piece("diamond_chestplate", 528);
        player.armor[3] = piece("diamond_helmet", 363);
        assertEquals(20, player.armorValue());
        player.attack(DamageSource.CACTUS, 5f);
        assertEquals(20f - 5f * 5 / 25, player.health, 1e-5);
        for (ItemStack piece : player.armor) assertEquals(1, piece.damage(), "at least one point of wear each");
    }

    @Test
    void aSmallerHitDuringTheInvulnerabilityWindowIsIgnored() {
        Player player = player();
        assertTrue(player.attack(DamageSource.GENERIC, 4f));
        assertFalse(player.attack(DamageSource.GENERIC, 3f));
        assertEquals(16f, player.health, 1e-6);
        assertTrue(player.attack(DamageSource.GENERIC, 6f));
        assertEquals(14f, player.health, 1e-6, "only the difference gets through");
    }

    @Test
    void creativePlayersOnlyFeelTheVoid() {
        Player player = player();
        player.disableDamage = true;
        assertFalse(player.attack(DamageSource.LAVA, 4f));
        assertEquals(20f, player.health);
        assertTrue(player.attack(DamageSource.OUT_OF_WORLD, 4f));
        assertEquals(16f, player.health, 1e-6);
    }

    @Test
    void lethalDamageKillsAndTellsTheListener() {
        Player player = player();
        DamageSource[] cause = new DamageSource[1];
        player.damageListener = new Player.DamageListener() {
            @Override public void died(DamageSource source) { cause[0] = source; }
        };
        player.attack(DamageSource.DROWN, 25f);
        assertTrue(player.dead);
        assertEquals(0f, player.health);
        assertSame(DamageSource.DROWN, cause[0]);
    }
}
