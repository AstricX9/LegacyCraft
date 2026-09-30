package net.minecraft.item;

import net.minecraft.nbt.NbtTag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Item tags: enchantments, stored enchantments, prior work and names (HYPOTHESIS: recalled 1.8.9 ItemStack NBT keys). */
final class ItemStackTest {
    private static final Item SWORD = TestItems.item("diamond_sword", 1, 1561), BOOK = TestItems.item("enchanted_book", 1, 0),
        DIRT = TestItems.item("dirt");

    @Test
    void tagsSurviveWritingAndReading() {
        ItemStack sword = new ItemStack(SWORD, 1, 12);
        sword.addEnchantment(16, 5);
        sword.addEnchantment(34, 3);
        sword.setRepairCost(3);
        sword.setCustomName("Edge");
        NbtTag.Compound tag = sword.writeTag();
        assertTrue(tag.get("ench") instanceof NbtTag.NbtList);
        ItemStack loaded = new ItemStack(SWORD, 1, 12);
        loaded.readTag(tag);
        assertEquals(Map.of(16, 5, 34, 3), loaded.enchantments());
        assertEquals(3, loaded.repairCost());
        assertEquals("Edge", loaded.customName());
        assertTrue(loaded.tagsEqual(sword));
    }

    @Test
    void storedEnchantmentsUseTheirOwnKeyAndKeepTheHigherLevel() {
        ItemStack book = new ItemStack(BOOK);
        book.addStoredEnchantment(0, 2);
        book.addStoredEnchantment(0, 1);
        assertEquals(2, book.storedEnchantments().get(0));
        NbtTag.Compound tag = book.writeTag();
        assertNull(tag.get("ench"));
        assertNotNull(tag.get("StoredEnchantments"));
    }

    @Test
    void plainStacksHaveNoTag() {
        assertNull(new ItemStack(DIRT, 5).writeTag());
    }

    @Test
    void stacksWithDifferentTagsDontMerge() {
        ItemStack named = new ItemStack(DIRT, 10), plain = new ItemStack(DIRT, 10);
        named.setCustomName("Soil");
        assertFalse(named.canMerge(plain));
        assertEquals(0, named.mergeFrom(plain));
        ItemStack otherNamed = new ItemStack(DIRT, 3);
        otherNamed.setCustomName("Soil");
        assertEquals(3, named.mergeFrom(otherNamed));
    }

    @Test
    void splittingAndCopyingKeepTheTags() {
        ItemStack named = new ItemStack(DIRT, 10);
        named.setCustomName("Soil");
        assertEquals("Soil", named.split(4).customName());
        assertEquals("Soil", named.copy().customName());
        assertEquals("Soil", named.withCount(1).customName());
        ItemStack book = new ItemStack(TestItems.item("book"));
        book.addStoredEnchantment(5, 1);
        assertEquals(1, book.asItem(BOOK).storedEnchantments().get(5));
    }
}
