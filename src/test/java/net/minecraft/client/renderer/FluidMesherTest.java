package net.minecraft.client.renderer;

import net.minecraft.block.BlockState;
import net.minecraft.testing.GameData;
import net.minecraft.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Liquid surface corner heights (HYPOTHESIS: recalled BlockFluidRenderer.getFluidHeight). */
final class FluidMesherTest {
    private static final int Y = 250;

    private static World world() {
        World world = GameData.newWorld(42L);
        world.disablePopulation();
        return world;
    }

    private static void put(World world, int x, int y, int z, String block, int meta) {
        world.setState(x, y, z, BlockState.of(GameData.blocks().get("minecraft:" + block), meta));
    }

    private static float height(World world, int x, int z) {
        return FluidMesher.height(new SectionSnapshot(world, 0, Y >> 4, 0), x, Y, z, "water");
    }

    @Test
    void aPoolOfSourcesSitsOneNinthBelowTheTop() {
        World world = world();
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) put(world, x, Y, z, "water", 0);
        assertEquals(8f / 9f, height(world, 0, 0), 1e-6);
    }

    @Test
    void aLoneSourceSlopesTowardsTheOpenAirAroundIt() {
        World world = world();
        put(world, 0, Y, 0, "water", 0);
        // One source (weight 10 + 1 at 1/9) and three open blocks pulling to 0.
        assertEquals(1f - (11f / 9f + 3f) / 14f, height(world, 0, 0), 1e-6);
    }

    @Test
    void thinFlowingWaterIsLowerAndLiquidAboveFillsTheCorner() {
        World world = world();
        put(world, 0, Y, 0, "flowing_water", 7);
        put(world, -1, Y, 0, "stone", 0);
        put(world, 0, Y, -1, "stone", 0);
        put(world, -1, Y, -1, "stone", 0);
        assertEquals(1f - 8f / 9f, height(world, 0, 0), 1e-6);
        put(world, 0, Y + 1, 0, "water", 0);
        assertEquals(1f, height(world, 0, 0));
    }
}
