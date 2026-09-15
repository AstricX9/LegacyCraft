package net.minecraft.inventory;

import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Ordered recipe registry; the first matching recipe wins. */
public final class RecipeBook {
    private final List<ShapedRecipe> recipes = new ArrayList<>();
    public void register(ShapedRecipe recipe) { recipes.add(recipe); }
    public ItemStack craft(CraftingGrid grid) {
        for (ShapedRecipe recipe : recipes) if (recipe.matches(grid)) return recipe.output();
        return null;
    }
    public List<ShapedRecipe> recipes() { return List.copyOf(recipes); }
}
