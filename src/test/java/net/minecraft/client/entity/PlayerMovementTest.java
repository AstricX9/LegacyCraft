package net.minecraft.client.entity;

import net.minecraft.block.BlockState;
import net.minecraft.testing.GameData;
import net.minecraft.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The player's tick physics on a long stone runway high above the terrain
 * (HYPOTHESIS: the targets are vanilla 1.8.9's well-known speeds and heights).
 */
final class PlayerMovementTest {
    private static final int FLOOR = 200;
    private static final Player.Input FORWARD = new Player.Input(true, false, false, false, false, false, false);
    private static final Player.Input SPRINT = new Player.Input(true, false, false, false, false, false, true);
    private static final Player.Input SPRINT_JUMP = new Player.Input(true, false, false, false, true, false, true);
    private static final Player.Input SNEAK = new Player.Input(true, false, false, false, false, true, false);
    private static final Player.Input JUMP = new Player.Input(false, false, false, false, true, false, false);
    private World world;
    private Player player;

    @BeforeEach
    void runway() {
        world = GameData.newWorld(3L);
        fill(-2, FLOOR, -2, 2, FLOOR, 300, "stone");
        player = new Player(world);
        player.setPositionAndRotation(0.5, FLOOR + 1, 0.5, 180f, 0f); // yaw 180 here faces +z (vanilla yaw 0)
        settle();
    }

    private void fill(int x0, int y0, int z0, int x1, int y1, int z1, String block) {
        BlockState state = BlockState.defaultOf(GameData.blocks().get("minecraft:" + block));
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) world.setState(x, y, z, state);
    }

    private void settle() { for (int i = 0; i < 5; i++) player.tick(Player.Input.NONE); }

    /** Metres per second along +z once up to speed. */
    private double speed(Player.Input input) {
        for (int i = 0; i < 40; i++) player.tick(input);
        double start = player.z;
        for (int i = 0; i < 100; i++) player.tick(input);
        return (player.z - start) / 5.0;
    }

    @Test
    void walkingSprintingAndSneakingSpeeds() {
        assertEquals(4.317, speed(FORWARD), 0.01);
        runway();
        assertEquals(5.612, speed(SPRINT), 0.01);
        runway();
        assertEquals(1.31, speed(SNEAK), 0.02);
    }

    /** Blocking with a sword walks at a fifth of the input (0.2 x 4.317 m/s) and cannot sprint. */
    @Test
    void blockingWalksSlowlyAndStopsSprinting() {
        net.minecraft.item.ItemStack sword = new net.minecraft.item.ItemStack(net.minecraft.item.TestItems.sword("iron_sword"));
        player.setItemInUse(sword, 72000);
        assertTrue(player.isBlocking());
        assertEquals(4.317 * 0.2, speed(SPRINT), 0.02);
        assertFalse(player.sprinting);
        player.clearItemInUse();
        assertEquals(5.612, speed(SPRINT), 0.01);
    }

    @Test
    void sprintJumpingIsTheFastestOnFoot() {
        double sprint = speed(SPRINT);
        runway();
        double sprintJump = speed(SPRINT_JUMP);
        assertTrue(sprintJump > sprint + 1.0, "sprint-jumping " + sprintJump + " m/s vs sprinting " + sprint);
    }

    /**
     * 1.8 zeroes any motion under 0.005 a tick, which swallows the last
     * 0.003 of the rise: 1.2492 blocks (1.9+, with a 0.003 cut-off, reaches 1.2522).
     */
    @Test
    void aJumpRisesJustUnderOneAndAQuarterBlocks() {
        double peak = player.y;
        player.tick(JUMP);
        for (int i = 0; i < 20; i++) {
            player.tick(Player.Input.NONE);
            peak = Math.max(peak, player.y);
        }
        assertEquals(1.2492, peak - (FLOOR + 1), 0.0001);
        assertEquals(FLOOR + 1, player.y, 1e-9, "back on the ground");
    }

    @Test
    void sneakingNeverWalksOffAnEdge() {
        fill(-2, FLOOR, 0, 2, FLOOR, 300, "air");
        fill(-2, FLOOR, -2, 2, FLOOR, 0, "stone");
        for (int i = 0; i < 60; i++) player.tick(SNEAK);
        assertEquals(FLOOR + 1, player.y, 1e-9, "still on the runway");
        assertTrue(player.z > 1.0 && player.z < 1.31, "stopped at the edge, z " + player.z);
    }

    @Test
    void holdingJumpTreadsWaterAndLettingGoSinks() {
        fill(-2, FLOOR + 1, -2, 2, FLOOR + 6, 2, "water");
        player.setPositionAndRotation(0.5, FLOOR + 3, 0.5, 180f, 0f);
        for (int i = 0; i < 60; i++) player.tick(JUMP);
        assertTrue(player.inWater || player.y > FLOOR + 5, "near the surface");
        assertTrue(player.y > FLOOR + 4.5, "rose to the surface, y " + player.y);
        for (int i = 0; i < 100; i++) player.tick(Player.Input.NONE);
        assertEquals(FLOOR + 1, player.y, 1e-6, "sank to the bottom");
    }

    @Test
    void creativeFlightTopsOutAt11MetresPerSecond() {
        player.allowFlying = true;
        player.isFlying = true;
        player.setPositionAndRotation(0.5, FLOOR + 10, 0.5, 180f, 0f);
        double fly = speed(FORWARD);
        assertEquals(10.9, fly, 0.1);
        assertTrue(player.isFlying);
    }

    @Test
    void doubleTappingForwardSprints() {
        player.tick(FORWARD);
        player.tick(Player.Input.NONE);
        player.tick(FORWARD);
        assertTrue(player.sprinting);
    }
}
