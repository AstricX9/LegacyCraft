package net.minecraft.entity;

import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.item.TestItems;
import net.minecraft.testing.GameData;
import net.minecraft.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Dropped items meeting water and lava (HYPOTHESIS: recalled 1.8.9 rules). */
final class EntityItemTest {
    private static final int FLOOR = 200;

    /** A 3x3 pool of the liquid, 4 deep, on a stone floor high in the air. */
    private static World pool(String liquid) {
        World world = GameData.newWorld(42L);
        BlockState stone = BlockState.defaultOf(GameData.blocks().get("minecraft:stone"));
        BlockState fluid = BlockState.defaultOf(GameData.blocks().get("minecraft:" + liquid));
        for (int x = -1; x <= 1; x++)
            for (int z = -1; z <= 1; z++) {
                world.setState(x, FLOOR, z, stone);
                for (int y = FLOOR + 1; y <= FLOOR + 4; y++) world.setState(x, y, z, fluid);
            }
        return world;
    }

    private static EntityItem dropAbove(World world) {
        EntityItem item = new EntityItem(world, 0.5, FLOOR + 7, 0.5, new ItemStack(TestItems.item("stick")));
        item.motionX = item.motionZ = 0;
        return item;
    }

    /**
     * The first splash is at the surface. An item only counts as in water
     * while its height within a block is 0.15-0.6 (the shrunken-box quirk),
     * so a sinking item splashes again as it passes each block.
     */
    @Test
    void fallingIntoWaterSplashesAtTheSurface() {
        EntityItem item = dropAbove(pool("water"));
        double firstSplashY = Double.NaN;
        for (int tick = 0; tick < 100; tick++) {
            item.tick();
            if (item.takeSplash() && Double.isNaN(firstSplashY)) firstSplashY = item.y();
        }
        assertTrue(firstSplashY > FLOOR + 3.5 && firstSplashY < FLOOR + 5, "first splash at " + firstSplashY);
        assertFalse(item.isRemoved());
    }

    @Test
    void lavaBurnsItemsAwayWithinAFewTicks() {
        EntityItem item = dropAbove(pool("lava"));
        int tick = 0;
        while (!item.isRemoved() && tick < 200) {
            item.tick();
            tick++;
        }
        assertTrue(item.isRemoved(), "still there after " + tick + " ticks");
        assertTrue(tick < 60, "gone after " + tick + " ticks");
    }

    @Test
    void itemsOnDryGroundNeverSplashOrBurn() {
        World world = pool("water");
        world.setState(0, FLOOR + 5, 0, BlockState.defaultOf(GameData.blocks().get("minecraft:stone")));
        EntityItem item = new EntityItem(world, 0.5, FLOOR + 6.5, 0.5, new ItemStack(TestItems.item("stick")));
        item.motionX = item.motionZ = 0;
        for (int tick = 0; tick < 60; tick++) {
            item.tick();
            assertFalse(item.takeSplash());
            assertFalse(item.isBurning());
        }
    }
}
