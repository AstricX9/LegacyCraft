package net.minecraft.client.renderer.block.model;

import net.minecraft.block.BlockRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.block.state.ActualState;
import net.minecraft.block.state.TestBlockAccess;
import net.minecraft.client.resources.ClasspathResourceManager;
import net.minecraft.util.Direction;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Checks stair corner shapes by geometry rather than by name: the resolved
 * shape is looked up in the supplied 1.8.9 blockstate files and baked, and the
 * full-height quarter cells of the result must join the neighbouring stair.
 * This pins the meaning of inner/outer left/right (for both halves) to the
 * shipped assets instead of to anyone's memory of the code.
 */
class StairShapeGeometryTest {
    private final ClasspathResourceManager resources = new ClasspathResourceManager();
    private final BlockRegistry blocks = BlockRegistry.load(resources);
    private final BlockModelManager models = new BlockModelManager(resources, blocks);

    @Test
    void straightStairsRiseTowardsTheirFacing() {
        for (String half : new String[]{"bottom", "top"}) {
            for (Direction facing : Direction.HORIZONTALS) {
                TestBlockAccess world = new TestBlockAccess(blocks).set(0, 64, 0, stairs(facing, half));
                assertEquals(cells(facing), tallCells(world, half), facing + " " + half);
            }
        }
    }

    @Test
    void outerCornersKeepOnlyTheSharedQuarter() {
        for (String half : new String[]{"bottom", "top"}) {
            for (Direction facing : Direction.HORIZONTALS) {
                for (Direction across : new Direction[]{facing.rotateY(), facing.rotateYCCW()}) {
                    TestBlockAccess world = new TestBlockAccess(blocks)
                        .set(0, 64, 0, stairs(facing, half))
                        .set(facing.xOffset, 64, facing.zOffset, stairs(across, half));
                    assertEquals(Set.of(cell(facing, across)), tallCells(world, half),
                        "outer " + facing + " with " + across + " behind, " + half);
                }
            }
        }
    }

    @Test
    void innerCornersFillAllButTheOppositeQuarter() {
        for (String half : new String[]{"bottom", "top"}) {
            for (Direction facing : Direction.HORIZONTALS) {
                for (Direction across : new Direction[]{facing.rotateY(), facing.rotateYCCW()}) {
                    TestBlockAccess world = new TestBlockAccess(blocks)
                        .set(0, 64, 0, stairs(facing, half))
                        .set(-facing.xOffset, 64, -facing.zOffset, stairs(across, half));
                    Set<String> expected = new TreeSet<>(Set.of("NE", "NW", "SE", "SW"));
                    expected.remove(cell(facing.opposite(), across.opposite()));
                    assertEquals(expected, tallCells(world, half),
                        "inner " + facing + " with " + across + " in front, " + half);
                }
            }
        }
    }

    @Test
    void aStairContinuingTheRowPreventsTheCorner() {
        // Behind (east) turns north, but the south side already continues this row facing east.
        TestBlockAccess world = new TestBlockAccess(blocks)
            .set(0, 64, 0, stairs(Direction.EAST, "bottom"))
            .set(1, 64, 0, stairs(Direction.NORTH, "bottom"))
            .set(0, 64, 1, stairs(Direction.EAST, "bottom"));
        assertEquals("straight", world.actual(0, 64, 0).get("shape"));
    }

    private BlockState stairs(Direction facing, String half) {
        return blocks.get("minecraft:oak_stairs").defaultState().with("facing", facing.getName()).with("half", half);
    }

    /**
     * Quarter cells (NE/NW/SE/SW) covered by the full-height part: for bottom
     * stairs the upward faces at y=1, for top stairs the downward faces at y=0.
     */
    private Set<String> tallCells(TestBlockAccess world, String half) {
        ActualState state = world.actual(0, 64, 0);
        BakedModel model = models.getModel(state, 0, 64, 0);
        boolean bottom = half.equals("bottom");
        Set<String> cells = new TreeSet<>();
        for (BakedQuad quad : model.quads()) {
            if (quad.face() != (bottom ? Direction.UP : Direction.DOWN)) continue;
            if (Math.abs(quad.minY() - (bottom ? 1f : 0f)) > 1e-4) continue;
            for (float cx : new float[]{.25f, .75f}) {
                for (float cz : new float[]{.25f, .75f}) {
                    if (cx > quad.minX() && cx < quad.maxX() && cz > quad.minZ() && cz < quad.maxZ())
                        cells.add((cz < .5f ? "N" : "S") + (cx < .5f ? "W" : "E"));
                }
            }
        }
        return cells;
    }

    private static Set<String> cells(Direction side) {
        Set<String> result = new TreeSet<>();
        for (String cell : new String[]{"NE", "NW", "SE", "SW"})
            if (cell.indexOf(Character.toUpperCase(side.getName().charAt(0))) >= 0) result.add(cell);
        return result;
    }

    private static String cell(Direction a, Direction b) {
        Direction northSouth = a.axis() == 'z' ? a : b, eastWest = a.axis() == 'x' ? a : b;
        return (northSouth == Direction.NORTH ? "N" : "S") + (eastWest == Direction.WEST ? "W" : "E");
    }
}
