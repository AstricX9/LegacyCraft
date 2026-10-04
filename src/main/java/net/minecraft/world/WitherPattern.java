package net.minecraft.world;

import java.util.ArrayList;
import java.util.List;

/**
 * The wither's build pattern (1.8.9 BlockSkull.getWitherPattern and
 * BlockPattern.match, HYPOTHESIS: recalled): rows "^^^", "###", "~#~" where
 * ^ is a wither skeleton skull, # soul sand and ~ air, standing or lying
 * in any direction. The search tries every corner within 2 blocks above
 * and east/south of the placed skull (BlockPos.getAllInBox), each pointing
 * ("finger") and row ("thumb") direction in EnumFacing order, rows running
 * against the thumb and columns along finger x thumb.
 */
public final class WitherPattern {
    private WitherPattern() {}

    private static final String[] ROWS = {"^^^", "###", "~#~"};
    /** EnumFacing order: down, up, north, south, west, east. */
    private static final int[][] FACINGS = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};

    /**
     * A found pattern: {@code cells[row][column]} block positions; the
     * wither appears over the bottom middle soul sand, facing along x
     * ({@code alongX}, yaw 0) or z (yaw 90).
     */
    public record Match(int[][][] cells, boolean alongX) {
        public int[] spawnBlock() { return cells[2][1]; }
        public List<int[]> all() {
            List<int[]> result = new ArrayList<>();
            for (int[][] row : cells) for (int[] cell : row) result.add(cell);
            return result;
        }
    }

    /** BlockPattern.match from the placed skull at (x, y, z), or null. */
    public static Match find(World world, int x, int y, int z) {
        for (int bx = x; bx <= x + 2; bx++)
            for (int by = y; by <= y + 2; by++)
                for (int bz = z; bz <= z + 2; bz++)
                    for (int f = 0; f < 6; f++)
                        for (int t = 0; t < 6; t++) {
                            if (t == f || t == (f ^ 1)) continue;
                            Match match = check(world, bx, by, bz, FACINGS[f], FACINGS[t]);
                            if (match != null) return match;
                        }
        return null;
    }

    /** BlockPattern.checkPatternAt / translateOffset. */
    private static Match check(World world, int x, int y, int z, int[] finger, int[] thumb) {
        int[] palm = {finger[1] * thumb[2] - finger[2] * thumb[1], finger[2] * thumb[0] - finger[0] * thumb[2],
            finger[0] * thumb[1] - finger[1] * thumb[0]};
        int[][][] cells = new int[3][3][];
        for (int column = 0; column < 3; column++)
            for (int row = 0; row < 3; row++) {
                int px = x - thumb[0] * row + palm[0] * column;
                int py = y - thumb[1] * row + palm[1] * column;
                int pz = z - thumb[2] * row + palm[2] * column;
                if (!matches(world, px, py, pz, ROWS[row].charAt(column))) return null;
                cells[row][column] = new int[] {px, py, pz};
            }
        return new Match(cells, finger[0] != 0);
    }

    private static boolean matches(World world, int x, int y, int z, char symbol) {
        if (y < 0 || y >= World.HEIGHT) return symbol == '~';
        if (world.generatedChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16)) == null) return false;
        String name = world.get(x, y, z).name().path();
        return switch (symbol) {
            case '^' -> name.equals("skull") && world.skull(x, y, z) != null && world.skull(x, y, z).type() == 1;
            case '#' -> name.equals("soul_sand");
            default -> world.get(x, y, z).material().equals("air");
        };
    }
}
