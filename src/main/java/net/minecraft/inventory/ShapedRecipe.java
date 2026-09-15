package net.minecraft.inventory;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import java.util.List;
import java.util.Objects;

/** A shaped recipe that can match at any grid offset and may be mirrored. */
public final class ShapedRecipe {
    private final ResourceLocation name;
    private final int width;
    private final int height;
    private final List<Item> ingredients;
    private final ItemStack output;

    public ShapedRecipe(ResourceLocation name, int width, int height, List<Item> ingredients, ItemStack output) {
        this.name = Objects.requireNonNull(name, "name");
        if (width < 1 || height < 1 || ingredients.size() != width * height)
            throw new IllegalArgumentException("Recipe dimensions do not match ingredients");
        this.width = width;
        this.height = height;
        this.ingredients = List.copyOf(ingredients);
        this.output = Objects.requireNonNull(output, "output").copy();
    }
    public ResourceLocation name() { return name; }
    public ItemStack output() { return output.copy(); }

    public boolean matches(CraftingGrid grid) {
        for (int y = 0; y <= grid.height() - height; y++)
            for (int x = 0; x <= grid.width() - width; x++)
                if (matchesAt(grid, x, y, false) || matchesAt(grid, x, y, true)) return true;
        return false;
    }

    private boolean matchesAt(CraftingGrid grid, int offsetX, int offsetY, boolean mirrored) {
        for (int y = 0; y < grid.height(); y++) {
            for (int x = 0; x < grid.width(); x++) {
                int recipeX = x - offsetX, recipeY = y - offsetY;
                Item expected = null;
                if (recipeX >= 0 && recipeX < width && recipeY >= 0 && recipeY < height) {
                    if (mirrored) recipeX = width - recipeX - 1;
                    expected = ingredients.get(recipeY * width + recipeX);
                }
                ItemStack actual = grid.get(x, y);
                if (expected == null ? actual != null : actual == null || actual.item() != expected) return false;
            }
        }
        return true;
    }
}
