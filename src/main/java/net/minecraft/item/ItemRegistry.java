package net.minecraft.item;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRegistry;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Indexed registry for the per-item JSON catalog generated from the resource pack. */
public final class ItemRegistry {
    private static final ResourceLocation INDEX = new ResourceLocation("legacycraft:indexes/items.json");
    private final Map<ResourceLocation, Item> byName = new LinkedHashMap<>();
    private final List<Item> byId = new ArrayList<>();

    public static ItemRegistry load(IResourceManager resources, BlockRegistry blocks) {
        ItemRegistry registry = new ItemRegistry();
        try {
            JsonObject index;
            try (var reader = new InputStreamReader(resources.open(INDEX), StandardCharsets.UTF_8)) {
                index = JsonParser.parseReader(reader).getAsJsonObject();
            }
            Set<ResourceLocation> names = new HashSet<>();
            for (JsonElement rawEntry : index.getAsJsonArray("entries")) {
                JsonObject entry = rawEntry.getAsJsonObject();
                int id = entry.get("index").getAsInt();
                if (id != registry.byId.size())
                    throw new IllegalStateException("Non-contiguous item index at " + id);
                ResourceLocation source = new ResourceLocation(entry.get("definition").getAsString());
                JsonObject value;
                try (var reader = new InputStreamReader(resources.open(source), StandardCharsets.UTF_8)) {
                    value = JsonParser.parseReader(reader).getAsJsonObject();
                }
                ResourceLocation name = new ResourceLocation(value.get("name").getAsString());
                if (!names.add(name)) throw new IllegalStateException("Duplicate item " + name);
                if (id != value.get("index").getAsInt()
                    || !name.toString().equals(entry.get("name").getAsString()))
                    throw new IllegalStateException("Item/index mismatch for " + source);
                int stackSize = value.get("maxStackSize").getAsInt();
                int maxDamage = value.get("maxDamage").getAsInt();
                if (stackSize < 1 || stackSize > 64 || maxDamage < 0)
                    throw new IllegalStateException("Invalid item limits for " + name);
                Item.UseAction action = Item.UseAction.valueOf(value.get("useAction").getAsString().toUpperCase());
                ResourceLocation model = new ResourceLocation(value.get("model").getAsString());
                Block placed = value.has("placeBlock") && !value.get("placeBlock").isJsonNull()
                    ? blocks.get(new ResourceLocation(value.get("placeBlock").getAsString())) : null;
                int placedMeta = value.has("placeMeta") ? value.get("placeMeta").getAsInt() : 0;
                if (placed == null && value.has("placeBlock") && !value.get("placeBlock").isJsonNull())
                    throw new IllegalStateException("Item " + name + " places unknown block " + value.get("placeBlock"));
                if (placedMeta < 0 || placedMeta > 15) throw new IllegalStateException("Invalid placed metadata for " + name);
                Item item = new Item(id, name, stackSize, maxDamage, action,
                    value.get("food").getAsInt(), value.get("saturation").getAsFloat(), model, placed, placedMeta);
                registry.byName.put(name, item);
                registry.byId.add(item);
            }
            if (registry.size() != index.get("count").getAsInt())
                throw new IllegalStateException("Item index count mismatch");
            return registry;
        } catch (IOException error) {
            throw new IllegalStateException("Could not build the item registry from resources", error);
        }
    }

    public Item get(ResourceLocation name) { return byName.get(name); }
    public Item get(String name) {
        Item item = get(new ResourceLocation(name));
        if (item == null) throw new IllegalArgumentException("Unknown item " + name);
        return item;
    }
    public Item byId(int id) { return id >= 0 && id < byId.size() ? byId.get(id) : null; }
    public int size() { return byId.size(); }
    public Collection<Item> values() { return List.copyOf(byId); }
}
