package net.minecraft.inventory;

import net.minecraft.item.ItemStack;

/** One clickable storage position in a container screen. */
public interface Slot {
    ItemStack get();
    void set(ItemStack stack);
    /** Whether the stack may be put here (armor slots only take their piece). */
    default boolean accepts(ItemStack stack) { return true; }
    /** Most items this slot holds regardless of the item's own stack size. */
    default int limit() { return 64; }
    /** Most of {@code stack} this slot holds (a furnace's fuel slot takes one bucket); by default {@link #limit()}. */
    default int limit(ItemStack stack) { return limit(); }

    /** A slot backed by an array element. */
    static Slot of(ItemStack[] array, int index) {
        return new Slot() {
            public ItemStack get() { return array[index]; }
            public void set(ItemStack stack) { array[index] = stack == null || stack.isEmpty() ? null : stack; }
        };
    }

    /** One of the four armor slots: only the matching piece, one at a time. */
    static Slot armor(InventoryPlayer inventory, int armorSlot) {
        return new Slot() {
            public ItemStack get() { return inventory.armorInventory[armorSlot]; }
            public void set(ItemStack stack) { inventory.armorInventory[armorSlot] = stack == null || stack.isEmpty() ? null : stack; }
            public boolean accepts(ItemStack stack) { return stack != null && ArmorValues.armorSlot(stack.item()) == armorSlot; }
            public int limit() { return 1; }
        };
    }
}
