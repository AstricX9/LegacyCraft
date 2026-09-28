package net.minecraft.client.entity;

import net.minecraft.block.BlockRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.testing.GameData;
import net.minecraft.util.Direction;
import net.minecraft.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Collision and targeting against per-state block shapes, on a stone floor high above the terrain. */
final class PlayerCollisionTest {
    private static final int FLOOR = 200;
    private final BlockRegistry blocks = GameData.blocks();
    private World world;
    private Player player;

    @BeforeEach
    void buildFloor() {
        world = GameData.newWorld(1L);
        for (int x = -4; x <= 8; x++) for (int z = -4; z <= 4; z++) set(x, FLOOR, z, "minecraft:stone", 0);
        player = new Player(world);
        player.x = .5; player.y = FLOOR + 1; player.z = .5; player.onGround = true;
    }

    private void set(int x, int y, int z, String block, int meta) { world.setState(x, y, z, BlockState.of(blocks.get(block), meta)); }

    /** Walks east in small steps, like the client's 120 Hz movement ticks. */
    private void walkEast(double distance) {
        for (int i = 0; i < (int) (distance / .03); i++) player.move(.03, -.01, 0);
    }

    @Test
    void walksUpSlabsButNotFullBlocks() {
        set(2, FLOOR + 1, 0, "minecraft:stone_slab", 0);
        walkEast(2.5);
        assertEquals(FLOOR + 1.5, player.y, 1e-6, "stepped onto the slab");
        assertTrue(player.x > 2);

        buildFloor();
        set(2, FLOOR + 1, 0, "minecraft:stone", 0);
        walkEast(2.5);
        assertEquals(FLOOR + 1, player.y, 1e-6);
        assertEquals(1.7, player.x, 1e-6, "stopped against the block face");
    }

    @Test
    void fencesAreTooTallToStepOverAndStopAtThePost() {
        set(2, FLOOR + 1, 0, "minecraft:fence", 0);
        walkEast(2.5);
        assertEquals(FLOOR + 1, player.y, 1e-6);
        assertEquals(2.375 - .3, player.x, 1e-6, "the unconnected post starts 6/16 into the block");
    }

    @Test
    void walksThroughTorchesAndOverCarpet() {
        set(2, FLOOR + 1, 0, "minecraft:torch", 5);
        set(4, FLOOR + 1, 0, "minecraft:carpet", 0);
        walkEast(4.2);
        assertTrue(player.x > 4.5);
        assertEquals(FLOOR + 1 + 1 / 16.0, player.y, 1e-6, "standing on the carpet");
    }

    @Test
    void theCrosshairAimsPastSmallBlocks() {
        set(2, FLOOR + 1, 0, "minecraft:red_flower", 0);
        set(3, FLOOR + 1, 0, "minecraft:stone", 0);
        // Looking east at knee height through the flower's empty upper corner.
        WorldRenderer.Hit hit = WorldRenderer.raycast(world, .5, FLOOR + 1.8, .5, 1, 0, 0, 5);
        assertNotNull(hit);
        assertEquals(3, hit.x());
        assertEquals(Direction.WEST, hit.face());

        set(1, FLOOR + 1, 0, "minecraft:stone_slab", 0);
        WorldRenderer.Hit slab = WorldRenderer.raycast(world, 1.5, FLOOR + 3, .5, 0, -1, 0, 5);
        assertEquals(Direction.UP, slab.face());
        assertEquals(.5f, slab.hitY(), 1e-5, "hits the top of the half slab");
        assertFalse(slab.box().maxY() > FLOOR + 1.5 + 1e-9);
    }
}
