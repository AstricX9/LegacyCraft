package net.minecraft.inventory;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.TestItems;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Inventory slot clicks: pick up, place, split, merge, swap, armour slots,
 * number keys, shift-click and creative middle-click.
 */
final class SlotClicksTest {
    private final Item stone = TestItems.item("stone"), dirt = TestItems.item("dirt");
    private final Item helmet = TestItems.item("iron_helmet", 1, 165);
    private InventoryPlayer inventory;

    @BeforeEach
    void setUp() { inventory = new InventoryPlayer(); }

    private Slot slot(int index) { return Slot.of(inventory.mainInventory, index); }

    @Test
    void leftClickPicksUpAndPutsDownWholeStacks() {
        inventory.mainInventory[9] = new ItemStack(stone, 40);
        SlotClicks.click(slot(9), inventory, 0);
        assertNull(inventory.mainInventory[9]);
        assertEquals(40, inventory.getItemStack().count());
        SlotClicks.click(slot(10), inventory, 0);
        assertEquals(40, inventory.mainInventory[10].count());
        assertNull(inventory.getItemStack());
    }

    @Test
    void rightClickTakesHalfRoundedUpAndPlacesOne() {
        inventory.mainInventory[9] = new ItemStack(stone, 5);
        SlotClicks.click(slot(9), inventory, 1);
        assertEquals(3, inventory.getItemStack().count());
        assertEquals(2, inventory.mainInventory[9].count());
        SlotClicks.click(slot(11), inventory, 1);
        assertEquals(1, inventory.mainInventory[11].count());
        assertEquals(2, inventory.getItemStack().count());
    }

    @Test
    void mergesUpToTheStackLimitAndSwapsDifferentItems() {
        inventory.mainInventory[9] = new ItemStack(stone, 60);
        inventory.setItemStack(new ItemStack(stone, 10));
        SlotClicks.click(slot(9), inventory, 0);
        assertEquals(64, inventory.mainInventory[9].count());
        assertEquals(6, inventory.getItemStack().count());

        inventory.setItemStack(new ItemStack(dirt, 3));
        SlotClicks.click(slot(9), inventory, 0);
        assertSame(dirt, inventory.mainInventory[9].item());
        assertSame(stone, inventory.getItemStack().item());
    }

    @Test
    void armorSlotsTakeOnlyMatchingPiecesOneAtATime() {
        Slot head = Slot.armor(inventory, 3);
        inventory.setItemStack(new ItemStack(stone, 5));
        SlotClicks.click(head, inventory, 0);
        assertNull(inventory.armorInventory[3]);
        inventory.setItemStack(new ItemStack(helmet, 1));
        SlotClicks.click(head, inventory, 0);
        assertSame(helmet, inventory.armorInventory[3].item());
        assertEquals(2, inventory.getTotalArmorValue());
    }

    @Test
    void numberKeySwapsWithTheHotbar() {
        inventory.mainInventory[20] = new ItemStack(stone, 7);
        inventory.mainInventory[2] = new ItemStack(dirt, 1);
        SlotClicks.swapWithHotbar(slot(20), inventory, 2);
        assertSame(stone, inventory.mainInventory[2].item());
        assertSame(dirt, inventory.mainInventory[20].item());
    }

    @Test
    void shiftClickMovesBetweenHotbarAndMainAndEquipsArmor() {
        inventory.mainInventory[0] = new ItemStack(stone, 10);
        inventory.mainInventory[12] = new ItemStack(stone, 60);
        SlotClicks.shiftClickPlayerSlot(inventory, slot(0), true, false);
        assertNull(inventory.mainInventory[0]);
        assertEquals(64, inventory.mainInventory[12].count());
        assertEquals(6, inventory.mainInventory[9].count(), "the rest goes to the first empty main slot");

        inventory.mainInventory[30] = new ItemStack(helmet, 1);
        SlotClicks.shiftClickPlayerSlot(inventory, slot(30), false, false);
        assertNotNull(inventory.armorInventory[3]);
        assertNull(inventory.mainInventory[30]);
    }

    @Test
    void creativeMiddleClickClonesAFullStack() {
        inventory.mainInventory[4] = new ItemStack(stone, 1);
        SlotClicks.cloneStack(slot(4), inventory);
        assertEquals(64, inventory.getItemStack().count());
        assertEquals(1, inventory.mainInventory[4].count());
    }
}
