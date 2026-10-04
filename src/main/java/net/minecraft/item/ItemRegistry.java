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
    private static final ResourceLocation CREATIVE_TABS = new ResourceLocation("legacycraft:creative_tabs.json");
    private final Map<ResourceLocation, Item> byName = new LinkedHashMap<>();
    private final List<Item> byId = new ArrayList<>();
    /** Vanilla "id@data" (data 0 for damageable items) to the item standing for it. */
    private final Map<String, Item> byVanilla = new java.util.HashMap<>();

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
            JsonObject tabs = null;
            if (resources.exists(CREATIVE_TABS))
                try (var reader = new InputStreamReader(resources.open(CREATIVE_TABS), StandardCharsets.UTF_8)) {
                    tabs = JsonParser.parseReader(reader).getAsJsonObject();
                }
            VanillaItems.assign(registry, tabs, blocks);
            for (Item item : registry.byId) registry.byVanilla.putIfAbsent(key(item.vanillaId(), item.vanillaData()), item);
            VanillaItems.addVariants(registry);
            return registry;
        } catch (IOException error) {
            throw new IllegalStateException("Could not build the item registry from resources", error);
        }
    }

    public Item get(ResourceLocation name) { return byName.get(name); }

    private static String key(ResourceLocation id, int data) { return id + "@" + data; }

    /** Adds a variant of {@code base} (its model) under a new name, standing for the base's vanilla ID with {@code data}. */
    Item addVariant(Item base, String path, Item.Kind kind, int data) {
        ResourceLocation name = new ResourceLocation(base.name().namespace(), path);
        Item existing = byName.get(name);
        if (existing != null) return existing;
        Item.UseAction action = base.useAction();
        int stackSize = base.maxStackSize();
        Item item = base.variant(byId.size(), name, kind, data, action, stackSize);
        byName.put(name, item);
        byId.add(item);
        byVanilla.putIfAbsent(key(item.vanillaId(), data), item);
        return item;
    }

    /**
     * The potion item for a data value (ItemPotion with damage): water and
     * splash water are the bottle models, anything else a variant drawn with
     * the drinkable or splash bottle, made the first time it is asked for.
     */
    public Item potion(int data) {
        data &= 0xFFFF;
        Item known = byVanilla.get(key(new ResourceLocation("minecraft:potion"), data));
        if (known != null) return known;
        Item base = get(new ResourceLocation(Potions.isSplash(data) ? "minecraft:bottle_splash" : "minecraft:bottle_drinkable"));
        if (base == null) return null;
        Item item = addVariant(base, "potion_" + data, Item.Kind.POTION, data);
        return item;
    }

    /**
     * The item for a vanilla ID and data value ("minecraft:dye", 1), or
     * null. Damageable items match any data value (their durability), and
     * any potion or known spawn egg data value is made on demand.
     */
    public Item fromVanilla(ResourceLocation id, int data) {
        Item exact = byVanilla.get(key(id, data));
        if (exact != null) return exact;
        Item plain = byVanilla.get(key(id, 0));
        if (plain != null && plain.isDamageable()) return plain;
        if (id.path().equals("potion") && id.namespace().equals("minecraft")) return potion(data);
        return null;
    }

    /**
     * An item as saved or typed: a vanilla ID with its data value first, then
     * a LegacyCraft item name (older saves, model names like "dye_red").
     */
    public Item resolve(String name, int data) {
        ResourceLocation id;
        try {
            id = new ResourceLocation(name.contains(":") ? name : "minecraft:" + name);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
        Item vanilla = fromVanilla(id, data);
        if (vanilla != null) return vanilla;
        return byName.get(id);
    }
    public Item get(String name) {
        Item item = get(new ResourceLocation(name));
        if (item == null) throw new IllegalArgumentException("Unknown item " + name);
        return item;
    }
    public Item byId(int id) { return id >= 0 && id < byId.size() ? byId.get(id) : null; }
    public int size() { return byId.size(); }
    public Collection<Item> values() { return List.copyOf(byId); }
}
