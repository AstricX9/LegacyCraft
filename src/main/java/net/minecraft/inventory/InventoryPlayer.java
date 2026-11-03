package net.minecraft.inventory;

import net.minecraft.block.BlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * The player's inventory with 1.8.9's layout (master context §21): 36 main
 * slots where 0-8 are the hotbar, 4 armor slots (0 boots, 1 leggings,
 * 2 chestplate, 3 helmet) and the stack held on the cursor while a container
 * screen is open.
 */
public final class InventoryPlayer {
    public static final int MAIN_SIZE = 36, HOTBAR_SIZE = 9, ARMOR_SIZE = 4;

    public final ItemStack[] mainInventory = new ItemStack[MAIN_SIZE];
    public final ItemStack[] armorInventory = new ItemStack[ARMOR_SIZE];
    public int currentItem;
    private ItemStack itemStack;

    public ItemStack getCurrentItem() { return mainInventory[currentItem]; }
    public ItemStack getItemStack() { return itemStack; }
    public void setItemStack(ItemStack stack) { itemStack = stack == null || stack.isEmpty() ? null : stack; }

    /**
     * Right-click with a piece of armour (ItemArmor.onItemRightClick): it
     * moves from the selected hotbar slot into its armour slot if that slot is
     * empty, in every game mode. Returns whether it was put on.
     */
    public boolean equipHeldArmor() {
        ItemStack held = getCurrentItem();
        if (held == null || !ArmorValues.isArmorPiece(held.item())) return false;
        int slot = ArmorValues.armorSlot(held.item());
        if (slot < 0 || armorInventory[slot] != null) return false;
        armorInventory[slot] = held.split(1);
        if (held.isEmpty()) mainInventory[currentItem] = null;
        return true;
    }

    /** Mouse wheel over the hotbar: one slot per notch, wrapping. */
    public void changeCurrentItem(int direction) {
        if (direction > 0) direction = 1;
        if (direction < 0) direction = -1;
        currentItem -= direction;
        while (currentItem < 0) currentItem += HOTBAR_SIZE;
        while (currentItem >= HOTBAR_SIZE) currentItem -= HOTBAR_SIZE;
    }

    /** Slot of a hotbar stack placing this exact (block, metadata), or -1. */
    public int hotbarSlotPlacing(BlockState state) {
        for (int i = 0; i < HOTBAR_SIZE; i++) {
            ItemStack stack = mainInventory[i];
            if (stack != null && state.equals(stack.item().placedState())) return i;
        }
        return -1;
    }

    /**
     * Pick block (middle click on a block): select the hotbar slot already
     * holding the item. In Creative, otherwise move to the first empty hotbar
     * slot (or keep the selected one) and put a full stack of the item there.
     * HYPOTHESIS: the stack size given in Creative.
     */
    public void pickItem(Item item, boolean creative) {
        for (int i = 0; i < HOTBAR_SIZE; i++) {
            ItemStack stack = mainInventory[i];
            if (stack != null && stack.item() == item) { currentItem = i; return; }
        }
        if (!creative) return;
        for (int i = 0; i < HOTBAR_SIZE; i++) {
            if (mainInventory[i] == null) { currentItem = i; break; }
        }
        mainInventory[currentItem] = new ItemStack(item, item.maxStackSize());
    }

    /** Level.dat slot numbering: 0-35 main inventory, 100-103 armor (boots first). */
    public ItemStack getBySaveSlot(int slot) {
        if (slot >= 0 && slot < MAIN_SIZE) return mainInventory[slot];
        if (slot >= 100 && slot < 100 + ARMOR_SIZE) return armorInventory[slot - 100];
        return null;
    }

    public void setBySaveSlot(int slot, ItemStack stack) {
        if (slot >= 0 && slot < MAIN_SIZE) mainInventory[slot] = stack;
        else if (slot >= 100 && slot < 100 + ARMOR_SIZE) armorInventory[slot - 100] = stack;
    }

    /** Merges into matching stacks first, then empty slots (hotbar first, as vanilla). */
    public boolean addItemStackToInventory(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        for (ItemStack slot : mainInventory) {
            if (stack.isEmpty()) return true;
            if (slot != null) slot.mergeFrom(stack);
        }
        for (int i = 0; i < MAIN_SIZE && !stack.isEmpty(); i++) {
            if (mainInventory[i] == null) {
                mainInventory[i] = stack.split(Math.min(stack.count(), stack.item().maxStackSize()));
            }
        }
        return stack.isEmpty();
    }

    /** Removes one item from the selected slot (survival block placement). */
    public void consumeCurrentItem() {
        ItemStack stack = mainInventory[currentItem];
        if (stack == null) return;
        stack.split(1);
        if (stack.isEmpty()) mainInventory[currentItem] = null;
    }

    public void clear() {
        java.util.Arrays.fill(mainInventory, null);
        java.util.Arrays.fill(armorInventory, null);
        itemStack = null;
    }

    /** 1.8.9 armor points of what is worn (HYPOTHESIS values per piece; see ArmorValues). */
    public int getTotalArmorValue() {
        int total = 0;
        for (int slot = 0; slot < ARMOR_SIZE; slot++) {
            ItemStack stack = armorInventory[slot];
            if (stack != null) total += ArmorValues.points(stack.item(), slot);
        }
        return total;
    }
}
