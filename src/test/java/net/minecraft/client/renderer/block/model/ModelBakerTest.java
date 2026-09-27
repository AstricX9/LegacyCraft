package net.minecraft.client.renderer.block.model;

import net.minecraft.block.BlockRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.client.resources.ClasspathResourceManager;
import net.minecraft.util.Direction;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelBakerTest {
    private final ClasspathResourceManager resources = new ClasspathResourceManager();
    private final BlockRegistry blocks = BlockRegistry.load(resources);
    private final BlockModelManager models = new BlockModelManager(resources, blocks);

    private BakedModel model(String block, int meta) { return models.getModel(BlockState.of(blocks.get(block), meta)); }

    private static String textureOn(BakedModel model, Direction face) {
        for (BakedQuad quad : model.quads()) if (quad.face() == face) return quad.texture().path();
        return null;
    }

    @Test
    void yRotationTurnsTheFurnaceFront() {
        assertEquals("textures/blocks/furnace_front_off.png", textureOn(model("minecraft:furnace", 2), Direction.NORTH));
        assertEquals("textures/blocks/furnace_front_off.png", textureOn(model("minecraft:furnace", 5), Direction.EAST));
        assertEquals("textures/blocks/furnace_front_off.png", textureOn(model("minecraft:furnace", 4), Direction.WEST));
    }

    @Test
    void xRotationPointsThePistonHeadDown() {
        BakedModel down = model("minecraft:piston", 0);
        assertEquals("textures/blocks/piston_top_normal.png", textureOn(down, Direction.DOWN));
        assertEquals(Direction.DOWN, faceWithCull(down, Direction.DOWN));
    }

    @Test
    void logAxisPutsTheRingsOnTheAxisFaces() {
        BakedModel alongX = model("minecraft:log", 4);
        assertEquals("textures/blocks/log_oak_top.png", textureOn(alongX, Direction.EAST));
        assertEquals("textures/blocks/log_oak_top.png", textureOn(alongX, Direction.WEST));
        assertEquals("textures/blocks/log_oak.png", textureOn(alongX, Direction.UP));
    }

    @Test
    void elementRotationLeansWallTorchesAwayFromTheWall() {
        // facing=east: attached to the block on the west, flame leans towards +x.
        // The flame cap (the only upward face) starts above the wall edge at x=0.
        BakedModel torch = model("minecraft:torch", 1);
        BakedQuad cap = torch.quads().stream().filter(quad -> quad.face() == Direction.UP).findFirst().orElseThrow();
        float capX = (cap.minX() + cap.maxX()) / 2;
        assertTrue(capX > .15f, "wall torch flame should lean east, was at x=" + capX);
    }

    @Test
    void weightedVariantsAreStablePerPositionAndVaryAcrossPositions() {
        WeightedModel grass = models.variantsFor(net.minecraft.block.state.ActualState.withoutWorld(
            blocks.get("minecraft:grass").defaultState()));
        assertEquals(4, grass.entries().size());
        assertEquals(grass.select(12, 70, -5), grass.select(12, 70, -5));
        Set<BakedModel> seen = new HashSet<>();
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) seen.add(grass.select(x, 64, z));
        assertEquals(4, seen.size(), "all four grass rotations should appear in a chunk");
    }

    private static Direction faceWithCull(BakedModel model, Direction face) {
        for (BakedQuad quad : model.quads()) if (quad.face() == face) return quad.cullFace();
        return null;
    }
}
