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
 *
 * <p>The mouse works as in 1.8.9's GuiContainer (HYPOTHESIS: recalled
 * rules). With an empty hand a click acts when pressed. Holding a stack, a
 * press starts a drag and the release decides: dragging across slots
 * spreads the stack evenly (left button), one per slot (right) or a full
 * stack per slot (middle, Creative), previewed as you drag with the rest
 * left on the cursor; letting go without having dragged over a slot is an
 * ordinary click. Double-clicking a slot with the left button gathers
 * matching items from the other slots onto the cursor (part-filled stacks
 * first), and shift-double-click shift-clicks every matching stack of that
 * inventory.
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
        ItemStack cursor = inventory() == null ? null : inventory().getItemStack();
        for (SlotPos pos : slots) {
            ItemStack shown = pos.slot().get();
            String countText = null;
            if (dragSplitting && dragSlots.size() > 1 && dragSlots.contains(pos) && cursor != null) {
                // The drag preview: what the slot will hold, over a white highlight; yellow when capped.
                glDisable(GL_DEPTH_TEST);
                drawRect(pos.x(), pos.y(), pos.x() + 16, pos.y() + 16, 0x80FFFFFF);
                int have = shown == null ? 0 : shown.count();
                int wanted = dragShare(cursor, dragSlots.size()) + have, capped = Math.min(wanted, cap(pos, cursor));
                if (capped < wanted) countText = FontRenderer.FORMAT_CHAR + "e" + capped;
                shown = cursor.withCount(Math.max(1, capped));
            }
            drawSlotStack(shown, pos.x(), pos.y(), countText);
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

    protected void drawSlotStack(ItemStack stack, int x, int y) { drawSlotStack(stack, x, y, null); }

    /** With {@code countText}, that text replaces the stack size in the corner. */
    protected void drawSlotStack(ItemStack stack, int x, int y, String countText) {
        if (stack == null) return;
        mc.itemRenderer().renderItemAndEffectIntoGUI(stack, x, y);
        mc.itemRenderer().renderItemOverlays(fontRendererObj, stack, x, y, countText);
    }

    /** The held stack at the mouse, then the tooltip of the hovered stack when nothing is held. */
    protected void drawCursorAndTooltip(ItemStack hoveredStack, int mouseX, int mouseY) {
        ItemStack cursor = inventory().getItemStack();
        if (cursor != null) {
            glPushMatrix();
            glTranslatef(0, 0, 200);
            if (dragSplitting && dragSlots.size() > 1) {
                // While spreading, the cursor shows what will be left over (a yellow 0 when nothing).
                int left = dragRemnant(cursor);
                drawSlotStack(cursor.withCount(Math.max(1, left)), mouseX - 8, mouseY - 8,
                    left <= 0 ? FontRenderer.FORMAT_CHAR + "e0" : null);
            } else {
                drawSlotStack(cursor, mouseX - 8, mouseY - 8);
            }
            glPopMatrix();
        } else if (hoveredStack != null) {
            drawHoveringText(tooltip(hoveredStack), mouseX, mouseY);
        }
    }

    /**
     * Tooltip lines as 1.8.9's ItemStack.getTooltip and GuiScreen.renderToolTip
     * build them: the display name (an anvil name in italics, with
     * " (#0001/1)" or " (#0276)" when advanced tooltips are on, F3+H) in
     * the item's rarity colour (aqua when enchanted, yellow for an enchanted
     * book, otherwise white), then a book's stored enchantments, then the
     * item's enchantments ("Sharpness V"), then "Durability: x / y" for a
     * damaged item when advanced. Lines after the first are grey.
     */
    protected List<String> tooltip(ItemStack stack) {
        List<String> lines = new ArrayList<>();
        Item item = stack.item();
        String name = mc.stackName(stack);
        if (stack.hasCustomName()) name = FontRenderer.FORMAT_CHAR + "o" + name;
        boolean advanced = mc.settings().advancedItemTooltips;
        CreativeTabs tabs = mc.creativeTabs();
        if (advanced && tabs != null && tabs.entry(item) != null) {
            CreativeTabs.Entry entry = tabs.entry(item);
            name += tabs.hasSubtypes(item) ? String.format(" (#%04d/%d)", entry.id(), entry.meta()) : String.format(" (#%04d)", entry.id());
        }
        lines.add(rarityColor(stack) + name);
        for (var entry : stack.storedEnchantments().entrySet()) lines.add(GRAY + enchantmentName(entry.getKey(), entry.getValue()));
        for (var entry : stack.enchantments().entrySet()) lines.add(GRAY + enchantmentName(entry.getKey(), entry.getValue()));
        if (advanced && item.isDamageable() && stack.damage() > 0)
            lines.add(GRAY + "Durability: " + (item.maxDamage() - stack.damage()) + " / " + item.maxDamage());
        return lines;
    }

    /** EnumRarity colour of a stack's name: RARE (aqua) when enchanted, UNCOMMON (yellow) for an enchanted book, else white. */
    public static String rarityColor(ItemStack stack) {
        if (stack.isEnchanted()) return FontRenderer.FORMAT_CHAR + "b";
        if (!stack.storedEnchantments().isEmpty()) return FontRenderer.FORMAT_CHAR + "e";
        return FontRenderer.FORMAT_CHAR + "f";
    }

    /** Enchantment.getTranslatedName: "Efficiency IV"; an unknown id shows its key. */
    public static String enchantmentName(int id, int level) {
        net.minecraft.enchantment.Enchantment enchantment = net.minecraft.enchantment.Enchantment.byId(id);
        String name = enchantment == null ? "enchantment.unknown" : enchantment.translationKey();
        return I18n.format(name) + " " + I18n.format(net.minecraft.enchantment.EnchantmentHelper.levelKey(level));
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

    // ---- mouse (GuiContainer.mouseClicked / mouseClickMove / mouseReleased) ------------------

    /** Drag-splitting: under way, with which button, its mode (0 even, 1 one each, 2 full stacks) and the slots crossed. */
    private boolean dragSplitting, ignoreMouseUp, doubleClick;
    private int dragButton, dragMode;
    private final java.util.LinkedHashSet<SlotPos> dragSlots = new java.util.LinkedHashSet<>();
    private SlotPos lastClickSlot;
    private long lastClickTime;
    private int lastClickButton = -1;
    /** The stack under the first press of a shift-click, for shift-double-click. */
    private ItemStack shiftClickedStack;

    /** Milliseconds, for double-click timing (tests may substitute a clock). */
    protected long clock() { return System.currentTimeMillis(); }

    public boolean isDragSplitting() { return dragSplitting; }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) {
        trackMouse(mouseX, mouseY);
        super.mouseClicked(mouseX, mouseY, button);
        SlotPos pos = slotAt(mouseX, mouseY);
        long now = clock();
        doubleClick = pos != null && pos == lastClickSlot && now - lastClickTime < 250 && lastClickButton == button;
        ignoreMouseUp = false;
        if (button == 0 || button == 1 || button == 2) {
            boolean outside = pos == null && isOutside(mouseX, mouseY);
            if ((pos != null || outside) && !dragSplitting) {
                if (inventory().getItemStack() == null) {
                    if (pos != null) {
                        boolean shift = button != 2 && isShiftKeyDown();
                        // Remembered for shift-double-click; the second press lands on the emptied slot, so keep the first.
                        if (shift && pos.slot().get() != null) shiftClickedStack = pos.slot().get().copy();
                        handleSlotClick(pos, button, shift);
                    }
                    ignoreMouseUp = true;
                } else {
                    dragSplitting = true;
                    dragButton = button;
                    dragSlots.clear();
                    dragMode = button == 0 ? 0 : button == 1 ? 1 : 2;
                }
            }
        }
        lastClickSlot = pos;
        lastClickTime = now;
        lastClickButton = button;
    }

    @Override
    public void mouseDragged(int mouseX, int mouseY, int button, long time) {
        trackMouse(mouseX, mouseY);
        SlotPos pos = slotAt(mouseX, mouseY);
        ItemStack cursor = inventory().getItemStack();
        if (dragSplitting && pos != null && cursor != null && cursor.count() > dragSlots.size() && canAddTo(pos, cursor)
            && pos.slot().accepts(cursor) && canDragIntoSlot(pos) && (dragMode != 2 || mc.isCreative()))
            dragSlots.add(pos);
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int button) {
        super.mouseReleased(mouseX, mouseY, button);
        trackMouse(mouseX, mouseY);
        SlotPos pos = slotAt(mouseX, mouseY);
        if (doubleClick && pos != null && button == 0 && canMergeSlot(pos)) {
            if (isShiftKeyDown()) {
                if (shiftClickedStack != null)
                    for (SlotPos other : List.copyOf(slots)) {
                        ItemStack stack = other.slot().get();
                        if (stack != null && sameInventory(other, pos) && canAddTo(other, shiftClickedStack)) shiftClick(other);
                    }
            } else {
                collectAll(pos);
            }
            doubleClick = false;
            lastClickTime = 0;
        } else {
            if (dragSplitting && dragButton != button) {
                dragSplitting = false;
                dragSlots.clear();
                ignoreMouseUp = true;
                return;
            }
            if (ignoreMouseUp) {
                ignoreMouseUp = false;
                return;
            }
            ItemStack cursor = inventory().getItemStack();
            if (dragSplitting && !dragSlots.isEmpty()) {
                finishDrag();
            } else if (cursor != null) {
                if (button == 2) {
                    // Creative clone needs an empty hand: nothing.
                } else if (pos != null) {
                    boolean shift = isShiftKeyDown();
                    if (shift) shiftClickedStack = pos.slot().get() == null ? null : pos.slot().get().copy();
                    handleSlotClick(pos, button, shift);
                } else if (isOutside(mouseX, mouseY)) {
                    dropCursor(button);
                }
            }
        }
        if (inventory().getItemStack() == null) lastClickTime = 0;
        dragSplitting = false;
    }

    /** Whether a drag may drop items here (vanilla canDragIntoSlot: not a crafting result). */
    protected boolean canDragIntoSlot(SlotPos pos) { return !(pos.slot() instanceof CraftingGrid.ResultSlot); }

    /** Whether double-click gathering may take from here (vanilla canMergeSlot: not a crafting result). */
    protected boolean canMergeSlot(SlotPos pos) { return !(pos.slot() instanceof CraftingGrid.ResultSlot); }

    /** Container.canAddItemToSlot(slot, stack, true): empty, or the same item and not over-full. */
    static boolean canAddTo(SlotPos pos, ItemStack stack) {
        ItemStack there = pos.slot().get();
        return there == null || there.canMerge(stack) && there.count() <= stack.item().maxStackSize();
    }

    /** Whether two slots belong to the same inventory (the grid's cells vs the player's slots). */
    protected boolean sameInventory(SlotPos a, SlotPos b) {
        boolean resultA = a.slot() instanceof CraftingGrid.ResultSlot, resultB = b.slot() instanceof CraftingGrid.ResultSlot;
        if (resultA || resultB) return resultA && resultB;
        return (a.kind() == Kind.OTHER) == (b.kind() == Kind.OTHER);
    }

    /** Container.computeStackSize before adding what the slot already has: the share per slot. */
    private int dragShare(ItemStack cursor, int slotCount) {
        return switch (dragMode) {
            case 0 -> cursor.count() / slotCount;
            case 1 -> 1;
            default -> cursor.item().maxStackSize();
        };
    }

    /** The most a slot can hold of a stack. */
    private static int cap(SlotPos pos, ItemStack stack) { return Math.min(stack.item().maxStackSize(), pos.slot().limit(stack)); }

    /** What the cursor would keep if the drag ended now. */
    private int dragRemnant(ItemStack cursor) {
        int left = cursor.count();
        for (SlotPos pos : dragSlots) {
            ItemStack there = pos.slot().get();
            int have = there == null ? 0 : there.count();
            left -= Math.min(dragShare(cursor, dragSlots.size()) + have, cap(pos, cursor)) - have;
        }
        return left;
    }

    /** Ends a drag (Container QUICK_CRAFT, stage 2): each crossed slot gets its share, the rest stays on the cursor. */
    private void finishDrag() {
        ItemStack cursor = inventory().getItemStack();
        if (cursor == null) return;
        int left = cursor.count(), slotCount = dragSlots.size();
        for (SlotPos pos : dragSlots) {
            if (!canAddTo(pos, cursor) || !pos.slot().accepts(cursor) || cursor.count() < slotCount || !canDragIntoSlot(pos)) continue;
            ItemStack there = pos.slot().get();
            int have = there == null ? 0 : there.count();
            int total = Math.min(dragShare(cursor, slotCount) + have, cap(pos, cursor));
            left -= total - have;
            pos.slot().set(cursor.withCount(total));
        }
        inventory().setItemStack(left <= 0 ? null : cursor.withCount(left));
        dragSlots.clear();
    }

    /**
     * Double-click (Container PICKUP_ALL): onto the held stack, from every
     * other slot that allows it, the same item, first from stacks that are not
     * full, then from full ones, until the held stack is full.
     */
    private void collectAll(SlotPos clicked) {
        ItemStack cursor = inventory().getItemStack();
        ItemStack there = clicked.slot().get();
        if (cursor == null || there != null) return;
        int max = cursor.item().maxStackSize();
        for (int pass = 0; pass < 2; pass++)
            for (SlotPos pos : slots) {
                if (cursor.count() >= max) return;
                ItemStack stack = pos.slot().get();
                if (stack == null || !canAddTo(pos, cursor) || !canMergeSlot(pos) || !stack.canMerge(cursor)) continue;
                if (pass == 0 && stack.count() == max) continue;
                int take = Math.min(max - cursor.count(), stack.count());
                cursor.mergeFrom(stack.split(take));
                pos.slot().set(stack.isEmpty() ? null : stack);
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
    public void onGuiClosed() {
        ItemStack cursor = inventory().getItemStack();
        if (cursor != null) {
            inventory().setItemStack(null);
            inventory().addItemStackToInventory(cursor);
        }
    }
}
