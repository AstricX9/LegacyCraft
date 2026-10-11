package net.minecraft.client.additions;

/**
 * One optional change to the 1.8.9 experience, as listed in
 * {@code assets/legacycraft/additions.json}. Game additions are switched per
 * instance (in the game and in the launcher); launcher additions only change
 * the launcher and are listed here so the catalog stays in one place.
 */
public record Addition(String id, Category category, Scope scope, boolean recommended, String name, String description) {
    /** The groups the Additions screens show, in order. */
    public enum Category {
        GAMEPLAY("Gameplay"), GRAPHICS("Graphics"), INTERFACE("Interface"), ACCESSIBILITY("Accessibility"), LAUNCHER("Launcher");

        public final String title;

        Category(String title) { this.title = title; }

        static Category byId(String id) { return valueOf(id.toUpperCase(java.util.Locale.ROOT)); }
    }

    public enum Scope { GAME, LAUNCHER }
}
