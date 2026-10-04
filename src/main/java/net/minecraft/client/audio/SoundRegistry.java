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

    /**
     * Reads sounds.json from the base pack and then from every resource pack
     * above it (SoundHandler.onResourceManagerReload): a pack's event adds its
     * sounds to the existing event unless it says {@code "replace": true}.
     */
    public static SoundRegistry load(IResourceManager resources) {
        SoundRegistry registry = new SoundRegistry();
        List<java.io.InputStream> files;
        try {
            files = resources.openAll(SOUNDS);
        } catch (IOException error) {
            throw new IllegalStateException("Could not load sounds.json", error);
        }
        if (files.isEmpty()) throw new IllegalStateException("Could not load sounds.json: missing");
        for (int i = 0; i < files.size(); i++) {
            try (var reader = new InputStreamReader(files.get(i), StandardCharsets.UTF_8)) {
                registry.read(JsonParser.parseReader(reader).getAsJsonObject());
            } catch (IOException | RuntimeException error) {
                if (i == 0) throw new IllegalStateException("Could not load sounds.json", error);
                System.err.println("Invalid sounds.json in a resource pack: " + error.getMessage());
            }
        }
        return registry;
    }

    private void read(JsonObject root) {
        SoundRegistry registry = this;
        {
            root.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                ResourceLocation name = new ResourceLocation("minecraft", entry.getKey());
                JsonObject value = entry.getValue().getAsJsonObject();
                SoundEvent existing = registry.events.get(name);
                boolean replace = value.has("replace") && value.get("replace").getAsBoolean();
                String category = value.has("category") ? value.get("category").getAsString()
                    : existing != null ? existing.category() : "master";
                List<SoundEvent.Variant> variants = new ArrayList<>();
                if (existing != null && !replace) variants.addAll(existing.variants());
                for (JsonElement raw : value.getAsJsonArray("sounds")) {
                    if (raw.isJsonPrimitive()) variants.add(variant(raw.getAsString(), 1, 1, 1, false));
                    else {
                        JsonObject object = raw.getAsJsonObject();
                        variants.add(variant(object.get("name").getAsString(),
                            number(object, "volume", 1), number(object, "pitch", 1),
                            (int) number(object, "weight", 1), bool(object, "stream", false)));
                    }
                }
                if (!variants.isEmpty()) registry.events.put(name, new SoundEvent(name, category, variants));
            });
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
