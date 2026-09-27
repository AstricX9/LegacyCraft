package net.minecraft.block.state;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * The state properties of one block and the codec between them and the 4-bit
 * legacy metadata (master context §11.4, §15.1), loaded from the block's own
 * generated states file (sourced from {@code data/mc189/block_states/<block>.json},
 * kept separate from the block's data definition).
 *
 * <p>Properties are either <i>stored</i> (packed into metadata by the ordered
 * bit fields) or <i>derived</i> — present in the property list but never
 * packed; the block class computes them from its neighbours in
 * {@link net.minecraft.block.Block#actualState} (fence connections, stair shape...).
 * Stored-only decoding leaves derived properties at their defaults.
 */
public final class BlockStateDefinition {
    public record MetaField(String property, int shift, int bits, List<String> map,
                            String fallback, Map<String, String> when) {
        boolean applies(Map<String, String> values) {
            for (var condition : when.entrySet())
                if (!condition.getValue().equals(values.get(condition.getKey()))) return false;
            return true;
        }
    }

    /** Blockstate-file selection: a file template with {@code {property}} placeholders, ignored properties, or a named custom mapper. */
    public record ModelMapping(String file, Set<String> ignored, String mapper) {}

    public static final BlockStateDefinition EMPTY = new BlockStateDefinition(
        new TreeMap<>(), Map.of(), List.of(), new ModelMapping(null, Set.of(), null));

    private final SortedMap<String, List<String>> properties;
    private final SortedMap<String, String> defaults;
    private final List<MetaField> fields;
    private final Set<String> stored;
    private final ModelMapping model;
    private final List<SortedMap<String, String>> decoded = new ArrayList<>(16);

    private BlockStateDefinition(SortedMap<String, List<String>> properties, Map<String, String> explicitDefaults,
                                 List<MetaField> fields, ModelMapping model) {
        this.properties = Collections.unmodifiableSortedMap(properties);
        SortedMap<String, String> defaults = new TreeMap<>();
        for (var entry : properties.entrySet())
            defaults.put(entry.getKey(), explicitDefaults.getOrDefault(entry.getKey(), entry.getValue().get(0)));
        this.defaults = Collections.unmodifiableSortedMap(defaults);
        this.fields = List.copyOf(fields);
        Set<String> stored = new LinkedHashSet<>();
        for (MetaField field : fields) stored.add(field.property());
        this.stored = Collections.unmodifiableSet(stored);
        this.model = model;
        for (int meta = 0; meta < 16; meta++) decoded.add(Collections.unmodifiableSortedMap(decodeUncached(meta)));
    }

    /** Parses a block's generated states file; {@code null} means a stateless block. */
    public static BlockStateDefinition parse(String blockName, JsonObject json) {
        if (json == null) return EMPTY;
        SortedMap<String, List<String>> properties = new TreeMap<>();
        if (json.has("properties")) {
            for (var entry : json.getAsJsonObject("properties").entrySet()) {
                List<String> values = new ArrayList<>();
                for (JsonElement value : entry.getValue().getAsJsonArray()) values.add(value.getAsString());
                if (values.isEmpty() || new LinkedHashSet<>(values).size() != values.size())
                    throw invalid(blockName, "property " + entry.getKey() + " needs distinct values");
                properties.put(entry.getKey(), List.copyOf(values));
            }
        }
        Map<String, String> defaults = new LinkedHashMap<>();
        if (json.has("defaults")) {
            for (var entry : json.getAsJsonObject("defaults").entrySet()) {
                requireValue(blockName, properties, entry.getKey(), entry.getValue().getAsString());
                defaults.put(entry.getKey(), entry.getValue().getAsString());
            }
        }
        List<MetaField> fields = new ArrayList<>();
        Set<String> decodedSoFar = new LinkedHashSet<>();
        int usedBits = 0;
        if (json.has("meta")) {
            for (JsonElement raw : json.getAsJsonArray("meta")) {
                JsonObject field = raw.getAsJsonObject();
                String property = field.get("property").getAsString();
                if (!properties.containsKey(property)) throw invalid(blockName, "meta field for unknown property " + property);
                int shift = field.has("shift") ? field.get("shift").getAsInt() : 0;
                int bits = field.get("bits").getAsInt();
                if (bits < 1 || shift < 0 || shift + bits > 4) throw invalid(blockName, "meta field " + property + " exceeds 4 bits");
                List<String> map = new ArrayList<>();
                if (field.has("map")) {
                    for (JsonElement value : field.getAsJsonArray("map")) {
                        String text = value.isJsonNull() ? null : value.getAsString();
                        if (text != null) requireValue(blockName, properties, property, text);
                        map.add(text);
                    }
                    if (map.size() > (1 << bits)) throw invalid(blockName, "map for " + property + " is larger than its bit field");
                } else {
                    map.addAll(properties.get(property));
                }
                String fallback = field.has("fallback") ? field.get("fallback").getAsString() : null;
                if (fallback != null) requireValue(blockName, properties, property, fallback);
                Map<String, String> when = new LinkedHashMap<>();
                if (field.has("when")) {
                    for (var condition : field.getAsJsonObject("when").entrySet()) {
                        if (!decodedSoFar.contains(condition.getKey()))
                            throw invalid(blockName, "condition on " + condition.getKey() + " must follow the field that decodes it");
                        requireValue(blockName, properties, condition.getKey(), condition.getValue().getAsString());
                        when.put(condition.getKey(), condition.getValue().getAsString());
                    }
                } else {
                    int mask = ((1 << bits) - 1) << shift;
                    if ((usedBits & mask) != 0) throw invalid(blockName, "unconditional meta fields overlap at " + property);
                    usedBits |= mask;
                }
                fields.add(new MetaField(property, shift, bits, Collections.unmodifiableList(map), fallback, Map.copyOf(when)));
                decodedSoFar.add(property);
            }
        }
        ModelMapping model = new ModelMapping(null, Set.of(), null);
        if (json.has("model")) {
            JsonObject mapping = json.getAsJsonObject("model");
            Set<String> ignored = new LinkedHashSet<>();
            if (mapping.has("ignore")) {
                for (JsonElement value : mapping.getAsJsonArray("ignore")) {
                    if (!properties.containsKey(value.getAsString())) throw invalid(blockName, "ignores unknown property " + value.getAsString());
                    ignored.add(value.getAsString());
                }
            }
            String file = mapping.has("file") ? mapping.get("file").getAsString() : null;
            if (file != null) {
                for (String placeholder : placeholders(file))
                    if (!properties.containsKey(placeholder)) throw invalid(blockName, "file template uses unknown property " + placeholder);
            }
            model = new ModelMapping(file, Set.copyOf(ignored), mapping.has("mapper") ? mapping.get("mapper").getAsString() : null);
        }
        return new BlockStateDefinition(properties, defaults, fields, model);
    }

    public SortedMap<String, List<String>> properties() { return properties; }
    public SortedMap<String, String> defaults() { return defaults; }
    public List<MetaField> fields() { return fields; }
    public boolean isStored(String property) { return stored.contains(property); }
    public ModelMapping model() { return model; }
    public boolean hasProperty(String property) { return properties.containsKey(property); }

    /** Stored property values for a metadata value; derived properties are at their defaults. */
    public SortedMap<String, String> decode(int metadata) { return decoded.get(metadata & 15); }

    public int encode(Map<String, String> values) {
        int metadata = 0;
        for (MetaField field : fields) {
            if (!field.applies(values)) continue;
            String value = values.getOrDefault(field.property(), defaults.get(field.property()));
            int raw = field.map().indexOf(value);
            if (raw < 0) throw new IllegalArgumentException("Property " + field.property() + "=" + value + " has no metadata encoding");
            metadata |= raw << field.shift();
        }
        return metadata;
    }

    public int defaultMetadata() { return encode(defaults); }

    private SortedMap<String, String> decodeUncached(int metadata) {
        SortedMap<String, String> values = new TreeMap<>(defaults);
        for (MetaField field : fields) {
            if (!field.applies(values)) continue;
            int raw = (metadata >>> field.shift()) & ((1 << field.bits()) - 1);
            String value = raw < field.map().size() ? field.map().get(raw) : null;
            if (value == null) value = field.fallback() != null ? field.fallback() : defaults.get(field.property());
            values.put(field.property(), value);
        }
        return values;
    }

    public static List<String> placeholders(String template) {
        List<String> names = new ArrayList<>();
        int start = template.indexOf('{');
        while (start >= 0) {
            int end = template.indexOf('}', start);
            if (end < 0) throw new IllegalArgumentException("Unclosed placeholder in " + template);
            names.add(template.substring(start + 1, end));
            start = template.indexOf('{', end);
        }
        return names;
    }

    private static void requireValue(String block, Map<String, List<String>> properties, String property, String value) {
        List<String> values = properties.get(property);
        if (values == null || !values.contains(value))
            throw invalid(block, property + "=" + value + " is not a declared value");
    }

    private static IllegalStateException invalid(String block, String message) {
        return new IllegalStateException("Invalid block states for " + block + ": " + message);
    }
}
