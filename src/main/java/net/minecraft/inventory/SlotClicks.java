package net.minecraft.inventory;

import net.minecraft.item.ItemStack;

/**
 * The 1.8.9 slot interactions shared by every container screen (master
 * context §21), independent of any GUI so they can be tested:
 * <ul>
 *   <li>left click: pick up the whole stack, put the whole cursor down, merge
 *       into a matching stack, or swap different items;</li>
 *   <li>right click: pick up half (rounded up), or put down one;</li>
 *   <li>number keys 1-9: swap the slot with that hotbar slot;</li>
 *   <li>creative middle click: copy a full stack of the slot's item onto an empty cursor.</li>
 * </ul>
 * Drag-splitting and double-click collecting are not implemented yet (DEV-015).
 */
public final class SlotClicks {
    private SlotClicks() {}

    public static void click(Slot slot, InventoryPlayer inventory, int button) {
        ItemStack cursor = inventory.getItemStack();
        ItemStack stack = slot.get();
        if (cursor == null) {
            if (stack == null) return;
            int take = button == 0 ? stack.count() : (stack.count() + 1) / 2;
            ItemStack taken = stack.split(take);
            slot.set(stack.isEmpty() ? null : stack);
            inventory.setItemStack(taken);
            return;
        }
        if (!slot.accepts(cursor)) return;
        int room = Math.min(slot.limit(), cursor.item().maxStackSize());
        if (stack == null) {
            int put = Math.min(button == 0 ? cursor.count() : 1, room);
            slot.set(cursor.split(put));
            inventory.setItemStack(cursor.isEmpty() ? null : cursor);
            return;
        }
        if (stack.canMerge(cursor)) {
            int want = button == 0 ? cursor.count() : 1;
            int put = Math.min(want, room - stack.count());
            if (put > 0) stack.mergeFrom(cursor.split(put));
            inventory.setItemStack(cursor.isEmpty() ? null : cursor);
            return;
        }
        if (cursor.count() <= room) {
            slot.set(cursor);
            inventory.setItemStack(stack);
        }
    }

    /** Number key {@code hotbarIndex} (0-8) pressed over {@code slot}. */
    public static void swapWithHotbar(Slot slot, InventoryPlayer inventory, int hotbarIndex) {
        Slot hotbar = Slot.of(inventory.mainInventory, hotbarIndex);
        ItemStack fromHotbar = hotbar.get(), fromSlot = slot.get();
        if (fromHotbar != null && !slot.accepts(fromHotbar)) return;
        slot.set(fromHotbar);
        hotbar.set(fromSlot);
    }

    /** Creative middle click: a full stack of the slot's item onto an empty cursor. */
    public static void cloneStack(Slot slot, InventoryPlayer inventory) {
        ItemStack stack = slot.get();
        if (inventory.getItemStack() != null || stack == null) return;
        inventory.setItemStack(new ItemStack(stack.item(), stack.item().maxStackSize(), stack.damage()));
    }

    /**
     * Shift-click in the player inventory: hotbar and armor go to the main 27
     * slots, main slots go to the hotbar; armor pieces go to their armor slot
     * first. Partial moves leave the rest behind.
     */
    public static void shiftClickPlayerSlot(InventoryPlayer inventory, Slot slot, boolean fromHotbar, boolean fromArmor) {
        ItemStack stack = slot.get();
        if (stack == null) return;
        int armor = ArmorValues.armorSlot(stack.item());
        if (!fromArmor && armor >= 0 && inventory.armorInventory[armor] == null) {
            inventory.armorInventory[armor] = stack.split(1);
            slot.set(stack.isEmpty() ? null : stack);
            return;
        }
        boolean toMain = fromHotbar || fromArmor;
        int from = toMain ? InventoryPlayer.HOTBAR_SIZE : 0;
        int to = toMain ? InventoryPlayer.MAIN_SIZE : InventoryPlayer.HOTBAR_SIZE;
        merge(inventory.mainInventory, from, to, stack);
        if (fromArmor && !stack.isEmpty()) merge(inventory.mainInventory, 0, InventoryPlayer.HOTBAR_SIZE, stack);
        slot.set(stack.isEmpty() ? null : stack);
    }

    /** Vanilla mergeItemStack: fill matching stacks in range, then the first empty slot. */
    static void merge(ItemStack[] slots, int from, int to, ItemStack stack) {
        for (int i = from; i < to && !stack.isEmpty(); i++)
            if (slots[i] != null && slots[i].canMerge(stack)) slots[i].mergeFrom(stack);
        for (int i = from; i < to && !stack.isEmpty(); i++)
            if (slots[i] == null) slots[i] = stack.split(stack.count());
    }
}
