package net.minecraft.client;

/**
 * Every place the client names itself. Vanilla 1.8.9 prints "Minecraft 1.8.9"
 * and a Mojang copyright line on the title screen; LegacyCraft is a clean-room
 * replica and must not imply it is a Mojang product (master context §2.8), so
 * those two strings are replaced here and only here (deviations.yml DEV-012).
 * The F3 version line keeps vanilla's format with LegacyCraft as the client
 * brand, exactly like any non-vanilla 1.8.9 client reports itself.
 */
public final class Branding {
    public static final String VERSION = "1.8.9";
    /** What ClientBrandRetriever would report ("vanilla" for Mojang's client). */
    public static final String CLIENT_BRAND = "legacycraft";
    public static final String TITLE_VERSION = "LegacyCraft " + VERSION;
    public static final String TITLE_NOTICE = "Not affiliated with Mojang or Microsoft";
    public static final String WINDOW_TITLE = "LegacyCraft " + VERSION;
    /** LegacyCraft's own release ("lc:" in version.txt), e.g. "a1.0.0". */
    public static final String LEGACYCRAFT_VERSION = versionFileEntry("lc", "unknown");
    /** The Minecraft release LegacyCraft replicates ("mc:" in version.txt). */
    public static final String MINECRAFT_VERSION = versionFileEntry("mc", VERSION);

    private Branding() {}

    /** The title-screen tooltip over {@link #TITLE_VERSION}: LegacyCraft's version, then the Minecraft one it references. */
    public static java.util.List<String> versionTooltip() {
        return java.util.List.of("LegacyCraft " + LEGACYCRAFT_VERSION, "Minecraft " + MINECRAFT_VERSION);
    }

    /** One "key: value" line of the bundled /version.txt (copied from the repository root at build time). */
    private static String versionFileEntry(String key, String fallback) {
        try (var stream = Branding.class.getResourceAsStream("/version.txt")) {
            if (stream == null) return fallback;
            for (String line : new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split("\\R")) {
                int colon = line.indexOf(':');
                if (colon > 0 && line.substring(0, colon).trim().equalsIgnoreCase(key)) {
                    String value = line.substring(colon + 1).trim();
                    return value.isEmpty() ? fallback : value;
                }
            }
        } catch (java.io.IOException ignored) {
            // fall through
        }
        return fallback;
    }

    /** F3's first line: "Minecraft 1.8.9 (1.8.9/legacycraft)". */
    public static String debugVersionLine() { return "Minecraft " + VERSION + " (" + VERSION + "/" + CLIENT_BRAND + ")"; }
}
