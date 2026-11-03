package net.minecraft.enchantment;

import net.minecraft.entity.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.item.TestItems;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Enchantment ids, level windows, compatibility and protection (HYPOTHESIS: recalled 1.8.9 Enchantment subclasses). */
final class EnchantmentTest {
    private static ItemStack stack(String name, int maxDamage) { return new ItemStack(TestItems.item(name, 1, maxDamage)); }

    @Test
    void legacyIdsAndNamesMatchTheCommandNames() {
        assertSame(Enchantment.PROTECTION, Enchantment.byId(0));
        assertSame(Enchantment.SHARPNESS, Enchantment.byId(16));
        assertSame(Enchantment.EFFICIENCY, Enchantment.byId(32));
        assertSame(Enchantment.LURE, Enchantment.byId(62));
        assertNull(Enchantment.byId(9));
        assertSame(Enchantment.SILK_TOUCH, Enchantment.byName("silk_touch"));
        assertSame(Enchantment.FORTUNE, Enchantment.byName("35"));
        assertEquals("enchantment.protect.all", Enchantment.PROTECTION.translationKey());
        assertEquals(25, Enchantment.ALL.size());
    }

    @Test
    void levelWindowsFollowEachEnchantmentsFormula() {
        assertEquals(1, Enchantment.PROTECTION.minEnchantability(1));
        assertEquals(21, Enchantment.PROTECTION.maxEnchantability(1));
        assertEquals(34, Enchantment.PROTECTION.minEnchantability(4));
        assertEquals(54, Enchantment.PROTECTION.maxEnchantability(4));
        assertEquals(30, Enchantment.RESPIRATION.minEnchantability(3), "ten per level");
        assertEquals(60, Enchantment.RESPIRATION.maxEnchantability(3));
        assertEquals(1, Enchantment.EFFICIENCY.minEnchantability(1));
        assertEquals(61, Enchantment.EFFICIENCY.maxEnchantability(1), "1 + 10 x level + 50");
        assertEquals(15, Enchantment.SILK_TOUCH.minEnchantability(1));
        assertEquals(61, Enchantment.SILK_TOUCH.maxEnchantability(1));
        assertEquals(20, Enchantment.INFINITY.minEnchantability(1));
        assertEquals(50, Enchantment.INFINITY.maxEnchantability(1));
    }

    @Test
    void protectionsExcludeEachOtherExceptFeatherFalling() {
        assertFalse(Enchantment.PROTECTION.canApplyTogether(Enchantment.FIRE_PROTECTION));
        assertFalse(Enchantment.BLAST_PROTECTION.canApplyTogether(Enchantment.PROJECTILE_PROTECTION));
        assertTrue(Enchantment.PROTECTION.canApplyTogether(Enchantment.FEATHER_FALLING));
        assertTrue(Enchantment.FEATHER_FALLING.canApplyTogether(Enchantment.FIRE_PROTECTION));
        assertFalse(Enchantment.PROTECTION.canApplyTogether(Enchantment.PROTECTION), "never with itself");
    }

    @Test
    void damageTypesAndSilkTouchWithFortuneConflict() {
        assertFalse(Enchantment.SHARPNESS.canApplyTogether(Enchantment.SMITE));
        assertFalse(Enchantment.SILK_TOUCH.canApplyTogether(Enchantment.FORTUNE));
        assertFalse(Enchantment.FORTUNE.canApplyTogether(Enchantment.SILK_TOUCH));
        assertFalse(Enchantment.LOOTING.canApplyTogether(Enchantment.SILK_TOUCH));
        assertTrue(Enchantment.SHARPNESS.canApplyTogether(Enchantment.UNBREAKING));
        assertTrue(Enchantment.EFFICIENCY.canApplyTogether(Enchantment.FORTUNE));
    }

    @Test
    void anvilsAllowAFewExtrasBeyondTheTable() {
        assertTrue(Enchantment.SHARPNESS.canApply(stack("diamond_axe", 1561)), "axes take damage enchantments");
        assertFalse(Enchantment.SHARPNESS.canApply(stack("diamond_pickaxe", 1561)));
        assertTrue(Enchantment.EFFICIENCY.canApply(stack("shears", 238)));
        assertTrue(Enchantment.SILK_TOUCH.canApply(stack("shears", 238)));
        assertTrue(Enchantment.UNBREAKING.canApply(stack("flint_and_steel", 64)), "anything that wears");
        assertTrue(Enchantment.THORNS.canApply(stack("iron_boots", 195)), "any armour piece");
        assertFalse(Enchantment.EFFICIENCY.canApply(stack("diamond_sword", 1561)));
        assertFalse(Enchantment.FEATHER_FALLING.canApply(stack("iron_helmet", 165)));
        assertTrue(Enchantment.RESPIRATION.canApply(stack("iron_helmet", 165)));
    }

    @Test
    void protectionModifiersScaleWithTheSource() {
        // (6 + level^2) / 3 = 7.33 at level IV.
        assertEquals(5, Enchantment.PROTECTION.protectionModifier(4, DamageSource.FALL), "x0.75 against anything");
        assertEquals(18, Enchantment.FEATHER_FALLING.protectionModifier(4, DamageSource.FALL), "x2.5 against falls");
        assertEquals(0, Enchantment.FEATHER_FALLING.protectionModifier(4, DamageSource.LAVA));
        assertEquals(9, Enchantment.FIRE_PROTECTION.protectionModifier(4, DamageSource.LAVA), "x1.25 against fire");
        assertEquals(0, Enchantment.PROTECTION.protectionModifier(4, DamageSource.OUT_OF_WORLD), "the void ignores armour");
        assertEquals(0, Enchantment.SHARPNESS.protectionModifier(5, DamageSource.FALL));
    }
}
