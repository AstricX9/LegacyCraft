package net.minecraft.client.gui;

import net.minecraft.inventory.Furnace;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.TestItems;
import net.minecraft.item.crafting.FurnaceRecipes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The furnace screen's slots (HYPOTHESIS: recalled 1.8.9 ContainerFurnace
 * and SlotFurnaceOutput). The fake screen is 427x240, so the 176x166
 * window's top-left is (125, 37).
 */
final class GuiFurnaceTest {
    private static final int LEFT = 125, TOP = 37;
    private ScreenFlowTest.Fake mc;
    private GuiFurnace screen;
    private Furnace furnace;
    private final Item ore = TestItems.item("iron_ore"), ingot = TestItems.item("iron_ingot");

    @BeforeEach
    void open() throws Exception {
        I18n.set(Map.of());
        mc = new ScreenFlowTest.Fake(Files.createTempDirectory("lc-furnace"));
        FurnaceRecipes recipes = new FurnaceRecipes();
        recipes.add(ore, ingot, 0.7f);
        furnace = new Furnace(recipes, TestItems::item);
        mc.furnace = furnace;
        mc.creative = false;
        screen = new GuiFurnace(0, 64, 0);
        mc.displayGuiScreen(screen);
    }

    private static final int[] INPUT = {LEFT + 56 + 8, TOP + 17 + 8}, FUEL = {LEFT + 56 + 8, TOP + 53 + 8},
        OUTPUT = {LEFT + 116 + 8, TOP + 35 + 8};

    /** Centre of main-inventory slot 9-35 or hotbar slot 0-8, in screen coordinates. */
    private static int[] slot(int index) {
        if (index < 9) return new int[]{LEFT + 8 + index * 18 + 8, TOP + 142 + 8};
        int row = index / 9 - 1, column = index % 9;
        return new int[]{LEFT + 8 + column * 18 + 8, TOP + 84 + row * 18 + 8};
    }

    private void click(int[] at, int button) {
        screen.mouseClicked(at[0], at[1], button);
        screen.mouseReleased(at[0], at[1], button);
    }

    private void shiftClick(int[] at) {
        mc.shiftDown = true;
        click(at, 0);
        mc.shiftDown = false;
    }

    private ItemStack main(int index) { return mc.inventory.mainInventory[index]; }

    @Test
    void shiftClickSendsSmeltablesToTheInputAndFuelToTheFuelSlot() {
        mc.inventory.mainInventory[9] = new ItemStack(ore, 20);
        mc.inventory.mainInventory[10] = new ItemStack(TestItems.item("coal"), 5);
        mc.inventory.mainInventory[11] = new ItemStack(TestItems.item("dirt"), 3);
        shiftClick(slot(9));
        shiftClick(slot(10));
        shiftClick(slot(11));
        assertEquals(20, furnace.get(Furnace.INPUT).count());
        assertEquals(5, furnace.get(Furnace.FUEL).count());
        assertNull(main(9));
        assertEquals(3, main(0).count(), "neither: main slots go to the hotbar");
    }

    @Test
    void shiftClickingTheOutputFillsTheHotbarFromTheRightAndCountsIt() {
        furnace.set(Furnace.OUTPUT, new ItemStack(ingot, 5));
        shiftClick(OUTPUT);
        assertNull(furnace.get(Furnace.OUTPUT));
        assertEquals(5, main(8).count());
        assertEquals(List.of("iron_ingot x5"), mc.smelted);
    }

    @Test
    void takingFromTheOutputByHandIsCounted() {
        furnace.set(Furnace.OUTPUT, new ItemStack(ingot, 5));
        click(OUTPUT, 1);
        assertEquals(3, mc.inventory.getItemStack().count(), "right click takes half, rounded up");
        assertEquals(2, furnace.get(Furnace.OUTPUT).count());
        click(OUTPUT, 0);
        assertEquals(5, mc.inventory.getItemStack().count(), "holding the same item takes the rest");
        assertNull(furnace.get(Furnace.OUTPUT));
        assertEquals(List.of("iron_ingot x3", "iron_ingot x2"), mc.smelted);
    }

    @Test
    void theOutputIsOnlyTakenWhenItAllFitsOnTheCursor() {
        furnace.set(Furnace.OUTPUT, new ItemStack(ingot, 10));
        mc.inventory.setItemStack(new ItemStack(ingot, 60));
        click(OUTPUT, 0);
        assertEquals(60, mc.inventory.getItemStack().count());
        assertEquals(10, furnace.get(Furnace.OUTPUT).count());
        assertTrue(mc.smelted.isEmpty());
    }

    @Test
    void nothingCanBePutInTheOutput() {
        mc.inventory.setItemStack(new ItemStack(ore, 4));
        click(OUTPUT, 0);
        assertNull(furnace.get(Furnace.OUTPUT));
        assertEquals(4, mc.inventory.getItemStack().count());
    }

    @Test
    void theFuelSlotRefusesWhatDoesNotBurn() {
        mc.inventory.setItemStack(new ItemStack(ore, 4));
        click(FUEL, 0);
        assertNull(furnace.get(Furnace.FUEL));
        click(INPUT, 0);
        assertEquals(4, furnace.get(Furnace.INPUT).count());
    }

    @Test
    void doubleClickGatheringFromTheOutputIsCounted() {
        furnace.set(Furnace.OUTPUT, new ItemStack(ingot, 7));
        mc.inventory.mainInventory[9] = new ItemStack(ingot, 1);
        int[] at = slot(9);
        screen.mouseClicked(at[0], at[1], 0);
        screen.mouseReleased(at[0], at[1], 0);
        screen.mouseClicked(at[0], at[1], 0);
        screen.mouseReleased(at[0], at[1], 0);
        assertEquals(8, mc.inventory.getItemStack().count());
        assertNull(furnace.get(Furnace.OUTPUT));
        assertEquals(List.of("iron_ingot x7"), mc.smelted);
    }

    @Test
    void aNumberKeyMovesTheOutputToTheHotbarPushingAsideWhatWasThere() {
        furnace.set(Furnace.OUTPUT, new ItemStack(ingot, 4));
        mc.inventory.mainInventory[2] = new ItemStack(TestItems.item("dirt"), 1);
        screen.numberKey(screen.slotAt(OUTPUT[0], OUTPUT[1]), 2);
        assertEquals(ingot, main(2).item());
        assertEquals(TestItems.item("dirt"), main(0).item(), "the dirt moved to the first empty slot");
        assertEquals(List.of("iron_ingot x4"), mc.smelted);
    }
}
