package net.minecraft.inventory;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.item.TestItems;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The enchanting table's offers, costs and bookshelves (HYPOTHESIS: recalled 1.8.9 ContainerEnchantment). */
final class EnchantingTableTest {
    /** A player with levels and a seed that changes whenever levels are spent. */
    static final class Enchanter implements EnchantingTable.Player {
        int level, seed = 1234;
        boolean creative;
        Enchanter(int level) { this.level = level; }
        public int experienceLevel() { return level; }
        public int xpSeed() { return seed; }
        public boolean isCreative() { return creative; }
        public void removeExperienceLevels(int levels) {
            level = Math.max(0, level - levels);
            seed = seed * 31 + 7;
        }
    }

    private static EnchantingTable table(Enchanter player, int shelves) {
        EnchantingTable table = new EnchantingTable(name -> TestItems.item(name, 1, 0));
        table.open(player.seed, shelves);
        return table;
    }

    private static ItemStack sword() { return new ItemStack(TestItems.item("diamond_sword", 1, 1561)); }
    private static ItemStack lapis(int count) { return new ItemStack(TestItems.item("dye_blue"), count); }

    @Test
    void noItemMeansNoOffers() {
        EnchantingTable table = table(new Enchanter(30), 15);
        assertArrayEquals(new int[]{0, 0, 0}, table.levels);
        assertArrayEquals(new int[]{-1, -1, -1}, table.clues);
    }

    @Test
    void fifteenShelvesOfferThirtyLevelsAtTheBottom() {
        EnchantingTable table = table(new Enchanter(30), 15);
        table.itemSlot().set(sword());
        assertEquals(30, table.levels[2]);
        assertTrue(table.levels[0] >= 1 && table.levels[0] <= table.levels[1]);
        for (int clue : table.clues) assertNotNull(Enchantment.byId(clue & 255));
    }

    @Test
    void enchantingCostsLapisAndLevelsAndRerollsTheSeed() {
        Enchanter player = new Enchanter(35);
        EnchantingTable table = table(player, 15);
        table.itemSlot().set(sword());
        table.lapisSlot().set(lapis(5));
        int clue = table.clues[2];
        assertTrue(table.enchant(2, player));
        ItemStack sword = table.item();
        assertTrue(sword.isEnchanted());
        assertTrue(sword.enchantments().containsKey(clue & 255), "the clue is one of the enchantments given");
        assertEquals(2, table.lapis().count());
        assertEquals(32, player.level, "the bottom button costs three levels");
        assertNotEquals(1234, player.seed);
        assertArrayEquals(new int[]{0, 0, 0}, table.levels, "an enchanted item gets no more offers");
    }

    @Test
    void theSameSeedMakesTheSameOffer() {
        EnchantingTable first = table(new Enchanter(30), 10), second = table(new Enchanter(30), 10);
        first.itemSlot().set(sword());
        second.itemSlot().set(sword());
        assertArrayEquals(first.levels, second.levels);
        assertArrayEquals(first.clues, second.clues);
    }

    @Test
    void offersNeedTheLevelsAndTheLapis() {
        Enchanter player = new Enchanter(5);
        EnchantingTable table = table(player, 15);
        table.itemSlot().set(sword());
        table.lapisSlot().set(lapis(3));
        assertFalse(table.enchant(2, player), "30 levels asked, 5 held");
        player.level = 40;
        table.lapisSlot().set(lapis(2));
        assertFalse(table.enchant(2, player), "three lapis needed");
        player.creative = true;
        table.lapisSlot().set(null);
        assertTrue(table.enchant(2, player), "Creative needs neither");
    }

    @Test
    void aBookBecomesAnEnchantedBookWithStoredEnchantments() {
        Enchanter player = new Enchanter(30);
        EnchantingTable table = table(player, 15);
        table.itemSlot().set(new ItemStack(TestItems.item("book")));
        table.lapisSlot().set(lapis(3));
        assertTrue(table.levels[2] > 0);
        assertTrue(table.enchant(2, player));
        ItemStack book = table.item();
        assertTrue(EnchantmentHelper.isEnchantedBook(book));
        assertFalse(book.storedEnchantments().isEmpty());
        assertFalse(book.isEnchanted());
    }

    @Test
    void theItemSlotHoldsOneAndTheLapisSlotOnlyLapis() {
        EnchantingTable table = table(new Enchanter(0), 0);
        assertEquals(1, table.itemSlot().limit());
        assertTrue(table.lapisSlot().accepts(lapis(1)));
        assertFalse(table.lapisSlot().accepts(new ItemStack(TestItems.item("coal"))));
    }

    @Test
    void closingGivesEverythingBack() {
        EnchantingTable table = table(new Enchanter(0), 0);
        table.itemSlot().set(sword());
        table.lapisSlot().set(lapis(4));
        InventoryPlayer inventory = new InventoryPlayer();
        table.returnAll(inventory, stack -> fail("everything fits"));
        assertNull(table.item());
        assertNull(table.lapis());
        assertNotNull(inventory.mainInventory[0]);
        assertEquals(4, inventory.mainInventory[1].count());
    }

    // ---- bookshelves ----------------------------------------------------------------------

    private static EnchantingTable.Blocks world(Map<String, String> blocks) {
        return (x, y, z) -> blocks.getOrDefault(x + "," + y + "," + z, "air");
    }

    @Test
    void aFullRingTwoHighCountsThirtyTwo() {
        Map<String, String> blocks = new HashMap<>();
        for (int x = -2; x <= 2; x++)
            for (int z = -2; z <= 2; z++)
                if (Math.abs(x) == 2 || Math.abs(z) == 2)
                    for (int y = 0; y <= 1; y++) blocks.put(x + "," + y + "," + z, "bookshelf");
        assertEquals(32, EnchantingTable.countBookshelves(world(blocks), 0, 0, 0));
    }

    @Test
    void somethingBetweenBlocksTheShelf() {
        Map<String, String> blocks = new HashMap<>();
        blocks.put("2,0,0", "bookshelf");
        assertEquals(1, EnchantingTable.countBookshelves(world(blocks), 0, 0, 0));
        blocks.put("1,1,0", "torch");
        assertEquals(0, EnchantingTable.countBookshelves(world(blocks), 0, 0, 0), "the air gap must be two high");
    }
}
