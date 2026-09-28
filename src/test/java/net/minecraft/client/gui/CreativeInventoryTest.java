package net.minecraft.client.gui;

import net.minecraft.item.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.TestItems;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

/**
 * Drives the creative inventory through the fake client. The fake screen is
 * 427x240 GUI pixels, so the 195x136 window's top-left is (116, 52).
 */
final class CreativeInventoryTest {
    private static final int LEFT = 116, TOP = 52;
    private ScreenFlowTest.Fake mc;
    private GuiContainerCreative screen;
    private final Item stone = TestItems.item("stone"), granite = TestItems.item("granite");

    @BeforeEach
    void open() throws Exception {
        I18n.set(Map.of());
        mc = new ScreenFlowTest.Fake(Files.createTempDirectory("lc-creative"));
        mc.creativeTabs = TestItems.creativeTabs();
        GuiContainerCreative.resetSelectedTabForTests();
        screen = new GuiContainerCreative();
        mc.displayGuiScreen(screen);
    }

    private void clickGrid(int index, int button) {
        int x = LEFT + 9 + (index % 9) * 18 + 8, y = TOP + 18 + (index / 9) * 18 + 8;
        screen.mouseClicked(x, y, button);
        screen.mouseReleased(x, y, button);
    }

    private void clickHotbar(int slot, int button) {
        int x = LEFT + 9 + slot * 18 + 8, y = TOP + 112 + 8;
        screen.mouseClicked(x, y, button);
        screen.mouseReleased(x, y, button);
    }

    /** Clicks a tab by index: top row above the window, bottom row below it. */
    private void clickTab(int index) {
        int column = index % 6;
        int x = column == 5 ? 195 - 28 + 2 : 28 * column + column;
        int y = index < 6 ? -16 : 136 + 16;
        screen.mouseClicked(LEFT + x + 14, TOP + y, 0);
        screen.mouseReleased(LEFT + x + 14, TOP + y, 0);
    }

    private ItemStack cursor() { return mc.inventory.getItemStack(); }

    @Test
    void opensOnBuildingBlocksWithTheirItems() {
        assertEquals(0, GuiContainerCreative.selectedTab());
        assertEquals(mc.creativeTabs.tab(0).entries(), screen.itemList());
        assertSame(stone, screen.entryAt(0).item());
        assertTrue(screen.needsScrollBars());
    }

    @Test
    void gridClicksTakeAddRemoveAndDelete() {
        clickGrid(0, 0);
        assertSame(stone, cursor().item());
        assertEquals(1, cursor().count());
        clickGrid(0, 0);
        assertEquals(2, cursor().count(), "left click with the same item adds one");
        clickGrid(0, 1);
        assertEquals(1, cursor().count(), "right click removes one");
        clickGrid(0, 1);
        assertNull(cursor());

        mc.shiftDown = true;
        clickGrid(0, 0);
        assertEquals(64, cursor().count(), "shift takes a full stack");
        mc.shiftDown = false;
        clickGrid(1, 0);
        assertNull(cursor(), "clicking a different item deletes the held stack");
        clickGrid(1, 2);
        assertSame(granite, cursor().item());
        assertEquals(64, cursor().count(), "middle click clones a full stack");
    }

    @Test
    void hotbarRowTakesStacksAndShiftClickClearsIt() {
        clickGrid(0, 0);
        clickHotbar(3, 0);
        assertSame(stone, mc.inventory.mainInventory[3].item());
        assertNull(cursor());
        mc.shiftDown = true;
        clickHotbar(3, 0);
        assertNull(mc.inventory.mainInventory[3]);
    }

    @Test
    void numberKeyOverTheGridFillsThatHotbarSlot() {
        screen.trackMouse(LEFT + 9 + 18 + 8, TOP + 18 + 8);
        screen.keyPressed(GLFW_KEY_3, 0);
        assertSame(granite, mc.inventory.mainInventory[2].item());
        assertEquals(64, mc.inventory.mainInventory[2].count());
    }

    @Test
    void draggingTheScrollbarFollowsThePointerUntilReleased() {
        int barX = LEFT + 175 + 6, barTop = TOP + 18;
        screen.mouseClicked(barX, barTop + 3, 0);
        assertEquals(0f, screen.currentScroll(), "pressing the top of the bar stays at the top");
        screen.followScrollbar(barTop + 112 / 2);
        assertEquals(0.5f, screen.currentScroll(), 0.01f, "the thumb's middle sits under the pointer");
        screen.followScrollbar(barTop + 300);
        assertEquals(1f, screen.currentScroll(), "dragging past the end clamps");
        screen.mouseReleased(barX + 50, barTop + 300, 0);
        screen.followScrollbar(barTop);
        assertEquals(1f, screen.currentScroll(), "released: the pointer no longer moves it");
    }

    @Test
    void tabsSwitchOnReleaseAndResetTheScroll() {
        screen.mouseScrolled(0, 0, -1);
        assertTrue(screen.currentScroll() > 0);
        assertTrue(screen.rowOffset() > 0);
        screen.mouseClicked(LEFT + 29 + 14, TOP - 16, 0);
        assertEquals(0, GuiContainerCreative.selectedTab(), "pressing a tab does not switch yet");
        screen.mouseReleased(LEFT + 29 + 14, TOP - 16, 0);
        assertEquals(1, GuiContainerCreative.selectedTab());
        assertEquals(0f, screen.currentScroll());
        clickTab(CreativeTabs.INVENTORY);
        assertEquals(CreativeTabs.INVENTORY, GuiContainerCreative.selectedTab());
        assertTrue(screen.itemList().isEmpty());
        assertFalse(screen.needsScrollBars());
    }

    @Test
    void chatKeyOpensSearchAndTypingFilters() {
        screen.keyPressed(GLFW_KEY_T, 0);
        screen.charTyped('t');
        assertEquals(CreativeTabs.SEARCH, GuiContainerCreative.selectedTab());
        assertEquals("", screen.searchText(), "the chat key itself is not typed");
        int everything = screen.itemList().size();
        assertEquals(mc.creativeTabs.tab(CreativeTabs.SEARCH).entries().size(), everything);
        for (char c : "gran".toCharArray()) {
            screen.keyPressed(GLFW_KEY_A + (c - 'a'), 0);
            screen.charTyped(c);
        }
        assertEquals("gran", screen.searchText());
        assertFalse(screen.itemList().isEmpty());
        assertTrue(screen.itemList().size() < everything);
        for (CreativeTabs.Entry entry : screen.itemList()) assertTrue(mc.itemName(entry.item()).toLowerCase().contains("gran"));
        screen.keyPressed(GLFW_KEY_E, 0);
        assertSame(screen, mc.screen, "the inventory key types into the search field instead of closing");
        screen.keyPressed(GLFW_KEY_BACKSPACE, 0);
        assertEquals("gra", screen.searchText());
        screen.keyPressed(GLFW_KEY_ESCAPE, 0);
        assertNull(mc.screen);
    }

    @Test
    void searchTooltipsNameTheItemsTab() {
        clickTab(CreativeTabs.SEARCH);
        List<String> lines = screen.tooltip(new ItemStack(granite, 1));
        assertEquals("Granite", lines.get(0));
        assertTrue(lines.get(1).endsWith("itemGroup.buildingBlocks"));
        mc.settings.advancedItemTooltips = true;
        assertEquals("Granite (#0001/1)", screen.tooltip(new ItemStack(granite, 1)).get(0));
    }

    @Test
    void inventoryTabDestroySlotDeletesAndShiftClearsEverything() {
        clickTab(CreativeTabs.INVENTORY);
        mc.inventory.mainInventory[15] = new ItemStack(stone, 10);
        mc.inventory.armorInventory[0] = new ItemStack(TestItems.item("iron_boots", 1, 195), 1);
        int destroyX = LEFT + 173 + 8, destroyY = TOP + 112 + 8;
        mc.inventory.setItemStack(new ItemStack(granite, 5));
        screen.mouseClicked(destroyX, destroyY, 0);
        assertNull(cursor());
        assertNotNull(mc.inventory.mainInventory[15]);
        mc.shiftDown = true;
        screen.mouseClicked(destroyX, destroyY, 0);
        assertNull(mc.inventory.mainInventory[15]);
        assertNull(mc.inventory.armorInventory[0]);
    }

    @Test
    void inventoryTabSlotsAreThePlayersOwn() {
        clickTab(CreativeTabs.INVENTORY);
        mc.inventory.mainInventory[9] = new ItemStack(stone, 7);
        screen.mouseClicked(LEFT + 9 + 8, TOP + 54 + 8, 0);
        assertEquals(7, cursor().count());
        assertNull(mc.inventory.mainInventory[9]);
    }

    @Test
    void closingReturnsTheHeldStackAndRemembersTheTab() {
        clickTab(4);
        mc.inventory.setItemStack(new ItemStack(stone, 5));
        mc.displayGuiScreen(null);
        assertNull(cursor());
        assertEquals(5, mc.inventory.mainInventory[0].count());
        mc.displayGuiScreen(new GuiContainerCreative());
        assertEquals(4, GuiContainerCreative.selectedTab());
    }

    @Test
    void dropKeyAndOutsideClicksThrowItems() {
        screen.trackMouse(LEFT + 9 + 8, TOP + 18 + 8);
        screen.keyPressed(GLFW_KEY_Q, 0);
        assertEquals(1, mc.dropped.get(0).count(), "Q over the grid throws one");
        screen.keyPressed(GLFW_KEY_Q, GLFW_MOD_CONTROL);
        assertEquals(64, mc.dropped.get(1).count(), "Ctrl+Q throws a stack");

        mc.inventory.mainInventory[4] = new ItemStack(stone, 10);
        screen.trackMouse(LEFT + 9 + 4 * 18 + 8, TOP + 112 + 8);
        screen.keyPressed(GLFW_KEY_Q, 0);
        assertEquals(9, mc.inventory.mainInventory[4].count());

        mc.inventory.setItemStack(new ItemStack(stone, 5));
        screen.mouseClicked(5, 5, 1);
        assertEquals(4, cursor().count(), "right click outside throws one");
        screen.mouseClicked(5, 5, 0);
        assertNull(cursor());
        assertEquals(4, mc.dropped.get(mc.dropped.size() - 1).count());
    }

    @Test
    void survivalInventoryReturnsTheCraftingGridOnClose() {
        GuiInventory inventory = new GuiInventory();
        mc.displayGuiScreen(inventory);
        mc.inventory.setItemStack(new ItemStack(stone, 3));
        // Top-left crafting cell at (88, 26) in a 176x166 window whose top-left is (125, 37).
        inventory.mouseClicked(125 + 88 + 8, 37 + 26 + 8, 0);
        assertEquals(3, inventory.craftMatrix()[0].count());
        mc.displayGuiScreen(null);
        assertNull(inventory.craftMatrix()[0]);
        assertEquals(3, mc.inventory.mainInventory[0].count());
    }
}
