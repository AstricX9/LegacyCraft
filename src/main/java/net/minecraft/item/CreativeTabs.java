package net.minecraft.item;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The twelve 1.8.9 creative inventory tabs and what each lists, loaded from
 * {@code assets/legacycraft/creative_tabs.json} (HYPOTHESIS data: membership,
 * order and legacy IDs from memory of the vanilla client; see that file).
 * Also the source of item display names: each entry carries the vanilla
 * translation key for its item.
 */
public final class CreativeTabs {
    public static final int SEARCH = 5, INVENTORY = 11;
    private static final ResourceLocation DATA = new ResourceLocation("legacycraft:creative_tabs.json");

    /**
     * One listed item: the LegacyCraft item, its vanilla ID/data value (for
     * ordering) and its name key, and for an enchanted book the stack to hand
     * out (null for a plain item).
     */
    public record Entry(Item item, int id, int meta, String nameKey, ItemStack stack) {
        public Entry(Item item, int id, int meta, String nameKey) { this(item, id, meta, nameKey, null); }

        /** What the creative inventory gives for this entry: one of the item, or a copy of its stack. */
        public ItemStack newStack() { return stack != null ? stack.copy() : new ItemStack(item, 1); }
    }

    public static final int TOOLS = 7, COMBAT = 8;

    /** A tab: its index (0-5 top row, 6-11 bottom row), label key suffix, icon item and background texture name. */
    public record Tab(int index, String label, Item icon, String background, List<Entry> entries) {
        public boolean isTopRow() { return index < 6; }
        public int column() { return index % 6; }
        public String translationKey() { return "itemGroup." + label; }
        public boolean hasScrollbar() { return index != INVENTORY; }
        public boolean drawsTitle() { return index != INVENTORY; }
    }

    private final List<Tab> tabs;
    private final Map<Item, Entry> byItem = new HashMap<>();
    private final Map<Item, Tab> tabOf = new HashMap<>();
    private final List<String> missing = new ArrayList<>();
    private final java.util.Set<Integer> idsWithSubtypes = new java.util.HashSet<>();

    private CreativeTabs(List<Tab> tabs) { this.tabs = tabs; }

    public static CreativeTabs load(IResourceManager resources, ItemRegistry items) {
        JsonObject root;
        try (var reader = new InputStreamReader(resources.open(DATA), StandardCharsets.UTF_8)) {
            root = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException error) {
            throw new IllegalStateException("Could not read " + DATA, error);
        }
        return parse(root, name -> items.get(new ResourceLocation("minecraft", name)));
    }

    /** Builds the tabs from the JSON document, resolving item names with {@code lookup} (null when unknown). */
    public static CreativeTabs parse(JsonObject root, java.util.function.Function<String, Item> lookup) {
        List<Tab> tabs = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        List<Entry> everything = new ArrayList<>();
        for (JsonElement rawTab : root.getAsJsonArray("tabs")) {
            JsonObject tab = rawTab.getAsJsonObject();
            List<Entry> entries = new ArrayList<>();
            for (JsonElement rawItem : tab.getAsJsonArray("items")) {
                JsonObject object = rawItem.getAsJsonObject();
                String name = object.get("item").getAsString();
                Item item = lookup.apply(name);
                if (item == null) { missing.add(name); continue; }
                entries.add(new Entry(item, object.get("id").getAsInt(), object.get("meta").getAsInt(), object.get("key").getAsString()));
            }
            everything.addAll(entries);
            int index = tab.get("index").getAsInt();
            Item book = lookup.apply("enchanted_book");
            // CreativeTabs.addEnchantmentBooksToList: tools and combat end with a book of each of their enchantments at its top level.
            if (book != null && (index == TOOLS || index == COMBAT)) {
                for (net.minecraft.enchantment.Enchantment enchantment : net.minecraft.enchantment.Enchantment.ALL) {
                    var type = enchantment.type;
                    boolean tools = type == net.minecraft.enchantment.Enchantment.Type.DIGGER
                        || type == net.minecraft.enchantment.Enchantment.Type.FISHING_ROD
                        || type == net.minecraft.enchantment.Enchantment.Type.BREAKABLE;
                    if (tools == (index == TOOLS)) entries.add(bookEntry(book, enchantment, enchantment.maxLevel));
                }
            }
            tabs.add(new Tab(tab.get("index").getAsInt(), tab.get("label").getAsString(),
                lookup.apply(tab.get("icon").getAsString()), tab.get("background").getAsString(), entries));
        }
        everything.removeIf(entry -> entry.stack() != null);
        everything.sort(Comparator.comparingInt(Entry::id).thenComparingInt(Entry::meta));
        // The search tab ends with every level of every enchantment on a book (ItemEnchantedBook.getAll).
        Item book = lookup.apply("enchanted_book");
        if (book != null)
            for (net.minecraft.enchantment.Enchantment enchantment : net.minecraft.enchantment.Enchantment.ALL)
                for (int level = enchantment.minLevel(); level <= enchantment.maxLevel; level++)
                    everything.add(bookEntry(book, enchantment, level));
        Tab search = tabs.get(SEARCH);
        tabs.set(SEARCH, new Tab(search.index(), search.label(), search.icon(), search.background(), List.copyOf(everything)));
        CreativeTabs result = new CreativeTabs(List.copyOf(tabs));
        result.missing.addAll(missing);
        for (Tab tab : tabs) {
            if (tab.index() == SEARCH) continue;
            for (Entry entry : tab.entries()) {
                result.byItem.putIfAbsent(entry.item(), entry);
                result.tabOf.putIfAbsent(entry.item(), tab);
                if (entry.meta() != 0) result.idsWithSubtypes.add(entry.id());
            }
        }
        if (!missing.isEmpty()) System.err.println("Creative tabs list items the resource pack lacks: " + missing);
        return result;
    }

    private static Entry bookEntry(Item book, net.minecraft.enchantment.Enchantment enchantment, int level) {
        ItemStack stack = new ItemStack(book);
        stack.addStoredEnchantment(enchantment.id, level);
        return new Entry(book, 403, 0, "item.enchantedBook.name", stack);
    }

    public List<Tab> tabs() { return tabs; }
    public Tab tab(int index) { return tabs.get(index); }
    public List<String> missingItems() { return missing; }

    /** The tab an item is listed under (not Search), or null. */
    public Tab tabOf(Item item) { return tabOf.get(item); }

    /** The listing of an item (legacy ID, data value, name key), or null when it is not listed. */
    public Entry entry(Item item) { return byItem.get(item); }

    /** Whether the item's vanilla ID has data-value variants (vanilla getHasSubtypes, approximated from the tab data). */
    public boolean hasSubtypes(Item item) {
        Entry entry = byItem.get(item);
        return entry != null && idsWithSubtypes.contains(entry.id());
    }

    /** The vanilla translation key for an item's name, or null when it is not listed. */
    public String nameKey(Item item) {
        Entry entry = byItem.get(item);
        return entry == null ? null : entry.nameKey();
    }

    /** Search-tab filtering: case-insensitive substring of the display name, as 1.8.9 does. */
    public static List<Entry> filter(List<Entry> entries, String query, java.util.function.Function<Entry, String> displayName) {
        String needle = query.toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) return entries;
        List<Entry> result = new ArrayList<>();
        for (Entry entry : entries)
            if (displayName.apply(entry).toLowerCase(Locale.ROOT).contains(needle)) result.add(entry);
        return result;
    }
}
