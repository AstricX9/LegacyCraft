package net.minecraft.block;

import net.minecraft.block.state.TestBlockAccess;
import net.minecraft.client.resources.ClasspathResourceManager;
import net.minecraft.util.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockUpdatesTest {
    private final BlockRegistry blocks = BlockRegistry.load(new ClasspathResourceManager());

    private String at(TestBlockAccess world, int x, int y, int z) { return world.getState(x, y, z).block().name().path(); }

    private void breakBlock(TestBlockAccess world, int x, int y, int z) {
        world.setState(x, y, z, BlockState.of(blocks.air(), 0));
        BlockUpdates.afterChange(world, x, y, z);
    }

    @Test
    void wallTorchesPopWhenTheirWallGoes() {
        TestBlockAccess world = new TestBlockAccess(blocks)
            .set(0, 64, 0, "minecraft:stone", 0)
            .set(1, 64, 0, "minecraft:torch", 1)   // facing east: hangs on the block to its west
            .set(0, 65, 0, "minecraft:torch", 5);  // standing on top
        breakBlock(world, 0, 64, 0);
        assertEquals("air", at(world, 1, 64, 0));
        assertEquals("air", at(world, 0, 65, 0));
    }

    @Test
    void breakingEitherDoorHalfRemovesTheOther() {
        TestBlockAccess world = new TestBlockAccess(blocks)
            .set(0, 63, 0, "minecraft:stone", 0)
            .set(0, 64, 0, "minecraft:wooden_door", 3)
            .set(0, 65, 0, "minecraft:wooden_door", 8);
        breakBlock(world, 0, 65, 0);
        assertEquals("air", at(world, 0, 64, 0));

        world.set(0, 64, 0, "minecraft:wooden_door", 3).set(0, 65, 0, "minecraft:wooden_door", 8);
        breakBlock(world, 0, 63, 0);
        assertEquals("air", at(world, 0, 64, 0), "a door needs a solid block below");
        assertEquals("air", at(world, 0, 65, 0));
    }

    @Test
    void plantsNeedTheirSoilAndCascadeUpwards() {
        TestBlockAccess world = new TestBlockAccess(blocks)
            .set(0, 63, 0, "minecraft:dirt", 0)
            .set(0, 64, 0, "minecraft:double_plant", 4)
            .set(0, 65, 0, "minecraft:double_plant", 8)
            .set(2, 63, 0, "minecraft:sand", 0)
            .set(2, 64, 0, "minecraft:cactus", 0)
            .set(2, 65, 0, "minecraft:cactus", 0);
        breakBlock(world, 0, 63, 0);
        assertEquals("air", at(world, 0, 64, 0));
        assertEquals("air", at(world, 0, 65, 0));
        breakBlock(world, 2, 63, 0);
        assertEquals("air", at(world, 2, 65, 0), "the whole cactus column falls");
    }

    @Test
    void vinesFallOnlyWhenNothingHoldsThemAtAll() {
        // south=1, west=2: walls on the south and west.
        TestBlockAccess world = new TestBlockAccess(blocks)
            .set(0, 64, 1, "minecraft:stone", 0)
            .set(-1, 64, 0, "minecraft:stone", 0)
            .set(0, 64, 0, "minecraft:vine", 1 | 2);
        breakBlock(world, -1, 64, 0);
        // 1.8 keeps the stored sides: losing one wall does not change the vine (verified against vanilla saves).
        assertEquals(1 | 2, world.getState(0, 64, 0).metadata());
        breakBlock(world, 0, 64, 1);
        assertEquals("air", at(world, 0, 64, 0), "with no wall and nothing above, the vine drops");
    }

    @Test
    void vinesHangFromWhateverIsAboveThem() {
        TestBlockAccess world = new TestBlockAccess(blocks)
            .set(0, 65, 0, "minecraft:leaves", 0)
            .set(0, 64, 0, "minecraft:vine", 4)      // north side, with no wall to the north
            .set(0, 63, 0, "minecraft:vine", 4);
        BlockUpdates.afterChange(world, 0, 65, 0);
        assertEquals("vine", at(world, 0, 64, 0), "a vine hangs under leaves");
        assertEquals("vine", at(world, 0, 63, 0), "and under another vine");
        breakBlock(world, 0, 65, 0);
        assertEquals("air", at(world, 0, 64, 0));
        assertEquals("air", at(world, 0, 63, 0), "the whole strand falls");
    }

    @Test
    void placementRefusesStatesThatCouldNotStay() {
        TestBlockAccess world = new TestBlockAccess(blocks).set(0, 64, 0, "minecraft:stone", 0);
        var onTop = new BlockPlacement.Context(0, 64, 0, Direction.UP, .5f, 1f, .5f, Direction.NORTH, 5, 66, 5);
        assertTrue(BlockPlacement.place(world, blocks, blocks.get("minecraft:red_flower"), 0, onTop).isEmpty(),
            "flowers do not grow on stone");
        world.set(0, 64, 0, "minecraft:glass_pane", 0);
        assertTrue(BlockPlacement.place(world, blocks, blocks.get("minecraft:torch"), 0, onTop).isEmpty(),
            "torches cannot stand on panes");
        world.set(0, 64, 0, "minecraft:grass", 0);
        assertEquals(1, BlockPlacement.place(world, blocks, blocks.get("minecraft:red_flower"), 0, onTop).size());
    }
}
