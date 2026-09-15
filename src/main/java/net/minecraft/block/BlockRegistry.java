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
import java.util.HashSet;
import java.util.Set;

/** Loads individually-authored block definitions through a generated, stable ID index. */
public final class BlockRegistry {
    private static final ResourceLocation INDEX = new ResourceLocation("legacycraft:indexes/blocks.json");
    private final Map<ResourceLocation, Block> byName = new LinkedHashMap<>();
    private final List<Block> byId = new ArrayList<>();

    public static BlockRegistry load(IResourceManager resources) {
        BlockRegistry registry = new BlockRegistry();
        try {
            JsonObject index;
            try (var reader = new InputStreamReader(resources.open(INDEX), StandardCharsets.UTF_8)) {
                index = JsonParser.parseReader(reader).getAsJsonObject();
            }
            int expectedCount = index.get("count").getAsInt();
            Set<ResourceLocation> names = new HashSet<>();
            for (JsonElement rawEntry : index.getAsJsonArray("entries")) {
                JsonObject entry = rawEntry.getAsJsonObject();
                int id = entry.get("index").getAsInt();
                if (id != registry.byId.size())
                    throw new IllegalStateException("Non-contiguous block index: expected "
                        + registry.byId.size() + " but found " + id);
                ResourceLocation definition = new ResourceLocation(entry.get("definition").getAsString());
                JsonObject value;
                try (var reader = new InputStreamReader(resources.open(definition), StandardCharsets.UTF_8)) {
                    value = JsonParser.parseReader(reader).getAsJsonObject();
                }
                ResourceLocation name = new ResourceLocation(value.get("name").getAsString());
                if (!name.toString().equals(entry.get("name").getAsString()))
                    throw new IllegalStateException("Definition/index name mismatch for " + definition);
                if (value.get("index").getAsInt() != id)
                    throw new IllegalStateException("Definition/index ID mismatch for " + name);
                if (!names.add(name)) throw new IllegalStateException("Duplicate block " + name);
                registry.register(id, name, value);
            }
            if (registry.size() != expectedCount)
                throw new IllegalStateException("Block index declared " + expectedCount
                    + " entries but loaded " + registry.size());
            if (registry.byId.isEmpty() || !registry.byId.get(0).name().toString().equals("minecraft:air"))
                throw new IllegalStateException("Block index 0 must be minecraft:air");
            return registry;
        } catch (IOException error) {
            throw new IllegalStateException("Could not build the block registry from resources", error);
        }
    }

    private void register(int id, ResourceLocation name, JsonObject value) {
        String material = stringValue(value, "material", "rock");
        boolean solid = booleanValue(value, "solid", true);
        boolean opaque = booleanValue(value, "opaque", true);
        boolean translucent = booleanValue(value, "translucent", false);
        float hardness = floatValue(value, "hardness", 1.5f);
        Block.RenderType renderType = Block.RenderType.valueOf(
            stringValue(value, "renderType", "model").toUpperCase());
        int tint = parseColor(stringValue(value, "tintColor", "#FFFFFF"));
        int lightLevel = intValue(value, "lightLevel", 0);
        if (lightLevel < 0 || lightLevel > 15)
            throw new IllegalStateException("Invalid light level for " + name + ": " + lightLevel);
        String model = stringValue(value, "model", null);
        ResourceLocation modelOverride = model == null ? null : new ResourceLocation(model);
        Block block = new Block(id, name, material, solid, opaque, translucent,
            hardness, renderType, tint, lightLevel, modelOverride);
        byName.put(name, block);
        byId.add(block);
    }

    private static String stringValue(JsonObject object, String key, String fallback) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? fallback : value.getAsString();
    }
    private static boolean booleanValue(JsonObject object, String key, boolean fallback) {
        JsonElement value = object.get(key);
        return value == null ? fallback : value.getAsBoolean();
    }
    private static float floatValue(JsonObject object, String key, float fallback) {
        JsonElement value = object.get(key);
        return value == null ? fallback : value.getAsFloat();
    }
    private static int intValue(JsonObject object, String key, int fallback) {
        JsonElement value = object.get(key);
        return value == null ? fallback : value.getAsInt();
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
