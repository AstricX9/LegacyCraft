package net.minecraft.block;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** Discovers the supplied blockstate pack and assigns stable alphabetical runtime IDs. */
public final class BlockRegistry {
    private static final ResourceLocation PROPERTIES = new ResourceLocation("legacycraft:block_properties.json");
    private final Map<ResourceLocation, Block> byName = new LinkedHashMap<>();
    private final List<Block> byId = new ArrayList<>();

    public static BlockRegistry load(IResourceManager resources) {
        BlockRegistry registry = new BlockRegistry();
        try {
            JsonObject root;
            try (var reader = new InputStreamReader(resources.open(PROPERTIES), StandardCharsets.UTF_8)) {
                root = JsonParser.parseReader(reader).getAsJsonObject();
            }
            JsonObject defaults = root.getAsJsonObject("defaults");
            JsonObject overrides = root.getAsJsonObject("blocks");
            TreeSet<ResourceLocation> names = new TreeSet<>((a,b) -> a.toString().compareTo(b.toString()));
            for (ResourceLocation file : resources.list(new ResourceLocation("minecraft:blockstates"), ".json")) {
                String path = file.path().substring("blockstates/".length());
                names.add(new ResourceLocation(file.namespace(), path.substring(0, path.length() - 5)));
            }
            for (String explicit : overrides.keySet()) names.add(new ResourceLocation(explicit));
            names.remove(new ResourceLocation("minecraft:air"));
            registry.register(new ResourceLocation("minecraft:air"), defaults,
                overrides.getAsJsonObject("minecraft:air"));
            for (ResourceLocation name : names) {
                JsonObject override = overrides.has(name.toString()) ? overrides.getAsJsonObject(name.toString()) : null;
                registry.register(name, defaults, override);
            }
            return registry;
        } catch (IOException error) {
            throw new IllegalStateException("Could not build the block registry from resources", error);
        }
    }

    private void register(ResourceLocation name, JsonObject defaults, JsonObject override) {
        String material = stringValue(defaults, override, "material", "rock");
        boolean solid = booleanValue(defaults, override, "solid", true);
        boolean opaque = booleanValue(defaults, override, "opaque", true);
        boolean translucent = booleanValue(defaults, override, "translucent", false);
        float hardness = floatValue(defaults, override, "hardness", 1.5f);
        Block.RenderType renderType = Block.RenderType.valueOf(
            stringValue(defaults, override, "renderType", "model").toUpperCase());
        int tint = parseColor(stringValue(defaults, override, "tintColor", "#FFFFFF"));
        String model = stringValue(defaults, override, "model", null);
        ResourceLocation modelOverride = model == null ? null : new ResourceLocation(model);
        Block block = new Block(byId.size(), name, material, solid, opaque, translucent,
            hardness, renderType, tint, modelOverride);
        byName.put(name, block);
        byId.add(block);
    }

    private static JsonElement value(JsonObject defaults, JsonObject override, String key) {
        return override != null && override.has(key) ? override.get(key) : defaults.get(key);
    }
    private static String stringValue(JsonObject defaults, JsonObject override, String key, String fallback) {
        JsonElement value = value(defaults, override, key);
        return value == null || value.isJsonNull() ? fallback : value.getAsString();
    }
    private static boolean booleanValue(JsonObject defaults, JsonObject override, String key, boolean fallback) {
        JsonElement value = value(defaults, override, key);
        return value == null ? fallback : value.getAsBoolean();
    }
    private static float floatValue(JsonObject defaults, JsonObject override, String key, float fallback) {
        JsonElement value = value(defaults, override, key);
        return value == null ? fallback : value.getAsFloat();
    }
    private static int parseColor(String color) {
        return Integer.parseInt(color.startsWith("#") ? color.substring(1) : color, 16) & 0xffffff;
    }

    public Block get(ResourceLocation name) { return byName.get(name); }
    public Block get(String name) {
        Block block = get(new ResourceLocation(name));
        if (block == null) throw new IllegalArgumentException("Unknown block " + name);
        return block;
    }
    public Block byId(int id) { return id >= 0 && id < byId.size() ? byId.get(id) : byId.get(0); }
    public Block air() { return byId.get(0); }
    public int size() { return byId.size(); }
    public Collection<Block> values() { return List.copyOf(byId); }
}
