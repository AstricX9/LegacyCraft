package net.minecraft.client.gui;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.TestItems;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Drag-splitting and double-click gathering in the survival inventory
 * (HYPOTHESIS: recalled 1.8.9 GuiContainer and Container rules). The fake
 * screen is 427x240, so the 176x166 window's top-left is (125, 37).
 */
final class ContainerMouseTest {
    private static final int LEFT = 125, TOP = 37;
    private ScreenFlowTest.Fake mc;
    private GuiInventory screen;
    private final Item stone = TestItems.item("stone"), granite = TestItems.item("granite");

    @BeforeEach
    void open() throws Exception {
        I18n.set(Map.of());
        mc = new ScreenFlowTest.Fake(Files.createTempDirectory("lc-mouse"));
        screen = new GuiInventory();
        mc.displayGuiScreen(screen);
    }

    /** Centre of main-inventory slot 9-35 or hotbar slot 0-8, in screen coordinates. */
    private static int[] slot(int index) {
        if (index < 9) return new int[]{LEFT + 8 + index * 18 + 8, TOP + 142 + 8};
        int row = index / 9 - 1, column = index % 9;
        return new int[]{LEFT + 8 + column * 18 + 8, TOP + 84 + row * 18 + 8};
    }

    private void drag(int button, int... indices) {
        int[] first = slot(indices[0]);
        screen.mouseClicked(first[0], first[1], button);
        for (int index : indices) {
            int[] at = slot(index);
            screen.mouseDragged(at[0], at[1], button, 10);
        }
        int[] last = slot(indices[indices.length - 1]);
        screen.mouseReleased(last[0], last[1], button);
    }

    private ItemStack main(int index) { return mc.inventory.mainInventory[index]; }
    private ItemStack cursor() { return mc.inventory.getItemStack(); }

    @Test
    void leftDraggingSpreadsTheStackEvenly() {
        mc.inventory.setItemStack(new ItemStack(stone, 10));
        drag(0, 9, 10, 11);
        assertEquals(3, main(9).count());
        assertEquals(3, main(10).count());
        assertEquals(3, main(11).count());
        assertEquals(1, cursor().count(), "the remainder stays in hand");
    }

    @Test
    void rightDraggingPutsOneInEachSlotAndTopsUpMatchingStacks() {
        main(0);
        mc.inventory.mainInventory[12] = new ItemStack(stone, 4);
        mc.inventory.mainInventory[13] = new ItemStack(granite, 1);
        mc.inventory.setItemStack(new ItemStack(stone, 10));
        drag(1, 9, 10, 12, 13);
        assertEquals(1, main(9).count());
        assertEquals(1, main(10).count());
        assertEquals(5, main(12).count(), "a matching stack gets one more");
        assertEquals(granite, main(13).item(), "a different item is skipped");
        assertEquals(7, cursor().count());
    }

    @Test
    void releasingWithoutDraggingIsAnOrdinaryClick() {
        mc.inventory.setItemStack(new ItemStack(stone, 10));
        int[] at = slot(9);
        screen.mouseClicked(at[0], at[1], 0);
        assertTrue(screen.isDragSplitting());
        screen.mouseReleased(at[0], at[1], 0);
        assertEquals(10, main(9).count());
        assertNull(cursor());
    }

    @Test
    void pressingTheOtherButtonCancelsTheDrag() {
        mc.inventory.setItemStack(new ItemStack(stone, 10));
        int[] a = slot(9), b = slot(10);
        screen.mouseClicked(a[0], a[1], 0);
        screen.mouseDragged(a[0], a[1], 0, 10);
        screen.mouseDragged(b[0], b[1], 0, 10);
        screen.mouseReleased(b[0], b[1], 1);
        assertNull(main(9));
        assertNull(main(10));
        assertEquals(10, cursor().count());
    }

    @Test
    void doubleClickGathersPartStacksBeforeFullOnes() {
        mc.inventory.mainInventory[9] = new ItemStack(stone, 10);
        mc.inventory.mainInventory[10] = new ItemStack(stone, 64);
        mc.inventory.mainInventory[11] = new ItemStack(stone, 20);
        mc.inventory.mainInventory[0] = new ItemStack(stone, 5);
        mc.inventory.mainInventory[12] = new ItemStack(granite, 3);
        int[] at = slot(9);
        screen.mouseClicked(at[0], at[1], 0);
        screen.mouseReleased(at[0], at[1], 0);
        assertEquals(10, cursor().count());
        screen.mouseClicked(at[0], at[1], 0);
        screen.mouseReleased(at[0], at[1], 0);
        assertEquals(64, cursor().count());
        assertNull(main(11), "the 20 went first");
        assertNull(main(0), "then the 5 on the hotbar");
        assertEquals(35, main(10).count(), "the full stack gave the last 29");
        assertEquals(3, main(12).count(), "other items stay");
        assertNull(main(9));
    }

    @Test
    void shiftDoubleClickMovesEveryMatchingStackOfThatInventory() {
        GuiCrafting table = new GuiCrafting(0, 64, 0);
        mc.displayGuiScreen(table);
        table.grid().cells()[0] = new ItemStack(stone, 3);
        table.grid().cells()[4] = new ItemStack(stone, 2);
        table.grid().cells()[8] = new ItemStack(granite, 1);
        int x = LEFT + 30 + 8, y = TOP + 17 + 8;
        mc.shiftDown = true;
        table.mouseClicked(x, y, 0);
        table.mouseReleased(x, y, 0);
        table.mouseClicked(x, y, 0);
        table.mouseReleased(x, y, 0);
        assertNull(table.grid().cells()[0]);
        assertNull(table.grid().cells()[4], "the other stone in the grid went too");
        assertNotNull(table.grid().cells()[8]);
        assertEquals(5, main(9).count());
    }
}
