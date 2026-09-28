package net.minecraft.client.gui;

import net.minecraft.inventory.CraftingGrid;
import net.minecraft.inventory.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

/**
 * The crafting table (1.8.9 GuiCrafting / ContainerWorkbench): a 3x3 grid and
 * its result above the inventory, from {@code container/crafting_table.png}.
 * It closes when the table is gone or the player is more than 8 blocks from
 * it; what is left in the grid goes back to the inventory. Shift-click moves
 * between the main slots and the hotbar (armour is not equipped from here),
 * and moves grid items into the inventory. Slot positions are HYPOTHESIS.
 */
public class GuiCrafting extends GuiContainer {
    static final ResourceLocation BACKGROUND = new ResourceLocation("minecraft:textures/gui/container/crafting_table.png");
    private final int tableX, tableY, tableZ;
    private CraftingGrid grid;

    public GuiCrafting(int x, int y, int z) {
        tableX = x;
        tableY = y;
        tableZ = z;
        xSize = 176;
        ySize = 166;
    }

    @Override
    protected void buildSlots() {
        InventoryPlayer inventory = inventory();
        if (grid == null) grid = new CraftingGrid(3, 3, mc.crafting(), mc::itemByName);
        slots.add(new SlotPos(grid.resultSlot(), 124, 35, Kind.OTHER));
        for (int row = 0; row < 3; row++)
            for (int column = 0; column < 3; column++)
                slots.add(new SlotPos(grid.cell(column + row * 3), 30 + column * 18, 17 + row * 18, Kind.OTHER));
        for (int row = 0; row < 3; row++)
            for (int column = 0; column < 9; column++)
                slots.add(new SlotPos(Slot.of(inventory.mainInventory, column + (row + 1) * 9), 8 + column * 18, 84 + row * 18, Kind.MAIN));
        for (int column = 0; column < 9; column++)
            slots.add(new SlotPos(Slot.of(inventory.mainInventory, column), 8 + column * 18, 142, Kind.HOTBAR));
    }

    public CraftingGrid grid() { return grid; }

    @Override
    protected boolean craftsBackwards() { return true; }

    @Override
    protected void shiftClick(SlotPos pos) {
        ItemStack stack = pos.slot().get();
        if (stack == null) return;
        InventoryPlayer inventory = inventory();
        switch (pos.kind()) {
            // ContainerWorkbench.transferStackInSlot: grid to main then hotbar, main to hotbar, hotbar to main.
            case OTHER -> {
                merge(inventory.mainInventory, InventoryPlayer.HOTBAR_SIZE, InventoryPlayer.MAIN_SIZE, stack);
                merge(inventory.mainInventory, 0, InventoryPlayer.HOTBAR_SIZE, stack);
            }
            case MAIN -> merge(inventory.mainInventory, 0, InventoryPlayer.HOTBAR_SIZE, stack);
            default -> merge(inventory.mainInventory, InventoryPlayer.HOTBAR_SIZE, InventoryPlayer.MAIN_SIZE, stack);
        }
        pos.slot().set(stack.isEmpty() ? null : stack);
    }

    private static void merge(ItemStack[] slots, int from, int to, ItemStack stack) {
        for (int i = from; i < to && !stack.isEmpty(); i++)
            if (slots[i] != null && slots[i].canMerge(stack)) slots[i].mergeFrom(stack);
        for (int i = from; i < to && !stack.isEmpty(); i++)
            if (slots[i] == null) slots[i] = stack.split(stack.count());
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (!mc.canInteractWith(tableX, tableY, tableZ, "crafting_table")) mc.displayGuiScreen(null);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        org.lwjgl.opengl.GL11.glColor4f(1, 1, 1, 1);
        mc.textures().bind(BACKGROUND);
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRendererObj.drawString(I18n.format("container.crafting"), 28, 6, 0x404040);
        fontRendererObj.drawString(I18n.format("container.inventory"), 8, ySize - 96 + 2, 0x404040);
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        if (grid != null) grid.returnAll(inventory(), mc::dropPlayerItem);
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }
}
