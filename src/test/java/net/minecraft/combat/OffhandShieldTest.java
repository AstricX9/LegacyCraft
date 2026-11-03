package net.minecraft.combat;

import net.minecraft.client.entity.Player;
import net.minecraft.entity.DamageSource;
import net.minecraft.inventory.InventoryPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.TestItems;
import net.minecraft.testing.GameData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The 1.9 offhand slot and shield blocking. */
final class OffhandShieldTest {
    @Test
    void swappingHandsAndTheOffhandSaveSlot() {
        InventoryPlayer inventory = new InventoryPlayer();
        ItemStack sword = new ItemStack(TestItems.item("iron_sword"));
        inventory.mainInventory[0] = sword;
        inventory.swapHands();
        assertNull(inventory.getCurrentItem());
        assertSame(sword, inventory.getOffhandItem());
        assertSame(sword, inventory.getBySaveSlot(-106));
        inventory.setBySaveSlot(-106, null);
        assertNull(inventory.getOffhandItem());
    }

    @Test
    void aRaisedShieldStopsHitsFromTheFrontAndWears() {
        Player player = new Player(GameData.newWorld(1L));
        player.yaw = 0f; // looking north (-z)
        ItemStack shield = new ItemStack(TestItems.sword("shield"));
        player.setItemInUse(shield, 72000);
        assertFalse(player.shieldRaised(), "not for the first 5 ticks");
        for (int i = 0; i < 5; i++) player.tick(Player.Input.NONE);
        assertTrue(player.shieldRaised());
        assertFalse(player.attackByMob(5f, player.x, player.z - 2, "zombie"), "in front: blocked");
        assertEquals(20f, player.health);
        assertEquals(6, shield.damage(), "1 + 5 damage of wear");
        player.hurtResistantTime = 0;
        assertTrue(player.attackByMob(5f, player.x, player.z + 2, "zombie"), "behind: hurts");
        assertTrue(player.health < 20f);
        assertFalse(player.blockWithShield(DamageSource.STARVE, 1f, player.x, player.z - 2), "unblockable");
    }
}
