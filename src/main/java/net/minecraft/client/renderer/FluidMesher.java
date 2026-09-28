package net.minecraft.client.renderer;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Liquids;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.util.ResourceLocation;

/**
 * Water and lava faces, after 1.8.9's BlockFluidRenderer (HYPOTHESIS,
 * recalled). The top is sloped: each corner's height averages the four
 * blocks around it (a source or falling block weighing ten times more, an
 * open non-solid block pulling it down to 0, a liquid above any of them
 * lifting it to the full block), lowered 0.001. Still liquid shows the still
 * texture; moving liquid shows the flowing texture turned to its flow and
 * scaled from the middle of the 32x32 sprite. The top is also drawn facing
 * down when something non-liquid and not a full block is above nearby, the
 * sides always both ways (inset 0.001 from the block edge, using the upper
 * half of the flowing sprite down to each corner's height) and the bottom
 * only facing down. Faces are shaded 1 (top), 0.5 (bottom), 0.8 (north,
 * south) and 0.6 (west, east), tinted by the biome's water colour, and lit
 * by the brighter of the block the face looks into and the one above that.
 */
final class FluidMesher {
    private static final float INSET = 0.001f;
    private static final ResourceLocation WATER_STILL = new ResourceLocation("minecraft:textures/blocks/water_still.png"),
        WATER_FLOW = new ResourceLocation("minecraft:textures/blocks/water_flow.png"),
        LAVA_STILL = new ResourceLocation("minecraft:textures/blocks/lava_still.png"),
        LAVA_FLOW = new ResourceLocation("minecraft:textures/blocks/lava_flow.png");

    private final TextureAtlas atlas;
    private final BiomeColors biomeColors;

    FluidMesher(TextureAtlas atlas, BiomeColors biomeColors) {
        this.atlas = atlas;
        this.biomeColors = biomeColors;
    }

    void build(SectionMesher.Buffer out, RenderView view, BlockState state, int x, int y, int z,
               int startX, int startY, int startZ) {
        Block block = state.block();
        String material = Liquids.liquid(block);
        boolean water = material.equals("water");
        TextureAtlas.Tile still = atlas.tile(water ? WATER_STILL : LAVA_STILL);
        TextureAtlas.Tile flow = atlas.tile(water ? WATER_FLOW : LAVA_FLOW);
        int tint = water ? biomeColors.waterAt(view, x, z) : 0xffffff;
        float red = (tint >> 16 & 255) / 255f, green = (tint >> 8 & 255) / 255f, blue = (tint & 255) / 255f;
        float lx = x - startX, ly = y - startY, lz = z - startZ;

        boolean top = !view.getState(x, y + 1, z).block().material().equals(material);
        boolean bottom = sideVisible(view, x, y - 1, z, material);
        boolean[] sides = {
            sideVisible(view, x, y, z - 1, material), sideVisible(view, x, y, z + 1, material),
            sideVisible(view, x - 1, y, z, material), sideVisible(view, x + 1, y, z, material)};

        float h00 = height(view, x, y, z, material);
        float h01 = height(view, x, y, z + 1, material);
        float h11 = height(view, x + 1, y, z + 1, material);
        float h10 = height(view, x + 1, y, z, material);

        if (top) {
            h00 -= INSET; h01 -= INSET; h11 -= INSET; h10 -= INSET;
            double direction = Liquids.flowDirection(view, x, y, z, material);
            TextureAtlas.Tile tile = direction > -999.0 ? flow : still;
            float u0, v0, u1, v1, u2, v2, u3, v3;
            if (direction < -999.0) {
                u0 = u(tile, 0); v0 = v(tile, 0);
                u1 = u0; v1 = v(tile, 16);
                u2 = u(tile, 16); v2 = v1;
                u3 = u2; v3 = v0;
            } else {
                float sin = (float) Math.sin(direction) * 0.25f, cos = (float) Math.cos(direction) * 0.25f;
                u0 = u(tile, 8 + (-cos - sin) * 16); v0 = v(tile, 8 + (-cos + sin) * 16);
                u1 = u(tile, 8 + (-cos + sin) * 16); v1 = v(tile, 8 + (cos + sin) * 16);
                u2 = u(tile, 8 + (cos + sin) * 16); v2 = v(tile, 8 + (cos - sin) * 16);
                u3 = u(tile, 8 + (cos - sin) * 16); v3 = v(tile, 8 + (-cos - sin) * 16);
            }
            int light = light(view, x, y, z);
            out.ensure(SectionMesher.INTS_PER_QUAD * 2);
            vertex(out, lx, ly + h00, lz, red, green, blue, u0, v0, light);
            vertex(out, lx, ly + h01, lz + 1, red, green, blue, u1, v1, light);
            vertex(out, lx + 1, ly + h11, lz + 1, red, green, blue, u2, v2, light);
            vertex(out, lx + 1, ly + h10, lz, red, green, blue, u3, v3, light);
            if (openAbove(view, x, y + 1, z, material)) {
                vertex(out, lx, ly + h00, lz, red, green, blue, u0, v0, light);
                vertex(out, lx + 1, ly + h10, lz, red, green, blue, u3, v3, light);
                vertex(out, lx + 1, ly + h11, lz + 1, red, green, blue, u2, v2, light);
                vertex(out, lx, ly + h01, lz + 1, red, green, blue, u1, v1, light);
            }
        }

        if (bottom) {
            int light = light(view, x, y - 1, z);
            float r = red * .5f, g = green * .5f, b = blue * .5f;
            out.ensure(SectionMesher.INTS_PER_QUAD);
            vertex(out, lx, ly, lz + 1, r, g, b, still.u0(), still.v1(), light);
            vertex(out, lx, ly, lz, r, g, b, still.u0(), still.v0(), light);
            vertex(out, lx + 1, ly, lz, r, g, b, still.u1(), still.v0(), light);
            vertex(out, lx + 1, ly, lz + 1, r, g, b, still.u1(), still.v1(), light);
        }

        for (int side = 0; side < 4; side++) {
            if (!sides[side]) continue;
            float hA, hB, xA, xB, zA, zB;
            int nx = x, nz = z;
            switch (side) {
                case 0 -> { hA = h00; hB = h10; xA = lx; xB = lx + 1; zA = lz + INSET; zB = lz + INSET; nz--; }
                case 1 -> { hA = h11; hB = h01; xA = lx + 1; xB = lx; zA = lz + 1 - INSET; zB = lz + 1 - INSET; nz++; }
                case 2 -> { hA = h01; hB = h00; xA = lx + INSET; xB = lx + INSET; zA = lz + 1; zB = lz; nx--; }
                default -> { hA = h10; hB = h11; xA = lx + 1 - INSET; xB = lx + 1 - INSET; zA = lz; zB = lz + 1; nx++; }
            }
            float uA = u(flow, 0), uB = u(flow, 8);
            float vA = v(flow, (1 - hA) * 16 * .5f), vB = v(flow, (1 - hB) * 16 * .5f), vBottom = v(flow, 8);
            float shade = side < 2 ? .8f : .6f;
            float r = red * shade, g = green * shade, b = blue * shade;
            int light = light(view, nx, y, nz);
            out.ensure(SectionMesher.INTS_PER_QUAD * 2);
            vertex(out, xA, ly + hA, zA, r, g, b, uA, vA, light);
            vertex(out, xB, ly + hB, zB, r, g, b, uB, vB, light);
            vertex(out, xB, ly, zB, r, g, b, uB, vBottom, light);
            vertex(out, xA, ly, zA, r, g, b, uA, vBottom, light);
            vertex(out, xA, ly, zA, r, g, b, uA, vBottom, light);
            vertex(out, xB, ly, zB, r, g, b, uB, vBottom, light);
            vertex(out, xB, ly + hB, zB, r, g, b, uB, vB, light);
            vertex(out, xA, ly + hA, zA, r, g, b, uA, vA, light);
        }
    }

    /** A side or the bottom shows unless the neighbour is the same liquid or an opaque cube. */
    private static boolean sideVisible(RenderView view, int x, int y, int z, String material) {
        Block other = view.getState(x, y, z).block();
        return !other.material().equals(material) && !other.isOpaque();
    }

    /** Whether the top is also drawn facing down: some block around the one above is neither this liquid nor full. */
    private static boolean openAbove(RenderView view, int x, int y, int z, String material) {
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                Block block = view.getState(x + dx, y, z + dz).block();
                if (!block.material().equals(material) && !block.isFullCube()) return true;
            }
        return false;
    }

    /** Surface height at the north-west corner of the block at (x, z) (BlockFluidRenderer.getFluidHeight). */
    static float height(RenderView view, int x, int y, int z, String material) {
        int weight = 0;
        float drop = 0;
        for (int j = 0; j < 4; j++) {
            int bx = x - (j & 1), bz = z - (j >> 1 & 1);
            if (view.getState(bx, y + 1, bz).block().material().equals(material)) return 1f;
            BlockState state = view.getState(bx, y, bz);
            if (!state.block().material().equals(material)) {
                if (!Liquids.isSolid(state.block())) {
                    drop++;
                    weight++;
                }
            } else {
                int level = state.metadata();
                if (level >= 8 || level == 0) {
                    drop += Liquids.heightPercent(level) * 10f;
                    weight += 10;
                }
                drop += Liquids.heightPercent(level);
                weight++;
            }
        }
        return 1f - drop / weight;
    }

    /** The brighter sky and block light of this block and the one above (BlockLiquid.getMixedBrightnessForBlock). */
    private static int light(RenderView view, int x, int y, int z) {
        int sky = Math.max(view.skyLight(x, y, z), view.skyLight(x, y + 1, z));
        int block = Math.max(view.blockLight(x, y, z), view.blockLight(x, y + 1, z));
        return sky << 8 | block;
    }

    private static float u(TextureAtlas.Tile tile, float pixel) { return tile.u0() + (tile.u1() - tile.u0()) * pixel / 16f; }
    private static float v(TextureAtlas.Tile tile, float pixel) { return tile.v0() + (tile.v1() - tile.v0()) * pixel / 16f; }

    private static void vertex(SectionMesher.Buffer out, float x, float y, float z, float red, float green, float blue,
                               float u, float v, int light) {
        int[] data = out.data;
        int at = out.size;
        data[at++] = Float.floatToRawIntBits(x);
        data[at++] = Float.floatToRawIntBits(y);
        data[at++] = Float.floatToRawIntBits(z);
        data[at++] = 255 << 24 | channel(blue) << 16 | channel(green) << 8 | channel(red);
        data[at++] = Float.floatToRawIntBits(u);
        data[at++] = Float.floatToRawIntBits(v);
        data[at++] = Float.floatToRawIntBits(((light >> 8) + .5f) / 16f);
        data[at++] = Float.floatToRawIntBits(((light & 15) + .5f) / 16f);
        out.size = at;
    }

    private static int channel(float value) { return Math.max(0, Math.min(255, (int) (value * 255f + .5f))); }
}
