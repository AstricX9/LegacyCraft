package net.minecraft.client.renderer;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Liquids;
import net.minecraft.block.state.ActualState;
import net.minecraft.client.renderer.block.model.BakedModel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModelManager;
import net.minecraft.client.renderer.entity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.util.Direction;

import java.util.Arrays;
import java.util.Set;

/**
 * Turns a {@link SectionSnapshot} into vertex data for one 16x16x16 section:
 * every visible face of every block, shaded and lit exactly as before, split
 * into layers like vanilla's block render layers: solid and mipmapped
 * cut-out blocks (leaves, grass sides, panes, hoppers) in the first; the
 * CUTOUT layer (plants, torches, rails, glass, doors...), drawn with the
 * alpha test but without mipmaps, in the second; water, ice, stained glass,
 * slime and portals in the third, drawn after all the rest and sorted back
 * to front. Runs on a worker thread; it only reads
 * the snapshot and the loaded models and textures.
 */
final class SectionMesher {
    /**
     * x, y, z (floats, relative to the section corner), RGBA colour (4 bytes: tint, face shade and
     * ambient occlusion), u, v (floats, block atlas), then the light as lightmap coordinates (floats,
     * (level + 0.5) / 16 for sky and block light) so the time of day can change without rebuilding.
     */
    static final int INTS_PER_VERTEX = 8, INTS_PER_QUAD = 4 * INTS_PER_VERTEX;
    /**
     * With a shader pack each vertex carries three more ints: the face normal (signed bytes x, y, z), the
     * pack's block ID (mc_Entity, a short) and the quad's texture centre (mc_midTexCoord, two normalized
     * unsigned shorts); faces aren't shaded by direction and the ambient occlusion goes in the alpha, as
     * OptiFine does with separateAo.
     */
    static final int SHADER_INTS_PER_VERTEX = 11;
    /** The pack the terrain is meshed for, or null for the plain layout: set on the render thread, read by the workers. */
    static volatile net.minecraft.client.shaders.ShaderPack shaderPack;
    /** Vanilla's TRANSLUCENT block layer. */
    private static final Set<String> TRANSLUCENT = Set.of("water", "flowing_water", "ice", "stained_glass",
        "stained_glass_pane", "slime", "portal");

    /**
     * Vanilla's CUTOUT layer (Block.getBlockLayer, HYPOTHESIS: recalled per block). The world
     * renderer turns mipmapping off for it, which is why tall grass and flowers stay crisp in
     * the distance while leaves fade.
     */
    private static final Set<String> CUTOUT = Set.of("sapling", "glass", "golden_rail", "detector_rail", "web",
        "tallgrass", "deadbush", "yellow_flower", "red_flower", "brown_mushroom", "red_mushroom", "torch", "fire",
        "mob_spawner", "redstone_wire", "wheat", "wooden_door", "ladder", "rail", "lever", "iron_door",
        "unlit_redstone_torch", "redstone_torch", "cactus", "reeds", "unpowered_repeater", "powered_repeater",
        "trapdoor", "pumpkin_stem", "melon_stem", "vine", "waterlily", "nether_wart", "brewing_stand", "cocoa",
        "tripwire_hook", "beacon", "flower_pot", "carrots", "potatoes", "unpowered_comparator", "powered_comparator",
        "activator_rail", "iron_trapdoor", "double_plant", "spruce_door", "birch_door", "jungle_door", "acacia_door",
        "dark_oak_door");

    /** Growable vertex store. */
    static final class Buffer {
        int[] data = new int[INTS_PER_QUAD * 64];
        int size;
        /** Ints per vertex: {@link #INTS_PER_VERTEX}, or {@link #SHADER_INTS_PER_VERTEX} once {@link #widen}ed. */
        int stride = INTS_PER_VERTEX;
        /** Shader block ID of each quad, filled by {@link #tag}; quads before {@code tagged} have theirs. */
        private int[] ids = new int[64];
        private int tagged;

        /** Gives the quads added since the last call the shader block ID {@code id}. */
        void tag(int id) {
            int quads = quads();
            if (quads > ids.length) ids = Arrays.copyOf(ids, Math.max(quads, ids.length * 2));
            Arrays.fill(ids, tagged, quads, id);
            tagged = quads;
        }

        /** Rewrites the plain vertices in the shader layout, adding each quad's normal, block ID and texture centre. */
        void widen() {
            int quads = quads();
            int[] wide = new int[quads * 4 * SHADER_INTS_PER_VERTEX];
            for (int q = 0; q < quads; q++) {
                int base = q * INTS_PER_QUAD;
                float[] p = new float[12];
                float midU = 0, midV = 0;
                for (int v = 0; v < 4; v++) {
                    for (int c = 0; c < 3; c++) p[v * 3 + c] = Float.intBitsToFloat(data[base + v * INTS_PER_VERTEX + c]);
                    midU += Float.intBitsToFloat(data[base + v * INTS_PER_VERTEX + 4]) / 4;
                    midV += Float.intBitsToFloat(data[base + v * INTS_PER_VERTEX + 5]) / 4;
                }
                // The normal from the diagonals, so quads with a repeated corner still get one.
                float ax = p[6] - p[0], ay = p[7] - p[1], az = p[8] - p[2], bx = p[9] - p[3], by = p[10] - p[4], bz = p[11] - p[5];
                float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
                float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (length > 0) { nx /= length; ny /= length; nz /= length; } else ny = 1;
                int normal = (Math.round(nz * 127) & 255) << 16 | (Math.round(ny * 127) & 255) << 8 | (Math.round(nx * 127) & 255);
                int id = q < tagged ? ids[q] : 0;
                int mid = (Math.round(Math.max(0, Math.min(1, midV)) * 65535) << 16) | Math.round(Math.max(0, Math.min(1, midU)) * 65535);
                for (int v = 0; v < 4; v++) {
                    int to = (q * 4 + v) * SHADER_INTS_PER_VERTEX;
                    System.arraycopy(data, base + v * INTS_PER_VERTEX, wide, to, INTS_PER_VERTEX);
                    wide[to + 8] = normal;
                    wide[to + 9] = id & 0xffff;
                    wide[to + 10] = mid;
                }
            }
            data = wide;
            size = wide.length;
            stride = SHADER_INTS_PER_VERTEX;
        }

        void ensure(int more) {
            if (size + more > data.length) data = Arrays.copyOf(data, Math.max(data.length * 2, size + more));
        }

        int quads() { return size / (stride * 4); }
    }

    /** A finished section mesh: the three layers, ready to upload, and where its tile-entity-drawn blocks are. */
    static final class Mesh {
        final Buffer opaque = new Buffer(), cutout = new Buffer(), translucent = new Buffer();
        /** x, y, z of each chest, sign, banner and end portal ({@link BlockEntityRenderer#draws}). */
        int[] blockEntities = new int[0];
        int blockEntityCount;
        boolean isEmpty() { return opaque.size == 0 && cutout.size == 0 && translucent.size == 0 && blockEntityCount == 0; }

        void addBlockEntity(int x, int y, int z) {
            if (blockEntityCount * 3 + 3 > blockEntities.length)
                blockEntities = Arrays.copyOf(blockEntities, Math.max(12, blockEntities.length * 2));
            blockEntities[blockEntityCount * 3] = x;
            blockEntities[blockEntityCount * 3 + 1] = y;
            blockEntities[blockEntityCount * 3 + 2] = z;
            blockEntityCount++;
        }
    }

    private final TextureAtlas atlas;
    private final BlockModelManager models;
    private final BiomeColors biomeColors;
    private final FluidMesher fluids;

    SectionMesher(TextureAtlas atlas, BlockModelManager models, BiomeColors biomeColors) {
        this.atlas = atlas;
        this.models = models;
        this.biomeColors = biomeColors;
        fluids = new FluidMesher(atlas, biomeColors);
    }

    Mesh build(SectionSnapshot view, int sectionX, int sectionY, int sectionZ) {
        Mesh mesh = new Mesh();
        net.minecraft.client.shaders.ShaderPack shader = shaderPack;
        BlockLighting lighting = new BlockLighting(view);
        BlockLighting.Result light = new BlockLighting.Result();
        int startX = sectionX * 16, startY = sectionY * 16, startZ = sectionZ * 16;
        for (int y = startY; y < startY + 16; y++) {
            for (int z = startZ; z < startZ + 16; z++) {
                for (int x = startX; x < startX + 16; x++) {
                    int packed = view.packed(x, y, z);
                    if (packed == 0) continue;
                    BlockState state = view.getState(x, y, z);
                    Block block = state.block();
                    if (block.renderType() == Block.RenderType.INVISIBLE) continue;
                    // Tile-entity blocks are drawn each frame by their own renderers, not baked; mob heads
                    // (SkullRenderer) are tracked by the world, the rest by where the mesher found them.
                    if (block.renderType() == Block.RenderType.ENTITY) {
                        if (BlockEntityRenderer.draws(block.name().path())) mesh.addBlockEntity(x, y, z);
                        continue;
                    }
                    if (block.renderType() == Block.RenderType.FLUID && Liquids.isLiquid(block)) {
                        Buffer target = TRANSLUCENT.contains(block.name().path()) ? mesh.translucent : mesh.opaque;
                        fluids.build(target, view, state, x, y, z, startX, startY, startZ);
                        if (shader != null) target.tag(shader.blockId(block.name().path(), state::get));
                        continue;
                    }
                    ActualState actual = block.actualState(view, x, y, z, state);
                    BakedModel model;
                    // Model lookup may fill caches lazily; workers take turns rather than risk a torn map.
                    synchronized (models) { model = models.getModel(actual, x, y, z); }
                    if (model == null) continue;
                    String name = block.name().path();
                    Buffer target = TRANSLUCENT.contains(name) ? mesh.translucent : CUTOUT.contains(name) ? mesh.cutout : mesh.opaque;
                    for (BakedQuad quad : model.quads()) {
                        Direction cull = quad.cullFace();
                        if (cull != null && !faceVisible(state, view.getState(x + cull.xOffset, y + cull.yOffset, z + cull.zOffset)))
                            continue;
                        lighting.light(block, model, quad, x, y, z, light);
                        emitQuad(target, view, x, y, z, startX, startY, startZ, actual, quad, light, shader != null);
                    }
                    if (shader != null) target.tag(shader.blockId(name, actual::get));
                }
            }
        }
        if (shader != null) {
            mesh.opaque.widen();
            mesh.cutout.widen();
            mesh.translucent.widen();
        }
        return mesh;
    }

    static boolean faceVisible(BlockState self, BlockState neighbor) {
        Block other = neighbor.block();
        if (other.isOpaque()) return false;
        return switch (self.block().cullSame()) {
            case BLOCK -> other != self.block();
            case STATE -> !neighbor.equals(self);
            case MATERIAL -> !other.material().equals(self.block().material());
            case NONE -> true;
        };
    }

    private void emitQuad(Buffer out, RenderView view, int x, int y, int z, int startX, int startY, int startZ,
                          ActualState state, BakedQuad quad, BlockLighting.Result light, boolean shader) {
        Block block = state.block();
        TextureAtlas.Tile tile = atlas.tile(quad.texture());
        Direction face = quad.face();
        float shade = !quad.shade() || shader ? 1f : face == Direction.UP ? 1f : face == Direction.DOWN ? .5f
            : (face == Direction.NORTH || face == Direction.SOUTH) ? .8f : .6f;
        boolean water = block.material().equals("water");
        int tint = quad.tintIndex() >= 0 || water ? color(view, state, x, z) : 0xffffff;
        float red = ((tint >> 16) & 255) / 255f * shade, green = ((tint >> 8) & 255) / 255f * shade, blue = (tint & 255) / 255f * shade;
        int alpha = water ? Math.round(.58f * 255) : 255;
        out.ensure(INTS_PER_QUAD);
        int[] data = out.data;
        int at = out.size;
        for (int i = 0; i < 4; i++) {
            float occlusion = light.occlusion[i];
            data[at++] = Float.floatToRawIntBits(x - startX + quad.x(i));
            data[at++] = Float.floatToRawIntBits(y - startY + quad.y(i));
            data[at++] = Float.floatToRawIntBits(z - startZ + quad.z(i));
            data[at++] = shader && !water ? rgba(red, green, blue, channel(occlusion))
                : rgba(red * occlusion, green * occlusion, blue * occlusion, alpha);
            data[at++] = Float.floatToRawIntBits(mix(tile.u0(), tile.u1(), quad.u(i)));
            data[at++] = Float.floatToRawIntBits(mix(tile.v0(), tile.v1(), quad.v(i)));
            data[at++] = Float.floatToRawIntBits((light.sky[i] + .5f) / 16f);
            data[at++] = Float.floatToRawIntBits((light.block[i] + .5f) / 16f);
        }
        out.size = at;
    }

    /** Bytes R, G, B, A in memory order (the int is written little-endian). */
    private static int rgba(float red, float green, float blue, int alpha) {
        return alpha << 24 | channel(blue) << 16 | channel(green) << 8 | channel(red);
    }

    private static int channel(float value) { return Math.max(0, Math.min(255, (int) (value * 255f + .5f))); }

    /** Colour multiplier for tinted faces, from the biome colour maps blended over the surrounding columns. */
    private int color(RenderView view, ActualState state, int x, int z) {
        Block block = state.block();
        return switch (block.name().path()) {
            case "grass", "tallgrass" -> biomeColors.grassAt(view, x, z);
            case "double_plant" -> state.get("variant").equals("double_grass") || state.get("variant").equals("double_fern")
                ? biomeColors.grassAt(view, x, z) : 0xffffff;
            case "leaves" -> switch (state.get("variant")) {
                case "spruce" -> 0x619961;
                case "birch" -> 0x80a755;
                default -> biomeColors.foliageAt(view, x, z);
            };
            case "leaves2", "vine" -> biomeColors.foliageAt(view, x, z);
            case "water", "flowing_water" -> biomeColors.waterAt(view, x, z);
            case "waterlily" -> 0x208030;
            case "pumpkin_stem", "melon_stem" -> {
                int age = Integer.parseInt(state.get("age"));
                yield (age * 32) << 16 | (255 - age * 8) << 8 | (age * 4);
            }
            case "redstone_wire" -> {
                int power = Integer.parseInt(state.get("power"));
                float strength = power / 15f;
                float r = power == 0 ? .3f : strength * .6f + .4f;
                float g = Math.max(0f, strength * strength * .7f - .5f), b = Math.max(0f, strength * strength * .6f - .7f);
                yield (int) (r * 255) << 16 | (int) (g * 255) << 8 | (int) (b * 255);
            }
            default -> block.tintColor();
        };
    }

    private static float mix(float a, float b, float t) { return a + (b - a) * t; }

    /**
     * Reorders a translucent layer's quads farthest-first from a viewpoint
     * (section-local coordinates), so blending composites correctly.
     */
    static void sortBackToFront(Buffer buffer, float viewX, float viewY, float viewZ) {
        int quads = buffer.quads();
        if (quads < 2) return;
        float[] distance = new float[quads];
        Integer[] order = new Integer[quads];
        for (int q = 0; q < quads; q++) {
            int base = q * buffer.stride * 4;
            float cx = 0, cy = 0, cz = 0;
            for (int v = 0; v < 4; v++) {
                int at = base + v * buffer.stride;
                cx += Float.intBitsToFloat(buffer.data[at]);
                cy += Float.intBitsToFloat(buffer.data[at + 1]);
                cz += Float.intBitsToFloat(buffer.data[at + 2]);
            }
            float dx = cx / 4 - viewX, dy = cy / 4 - viewY, dz = cz / 4 - viewZ;
            distance[q] = dx * dx + dy * dy + dz * dz;
            order[q] = q;
        }
        Arrays.sort(order, (a, b) -> Float.compare(distance[b], distance[a]));
        int[] sorted = new int[buffer.size];
        int quadInts = buffer.stride * 4;
        for (int q = 0; q < quads; q++)
            System.arraycopy(buffer.data, order[q] * quadInts, sorted, q * quadInts, quadInts);
        buffer.data = sorted;
    }
}
