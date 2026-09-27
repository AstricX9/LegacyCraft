package net.minecraft.world.biome;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.block.BlockRegistry;
import net.minecraft.block.BlockState;
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

/** Loads the 1.8.9 biomes from their generated per-biome definitions. */
public final class BiomeRegistry {
    private static final ResourceLocation INDEX = new ResourceLocation("legacycraft:indexes/biomes.json");
    private final Map<ResourceLocation, Biome> byName = new LinkedHashMap<>();
    private final Map<Integer, Biome> byId = new LinkedHashMap<>();

    public static BiomeRegistry load(IResourceManager resources, BlockRegistry blocks) {
        BiomeRegistry registry = new BiomeRegistry();
        try {
            JsonObject index;
            try (var reader = new InputStreamReader(resources.open(INDEX), StandardCharsets.UTF_8)) {
                index = JsonParser.parseReader(reader).getAsJsonObject();
            }
            for (JsonElement raw : index.getAsJsonArray("entries")) {
                JsonObject entry = raw.getAsJsonObject();
                JsonObject value;
                try (var reader = new InputStreamReader(resources.open(
                    new ResourceLocation(entry.get("definition").getAsString())), StandardCharsets.UTF_8)) {
                    value = JsonParser.parseReader(reader).getAsJsonObject();
                }
                ResourceLocation name = new ResourceLocation(value.get("name").getAsString());
                int id = value.get("id").getAsInt();
                if (id != entry.get("id").getAsInt() || !name.toString().equals(entry.get("name").getAsString()))
                    throw new IllegalStateException("Biome/index mismatch for " + name);
                Biome biome = new Biome(id, name,
                    value.get("temperature").getAsFloat(), value.get("rainfall").getAsFloat(),
                    value.get("depth").getAsDouble(), value.get("scale").getAsDouble(),
                    state(blocks, value, "topBlock", "minecraft:grass", "topMetadata"),
                    state(blocks, value, "fillerBlock", "minecraft:dirt", "fillerMetadata"),
                    flag(value, "snow", false), flag(value, "rain", true),
                    color(value, "grassColor"), color(value, "foliageColor"), color(value, "grassMix"),
                    value.has("waterColor") ? color(value, "waterColor") : 0xFFFFFF,
                    count(value, "treesPerChunk"), count(value, "grassPerChunk"), count(value, "flowersPerChunk"),
                    count(value, "cactiPerChunk"), count(value, "mushroomsPerChunk"));
                if (registry.byId.put(id, biome) != null) throw new IllegalStateException("Duplicate biome ID " + id);
                registry.byName.put(name, biome);
            }
            if (registry.byId.size() != index.get("count").getAsInt())
                throw new IllegalStateException("Biome index count mismatch");
            return registry;
        } catch (IOException error) {
            throw new IllegalStateException("Could not build the biome registry from resources", error);
        }
    }

    private static BlockState state(BlockRegistry blocks, JsonObject value, String key, String fallback, String metaKey) {
        String name = value.has(key) ? value.get(key).getAsString() : fallback;
        return BlockState.of(blocks.get(name), value.has(metaKey) ? value.get(metaKey).getAsInt() : 0);
    }
    private static boolean flag(JsonObject value, String key, boolean fallback) {
        return value.has(key) ? value.get(key).getAsBoolean() : fallback;
    }
    private static int color(JsonObject value, String key) {
        if (!value.has(key)) return -1;
        String text = value.get(key).getAsString();
        return Integer.parseInt(text.startsWith("#") ? text.substring(1) : text, 16);
    }
    private static int count(JsonObject value, String key) {
        return value.has(key) ? value.get(key).getAsInt() : 0;
    }

    public Biome get(ResourceLocation name) { return byName.get(name); }
    public Biome get(String name) {
        Biome biome = get(new ResourceLocation(name));
        if (biome == null) throw new IllegalArgumentException("Unknown biome " + name);
        return biome;
    }
    public Biome byId(int id) { return byId.get(id); }
    public int size() { return byId.size(); }
    public Collection<Biome> values() { return List.copyOf(new ArrayList<>(byId.values())); }
}
