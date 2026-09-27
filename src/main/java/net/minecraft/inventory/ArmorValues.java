package net.minecraft.inventory;

import net.minecraft.item.Item;

import java.util.Map;

/**
 * Which armor slot an item fits and how many armor points it gives
 * (1.8.9 pre-toughness armor, master context §30.5). Points per piece
 * (helmet, chestplate, leggings, boots): leather 1/3/2/1, chainmail 2/5/4/1,
 * iron 2/6/5/2, gold 2/5/3/1, diamond 3/8/6/3 — COMMUNITY_CORROBORATED.
 * Pumpkins and mob heads also fit the helmet slot (0 points).
 */
public final class ArmorValues {
    private static final Map<String, int[]> MATERIALS = Map.of(
        "leather", new int[]{1, 3, 2, 1},
        "chainmail", new int[]{2, 5, 4, 1},
        "iron", new int[]{2, 6, 5, 2},
        "golden", new int[]{2, 5, 3, 1},
        "diamond", new int[]{3, 8, 6, 3});
    private static final String[] PIECES = {"helmet", "chestplate", "leggings", "boots"};

    private ArmorValues() {}

    /** Armor inventory slot (0 boots .. 3 helmet) the item goes in, or -1. */
    public static int armorSlot(Item item) {
        String path = item.name().path();
        for (int piece = 0; piece < PIECES.length; piece++)
            if (path.endsWith("_" + PIECES[piece]) && MATERIALS.containsKey(path.substring(0, path.length() - PIECES[piece].length() - 1)))
                return 3 - piece;
        if (path.equals("pumpkin") || path.startsWith("skull_")) return 3;
        return -1;
    }

    public static int points(Item item, int armorSlot) {
        String path = item.name().path();
        int piece = 3 - armorSlot;
        if (piece < 0 || piece > 3 || !path.endsWith("_" + PIECES[piece])) return 0;
        int[] values = MATERIALS.get(path.substring(0, path.length() - PIECES[piece].length() - 1));
        return values == null ? 0 : values[piece];
    }
}
