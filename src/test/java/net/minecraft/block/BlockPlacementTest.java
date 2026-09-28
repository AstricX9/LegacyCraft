package net.minecraft.block;

import net.minecraft.block.state.TestBlockAccess;
import net.minecraft.testing.GameData;
import net.minecraft.util.Direction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The state a held block takes when placed: log axes, stair facing and
 * flipping, slab merging, wall torches, player-facing blocks, two-block doors
 * and plants, and refused placements.
 */
final class BlockPlacementTest {
    private final BlockRegistry blocks = GameData.blocks();

    /** Clicks a face of the stone block at the origin while looking in {@code facing}. */
    private List<BlockPlacement.Placement> click(TestBlockAccess world, String block, int meta,
                                                 Direction face, float hitY, Direction facing) {
        var context = new BlockPlacement.Context(0, 64, 0, face, .5f, hitY, .5f, facing, 5.5, 66, 5.5);
        return BlockPlacement.place(world, blocks, blocks.get(block), meta, context);
    }

    private TestBlockAccess stoneWorld() {
        return new TestBlockAccess(blocks).set(0, 64, 0, "minecraft:stone", 0).set(0, 63, 0, "minecraft:stone", 0);
    }

    @Test
    void logsTakeTheAxisOfTheClickedFace() {
        var placed = click(stoneWorld(), "minecraft:log", 2, Direction.EAST, .5f, Direction.WEST).get(0);
        assertEquals(1, placed.x());
        assertEquals("x", placed.state().get("axis"));
        assertEquals("birch", placed.state().get("variant"));
        assertEquals("y", click(stoneWorld(), "minecraft:log", 0, Direction.UP, 1f, Direction.WEST).get(0).state().get("axis"));
    }

    @Test
    void stairsRiseAwayFromThePlayerAndFlipOnUpperHits() {
        var bottom = click(stoneWorld(), "minecraft:oak_stairs", 0, Direction.UP, 1f, Direction.NORTH).get(0).state();
        assertEquals("north", bottom.get("facing"));
        assertEquals("bottom", bottom.get("half"));
        assertEquals("top", click(stoneWorld(), "minecraft:oak_stairs", 0, Direction.EAST, .75f, Direction.WEST).get(0).state().get("half"));
        TestBlockAccess floating = new TestBlockAccess(blocks).set(0, 64, 0, "minecraft:stone", 0);
        assertEquals("top", click(floating, "minecraft:oak_stairs", 0, Direction.DOWN, 0f, Direction.WEST).get(0).state().get("half"));
    }

    @Test
    void slabsMergeIntoTheDoubleSlab() {
        TestBlockAccess world = new TestBlockAccess(blocks).set(0, 64, 0, "minecraft:stone_slab", 3); // bottom cobblestone slab
        var merged = click(world, "minecraft:stone_slab", 3, Direction.UP, .5f, Direction.NORTH);
        assertEquals(List.of(new BlockPlacement.Placement(0, 64, 0,
            BlockState.of(blocks.get("minecraft:double_stone_slab"), 3))), merged);
        // A different variant stacks on top instead.
        var stacked = click(world, "minecraft:stone_slab", 4, Direction.UP, .5f, Direction.NORTH).get(0);
        assertEquals(65, stacked.y());
        assertEquals("minecraft:stone_slab", stacked.state().block().name().toString());
    }

    @Test
    void torchesAttachToTheClickedWall() {
        assertEquals("east", click(stoneWorld(), "minecraft:torch", 0, Direction.EAST, .5f, Direction.WEST).get(0).state().get("facing"));
        assertEquals("up", click(stoneWorld(), "minecraft:torch", 0, Direction.UP, 1f, Direction.WEST).get(0).state().get("facing"));
    }

    @Test
    void furnacesAndPumpkinsFaceThePlayer() {
        assertEquals("south", click(stoneWorld(), "minecraft:furnace", 0, Direction.UP, 1f, Direction.NORTH).get(0).state().get("facing"));
        assertEquals("east", click(stoneWorld(), "minecraft:pumpkin", 0, Direction.UP, 1f, Direction.WEST).get(0).state().get("facing"));
    }

    @Test
    void doorsAndDoublePlantsOccupyTwoBlocks() {
        var door = click(stoneWorld(), "minecraft:wooden_door", 0, Direction.UP, 1f, Direction.NORTH);
        assertEquals(2, door.size());
        assertEquals("lower", door.get(0).state().get("half"));
        assertEquals("north", door.get(0).state().get("facing"));
        assertEquals(66, door.get(1).y());
        assertEquals("upper", door.get(1).state().get("half"));

        var rose = click(stoneWorld().set(0, 64, 0, "minecraft:grass", 0), "minecraft:double_plant", 4, Direction.UP, 1f, Direction.NORTH);
        assertEquals("double_rose", rose.get(0).state().get("variant"));
        assertEquals(8, rose.get(1).state().metadata());
    }

    @Test
    void occupiedTargetsAndUnsupportedFacesAreRejected() {
        TestBlockAccess world = stoneWorld().set(0, 65, 0, "minecraft:stone", 0);
        assertTrue(click(world, "minecraft:stone", 0, Direction.UP, 1f, Direction.NORTH).isEmpty());
        assertTrue(click(stoneWorld(), "minecraft:trapdoor", 0, Direction.UP, 1f, Direction.NORTH).isEmpty(),
            "1.8 trapdoors only attach to the side of a block");
    }
}
