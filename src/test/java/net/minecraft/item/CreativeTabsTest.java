package net.minecraft.item;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CreativeTabsTest {
    private final CreativeTabs tabs = TestItems.creativeTabs();

    @Test
    void twelveTabsInVanillaOrder() {
        assertEquals(12, tabs.tabs().size());
        List<String> labels = tabs.tabs().stream().map(CreativeTabs.Tab::label).toList();
        assertEquals(List.of("buildingBlocks", "decorations", "redstone", "transportation", "misc", "search",
            "food", "tools", "combat", "brewing", "materials", "inventory"), labels);
        for (CreativeTabs.Tab tab : tabs.tabs()) {
            assertEquals(tab.index() < 6, tab.isTopRow());
            assertNotNull(tab.icon(), tab.label() + " has an icon");
        }
        assertEquals("item_search", tabs.tab(CreativeTabs.SEARCH).background());
        assertEquals("inventory", tabs.tab(CreativeTabs.INVENTORY).background());
        assertTrue(tabs.tab(CreativeTabs.INVENTORY).entries().isEmpty());
    }

    @Test
    void eachTabListsInIdOrderWithoutDuplicates() {
        Set<Item> seen = new HashSet<>();
        int total = 0;
        for (CreativeTabs.Tab tab : tabs.tabs()) {
            if (tab.index() == CreativeTabs.SEARCH) continue;
            CreativeTabs.Entry previous = null;
            for (CreativeTabs.Entry entry : tab.entries()) {
                assertTrue(seen.add(entry.item()), entry.item() + " is listed once");
                assertTrue(entry.nameKey().endsWith(".name"), entry.nameKey());
                if (previous != null)
                    assertTrue(previous.id() < entry.id() || previous.id() == entry.id() && previous.meta() < entry.meta(),
                        tab.label() + ": " + previous.item() + " before " + entry.item());
                previous = entry;
                total++;
            }
        }
        assertEquals(total, tabs.tab(CreativeTabs.SEARCH).entries().size(), "search lists every item");
    }

    @Test
    void namesSubtypesAndTabsByItem() {
        Item granite = TestItems.item("granite");
        assertEquals("tile.stone.granite.name", tabs.nameKey(granite));
        assertTrue(tabs.hasSubtypes(granite));
        assertEquals(1, tabs.entry(granite).id());
        assertEquals("buildingBlocks", tabs.tabOf(granite).label());
        assertNull(tabs.nameKey(TestItems.item("not_an_item")));
    }

    @Test
    void searchFiltersCaseInsensitively() {
        List<CreativeTabs.Entry> all = tabs.tab(CreativeTabs.SEARCH).entries();
        List<CreativeTabs.Entry> found = CreativeTabs.filter(all, "GRAN", entry -> entry.item().name().path());
        assertFalse(found.isEmpty());
        for (CreativeTabs.Entry entry : found) assertTrue(entry.item().name().path().contains("gran"));
        assertSame(all, CreativeTabs.filter(all, "", entry -> ""));
    }
}
