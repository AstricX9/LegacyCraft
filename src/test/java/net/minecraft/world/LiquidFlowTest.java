package net.minecraft.world;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Liquids;
import net.minecraft.testing.GameData;
import net.minecraft.util.AxisAlignedBB;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Water and lava spreading on scheduled ticks (HYPOTHESIS: recalled 1.8.9 BlockDynamicLiquid rules). */
final class LiquidFlowTest {
    private static final int FLOOR = 240;

    private static Block block(String name) { return GameData.blocks().get("minecraft:" + name); }

    /** A stone floor from -radius to radius high in the air (nothing else nearby). */
    private static World floor(int radius) {
        World world = GameData.newWorld(42L);
        BlockState stone = BlockState.defaultOf(block("stone"));
        for (int x = -radius; x <= radius; x++)
            for (int z = -radius; z <= radius; z++) world.setState(x, FLOOR, z, stone);
        return world;
    }

    private static void run(World world, int ticks) {
        for (int i = 0; i < ticks; i++) world.tick();
    }

    private static void pour(World world, int x, int y, int z, String liquid) {
        world.setStateAndUpdate(x, y, z, BlockState.of(block("flowing_" + liquid), 0));
    }

    private static String material(World world, int x, int y, int z) { return world.get(x, y, z).material(); }

    @Test
    void waterSpreadsSevenBlocksThinningByOneLevelAndThenRests() {
        World world = floor(10);
        pour(world, 0, FLOOR + 1, 0, "water");
        run(world, 200);
        for (int distance = 1; distance <= 7; distance++) {
            BlockState state = world.getState(distance, FLOOR + 1, 0);
            assertEquals("water", state.block().material(), "at " + distance);
            assertEquals(distance, state.metadata(), "level at " + distance);
        }
        assertTrue(world.get(8, FLOOR + 1, 0).isAir());
        // Diagonals are reached around the corner: level = x + z.
        assertEquals(4, world.getState(2, FLOOR + 1, 2).metadata());
        assertEquals(block("water"), world.get(0, FLOOR + 1, 0), "the source settles as still water");
        assertEquals(block("water"), world.get(5, FLOOR + 1, 0), "flowing water settles when nothing changes");
        assertTrue(world.scheduledTicks().isEmpty());
    }

    @Test
    void lavaSpreadsThreeBlocksTwoLevelsAtATime() {
        World world = floor(6);
        pour(world, 0, FLOOR + 1, 0, "lava");
        run(world, 2000);
        assertEquals(2, world.getState(1, FLOOR + 1, 0).metadata());
        assertEquals(6, world.getState(3, FLOOR + 1, 0).metadata());
        assertTrue(world.get(4, FLOOR + 1, 0).isAir());
    }

    @Test
    void waterFallsAsFallingLiquidAndSpreadsFromWhereItLands() {
        World world = floor(8);
        BlockState stone = BlockState.defaultOf(block("stone"));
        world.setState(0, FLOOR + 5, 0, stone);
        pour(world, 0, FLOOR + 6, 0, "water");
        run(world, 300);
        // Off the pillar edge the water drops: falling blocks carry level 8 or more.
        assertTrue(world.getState(1, FLOOR + 4, 0).metadata() >= 8);
        assertTrue(world.getState(1, FLOOR + 1, 0).metadata() >= 8);
        assertEquals(1, world.getState(2, FLOOR + 1, 0).metadata(), "landing spreads like a source");
    }

    @Test
    void waterHeadsOnlyTowardsTheNearestDrop() {
        World world = floor(8);
        world.set(3, FLOOR, 0, block("air"));
        pour(world, 0, FLOOR + 1, 0, "water");
        run(world, 10);
        assertEquals("water", material(world, 1, FLOOR + 1, 0));
        assertTrue(world.get(-1, FLOOR + 1, 0).isAir(), "no flow away from the drop");
        assertTrue(world.get(0, FLOOR + 1, 1).isAir());
    }

    @Test
    void twoSourcesBesideAGapOverStoneMakeANewSource() {
        World world = floor(8);
        pour(world, -1, FLOOR + 1, 0, "water");
        pour(world, 1, FLOOR + 1, 0, "water");
        run(world, 100);
        assertEquals(0, world.getState(0, FLOOR + 1, 0).metadata());
    }

    @Test
    void removingTheSourceDrainsTheFlow() {
        World world = floor(10);
        pour(world, 0, FLOOR + 1, 0, "water");
        run(world, 200);
        world.setStateAndUpdate(0, FLOOR + 1, 0, BlockState.defaultOf(block("air")));
        run(world, 400);
        for (int x = -8; x <= 8; x++) assertTrue(world.get(x, FLOOR + 1, 0).isAir(), "water left at " + x);
    }

    @Test
    void breakingAWallLetsStillWaterFlowAgain() {
        World world = floor(8);
        BlockState stone = BlockState.defaultOf(block("stone"));
        world.setState(1, FLOOR + 1, 0, stone);
        world.setState(0, FLOOR + 1, 0, BlockState.defaultOf(block("water")));
        run(world, 20);
        assertTrue(world.get(-1, FLOOR + 1, 0).isAir(), "still water placed directly stays put");
        world.setStateAndUpdate(1, FLOOR + 1, 0, BlockState.defaultOf(block("air")));
        run(world, 20);
        assertEquals("water", material(world, 1, FLOOR + 1, 0));
    }

    @Test
    void lavaHardensWhereItMeetsWater() {
        World world = floor(8);
        world.setState(1, FLOOR + 1, 0, BlockState.defaultOf(block("water")));
        pour(world, 0, FLOOR + 1, 0, "lava");
        assertEquals(block("obsidian"), world.get(0, FLOOR + 1, 0), "a lava source beside water");

        World thin = floor(8);
        thin.setState(1, FLOOR + 1, 0, BlockState.defaultOf(block("water")));
        thin.setStateAndUpdate(0, FLOOR + 1, 0, BlockState.of(block("flowing_lava"), 2));
        assertEquals(block("cobblestone"), thin.get(0, FLOOR + 1, 0), "flowing lava beside water");
    }

    @Test
    void lavaFlowingDownIntoWaterMakesStone() {
        World world = floor(8);
        world.setState(0, FLOOR + 1, 0, BlockState.defaultOf(block("water")));
        pour(world, 0, FLOOR + 2, 0, "lava");
        run(world, 40);
        assertEquals(block("stone"), world.get(0, FLOOR + 1, 0));
    }

    @Test
    void liquidWashesAwayTorchesAndReportsThem() {
        World world = floor(8);
        world.setState(1, FLOOR + 1, 0, BlockState.defaultOf(block("torch")));
        java.util.List<String> washed = new java.util.ArrayList<>();
        world.setEvents(new World.Events() {
            @Override public void washedAway(int x, int y, int z, BlockState previous) {
                washed.add(x + "," + (y - FLOOR) + "," + z + "=" + previous.block().name().path());
            }
        });
        pour(world, 0, FLOOR + 1, 0, "water");
        run(world, 20);
        assertEquals("water", material(world, 1, FLOOR + 1, 0));
        assertTrue(washed.contains("1,1,0=torch"), washed.toString());
    }

    @Test
    void flowPointsDownstreamAndPushesEntities() {
        World world = floor(10);
        pour(world, 0, FLOOR + 1, 0, "water");
        run(world, 200);
        double[] flow = Liquids.flowVector(world, 3, FLOOR + 1, 0, "water");
        assertTrue(flow[0] > 0.9, "flow at x=3 should point east, was " + flow[0]);
        double[] current = world.liquidCurrent(new AxisAlignedBB(3.2, FLOOR + 1.0, -0.3, 3.8, FLOOR + 1.1, 0.3), "water");
        assertNotNull(current);
        assertTrue(current[0] > 0);
        assertEquals(0, Liquids.flowVector(world, 0, FLOOR + 1, 0, "water")[1]);
    }

    @Test
    void pendingTicksAreDueInOrderAndNotDuplicated() {
        World world = floor(2);
        Block flowing = block("flowing_water");
        world.scheduleUpdate(5, FLOOR + 5, 5, flowing, 10);
        world.scheduleUpdate(5, FLOOR + 5, 5, flowing, 3);
        assertEquals(1, world.scheduledTicks().size());
        assertEquals(10, world.scheduledTicks().get(0).due());
        assertTrue(world.isScheduled(5, FLOOR + 5, 5, flowing));
    }
}
