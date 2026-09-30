package net.minecraft.inventory;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * An open enchanting table (1.8.9 ContainerEnchantment; HYPOTHESIS: recalled).
 * One item slot (one item at a time) and a lapis slot. Whenever the item
 * changes, the three offers are rolled from the player's enchantment seed and
 * the bookshelves around the table: {@link #levels} the level each button
 * asks for (0 when there is none) and {@link #clues} one enchantment each
 * offer will contain ({@code id | level << 8}, -1 when unknown). Enchanting
 * with button {@code b} needs {@code b + 1} lapis and levels, and at least
 * the offer's level, except in Creative; it costs {@code b + 1} of each, and
 * the player's seed changes, so the next offers differ. A book becomes an
 * enchanted book, losing one of the rolled enchantments at random when it
 * rolled more than one.
 */
public final class EnchantingTable {
    /** What the table needs from the player using it. */
    public interface Player {
        int experienceLevel();
        /** The seed the offers are rolled from (EntityPlayer.xpSeed). */
        int xpSeed();
        boolean isCreative();
        /** Takes the levels an enchantment costs (removeExperienceLevel, which also picks a new seed). */
        void removeExperienceLevels(int levels);
    }

    private final ItemStack[] slots = new ItemStack[2];
    public final int[] levels = new int[3];
    public final int[] clues = {-1, -1, -1};
    private final Random random = new Random();
    private final Function<String, Item> items;
    private int bookshelves;
    private int xpSeed;

    /** {@code items} resolves "enchanted_book" (the item a book turns into). */
    public EnchantingTable(Function<String, Item> items) { this.items = items; }

    /** The item slot: anything, one at a time (SlotEnchantmentTable); changing it re-rolls the offers. */
    public Slot itemSlot() {
        return new Slot() {
            public ItemStack get() { return slots[0]; }
            public void set(ItemStack stack) {
                slots[0] = stack == null || stack.isEmpty() ? null : stack;
                roll();
            }
            public int limit() { return 1; }
        };
    }

    /** The lapis slot: only lapis lazuli. */
    public Slot lapisSlot() {
        return new Slot() {
            public ItemStack get() { return slots[1]; }
            public void set(ItemStack stack) { slots[1] = stack == null || stack.isEmpty() ? null : stack; }
            public boolean accepts(ItemStack stack) { return isLapis(stack); }
        };
    }

    public static boolean isLapis(ItemStack stack) { return stack != null && stack.item().name().path().equals("dye_blue"); }

    public ItemStack item() { return slots[0]; }
    public ItemStack lapis() { return slots[1]; }
    public int bookshelves() { return bookshelves; }

    /** Opening the table (the container's constructor): the player's seed and the shelves around it. */
    public void open(int xpSeed, int bookshelves) {
        this.xpSeed = xpSeed;
        this.bookshelves = bookshelves;
        roll();
    }

    /** onCraftMatrixChanged: rolls the three offers for the item in the slot. */
    public void roll() {
        ItemStack stack = slots[0];
        if (stack != null && EnchantmentHelper.isEnchantable(stack)) {
            random.setSeed(xpSeed);
            for (int i = 0; i < 3; i++) {
                levels[i] = EnchantmentHelper.buttonLevel(random, i, bookshelves, stack);
                clues[i] = -1;
                if (levels[i] < i + 1) levels[i] = 0;
            }
            for (int i = 0; i < 3; i++) {
                if (levels[i] <= 0) continue;
                List<EnchantmentHelper.Data> list = enchantments(stack, i, levels[i]);
                if (list != null && !list.isEmpty()) {
                    EnchantmentHelper.Data clue = list.get(random.nextInt(list.size()));
                    clues[i] = clue.enchantment().id | clue.level() << 8;
                }
            }
        } else {
            for (int i = 0; i < 3; i++) {
                levels[i] = 0;
                clues[i] = -1;
            }
        }
    }

    /**
     * What button {@code button} would give (func_178148_a): the list rolled
     * from the seed plus the button, a book dropping one entry at random.
     */
    public List<EnchantmentHelper.Data> enchantments(ItemStack stack, int button, int level) {
        random.setSeed(xpSeed + button);
        List<EnchantmentHelper.Data> list = EnchantmentHelper.buildEnchantmentList(random, stack, level);
        if (EnchantmentHelper.isBook(stack) && list != null && list.size() > 1) list.remove(random.nextInt(list.size()));
        return list;
    }

    /** Whether button {@code button} may be pressed now (what GuiEnchantment greys out). */
    public boolean canEnchant(int button, Player player) {
        int cost = button + 1;
        if (!player.isCreative() && (slots[1] == null || slots[1].count() < cost)) return false;
        return levels[button] > 0 && slots[0] != null
            && (player.isCreative() || player.experienceLevel() >= cost && player.experienceLevel() >= levels[button]);
    }

    /** ContainerEnchantment.enchantItem; returns whether the item was enchanted. */
    public boolean enchant(int button, Player player) {
        if (button < 0 || button > 2 || !canEnchant(button, player)) return false;
        ItemStack stack = slots[0];
        List<EnchantmentHelper.Data> list = enchantments(stack, button, levels[button]);
        if (list == null) return false;
        int cost = button + 1;
        player.removeExperienceLevels(cost);
        boolean book = EnchantmentHelper.isBook(stack);
        if (book) {
            Item enchanted = items.apply("enchanted_book");
            if (enchanted == null) return false;
            stack = stack.asItem(enchanted);
            slots[0] = stack;
        }
        for (EnchantmentHelper.Data data : list) {
            if (book) stack.addStoredEnchantment(data.enchantment().id, data.level());
            else stack.addEnchantment(data.enchantment().id, data.level());
        }
        if (!player.isCreative()) {
            ItemStack lapis = slots[1];
            lapis.split(cost);
            if (lapis.isEmpty()) slots[1] = null;
        }
        xpSeed = player.xpSeed();
        roll();
        return true;
    }

    /** Closing the table (onContainerClosed): the item and lapis go back to the player. */
    public void returnAll(InventoryPlayer inventory, Consumer<ItemStack> drop) {
        for (int i = 0; i < slots.length; i++) {
            ItemStack stack = slots[i];
            if (stack == null) continue;
            slots[i] = null;
            inventory.addItemStackToInventory(stack);
            if (!stack.isEmpty()) drop.accept(stack);
        }
        roll();
    }

    /** Reads a block's name at a position (for counting bookshelves). */
    public interface Blocks {
        String name(int x, int y, int z);
    }

    /**
     * The bookshelves feeding a table (ContainerEnchantment.onCraftMatrixChanged):
     * for each of the 8 directions whose two blocks next to the table (at its
     * height and one above) are air, the shelves two out at both heights, and
     * for diagonals also the two shelves beside that corner.
     */
    public static int countBookshelves(Blocks world, int x, int y, int z) {
        int count = 0;
        for (int j = -1; j <= 1; j++) {
            for (int k = -1; k <= 1; k++) {
                if (j == 0 && k == 0) continue;
                if (!isAir(world, x + k, y, z + j) || !isAir(world, x + k, y + 1, z + j)) continue;
                if (isShelf(world, x + k * 2, y, z + j * 2)) count++;
                if (isShelf(world, x + k * 2, y + 1, z + j * 2)) count++;
                if (k != 0 && j != 0) {
                    if (isShelf(world, x + k * 2, y, z + j)) count++;
                    if (isShelf(world, x + k * 2, y + 1, z + j)) count++;
                    if (isShelf(world, x + k, y, z + j * 2)) count++;
                    if (isShelf(world, x + k, y + 1, z + j * 2)) count++;
                }
            }
        }
        return count;
    }

    private static boolean isAir(Blocks world, int x, int y, int z) { return "air".equals(world.name(x, y, z)); }
    private static boolean isShelf(Blocks world, int x, int y, int z) { return "bookshelf".equals(world.name(x, y, z)); }
}
