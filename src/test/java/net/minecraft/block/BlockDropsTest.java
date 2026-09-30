package net.minecraft.block;

import com.google.gson.JsonParser;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.TestItems;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Silk Touch and Fortune on block drops (HYPOTHESIS: recalled 1.8.9 canSilkHarvest and quantityDroppedWithBonus). */
final class BlockDropsTest {
    private static final Item PICKAXE = TestItems.item("diamond_pickaxe", 1, 1561), SHOVEL = TestItems.item("diamond_shovel", 1, 1561);

    private static final BlockDrops DROPS = BlockDrops.parse(JsonParser.parseString("""
        {"blocks": {
          "coal_ore": [{"item": "coal"}],
          "lapis_ore": [{"item": "dye_blue", "count": [4, 8]}],
          "redstone_ore": [{"item": "redstone", "count": [4, 5]}],
          "lit_redstone_ore": [{"item": "redstone", "count": [4, 5]}],
          "glowstone": [{"item": "glowstone_dust", "count": [2, 4]}],
          "glass": [],
          "glass_pane": [],
          "stone": [{"item": "cobblestone", "meta": [0]}],
          "double_stone_slab": [{"block": "stone_slab", "count": [2, 2]}]
        }}""").getAsJsonObject(), new BlockDrops.Items() {
        @Override public Item byName(String name) { return TestItems.item(name); }
        @Override public Item placing(String block, int metadata) { return TestItems.item(block + (metadata == 0 ? "" : "#" + metadata)); }
    });

    private static List<ItemStack> drops(String block, String material, Item tool, int fortune, boolean silk, boolean fullCube, Random random) {
        return DROPS.drops(block, material, 0, tool, fortune, silk, fullCube, random);
    }

    private static String only(List<ItemStack> drops) {
        assertEquals(1, drops.size(), drops.toString());
        return drops.get(0).item().name().path() + " x" + drops.get(0).count();
    }

    @Test
    void silkTouchTakesFullBlocksWhole() {
        Random random = new Random(1);
        assertEquals("coal_ore x1", only(drops("coal_ore", "rock", PICKAXE, 0, true, true, random)));
        assertEquals("stone x1", only(drops("stone", "rock", PICKAXE, 0, true, true, random)));
        assertEquals("glass x1", only(drops("glass", "glass", null, 0, true, true, random)));
        assertTrue(drops("glass", "glass", null, 0, false, true, random).isEmpty(), "glass breaks without it");
    }

    @Test
    void silkTouchHasItsExceptions() {
        Random random = new Random(2);
        assertEquals("redstone_ore x1", only(drops("lit_redstone_ore", "rock", PICKAXE, 0, true, true, random)));
        assertEquals("glass_pane x1", only(drops("glass_pane", "glass", null, 0, true, false, random)), "panes aren't cubes but take it");
        assertEquals("stone_slab x2", only(drops("double_stone_slab", "rock", PICKAXE, 0, true, true, random)), "double slabs never");
        assertTrue(drops("stone", "rock", null, 0, true, true, random).isEmpty(), "the tool must still harvest it");
        assertFalse(BlockDrops.canSilkHarvest("furnace", true), "tile entities never");
        assertTrue(BlockDrops.canSilkHarvest("ender_chest", false));
    }

    @Test
    void fortuneMultipliesOres() {
        Random random = new Random(3);
        int total = 0, most = 0;
        for (int i = 0; i < 2000; i++) {
            List<ItemStack> drops = drops("coal_ore", "rock", PICKAXE, 3, false, true, random);
            int count = drops.get(0).count();
            assertTrue(count >= 1 && count <= 4, "up to (Fortune + 1) times: " + count);
            total += count;
            most = Math.max(most, count);
        }
        assertEquals(4, most);
        assertEquals(2.2, total / 2000.0, 0.15, "1, 1, 2, 3 or 4 equally");
    }

    @Test
    void fortuneAddsToRedstoneAndCapsGlowstone() {
        Random random = new Random(4);
        int most = 0;
        for (int i = 0; i < 500; i++) {
            most = Math.max(most, drops("redstone_ore", "rock", TestItems.item("iron_pickaxe", 1, 250), 3, false, true, random).get(0).count());
            int glowstone = drops("glowstone", "glass", null, 3, false, true, random).get(0).count();
            assertTrue(glowstone >= 2 && glowstone <= 4, "clamped to 4: " + glowstone);
        }
        assertEquals(8, most, "4-5 plus 0-3");
    }

    @Test
    void fortuneThreeAlwaysGivesFlintFromGravel() {
        Random random = new Random(5);
        for (int i = 0; i < 50; i++) assertEquals("flint x1", only(drops("gravel", "sand", SHOVEL, 3, false, true, random)));
    }

    @Test
    void withoutEnchantmentsNothingChanges() {
        Random random = new Random(6);
        for (int i = 0; i < 100; i++) assertEquals("coal x1", only(drops("coal_ore", "rock", PICKAXE, 0, false, true, random)));
    }
}
