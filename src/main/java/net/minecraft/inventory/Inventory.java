package net.minecraft.inventory;

import net.minecraft.item.ItemStack;

import java.util.Arrays;

/** Fixed-size inventory that merges compatible stacks before occupying empty slots. */
public final class Inventory {
    private final ItemStack[] slots;

    public Inventory(int size) {
        if (size < 1) throw new IllegalArgumentException("Inventory must have at least one slot");
        slots = new ItemStack[size];
    }

    public int size() { return slots.length; }
    public ItemStack get(int slot) { return slots[checked(slot)]; }
    public void set(int slot, ItemStack stack) { slots[checked(slot)] = stack; }
    public ItemStack remove(int slot) {
        int index = checked(slot);
        ItemStack result = slots[index];
        slots[index] = null;
        return result;
    }

    /** Inserts as much as possible and returns the number of items left in the input stack. */
    public int insert(ItemStack input) {
        if (input == null || input.isEmpty()) return 0;
        for (ItemStack slot : slots) {
            if (input.isEmpty()) return 0;
            if (slot != null) slot.mergeFrom(input);
        }
        for (int i = 0; i < slots.length && !input.isEmpty(); i++) {
            if (slots[i] == null) {
                int moved = Math.min(input.count(), input.item().maxStackSize());
                slots[i] = input.split(moved);
            }
        }
        return input.count();
    }

    public int count(net.minecraft.item.Item item) {
        return Arrays.stream(slots).filter(stack -> stack != null && stack.item() == item)
            .mapToInt(ItemStack::count).sum();
    }

    private int checked(int slot) {
        if (slot < 0 || slot >= slots.length) throw new IndexOutOfBoundsException("slot " + slot);
        return slot;
    }
}
