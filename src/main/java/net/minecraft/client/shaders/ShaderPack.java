package net.minecraft.client.shaders;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * An OptiFine-format shader pack (a .zip or folder with a {@code shaders/}
 * folder inside), read into memory: the program sources with their
 * {@code #include}s filled in and the game's standard macros added,
 * shaders.properties, and the block IDs from block.properties. Option
 * changes come from {@code <pack name>.txt} next to the pack, OptiFine's own
 * format ({@code SHADOW_QUALITY=0}).
 */
public final class ShaderPack {
    private static final Pattern INCLUDE = Pattern.compile("^\\s*#\\s*include\\s+[\"<](.+?)[\">].*$");
    private static final Pattern OPTION = Pattern.compile("^(\\s*)(//)?\\s*#define\\s+(\\w+)(?:\\s+([^\\s/]+))?(.*)$");
    private static final Pattern CONST_OPTION = Pattern.compile("^(\\s*)const\\s+(\\w+)\\s+(\\w+)\\s*=\\s*([^;]+);(.*)$");
    private static final Pattern VALUES = Pattern.compile("//\\s*\\[(.*?)]");

    public final String name;
    private final Path path;
    /** Files under {@code shaders/}, by path relative to it ("world0/final.fsh", "lib/common.glsl"). */
    private final Map<String, byte[]> files;
    private final Map<String, String> options;
    private final Map<String, String> standardMacros;
    /** Macros visible to shaders.properties: the standard ones plus the pack's options. */
    final Map<String, String> macros = new HashMap<>();
    /** shaders.properties after its #if lines, in file order. */
    final Map<String, String> properties = new LinkedHashMap<>();
    /** Block name to its ID rules, from block.properties. */
    private final Map<String, List<BlockRule>> blocks = new HashMap<>();
    /** variable.* and uniform.* lines of shaders.properties, in order. */
    final List<CustomUniform> customUniforms = new ArrayList<>();

    record CustomUniform(String type, String name, Expression.Node expression, boolean uniform) {}
    private record BlockRule(int id, String[][] conditions) {}
    /** Entity and item names (see {@link #key}) to their IDs, from entity.properties and item.properties. */
    private final Map<String, Integer> entities = new HashMap<>(), items = new HashMap<>();

    private ShaderPack(Path path, String name, Map<String, byte[]> files, Map<String, String> options, Map<String, String> standardMacros) {
        this.path = path;
        this.name = name;
        this.files = files;
        this.options = options;
        this.standardMacros = standardMacros;
    }

    /** Reads the pack at {@code path} (a .zip or a folder). */
    public static ShaderPack load(Path path, Map<String, String> standardMacros) throws IOException {
        Map<String, byte[]> all = new HashMap<>();
        if (Files.isDirectory(path)) {
            try (Stream<Path> walk = Files.walk(path)) {
                for (Path file : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator)
                    all.put(path.relativize(file).toString().replace('\\', '/'), Files.readAllBytes(file));
            }
        } else {
            try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(path))) {
                for (ZipEntry entry; (entry = zip.getNextEntry()) != null; )
                    if (!entry.isDirectory()) all.put(entry.getName().replace('\\', '/'), zip.readAllBytes());
            }
        }
        // The shaders folder may sit one level down (a zip of the pack's folder).
        String root = null;
        for (String file : all.keySet()) {
            int at = file.indexOf("shaders/");
            if (at >= 0 && (at == 0 || file.charAt(at - 1) == '/') && (root == null || at < root.length()))
                root = file.substring(0, at + "shaders/".length());
        }
        if (root == null) throw new IOException("there's no shaders folder in " + path.getFileName());
        Map<String, byte[]> files = new HashMap<>();
        for (Map.Entry<String, byte[]> e : all.entrySet())
            if (e.getKey().startsWith(root)) files.put(e.getKey().substring(root.length()), e.getValue());

        String name = path.getFileName().toString();
        Map<String, String> options = new HashMap<>();
        Path config = path.resolveSibling(name + ".txt");
        if (Files.isRegularFile(config)) {
            Properties p = new Properties();
            try (InputStream in = Files.newInputStream(config)) { p.load(in); }
            for (String key : p.stringPropertyNames()) options.put(key, p.getProperty(key).trim());
        }
        ShaderPack pack = new ShaderPack(path, name, files, options, standardMacros);
        pack.readProperties();
        return pack;
    }

    public boolean has(String file) { return files.containsKey(file); }

    public byte[] bytes(String file) { return files.get(file.startsWith("/") ? file.substring(1) : file); }

    /**
     * The source of a program ("final", "gbuffers_terrain") for a dimension
     * folder ("world0"), {@code ext} ".vsh"/".fsh"; the dimension's own file
     * if it has one, else the pack's top-level one; null if neither exists.
     */
    String program(String dimension, String name, String ext) {
        String file = dimension + "/" + name + ext;
        if (!files.containsKey(file)) file = name + ext;
        if (!files.containsKey(file)) return null;
        StringBuilder out = new StringBuilder();
        include(file, out, 0);
        return addMacros(out.toString());
    }

    /**
     * Puts the standard macros after the #version line, and moves #extension lines up there too:
     * packs put them mid-file (inside #if), which NVIDIA allows but Mesa doesn't, as Iris does.
     */
    private String addMacros(String source) {
        StringBuilder defines = new StringBuilder();
        Matcher extension = Pattern.compile("^[ \\t]*#[ \\t]*extension[^\\n]*\\n", Pattern.MULTILINE).matcher(source);
        StringBuilder rest = new StringBuilder();
        while (extension.find()) {
            defines.append(extension.group().trim()).append('\n');
            extension.appendReplacement(rest, "\n");
        }
        extension.appendTail(rest);
        source = rest.toString();
        for (Map.Entry<String, String> e : standardMacros.entrySet())
            defines.append("#define ").append(e.getKey()).append(' ').append(e.getValue()).append('\n');
        Matcher version = Pattern.compile("^\\s*#version[^\\n]*\\n", Pattern.MULTILINE).matcher(source);
        if (!version.find()) return "#version 120\n" + defines + source;
        return source.substring(0, version.end()) + defines + source.substring(version.end());
    }

    private void include(String file, StringBuilder out, int depth) {
        byte[] data = files.get(file);
        if (data == null || depth > 32) {
            out.append("// missing include ").append(file).append('\n');
            return;
        }
        String dir = file.contains("/") ? file.substring(0, file.lastIndexOf('/') + 1) : "";
        for (String line : new String(data, StandardCharsets.UTF_8).split("\r?\n", -1)) {
            Matcher m = INCLUDE.matcher(line);
            if (m.matches()) {
                String target = m.group(1);
                include(normalize(target.startsWith("/") ? target.substring(1) : dir + target), out, depth + 1);
            } else {
                out.append(option(line)).append('\n');
            }
        }
    }

    private static String normalize(String path) {
        List<String> parts = new ArrayList<>();
        for (String part : path.split("/")) {
            if (part.isEmpty() || part.equals(".")) continue;
            if (part.equals("..")) { if (!parts.isEmpty()) parts.remove(parts.size() - 1); }
            else parts.add(part);
        }
        return String.join("/", parts);
    }

    /** A #define line with the player's value for that option, if they changed it. */
    private String option(String line) {
        if (options.isEmpty()) return line;
        if (line.contains("const")) {
            Matcher c = CONST_OPTION.matcher(line);
            if (c.matches() && options.containsKey(c.group(3)))
                return c.group(1) + "const " + c.group(2) + " " + c.group(3) + " = " + options.get(c.group(3)) + ";" + c.group(5);
        }
        if (!line.contains("#define")) return line;
        Matcher m = OPTION.matcher(line);
        if (!m.matches() || !options.containsKey(m.group(3))) return line;
        String value = options.get(m.group(3));
        if (m.group(4) == null || value.equals("true") || value.equals("false")) {
            // A switch: commented out is off.
            return (value.equals("false") ? "//" : "") + "#define " + m.group(3) + m.group(5);
        }
        return m.group(1) + "#define " + m.group(3) + " " + value + m.group(5);
    }

    /** The lines of a program the driver will compile, after the conditionals (for finding what it declares). */
    List<String> activeLines(String source) {
        return new Preprocessor(Map.of()).process(source, false);
    }

    private void readProperties() {
        // shaders.properties sees the pack's options as macros: the defines that a full program ends up with.
        String probe = program("world0", "final", ".fsh");
        if (probe == null) probe = program("world0", "composite", ".fsh");
        Preprocessor options = new Preprocessor(Map.of());
        if (probe != null) options.process(probe, false);
        macros.putAll(options.macros);
        byte[] data = files.get("shaders.properties");
        if (data != null) {
            List<String> lines = new Preprocessor(macros).process(new String(data, StandardCharsets.UTF_8), false);
            Preprocessor values = new Preprocessor(macros);
            for (Map.Entry<String, String> e : parse(lines).entrySet()) {
                String key = e.getKey(), value = e.getValue();
                String[] parts = key.split("\\.");
                if (parts.length == 3 && (parts[0].equals("uniform") || parts[0].equals("variable"))) {
                    try {
                        customUniforms.add(new CustomUniform(parts[1], parts[2], Expression.parse(value), parts[0].equals("uniform")));
                    } catch (RuntimeException ex) {
                        System.err.println("Shader pack " + name + ": skipping " + key + ": " + ex.getMessage());
                    }
                } else {
                    // Buffer and image sizes may be written as option names (size.buffer.colortex1 = REFLECTION_RES REFLECTION_RES).
                    properties.put(key, key.startsWith("size.buffer.") || key.startsWith("image.") ? values.expand(value) : value);
                }
            }
        }
        readIds(files.get("entity.properties"), "entity.", entities, macros);
        readIds(files.get("item.properties"), "item.", items, macros);
        data = files.get("block.properties");
        if (data != null) {
            List<String> lines = new Preprocessor(macros).process(new String(data, StandardCharsets.UTF_8), false);
            for (Map.Entry<String, String> e : parse(lines).entrySet()) {
                if (!e.getKey().startsWith("block.")) continue;
                int id;
                try { id = Integer.parseInt(e.getKey().substring(6)); } catch (NumberFormatException ex) { continue; }
                for (String entry : e.getValue().split("\\s+")) {
                    if (entry.isEmpty()) continue;
                    String[] parts = entry.split(":");
                    int first = parts[0].equals("minecraft") ? 1 : 0;
                    if (first >= parts.length) continue;
                    List<String[]> conditions = new ArrayList<>();
                    for (int i = first + 1; i < parts.length; i++) {
                        String[] kv = parts[i].split("=", 2);
                        if (kv.length == 2) conditions.add(new String[]{kv[0], kv[1]});
                    }
                    blocks.computeIfAbsent(parts[first], k -> new ArrayList<>()).add(new BlockRule(id, conditions.toArray(new String[0][])));
                }
            }
        }
    }

    /** {@code <prefix>N=name name...} lines into name to N (the first ID listing a name wins). */
    private static void readIds(byte[] data, String prefix, Map<String, Integer> out, Map<String, String> macros) {
        if (data == null) return;
        List<String> lines = new Preprocessor(macros).process(new String(data, StandardCharsets.UTF_8), false);
        for (Map.Entry<String, String> e : parse(lines).entrySet()) {
            if (!e.getKey().startsWith(prefix)) continue;
            int id;
            try { id = Integer.parseInt(e.getKey().substring(prefix.length())); } catch (NumberFormatException ex) { continue; }
            for (String name : e.getValue().split("\\s+")) if (!name.isEmpty()) out.putIfAbsent(key(name), id);
        }
    }

    /**
     * Names compared the way 1.8.9's and newer spellings meet: VillagerGolem, villager_golem and
     * minecraft:villager_golem are the same entity.
     */
    private static String key(String name) {
        int colon = name.indexOf(':');
        if (colon >= 0 && name.substring(0, colon).equals("minecraft")) name = name.substring(colon + 1);
        return name.replace("_", "").toLowerCase(Locale.ROOT);
    }

    /** The entity's ID from entity.properties (by its 1.8.9 save name, like Creeper), -1 if the pack names none. */
    public int entityId(String entity) {
        Integer id = entities.get(key(entity));
        if (id == null) id = entities.get(key(RENAMED_ENTITIES.getOrDefault(entity, entity)));
        return id == null ? -1 : id;
    }

    /** 1.8.9 save names that later versions spell differently, for packs that only list the new names. */
    private static final Map<String, String> RENAMED_ENTITIES = Map.ofEntries(
        Map.entry("LavaSlime", "magma_cube"), Map.entry("PigZombie", "zombie_pigman"), Map.entry("SnowMan", "snowman"),
        Map.entry("EntityHorse", "horse"), Map.entry("Ozelot", "ocelot"), Map.entry("MushroomCow", "mooshroom"),
        Map.entry("WitherBoss", "wither"), Map.entry("PrimedTnt", "tnt"), Map.entry("FallingSand", "falling_block"),
        Map.entry("ThrownPotion", "potion"), Map.entry("ThrownEnderpearl", "ender_pearl"), Map.entry("MinecartRideable", "minecart"),
        Map.entry("EnderCrystal", "end_crystal"), Map.entry("SmallFireball", "small_fireball"));

    /** The item's ID from item.properties, -1 if the pack names none. */
    public int itemId(String item) { return items.getOrDefault(key(item), -1); }

    /** key=value lines (later keys win), with Java .properties line continuations. */
    private static Map<String, String> parse(List<String> lines) {
        Map<String, String> out = new LinkedHashMap<>();
        StringBuilder joined = new StringBuilder();
        for (String line : lines) joined.append(line).append('\n');
        Properties p = new Properties() {
            // Keep file order: later threads of #if may set the same key, and the last one stands.
            @Override public synchronized Object put(Object key, Object value) { out.put((String) key, ((String) value).trim()); return null; }
        };
        try { p.load(new java.io.StringReader(joined.toString())); } catch (IOException ignored) {}
        return out;
    }

    /** The shader block ID (mc_Entity.x) for a block by its 1.8.9 name and a property lookup, 0 if the pack names none. */
    public int blockId(String block, java.util.function.Function<String, String> property) {
        List<BlockRule> rules = blocks.get(block);
        if (rules == null) return 0;
        for (BlockRule rule : rules) {
            boolean match = true;
            for (String[] condition : rule.conditions) {
                String value;
                try { value = property.apply(condition[0]); } catch (IllegalArgumentException e) { value = null; }
                // Properties 1.8.9 blocks don't have (waterlogged...) don't rule a block out.
                if (value != null && !List.of(condition[1].split(",")).contains(value.toLowerCase(Locale.ROOT))) { match = false; break; }
            }
            if (match) return rule.id;
        }
        return 0;
    }

    public String property(String key) { return properties.get(key); }

    public Map<String, String> properties() { return java.util.Collections.unmodifiableMap(properties); }

    // ---- Options (the pack's settings screens) ---------------------------------------------------

    /**
     * A setting the pack offers: a switch ({@code #define X} / {@code //#define X}) or a value
     * with its choices ({@code #define X 2 //[0 1 2]}, {@code const float x = 1.0; //[0.5 1.0]}).
     */
    public record Option(String name, String defaultValue, List<String> values, boolean toggle) {}

    /**
     * The options named on the pack's screens and sliders, in file order, with their defaults
     * from the sources (the player's values are in {@link #optionValues}).
     */
    public Map<String, Option> options() {
        java.util.Set<String> shown = new java.util.HashSet<>();
        for (Map.Entry<String, String> e : properties.entrySet())
            if (e.getKey().equals("screen") || e.getKey().startsWith("screen.") || e.getKey().equals("sliders")
                || e.getKey().startsWith("profile."))
                for (String token : e.getValue().split("\\s+")) shown.add(token.replaceAll("^!|=.*$", ""));
        Map<String, Option> found = new LinkedHashMap<>();
        List<String> names = new ArrayList<>(files.keySet());
        java.util.Collections.sort(names);
        for (String file : names) {
            if (!file.endsWith(".glsl") && !file.endsWith(".fsh") && !file.endsWith(".vsh") && !file.endsWith(".gsh")) continue;
            for (String line : new String(files.get(file), StandardCharsets.UTF_8).split("\r?\n")) {
                Matcher m = OPTION.matcher(line), c = CONST_OPTION.matcher(line);
                String name, value;
                boolean toggle;
                if (m.matches()) {
                    name = m.group(3);
                    toggle = m.group(4) == null;
                    value = toggle ? Boolean.toString(m.group(2) == null) : m.group(4);
                } else if (c.matches()) {
                    name = c.group(3);
                    toggle = false;
                    value = c.group(4).trim();
                } else continue;
                if (found.containsKey(name)) continue;
                Matcher v = VALUES.matcher(m.matches() ? m.group(5) : c.group(5));
                List<String> values = v.find() ? List.of(v.group(1).trim().split("\\s+")) : List.of();
                // Without screens, any value with a list of choices is an option; with them, what they show.
                if (shown.isEmpty() ? (toggle || values.isEmpty()) : !shown.contains(name)) continue;
                if (toggle) values = List.of("true", "false");
                else if (!values.contains(value)) { values = new ArrayList<>(values); values.add(0, value); }
                found.put(name, new Option(name, value, List.copyOf(values), toggle));
            }
        }
        return found;
    }

    /** The player's changed options (from {@code <pack>.txt}), by name. */
    public Map<String, String> optionValues() { return Map.copyOf(options); }

    /** Saves the player's options to {@code <pack>.txt}, leaving out those at their default. */
    public void saveOptions(Map<String, String> values) throws IOException {
        Map<String, Option> known = options();
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, String> e : new java.util.TreeMap<>(values).entrySet()) {
            Option o = known.get(e.getKey());
            if (o == null || o.defaultValue().equals(e.getValue())) continue;
            out.append(e.getKey()).append('=').append(e.getValue()).append('\n');
        }
        Path config = path.resolveSibling(name + ".txt");
        if (out.length() == 0) Files.deleteIfExists(config);
        else Files.writeString(config, out.toString(), StandardCharsets.UTF_8);
    }

    /** The pack's English names for options, screens, values and profiles (lang/en_US.lang). */
    public Map<String, String> lang() {
        Map<String, String> out = new HashMap<>();
        for (String file : files.keySet()) {
            if (!file.toLowerCase(Locale.ROOT).equals("lang/en_us.lang")) continue;
            for (String line : new String(files.get(file), StandardCharsets.UTF_8).split("\r?\n")) {
                int eq = line.indexOf('=');
                if (eq > 0 && !line.startsWith("#")) out.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
            }
        }
        return out;
    }
}
