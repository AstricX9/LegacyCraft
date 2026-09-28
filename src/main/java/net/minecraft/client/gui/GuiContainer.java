package net.minecraft.client.gui;

import net.minecraft.client.settings.KeyCodes;
import net.minecraft.inventory.CraftingGrid;
import net.minecraft.inventory.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.inventory.SlotClicks;
import net.minecraft.item.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

/**
 * Base for inventory-style screens (master context §21): a background of
 * {@code xSize} x {@code ySize} centred on screen, slots drawn at 16x16 with a
 * translucent white highlight under the mouse, a tooltip for the hovered
 * stack, and the cursor stack following the mouse. Clicks follow
 * {@link SlotClicks}; Escape or the inventory key closes the screen and any
 * stack still on the cursor goes back into the inventory.
 */
public abstract class GuiContainer extends GuiScreen {
    /** A slot placed on the screen at (x, y) relative to the background's top-left. */
    public record SlotPos(Slot slot, int x, int y, Kind kind) {}
    public enum Kind { HOTBAR, MAIN, ARMOR, OTHER }

    protected int xSize = 176, ySize = 166;
    protected int guiLeft, guiTop;
    protected final List<SlotPos> slots = new ArrayList<>();

    protected InventoryPlayer inventory() { return mc.playerInventory(); }

    @Override
    public void initGui() {
        guiLeft = (width - xSize) / 2;
        guiTop = (height - ySize) / 2;
        slots.clear();
        buildSlots();
    }

    /** Adds this screen's slots to {@link #slots}. */
    protected abstract void buildSlots();

    protected abstract void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY);

    /** Text and overlays in background-relative coordinates. */
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {}

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        trackMouse(mouseX, mouseY);
        drawDefaultBackground();
        drawGuiContainerBackgroundLayer(partialTicks, mouseX, mouseY);
        super.drawScreen(mouseX, mouseY, partialTicks);
        glPushMatrix();
        glTranslatef(guiLeft, guiTop, 0);
        SlotPos hovered = null;
        for (SlotPos pos : slots) {
            drawSlotStack(pos.slot().get(), pos.x(), pos.y());
            if (isMouseOverSlot(pos, mouseX, mouseY)) {
                hovered = pos;
                glDisable(GL_DEPTH_TEST);
                drawGradientRect(pos.x(), pos.y(), pos.x() + 16, pos.y() + 16, 0x80FFFFFF, 0x80FFFFFF);
            }
        }
        drawGuiContainerForegroundLayer(mouseX, mouseY);
        glPopMatrix();
        drawCursorAndTooltip(hovered == null ? null : hovered.slot().get(), mouseX, mouseY);
    }

    protected void drawSlotStack(ItemStack stack, int x, int y) {
        if (stack == null) return;
        mc.itemRenderer().renderItemAndEffectIntoGUI(stack, x, y);
        mc.itemRenderer().renderItemOverlays(fontRendererObj, stack, x, y);
    }

    /** The held stack at the mouse, then the tooltip of the hovered stack when nothing is held. */
    protected void drawCursorAndTooltip(ItemStack hoveredStack, int mouseX, int mouseY) {
        ItemStack cursor = inventory().getItemStack();
        if (cursor != null) {
            glPushMatrix();
            glTranslatef(0, 0, 200);
            drawSlotStack(cursor, mouseX - 8, mouseY - 8);
            glPopMatrix();
        } else if (hoveredStack != null) {
            drawHoveringText(tooltip(hoveredStack), mouseX, mouseY);
        }
    }

    /**
     * Tooltip lines as 1.8.9's ItemStack.getTooltip builds them: the display
     * name (with " (#0001/1)" or " (#0276)" when advanced tooltips are on,
     * F3+H), then "Durability: x / y" for a damaged item when advanced. Lines
     * after the first are grey. Item rarity colours are not modelled; every
     * name is white (DEV-015).
     */
    protected List<String> tooltip(ItemStack stack) {
        List<String> lines = new ArrayList<>();
        Item item = stack.item();
        String name = mc.itemName(item);
        boolean advanced = mc.settings().advancedItemTooltips;
        CreativeTabs tabs = mc.creativeTabs();
        if (advanced && tabs != null && tabs.entry(item) != null) {
            CreativeTabs.Entry entry = tabs.entry(item);
            name += tabs.hasSubtypes(item) ? String.format(" (#%04d/%d)", entry.id(), entry.meta()) : String.format(" (#%04d)", entry.id());
        }
        lines.add(name);
        if (advanced && item.isDamageable() && stack.damage() > 0)
            lines.add(GRAY + "Durability: " + (item.maxDamage() - stack.damage()) + " / " + item.maxDamage());
        return lines;
    }

    protected static final String GRAY = FontRenderer.FORMAT_CHAR + "7";

    /** Vanilla's isPointInRegion: a 1px margin around the 16x16 slot. */
    protected boolean isMouseOverSlot(SlotPos pos, int mouseX, int mouseY) {
        int x = mouseX - guiLeft, y = mouseY - guiTop;
        return x >= pos.x() - 1 && x < pos.x() + 16 + 1 && y >= pos.y() - 1 && y < pos.y() + 16 + 1;
    }

    protected SlotPos slotAt(int mouseX, int mouseY) {
        for (SlotPos pos : slots) if (isMouseOverSlot(pos, mouseX, mouseY)) return pos;
        return null;
    }

    protected boolean isOutside(int mouseX, int mouseY) {
        return mouseX < guiLeft || mouseY < guiTop || mouseX >= guiLeft + xSize || mouseY >= guiTop + ySize;
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) {
        trackMouse(mouseX, mouseY);
        super.mouseClicked(mouseX, mouseY, button);
        SlotPos pos = slotAt(mouseX, mouseY);
        if (pos != null) {
            handleSlotClick(pos, button, isShiftKeyDown());
        } else if (isOutside(mouseX, mouseY) && inventory().getItemStack() != null) {
            dropCursor(button);
        }
    }

    protected void handleSlotClick(SlotPos pos, int button, boolean shift) {
        if (pos.slot() instanceof CraftingGrid.ResultSlot result && button != 2) {
            if (shift) result.grid().shiftClick(inventory(), craftsBackwards(), mc::dropPlayerItem);
            else result.grid().click(inventory(), button, mc::dropPlayerItem);
            return;
        }
        if (button == 2) {
            if (mc.isCreative()) SlotClicks.cloneStack(pos.slot(), inventory());
            return;
        }
        if (shift) {
            shiftClick(pos);
            return;
        }
        SlotClicks.click(pos.slot(), inventory(), button);
    }

    /** Whether shift-crafting fills the hotbar from its last slot first (the crafting table does). */
    protected boolean craftsBackwards() { return false; }

    protected void shiftClick(SlotPos pos) {
        SlotClicks.shiftClickPlayerSlot(inventory(), pos.slot(), pos.kind() == Kind.HOTBAR, pos.kind() == Kind.ARMOR);
    }

    /** Clicking outside the window throws the held stack: all of it with left, one with right. */
    protected void dropCursor(int button) {
        ItemStack cursor = inventory().getItemStack();
        if (button == 1 && cursor.count() > 1) {
            mc.dropPlayerItem(cursor.split(1));
        } else {
            inventory().setItemStack(null);
            mc.dropPlayerItem(cursor);
        }
    }

    /** The drop key over a slot throws one item from it, or the whole stack with Ctrl. */
    protected void dropFromSlot(SlotPos pos, boolean wholeStack) {
        if (pos.slot() instanceof CraftingGrid.ResultSlot result) {
            result.grid().throwResult(inventory(), wholeStack, mc::dropPlayerItem);
            return;
        }
        ItemStack stack = pos.slot().get();
        if (stack == null) return;
        ItemStack thrown = wholeStack ? stack.split(stack.count()) : stack.split(1);
        pos.slot().set(stack.isEmpty() ? null : stack);
        mc.dropPlayerItem(thrown);
    }

    @Override
    public void keyPressed(int key, int modifiers) {
        int legacy = KeyCodes.fromGlfwKey(key);
        if (key == GLFW_KEY_ESCAPE || legacy != KeyCodes.NONE && legacy == mc.settings().keyBindInventory.getKeyCode()) {
            mc.displayGuiScreen(null);
            return;
        }
        SlotPos hovered = slotAt(mouseXForKeys(), mouseYForKeys());
        if (hovered != null && inventory().getItemStack() == null && legacy != KeyCodes.NONE
            && legacy == mc.settings().keyBindDrop.getKeyCode()) {
            dropFromSlot(hovered, (modifiers & GLFW_MOD_CONTROL) != 0);
            return;
        }
        if (hovered != null && inventory().getItemStack() == null) {
            for (int i = 0; i < 9; i++) {
                if (legacy != KeyCodes.NONE && legacy == mc.settings().keyBindsHotbar[i].getKeyCode()) {
                    numberKey(hovered, i);
                    return;
                }
            }
        }
    }

    protected void numberKey(SlotPos pos, int hotbarIndex) {
        if (pos.slot() instanceof CraftingGrid.ResultSlot result) result.grid().toHotbar(inventory(), hotbarIndex, mc::dropPlayerItem);
        else SlotClicks.swapWithHotbar(pos.slot(), inventory(), hotbarIndex);
    }

    private int lastMouseX, lastMouseY;
    protected int mouseXForKeys() { return lastMouseX; }
    protected int mouseYForKeys() { return lastMouseY; }

    /** Remembers the pointer so number keys know which slot is hovered. */
    public void trackMouse(int mouseX, int mouseY) { lastMouseX = mouseX; lastMouseY = mouseY; }

    @Override
    public void mouseDragged(int mouseX, int mouseY, int button, long time) { trackMouse(mouseX, mouseY); }

    @Override
    public void onGuiClosed() {
        ItemStack cursor = inventory().getItemStack();
        if (cursor != null) {
            inventory().setItemStack(null);
            inventory().addItemStackToInventory(cursor);
        }
    }
}
