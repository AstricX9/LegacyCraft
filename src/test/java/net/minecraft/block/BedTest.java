package net.minecraft.block;

import net.minecraft.testing.GameData;
import net.minecraft.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Sleeping rules for beds (1.8.9 BlockBed and EntityPlayer.trySleep). */
final class BedTest {
    @Test
    void sleepIsOnlyAllowedAtNightOrInAThunderstorm() {
        assertFalse(BlockBed.canSleepNow(6000, false, false), "noon");
        assertTrue(BlockBed.canSleepNow(18000, false, false), "midnight");
        assertFalse(BlockBed.canSleepNow(12000, false, false), "sunset");
        assertTrue(BlockBed.canSleepNow(13000, false, false), "dusk");
        assertTrue(BlockBed.canSleepNow(6000, true, true), "thunderstorm at noon");
        assertFalse(BlockBed.canSleepNow(6000, true, false), "plain rain at noon");
    }

    @Test
    void headExitAndLyingSpotFollowTheBed() {
        World world = GameData.newWorld(1L);
        var bed = world.registry().get("minecraft:bed");
        var stone = world.registry().get("minecraft:stone");
        var air = world.registry().air();
        for (int x = -3; x <= 3; x++)
            for (int z = -3; z <= 4; z++) {
                world.setState(x, 99, z, BlockState.of(stone, 0));
                for (int y = 100; y < 103; y++) world.setState(x, y, z, BlockState.of(air, 0));
            }
        BlockState foot = BlockState.of(bed, 0).with("facing", "south").with("part", "foot").with("occupied", "false");
        world.setState(0, 100, 0, foot);
        world.setState(0, 100, 1, foot.with("part", "head"));
        assertArrayEquals(new int[]{0, 100, 1}, BlockBed.head(world, 0, 100, 0));
        assertArrayEquals(new int[]{-1, 100, 0}, BlockBed.exitLocation(world, 0, 100, 1));
        double[] lie = BlockBed.sleepPosition(world, 0, 100, 1);
        assertEquals(1.9, lie[2], 1e-9);
        assertEquals(100.6875, lie[1], 1e-9);
        assertEquals(net.minecraft.util.Direction.NORTH, BlockBed.footward(world, 0, 100, 1));
    }
}
