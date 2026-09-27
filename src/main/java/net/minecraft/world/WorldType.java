package net.minecraft.world;

import java.util.Locale;

/**
 * 1.8.9 world types with their save names ({@code generatorName} in
 * level.dat) and ids (VERIFIED_HISTORY for the set; ids and version numbers
 * HYPOTHESIS until checked against a vanilla level.dat).
 *
 * <p>Only DEFAULT and FLAT change what LegacyCraft generates today; the others
 * are selectable and saved faithfully but generate like DEFAULT (DEV-001,
 * DEV-011).
 */
public enum WorldType {
    DEFAULT(0, "default", 1, true),
    FLAT(1, "flat", 0, true),
    LARGE_BIOMES(2, "largeBiomes", 0, true),
    AMPLIFIED(3, "amplified", 0, true),
    CUSTOMIZED(4, "customized", 0, true),
    DEBUG_WORLD(5, "debug_all_block_states", 0, true),
    DEFAULT_1_1(8, "default_1_1", 0, false);

    private final int id;
    private final String name;
    private final int generatorVersion;
    private final boolean canBeCreated;

    WorldType(int id, String name, int generatorVersion, boolean canBeCreated) {
        this.id = id; this.name = name; this.generatorVersion = generatorVersion; this.canBeCreated = canBeCreated;
    }

    public int getWorldTypeID() { return id; }
    public String getWorldTypeName() { return name; }
    public int getGeneratorVersion() { return generatorVersion; }
    public boolean getCanBeCreated() { return canBeCreated; }
    public String getTranslateName() { return "generator." + name; }
    /** AMPLIFIED shows an extra line under its button. */
    public boolean showWorldInfoNotice() { return this == AMPLIFIED; }
    public String getTranslatedInfo() { return getTranslateName() + ".info"; }
    /** FLAT and CUSTOMIZED have a "Customize" screen. */
    public boolean isCustomizable() { return this == FLAT || this == CUSTOMIZED; }

    /** Save-name lookup, case-insensitive as vanilla's {@code parseWorldType}; null when unknown. */
    public static WorldType parse(String name) {
        for (WorldType type : values())
            if (type.name.toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT))) return type;
        return null;
    }

    /** The next type in the Create World button's cycle, skipping ones that can't be created. */
    public WorldType next(boolean allowDebug) {
        WorldType[] all = values();
        int index = ordinal();
        do {
            index = (index + 1) % all.length;
        } while (!all[index].canBeCreated || (all[index] == DEBUG_WORLD && !allowDebug));
        return all[index];
    }
}
