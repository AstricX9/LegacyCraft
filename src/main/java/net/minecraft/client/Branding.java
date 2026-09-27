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

    private Branding() {}

    /** F3's first line: "Minecraft 1.8.9 (1.8.9/legacycraft)". */
    public static String debugVersionLine() { return "Minecraft " + VERSION + " (" + VERSION + "/" + CLIENT_BRAND + ")"; }
}
