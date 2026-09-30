package net.minecraft.stats;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The 34 achievements of 1.8.9 in their registration order (AchievementList,
 * HYPOTHESIS: map positions, icons, parents and special flags recalled).
 * Icons are LegacyCraft item names.
 */
public final class AchievementList {
    private AchievementList() {}

    public static final List<Achievement> ALL = List.of(
        new Achievement("openInventory", 0, 0, "book", null, false),
        new Achievement("mineWood", 2, 1, "oak_log", "openInventory", false),
        new Achievement("buildWorkBench", 4, -1, "crafting_table", "mineWood", false),
        new Achievement("buildPickaxe", 4, 2, "wooden_pickaxe", "buildWorkBench", false),
        new Achievement("buildFurnace", 3, 4, "furnace", "buildPickaxe", false),
        new Achievement("acquireIron", 1, 4, "iron_ingot", "buildFurnace", false),
        new Achievement("buildHoe", 2, -3, "wooden_hoe", "buildWorkBench", false),
        new Achievement("makeBread", -1, -3, "bread", "buildHoe", false),
        new Achievement("bakeCake", 0, -5, "cake", "buildHoe", false),
        new Achievement("buildBetterPickaxe", 6, 2, "stone_pickaxe", "buildPickaxe", false),
        new Achievement("cookFish", 2, 6, "cooked_cod", "buildFurnace", false),
        new Achievement("onARail", 2, 3, "rail", "acquireIron", true),
        new Achievement("buildSword", 6, -1, "wooden_sword", "buildWorkBench", false),
        new Achievement("killEnemy", 8, -1, "bone", "buildSword", false),
        new Achievement("killCow", 7, -3, "leather", "buildSword", false),
        new Achievement("flyPig", 9, -3, "saddle", "killCow", true),
        new Achievement("snipeSkeleton", 7, 0, "bow", "killEnemy", true),
        new Achievement("diamonds", -1, 5, "diamond", "acquireIron", false),
        new Achievement("diamondsToYou", -1, 2, "diamond", "diamonds", false),
        new Achievement("portal", -1, 7, "obsidian", "diamonds", false),
        new Achievement("ghast", -4, 8, "ghast_tear", "portal", true),
        new Achievement("blazeRod", 0, 9, "blaze_rod", "portal", false),
        new Achievement("potion", 2, 8, "bottle_drinkable", "blazeRod", false),
        new Achievement("theEnd", 3, 10, "ender_eye", "blazeRod", true),
        new Achievement("theEnd2", 4, 13, "dragon_egg", "theEnd", true),
        new Achievement("enchantments", -4, 4, "enchanting_table", "diamonds", false),
        new Achievement("overkill", -4, 1, "diamond_sword", "enchantments", true),
        new Achievement("bookcase", -3, 6, "bookshelf", "enchantments", false),
        new Achievement("breedCow", 7, -5, "wheat", "killCow", false),
        new Achievement("spawnWither", 7, 12, "skull_wither", "theEnd2", false),
        new Achievement("killWither", 7, 10, "nether_star", "spawnWither", false),
        new Achievement("fullBeacon", 7, 8, "beacon", "killWither", true),
        new Achievement("exploreAllBiomes", 4, 8, "diamond_boots", "theEnd", true),
        new Achievement("overpowered", 6, 4, "golden_apple", "buildBetterPickaxe", true));

    private static final Map<String, Achievement> BY_ID = new LinkedHashMap<>();
    static { for (Achievement achievement : ALL) BY_ID.put(achievement.id(), achievement); }

    public static final int MIN_COLUMN = ALL.stream().mapToInt(Achievement::column).min().orElse(0);
    public static final int MAX_COLUMN = ALL.stream().mapToInt(Achievement::column).max().orElse(0);
    public static final int MIN_ROW = ALL.stream().mapToInt(Achievement::row).min().orElse(0);
    public static final int MAX_ROW = ALL.stream().mapToInt(Achievement::row).max().orElse(0);

    /** By id ("mineWood") or stat name ("achievement.mineWood"); null when unknown. */
    public static Achievement get(String name) {
        if (name == null) return null;
        return BY_ID.get(name.startsWith("achievement.") ? name.substring(12) : name);
    }

    public static Achievement parent(Achievement achievement) { return get(achievement.parent()); }

    /**
     * What crafting an item unlocks (the ItemCrafted triggers in
     * SlotCrafting.onCrafting), or null.
     */
    public static String forCrafted(String item) {
        return switch (item) {
            case "crafting_table" -> "buildWorkBench";
            case "wooden_pickaxe" -> "buildPickaxe";
            case "furnace" -> "buildFurnace";
            case "wooden_hoe" -> "buildHoe";
            case "bread" -> "makeBread";
            case "cake" -> "bakeCake";
            case "stone_pickaxe" -> "buildBetterPickaxe";
            case "wooden_sword" -> "buildSword";
            case "enchanting_table" -> "enchantments";
            case "bookshelf" -> "bookcase";
            default -> null;
        };
    }

    /**
     * Biomes counted toward Adventuring Time, by id with vanilla's biomeName
     * (as saved in the stats file): every biome below id 128 except Hell,
     * The End, FrozenOcean and Extreme Hills Edge (BiomeGenBase.explorationBiomesList).
     */
    public static final Map<Integer, String> EXPLORATION_BIOMES;
    static {
        String[] names = {"Ocean", "Plains", "Desert", "Extreme Hills", "Forest", "Taiga", "Swampland", "River", null, null,
            null, "FrozenRiver", "Ice Plains", "Ice Mountains", "MushroomIsland", "MushroomIslandShore", "Beach", "DesertHills",
            "ForestHills", "TaigaHills", null, "Jungle", "JungleHills", "JungleEdge", "Deep Ocean", "Stone Beach", "Cold Beach",
            "Birch Forest", "Birch Forest Hills", "Roofed Forest", "Cold Taiga", "Cold Taiga Hills", "Mega Taiga",
            "Mega Taiga Hills", "Extreme Hills+", "Savanna", "Savanna Plateau", "Mesa", "Mesa Plateau F", "Mesa Plateau"};
        Map<Integer, String> biomes = new LinkedHashMap<>();
        for (int id = 0; id < names.length; id++) if (names[id] != null) biomes.put(id, names[id]);
        EXPLORATION_BIOMES = java.util.Collections.unmodifiableMap(biomes);
    }

    /** What taking an item out of a furnace unlocks (SlotFurnaceOutput.onCrafting), or null. */
    public static String forSmelted(String item) {
        return switch (item) {
            case "iron_ingot" -> "acquireIron";
            case "cooked_cod", "cooked_salmon" -> "cookFish";
            default -> null;
        };
    }

    /** What picking an item up unlocks (EntityItem.onCollideWithPlayer), or null. */
    public static String forPickedUp(String item) {
        return switch (item) {
            case "oak_log", "spruce_log", "birch_log", "jungle_log", "acacia_log", "dark_oak_log" -> "mineWood";
            case "leather" -> "killCow";
            case "diamond" -> "diamonds";
            case "blaze_rod" -> "blazeRod";
            default -> null;
        };
    }
}
