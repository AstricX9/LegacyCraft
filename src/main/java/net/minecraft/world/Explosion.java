package net.minecraft.world;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.state.ActualState;
import net.minecraft.util.AxisAlignedBB;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * The block side of an explosion (1.8.9 Explosion.doExplosionA, HYPOTHESIS:
 * recalled constants): 1352 rays from the centre through the surface of a
 * 16x16x16 grid, each starting with {@code size x (0.7..1.3)} strength and
 * losing 0.225 per 0.3-block step plus {@code (resistance + 0.3) x 0.3} for
 * every non-air block it passes; a block is destroyed if the ray still has
 * strength there. Also the exposure an entity has to the centre
 * (World.getBlockDensity) and the line-of-sight test both use.
 */
public final class Explosion {
    public final double x, y, z;
    public final float size;
    private final Set<Long> affected = new LinkedHashSet<>();
    private final List<int[]> affectedList = new ArrayList<>();
    /**
     * The most a block may resist this blast (Entity.getExplosionResistance):
     * a blue wither skull caps it at 0.8, except bedrock, end portals, end
     * portal frames and command blocks.
     */
    public float resistanceCap = Float.MAX_VALUE;
    private static final java.util.Set<String> UNCAPPED = java.util.Set.of("bedrock", "end_portal", "end_portal_frame", "command_block");

    public Explosion(double x, double y, double z, float size) {
        this.x = x; this.y = y; this.z = z;
        this.size = size;
    }

    /** The blocks the rays reach (doExplosionA's affectedBlockPositions), in the order found. */
    public List<int[]> affectedBlocks() { return affectedList; }

    public void computeAffectedBlocks(World world, Random rand) {
        for (int i = 0; i < 16; i++)
            for (int j = 0; j < 16; j++)
                for (int k = 0; k < 16; k++) {
                    if (i != 0 && i != 15 && j != 0 && j != 15 && k != 0 && k != 15) continue;
                    double dx = i / 15.0 * 2.0 - 1.0, dy = j / 15.0 * 2.0 - 1.0, dz = k / 15.0 * 2.0 - 1.0;
                    double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    dx /= length; dy /= length; dz /= length;
                    float strength = size * (0.7f + rand.nextFloat() * 0.6f);
                    double px = x, py = y, pz = z;
                    for (; strength > 0f; strength -= 0.22500001f) {
                        int bx = floor(px), by = floor(py), bz = floor(pz);
                        if (by >= 0 && by < World.HEIGHT) {
                            Block block = world.get(bx, by, bz);
                            if (!block.isAir()) {
                                float resistance = resistance(block);
                                if (resistance > resistanceCap && !UNCAPPED.contains(block.name().path())) resistance = resistanceCap;
                                strength -= (resistance + 0.3f) * 0.3f;
                            }
                            if (strength > 0f) {
                                long key = ((long) (bx & 0x3FFFFFF) << 38) | ((long) (by & 0xFFF) << 26) | (bz & 0x3FFFFFF);
                                if (affected.add(key)) affectedList.add(new int[] {bx, by, bz});
                            }
                        }
                        px += dx * 0.30000001192092896;
                        py += dy * 0.30000001192092896;
                        pz += dz * 0.30000001192092896;
                    }
                }
    }

    /**
     * Block.getExplosionResistance: a fifth of the block's resistance, which
     * is the larger of 5 x hardness and 3 x the value its registration sets
     * (stone 10, obsidian 2000, bedrock 6000000, water and lava 100...).
     */
    public static float resistance(Block block) {
        String name = block.name().path();
        float hardness = block.hardness();
        Float set = SET_RESISTANCE.get(name);
        float stored = Math.max(hardness * 5f, 0f);
        if (set != null) stored = Math.max(stored, set * 3f);
        if (hardness < 0 && set == null) stored = 18000000f;
        return stored / 5f;
    }

    /** setResistance arguments from Block.registerBlocks (HYPOTHESIS: recalled). */
    private static final Map<String, Float> SET_RESISTANCE = Map.ofEntries(
        Map.entry("stone", 10f), Map.entry("cobblestone", 10f), Map.entry("planks", 5f), Map.entry("bedrock", 6000000f),
        Map.entry("water", 100f), Map.entry("flowing_water", 100f), Map.entry("lava", 100f), Map.entry("flowing_lava", 100f),
        Map.entry("gold_ore", 5f), Map.entry("iron_ore", 5f), Map.entry("coal_ore", 5f), Map.entry("lapis_ore", 5f),
        Map.entry("lapis_block", 5f), Map.entry("gold_block", 10f), Map.entry("iron_block", 10f),
        Map.entry("double_stone_slab", 10f), Map.entry("stone_slab", 10f), Map.entry("double_wooden_slab", 5f),
        Map.entry("wooden_slab", 5f), Map.entry("brick_block", 10f), Map.entry("mossy_cobblestone", 10f),
        Map.entry("obsidian", 2000f), Map.entry("oak_stairs", 5f), Map.entry("spruce_stairs", 5f), Map.entry("birch_stairs", 5f),
        Map.entry("jungle_stairs", 5f), Map.entry("acacia_stairs", 5f), Map.entry("dark_oak_stairs", 5f),
        Map.entry("stone_stairs", 10f), Map.entry("brick_stairs", 10f), Map.entry("stone_brick_stairs", 10f),
        Map.entry("nether_brick_stairs", 10f), Map.entry("sandstone_stairs", 0f), Map.entry("quartz_stairs", 0f),
        Map.entry("diamond_ore", 5f), Map.entry("diamond_block", 10f), Map.entry("iron_door", 5f), Map.entry("redstone_ore", 5f),
        Map.entry("lit_redstone_ore", 5f), Map.entry("jukebox", 10f), Map.entry("fence", 5f), Map.entry("spruce_fence", 5f),
        Map.entry("birch_fence", 5f), Map.entry("jungle_fence", 5f), Map.entry("dark_oak_fence", 5f), Map.entry("acacia_fence", 5f),
        Map.entry("fence_gate", 5f), Map.entry("spruce_fence_gate", 5f), Map.entry("birch_fence_gate", 5f),
        Map.entry("jungle_fence_gate", 5f), Map.entry("dark_oak_fence_gate", 5f), Map.entry("acacia_fence_gate", 5f),
        Map.entry("stonebrick", 10f), Map.entry("iron_bars", 10f), Map.entry("nether_brick", 10f), Map.entry("nether_brick_fence", 10f),
        Map.entry("enchanting_table", 2000f), Map.entry("end_portal_frame", 6000000f), Map.entry("end_stone", 15f),
        Map.entry("dragon_egg", 15f), Map.entry("ender_chest", 1000f), Map.entry("emerald_ore", 5f), Map.entry("emerald_block", 10f),
        Map.entry("cobblestone_wall", 10f), Map.entry("anvil", 2000f), Map.entry("redstone_block", 10f), Map.entry("hopper", 8f),
        Map.entry("stained_hardened_clay", 7f), Map.entry("hardened_clay", 7f), Map.entry("coal_block", 10f),
        Map.entry("prismarine", 10f), Map.entry("iron_trapdoor", 5f), Map.entry("command_block", 6000000f),
        Map.entry("barrier", 6000001.5f), Map.entry("end_portal", 6000000f), Map.entry("portal", 0f), Map.entry("web", 0f));

    // ---- line of sight -------------------------------------------------------------------------

    /**
     * World.rayTraceBlocks between two points: whether a block that can be
     * aimed at (not air, liquids or fire) is in the way.
     */
    public static boolean blocked(World world, double x1, double y1, double z1, double x2, double y2, double z2) {
        double dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0E-7) return false;
        dx /= length; dy /= length; dz /= length;
        int bx = floor(x1), by = floor(y1), bz = floor(z1);
        int endX = floor(x2), endY = floor(y2), endZ = floor(z2);
        int stepX = dx > 0 ? 1 : -1, stepY = dy > 0 ? 1 : -1, stepZ = dz > 0 ? 1 : -1;
        double deltaX = dx == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / dx);
        double deltaY = dy == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / dy);
        double deltaZ = dz == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / dz);
        double nextX = dx == 0 ? Double.POSITIVE_INFINITY : (dx > 0 ? bx + 1 - x1 : x1 - bx) * deltaX;
        double nextY = dy == 0 ? Double.POSITIVE_INFINITY : (dy > 0 ? by + 1 - y1 : y1 - by) * deltaY;
        double nextZ = dz == 0 ? Double.POSITIVE_INFINITY : (dz > 0 ? bz + 1 - z1 : z1 - bz) * deltaZ;
        for (int guard = 0; guard < 200; guard++) {
            if (hits(world, bx, by, bz, x1, y1, z1, dx, dy, dz, length)) return true;
            if (bx == endX && by == endY && bz == endZ) return false;
            if (nextX < nextY && nextX < nextZ) {
                if (nextX > length) return false;
                bx += stepX;
                nextX += deltaX;
            } else if (nextY < nextZ) {
                if (nextY > length) return false;
                by += stepY;
                nextY += deltaY;
            } else {
                if (nextZ > length) return false;
                bz += stepZ;
                nextZ += deltaZ;
            }
        }
        return false;
    }

    private static boolean hits(World world, int bx, int by, int bz, double ox, double oy, double oz,
                                double dx, double dy, double dz, double length) {
        if (by < 0 || by >= World.HEIGHT || world.generatedChunk(bx >> 4, bz >> 4) == null) return false;
        BlockState stored = world.getState(bx, by, bz);
        Block block = stored.block();
        if (block.isAir() || !block.isTargetable()) return false;
        ActualState state = block.actualState(world, bx, by, bz, stored);
        AxisAlignedBB local = block.selectionBox(state, world, bx, by, bz);
        if (local == null) return false;
        AxisAlignedBB box = local.offset(bx, by, bz);
        if (ox >= box.minX() && ox <= box.maxX() && oy >= box.minY() && oy <= box.maxY() && oz >= box.minZ() && oz <= box.maxZ())
            return true;
        AxisAlignedBB.RayHit entry = box.rayEntry(ox, oy, oz, dx, dy, dz);
        return entry != null && entry.distance() <= length;
    }

    /**
     * World.getBlockDensity: the share of points on a grid through the box
     * (about every half block) that see the centre.
     */
    public static float exposure(World world, double cx, double cy, double cz, AxisAlignedBB box) {
        double stepX = 1.0 / ((box.maxX() - box.minX()) * 2.0 + 1.0);
        double stepY = 1.0 / ((box.maxY() - box.minY()) * 2.0 + 1.0);
        double stepZ = 1.0 / ((box.maxZ() - box.minZ()) * 2.0 + 1.0);
        double offsetX = (1.0 - Math.floor(1.0 / stepX) * stepX) / 2.0;
        double offsetZ = (1.0 - Math.floor(1.0 / stepZ) * stepZ) / 2.0;
        if (stepX < 0 || stepY < 0 || stepZ < 0) return 0f;
        int seen = 0, total = 0;
        for (float fx = 0f; fx <= 1f; fx = (float) (fx + stepX))
            for (float fy = 0f; fy <= 1f; fy = (float) (fy + stepY))
                for (float fz = 0f; fz <= 1f; fz = (float) (fz + stepZ)) {
                    double px = box.minX() + (box.maxX() - box.minX()) * fx;
                    double py = box.minY() + (box.maxY() - box.minY()) * fy;
                    double pz = box.minZ() + (box.maxZ() - box.minZ()) * fz;
                    if (!blocked(world, px + offsetX, py, pz + offsetZ, cx, cy, cz)) seen++;
                    total++;
                }
        return total == 0 ? 0f : (float) seen / total;
    }

    private static int floor(double value) { return (int) Math.floor(value); }
}
