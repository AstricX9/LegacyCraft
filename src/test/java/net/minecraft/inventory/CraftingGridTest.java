package net.minecraft.inventory;

import com.google.gson.JsonParser;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.TestItems;
import net.minecraft.item.crafting.CraftingManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Taking from a crafting result (HYPOTHESIS: recalled SlotCrafting and Container.slotClick rules). */
final class CraftingGridTest {
    private static final CraftingManager RECIPES = CraftingManager.parse(JsonParser.parseString("""
        {"groups": {}, "recipes": [
          {"type": "shaped", "pattern": ["#"], "key": {"#": "oak_log"}, "result": "oak_planks", "count": 4},
          {"type": "shaped", "pattern": ["AAA", "BEB", "CCC"], "key": {"A": "milk_bucket", "B": "sugar", "E": "egg", "C": "wheat"}, "result": "cake"}
        ]}""").getAsJsonObject(), TestItems::item);
    private InventoryPlayer inventory;
    private CraftingGrid grid;
    private final List<ItemStack> dropped = new ArrayList<>();

    private static Item item(String name) { return TestItems.item(name); }

    @BeforeEach
    void setUp() {
        inventory = new InventoryPlayer();
        grid = new CraftingGrid(3, 3, RECIPES, TestItems::item);
    }

    @Test
    void takingTheResultUsesOneOfEachIngredient() {
        grid.cells()[4] = new ItemStack(item("oak_log"), 3);
        grid.click(inventory, 0, dropped::add);
        assertEquals(4, inventory.getItemStack().count());
        assertEquals(2, grid.cells()[4].count());
        grid.click(inventory, 0, dropped::add);
        assertEquals(8, inventory.getItemStack().count(), "the same result stacks onto the cursor");
        assertEquals(1, grid.cells()[4].count());
    }

    @Test
    void rightClickingTheResultTakesHalfAndLosesTheRest() {
        grid.cells()[0] = new ItemStack(item("oak_log"), 2);
        grid.click(inventory, 1, dropped::add);
        assertEquals(2, inventory.getItemStack().count());
        assertEquals(1, grid.cells()[0].count());
        assertEquals(4, grid.result().count(), "the next craft is whole again");
    }

    @Test
    void shiftClickCraftsEverythingIntoTheInventory() {
        grid.cells()[0] = new ItemStack(item("oak_log"), 20);
        grid.shiftClick(inventory, false, dropped::add);
        assertNull(grid.cells()[0]);
        assertEquals(64, inventory.mainInventory[9].count(), "main slots first for the survival inventory");
        assertEquals(16, inventory.mainInventory[10].count());

        CraftingGrid table = new CraftingGrid(3, 3, RECIPES, TestItems::item);
        InventoryPlayer other = new InventoryPlayer();
        table.cells()[0] = new ItemStack(item("oak_log"), 1);
        table.shiftClick(other, true, dropped::add);
        assertEquals(4, other.mainInventory[8].count(), "the crafting table fills the hotbar from its last slot");
    }

    @Test
    void milkBucketsLeaveTheirBucketsBehind() {
        String[] cake = {"milk_bucket", "milk_bucket", "milk_bucket", "sugar", "egg", "sugar", "wheat", "wheat", "wheat"};
        for (int i = 0; i < 9; i++) grid.cells()[i] = new ItemStack(item(cake[i]));
        grid.click(inventory, 0, dropped::add);
        assertEquals("cake", inventory.getItemStack().item().name().path());
        int buckets = 0;
        for (ItemStack stack : inventory.mainInventory) if (stack != null && stack.item() == item("bucket")) buckets += stack.count();
        assertEquals(3, buckets);
        for (ItemStack cell : grid.cells()) assertNull(cell);
    }

    @Test
    void closingReturnsTheGridToTheInventory() {
        grid.cells()[2] = new ItemStack(item("sugar"), 5);
        grid.returnAll(inventory, dropped::add);
        assertNull(grid.cells()[2]);
        assertEquals(5, inventory.mainInventory[0].count());
        assertTrue(dropped.isEmpty());
    }
}
