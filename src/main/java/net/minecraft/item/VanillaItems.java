package net.minecraft.item;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * 1.8.9 item IDs and data values (HYPOTHESIS: recalled tables, checked
 * against the creative tab data). LegacyCraft has one item per vanilla
 * item model, so red dye is its own item; here each is tied to the vanilla
 * item it stands for ({@code minecraft:dye}, data 1), for saving, /give and
 * the advanced tooltip. Items listed in the creative tabs take their numeric
 * ID and data value from there; block items stand for their block; anything
 * else keeps its own name with data 0.
 */
public final class VanillaItems {
    private VanillaItems() {}

    /** Numeric item IDs 256-430 and the records (Item.itemRegistry, 1.8.9). */
    private static final Map<Integer, String> ITEM_NAMES = new HashMap<>();
    private static final Map<String, Integer> ITEM_IDS = new HashMap<>();

    static {
        String[] names = {"iron_shovel", "iron_pickaxe", "iron_axe", "flint_and_steel", "apple", "bow", "arrow", "coal",
            "diamond", "iron_ingot", "gold_ingot", "iron_sword", "wooden_sword", "wooden_shovel", "wooden_pickaxe",
            "wooden_axe", "stone_sword", "stone_shovel", "stone_pickaxe", "stone_axe", "diamond_sword", "diamond_shovel",
            "diamond_pickaxe", "diamond_axe", "stick", "bowl", "mushroom_stew", "golden_sword", "golden_shovel",
            "golden_pickaxe", "golden_axe", "string", "feather", "gunpowder", "wooden_hoe", "stone_hoe", "iron_hoe",
            "diamond_hoe", "golden_hoe", "wheat_seeds", "wheat", "bread", "leather_helmet", "leather_chestplate",
            "leather_leggings", "leather_boots", "chainmail_helmet", "chainmail_chestplate", "chainmail_leggings",
            "chainmail_boots", "iron_helmet", "iron_chestplate", "iron_leggings", "iron_boots", "diamond_helmet",
            "diamond_chestplate", "diamond_leggings", "diamond_boots", "golden_helmet", "golden_chestplate",
            "golden_leggings", "golden_boots", "flint", "porkchop", "cooked_porkchop", "painting", "golden_apple", "sign",
            "wooden_door", "bucket", "water_bucket", "lava_bucket", "minecart", "saddle", "iron_door", "redstone",
            "snowball", "boat", "leather", "milk_bucket", "brick", "clay_ball", "reeds", "paper", "book", "slime_ball",
            "chest_minecart", "furnace_minecart", "egg", "compass", "fishing_rod", "clock", "glowstone_dust", "fish",
            "cooked_fish", "dye", "bone", "sugar", "cake", "bed", "repeater", "cookie", "filled_map", "shears", "melon",
            "pumpkin_seeds", "melon_seeds", "beef", "cooked_beef", "chicken", "cooked_chicken", "rotten_flesh",
            "ender_pearl", "blaze_rod", "ghast_tear", "gold_nugget", "nether_wart", "potion", "glass_bottle", "spider_eye",
            "fermented_spider_eye", "blaze_powder", "magma_cream", "brewing_stand", "cauldron", "ender_eye",
            "speckled_melon", "spawn_egg", "experience_bottle", "fire_charge", "writable_book", "written_book", "emerald",
            "item_frame", "flower_pot", "carrot", "potato", "baked_potato", "poisonous_potato", "map", "golden_carrot",
            "skull", "carrot_on_a_stick", "nether_star", "pumpkin_pie", "fireworks", "firework_charge", "enchanted_book",
            "comparator", "netherbrick", "quartz", "tnt_minecart", "hopper_minecart", "prismarine_shard",
            "prismarine_crystals", "rabbit", "cooked_rabbit", "rabbit_stew", "rabbit_foot", "rabbit_hide", "armor_stand",
            "iron_horse_armor", "golden_horse_armor", "diamond_horse_armor", "lead", "name_tag", "command_block_minecart",
            "mutton", "cooked_mutton", "banner", "spruce_door", "birch_door", "jungle_door", "acacia_door", "dark_oak_door"};
        for (int i = 0; i < names.length; i++) {
            ITEM_NAMES.put(256 + i, names[i]);
            ITEM_IDS.put(names[i], 256 + i);
        }
        String[] records = {"13", "cat", "blocks", "chirp", "far", "mall", "mellohi", "stal", "strad", "ward", "11", "wait"};
        for (int i = 0; i < records.length; i++) {
            ITEM_NAMES.put(2256 + i, "record_" + records[i]);
            ITEM_IDS.put("record_" + records[i], 2256 + i);
        }
    }

    /** The vanilla name of a numeric item ID (256 and up), or null. */
    public static String itemName(int id) { return ITEM_NAMES.get(id); }
    /** The numeric ID of a vanilla item name (256 and up), or -1. */
    public static int itemId(String path) { return ITEM_IDS.getOrDefault(path, -1); }

    /** Items made by another item class than the block's (doors, seeds, signs...): they keep their own vanilla name. */
    private static final Map<String, String> OWN_NAMES = Map.ofEntries(
        Map.entry("oak_door", "wooden_door"), Map.entry("bottle_drinkable", "potion"), Map.entry("bottle_splash", "potion"),
        Map.entry("cod", "fish"), Map.entry("salmon", "fish"), Map.entry("clownfish", "fish"), Map.entry("pufferfish", "fish"),
        Map.entry("cooked_cod", "cooked_fish"), Map.entry("cooked_salmon", "cooked_fish"), Map.entry("charcoal", "coal"));

    /**
     * Ties every loaded item to its vanilla ID and data value, using the
     * creative tab listing ({@code id}/{@code meta}) where the item is listed.
     */
    static void assign(ItemRegistry registry, JsonObject creativeTabs, net.minecraft.block.BlockRegistry blocks) {
        Map<String, int[]> listed = new HashMap<>();
        if (creativeTabs != null)
            for (JsonElement tab : creativeTabs.getAsJsonArray("tabs"))
                for (JsonElement raw : tab.getAsJsonObject().getAsJsonArray("items")) {
                    JsonObject entry = raw.getAsJsonObject();
                    listed.putIfAbsent(entry.get("item").getAsString(), new int[] {entry.get("id").getAsInt(), entry.get("meta").getAsInt()});
                }
        for (Item item : registry.values()) {
            String path = item.name().path();
            int[] numeric = listed.get(path);
            if (numeric != null) {
                String name = numeric[0] < 256 ? blockName(blocks, numeric[0], item) : ITEM_NAMES.get(numeric[0]);
                if (name != null) {
                    item.setVanilla(new ResourceLocation("minecraft", name), item.isDamageable() ? 0 : numeric[1]);
                    continue;
                }
            }
            String own = OWN_NAMES.get(path);
            if (own != null) {
                // The bare splash bottle is a splash potion with no effect (16384), not a second water bottle.
                item.setVanilla(new ResourceLocation("minecraft", own), path.equals("bottle_splash") ? 16384 : 0);
            } else if (item.placedBlock() != null && !ITEM_IDS.containsKey(path)) {
                item.setVanilla(item.placedBlock().name(), 0);
            } else {
                item.setVanilla(item.name(), 0);
            }
        }
    }

    private static String blockName(net.minecraft.block.BlockRegistry blocks, int id, Item item) {
        net.minecraft.block.Block block = blocks == null ? null : blocks.byId(id);
        if (block != null) return block.name().path();
        return item.placedBlock() != null ? item.placedBlock().name().path() : null;
    }

    // ---- variants ------------------------------------------------------------------------------

    /** A spawn egg: the entity's vanilla id, numeric id and the egg's base and spot colours (EntityList.entityEggs). */
    public record Egg(String entity, int id, int primary, int secondary) {}

    public static final Egg[] EGGS = {
        new Egg("Creeper", 50, 894731, 0), new Egg("Skeleton", 51, 12698049, 4802889),
        new Egg("Spider", 52, 3419431, 11013646), new Egg("Zombie", 54, 44975, 7969893),
        new Egg("Slime", 55, 5349438, 8306542), new Egg("Ghast", 56, 16382457, 12369084),
        new Egg("PigZombie", 57, 15373203, 5009705), new Egg("Enderman", 58, 1447446, 0),
        new Egg("CaveSpider", 59, 803406, 11013646), new Egg("Silverfish", 60, 7237230, 3158064),
        new Egg("Blaze", 61, 16167425, 16775294), new Egg("LavaSlime", 62, 3407872, 16579584),
        new Egg("Bat", 65, 4996656, 986895), new Egg("Witch", 66, 3407872, 5349438),
        new Egg("Endermite", 67, 1447446, 7237230), new Egg("Guardian", 68, 5931634, 15826224),
        new Egg("Pig", 90, 15771042, 14377823), new Egg("Sheep", 91, 15198183, 16758197),
        new Egg("Cow", 92, 4470310, 10592673), new Egg("Chicken", 93, 10592673, 16711680),
        new Egg("Squid", 94, 2243405, 7375001), new Egg("Wolf", 95, 14144467, 13545366),
        new Egg("MushroomCow", 96, 10489616, 12040119), new Egg("Ozelot", 98, 15720061, 5653556),
        new Egg("EntityHorse", 100, 12623485, 15656192), new Egg("Rabbit", 101, 10051392, 7555121),
        new Egg("Villager", 120, 5651507, 12422002)};

    public static Egg egg(int id) {
        for (Egg egg : EGGS) if (egg.id == id) return egg;
        return null;
    }

    /** The 16 dye colours by data value (EnumDyeColor.byDyeDamage): banner names use these. */
    public static final String[] DYE_COLORS = {"black", "red", "green", "brown", "blue", "purple", "cyan", "silver", "gray",
        "pink", "lime", "yellow", "lightBlue", "magenta", "orange", "white"};

    /**
     * Makes the variants vanilla has but no model of their own: the
     * enchanted golden apple, a spawn egg per entity, each banner colour and
     * the potions the creative inventory lists (others on demand).
     */
    static void addVariants(ItemRegistry registry) {
        Item apple = registry.get(new ResourceLocation("minecraft:golden_apple"));
        if (apple != null) registry.addVariant(apple, "enchanted_golden_apple", Item.Kind.ENCHANTED_APPLE, 1);
        Item egg = registry.get(new ResourceLocation("minecraft:spawn_egg"));
        if (egg != null)
            for (Egg e : EGGS) registry.addVariant(egg, "spawn_egg_" + e.entity.toLowerCase(java.util.Locale.ROOT), Item.Kind.SPAWN_EGG, e.id);
        Item banner = registry.get(new ResourceLocation("minecraft:banner"));
        if (banner != null) {
            banner.setVanilla(banner.vanillaId(), 0);
            for (int color = 1; color < 16; color++)
                registry.addVariant(banner, "banner_" + DYE_COLORS[color].toLowerCase(java.util.Locale.ROOT), Item.Kind.BANNER, color);
        }
        for (int data : Potions.creativeList()) registry.potion(data);
    }

    // ---- names ---------------------------------------------------------------------------------

    /**
     * The display name of a variant (Item.getItemStackDisplayName overrides),
     * or null to use the item's translation key: "Spawn Creeper", "Splash
     * Potion of Healing", "Water Bottle", "Red Banner".
     */
    public static String displayName(Item item, java.util.function.Function<String, String> translate) {
        switch (item.kind()) {
            case SPAWN_EGG -> {
                Egg egg = egg(item.vanillaData());
                String name = translate.apply("item.monsterPlacer.name").trim();
                return egg == null ? name : name + " " + translate.apply("entity." + egg.entity + ".name");
            }
            case BANNER -> { return translate.apply("item.banner." + DYE_COLORS[item.vanillaData() & 15] + ".name"); }
            case ENCHANTED_APPLE -> { return translate.apply("item.appleGold.name"); }
            case POTION -> { return Potions.name(item.vanillaData(), translate); }
            default -> {
                if (item.vanillaId().path().equals("potion")) return Potions.name(item.vanillaData(), translate);
                return null;
            }
        }
    }
}
