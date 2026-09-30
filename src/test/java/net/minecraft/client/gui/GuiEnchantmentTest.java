package net.minecraft.client.gui;

import net.minecraft.item.ItemStack;
import net.minecraft.item.TestItems;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The enchanting table screen (HYPOTHESIS: recalled 1.8.9 GuiEnchantment and
 * ContainerEnchantment). The fake screen is 427x240, so the window's
 * top-left is (125, 37).
 */
final class GuiEnchantmentTest {
    private static final int LEFT = 125, TOP = 37;
    private ScreenFlowTest.Fake mc;
    private GuiEnchantment screen;

    @BeforeEach
    void open() throws Exception {
        I18n.set(Map.of());
        mc = new ScreenFlowTest.Fake(Files.createTempDirectory("lc-enchant"));
        mc.creative = false;
        mc.level = 30;
        mc.shelves = 15;
        screen = new GuiEnchantment(0, 64, 0);
        mc.displayGuiScreen(screen);
    }

    private static ItemStack sword() { return new ItemStack(TestItems.item("iron_sword", 1, 250)); }

    private static int[] offer(int button) { return new int[]{LEFT + 60 + 20, TOP + 14 + 19 * button + 8}; }

    private void shiftClick(int index) {
        int[] at = index < 9 ? new int[]{LEFT + 8 + index * 18 + 8, TOP + 142 + 8}
            : new int[]{LEFT + 8 + index % 9 * 18 + 8, TOP + 84 + (index / 9 - 1) * 18 + 8};
        mc.shiftDown = true;
        screen.mouseClicked(at[0], at[1], 0);
        screen.mouseReleased(at[0], at[1], 0);
        mc.shiftDown = false;
    }

    @Test
    void clickingAnOfferEnchantsAndCharges() {
        screen.table().itemSlot().set(sword());
        screen.table().lapisSlot().set(new ItemStack(TestItems.item("dye_blue"), 3));
        assertEquals(30, screen.table().levels[2]);
        int[] at = offer(2);
        screen.mouseClicked(at[0], at[1], 0);
        assertTrue(screen.table().item().isEnchanted());
        assertEquals(27, mc.level);
        assertNull(screen.table().lapis());
    }

    @Test
    void shiftClickSortsLapisAndOneItemIntoTheTable() {
        mc.inventory.mainInventory[9] = new ItemStack(TestItems.item("dye_blue"), 10);
        mc.inventory.mainInventory[10] = new ItemStack(TestItems.item("book"), 3);
        shiftClick(9);
        shiftClick(10);
        assertEquals(10, screen.table().lapis().count());
        assertEquals(1, screen.table().item().count(), "only one book goes in");
        assertEquals(2, mc.inventory.mainInventory[10].count());
    }

    @Test
    void theTooltipShowsTheClueAndTheCost() {
        screen.table().itemSlot().set(sword());
        screen.table().lapisSlot().set(new ItemStack(TestItems.item("dye_blue"), 1));
        int[] at = offer(1);
        List<String> lines = screen.offerTooltip(at[0], at[1]);
        assertNotNull(lines);
        assertTrue(lines.get(0).contains("container.enchant.clue"));
        assertTrue(lines.contains("§ccontainer.enchant.lapis.many"), "two lapis needed, one held: " + lines);
        assertTrue(lines.contains("§7container.enchant.level.many"));
    }

    @Test
    void closingGivesTheItemsBack() {
        screen.table().itemSlot().set(sword());
        mc.displayGuiScreen(null);
        assertNotNull(mc.inventory.mainInventory[0]);
    }

    @Test
    void theGlyphTextIsThreeOrFourOfTheWords() {
        Random random = new Random(9);
        for (int i = 0; i < 100; i++) {
            String[] words = GuiEnchantment.randomName(random).split(" ");
            assertTrue(words.length == 3 || words.length == 4);
            for (String word : words) assertTrue(List.of(GuiEnchantment.NAME_PARTS).contains(word));
        }
    }
}
