package net.minecraft.item;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Tool rules: tool types, harvest levels, break times and wear. */
final class ToolValuesTest {
    private final Item woodPick = TestItems.item("wooden_pickaxe", 1, 59), stonePick = TestItems.item("stone_pickaxe", 1, 131);
    private final Item ironPick = TestItems.item("iron_pickaxe", 1, 250), diamondPick = TestItems.item("diamond_pickaxe", 1, 1561);
    private final Item goldPick = TestItems.item("golden_pickaxe", 1, 32), ironAxe = TestItems.item("iron_axe", 1, 250);
    private final Item shovel = TestItems.item("stone_shovel", 1, 131), sword = TestItems.item("iron_sword", 1, 250);
    private final Item shears = TestItems.item("shears", 1, 238);

    @Test
    void classifiesToolsByName() {
        assertEquals(ToolValues.Type.PICKAXE, ToolValues.type(goldPick));
        assertEquals(ToolValues.Type.AXE, ToolValues.type(ironAxe));
        assertEquals(ToolValues.Type.SHEARS, ToolValues.type(shears));
        assertEquals(ToolValues.Type.NONE, ToolValues.type(TestItems.item("stick")));
        assertEquals(ToolValues.Type.NONE, ToolValues.type(null));
        assertEquals(3, ToolValues.harvestLevel(diamondPick));
        assertEquals(-1, ToolValues.harvestLevel(sword));
    }

    @Test
    void harvestLevelsGateOres() {
        assertFalse(ToolValues.canHarvest(null, "stone", "rock"), "bare hands get nothing from stone");
        assertTrue(ToolValues.canHarvest(woodPick, "stone", "rock"));
        assertFalse(ToolValues.canHarvest(woodPick, "iron_ore", "rock"));
        assertTrue(ToolValues.canHarvest(stonePick, "iron_ore", "rock"));
        assertFalse(ToolValues.canHarvest(stonePick, "diamond_ore", "rock"));
        assertTrue(ToolValues.canHarvest(ironPick, "diamond_ore", "rock"));
        assertFalse(ToolValues.canHarvest(ironPick, "obsidian", "rock"));
        assertTrue(ToolValues.canHarvest(diamondPick, "obsidian", "rock"));
        assertFalse(ToolValues.canHarvest(goldPick, "iron_ore", "rock"), "gold is harvest level 0");
        assertTrue(ToolValues.canHarvest(null, "dirt", "ground"));
        assertFalse(ToolValues.canHarvest(null, "snow_layer", "snow"));
        assertTrue(ToolValues.canHarvest(shovel, "snow_layer", "snow"));
        assertTrue(ToolValues.canHarvest(sword, "web", "web"));
        assertTrue(ToolValues.canHarvest(shears, "web", "web"));
    }

    @Test
    void breakTimesFollowHardnessAndTools() {
        // Stone by hand: 1 / 1.5 / 100 per tick -> 150 ticks (7.5 s); wooden pickaxe: 2 / 1.5 / 30 -> 22.5 ticks.
        assertEquals(1f / 150f, ToolValues.breakProgressPerTick(null, "stone", "rock", 1.5f, true, false), 1e-6);
        assertEquals(2f / 45f, ToolValues.breakProgressPerTick(woodPick, "stone", "rock", 1.5f, true, false), 1e-6);
        // Dirt by hand: 1 / 0.5 / 30 per tick -> 15 ticks.
        assertEquals(1f / 15f, ToolValues.breakProgressPerTick(null, "dirt", "ground", 0.5f, true, false), 1e-6);
        assertEquals(1f / 75f, ToolValues.breakProgressPerTick(null, "dirt", "ground", 0.5f, false, false), 1e-6,
            "five times slower off the ground");
        assertEquals(1f, ToolValues.breakProgressPerTick(null, "torch", "circuits", 0f, true, false));
        assertEquals(0f, ToolValues.breakProgressPerTick(diamondPick, "bedrock", "rock", -1f, true, false));
        assertEquals(15f, ToolValues.strVsBlock(sword, "web", "web"));
        assertEquals(1.5f, ToolValues.strVsBlock(sword, "leaves", "leaves"));
        assertEquals(6f, ToolValues.strVsBlock(ironAxe, "log", "wood"));
        assertEquals(4f, ToolValues.strVsBlock(shovel, "gravel", "sand"));
        assertEquals(1f, ToolValues.strVsBlock(shovel, "stone", "rock"));
        assertEquals(5f, ToolValues.strVsBlock(shears, "wool", "cloth"));
    }

    @Test
    void toolsWearOnlyOnBlocksWithHardness() {
        assertEquals(1, ToolValues.damageForBreak(ironPick, "stone", "rock", 1.5f));
        assertEquals(0, ToolValues.damageForBreak(ironPick, "torch", "circuits", 0f));
        assertEquals(2, ToolValues.damageForBreak(sword, "dirt", "ground", 0.5f));
        assertEquals(1, ToolValues.damageForBreak(shears, "leaves", "leaves", 0.2f));
        assertEquals(0, ToolValues.damageForBreak(shears, "dirt", "ground", 0.5f));
        assertEquals(0, ToolValues.damageForBreak(null, "dirt", "ground", 0.5f));
    }
}
