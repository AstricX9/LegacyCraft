package net.minecraft.client.gui;

import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.IllegalFormatException;
import java.util.Map;

/**
 * Translation lookup from the supplied 1.8.9 {@code lang/<code>.lang} files
 * (master context §124). All menu text comes from the resource pack, never
 * from strings baked into LegacyCraft, so menus read exactly like vanilla for
 * the chosen language.
 *
 * <p>Like vanilla, {@code en_US} is always loaded first and the selected
 * language is layered over it; a missing key renders as the key itself.
 */
public final class I18n {
    private static volatile Map<String, String> table = new HashMap<>();
    private static volatile String currentLanguage = "en_US";

    private I18n() {}

    /** Loads en_US plus {@code languageCode}; missing files are skipped. */
    public static void load(IResourceManager resources, String languageCode) {
        Map<String, String> merged = new HashMap<>();
        read(resources, "en_US", merged);
        if (!languageCode.equals("en_US")) read(resources, languageCode, merged);
        table = merged;
        currentLanguage = languageCode;
    }

    /** Replaces the table directly (tests and tools). */
    public static void set(Map<String, String> entries) { table = new HashMap<>(entries); }

    public static String currentLanguage() { return currentLanguage; }

    private static void read(IResourceManager resources, String code, Map<String, String> into) {
        ResourceLocation location = new ResourceLocation("minecraft", "lang/" + code + ".lang");
        if (!resources.exists(location)) return;
        try (InputStream stream = resources.open(location)) {
            parse(stream, into);
        } catch (IOException error) {
            System.err.println("Could not read " + location + ": " + error.getMessage());
        }
    }

    /** Parses {@code key=value} lines; {@code #} comments and blank lines are ignored. */
    public static void parse(InputStream stream, Map<String, String> into) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty() || line.charAt(0) == '#') continue;
            int equals = line.indexOf('=');
            if (equals <= 0) continue;
            into.put(line.substring(0, equals), line.substring(equals + 1));
        }
    }

    public static boolean hasKey(String key) { return table.containsKey(key); }

    /**
     * The translated, {@link String#format}-expanded text; the key itself when
     * untranslated, and the raw pattern when the arguments do not fit it.
     */
    public static String format(String key, Object... args) {
        String pattern = table.get(key);
        if (pattern == null) return key;
        if (args.length == 0 && pattern.indexOf('%') < 0) return pattern;
        try {
            return String.format(pattern, args);
        } catch (IllegalFormatException badArguments) {
            return "Format error: " + pattern;
        }
    }
}
