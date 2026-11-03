package net.minecraft.world;

import net.minecraft.block.BlockState;
import net.minecraft.world.chunk.Chunk;

import java.util.Random;

/**
 * End gateways (1.9 WorldGenEndGateway and TileEntityEndGateway, HYPOTHESIS:
 * recalled): a gateway block held in a bedrock frame; the ones the dragon's
 * deaths open around the main island lead out along their direction to the
 * outer islands, about 1000 blocks away, where a gateway back is built ten
 * blocks over the land (raising a small island first if there's none).
 */
public final class EndGateway {
    private EndGateway() {}

    /** WorldGenEndGateway: the gateway with bedrock above, below and around its sides, air on its level. */
    public static void build(World world, int x, int y, int z) {
        BlockState bedrock = state(world, "bedrock"), air = BlockState.of(world.registry().air(), 0);
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -2; dy <= 2; dy++)
                for (int dz = -1; dz <= 1; dz++) {
                    boolean column = dx == 0, level = dy == 0, row = dz == 0, end = Math.abs(dy) == 2;
                    BlockState state;
                    if (column && level && row) state = state(world, "end_gateway");
                    else if (level) state = air;
                    else if (end && column && row) state = bedrock;
                    else if ((column || row) && !end) state = bedrock;
                    else state = air;
                    world.setState(x + dx, y + dy, z + dz, state);
                }
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) world.notifyNeighbours(x + dx, y, z + dz);
    }

    /**
     * TileEntityEndGateway.findExitPortal: 1024 blocks out from 0,0 through
     * the gateway, stepping back inwards while there is land and outwards until
     * there is, then the highest block near the land's spawn spot, ten up.
     */
    public static int[] findExit(World world, int gatewayX, int gatewayZ, Random random) {
        double length = Math.sqrt((double) gatewayX * gatewayX + (double) gatewayZ * gatewayZ);
        double nx = length == 0 ? 1 : gatewayX / length, nz = length == 0 ? 0 : gatewayZ / length;
        double vx = nx * 1024, vz = nz * 1024;
        for (int i = 16; hasLand(world, vx, vz) && i-- > 0; ) { vx -= nx * 16; vz -= nz * 16; }
        for (int j = 16; !hasLand(world, vx, vz) && j-- > 0; ) { vx += nx * 16; vz += nz * 16; }
        int[] spot = spawnPoint(world, floor(vx) >> 4, floor(vz) >> 4);
        if (spot == null) {
            spot = new int[]{floor(vx + 0.5), 75, floor(vz + 0.5)};
            island(world, spot[0], spot[1], spot[2], random);
        }
        spot = highest(world, spot[0], spot[1], spot[2], 16, true);
        return new int[]{spot[0], spot[1] + 10, spot[2]};
    }

    /** TileEntityEndGateway.findExitPosition: where an entity comes out beside a gateway, two above the highest block within 5. */
    public static int[] standingSpot(World world, int x, int y, int z) {
        int[] top = highest(world, x, y, z, 5, false);
        return new int[]{top[0], top[1] + 2, top[2]};
    }

    /** Chunk.getTopFilledSegment() > 0: anything above the bottom section of the chunk at this spot. */
    private static boolean hasLand(World world, double x, double z) {
        Chunk chunk = world.getChunk(floor(x) >> 4, floor(z) >> 4);
        for (int section = 1; section < World.HEIGHT / 16; section++) if (chunk.section(section) != null) return true;
        return false;
    }

    /** TileEntityEndGateway.findSpawnpointInChunk: the end stone with two air above nearest the chunk's corner. */
    private static int[] spawnPoint(World world, int chunkX, int chunkZ) {
        int[] best = null;
        double bestDistance = 0;
        int ox = chunkX << 4, oz = chunkZ << 4;
        for (int x = ox; x <= ox + 15; x++)
            for (int z = oz; z <= oz + 15; z++)
                for (int y = 30; y < World.HEIGHT - 2; y++) {
                    if (!world.get(x, y, z).name().path().equals("end_stone")) continue;
                    if (!world.get(x, y + 1, z).isAir() || !world.get(x, y + 2, z).isAir()) continue;
                    double d = (double) (x - ox) * (x - ox) + (double) y * y + (double) (z - oz) * (z - oz);
                    if (best == null || d < bestDistance) { best = new int[]{x, y, z}; bestDistance = d; }
                }
        return best;
    }

    /** TileEntityEndGateway.findHighestBlock: the highest full block (bedrock only if allowed) within the radius. */
    private static int[] highest(World world, int cx, int cy, int cz, int radius, boolean allowBedrock) {
        int[] best = {cx, -1, cz};
        for (int dx = -radius; dx <= radius; dx++)
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) continue;
                for (int y = World.HEIGHT - 1; y > best[1]; y--) {
                    BlockState state = world.getState(cx + dx, y, cz + dz);
                    if (!state.block().isOpaque() || !allowBedrock && state.block().name().path().equals("bedrock")) continue;
                    best = new int[]{cx + dx, y, cz + dz};
                    break;
                }
            }
        return best[1] < 0 ? new int[]{cx, cy, cz} : best;
    }

    /** WorldGenEndIsland: a cone of end stone 4-6 wide, narrowing as it goes down. */
    static void island(World world, int x, int y, int z, Random random) {
        BlockState endStone = state(world, "end_stone");
        float size = random.nextInt(3) + 4;
        for (int dy = 0; size > 0.5f; dy--) {
            for (int dx = (int) Math.floor(-size); dx <= (int) Math.ceil(size); dx++)
                for (int dz = (int) Math.floor(-size); dz <= (int) Math.ceil(size); dz++)
                    if (dx * dx + dz * dz <= (size + 1f) * (size + 1f)) world.setState(x + dx, y + dy, z + dz, endStone);
            size -= random.nextInt(2) + 0.5f;
        }
    }

    private static int floor(double value) { return (int) Math.floor(value); }

    private static BlockState state(World world, String name) {
        return BlockState.of(world.registry().get("minecraft:" + name), 0);
    }
}
