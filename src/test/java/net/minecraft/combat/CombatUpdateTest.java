package net.minecraft.combat;

import net.minecraft.client.entity.Player;
import net.minecraft.inventory.ArmorValues;
import net.minecraft.item.ItemStack;
import net.minecraft.item.TestItems;
import net.minecraft.item.ToolValues;
import net.minecraft.testing.GameData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The 1.9 combat update: weapon values, the attack cooldown, armour toughness and faster healing. */
final class CombatUpdateTest {
    @Test
    void weaponDamageAndSpeedFollowTheNineTables() {
        assertEquals(7f, ToolValues.attackDamage(TestItems.item("diamond_sword")));
        assertEquals(9f, ToolValues.attackDamage(TestItems.item("stone_axe")));
        assertEquals(7f, ToolValues.attackDamage(TestItems.item("golden_axe")));
        assertEquals(4.5f, ToolValues.attackDamage(TestItems.item("iron_shovel")));
        assertEquals(1f, ToolValues.attackDamage(TestItems.item("diamond_hoe")));
        assertEquals(1.6f, ToolValues.attackSpeed(TestItems.item("wooden_sword")));
        assertEquals(0.9f, ToolValues.attackSpeed(TestItems.item("iron_axe")));
        assertEquals(4f, ToolValues.attackSpeed(TestItems.item("diamond_hoe")));
        assertEquals(4f, ToolValues.attackSpeed(null));
    }

    @Test
    void aSwordRechargesInTwelveAndAHalfTicksAndSwitchingRestartsIt() {
        Player player = new Player(GameData.newWorld(1L));
        ItemStack[] held = {new ItemStack(TestItems.item("diamond_sword"))};
        player.heldItem = () -> held[0];
        player.tick(Player.Input.NONE);
        assertEquals(12.5f, player.cooldownPeriod(), 1e-4);
        for (int i = 0; i < 11; i++) player.tick(Player.Input.NONE);
        assertTrue(player.attackStrength(0f) > 0.9f && player.attackStrength(0f) < 1f);
        player.tick(Player.Input.NONE);
        assertEquals(1f, player.attackStrength(0f));
        held[0] = new ItemStack(TestItems.item("diamond_axe"));
        player.tick(Player.Input.NONE);
        assertTrue(player.attackStrength(0f) < 0.1f);
    }

    @Test
    void toughnessHelpsAgainstBigHits() {
        // Full diamond: 20 armour, 8 toughness. A 10-damage hit still gets 70% off; without toughness only 60%.
        assertEquals(3f, ArmorValues.damageAfterArmor(10f, 20f, 8f), 1e-4);
        assertEquals(10f * (1f - 15f / 25f), ArmorValues.damageAfterArmor(10f, 20f, 0f), 1e-4);
        // Never under a fifth of the armour.
        assertEquals(60f * (1f - 4f / 25f), ArmorValues.damageAfterArmor(60f, 20f, 0f), 1e-4);
    }

    @Test
    void aFullBarWithSaturationHealsEveryHalfSecond() {
        Player player = new Player(GameData.newWorld(1L));
        player.foodStats.load(20, 5f, 0f, 0);
        player.health = 10f;
        for (int i = 0; i < 10; i++) {
            player.tick(Player.Input.NONE);
            player.hurtResistantTime = 0;
        }
        assertEquals(10f + 5f / 6f, player.health, 1e-4);
    }
}
