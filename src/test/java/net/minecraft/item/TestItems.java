package net.minecraft.item;

import net.minecraft.util.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/** Items made without the resource pack, for tests: one instance per name. */
public final class TestItems {
    private static final Map<String, Item> ITEMS = new HashMap<>();

    private TestItems() {}

    public static Item item(String name) { return item(name, 64, 0); }

    public static Item item(String name, int maxStackSize, int maxDamage) {
        return ITEMS.computeIfAbsent(name + "/" + maxStackSize + "/" + maxDamage, key -> new Item(ITEMS.size(),
            new ResourceLocation("minecraft", name), maxStackSize, maxDamage, Item.UseAction.NONE, 0, 0,
            new ResourceLocation("minecraft", "item/" + name), null, 0));
    }

    /** The creative tab data from the source tree (Gradle runs tests in the project directory). */
    public static CreativeTabs creativeTabs() {
        try (var reader = java.nio.file.Files.newBufferedReader(
                java.nio.file.Path.of("src/main/resources/assets/legacycraft/creative_tabs.json"))) {
            return CreativeTabs.parse(com.google.gson.JsonParser.parseReader(reader).getAsJsonObject(), TestItems::item);
        } catch (java.io.IOException error) {
            throw new IllegalStateException(error);
        }
    }
}
