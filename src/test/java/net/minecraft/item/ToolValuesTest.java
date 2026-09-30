package net.minecraft.item;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Efficiency and Aqua Affinity in the break speed (HYPOTHESIS: recalled 1.8.9 EntityPlayer.getToolDigEfficiency). */
final class ToolValuesTest {
    private static final Item PICKAXE = TestItems.item("iron_pickaxe", 1, 250);

    @Test
    void efficiencyAddsLevelSquaredPlusOneToASuitedTool() {
        float plain = ToolValues.breakProgressPerTick(PICKAXE, "stone", "rock", 1.5f, true, false, 0, false);
        float fast = ToolValues.breakProgressPerTick(PICKAXE, "stone", "rock", 1.5f, true, false, 5, false);
        assertEquals(6f / 1.5f / 30f, plain, 1e-6, "iron mines at 6");
        assertEquals((6f + 26f) / 1.5f / 30f, fast, 1e-6);
    }

    @Test
    void efficiencyDoesNothingWhereTheToolDoesntHelp() {
        float plain = ToolValues.breakProgressPerTick(PICKAXE, "dirt", "ground", 0.5f, true, false, 0, false);
        assertEquals(plain, ToolValues.breakProgressPerTick(PICKAXE, "dirt", "ground", 0.5f, true, false, 5, false));
    }

    @Test
    void aquaAffinityRemovesTheUnderwaterSlowdownOnly() {
        float dry = ToolValues.breakProgressPerTick(PICKAXE, "stone", "rock", 1.5f, true, false, 0, false);
        assertEquals(dry / 5f, ToolValues.breakProgressPerTick(PICKAXE, "stone", "rock", 1.5f, true, true, 0, false), 1e-6);
        assertEquals(dry, ToolValues.breakProgressPerTick(PICKAXE, "stone", "rock", 1.5f, true, true, 0, true), 1e-6);
        assertEquals(dry / 5f, ToolValues.breakProgressPerTick(PICKAXE, "stone", "rock", 1.5f, false, true, 0, true), 1e-6,
            "still slow while floating");
    }
}
