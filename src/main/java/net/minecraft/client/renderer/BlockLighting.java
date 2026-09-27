package net.minecraft.client.renderer;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.renderer.block.model.BakedModel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.util.Direction;

/**
 * Per-vertex light for block faces, following 1.8's "smooth lighting:
 * maximum". A face on the block's boundary samples the block it faces;
 * each corner averages the light of that block, the two blocks beside it in
 * the face's plane and the diagonal block between them (unless both sides are
 * solid), and darkens by ambient occlusion where those blocks are full cubes.
 * Vertices inside the face blend the four corners by position. Models with
 * {@code ambientocclusion: false}, light-emitting blocks and fluids use flat
 * lighting from a single sample. HYPOTHESIS until screenshot-compared (§85).
 */
final class BlockLighting {
    /** Light and shade for the four vertices of one quad. */
    static final class Result {
        final float[] sky = new float[4], block = new float[4], occlusion = new float[4];
    }

    private static final float OCCLUDED = .2f;
    private final RenderView world;

    BlockLighting(RenderView world) { this.world = world; }

    void light(Block block, BakedModel model, BakedQuad quad, int x, int y, int z, Result out) {
        Direction face = quad.face();
        boolean boundary = onBoundary(quad, face);
        int bx = boundary ? x + face.xOffset : x, by = boundary ? y + face.yOffset : y, bz = boundary ? z + face.zOffset : z;
        boolean smooth = model.ambientOcclusion() && block.lightLevel() == 0 && block.renderType() == Block.RenderType.MODEL;
        if (!smooth) {
            int center = sample(bx, by, bz);
            // Liquids take the brighter of their cell and the one above (vanilla BlockLiquid's mixed
            // brightness), so a water surface is lit by the air over it, flat per block.
            if (block.renderType() == Block.RenderType.FLUID) {
                int above = sample(bx, by + 1, bz);
                center = Math.max(center >> 8, above >> 8) << 8 | Math.max(center & 15, above & 15);
            }
            for (int i = 0; i < 4; i++) { out.sky[i] = center >> 8; out.block[i] = center & 15; out.occlusion[i] = 1f; }
            return;
        }
        Direction[] axes = planeAxes(face);
        Direction a = axes[0], b = axes[1];
        int center = sample(bx, by, bz);
        float centerAo = occlusion(bx, by, bz);
        float[][] cornerSky = new float[2][2], cornerBlock = new float[2][2], cornerAo = new float[2][2];
        for (int ia = 0; ia < 2; ia++) {
            for (int ib = 0; ib < 2; ib++) {
                Direction da = ia == 0 ? a.opposite() : a, db = ib == 0 ? b.opposite() : b;
                int ax = bx + da.xOffset, ay = by + da.yOffset, az = bz + da.zOffset;
                int cx = bx + db.xOffset, cy = by + db.yOffset, cz = bz + db.zOffset;
                int sideA = sample(ax, ay, az), sideB = sample(cx, cy, cz);
                float aoA = occlusion(ax, ay, az), aoB = occlusion(cx, cy, cz);
                boolean open = !opaque(ax, ay, az) || !opaque(cx, cy, cz);
                int dx = ax + db.xOffset, dy = ay + db.yOffset, dz = az + db.zOffset;
                int diagonal = open ? sample(dx, dy, dz) : sideA;
                float aoDiagonal = open ? occlusion(dx, dy, dz) : aoA;
                // An unlit sample (0 sky, 0 block) takes the centre's light instead of dragging the corner black.
                if (sideA == 0) sideA = center;
                if (sideB == 0) sideB = center;
                if (diagonal == 0) diagonal = center;
                cornerSky[ia][ib] = ((center >> 8) + (sideA >> 8) + (sideB >> 8) + (diagonal >> 8)) / 4f;
                cornerBlock[ia][ib] = ((center & 15) + (sideA & 15) + (sideB & 15) + (diagonal & 15)) / 4f;
                cornerAo[ia][ib] = (centerAo + aoA + aoB + aoDiagonal) / 4f;
            }
        }
        for (int i = 0; i < 4; i++) {
            float u = along(quad, i, a), v = along(quad, i, b);
            out.sky[i] = bilinear(cornerSky, u, v);
            out.block[i] = bilinear(cornerBlock, u, v);
            out.occlusion[i] = bilinear(cornerAo, u, v);
        }
    }

    /** Sky light in the high byte, block light in the low nibble; partial blocks use their brightest neighbour. */
    private int sample(int x, int y, int z) {
        BlockState state = world.getState(x, y, z);
        Block block = state.block();
        if (block.useNeighborBrightness()) {
            int sky = 0, light = 0;
            for (Direction side : new Direction[]{Direction.UP, Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH}) {
                sky = Math.max(sky, world.skyLight(x + side.xOffset, y + side.yOffset, z + side.zOffset));
                light = Math.max(light, world.blockLight(x + side.xOffset, y + side.yOffset, z + side.zOffset));
            }
            return sky << 8 | Math.max(light, block.lightLevel());
        }
        return world.skyLight(x, y, z) << 8 | Math.max(world.blockLight(x, y, z), block.lightLevel());
    }

    private float occlusion(int x, int y, int z) { return world.getState(x, y, z).block().isNormalCube() ? OCCLUDED : 1f; }
    private boolean opaque(int x, int y, int z) { return world.getState(x, y, z).block().lightOpacity() >= 15; }

    /** The two axes spanning a face, each pointing towards its positive side. */
    private static Direction[] planeAxes(Direction face) {
        return switch (face) {
            case UP, DOWN -> new Direction[]{Direction.EAST, Direction.SOUTH};
            case NORTH, SOUTH -> new Direction[]{Direction.EAST, Direction.UP};
            default -> new Direction[]{Direction.SOUTH, Direction.UP};
        };
    }

    private static float along(BakedQuad quad, int vertex, Direction axis) {
        float value = switch (axis) { case EAST -> quad.x(vertex); case UP -> quad.y(vertex); default -> quad.z(vertex); };
        return Math.max(0f, Math.min(1f, value));
    }

    private static float bilinear(float[][] corners, float u, float v) {
        float low = corners[0][0] + (corners[1][0] - corners[0][0]) * u;
        float high = corners[0][1] + (corners[1][1] - corners[0][1]) * u;
        return low + (high - low) * v;
    }

    /** Whether the quad lies flat on the side of the block it faces (so it is lit by the neighbour). */
    private static boolean onBoundary(BakedQuad quad, Direction face) {
        for (int i = 0; i < 4; i++) {
            float value = switch (face) {
                case UP -> quad.y(i) - 1; case DOWN -> quad.y(i);
                case SOUTH -> quad.z(i) - 1; case NORTH -> quad.z(i);
                case EAST -> quad.x(i) - 1; case WEST -> quad.x(i);
            };
            if (Math.abs(value) > 1e-4) return false;
        }
        return true;
    }
}
