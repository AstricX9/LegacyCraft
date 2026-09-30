package net.minecraft.client.gui;

import net.minecraft.item.ItemStack;
import net.minecraft.item.TestItems;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_E;

/**
 * The anvil screen (HYPOTHESIS: recalled 1.8.9 GuiRepair and ContainerRepair).
 * The fake screen is 427x240, so the window's top-left is (125, 37).
 */
final class GuiRepairTest {
    private static final int LEFT = 125, TOP = 37;
    private static final int[] OUTPUT = {LEFT + 134 + 8, TOP + 47 + 8};
    private ScreenFlowTest.Fake mc;
    private GuiRepair screen;

    @BeforeEach
    void open() throws Exception {
        I18n.set(Map.of());
        mc = new ScreenFlowTest.Fake(Files.createTempDirectory("lc-anvil"));
        mc.creative = false;
        screen = new GuiRepair(0, 64, 0);
        mc.displayGuiScreen(screen);
    }

    private void putFirst(ItemStack stack) { screen.slots.get(0).slot().set(stack); }

    private void click(int[] at) {
        screen.mouseClicked(at[0], at[1], 0);
        screen.mouseReleased(at[0], at[1], 0);
    }

    @Test
    void theFirstItemsNameFillsTheBoxAndTypingRenames() {
        putFirst(new ItemStack(TestItems.item("iron_sword", 1, 250)));
        assertEquals("Iron Sword", screen.nameField().getText());
        assertNull(screen.anvil().output(), "its own name costs nothing");
        screen.charTyped('!');
        assertEquals("Iron Sword!", screen.anvil().output().customName());
        assertEquals(1, screen.anvil().cost());
    }

    @Test
    void typingEDoesntCloseTheAnvil() {
        putFirst(new ItemStack(TestItems.item("dirt")));
        screen.keyPressed(GLFW_KEY_E, 0);
        assertSame(screen, mc.currentScreen());
    }

    @Test
    void theResultNeedsTheLevelsAndChargesThem() {
        putFirst(new ItemStack(TestItems.item("dirt"), 4));
        screen.charTyped('s');
        click(OUTPUT);
        assertNull(mc.inventory.getItemStack(), "no levels, no result");
        mc.level = 5;
        click(OUTPUT);
        ItemStack taken = mc.inventory.getItemStack();
        assertEquals("Dirts", taken.customName());
        assertEquals(4, taken.count());
        assertEquals(List.of(1), mc.anvilCharges);
        assertNull(screen.anvil().input(0));
        assertEquals("", screen.nameField().getText());
    }

    @Test
    void theCostLineTurnsRedWithoutTheLevels() {
        putFirst(new ItemStack(TestItems.item("dirt")));
        screen.charTyped('x');
        assertEquals("container.repair.cost", screen.costText());
        assertEquals(16736352, screen.costColor());
        mc.level = 1;
        assertEquals(8453920, screen.costColor());
    }

    @Test
    void closingGivesTheInputsBack() {
        putFirst(new ItemStack(TestItems.item("dirt"), 7));
        mc.displayGuiScreen(null);
        assertEquals(7, mc.inventory.mainInventory[0].count());
    }
}
