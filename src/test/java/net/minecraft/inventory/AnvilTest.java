package net.minecraft.inventory;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.TestItems;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Anvil repairs, combining, renaming and prior-work costs (HYPOTHESIS: recalled 1.8.9 ContainerRepair). */
final class AnvilTest {
    private static final Item PICKAXE = TestItems.item("diamond_pickaxe", 1, 1561), SWORD = TestItems.item("iron_sword", 1, 250),
        CHESTPLATE = TestItems.item("iron_chestplate", 1, 240), BOOK = TestItems.item("enchanted_book", 1, 0);

    private static Anvil anvil(ItemStack first, ItemStack second) {
        Anvil anvil = new Anvil(false);
        anvil.inputSlot(0).set(first);
        anvil.inputSlot(1).set(second);
        return anvil;
    }

    private static ItemStack book(Enchantment enchantment, int level) {
        ItemStack book = new ItemStack(BOOK);
        book.addStoredEnchantment(enchantment.id, level);
        return book;
    }

    @Test
    void eachMaterialRestoresAQuarterForOneLevel() {
        Anvil anvil = anvil(new ItemStack(PICKAXE, 1, 1000), new ItemStack(TestItems.item("diamond"), 5));
        assertEquals(0, anvil.output().damage(), "1000 -> 610 -> 220 -> 0");
        assertEquals(3, anvil.materialCost());
        assertEquals(3, anvil.cost());
        assertEquals(1, anvil.output().repairCost());
        assertEquals(3, anvil.take());
        assertNull(anvil.input(0));
        assertEquals(2, anvil.input(1).count(), "only the diamonds used are taken");
    }

    @Test
    void anUndamagedItemCantBeRepairedWithMaterial() {
        Anvil anvil = anvil(new ItemStack(PICKAXE), new ItemStack(TestItems.item("diamond")));
        assertNull(anvil.output());
        assertEquals(0, anvil.cost());
    }

    @Test
    void combiningAddsTheDurabilitiesPlusTwelvePercent() {
        Anvil anvil = anvil(new ItemStack(SWORD, 1, 200), new ItemStack(SWORD, 1, 100));
        assertEquals(20, anvil.output().damage(), "50 + 150 + 30 left of 250");
        assertEquals(2, anvil.cost());
    }

    @Test
    void differentItemsDontCombine() {
        assertNull(anvil(new ItemStack(SWORD, 1, 10), new ItemStack(PICKAXE, 1, 10)).output());
    }

    @Test
    void equalLevelsGoUpOneAndBooksCostHalf() {
        ItemStack sword = new ItemStack(SWORD);
        sword.addEnchantment(Enchantment.SHARPNESS.id, 3);
        Anvil anvil = anvil(sword, book(Enchantment.SHARPNESS, 3));
        assertEquals(4, anvil.output().level(Enchantment.SHARPNESS.id));
        assertEquals(4, anvil.cost(), "weight 10: 1 per level, at least 1 from a book");
    }

    @Test
    void levelsNeverPassTheMaximum() {
        ItemStack first = new ItemStack(SWORD), second = new ItemStack(SWORD);
        first.addEnchantment(Enchantment.SHARPNESS.id, 5);
        second.addEnchantment(Enchantment.SHARPNESS.id, 5);
        Anvil anvil = anvil(first, second);
        assertEquals(5, anvil.output().level(Enchantment.SHARPNESS.id));
        assertEquals(5, anvil.cost());
    }

    @Test
    void rareEnchantmentsCostMore() {
        Anvil anvil = anvil(new ItemStack(CHESTPLATE), book(Enchantment.THORNS, 3));
        assertEquals(3, anvil.output().level(Enchantment.THORNS.id));
        assertEquals(12, anvil.cost(), "weight 1: 8 per level, halved from a book");
    }

    @Test
    void aConflictingEnchantmentIsLeftOutForOneLevel() {
        ItemStack sword = new ItemStack(SWORD);
        sword.addEnchantment(Enchantment.SHARPNESS.id, 2);
        Anvil anvil = anvil(sword, book(Enchantment.SMITE, 2));
        assertEquals(0, anvil.output().level(Enchantment.SMITE.id));
        assertEquals(1, anvil.cost());
    }

    @Test
    void enchantmentsThatDontFitAreRefusedOutsideCreative() {
        assertNull(anvil(new ItemStack(PICKAXE), book(Enchantment.SHARPNESS, 1)).output());
        Anvil creative = new Anvil(true);
        creative.inputSlot(0).set(new ItemStack(PICKAXE));
        creative.inputSlot(1).set(book(Enchantment.SHARPNESS, 1));
        assertEquals(1, creative.output().level(Enchantment.SHARPNESS.id));
    }

    @Test
    void renamingCostsOneAndClearingANameToo() {
        Anvil anvil = anvil(new ItemStack(TestItems.item("dirt"), 3), null);
        assertNull(anvil.output(), "nothing to do");
        anvil.setName("Soil");
        assertEquals("Soil", anvil.output().customName());
        assertEquals(3, anvil.output().count());
        assertEquals(1, anvil.cost());
        ItemStack named = anvil.output();
        anvil.take();
        Anvil again = anvil(named, null);
        again.setName("");
        assertFalse(again.output().hasCustomName());
        assertEquals(1 + 1, again.cost(), "one for the change plus one of prior work");
    }

    @Test
    void priorWorkAddsUpAndDoublesPlusOne() {
        ItemStack first = new ItemStack(SWORD, 1, 100), second = new ItemStack(SWORD, 1, 100);
        first.setRepairCost(7);
        second.setRepairCost(3);
        Anvil anvil = anvil(first, second);
        assertEquals(10 + 2, anvil.cost());
        assertEquals(15, anvil.output().repairCost());
    }

    @Test
    void fortyLevelsIsTooExpensiveExceptForARenameAlone() {
        ItemStack worn = new ItemStack(SWORD, 1, 100);
        worn.setRepairCost(39);
        Anvil anvil = anvil(worn, new ItemStack(SWORD, 1, 100));
        assertEquals(41, anvil.cost());
        assertNull(anvil.output(), "too expensive");
        Anvil rename = anvil(worn, null);
        rename.setName("Old");
        assertEquals(39, rename.cost(), "a rename alone is held at 39");
        assertNotNull(rename.output());
    }

    @Test
    void theResultNeedsTheLevels() {
        Anvil anvil = anvil(new ItemStack(PICKAXE, 1, 1000), new ItemStack(TestItems.item("diamond"), 1));
        assertFalse(anvil.canTake(0));
        assertTrue(anvil.canTake(1));
        assertTrue(Anvil.isRepairMaterial(TestItems.item("wooden_axe", 1, 59), TestItems.item("spruce_planks")));
        assertTrue(Anvil.isRepairMaterial(TestItems.item("chainmail_helmet", 1, 165), TestItems.item("iron_ingot")));
        assertFalse(Anvil.isRepairMaterial(TestItems.item("golden_sword", 1, 32), TestItems.item("iron_ingot")));
    }
}
