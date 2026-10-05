package net.minecraft.world;

/**
 * The iron golem's build pattern (HYPOTHESIS: recalled, DEV-039): a T of
 * four iron blocks, arms across the top, with a pumpkin or jack o'lantern
 * on top and nothing beside the bottom block, built along x or z. Only the
 * pumpkin being placed is checked, as the head of the T.
 */
public final class GolemPattern {
    private GolemPattern() {}

    /** The blocks to clear (head first) and whether the arms run along x; null when there is no golem here. */
    public record Match(int[][] cells, boolean alongX) {
        /** The bottom iron block, which the golem stands in. */
        public int[] feet() { return cells[2]; }
    }

    public static Match find(World world, int x, int y, int z) {
        if (y < 2 || !isHead(world, x, y, z)) return null;
        if (!is(world, x, y - 1, z, "iron_block") || !is(world, x, y - 2, z, "iron_block")) return null;
        for (boolean alongX : new boolean[] {true, false}) {
            int ax = alongX ? 1 : 0, az = alongX ? 0 : 1;
            if (!is(world, x - ax, y - 1, z - az, "iron_block") || !is(world, x + ax, y - 1, z + az, "iron_block")) continue;
            if (!air(world, x - ax, y - 2, z - az) || !air(world, x + ax, y - 2, z + az)) continue;
            return new Match(new int[][] {{x, y, z}, {x, y - 1, z}, {x, y - 2, z},
                {x - ax, y - 1, z - az}, {x + ax, y - 1, z + az}}, alongX);
        }
        return null;
    }

    private static boolean isHead(World world, int x, int y, int z) {
        return is(world, x, y, z, "pumpkin") || is(world, x, y, z, "lit_pumpkin");
    }

    private static boolean is(World world, int x, int y, int z, String name) {
        return y >= 0 && y < World.HEIGHT && world.get(x, y, z).name().path().equals(name);
    }

    private static boolean air(World world, int x, int y, int z) {
        return y >= 0 && y < World.HEIGHT && world.get(x, y, z).material().equals("air");
    }
}
