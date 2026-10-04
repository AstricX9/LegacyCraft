package net.minecraft.item;

import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.testing.GameData;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Vanilla IDs and data values, the variants without models of their own, and potion data (HYPOTHESIS: recalled 1.8.9 tables). */
final class ItemVariantsTest {
    private final ItemRegistry items = GameData.items();

    private static ResourceLocation id(String path) { return new ResourceLocation("minecraft", path); }

    @Test
    void modelItemsStandForTheirVanillaItemAndData() {
        Item red = items.get("minecraft:dye_red");
        assertEquals(id("dye"), red.vanillaId());
        assertEquals(1, red.vanillaData());
        assertSame(red, items.fromVanilla(id("dye"), 1));
        assertSame(items.get("minecraft:charcoal"), items.fromVanilla(id("coal"), 1));
        assertSame(items.get("minecraft:salmon"), items.fromVanilla(id("fish"), 1));
        assertSame(items.get("minecraft:red_wool"), items.fromVanilla(id("wool"), 14));
        assertSame(items.get("minecraft:skull_creeper"), items.fromVanilla(id("skull"), 4));
        assertEquals(id("wooden_door"), items.get("minecraft:oak_door").vanillaId());
    }

    @Test
    void damageableItemsMatchAnyDurability() {
        Item sword = items.get("minecraft:diamond_sword");
        assertEquals(0, sword.vanillaData());
        assertSame(sword, items.fromVanilla(id("diamond_sword"), 500));
    }

    @Test
    void resolveTakesVanillaNamesFirstThenModelNames() {
        assertSame(items.get("minecraft:dye_blue"), items.resolve("dye", 4));
        assertSame(items.get("minecraft:dye_blue"), items.resolve("minecraft:dye_blue", 0));
        assertNull(items.resolve("not_an_item", 0));
    }

    @Test
    void enchantedAppleSpawnEggsAndBannersAreVariants() {
        Item notch = items.fromVanilla(id("golden_apple"), 1);
        assertNotNull(notch);
        assertEquals(Item.Kind.ENCHANTED_APPLE, notch.kind());
        assertEquals(items.get("minecraft:golden_apple").food(), notch.food());
        Item creeperEgg = items.fromVanilla(id("spawn_egg"), 50);
        assertNotNull(creeperEgg);
        assertEquals(Item.Kind.SPAWN_EGG, creeperEgg.kind());
        assertEquals("Creeper", VanillaItems.egg(creeperEgg.vanillaData()).entity());
        Item white = items.fromVanilla(id("banner"), 15);
        assertNotNull(white);
        assertEquals(items.get("minecraft:banner").placedBlock(), white.placedBlock());
    }

    @Test
    void potionDataValuesGiveVanillaEffects() {
        List<PotionEffect> regen = Potions.effects(8193);
        assertEquals(Potion.REGENERATION, regen.get(0).potion());
        assertEquals(900, regen.get(0).duration());
        assertEquals(0, regen.get(0).amplifier());
        PotionEffect strong = Potions.effects(8225).get(0);
        assertEquals(1, strong.amplifier());
        assertEquals(450, strong.duration());
        assertEquals(9600, Potions.effects(8258).get(0).duration(), "extended Swiftness 8:00");
        assertEquals(2700, Potions.effects(16386).get(0).duration(), "splash Swiftness 2:15");
        assertTrue(Potions.effects(0).isEmpty());
        assertTrue(Potions.effects(16).isEmpty(), "Awkward Potion");
        assertTrue(Potions.isInstant(8261));
        assertFalse(Potions.isInstant(8193));
        assertEquals(0x385DC6, Potions.color(0));
    }

    @Test
    void creativePotionsFollowTheVanillaList() {
        List<Integer> list = Potions.creativeList();
        assertTrue(list.contains(8227), "3:00 Fire Resistance takes the base's place");
        assertTrue(list.contains(8261), "Healing");
        assertTrue(list.contains(16421), "Splash Healing II");
        assertFalse(list.contains(8195));
        Item healing = items.potion(8261);
        assertEquals(Item.Kind.POTION, healing.kind());
        assertEquals(Item.UseAction.DRINK, healing.useAction());
        assertSame(healing, items.fromVanilla(id("potion"), 8261));
        Item splash = items.potion(16388);
        assertNotEquals(Item.UseAction.DRINK, splash.useAction());
        assertTrue(Potions.isSplash(splash.vanillaData()));
    }

    @Test
    void waterBottleIsPotionZeroAndTheBareSplashBottleIsNot() {
        assertSame(items.get("minecraft:bottle_drinkable"), items.fromVanilla(id("potion"), 0));
        assertEquals(16384, items.get("minecraft:bottle_splash").vanillaData());
    }

    @Test
    void bowStagesAndCastRodAreNotItems() {
        assertNull(items.get(id("bow_pulling_0")));
        assertNull(items.get(id("bow_pulling_2")));
        assertNull(items.get(id("fishing_rod_cast")));
        assertNotNull(items.get(id("bow")));
    }

    @Test
    void namesForVariants() {
        java.util.function.Function<String, String> keys = key -> key;
        assertEquals("item.monsterPlacer.name entity.Creeper.name", VanillaItems.displayName(items.fromVanilla(id("spawn_egg"), 50), keys));
        assertEquals("potion.prefix.grenade potion.heal.postfix", Potions.name(16389, keys));
        assertEquals("item.emptyPotion.name", Potions.name(0, keys));
    }
}
