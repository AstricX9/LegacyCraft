package net.minecraft.world.biome;

import java.util.Map;

/**
 * The display names 1.8.9 shows for each biome id (F3 "Biome:" line, the
 * Customized-world biome slider, flat-world presets). They are hard-coded
 * English in vanilla, not translated. COMMUNITY_CORROBORATED (Minecraft Wiki
 * 1.8 biome list); HYPOTHESIS until checked on the F3 screen.
 */
public final class BiomeNames {
    private static final Map<Integer, String> NAMES = Map.ofEntries(
        Map.entry(0, "Ocean"), Map.entry(1, "Plains"), Map.entry(2, "Desert"), Map.entry(3, "Extreme Hills"),
        Map.entry(4, "Forest"), Map.entry(5, "Taiga"), Map.entry(6, "Swampland"), Map.entry(7, "River"),
        Map.entry(8, "Hell"), Map.entry(9, "The End"), Map.entry(10, "FrozenOcean"), Map.entry(11, "FrozenRiver"),
        Map.entry(12, "Ice Plains"), Map.entry(13, "Ice Mountains"), Map.entry(14, "MushroomIsland"),
        Map.entry(15, "MushroomIslandShore"), Map.entry(16, "Beach"), Map.entry(17, "DesertHills"),
        Map.entry(18, "ForestHills"), Map.entry(19, "TaigaHills"), Map.entry(20, "Extreme Hills Edge"),
        Map.entry(21, "Jungle"), Map.entry(22, "JungleHills"), Map.entry(23, "JungleEdge"), Map.entry(24, "Deep Ocean"),
        Map.entry(25, "Stone Beach"), Map.entry(26, "Cold Beach"), Map.entry(27, "Birch Forest"),
        Map.entry(28, "Birch Forest Hills"), Map.entry(29, "Roofed Forest"), Map.entry(30, "Cold Taiga"),
        Map.entry(31, "Cold Taiga Hills"), Map.entry(32, "Mega Taiga"), Map.entry(33, "Mega Taiga Hills"),
        Map.entry(34, "Extreme Hills+"), Map.entry(35, "Savanna"), Map.entry(36, "Savanna Plateau"),
        Map.entry(37, "Mesa"), Map.entry(38, "Mesa Plateau F"), Map.entry(39, "Mesa Plateau"),
        Map.entry(129, "Sunflower Plains"), Map.entry(130, "Desert M"), Map.entry(131, "Extreme Hills M"),
        Map.entry(132, "Flower Forest"), Map.entry(133, "Taiga M"), Map.entry(134, "Swampland M"),
        Map.entry(140, "Ice Plains Spikes"), Map.entry(149, "Jungle M"), Map.entry(151, "JungleEdge M"),
        Map.entry(155, "Birch Forest M"), Map.entry(156, "Birch Forest Hills M"), Map.entry(157, "Roofed Forest M"),
        Map.entry(158, "Cold Taiga M"), Map.entry(160, "Mega Spruce Taiga"), Map.entry(161, "Redwood Taiga Hills M"),
        Map.entry(162, "Extreme Hills+ M"), Map.entry(163, "Savanna M"), Map.entry(164, "Savanna Plateau M"),
        Map.entry(165, "Mesa (Bryce)"), Map.entry(166, "Mesa Plateau F M"), Map.entry(167, "Mesa Plateau M"));

    private BiomeNames() {}

    /** The vanilla display name, or null for an id 1.8.9 does not define. */
    public static String get(int id) { return NAMES.get(id); }
}
