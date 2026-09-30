package net.minecraft.inventory;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A crafting grid and its result (1.8.9 InventoryCrafting, InventoryCraftResult
 * and SlotCrafting; HYPOTHESIS: recalled click rules). The result is always
 * what the grid makes now. Taking it uses up one item from every filled cell
 * (a milk bucket leaves its bucket). Quirks kept from vanilla: right-clicking
 * the result with an empty cursor takes half of it and the rest is lost as
 * the result is worked out again; shift-clicking crafts repeatedly while the
 * result stays the same item and the inventory takes at least part of it,
 * losing whatever did not fit of the last one.
 */
public final class CraftingGrid {
    private final int width, height;
    private final ItemStack[] cells;
    private final CraftingManager recipes;
    private final Function<String, Item> items;
    private Consumer<ItemStack> onCrafted = stack -> {};

    /** Told about every result taken (SlotCrafting.onCrafting: achievements). */
    public void setOnCrafted(Consumer<ItemStack> onCrafted) { this.onCrafted = onCrafted == null ? stack -> {} : onCrafted; }

    public CraftingGrid(int width, int height, CraftingManager recipes, Function<String, Item> items) {
        this.width = width;
        this.height = height;
        this.cells = new ItemStack[width * height];
        this.recipes = recipes;
        this.items = items;
    }

    public int width() { return width; }
    public int height() { return height; }
    public ItemStack get(int x, int y) { return cells[x + y * width]; }
    public void set(int x, int y, ItemStack stack) { cells[x + y * width] = stack; }
    public ItemStack[] cells() { return cells; }
    public Slot cell(int index) { return Slot.of(cells, index); }

    /** What the grid makes now, a new stack each call, or null. */
    public ItemStack result() {
        if (recipes == null) return null;
        for (int i = 0; i < cells.length; i++) if (cells[i] != null && cells[i].isEmpty()) cells[i] = null;
        return recipes.findMatchingRecipe(cells, width, height);
    }

    /** The result slot: shows {@link #result()}; nothing can be put in. */
    public final class ResultSlot implements Slot {
        @Override public ItemStack get() { return result(); }
        @Override public void set(ItemStack stack) {}
        @Override public boolean accepts(ItemStack stack) { return false; }
        public CraftingGrid grid() { return CraftingGrid.this; }
    }

    public ResultSlot resultSlot() { return new ResultSlot(); }

    /**
     * SlotCrafting.onPickupFromSlot: one item from each filled cell; a
     * container left behind goes to the inventory, else into its emptied cell,
     * else is dropped.
     */
    void consume(InventoryPlayer inventory, Consumer<ItemStack> drop) {
        ItemStack made = result();
        if (made != null) onCrafted.accept(made);
        for (int i = 0; i < cells.length; i++) {
            ItemStack stack = cells[i];
            if (stack == null) continue;
            stack.split(1);
            if (stack.isEmpty()) cells[i] = null;
            String container = CraftingManager.containerItem(stack.item());
            Item containerItem = container == null ? null : items.apply(container);
            if (containerItem == null) continue;
            ItemStack left = new ItemStack(containerItem);
            if (inventory.addItemStackToInventory(left) && left.isEmpty()) continue;
            if (cells[i] == null) cells[i] = left;
            else drop.accept(left);
        }
    }

    /** Clicking the result: 0 left, 1 right (Container.slotClick PICKUP on the result slot). */
    public void click(InventoryPlayer inventory, int button, Consumer<ItemStack> drop) {
        ItemStack result = result();
        if (result == null) return;
        ItemStack cursor = inventory.getItemStack();
        if (cursor == null) {
            int take = button == 0 ? result.count() : (result.count() + 1) / 2;
            inventory.setItemStack(result.split(take));
            consume(inventory, drop);
        } else if (cursor.canMerge(result) && result.item().maxStackSize() > 1
            && cursor.count() + result.count() <= result.item().maxStackSize()) {
            cursor.mergeFrom(result);
            consume(inventory, drop);
        }
    }

    /**
     * Shift-click on the result: crafts into the inventory again and again.
     * The survival inventory fills the 27 main slots then the hotbar; the
     * crafting table goes backwards from the last hotbar slot
     * ({@code backwards}, ContainerWorkbench's reversed merge).
     */
    public void shiftClick(InventoryPlayer inventory, boolean backwards, Consumer<ItemStack> drop) {
        ItemStack first = result();
        if (first == null) return;
        Item item = first.item();
        for (int guard = 0; guard < 4096; guard++) {
            ItemStack result = result();
            if (result == null || result.item() != item) return;
            int before = result.count();
            mergeIntoInventory(inventory, result, backwards);
            if (result.count() == before) return;
            consume(inventory, drop);
        }
    }

    /** Number key over the result: into that hotbar slot if it is empty. */
    public void toHotbar(InventoryPlayer inventory, int hotbar, Consumer<ItemStack> drop) {
        ItemStack result = result();
        if (result == null || inventory.mainInventory[hotbar] != null) return;
        inventory.mainInventory[hotbar] = result;
        consume(inventory, drop);
    }

    /** The drop key over the result: throws one (all with Ctrl) and crafts once. */
    public void throwResult(InventoryPlayer inventory, boolean wholeStack, Consumer<ItemStack> drop) {
        ItemStack result = result();
        if (result == null) return;
        ItemStack thrown = wholeStack ? result : result.split(1);
        consume(inventory, drop);
        drop.accept(thrown);
    }

    /** Closing the screen: everything in the grid goes back to the inventory, or is dropped if it doesn't fit. */
    public void returnAll(InventoryPlayer inventory, Consumer<ItemStack> drop) {
        for (int i = 0; i < cells.length; i++) {
            ItemStack stack = cells[i];
            if (stack == null) continue;
            cells[i] = null;
            inventory.addItemStackToInventory(stack);
            if (!stack.isEmpty()) drop.accept(stack);
        }
    }

    /** Container.mergeItemStack over the player's slots: onto matching stacks first, then into empty slots. */
    static void mergeIntoInventory(InventoryPlayer inventory, ItemStack stack, boolean backwards) {
        int[] order = new int[InventoryPlayer.MAIN_SIZE];
        int n = 0;
        if (backwards) {
            for (int i = InventoryPlayer.HOTBAR_SIZE - 1; i >= 0; i--) order[n++] = i;
            for (int i = InventoryPlayer.MAIN_SIZE - 1; i >= InventoryPlayer.HOTBAR_SIZE; i--) order[n++] = i;
        } else {
            for (int i = InventoryPlayer.HOTBAR_SIZE; i < InventoryPlayer.MAIN_SIZE; i++) order[n++] = i;
            for (int i = 0; i < InventoryPlayer.HOTBAR_SIZE; i++) order[n++] = i;
        }
        ItemStack[] slots = inventory.mainInventory;
        if (stack.item().maxStackSize() > 1)
            for (int i : order) {
                if (stack.isEmpty()) return;
                if (slots[i] != null && slots[i].canMerge(stack)) slots[i].mergeFrom(stack);
            }
        for (int i : order) {
            if (stack.isEmpty()) return;
            if (slots[i] == null) slots[i] = stack.split(stack.count());
        }
    }
}
