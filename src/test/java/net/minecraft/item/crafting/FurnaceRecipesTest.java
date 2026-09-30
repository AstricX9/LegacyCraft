package net.minecraft.item.crafting;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.block.TestBlocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.TestItems;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Furnace recipes over smelting.json and fuel burn times (HYPOTHESIS: recalled 1.8.9 FurnaceRecipes and getItemBurnTime). */
final class FurnaceRecipesTest {
    private static final FurnaceRecipes RECIPES = FurnaceRecipes.parse(json(), TestItems::item);

    private static JsonObject json() {
        try (var reader = Files.newBufferedReader(Path.of("src/main/resources/assets/legacycraft/smelting.json"))) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (java.io.IOException error) {
            throw new IllegalStateException(error);
        }
    }

    private static ItemStack s(String name) { return new ItemStack(TestItems.item(name)); }

    private static String smelt(String input) {
        ItemStack result = RECIPES.result(s(input));
        return result == null ? null : result.item().name().path();
    }

    @Test
    void everyListedInputHasARecipe() {
        // 26 listed recipes; groups add red sand, three more "log" logs and one more "log2" log.
        assertEquals(26 + 1 + 3 + 1, RECIPES.size());
    }

    @Test
    void oresSmeltIntoIngotsAndGems() {
        assertEquals("iron_ingot", smelt("iron_ore"));
        assertEquals("gold_ingot", smelt("gold_ore"));
        assertEquals("diamond", smelt("diamond_ore"));
        assertEquals("emerald", smelt("emerald_ore"));
        assertEquals("coal", smelt("coal_ore"));
        assertEquals("redstone", smelt("redstone_ore"));
        assertEquals("dye_blue", smelt("lapis_ore"));
        assertEquals("quartz", smelt("quartz_ore"));
    }

    @Test
    void everyVariantOfAWildcardBlockSmelts() {
        assertEquals("glass", smelt("sand"));
        assertEquals("glass", smelt("red_sand"));
        for (String log : new String[]{"oak_log", "spruce_log", "birch_log", "jungle_log", "acacia_log", "dark_oak_log"})
            assertEquals("charcoal", smelt(log), log);
    }

    @Test
    void onlyPlainStoneBricksCrackAndOnlyCodAndSalmonCook() {
        assertEquals("cracked_stonebrick", smelt("stonebrick"));
        assertNull(smelt("mossy_stonebrick"));
        assertEquals("cooked_cod", smelt("cod"));
        assertEquals("cooked_salmon", smelt("salmon"));
        assertNull(smelt("clownfish"));
        assertNull(smelt("pufferfish"));
        assertEquals("sponge", smelt("sponge_wet"));
        assertNull(smelt("dirt"));
    }

    @Test
    void experienceIsLookedUpByTheResult() {
        assertEquals(0.7f, RECIPES.experience(s("iron_ingot")));
        assertEquals(1.0f, RECIPES.experience(s("gold_ingot")));
        assertEquals(0.35f, RECIPES.experience(s("cooked_beef")));
        assertEquals(0.15f, RECIPES.experience(s("charcoal")));
        assertEquals(0f, RECIPES.experience(s("dirt")));
    }

    @Test
    void itemFuelsBurnForTheirVanillaTimes() {
        assertEquals(1600, FurnaceRecipes.burnTime(s("coal")));
        assertEquals(1600, FurnaceRecipes.burnTime(s("charcoal")));
        assertEquals(100, FurnaceRecipes.burnTime(s("stick")));
        assertEquals(20000, FurnaceRecipes.burnTime(s("lava_bucket")));
        assertEquals(2400, FurnaceRecipes.burnTime(s("blaze_rod")));
        assertEquals(200, FurnaceRecipes.burnTime(s("wooden_pickaxe")));
        assertEquals(200, FurnaceRecipes.burnTime(s("wooden_sword")));
        assertEquals(200, FurnaceRecipes.burnTime(s("wooden_hoe")));
        assertEquals(0, FurnaceRecipes.burnTime(s("stone_pickaxe")));
        assertEquals(0, FurnaceRecipes.burnTime(s("bucket")));
        assertEquals(0, FurnaceRecipes.burnTime(null));
    }

    @Test
    void blockFuelsGoByWhatTheyPlace() {
        assertEquals(300, FurnaceRecipes.burnTime(new ItemStack(TestItems.placing("oak_planks", TestBlocks.block("planks", "wood")))));
        assertEquals(150, FurnaceRecipes.burnTime(new ItemStack(TestItems.placing("oak_slab", TestBlocks.block("wooden_slab", "wood")))));
        assertEquals(16000, FurnaceRecipes.burnTime(new ItemStack(TestItems.placing("coal_block", TestBlocks.block("coal_block", "rock")))));
        assertEquals(100, FurnaceRecipes.burnTime(new ItemStack(TestItems.placing("oak_sapling", TestBlocks.block("sapling", "plants")))));
        assertEquals(0, FurnaceRecipes.burnTime(new ItemStack(TestItems.placing("oak_door", TestBlocks.door("wooden_door")))),
            "a door is not a block item in vanilla");
        assertEquals(0, FurnaceRecipes.burnTime(new ItemStack(TestItems.placing("ladder", TestBlocks.block("ladder", "circuits")))));
        assertEquals(0, FurnaceRecipes.burnTime(new ItemStack(TestItems.placing("cobblestone", TestBlocks.block("cobblestone", "rock")))));
    }

    @Test
    void aLavaBucketLeavesItsBucket() {
        assertEquals("bucket", FurnaceRecipes.containerItem(TestItems.item("lava_bucket")));
        assertNull(FurnaceRecipes.containerItem(TestItems.item("coal")));
    }
}
