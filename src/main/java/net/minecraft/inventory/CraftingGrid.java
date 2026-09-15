package net.minecraft.inventory;

import net.minecraft.item.ItemStack;

/** Mutable crafting input grid. Empty cells are represented by {@code null}. */
public final class CraftingGrid {
    private final int width;
    private final int height;
    private final ItemStack[] stacks;

    public CraftingGrid(int width, int height) {
        if (width < 1 || height < 1) throw new IllegalArgumentException("Invalid crafting grid");
        this.width = width;
        this.height = height;
        stacks = new ItemStack[width * height];
    }
    public int width() { return width; }
    public int height() { return height; }
    public ItemStack get(int x, int y) { return stacks[index(x, y)]; }
    public void set(int x, int y, ItemStack stack) { stacks[index(x, y)] = stack; }
    private int index(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) throw new IndexOutOfBoundsException();
        return y * width + x;
    }
}
