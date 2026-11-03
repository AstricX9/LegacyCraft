package net.minecraft.client.gui;

import net.minecraft.inventory.Chest;
import net.minecraft.inventory.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * A chest (1.8.9 GuiChest / ContainerChest, HYPOTHESIS: recalled): three
 * rows of 27 slots, or six for a large chest (the halves in
 * InventoryLargeChest order, the one further west or north first), over the
 * player's inventory, from {@code container/generic_54.png}. Shift-click
 * moves a chest stack into the inventory (hotbar from the right first) and an
 * inventory stack into the chest. Opening raises the lids; it closes when a
 * half is gone or the player is more than 8 blocks away.
 */
public class GuiChest extends GuiContainer {
    static final ResourceLocation BACKGROUND = new ResourceLocation("minecraft:textures/gui/container/generic_54.png");
    /** The chests shown, top half first, with their positions. */
    private final List<Chest> chests = new ArrayList<>();
    private final List<int[]> positions = new ArrayList<>();
    private final String block;
    private final int rows;
    private boolean opened;

    /** @param halves one or two positions, already in large-chest order */
    public GuiChest(String block, int[]... halves) {
        this.block = block;
        for (int[] half : halves) positions.add(half);
        rows = halves.length * 3;
        xSize = 176;
        ySize = 114 + rows * 18;
    }

    @Override
    protected void buildSlots() {
        if (chests.isEmpty())
            for (int[] p : positions) {
                Chest chest = mc.chestAt(p[0], p[1], p[2]);
                chests.add(chest != null ? chest : new Chest());
            }
        if (!opened) {
            opened = true;
            for (Chest chest : chests) chest.viewers++;
        }
        for (int row = 0; row < rows; row++)
            for (int column = 0; column < 9; column++)
                slots.add(new SlotPos(chests.get(row / 3).slot(row % 3 * 9 + column), 8 + column * 18, 18 + row * 18, Kind.OTHER));
        int offset = (rows - 4) * 18;
        InventoryPlayer inventory = inventory();
        for (int row = 0; row < 3; row++)
            for (int column = 0; column < 9; column++)
                slots.add(new SlotPos(Slot.of(inventory.mainInventory, column + (row + 1) * 9), 8 + column * 18, 103 + row * 18 + offset, Kind.MAIN));
        for (int column = 0; column < 9; column++)
            slots.add(new SlotPos(Slot.of(inventory.mainInventory, column), 8 + column * 18, 161 + offset, Kind.HOTBAR));
    }

    /** ContainerChest.transferStackInSlot. */
    @Override
    protected void shiftClick(SlotPos pos) {
        ItemStack stack = pos.slot().get();
        if (stack == null) return;
        int chestSlots = rows * 9;
        List<Slot> targets = new ArrayList<>();
        if (pos.kind() == Kind.OTHER) {
            // mergeItemStack(stack, rows * 9, size, true): the player's slots from the last one back.
            for (int i = slots.size() - 1; i >= chestSlots; i--) targets.add(slots.get(i).slot());
        } else {
            for (int i = 0; i < chestSlots; i++) targets.add(slots.get(i).slot());
        }
        for (Slot target : targets) {
            if (stack.isEmpty()) break;
            ItemStack there = target.get();
            if (there != null && there.canMerge(stack)) {
                there.mergeFrom(stack);
                target.set(there);
            }
        }
        for (Slot target : targets) {
            if (stack.isEmpty()) break;
            if (target.get() == null) target.set(stack.split(stack.count()));
        }
        pos.slot().set(stack.isEmpty() ? null : stack);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        for (int[] p : positions)
            if (!mc.canInteractWith(p[0], p[1], p[2], block)) {
                mc.displayGuiScreen(null);
                return;
            }
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        if (opened) for (Chest chest : chests) chest.viewers = Math.max(0, chest.viewers - 1);
        opened = false;
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        org.lwjgl.opengl.GL11.glColor4f(1, 1, 1, 1);
        mc.textures().bind(BACKGROUND);
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, rows * 18 + 17);
        drawTexturedModalRect(guiLeft, guiTop + rows * 18 + 17, 0, 126, xSize, 96);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRendererObj.drawString(I18n.format(rows > 3 ? "container.chestDouble" : "container.chest"), 8, 6, 0x404040);
        fontRendererObj.drawString(I18n.format("container.inventory"), 8, ySize - 96 + 2, 0x404040);
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }
}
