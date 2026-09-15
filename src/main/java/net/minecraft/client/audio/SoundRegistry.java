package net.minecraft.client.audio;

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

/** Discovers legacy sound events and their playback metadata from the bundled pack. */
public final class SoundRegistry {
    private static final ResourceLocation SOUNDS = new ResourceLocation("minecraft:sounds.json");
    private final Map<ResourceLocation, SoundEvent> events = new LinkedHashMap<>();

    public static SoundRegistry load(IResourceManager resources) {
        SoundRegistry registry = new SoundRegistry();
        try (var reader = new InputStreamReader(resources.open(SOUNDS), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            root.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                ResourceLocation name = new ResourceLocation("minecraft", entry.getKey());
                JsonObject value = entry.getValue().getAsJsonObject();
                String category = value.has("category") ? value.get("category").getAsString() : "master";
                List<SoundEvent.Variant> variants = new ArrayList<>();
                for (JsonElement raw : value.getAsJsonArray("sounds")) {
                    if (raw.isJsonPrimitive()) variants.add(variant(raw.getAsString(), 1, 1, 1, false));
                    else {
                        JsonObject object = raw.getAsJsonObject();
                        variants.add(variant(object.get("name").getAsString(),
                            number(object, "volume", 1), number(object, "pitch", 1),
                            (int) number(object, "weight", 1), bool(object, "stream", false)));
                    }
                }
                registry.events.put(name, new SoundEvent(name, category, variants));
            });
            return registry;
        } catch (IOException error) {
            throw new IllegalStateException("Could not load sounds.json", error);
        }
    }
    private static SoundEvent.Variant variant(String name, float volume, float pitch, int weight, boolean stream) {
        ResourceLocation raw = new ResourceLocation(name);
        return new SoundEvent.Variant(new ResourceLocation(raw.namespace(), "sounds/" + raw.path() + ".ogg"),
            volume, pitch, weight, stream);
    }
    private static float number(JsonObject object, String key, float fallback) {
        return object.has(key) ? object.get(key).getAsFloat() : fallback;
    }
    private static boolean bool(JsonObject object, String key, boolean fallback) {
        return object.has(key) ? object.get(key).getAsBoolean() : fallback;
    }
    public SoundEvent get(ResourceLocation name) { return events.get(name); }
    public SoundEvent get(String name) { return get(new ResourceLocation(name)); }
    public int size() { return events.size(); }
    public Collection<SoundEvent> values() { return List.copyOf(events.values()); }
}
