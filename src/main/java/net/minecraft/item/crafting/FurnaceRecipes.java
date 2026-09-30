package net.minecraft.item.crafting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * What a furnace makes and burns (1.8.9 FurnaceRecipes and
 * TileEntityFurnace.getItemBurnTime; HYPOTHESIS: the recipe list, the
 * experience values and the fuel rules are recalled, see smelting.json).
 * Every recipe takes one item and makes one; the experience for a result is
 * looked up by the result item, as vanilla does when the output is taken.
 */
public final class FurnaceRecipes {
    private static final ResourceLocation DATA = new ResourceLocation("legacycraft:smelting.json");

    /** Ticks one item takes to cook (TileEntityFurnace.getCookTime: the same for everything). */
    public static final int COOK_TIME = 200;

    private final Map<Item, Item> results = new LinkedHashMap<>();
    private final Map<Item, Float> experience = new LinkedHashMap<>();

    /** Loads smelting.json from the resource packs, resolving names against the item registry. */
    public static FurnaceRecipes load(net.minecraft.client.resources.IResourceManager resources, net.minecraft.item.ItemRegistry items) {
        try (var reader = new java.io.InputStreamReader(resources.open(DATA), java.nio.charset.StandardCharsets.UTF_8)) {
            JsonObject root = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
            return parse(root, name -> items.get(new ResourceLocation("minecraft", name)));
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Could not read " + DATA, error);
        }
    }

    /**
     * Reads smelting.json. {@code items} resolves a LegacyCraft item name; a
     * recipe naming an unknown item is skipped with a warning.
     */
    public static FurnaceRecipes parse(JsonObject json, Function<String, Item> items) {
        FurnaceRecipes recipes = new FurnaceRecipes();
        JsonObject groups = json.has("groups") ? json.getAsJsonObject("groups") : new JsonObject();
        for (JsonElement raw : json.getAsJsonArray("recipes")) {
            JsonObject recipe = raw.getAsJsonObject();
            try {
                Item result = item(items, recipe.get("result").getAsString());
                float xp = recipe.has("xp") ? recipe.get("xp").getAsFloat() : 0f;
                for (Item input : inputs(items, groups, recipe.get("input").getAsString()))
                    recipes.add(input, result, xp);
            } catch (RuntimeException error) {
                System.err.println("Skipping smelting recipe " + recipe + ": " + error.getMessage());
            }
        }
        return recipes;
    }

    /** Adds a recipe (FurnaceRecipes.addSmeltingRecipe); an input keeps its first result. */
    public void add(Item input, Item result, float xp) {
        results.putIfAbsent(input, result);
        experience.putIfAbsent(result, xp);
    }

    /** What one of {@code input} smelts into (a new stack of one), or null (getSmeltingResult). */
    public ItemStack result(ItemStack input) {
        if (input == null) return null;
        Item result = results.get(input.item());
        return result == null ? null : new ItemStack(result);
    }

    /** Experience per item for taking {@code result} out of a furnace (getSmeltingExperience); 0 when unknown. */
    public float experience(ItemStack result) {
        if (result == null) return 0f;
        Float xp = experience.get(result.item());
        return xp == null ? 0f : xp;
    }

    public int size() { return results.size(); }

    /**
     * Ticks a fuel item burns for (TileEntityFurnace.getItemBurnTime), 0 when
     * it isn't fuel. Block items: wooden slabs 150, anything else made of wood
     * 300, a block of coal 16000. Wooden tools, swords and hoes 200; sticks and
     * saplings 100; coal and charcoal 1600; blaze rods 2400; a lava bucket
     * 20000. Doors place a wooden block but are not block items in vanilla
     * (ItemDoor), so they don't burn.
     */
    public static int burnTime(ItemStack stack) {
        if (stack == null) return 0;
        Item item = stack.item();
        Block block = item.placedBlock();
        if (block != null && !block.isAir() && !(block instanceof BlockDoor)) {
            String blockName = block.name().path();
            if (blockName.equals("wooden_slab")) return 150;
            if (block.material().equals("wood")) return 300;
            if (blockName.equals("coal_block")) return 16000;
            if (blockName.equals("sapling")) return 100;
        }
        return switch (item.name().path()) {
            case "wooden_axe", "wooden_pickaxe", "wooden_shovel", "wooden_sword", "wooden_hoe" -> 200;
            case "stick" -> 100;
            case "coal", "charcoal" -> 1600;
            case "lava_bucket" -> 20000;
            case "blaze_rod" -> 2400;
            default -> 0;
        };
    }

    /** Whether a stack burns in a furnace (TileEntityFurnace.isItemFuel). */
    public static boolean isFuel(ItemStack stack) { return burnTime(stack) > 0; }

    /** What a fuel leaves behind once burnt (Item.getContainerItem): a lava bucket leaves its bucket. */
    public static String containerItem(Item item) {
        return item.name().path().equals("lava_bucket") ? "bucket" : null;
    }

    private static Set<Item> inputs(Function<String, Item> items, JsonObject groups, String name) {
        Set<Item> set = new HashSet<>();
        if (name.startsWith("#")) {
            String group = name.substring(1);
            if (!groups.has(group)) throw new IllegalArgumentException("unknown group " + name);
            for (JsonElement member : groups.getAsJsonArray(group)) set.add(item(items, member.getAsString()));
        } else {
            set.add(item(items, name));
        }
        return set;
    }

    private static Item item(Function<String, Item> items, String name) {
        Item item = items.apply(name);
        if (item == null) throw new IllegalArgumentException("unknown item " + name);
        return item;
    }
}
