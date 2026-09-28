package net.minecraft.client.gui;

import net.minecraft.inventory.CraftingGrid;
import net.minecraft.inventory.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

/**
 * 1.8.9's survival inventory (master context §21): 176x166 from
 * {@code container/inventory.png} with the four armor slots, the 2x2 crafting
 * grid and its result slot, 27 main slots and the hotbar. In Creative mode the
 * inventory key opens {@link GuiContainerCreative} instead.
 *
 * <p>The 2x2 grid crafts every recipe that fits in it ({@link CraftingGrid});
 * whatever is left in the grid returns to the inventory when the screen
 * closes, as vanilla does. The player stands beside the armor slots, looking
 * toward the mouse. Slot positions are HYPOTHESIS.
 */
public class GuiInventory extends GuiContainer {
    static final ResourceLocation BACKGROUND = new ResourceLocation("minecraft:textures/gui/container/inventory.png");
    private CraftingGrid grid;

    public GuiInventory() {
        xSize = 176;
        ySize = 166;
    }

    @Override
    protected void buildSlots() {
        InventoryPlayer inventory = inventory();
        // Kept across resizes (initGui runs again) so the grid's contents survive.
        if (grid == null) grid = new CraftingGrid(2, 2, mc.crafting(), mc::itemByName);
        slots.add(new SlotPos(grid.resultSlot(), 144, 36, Kind.OTHER));
        for (int row = 0; row < 2; row++)
            for (int column = 0; column < 2; column++)
                slots.add(new SlotPos(grid.cell(column + row * 2), 88 + column * 18, 26 + row * 18, Kind.OTHER));
        for (int i = 0; i < 4; i++)
            slots.add(new SlotPos(Slot.armor(inventory, 3 - i), 8, 8 + i * 18, Kind.ARMOR));
        for (int row = 0; row < 3; row++)
            for (int column = 0; column < 9; column++)
                slots.add(new SlotPos(Slot.of(inventory.mainInventory, column + (row + 1) * 9), 8 + column * 18, 84 + row * 18, Kind.MAIN));
        for (int column = 0; column < 9; column++)
            slots.add(new SlotPos(Slot.of(inventory.mainInventory, column), 8 + column * 18, 142, Kind.HOTBAR));
    }

    @Override
    protected void shiftClick(SlotPos pos) {
        if (pos.kind() == Kind.OTHER) {
            // Crafting grid: shift-click moves the stack into the inventory (main first, then hotbar).
            ItemStack stack = pos.slot().get();
            if (stack == null) return;
            InventoryPlayer inventory = inventory();
            mergeInto(inventory.mainInventory, InventoryPlayer.HOTBAR_SIZE, InventoryPlayer.MAIN_SIZE, stack);
            mergeInto(inventory.mainInventory, 0, InventoryPlayer.HOTBAR_SIZE, stack);
            pos.slot().set(stack.isEmpty() ? null : stack);
            return;
        }
        super.shiftClick(pos);
    }

    private static void mergeInto(ItemStack[] slots, int from, int to, ItemStack stack) {
        for (int i = from; i < to && !stack.isEmpty(); i++)
            if (slots[i] != null && slots[i].canMerge(stack)) slots[i].mergeFrom(stack);
        for (int i = from; i < to && !stack.isEmpty(); i++)
            if (slots[i] == null) slots[i] = stack.split(stack.count());
    }

    public ItemStack[] craftMatrix() { return grid.cells(); }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        org.lwjgl.opengl.GL11.glColor4f(1, 1, 1, 1);
        mc.textures().bind(BACKGROUND);
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);
        mc.drawPlayerOnScreen(guiLeft + 51, guiTop + 75, 30, guiLeft + 51 - mouseX, guiTop + 75 - 50 - mouseY);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRendererObj.drawString(I18n.format("container.crafting"), 86, 16, 0x404040);
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        if (grid != null) grid.returnAll(inventory(), mc::dropPlayerItem);
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }
}
