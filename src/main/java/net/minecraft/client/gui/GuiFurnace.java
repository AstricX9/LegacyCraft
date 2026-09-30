package net.minecraft.client.gui;

import net.minecraft.inventory.Furnace;
import net.minecraft.inventory.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.util.ResourceLocation;

/**
 * The furnace (1.8.9 GuiFurnace / ContainerFurnace): input above the flame,
 * fuel below it and the output right of the arrow, above the inventory, from
 * {@code container/furnace.png}. The flame shows the fire left of the fuel
 * that lit it and the arrow the cook progress. It closes when the furnace is
 * gone or the player is more than 8 blocks from it; the furnace keeps its
 * contents and keeps burning with the screen closed.
 *
 * <p>Shift-click (ContainerFurnace.transferStackInSlot): the output goes to
 * the inventory from the last hotbar slot backwards; the input and fuel go to
 * the inventory; from the inventory, anything that smelts goes to the input,
 * else fuel to the fuel slot, else between the main slots and the hotbar.
 * Slot positions and sprite offsets are HYPOTHESIS.
 */
public class GuiFurnace extends GuiContainer {
    static final ResourceLocation BACKGROUND = new ResourceLocation("minecraft:textures/gui/container/furnace.png");
    private final int furnaceX, furnaceY, furnaceZ;
    private Furnace furnace;

    public GuiFurnace(int x, int y, int z) {
        furnaceX = x;
        furnaceY = y;
        furnaceZ = z;
        xSize = 176;
        ySize = 166;
    }

    @Override
    protected void buildSlots() {
        InventoryPlayer inventory = inventory();
        if (furnace == null) furnace = mc.furnaceAt(furnaceX, furnaceY, furnaceZ);
        if (furnace == null) furnace = new Furnace(mc.smelting(), mc::itemByName);
        slots.add(new SlotPos(furnace.inputSlot(), 56, 17, Kind.OTHER));
        slots.add(new SlotPos(furnace.fuelSlot(), 56, 53, Kind.OTHER));
        slots.add(new SlotPos(furnace.outputSlot(mc::onItemSmelted), 116, 35, Kind.OTHER));
        for (int row = 0; row < 3; row++)
            for (int column = 0; column < 9; column++)
                slots.add(new SlotPos(Slot.of(inventory.mainInventory, column + (row + 1) * 9), 8 + column * 18, 84 + row * 18, Kind.MAIN));
        for (int column = 0; column < 9; column++)
            slots.add(new SlotPos(Slot.of(inventory.mainInventory, column), 8 + column * 18, 142, Kind.HOTBAR));
    }

    public Furnace furnace() { return furnace; }

    private static boolean isOutput(SlotPos pos) { return pos.slot() instanceof Furnace.OutputSlot; }

    @Override
    protected void shiftClick(SlotPos pos) {
        ItemStack stack = pos.slot().get();
        if (stack == null) return;
        InventoryPlayer inventory = inventory();
        if (isOutput(pos)) {
            mergeBackwards(inventory.mainInventory, stack);
        } else if (pos.kind() == Kind.OTHER) {
            merge(inventory.mainInventory, InventoryPlayer.HOTBAR_SIZE, InventoryPlayer.MAIN_SIZE, stack);
            merge(inventory.mainInventory, 0, InventoryPlayer.HOTBAR_SIZE, stack);
        } else if (furnace.recipes() != null && furnace.recipes().result(stack) != null) {
            mergeInto(slots.get(0).slot(), stack);
        } else if (FurnaceRecipes.isFuel(stack)) {
            mergeInto(slots.get(1).slot(), stack);
        } else if (pos.kind() == Kind.MAIN) {
            merge(inventory.mainInventory, 0, InventoryPlayer.HOTBAR_SIZE, stack);
        } else {
            merge(inventory.mainInventory, InventoryPlayer.HOTBAR_SIZE, InventoryPlayer.MAIN_SIZE, stack);
        }
        pos.slot().set(stack.isEmpty() ? null : stack);
    }

    /** Container.mergeItemStack into one furnace slot: onto a matching stack, else into it when empty. */
    private static void mergeInto(Slot slot, ItemStack stack) {
        ItemStack there = slot.get();
        if (there == null) {
            slot.set(stack.split(stack.count()));
        } else if (there.canMerge(stack)) {
            there.mergeFrom(stack);
            slot.set(there);
        }
    }

    /** Main slots [from, to): onto matching stacks, then the first empty slot. */
    private static void merge(ItemStack[] slots, int from, int to, ItemStack stack) {
        for (int i = from; i < to && !stack.isEmpty(); i++)
            if (slots[i] != null && slots[i].canMerge(stack)) slots[i].mergeFrom(stack);
        for (int i = from; i < to && !stack.isEmpty(); i++)
            if (slots[i] == null) slots[i] = stack.split(stack.count());
    }

    /** mergeItemStack(stack, 3, 39, true): hotbar from its last slot, then the main slots from the bottom right. */
    private static void mergeBackwards(ItemStack[] slots, ItemStack stack) {
        int[] order = new int[InventoryPlayer.MAIN_SIZE];
        int n = 0;
        for (int i = InventoryPlayer.HOTBAR_SIZE - 1; i >= 0; i--) order[n++] = i;
        for (int i = InventoryPlayer.MAIN_SIZE - 1; i >= InventoryPlayer.HOTBAR_SIZE; i--) order[n++] = i;
        for (int i : order) {
            if (stack.isEmpty()) return;
            if (slots[i] != null && slots[i].canMerge(stack)) slots[i].mergeFrom(stack);
        }
        for (int i : order) {
            if (stack.isEmpty()) return;
            if (slots[i] == null) slots[i] = stack.split(stack.count());
        }
    }

    /** Clicking the output while holding the same item takes all of it, if it all fits on the cursor. */
    @Override
    protected void handleSlotClick(SlotPos pos, int button, boolean shift) {
        ItemStack cursor = inventory().getItemStack();
        if (isOutput(pos) && !shift && button != 2 && cursor != null) {
            ItemStack output = pos.slot().get();
            if (output != null && output.canMerge(cursor) && output.item().maxStackSize() > 1
                && cursor.count() + output.count() <= cursor.item().maxStackSize()) {
                cursor.mergeFrom(output);
                pos.slot().set(null);
            }
            return;
        }
        super.handleSlotClick(pos, button, shift);
    }

    /**
     * A number key over the output moves it to that hotbar slot; an item
     * already there moves to the first empty inventory slot, and nothing
     * happens when there is none (Container.slotClick SWAP).
     */
    @Override
    protected void numberKey(SlotPos pos, int hotbarIndex) {
        if (!isOutput(pos)) {
            super.numberKey(pos, hotbarIndex);
            return;
        }
        ItemStack output = pos.slot().get();
        if (output == null) return;
        InventoryPlayer inventory = inventory();
        ItemStack inHotbar = inventory.mainInventory[hotbarIndex];
        if (inHotbar != null) {
            int empty = -1;
            for (int i = 0; i < InventoryPlayer.MAIN_SIZE && empty < 0; i++) if (inventory.mainInventory[i] == null) empty = i;
            if (empty < 0) return;
            inventory.mainInventory[hotbarIndex] = null;
            inventory.addItemStackToInventory(inHotbar);
        }
        inventory.mainInventory[hotbarIndex] = output;
        pos.slot().set(null);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (!mc.canInteractWith(furnaceX, furnaceY, furnaceZ, "furnace")
            && !mc.canInteractWith(furnaceX, furnaceY, furnaceZ, "lit_furnace")) mc.displayGuiScreen(null);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        org.lwjgl.opengl.GL11.glColor4f(1, 1, 1, 1);
        mc.textures().bind(BACKGROUND);
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);
        if (furnace.isBurning()) {
            int flame = furnace.burnLeftScaled(13);
            drawTexturedModalRect(guiLeft + 56, guiTop + 36 + 12 - flame, 176, 12 - flame, 14, flame + 1);
        }
        int arrow = furnace.cookProgressScaled(24);
        drawTexturedModalRect(guiLeft + 79, guiTop + 34, 176, 14, arrow + 1, 16);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        String title = I18n.format("container.furnace");
        fontRendererObj.drawString(title, xSize / 2 - fontRendererObj.getStringWidth(title) / 2, 6, 0x404040);
        fontRendererObj.drawString(I18n.format("container.inventory"), 8, ySize - 96 + 2, 0x404040);
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }
}
