package net.minecraft.world.gen;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generated chest loot (1.8.9 WeightedRandomChestContent and the tables of
 * WorldGenDungeons, StructureStrongholdPieces and StructureNetherBridgePieces,
 * HYPOTHESIS: recalled). Each draw picks an entry by weight and a count in
 * its range, then puts the stack in a random slot (overwriting whatever was
 * there); a count over the item's stack size goes in one at a time. The
 * random draws happen whether or not the items are loaded, so generation
 * comes out the same either way.
 */
public final class ChestLoot {
    private ChestLoot() {}

    /** WeightedRandomChestContent: an item (by LegacyCraft name and damage), its count range and weight. */
    record Entry(String item, int damage, int min, int max, int weight, java.util.Map<Integer, Integer> book) {
        Entry(String item, int min, int max, int weight) { this(item, 0, min, max, weight, null); }
    }

    static final List<Entry> DUNGEON = List.of(
        new Entry("saddle", 1, 1, 10), new Entry("iron_ingot", 1, 4, 10), new Entry("bread", 1, 1, 10),
        new Entry("wheat", 1, 4, 10), new Entry("gunpowder", 1, 4, 10), new Entry("string", 1, 4, 10),
        new Entry("bucket", 1, 1, 10), new Entry("golden_apple", 1, 1, 1), new Entry("redstone", 1, 4, 10),
        new Entry("record_13", 1, 1, 4), new Entry("record_cat", 1, 1, 4), new Entry("name_tag", 1, 1, 10),
        new Entry("golden_horse_armor", 1, 1, 2), new Entry("iron_horse_armor", 1, 1, 5),
        new Entry("diamond_horse_armor", 1, 1, 1));

    static final List<Entry> STRONGHOLD_CORRIDOR = List.of(
        new Entry("ender_pearl", 1, 1, 10), new Entry("diamond", 1, 3, 3), new Entry("iron_ingot", 1, 5, 10),
        new Entry("gold_ingot", 1, 3, 5), new Entry("redstone", 4, 9, 5), new Entry("bread", 1, 3, 15),
        new Entry("apple", 1, 3, 15), new Entry("iron_pickaxe", 1, 1, 5), new Entry("iron_sword", 1, 1, 5),
        new Entry("iron_chestplate", 1, 1, 5), new Entry("iron_helmet", 1, 1, 5), new Entry("iron_leggings", 1, 1, 5),
        new Entry("iron_boots", 1, 1, 5), new Entry("golden_apple", 1, 1, 1), new Entry("saddle", 1, 1, 1),
        new Entry("iron_horse_armor", 1, 1, 1), new Entry("golden_horse_armor", 1, 1, 1),
        new Entry("diamond_horse_armor", 1, 1, 1));

    static final List<Entry> STRONGHOLD_CROSSING = List.of(
        new Entry("iron_ingot", 1, 5, 10), new Entry("gold_ingot", 1, 3, 5), new Entry("redstone", 4, 9, 5),
        new Entry("coal", 3, 8, 10), new Entry("bread", 1, 3, 15), new Entry("apple", 1, 3, 15),
        new Entry("iron_pickaxe", 1, 1, 1));

    static final List<Entry> STRONGHOLD_LIBRARY = List.of(
        new Entry("book", 1, 3, 20), new Entry("paper", 2, 7, 20), new Entry("map", 1, 1, 1), new Entry("compass", 1, 1, 1));

    static final List<Entry> FORTRESS = List.of(
        new Entry("diamond", 1, 3, 5), new Entry("iron_ingot", 1, 5, 5), new Entry("gold_ingot", 1, 3, 15),
        new Entry("golden_sword", 1, 1, 5), new Entry("golden_chestplate", 1, 1, 5), new Entry("flint_and_steel", 1, 1, 5),
        new Entry("nether_wart", 3, 7, 5), new Entry("saddle", 1, 1, 10), new Entry("golden_horse_armor", 1, 1, 8),
        new Entry("iron_horse_armor", 1, 1, 5), new Entry("diamond_horse_armor", 1, 1, 3), new Entry("obsidian", 2, 4, 2));

    /**
     * ItemEnchantedBook.getRandom(rand, min, max, weight) added to a table
     * (WeightedRandomChestContent.func_177629_a): a book with one random
     * enchantment at a random level.
     */
    static List<Entry> withBook(List<Entry> table, Random rand, int min, int max, int weight) {
        Enchantment enchantment = Enchantment.ALL.get(rand.nextInt(Enchantment.ALL.size()));
        int lo = enchantment.minLevel(), hi = enchantment.maxLevel;
        int level = lo >= hi ? lo : rand.nextInt(hi - lo + 1) + lo;
        List<Entry> result = new ArrayList<>(table);
        result.add(new Entry("enchanted_book", 0, min, max, weight, java.util.Map.of(enchantment.id, level)));
        return result;
    }

    static List<Entry> withBook(List<Entry> table, Random rand) { return withBook(table, rand, 1, 1, 1); }

    /** WeightedRandomChestContent.generateChestContents into the chest at (x, y, z), if the world keeps one there. */
    static void fill(GenWorld w, int x, int y, int z, Random rand, List<Entry> table, int draws) {
        ItemStack[] slots = w.chestSlots(x, y, z);
        int total = 0;
        for (Entry entry : table) total += entry.weight();
        for (int i = 0; i < draws; i++) {
            int k = rand.nextInt(total);
            Entry picked = table.get(0);
            for (Entry entry : table) {
                k -= entry.weight();
                if (k < 0) { picked = entry; break; }
            }
            int count = picked.min() + rand.nextInt(picked.max() - picked.min() + 1);
            Item item = w.item(picked.item());
            int stackSize = item == null ? 64 : item.maxStackSize();
            if (stackSize >= count) put(slots, rand, item, picked, count);
            else for (int n = 0; n < count; n++) put(slots, rand, item, picked, 1);
        }
    }


    private static void put(ItemStack[] slots, Random rand, Item item, Entry entry, int count) {
        int slot = rand.nextInt(slots == null ? net.minecraft.inventory.Chest.SIZE : slots.length);
        if (slots == null || item == null) return;
        ItemStack stack = new ItemStack(item, count, entry.damage());
        if (entry.book() != null) entry.book().forEach(stack::addStoredEnchantment);
        slots[slot] = stack;
    }
}
