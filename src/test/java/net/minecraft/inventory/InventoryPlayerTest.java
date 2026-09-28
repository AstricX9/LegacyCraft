package net.minecraft.inventory;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.TestItems;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The player inventory: hotbar scrolling, pick block, adding and consuming items, save slots and armour. */
final class InventoryPlayerTest {
    private final Item stone = TestItems.item("stone"), dirt = TestItems.item("dirt");

    @Test
    void scrollWrapsAroundTheHotbar() {
        InventoryPlayer inventory = new InventoryPlayer();
        inventory.changeCurrentItem(1);
        assertEquals(8, inventory.currentItem, "wheel away from the user moves left");
        inventory.changeCurrentItem(-3);
        assertEquals(0, inventory.currentItem);
    }

    @Test
    void pickBlockSelectsOrFillsTheHotbar() {
        InventoryPlayer inventory = new InventoryPlayer();
        inventory.mainInventory[0] = new ItemStack(dirt, 1);
        inventory.mainInventory[5] = new ItemStack(stone, 3);
        inventory.pickItem(stone, false);
        assertEquals(5, inventory.currentItem);

        inventory.currentItem = 0;
        Item planks = TestItems.item("oak_planks");
        inventory.pickItem(planks, false);
        assertEquals(0, inventory.currentItem, "survival only selects what the hotbar holds");
        inventory.pickItem(planks, true);
        assertEquals(1, inventory.currentItem, "creative moves to the first empty hotbar slot");
        assertEquals(64, inventory.mainInventory[1].count());
    }

    @Test
    void addingMergesThenFillsEmptySlotsHotbarFirst() {
        InventoryPlayer inventory = new InventoryPlayer();
        inventory.mainInventory[20] = new ItemStack(stone, 60);
        assertTrue(inventory.addItemStackToInventory(new ItemStack(stone, 10)));
        assertEquals(64, inventory.mainInventory[20].count());
        assertEquals(6, inventory.mainInventory[0].count());
    }

    @Test
    void consumingEmptiesTheSlotAtZero() {
        InventoryPlayer inventory = new InventoryPlayer();
        inventory.mainInventory[0] = new ItemStack(stone, 1);
        inventory.consumeCurrentItem();
        assertNull(inventory.getCurrentItem());
    }

    @Test
    void saveSlotsCoverMainAndArmor() {
        InventoryPlayer inventory = new InventoryPlayer();
        inventory.setBySaveSlot(35, new ItemStack(stone, 2));
        inventory.setBySaveSlot(100, new ItemStack(TestItems.item("iron_boots", 1, 195), 1));
        assertSame(stone, inventory.mainInventory[35].item());
        assertNotNull(inventory.armorInventory[0]);
        assertEquals(2, inventory.getTotalArmorValue());
        assertNull(inventory.getBySaveSlot(50));
    }

    @Test
    void armorSlotsByItemName() {
        assertEquals(3, ArmorValues.armorSlot(TestItems.item("diamond_helmet")));
        assertEquals(2, ArmorValues.armorSlot(TestItems.item("leather_chestplate")));
        assertEquals(0, ArmorValues.armorSlot(TestItems.item("golden_boots")));
        assertEquals(3, ArmorValues.armorSlot(TestItems.item("pumpkin")));
        assertEquals(-1, ArmorValues.armorSlot(TestItems.item("stone")));
        assertEquals(8, ArmorValues.points(TestItems.item("diamond_chestplate"), 2));
    }
}
