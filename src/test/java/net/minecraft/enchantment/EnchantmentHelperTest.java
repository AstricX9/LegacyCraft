package net.minecraft.enchantment;

import net.minecraft.entity.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.item.TestItems;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The table's rolls and enchantment effects (HYPOTHESIS: recalled 1.8.9 EnchantmentHelper). */
final class EnchantmentHelperTest {
    private static ItemStack stack(String name, int maxDamage) { return new ItemStack(TestItems.item(name, 1, maxDamage)); }

    @Test
    void enchantabilityDependsOnTheMaterial() {
        assertEquals(22, EnchantmentHelper.enchantability(TestItems.item("golden_sword", 1, 32)));
        assertEquals(5, EnchantmentHelper.enchantability(TestItems.item("stone_pickaxe", 1, 131)));
        assertEquals(25, EnchantmentHelper.enchantability(TestItems.item("golden_helmet", 1, 77)));
        assertEquals(9, EnchantmentHelper.enchantability(TestItems.item("iron_boots", 1, 195)));
        assertEquals(1, EnchantmentHelper.enchantability(TestItems.item("book")));
        assertEquals(0, EnchantmentHelper.enchantability(TestItems.item("shears", 1, 238)));
    }

    @Test
    void onlyUnenchantedSingleToolsAndBooksAreEnchantable() {
        ItemStack sword = stack("iron_sword", 250);
        assertTrue(EnchantmentHelper.isEnchantable(sword));
        sword.addEnchantment(Enchantment.SHARPNESS.id, 1);
        assertFalse(EnchantmentHelper.isEnchantable(sword));
        assertTrue(EnchantmentHelper.isEnchantable(new ItemStack(TestItems.item("book"))));
        assertFalse(EnchantmentHelper.isEnchantable(new ItemStack(TestItems.item("book"), 2)));
        assertFalse(EnchantmentHelper.isEnchantable(new ItemStack(TestItems.item("dirt"))));
    }

    @Test
    void fifteenShelvesMakeTheBottomButtonThirty() {
        ItemStack sword = stack("iron_sword", 250);
        Random random = new Random(42);
        for (int i = 0; i < 200; i++) {
            assertEquals(30, EnchantmentHelper.buttonLevel(random, 2, 20, sword), "shelves beyond 15 don't count");
            int top = EnchantmentHelper.buttonLevel(random, 0, 15, sword);
            assertTrue(top >= 2 && top <= 10, "top is a third of 8..30: " + top);
        }
        assertEquals(0, EnchantmentHelper.buttonLevel(random, 2, 15, stack("shears", 238)), "nothing for enchantability 0");
    }

    @Test
    void rolledListsAreRepeatableCompatibleAndFitTheItem() {
        ItemStack pickaxe = stack("diamond_pickaxe", 1561);
        for (long seed = 0; seed < 300; seed++) {
            List<EnchantmentHelper.Data> first = EnchantmentHelper.buildEnchantmentList(new Random(seed), pickaxe, 30);
            List<EnchantmentHelper.Data> again = EnchantmentHelper.buildEnchantmentList(new Random(seed), pickaxe, 30);
            assertEquals(first, again, "same seed, same offer");
            assertNotNull(first);
            Set<Enchantment> seen = new HashSet<>();
            for (EnchantmentHelper.Data data : first) {
                assertTrue(data.enchantment().type.canEnchantItem(pickaxe.item()), data.toString());
                assertTrue(data.level() >= 1 && data.level() <= data.enchantment().maxLevel);
                for (Enchantment other : seen) assertTrue(other.canApplyTogether(data.enchantment()), first.toString());
                seen.add(data.enchantment());
            }
        }
    }

    @Test
    void booksCanRollAnything() {
        ItemStack book = new ItemStack(TestItems.item("book"));
        Set<Enchantment.Type> types = new HashSet<>();
        for (long seed = 0; seed < 2000; seed++) {
            List<EnchantmentHelper.Data> list = EnchantmentHelper.buildEnchantmentList(new Random(seed), book, 30);
            if (list != null) for (EnchantmentHelper.Data data : list) types.add(data.enchantment().type);
        }
        assertTrue(types.contains(Enchantment.Type.ARMOR));
        assertTrue(types.contains(Enchantment.Type.DIGGER));
        assertTrue(types.contains(Enchantment.Type.BOW));
    }

    @Test
    void protectionPointsAreHalfTheModifierPlusARandomHalf() {
        ItemStack boots = stack("diamond_boots", 429);
        boots.addEnchantment(Enchantment.FEATHER_FALLING.id, 4);
        ItemStack[] armour = {boots, null, null, null};
        Random random = new Random(7);
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < 500; i++) seen.add(EnchantmentHelper.protectionPoints(armour, DamageSource.FALL, random));
        assertEquals(9, seen.stream().min(Integer::compare).orElseThrow(), "modifier 18: 9 + 0..9");
        assertEquals(18, seen.stream().max(Integer::compare).orElseThrow());
        assertEquals(0, EnchantmentHelper.protectionPoints(armour, DamageSource.CACTUS, random));
    }

    @Test
    void protectionIsCappedAtTwentyFive() {
        ItemStack[] armour = new ItemStack[4];
        for (int i = 0; i < 4; i++) {
            armour[i] = stack("diamond_helmet", 363);
            armour[i].addEnchantment(Enchantment.PROTECTION.id, 4);
            armour[i].addEnchantment(Enchantment.FIRE_PROTECTION.id, 4);
        }
        Random random = new Random(3);
        for (int i = 0; i < 200; i++) {
            int points = EnchantmentHelper.protectionPoints(armour, DamageSource.LAVA, random);
            assertTrue(points >= 13 && points <= 25, "(25 + 1) / 2 + 0..12: " + points);
        }
    }

    @Test
    void fireProtectionShortensBurning() {
        ItemStack chest = stack("iron_chestplate", 240);
        chest.addEnchantment(Enchantment.FIRE_PROTECTION.id, 4);
        assertEquals(300 - 180, EnchantmentHelper.fireDuration(new ItemStack[]{null, null, chest, null}, 300));
        assertEquals(300, EnchantmentHelper.fireDuration(new ItemStack[4], 300));
    }

    @Test
    void unbreakingSavesWearOnTools() {
        Random random = new Random(11);
        ItemStack plain = stack("iron_pickaxe", 1000), tough = stack("iron_pickaxe", 1000);
        tough.addEnchantment(Enchantment.UNBREAKING.id, 3);
        for (int i = 0; i < 400; i++) {
            EnchantmentHelper.damageItem(plain, 1, random);
            EnchantmentHelper.damageItem(tough, 1, random);
        }
        assertEquals(400, plain.damage());
        assertTrue(tough.damage() > 50 && tough.damage() < 160, "about a quarter wears: " + tough.damage());
    }

    @Test
    void unbreakingOnArmourStillWearsSixtyPercentOfTheTime() {
        Random random = new Random(5);
        ItemStack helmet = stack("iron_helmet", 5000);
        helmet.addEnchantment(Enchantment.UNBREAKING.id, 3);
        for (int i = 0; i < 1000; i++) EnchantmentHelper.damageItem(helmet, 1, random);
        assertTrue(helmet.damage() > 600 && helmet.damage() < 760, "0.6 + 0.4 / 4 = 70%: " + helmet.damage());
    }

    @Test
    void enchantedBooksGlintAndReadTheirStoredEnchantments() {
        ItemStack book = new ItemStack(TestItems.item("enchanted_book", 1, 0));
        book.addStoredEnchantment(Enchantment.LOOTING.id, 2);
        assertTrue(EnchantmentHelper.hasGlint(book));
        assertFalse(book.isEnchanted(), "stored enchantments don't enchant the book itself");
        assertEquals(2, EnchantmentHelper.enchantments(book).get(Enchantment.LOOTING.id));
        assertFalse(EnchantmentHelper.hasGlint(new ItemStack(TestItems.item("book"))));
    }
}
