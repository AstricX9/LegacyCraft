package net.minecraft.world;

import java.util.Objects;

/**
 * Everything the Create New World screen decides (master context §13.2, §71).
 * Immutable; the builder-style {@code with*} methods return copies.
 */
public record WorldSettings(long seed, GameType gameType, boolean mapFeatures, boolean hardcore, WorldType worldType,
                            boolean commandsAllowed, boolean bonusChest, String generatorOptions) {
    public WorldSettings {
        Objects.requireNonNull(gameType, "gameType");
        Objects.requireNonNull(worldType, "worldType");
        generatorOptions = generatorOptions == null ? "" : generatorOptions;
    }

    /** 1.8.9 game modes with their level.dat {@code GameType} ids. */
    public enum GameType {
        NOT_SET(-1, ""), SURVIVAL(0, "survival"), CREATIVE(1, "creative"), ADVENTURE(2, "adventure"), SPECTATOR(3, "spectator");

        private final int id;
        private final String name;

        GameType(int id, String name) { this.id = id; this.name = name; }
        public int getID() { return id; }
        public String getName() { return name; }
        /** "gameMode.survival" etc. — "Survival Mode" in en_US. */
        public String translationKey() { return "gameMode." + name; }

        public static GameType getByID(int id) {
            for (GameType type : values()) if (type.id == id) return type;
            return SURVIVAL;
        }

        public static GameType getByName(String name) {
            for (GameType type : values()) if (type.name.equals(name)) return type;
            return SURVIVAL;
        }
    }

    /**
     * The Create World seed rule (VERIFIED_HISTORY, widely documented): blank
     * uses the supplied random value, a parseable non-zero long is used as-is,
     * anything else uses {@link String#hashCode()}.
     */
    public static long parseSeed(String text, long randomSeed) {
        if (text == null || text.isEmpty()) return randomSeed;
        try {
            long value = Long.parseLong(text);
            return value != 0L ? value : randomSeed;
        } catch (NumberFormatException notANumber) {
            return text.hashCode();
        }
    }
}
