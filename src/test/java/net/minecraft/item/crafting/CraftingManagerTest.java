package net.minecraft.item.crafting;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.TestItems;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Recipe matching over recipes.json (HYPOTHESIS: the recipes are recalled 1.8.9 ones). */
final class CraftingManagerTest {
    private static final CraftingManager RECIPES = load();

    private static JsonObject json() {
        try (var reader = Files.newBufferedReader(Path.of("src/main/resources/assets/legacycraft/recipes.json"))) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (java.io.IOException error) {
            throw new IllegalStateException(error);
        }
    }

    private static CraftingManager load() { return CraftingManager.parse(json(), TestItems::item); }

    private static ItemStack s(String name) { return name == null ? null : new ItemStack(TestItems.item(name)); }

    /** A grid from rows of names ("." for empty). */
    private static ItemStack craft(int size, String... cells) {
        ItemStack[] grid = new ItemStack[size * size];
        for (int i = 0; i < cells.length; i++) grid[i] = cells[i].equals(".") ? null : s(cells[i]);
        return RECIPES.findMatchingRecipe(grid, size, size);
    }

    private static void assertMakes(String item, int count, ItemStack result) {
        assertNotNull(result, "expected " + item);
        assertEquals(item, result.item().name().path());
        assertEquals(count, result.count());
    }

    @Test
    void everyRecipeInTheDataLoads() {
        int listed = json().getAsJsonArray("recipes").size();
        assertEquals(listed + 1, RECIPES.recipes().size(), "all listed recipes plus tool repair");
    }

    @Test
    void aPickaxeTakesAnyPlanksAndTwoSticks() {
        assertMakes("wooden_pickaxe", 1, craft(3,
            "oak_planks", "birch_planks", "oak_planks",
            ".", "stick", ".",
            ".", "stick", "."));
        assertMakes("iron_pickaxe", 1, craft(3,
            "iron_ingot", "iron_ingot", "iron_ingot",
            ".", "stick", ".",
            ".", "stick", "."));
        assertNull(craft(3,
            "oak_planks", "oak_planks", "oak_planks",
            ".", "stick", ".",
            ".", "stick", "dirt"), "an extra item spoils the recipe");
    }

    @Test
    void shapedRecipesMayMoveAndMirror() {
        assertMakes("stick", 4, craft(3,
            ".", ".", ".",
            ".", ".", "spruce_planks",
            ".", ".", "spruce_planks"));
        assertMakes("wooden_axe", 1, craft(3,
            "oak_planks", "oak_planks", ".",
            "oak_planks", "stick", ".",
            ".", "stick", "."));
        // Mirrored left to right.
        assertMakes("wooden_axe", 1, craft(3,
            ".", "oak_planks", "oak_planks",
            ".", "stick", "oak_planks",
            ".", "stick", "."));
    }

    @Test
    void theTwoByTwoGridOnlyFitsSmallRecipes() {
        assertMakes("crafting_table", 1, craft(2, "oak_planks", "oak_planks", "oak_planks", "oak_planks"));
        assertMakes("oak_planks", 4, craft(2, ".", "oak_log", ".", "."));
        assertMakes("torch", 4, craft(2, "charcoal", ".", "stick", "."));
    }

    @Test
    void shapelessRecipesTakeTheirIngredientsAnywhere() {
        assertMakes("mushroom_stew", 1, craft(3, "bowl", ".", ".", ".", ".", "red_mushroom", "brown_mushroom", ".", "."));
        assertMakes("dye_orange", 2, craft(2, "dye_yellow", ".", ".", "dye_red"));
        assertNull(craft(2, "dye_yellow", "dye_yellow", ".", "dye_red"));
    }

    @Test
    void twoWornToolsRepairIntoOneWithAFivePercentBonus() {
        Item pickaxe = TestItems.item("diamond_pickaxe_test", 1, 1561);
        ItemStack[] grid = new ItemStack[4];
        grid[0] = new ItemStack(pickaxe, 1, 1000);
        grid[3] = new ItemStack(pickaxe, 1, 1200);
        ItemStack result = RECIPES.findMatchingRecipe(grid, 2, 2);
        assertNotNull(result);
        // (561 + 361 + 78) left of 1561.
        assertEquals(1561 - (561 + 361 + 78), result.damage());
    }
}
