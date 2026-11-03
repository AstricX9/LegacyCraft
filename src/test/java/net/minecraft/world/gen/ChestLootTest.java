package net.minecraft.world.gen;

import net.minecraft.inventory.Chest;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.testing.GameData;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.Biome;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Generated chest loot and chest lids (1.8.9 tables). */
final class ChestLootTest {
    private static Item item(String name) { return GameData.items().get(new ResourceLocation("minecraft", name)); }

    /** Only what loot filling needs: one chest's slots and the items. */
    private static final class OneChest implements GenWorld {
        final ItemStack[] slots = new ItemStack[Chest.SIZE];
        @Override public int get(int x, int y, int z) { return 0; }
        @Override public void set(int x, int y, int z, int packed) {}
        @Override public Biome biome(int x, int z) { return null; }
        @Override public int height(int x, int z) { return 0; }
        @Override public int light(int x, int y, int z) { return 15; }
        @Override public int blockLight(int x, int y, int z) { return 0; }
        @Override public boolean seesSky(int x, int y, int z) { return true; }
        @Override public GenBlocks blocks() { return GameData.genBlocks(); }
        @Override public ItemStack[] chestSlots(int x, int y, int z) { return slots; }
        @Override public Item item(String name) { return ChestLootTest.item(name); }
    }

    @Test
    void everyLootItemExists() {
        for (List<ChestLoot.Entry> table : List.of(ChestLoot.DUNGEON, ChestLoot.STRONGHOLD_CORRIDOR, ChestLoot.STRONGHOLD_CROSSING,
            ChestLoot.STRONGHOLD_LIBRARY, ChestLoot.FORTRESS))
            for (ChestLoot.Entry entry : table) assertNotNull(item(entry.item()), entry.item());
        assertNotNull(item("enchanted_book"));
    }

    @Test
    void aDungeonChestGetsUpToEightStacksTheSameWayEachTime() {
        OneChest a = new OneChest(), b = new OneChest();
        Random ra = new Random(5), rb = new Random(5);
        ChestLoot.fill(a, 0, 0, 0, ra, ChestLoot.withBook(ChestLoot.DUNGEON, ra), 8);
        ChestLoot.fill(b, 0, 0, 0, rb, ChestLoot.withBook(ChestLoot.DUNGEON, rb), 8);
        int stacks = 0;
        for (int i = 0; i < Chest.SIZE; i++) {
            if (a.slots[i] == null) { assertNull(b.slots[i]); continue; }
            stacks++;
            assertEquals(a.slots[i].item(), b.slots[i].item());
            assertEquals(a.slots[i].count(), b.slots[i].count());
        }
        assertTrue(stacks >= 1 && stacks <= 8, "stacks " + stacks);
        assertEquals(ra.nextLong(), rb.nextLong());
    }

    @Test
    void aBookEntryCarriesItsEnchantment() {
        List<ChestLoot.Entry> table = ChestLoot.withBook(List.of(), new Random(1), 1, 1, 1);
        OneChest chest = new OneChest();
        ChestLoot.fill(chest, 0, 0, 0, new Random(2), table, 1);
        ItemStack book = java.util.Arrays.stream(chest.slots).filter(java.util.Objects::nonNull).findFirst().orElseThrow();
        assertEquals(item("enchanted_book"), book.item());
        assertFalse(book.storedEnchantments().isEmpty());
    }

    @Test
    void theLidOpensAndClosesWithItsSounds() {
        Chest chest = new Chest();
        chest.viewers = 1;
        assertEquals(Chest.LidSound.OPEN, chest.tickLid());
        for (int i = 0; i < 12; i++) assertEquals(Chest.LidSound.NONE, chest.tickLid());
        assertEquals(1f, chest.lidAngle);
        chest.viewers = 0;
        int closes = 0;
        for (int i = 0; i < 12; i++) if (chest.tickLid() == Chest.LidSound.CLOSE) closes++;
        assertEquals(1, closes);
        assertEquals(0f, chest.lidAngle);
    }
}
