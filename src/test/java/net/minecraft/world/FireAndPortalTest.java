package net.minecraft.world;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.state.ActualState;
import net.minecraft.testing.GameData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Fire burning and nether portals lighting (HYPOTHESIS: recalled BlockFire and BlockPortal.Size rules). */
final class FireAndPortalTest {
    private static final int FLOOR = 240;

    private static Block block(String name) { return GameData.blocks().get("minecraft:" + name); }

    private static World world() {
        World world = GameData.newWorld(42L);
        world.disablePopulation();
        return world;
    }

    private static void put(World world, int x, int y, int z, String name) { world.setState(x, y, z, BlockState.defaultOf(block(name))); }

    private static void light(World world, int x, int y, int z) {
        world.setStateAndUpdate(x, y, z, BlockState.of(block("fire"), 0));
    }

    private static void run(World world, int ticks) { for (int i = 0; i < ticks; i++) world.tick(); }

    /** An obsidian frame with an inside {@code width} x {@code height}, bottom-left inside corner at (x, y, z). */
    private static void frame(World world, int x, int y, int z, int width, int height, boolean alongX) {
        for (int i = -1; i <= width; i++)
            for (int j = -1; j <= height; j++) {
                boolean edge = i == -1 || i == width || j == -1 || j == height;
                if (!edge) continue;
                if (alongX) put(world, x + i, y + j, z, "obsidian");
                else put(world, x, y + j, z + i, "obsidian");
            }
    }

    @Test
    void fireInAnObsidianFrameLightsAPortal() {
        World world = world();
        frame(world, 0, FLOOR, 0, 2, 3, true);
        light(world, 0, FLOOR, 0);
        for (int i = 0; i < 2; i++)
            for (int j = 0; j < 3; j++) {
                BlockState state = world.getState(i, FLOOR + j, 0);
                assertEquals(block("portal"), state.block(), "inside at " + i + "," + j);
                assertEquals("x", state.get("axis"));
            }
    }

    @Test
    void northSouthFramesLightAlongZ() {
        World world = world();
        frame(world, 0, FLOOR, 0, 3, 4, false);
        light(world, 0, FLOOR + 2, 2);
        assertEquals(block("portal"), world.get(0, FLOOR, 0));
        assertEquals("z", world.getState(0, FLOOR + 3, 2).get("axis"));
    }

    @Test
    void anIncompleteFrameOnlyGetsFire() {
        World world = world();
        frame(world, 0, FLOOR, 0, 2, 3, true);
        world.set(2, FLOOR + 1, 0, block("air"));
        light(world, 0, FLOOR, 0);
        assertEquals(block("fire"), world.get(0, FLOOR, 0), "fire on obsidian, no portal");
        assertNotEquals(block("portal"), world.get(1, FLOOR + 1, 0));
    }

    @Test
    void breakingTheFrameTakesTheWholePortalOut() {
        World world = world();
        frame(world, 0, FLOOR, 0, 2, 3, true);
        light(world, 0, FLOOR, 0);
        java.util.List<String> popped = new java.util.ArrayList<>(), shattered = new java.util.ArrayList<>();
        world.setEvents(new World.Events() {
            @Override public void popped(int x, int y, int z, BlockState previous) { popped.add(previous.block().name().path()); }
            @Override public void shattered(int x, int y, int z, BlockState previous) { shattered.add(previous.block().name().path()); }
        });
        world.setStateAndUpdate(-1, FLOOR + 1, 0, BlockState.defaultOf(block("air")));
        for (int i = 0; i < 2; i++)
            for (int j = 0; j < 3; j++) assertTrue(world.get(i, FLOOR + j, 0).isAir(), "left at " + i + "," + j);
        assertEquals(java.util.List.of(), popped, "portal blocks drop nothing");
        assertEquals(6, shattered.size(), "each portal block shatters (glass sound and particles): " + shattered);
    }

    @Test
    void fireOnStoneBurnsOutAndOnNetherrackBurnsForever() {
        World world = world();
        put(world, 0, FLOOR, 0, "stone");
        put(world, 5, FLOOR, 0, "netherrack");
        light(world, 0, FLOOR + 1, 0);
        light(world, 5, FLOOR + 1, 0);
        assertEquals(block("fire"), world.get(0, FLOOR + 1, 0));
        run(world, 6000);
        assertTrue(world.get(0, FLOOR + 1, 0).isAir(), "burnt out");
        assertEquals(block("fire"), world.get(5, FLOOR + 1, 0));
    }

    @Test
    void fireInMidAirGoesOutAtOnce() {
        World world = world();
        light(world, 0, FLOOR + 5, 0);
        assertTrue(world.get(0, FLOOR + 5, 0).isAir());
    }

    @Test
    void fireBurnsWoolAwayUnlessFireTickIsOff() {
        World world = world();
        put(world, 0, FLOOR, 0, "wool");
        light(world, 0, FLOOR + 1, 0);
        run(world, 12000);
        assertNotEquals(block("wool"), world.get(0, FLOOR, 0), "the wool burnt");

        World calm = world();
        calm.setDoFireTick(false);
        put(calm, 0, FLOOR, 0, "wool");
        light(calm, 0, FLOOR + 1, 0);
        run(calm, 12000);
        assertEquals(block("wool"), calm.get(0, FLOOR, 0));
        assertEquals(block("fire"), calm.get(0, FLOOR + 1, 0));
    }

    @Test
    void fireWithNothingBelowClimbsTheFlammableSides() {
        World world = world();
        put(world, 1, FLOOR, 0, "wool");
        world.setState(0, FLOOR, 0, BlockState.of(block("fire"), 0));
        BlockState state = world.getState(0, FLOOR, 0);
        ActualState actual = state.block().actualState(world, 0, FLOOR, 0, state);
        assertEquals("true", actual.get("east"));
        assertEquals("false", actual.get("west"));
    }
}
