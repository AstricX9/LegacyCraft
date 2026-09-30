package net.minecraft.client.gui;

import net.minecraft.inventory.Anvil;
import net.minecraft.inventory.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

/**
 * The anvil (1.8.9 GuiRepair / ContainerRepair; HYPOTHESIS: recalled
 * positions and colours): two inputs, a result and a name box above the
 * inventory, from {@code container/anvil.png}. Putting an item in the first
 * slot fills the box with its name; editing the box renames the result. The
 * cost shows in green, or red when the player lacks the levels (or "Too
 * Expensive!"); a red cross covers the arrow while there is input but no
 * result. The inputs go back to the player on close.
 */
public class GuiRepair extends GuiContainer {
    static final ResourceLocation BACKGROUND = new ResourceLocation("minecraft:textures/gui/container/anvil.png");
    private final int anvilX, anvilY, anvilZ;
    private Anvil anvil;
    private GuiTextField nameField;
    private ItemStack lastFirst;

    public GuiRepair(int x, int y, int z) {
        anvilX = x;
        anvilY = y;
        anvilZ = z;
        xSize = 176;
        ySize = 166;
    }

    public Anvil anvil() { return anvil; }
    public GuiTextField nameField() { return nameField; }

    @Override
    public void initGui() {
        super.initGui();
        String text = nameField == null ? "" : nameField.getText();
        nameField = new GuiTextField(0, fontRendererObj, guiLeft + 62, guiTop + 24, 103, 12);
        nameField.setTextColor(-1);
        nameField.setDisabledTextColour(-1);
        nameField.setEnableBackgroundDrawing(false);
        nameField.setMaxStringLength(30);
        nameField.setText(text);
        nameField.setFocused(true);
        nameField.setEnabled(anvil.input(0) != null);
    }

    @Override
    protected void buildSlots() {
        InventoryPlayer inventory = inventory();
        if (anvil == null) anvil = new Anvil(mc.isCreative());
        Slot first = anvil.inputSlot(0);
        slots.add(new SlotPos(new Slot() {
            public ItemStack get() { return first.get(); }
            public void set(ItemStack stack) {
                first.set(stack);
                firstChanged();
            }
        }, 27, 47, Kind.OTHER));
        slots.add(new SlotPos(anvil.inputSlot(1), 76, 47, Kind.OTHER));
        slots.add(new SlotPos(anvil.outputSlot(), 134, 47, Kind.OTHER));
        for (int row = 0; row < 3; row++)
            for (int column = 0; column < 9; column++)
                slots.add(new SlotPos(Slot.of(inventory.mainInventory, column + (row + 1) * 9), 8 + column * 18, 84 + row * 18, Kind.MAIN));
        for (int column = 0; column < 9; column++)
            slots.add(new SlotPos(Slot.of(inventory.mainInventory, column), 8 + column * 18, 142, Kind.HOTBAR));
    }

    /** GuiRepair.sendSlotContents for slot 0: a new first item puts its name in the box. */
    private void firstChanged() {
        ItemStack stack = anvil.input(0);
        if (stack == lastFirst) return;
        lastFirst = stack;
        if (nameField == null) return;
        nameField.setText(stack == null ? "" : mc.stackName(stack));
        nameField.setEnabled(stack != null);
        if (stack != null) rename();
    }

    /** GuiRepair.renameItem: the item's own unchanged name counts as no name. */
    private void rename() {
        String text = nameField.getText();
        ItemStack first = anvil.input(0);
        if (first != null && !first.hasCustomName() && text.equals(mc.stackName(first))) text = "";
        anvil.setName(text);
    }

    private static boolean isOutput(SlotPos pos) { return pos.slot() instanceof Anvil.OutputSlot; }

    /** Takes the result: the anvil charges the levels and may wear. */
    private void tookOutput() {
        int levels = anvil.take();
        lastFirst = null;
        mc.onAnvilUsed(anvilX, anvilY, anvilZ, levels);
        nameField.setText("");
        nameField.setEnabled(false);
        anvil.setName("");
    }

    @Override
    protected void handleSlotClick(SlotPos pos, int button, boolean shift) {
        if (isOutput(pos) && button != 2) {
            ItemStack output = anvil.output();
            if (output == null || !anvil.canTake(mc.experienceLevel())) return;
            if (shift) {
                ItemStack moving = output.copy();
                GuiEnchantment.mergeBackwards(inventory().mainInventory, moving);
                if (moving.count() == output.count()) return;
                if (!moving.isEmpty()) mc.dropPlayerItem(moving);
            } else {
                ItemStack cursor = inventory().getItemStack();
                if (cursor == null) inventory().setItemStack(output);
                else if (cursor.canMerge(output) && cursor.count() + output.count() <= cursor.item().maxStackSize()) cursor.mergeFrom(output.copy());
                else return;
            }
            tookOutput();
            return;
        }
        super.handleSlotClick(pos, button, shift);
    }

    /** ContainerRepair.transferStackInSlot: inputs to the inventory, inventory into the inputs. */
    @Override
    protected void shiftClick(SlotPos pos) {
        ItemStack stack = pos.slot().get();
        if (stack == null) return;
        if (pos.kind() == Kind.OTHER) {
            ItemStack[] main = inventory().mainInventory;
            for (int i = InventoryPlayer.HOTBAR_SIZE; i < InventoryPlayer.MAIN_SIZE && !stack.isEmpty(); i++)
                if (main[i] != null && main[i].canMerge(stack)) main[i].mergeFrom(stack);
            for (int i = 0; i < InventoryPlayer.HOTBAR_SIZE && !stack.isEmpty(); i++)
                if (main[i] != null && main[i].canMerge(stack)) main[i].mergeFrom(stack);
            for (int i = InventoryPlayer.HOTBAR_SIZE; i < InventoryPlayer.MAIN_SIZE && !stack.isEmpty(); i++)
                if (main[i] == null) main[i] = stack.split(stack.count());
            for (int i = 0; i < InventoryPlayer.HOTBAR_SIZE && !stack.isEmpty(); i++)
                if (main[i] == null) main[i] = stack.split(stack.count());
            pos.slot().set(stack.isEmpty() ? null : stack);
            return;
        }
        for (int i = 0; i < 2 && !stack.isEmpty(); i++) {
            ItemStack there = slots.get(i).slot().get();
            if (there != null && there.canMerge(stack)) {
                there.mergeFrom(stack);
                slots.get(i).slot().set(there);
            }
        }
        for (int i = 0; i < 2 && !stack.isEmpty(); i++)
            if (slots.get(i).slot().get() == null) slots.get(i).slot().set(stack.split(stack.count()));
        pos.slot().set(stack.isEmpty() ? null : stack);
    }

    @Override
    protected void dropFromSlot(SlotPos pos, boolean wholeStack) {
        if (isOutput(pos)) {
            ItemStack output = anvil.output();
            if (output == null || !anvil.canTake(mc.experienceLevel())) return;
            mc.dropPlayerItem(output);
            tookOutput();
            return;
        }
        super.dropFromSlot(pos, wholeStack);
    }

    @Override
    protected void numberKey(SlotPos pos, int hotbarIndex) {
        if (isOutput(pos)) {
            ItemStack output = anvil.output();
            if (output == null || !anvil.canTake(mc.experienceLevel())) return;
            InventoryPlayer inventory = inventory();
            if (inventory.mainInventory[hotbarIndex] != null) return;
            inventory.mainInventory[hotbarIndex] = output;
            tookOutput();
            return;
        }
        super.numberKey(pos, hotbarIndex);
    }

    @Override
    protected boolean canDragIntoSlot(SlotPos pos) { return !isOutput(pos) && super.canDragIntoSlot(pos); }

    @Override
    protected boolean canMergeSlot(SlotPos pos) { return !isOutput(pos) && super.canMergeSlot(pos); }

    /** Every printable key edits the name (so E types an "e"); Escape still closes. */
    @Override
    public void keyPressed(int key, int modifiers) {
        if (key != GLFW_KEY_ESCAPE && nameField.isFocused()) {
            String before = nameField.getText();
            if (nameField.keyPressed(key, modifiers)) {
                if (!before.equals(nameField.getText())) rename();
                return;
            }
            if (key >= GLFW_KEY_SPACE && key <= GLFW_KEY_GRAVE_ACCENT) return;
        }
        super.keyPressed(key, modifiers);
    }

    @Override
    public void charTyped(char character) {
        String before = nameField.getText();
        if (nameField.charTyped(character) && !before.equals(nameField.getText())) rename();
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        nameField.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        nameField.updateCursorCounter();
        if (!mc.canInteractWith(anvilX, anvilY, anvilZ, "anvil")) mc.displayGuiScreen(null);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        glColor4f(1, 1, 1, 1);
        mc.textures().bind(BACKGROUND);
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);
        drawTexturedModalRect(guiLeft + 59, guiTop + 20, 0, ySize + (anvil.input(0) != null ? 0 : 16), 110, 16);
        if ((anvil.input(0) != null || anvil.input(1) != null) && anvil.output() == null)
            drawTexturedModalRect(guiLeft + 99, guiTop + 45, xSize, 0, 28, 21);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        glDisable(GL_LIGHTING);
        glDisable(GL_BLEND);
        nameField.drawTextBox();
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRendererObj.drawString(I18n.format("container.repair"), 60, 6, 0x404040);
        String cost = costText();
        if (cost != null) {
            int color = costColor();
            int shadow = 0xFF000000 | (color & 0xFCFCFC) >> 2;
            int x = xSize - 8 - fontRendererObj.getStringWidth(cost), y = 67;
            fontRendererObj.drawString(cost, x, y + 1, shadow);
            fontRendererObj.drawString(cost, x + 1, y, shadow);
            fontRendererObj.drawString(cost, x + 1, y + 1, shadow);
            fontRendererObj.drawString(cost, x, y, color);
        }
    }

    /** The cost line, or null when none shows (nothing to pay, or no result to take). */
    String costText() {
        if (anvil.cost() <= 0) return null;
        if (anvil.cost() >= 40 && !mc.isCreative()) return I18n.format("container.repair.expensive");
        if (anvil.output() == null) return null;
        return I18n.format("container.repair.cost", anvil.cost());
    }

    /** Green when the result can be taken, else red. */
    int costColor() {
        if (anvil.cost() >= 40 && !mc.isCreative()) return 16736352;
        return anvil.canTake(mc.experienceLevel()) ? 8453920 : 16736352;
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        if (anvil != null) anvil.returnAll(inventory(), mc::dropPlayerItem);
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }
}
