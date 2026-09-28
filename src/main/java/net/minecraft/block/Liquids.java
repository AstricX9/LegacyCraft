package net.minecraft.block;

import net.minecraft.util.Direction;
import net.minecraft.world.IBlockAccess;

/**
 * Shared liquid rules (1.8.9 BlockLiquid and Material; HYPOTHESIS, recalled):
 * the "level" metadata (0 a source, 1-7 thinner flowing liquid, 8 and above
 * falling), the surface height it gives, the flow vector that pushes entities
 * and orients the flowing texture, and which materials count as solid or
 * block movement (liquids flow into anything that does not).
 */
public final class Liquids {
    private Liquids() {}

    /** "water" or "lava" for liquid blocks, else null. */
    public static String liquid(Block block) {
        String material = block.material();
        return material.equals("water") || material.equals("lava") ? material : null;
    }

    public static boolean isLiquid(Block block) { return liquid(block) != null; }

    /** The level of a liquid of this material at the position, or -1 for anything else (BlockLiquid.getLevel). */
    public static int level(IBlockAccess world, int x, int y, int z, String material) {
        BlockState state = world.getState(x, y, z);
        return state.block().material().equals(material) ? state.metadata() : -1;
    }

    /** As {@link #level} with falling liquid counted as 0 (getEffectiveFlowDecay). */
    public static int effectiveLevel(IBlockAccess world, int x, int y, int z, String material) {
        int level = level(world, x, y, z, material);
        return level >= 8 ? 0 : level;
    }

    /** How far below the block top the surface sits: (level + 1) / 9, falling liquid counting as level 0. */
    public static float heightPercent(int level) {
        if (level >= 8) level = 0;
        return (level + 1) / 9f;
    }

    /** Material.isSolid: false for air, liquids, plants, vines, circuits, fire, portals, carpet and snow layers. */
    public static boolean isSolid(Block block) {
        return switch (block.material()) {
            case "air", "water", "lava", "plants", "vine", "circuits", "fire", "portal", "carpet" -> false;
            case "snow" -> !block.name().path().equals("snow_layer");
            default -> true;
        };
    }

    /** Material.blocksMovement: as {@link #isSolid}, except cobwebs don't. */
    public static boolean blocksMovement(Block block) {
        return isSolid(block) && !block.material().equals("web");
    }

    /**
     * The direction a liquid flows at a position (BlockLiquid.getFlowVector),
     * normalised: towards lower neighbours, towards drops beside it, and
     * down (-6 before normalising) for falling liquid beside a solid wall.
     * Zero when still.
     */
    public static double[] flowVector(IBlockAccess world, int x, int y, int z, String material) {
        double vx = 0, vy = 0, vz = 0;
        int here = effectiveLevel(world, x, y, z, material);
        for (Direction side : Direction.HORIZONTALS) {
            int nx = x + side.xOffset, nz = z + side.zOffset;
            int there = effectiveLevel(world, nx, y, nz, material);
            if (there < 0) {
                if (!blocksMovement(world.getState(nx, y, nz).block())) {
                    there = effectiveLevel(world, nx, y - 1, nz, material);
                    if (there >= 0) {
                        int weight = there - (here - 8);
                        vx += side.xOffset * weight;
                        vz += side.zOffset * weight;
                    }
                }
            } else {
                int weight = there - here;
                vx += side.xOffset * weight;
                vz += side.zOffset * weight;
            }
        }
        if (world.getState(x, y, z).metadata() >= 8) {
            for (Direction side : Direction.HORIZONTALS) {
                int nx = x + side.xOffset, nz = z + side.zOffset;
                if (wallSolid(world, nx, y, nz, material) || wallSolid(world, nx, y + 1, nz, material)) {
                    double[] unit = normalize(vx, vy, vz);
                    vx = unit[0];
                    vy = unit[1] - 6.0;
                    vz = unit[2];
                    break;
                }
            }
        }
        return normalize(vx, vy, vz);
    }

    /** BlockLiquid.isBlockSolid for a horizontal side: another material that is solid and not ice. */
    private static boolean wallSolid(IBlockAccess world, int x, int y, int z, String material) {
        Block block = world.getState(x, y, z).block();
        if (block.material().equals(material)) return false;
        return !block.material().equals("ice") && isSolid(block);
    }

    /** Angle of the flowing texture (atan2(z, x) - pi/2), or -1000 for still liquid (BlockLiquid.getFlowDirection). */
    public static double flowDirection(IBlockAccess world, int x, int y, int z, String material) {
        double[] flow = flowVector(world, x, y, z, material);
        return flow[0] == 0 && flow[2] == 0 ? -1000.0 : Math.atan2(flow[2], flow[0]) - Math.PI / 2;
    }

    /** Vec3.normalize: vectors shorter than 1e-4 become zero. */
    public static double[] normalize(double x, double y, double z) {
        double length = Math.sqrt(x * x + y * y + z * z);
        return length < 1.0E-4 ? new double[]{0, 0, 0} : new double[]{x / length, y / length, z / length};
    }
}
