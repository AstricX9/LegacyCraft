package net.minecraft.server.dedicated;

import net.minecraft.inventory.CraftingGrid;
import net.minecraft.inventory.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.inventory.SlotClicks;
import net.minecraft.item.ItemStack;
import net.minecraft.network.v47.Serverbound;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A window as both sides number it, and what a click in it does. Window 0 is
 * the player's own (0 result, 1-4 crafting grid, 5-8 armor from the helmet
 * down, 9-35 main inventory, 36-44 hotbar); a crafting table is window
 * {@code id > 0} with 0 result, 1-9 grid, 10-36 main, 37-45 hotbar.
 *
 * <p>The click rules are the ones the 1.8.9 client predicts with; the server
 * applies them to its own copy, and the caller then resends the window so a
 * wrong prediction is corrected.
 */
final class ServerWindow {
    final int id;
    /** Wire window type for the open-window packet; null for the player's own window. */
    final String type;
    final CraftingGrid grid;
    private final InventoryPlayer inventory;
    private final List<Slot> slots = new ArrayList<>();
    /** First main-inventory slot; the hotbar follows the 27 main slots. */
    private final int mainStart;

    private int dragButton = -1;
    private final Set<Integer> dragSlots = new LinkedHashSet<>();

    private ServerWindow(int id, String type, CraftingGrid grid, InventoryPlayer inventory) {
        this.id = id;
        this.type = type;
        this.grid = grid;
        this.inventory = inventory;
        slots.add(grid.resultSlot());
        for (int i = 0; i < grid.cells().length; i++) slots.add(grid.cell(i));
        if (type == null) for (int armor = 3; armor >= 0; armor--) slots.add(Slot.armor(inventory, armor));
        mainStart = slots.size();
        for (int i = InventoryPlayer.HOTBAR_SIZE; i < InventoryPlayer.MAIN_SIZE; i++) slots.add(Slot.of(inventory.mainInventory, i));
        for (int i = 0; i < InventoryPlayer.HOTBAR_SIZE; i++) slots.add(Slot.of(inventory.mainInventory, i));
    }

    static ServerWindow player(InventoryPlayer inventory, CraftingGrid grid2x2) { return new ServerWindow(0, null, grid2x2, inventory); }
    static ServerWindow craftingTable(int id, InventoryPlayer inventory, CraftingGrid grid3x3) {
        return new ServerWindow(id, "minecraft:crafting_table", grid3x3, inventory);
    }

    int size() { return slots.size(); }
    ItemStack get(int slot) { return slots.get(slot).get(); }
    /** Sets a slot outright (Creative); the result slot ignores it. */
    void set(int slot, ItemStack stack) { slots.get(slot).set(stack); }
    boolean isPlayerWindow() { return type == null; }

    /** The window slot showing a hotbar index, for single-slot updates. */
    int hotbarSlot(int hotbar) { return mainStart + 27 + hotbar; }

    /** Applies one click; {@code drop} throws a stack out of the player's hand. */
    void click(Serverbound.ClickWindow click, boolean creative, Consumer<ItemStack> drop) {
        int index = click.slot(), button = click.button();
        boolean outside = index == Serverbound.ClickWindow.OUTSIDE;
        if (!outside && (index < 0 || index >= slots.size())) return;
        if (click.mode() != 5) dragButton = -1;
        switch (click.mode()) {
            case 0 -> {
                if (outside) {
                    ItemStack cursor = inventory.getItemStack();
                    if (cursor == null) return;
                    if (button == 0) { inventory.setItemStack(null); drop.accept(cursor); }
                    else if (button == 1) { drop.accept(cursor.split(1)); inventory.setItemStack(cursor); }
                } else if (index == 0) {
                    grid.click(inventory, button, drop);
                } else if (button == 0 || button == 1) {
                    SlotClicks.click(slots.get(index), inventory, button);
                }
            }
            case 1 -> { if (!outside) shiftClick(index, drop); }
            case 2 -> {
                if (outside || button < 0 || button > 8) return;
                if (index == 0) grid.toHotbar(inventory, button, drop);
                else SlotClicks.swapWithHotbar(slots.get(index), inventory, button);
            }
            case 3 -> { if (!outside && creative && index != 0) SlotClicks.cloneStack(slots.get(index), inventory); }
            case 4 -> {
                if (outside || inventory.getItemStack() != null) return;
                if (index == 0) { grid.throwResult(inventory, button == 1, drop); return; }
                Slot slot = slots.get(index);
                ItemStack stack = slot.get();
                if (stack == null) return;
                ItemStack thrown = button == 1 ? stack : stack.split(1);
                slot.set(button == 1 || stack.isEmpty() ? null : stack);
                drop.accept(thrown);
            }
            case 5 -> drag(index, button, creative);
            case 6 -> { if (!outside && index != 0) gather(); }
            default -> {}
        }
    }

    private void shiftClick(int index, Consumer<ItemStack> drop) {
        if (index == 0) {
            grid.shiftClick(inventory, !isPlayerWindow(), drop);
            return;
        }
        Slot slot = slots.get(index);
        ItemStack stack = slot.get();
        if (stack == null) return;
        int hotbarStart = mainStart + 27;
        if (index < mainStart) {
            // Grid or armor into the inventory: main slots first, then the hotbar.
            if (isPlayerWindow() && index >= 5) { SlotClicks.shiftClickPlayerSlot(inventory, slot, false, true); return; }
            merge(stack, InventoryPlayer.HOTBAR_SIZE, InventoryPlayer.MAIN_SIZE);
            merge(stack, 0, InventoryPlayer.HOTBAR_SIZE);
        } else if (isPlayerWindow()) {
            SlotClicks.shiftClickPlayerSlot(inventory, slot, index >= hotbarStart, false);
            return;
        } else if (index >= hotbarStart) {
            merge(stack, InventoryPlayer.HOTBAR_SIZE, InventoryPlayer.MAIN_SIZE);
        } else {
            merge(stack, 0, InventoryPlayer.HOTBAR_SIZE);
        }
        slot.set(stack.isEmpty() ? null : stack);
    }

    /** Onto matching stacks in the main-inventory range first, then into empty slots. */
    private void merge(ItemStack stack, int from, int to) {
        ItemStack[] main = inventory.mainInventory;
        for (int i = from; i < to && !stack.isEmpty(); i++)
            if (main[i] != null && main[i].canMerge(stack)) main[i].mergeFrom(stack);
        for (int i = from; i < to && !stack.isEmpty(); i++)
            if (main[i] == null) main[i] = stack.split(Math.min(stack.count(), stack.item().maxStackSize()));
    }

    /**
     * Drag splitting: button 0/4/8 starts a left/right/middle drag outside any
     * slot, 1/5/9 adds a slot, 2/6/10 ends it. Left spreads the cursor
     * evenly, right puts one in each, middle (Creative) fills each slot.
     */
    private void drag(int index, int button, boolean creative) {
        int kind = button >> 2, stage = button & 3;
        ItemStack cursor = inventory.getItemStack();
        if (stage == 0) {
            dragButton = kind;
            dragSlots.clear();
            return;
        }
        if (dragButton != kind || cursor == null || (kind == 2 && !creative)) { dragButton = -1; return; }
        if (stage == 1) {
            if (index <= 0 || index >= slots.size()) return;
            Slot slot = slots.get(index);
            ItemStack there = slot.get();
            if (slot.accepts(cursor) && (there == null || there.canMerge(cursor))) dragSlots.add(index);
            return;
        }
        dragButton = -1;
        if (stage != 2 || dragSlots.isEmpty()) return;
        int each = switch (kind) {
            case 0 -> Math.max(1, cursor.count() / dragSlots.size());
            case 1 -> 1;
            default -> cursor.item().maxStackSize();
        };
        for (int index2 : dragSlots) {
            if (kind != 2 && cursor.isEmpty()) break;
            Slot slot = slots.get(index2);
            ItemStack there = slot.get();
            int room = Math.min(slot.limit(cursor), cursor.item().maxStackSize()) - (there == null ? 0 : there.count());
            int put = Math.min(each, room);
            if (kind != 2) put = Math.min(put, cursor.count());
            if (put <= 0) continue;
            ItemStack added = kind == 2 ? cursor.withCount(put) : cursor.split(put);
            if (there == null) slot.set(added);
            else there.mergeFrom(added);
        }
        dragSlots.clear();
        inventory.setItemStack(cursor.isEmpty() ? null : cursor);
    }

    /** Double click: pulls matching items into the cursor stack, part-full stacks first. */
    private void gather() {
        ItemStack cursor = inventory.getItemStack();
        if (cursor == null) return;
        int max = cursor.item().maxStackSize();
        for (int pass = 0; pass < 2; pass++)
            for (int i = 1; i < slots.size() && cursor.count() < max; i++) {
                Slot slot = slots.get(i);
                ItemStack stack = slot.get();
                if (stack == null || !stack.canMerge(cursor) || (pass == 0 && stack.count() >= stack.item().maxStackSize())) continue;
                cursor.mergeFrom(stack.split(Math.min(stack.count(), max - cursor.count())));
                slot.set(stack.isEmpty() ? null : stack);
            }
    }

    /** Closing: the grid goes back to the inventory and the cursor stack with it (dropped if there's no room). */
    void close(Consumer<ItemStack> drop) {
        grid.returnAll(inventory, drop);
        ItemStack cursor = inventory.getItemStack();
        inventory.setItemStack(null);
        if (cursor != null && !inventory.addItemStackToInventory(cursor)) drop.accept(cursor);
        dragButton = -1;
    }
}
