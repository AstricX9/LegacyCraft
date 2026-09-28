package net.minecraft.client.renderer.block.model;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.block.state.ActualState;
import net.minecraft.block.state.TestBlockAccess;
import net.minecraft.client.resources.ClasspathResourceManager;
import net.minecraft.testing.GameData;
import net.minecraft.util.AxisAlignedBB;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Where a block's hit box is simply the shape it is drawn with (slabs,
 * doors, trapdoors, ladders, carpets, cakes...), the 1.8.9 models are
 * independent evidence for the box: the selection box must equal the
 * bounds of the baked model for every state.
 */
final class BlockShapesModelTest {
    private final ClasspathResourceManager resources = GameData.resources();
    private final BlockRegistry blocks = GameData.blocks();
    private final BlockModelManager models = new BlockModelManager(resources, blocks);

    @Test
    void selectionBoxesMatchTheSuppliedModels() {
        String[] names = {"minecraft:stone_slab", "minecraft:wooden_slab", "minecraft:stone_slab2", "minecraft:trapdoor",
            "minecraft:iron_trapdoor", "minecraft:carpet", "minecraft:snow_layer", "minecraft:cake",
            "minecraft:enchanting_table", "minecraft:farmland", "minecraft:daylight_detector",
            "minecraft:bed", "minecraft:anvil", "minecraft:flower_pot"};
        List<String> failures = new ArrayList<>();
        for (String name : names) {
            Block block = blocks.get(name);
            for (int meta = 0; meta < 16; meta++) {
                BlockState state = BlockState.of(block, meta);
                ActualState actual = ActualState.withoutWorld(state);
                AxisAlignedBB expected = modelBounds(models.getModel(state));
                AxisAlignedBB box = block.selectionBox(actual, new TestBlockAccess(blocks), 0, 0, 0);
                if (!close(expected, box)) failures.add(name + " meta " + meta + ": model " + expected + " vs box " + box);
            }
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    @Test
    void ladderBoxesContainTheFlatLadderModel() {
        Block ladder = blocks.get("minecraft:ladder");
        for (int meta = 2; meta <= 5; meta++) {
            BlockState state = BlockState.of(ladder, meta);
            AxisAlignedBB model = modelBounds(models.getModel(state));
            AxisAlignedBB box = ladder.selectionBox(ActualState.withoutWorld(state), new TestBlockAccess(blocks), 0, 0, 0);
            assertTrue(model.minX() >= box.minX() - 1e-4 && model.maxX() <= box.maxX() + 1e-4
                && model.minZ() >= box.minZ() - 1e-4 && model.maxZ() <= box.maxZ() + 1e-4, state.values() + ": " + model + " outside " + box);
        }
    }

    @Test
    void doorBoxesFollowTheDoorModelInEveryState() {
        List<String> failures = new ArrayList<>();
        Block door = blocks.get("minecraft:wooden_door");
        for (int lowerMeta = 0; lowerMeta < 8; lowerMeta++) {
            for (int hinge = 0; hinge < 2; hinge++) {
                TestBlockAccess world = new TestBlockAccess(blocks)
                    .set(0, 64, 0, BlockState.of(door, lowerMeta))
                    .set(0, 65, 0, BlockState.of(door, 8 | hinge));
                ActualState actual = world.actual(0, 64, 0);
                AxisAlignedBB expected = modelBounds(models.getModel(actual, 0, 64, 0));
                AxisAlignedBB box = door.selectionBox(actual, world, 0, 64, 0);
                if (!close(expected, box)) failures.add(actual.values() + ": model " + expected + " vs box " + box);
            }
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    @Test
    void stairCollisionFillsTheCellsTheModelDraws() {
        for (int meta = 0; meta < 8; meta++) {
            for (int across = 0; across < 4; across++) {
                BlockState stairs = BlockState.of(blocks.get("minecraft:oak_stairs"), meta);
                TestBlockAccess world = new TestBlockAccess(blocks).set(0, 64, 0, stairs)
                    .set(1, 64, 0, BlockState.of(blocks.get("minecraft:oak_stairs"), across | (meta & 4)));
                ActualState actual = world.actual(0, 64, 0);
                List<AxisAlignedBB> boxes = actual.block().collisionBoxes(actual, world, 0, 64, 0);
                BakedModel model = models.getModel(actual, 0, 64, 0);
                for (float cx : new float[]{.25f, .75f}) for (float cy : new float[]{.25f, .75f}) for (float cz : new float[]{.25f, .75f}) {
                    boolean solid = boxes.stream().anyMatch(b -> cx > b.minX() && cx < b.maxX() && cy > b.minY() && cy < b.maxY() && cz > b.minZ() && cz < b.maxZ());
                    assertEquals(drawn(model, cx, cy, cz), solid, actual.values() + " at " + cx + "," + cy + "," + cz);
                }
            }
        }
    }

    /** Whether a point lies inside the model: an odd number of the model's faces lie above it along +Y. */
    private static boolean drawn(BakedModel model, float x, float y, float z) {
        int crossings = 0;
        for (BakedQuad quad : model.quads()) {
            if (quad.face() != net.minecraft.util.Direction.UP && quad.face() != net.minecraft.util.Direction.DOWN) continue;
            if (quad.minY() > y && x > quad.minX() && x < quad.maxX() && z > quad.minZ() && z < quad.maxZ()) crossings++;
        }
        return crossings % 2 == 1;
    }

    private static AxisAlignedBB modelBounds(BakedModel model) {
        double minX = 9, minY = 9, minZ = 9, maxX = -9, maxY = -9, maxZ = -9;
        for (BakedQuad quad : model.quads()) {
            minX = Math.min(minX, quad.minX()); maxX = Math.max(maxX, quad.maxX());
            minY = Math.min(minY, quad.minY()); maxY = Math.max(maxY, quad.maxY());
            minZ = Math.min(minZ, quad.minZ()); maxZ = Math.max(maxZ, quad.maxZ());
        }
        return new AxisAlignedBB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static boolean close(AxisAlignedBB a, AxisAlignedBB b) {
        double e = 1e-4;
        return b != null && Math.abs(a.minX() - b.minX()) < e && Math.abs(a.minY() - b.minY()) < e && Math.abs(a.minZ() - b.minZ()) < e
            && Math.abs(a.maxX() - b.maxX()) < e && Math.abs(a.maxY() - b.maxY()) < e && Math.abs(a.maxZ() - b.maxZ()) < e;
    }
}
