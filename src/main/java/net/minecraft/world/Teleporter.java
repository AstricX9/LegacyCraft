package net.minecraft.world;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.world.chunk.Chunk;

import java.util.Random;

/**
 * Where portal travel lands (1.8.9 Teleporter, HYPOTHESIS: recalled): the
 * nearest portal block within 128 blocks of the arrival point (its bottom
 * block), or else a new portal built at the closest spot within 16 blocks
 * that has room for a 3x4 (or failing that 1x4) frame on solid ground, or
 * failing both, a portal on a little obsidian platform at y 70 (no higher
 * than 10 below the top of the dimension). Vanilla searches every chunk in
 * range, loading and generating them; a portal can only exist where chunks
 * already are, so only existing and saved chunks are searched here.
 */
public final class Teleporter {
    private Teleporter() {}

    /** Teleporter.placeInPortal: the spot to stand on {x, y, z}, finding or making a portal. */
    public static double[] placeInPortal(World world, double x, double y, double z, Random random) {
        double[] found = existingPortal(world, x, y, z);
        if (found != null) return found;
        makePortal(world, x, y, z, random);
        found = existingPortal(world, x, y, z);
        return found != null ? found : new double[] {x, y, z};
    }

    /**
     * Teleporter.placeInExistingPortal: the nearest portal column's bottom
     * block, standing in the middle of the portal's width.
     */
    static double[] existingPortal(World world, double px, double py, double pz) {
        Block portal = world.registry().get("minecraft:portal");
        if (portal == null) return null;
        int portalId = portal.registryId();
        int bx = (int) Math.floor(px), by = (int) Math.floor(py), bz = (int) Math.floor(pz);
        int top = world.dimension().actualHeight - 1;
        double best = -1;
        int fx = 0, fy = 0, fz = 0;
        int minChunkX = (bx - 128) >> 4, maxChunkX = (bx + 128) >> 4, minChunkZ = (bz - 128) >> 4, maxChunkZ = (bz + 128) >> 4;
        for (int cx = minChunkX; cx <= maxChunkX; cx++)
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                Chunk chunk = world.generatedChunk(cx, cz);
                if (chunk == null) {
                    World.ChunkSource source = world.chunkSource();
                    if (source == null || !source.has(cx, cz)) continue;
                    chunk = world.getChunk(cx, cz);
                }
                for (int lx = 0; lx < 16; lx++)
                    for (int lz = 0; lz < 16; lz++) {
                        int x = cx * 16 + lx, z = cz * 16 + lz;
                        if (x < bx - 128 || x > bx + 128 || z < bz - 128 || z > bz + 128) continue;
                        for (int y = top; y >= 0; y--) {
                            if (chunk.get(lx, y, lz) >>> BlockState.METADATA_BITS != portalId) continue;
                            while (y > 0 && chunk.get(lx, y - 1, lz) >>> BlockState.METADATA_BITS == portalId) y--;
                            double dx = x - bx, dy = y - by, dz = z - bz;
                            double distance = dx * dx + dy * dy + dz * dz;
                            if (best < 0 || distance < best) {
                                best = distance;
                                fx = x; fy = y; fz = z;
                            }
                        }
                    }
            }
        if (best < 0) return null;
        // Stand in the middle of the portal: move half a block toward a portal neighbour along its axis.
        double sx = fx + 0.5, sz = fz + 0.5;
        if (world.get(fx - 1, fy, fz) == portal) sx -= 0.5;
        else if (world.get(fx + 1, fy, fz) == portal) sx += 0.5;
        else if (world.get(fx, fy, fz - 1) == portal) sz -= 0.5;
        else if (world.get(fx, fy, fz + 1) == portal) sz += 0.5;
        return new double[] {sx, fy, sz};
    }

    /** Teleporter.makePortal. */
    static void makePortal(World world, double px, double py, double pz, Random random) {
        int height = world.dimension().actualHeight;
        int j = (int) Math.floor(px), k = (int) Math.floor(py), l = (int) Math.floor(pz);
        int i1 = j, j1 = k, k1 = l, l1 = 0;
        int i2 = random.nextInt(4);
        double best = -1;
        for (int x = j - 16; x <= j + 16; x++) {
            double dx = x + 0.5 - px;
            for (int z = l - 16; z <= l + 16; z++) {
                double dz = z + 0.5 - pz;
                column:
                for (int y = height - 1; y >= 0; y--) {
                    if (!isAir(world, x, y, z)) continue;
                    while (y > 0 && isAir(world, x, y - 1, z)) y--;
                    for (int k3 = i2; k3 < i2 + 4; k3++) {
                        int l3 = k3 % 2, i4 = 1 - l3;
                        if (k3 % 4 >= 2) {
                            l3 = -l3;
                            i4 = -i4;
                        }
                        for (int j4 = 0; j4 < 3; j4++)
                            for (int k4 = 0; k4 < 4; k4++)
                                for (int l4 = -1; l4 < 4; l4++) {
                                    int bx = x + (k4 - 1) * l3 + j4 * i4, by = y + l4, bz = z + (k4 - 1) * i4 - j4 * l3;
                                    if (l4 < 0 && !isSolid(world, bx, by, bz) || l4 >= 0 && !isAir(world, bx, by, bz)) continue column;
                                }
                        double dy = y + 0.5 - py;
                        double distance = dx * dx + dy * dy + dz * dz;
                        if (best < 0 || distance < best) {
                            best = distance;
                            i1 = x; j1 = y; k1 = z;
                            l1 = k3 % 4;
                        }
                    }
                }
            }
        }
        if (best < 0) {
            for (int x = j - 16; x <= j + 16; x++) {
                double dx = x + 0.5 - px;
                for (int z = l - 16; z <= l + 16; z++) {
                    double dz = z + 0.5 - pz;
                    column:
                    for (int y = height - 1; y >= 0; y--) {
                        if (!isAir(world, x, y, z)) continue;
                        while (y > 0 && isAir(world, x, y - 1, z)) y--;
                        for (int k7 = i2; k7 < i2 + 2; k7++) {
                            int k8 = k7 % 2, j9 = 1 - k8;
                            for (int j10 = 0; j10 < 4; j10++)
                                for (int j11 = -1; j11 < 4; j11++) {
                                    int bx = x + (j10 - 1) * k8, by = y + j11, bz = z + (j10 - 1) * j9;
                                    if (j11 < 0 && !isSolid(world, bx, by, bz) || j11 >= 0 && !isAir(world, bx, by, bz)) continue column;
                                }
                            double dy = y + 0.5 - py;
                            double distance = dx * dx + dy * dy + dz * dz;
                            if (best < 0 || distance < best) {
                                best = distance;
                                i1 = x; j1 = y; k1 = z;
                                l1 = k7 % 2;
                            }
                        }
                    }
                }
            }
        }
        int ox = i1, oy = j1, oz = k1;
        int l6 = l1 % 2, i3 = 1 - l6;
        if (l1 % 4 >= 2) {
            l6 = -l6;
            i3 = -i3;
        }
        BlockState obsidian = BlockState.defaultOf(world.registry().get("minecraft:obsidian"));
        BlockState air = BlockState.defaultOf(world.registry().air());
        if (best < 0) {
            oy = Math.max(70, Math.min(height - 10, j1));
            for (int j7 = -1; j7 <= 1; j7++)
                for (int l7 = 1; l7 < 3; l7++)
                    for (int k8 = -1; k8 < 3; k8++)
                        world.setState(ox + (l7 - 1) * l6 + j7 * i3, oy + k8, oz + (l7 - 1) * i3 - j7 * l6, k8 < 0 ? obsidian : air);
        }
        BlockState portal = world.registry().get("minecraft:portal").defaultState().with("axis", l6 != 0 ? "x" : "z");
        for (int i8 = 0; i8 < 4; i8++) {
            for (int l8 = 0; l8 < 4; l8++)
                for (int l9 = -1; l9 < 4; l9++) {
                    boolean frame = l8 == 0 || l8 == 3 || l9 == -1 || l9 == 3;
                    world.setState(ox + (l8 - 1) * l6, oy + l9, oz + (l8 - 1) * i3, frame ? obsidian : portal);
                }
        }
    }

    private static boolean isAir(World world, int x, int y, int z) { return y >= 0 && y < World.HEIGHT && world.get(x, y, z).isAir(); }
    private static boolean isSolid(World world, int x, int y, int z) { return y >= 0 && y < World.HEIGHT && world.get(x, y, z).isSolid(); }
}
