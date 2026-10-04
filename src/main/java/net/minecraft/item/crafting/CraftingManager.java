package net.minecraft.item.crafting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * The crafting recipes (1.8.9 CraftingManager, HYPOTHESIS: the recipe list
 * is recalled, see recipes.json and DEV-024). Shaped recipes match anywhere
 * in the grid, also mirrored left to right; shapeless ones take their
 * ingredients in any cells. Shaped recipes are tried before shapeless ones,
 * bigger before smaller; the first match wins. Two damaged copies of the same
 * tool or armour piece repair into one (RecipeRepairItem).
 */
public final class CraftingManager {
    /** One ingredient: any of these items (a single item, or a group standing for vanilla's wildcard metadata). */
    record Ingredient(Set<Item> items) {
        boolean test(ItemStack stack) { return stack != null && items.contains(stack.item()); }
    }

    /** A recipe: whether a grid matches it, and what it makes. */
    public interface Recipe {
        boolean matches(ItemStack[] grid, int width, int height);
        ItemStack result(ItemStack[] grid);
        /** Cells the recipe covers (vanilla getRecipeSize), for the sort. */
        int size();
    }

    record Shaped(int width, int height, Ingredient[] cells, Item output, int count) implements Recipe {
        @Override public boolean matches(ItemStack[] grid, int gridWidth, int gridHeight) {
            for (int x = 0; x <= gridWidth - width; x++)
                for (int y = 0; y <= gridHeight - height; y++)
                    if (matchesAt(grid, gridWidth, gridHeight, x, y, true) || matchesAt(grid, gridWidth, gridHeight, x, y, false))
                        return true;
            return false;
        }

        /** Every grid cell: inside the placed recipe it must match its ingredient, outside it must be empty. */
        private boolean matchesAt(ItemStack[] grid, int gridWidth, int gridHeight, int offsetX, int offsetY, boolean mirrored) {
            for (int gx = 0; gx < gridWidth; gx++)
                for (int gy = 0; gy < gridHeight; gy++) {
                    int rx = gx - offsetX, ry = gy - offsetY;
                    Ingredient expected = null;
                    if (rx >= 0 && ry >= 0 && rx < width && ry < height)
                        expected = cells[(mirrored ? width - rx - 1 : rx) + ry * width];
                    ItemStack stack = grid[gx + gy * gridWidth];
                    if (expected == null ? stack != null : !expected.test(stack)) return false;
                }
            return true;
        }

        @Override public ItemStack result(ItemStack[] grid) { return new ItemStack(output, count); }
        @Override public int size() { return width * height; }
    }

    record Shapeless(List<Ingredient> ingredients, Item output, int count) implements Recipe {
        @Override public boolean matches(ItemStack[] grid, int width, int height) {
            List<Ingredient> left = new ArrayList<>(ingredients);
            for (ItemStack stack : grid) {
                if (stack == null) continue;
                boolean found = false;
                for (int i = 0; i < left.size(); i++)
                    if (left.get(i).test(stack)) {
                        left.remove(i);
                        found = true;
                        break;
                    }
                if (!found) return false;
            }
            return left.isEmpty();
        }

        @Override public ItemStack result(ItemStack[] grid) { return new ItemStack(output, count); }
        @Override public int size() { return ingredients.size(); }
    }

    /**
     * Two single, damaged-or-not copies of the same damageable item make one
     * whose remaining durability is the sum plus 5% of the maximum
     * (RecipeRepairItem).
     */
    static final class Repair implements Recipe {
        @Override public boolean matches(ItemStack[] grid, int width, int height) { return result(grid) != null; }

        @Override public ItemStack result(ItemStack[] grid) {
            ItemStack first = null, second = null;
            for (ItemStack stack : grid) {
                if (stack == null) continue;
                if (first == null) first = stack;
                else if (second == null) second = stack;
                else return null;
            }
            if (second == null || first.item() != second.item() || first.count() != 1 || second.count() != 1
                || !first.item().isDamageable()) return null;
            int max = first.item().maxDamage();
            int left = (max - first.damage()) + (max - second.damage()) + max * 5 / 100;
            return new ItemStack(first.item(), 1, Math.max(0, max - left));
        }

        @Override public int size() { return 4; }
    }

    private final List<Recipe> recipes = new ArrayList<>();
    private static final net.minecraft.util.ResourceLocation DATA = new net.minecraft.util.ResourceLocation("legacycraft:recipes.json");

    /** Loads recipes.json from the resource packs, resolving names against the item registry. */
    public static CraftingManager load(net.minecraft.client.resources.IResourceManager resources, net.minecraft.item.ItemRegistry items) {
        try (var reader = new java.io.InputStreamReader(resources.open(DATA), java.nio.charset.StandardCharsets.UTF_8)) {
            JsonObject root = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
            CraftingManager manager = parse(root, name -> items.get(new net.minecraft.util.ResourceLocation("minecraft", name)));
            // The enchanted golden apple has no model of its own, so its recipe can't be named in recipes.json:
            // eight blocks of gold around an apple (CraftingManager, 1.8.9).
            Item block = items.get(new net.minecraft.util.ResourceLocation("minecraft:gold_block"));
            Item apple = items.get(new net.minecraft.util.ResourceLocation("minecraft:apple"));
            Item notch = items.get(new net.minecraft.util.ResourceLocation("minecraft:enchanted_golden_apple"));
            if (block != null && apple != null && notch != null) {
                Ingredient gold = new Ingredient(Set.of(block));
                manager.recipes.add(new Shaped(3, 3, new Ingredient[] {gold, gold, gold, gold, new Ingredient(Set.of(apple)), gold,
                    gold, gold, gold}, notch, 1));
            }
            return manager;
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Could not read " + DATA, error);
        }
    }

    public List<Recipe> recipes() { return List.copyOf(recipes); }

    /**
     * Reads recipes.json. {@code items} resolves a LegacyCraft item name
     * ("oak_planks"); a recipe naming an unknown item is skipped with a warning.
     */
    public static CraftingManager parse(JsonObject json, Function<String, Item> items) {
        CraftingManager manager = new CraftingManager();
        JsonObject groups = json.has("groups") ? json.getAsJsonObject("groups") : new JsonObject();
        for (JsonElement raw : json.getAsJsonArray("recipes")) {
            JsonObject recipe = raw.getAsJsonObject();
            try {
                Item output = item(items, recipe.get("result").getAsString());
                int count = recipe.has("count") ? recipe.get("count").getAsInt() : 1;
                if (recipe.get("type").getAsString().equals("shaped")) {
                    JsonArray pattern = recipe.getAsJsonArray("pattern");
                    JsonObject key = recipe.getAsJsonObject("key");
                    int height = pattern.size(), width = 0;
                    for (JsonElement row : pattern) width = Math.max(width, row.getAsString().length());
                    Ingredient[] cells = new Ingredient[width * height];
                    for (int y = 0; y < height; y++) {
                        String row = pattern.get(y).getAsString();
                        for (int x = 0; x < row.length(); x++) {
                            char symbol = row.charAt(x);
                            if (symbol == ' ') continue;
                            if (!key.has(String.valueOf(symbol))) throw new IllegalArgumentException("no key for '" + symbol + "'");
                            cells[x + y * width] = ingredient(items, groups, key.get(String.valueOf(symbol)).getAsString());
                        }
                    }
                    manager.recipes.add(new Shaped(width, height, cells, output, count));
                } else {
                    List<Ingredient> ingredients = new ArrayList<>();
                    for (JsonElement name : recipe.getAsJsonArray("ingredients"))
                        ingredients.add(ingredient(items, groups, name.getAsString()));
                    manager.recipes.add(new Shapeless(ingredients, output, count));
                }
            } catch (RuntimeException error) {
                System.err.println("Skipping recipe " + recipe + ": " + error.getMessage());
            }
        }
        // CraftingManager's sort: shaped before shapeless, then larger recipes first (stable otherwise).
        manager.recipes.sort((a, b) -> {
            boolean aShapeless = a instanceof Shapeless, bShapeless = b instanceof Shapeless;
            if (aShapeless != bShapeless) return aShapeless ? 1 : -1;
            return Integer.compare(b.size(), a.size());
        });
        // Repair matches only two lone copies of one damageable item, which no listed recipe uses, so its place
        // in the order doesn't matter; last keeps the sort consistent.
        manager.recipes.add(new Repair());
        return manager;
    }

    private static Ingredient ingredient(Function<String, Item> items, JsonObject groups, String name) {
        Set<Item> set = new HashSet<>();
        if (name.startsWith("#")) {
            String group = name.substring(1);
            if (!groups.has(group)) throw new IllegalArgumentException("unknown group " + name);
            for (JsonElement member : groups.getAsJsonArray(group)) set.add(item(items, member.getAsString()));
        } else {
            set.add(item(items, name));
        }
        return new Ingredient(Set.copyOf(set));
    }

    private static Item item(Function<String, Item> items, String name) {
        Item item = items.apply(name);
        if (item == null) throw new IllegalArgumentException("unknown item " + name);
        return item;
    }

    /** What a grid of {@code width} x {@code height} cells (row by row, null for empty) makes, or null. */
    public ItemStack findMatchingRecipe(ItemStack[] grid, int width, int height) {
        for (Recipe recipe : recipes)
            if (recipe.matches(grid, width, height)) return recipe.result(grid);
        return null;
    }

    /** Items left in a cell after crafting uses one of them (Item.getContainerItem): a milk bucket leaves its bucket. */
    public static String containerItem(Item item) {
        return item.name().path().equals("milk_bucket") ? "bucket" : null;
    }
}
