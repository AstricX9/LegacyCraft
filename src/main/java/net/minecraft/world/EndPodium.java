package net.minecraft.world;

import net.minecraft.block.BlockState;

/**
 * The End's exit portal (1.9 WorldGenEndPodium, HYPOTHESIS: recalled): a
 * bedrock bowl of radius 3.5 on the main island's surface at 0,0, its 2.5
 * centre filled with end portal when active, a four-high bedrock pillar in
 * the middle with a torch on each side two blocks up.
 */
public final class EndPodium {
    private EndPodium() {}

    /** The podium's centre height: one above the surface at 0,0 (getTopSolidOrLiquidBlock), at least y 60 over the void. */
    public static int centreY(World world) {
        int top = world.surfaceHeight(0, 0);
        return top > 0 ? top + 1 : 60;
    }

    /** Whether a podium is already there: the End has no bedrock of its own, so any at 0,0 is its pillar. */
    public static boolean exists(World world) {
        return builtY(world) >= 0;
    }

    /** The centre height of the podium already built (one above the bedrock under its pillar), or -1. */
    public static int builtY(World world) {
        for (int y = 0; y < World.HEIGHT; y++) if (world.get(0, y, 0).name().path().equals("bedrock")) return y + 1;
        return -1;
    }

    public static void build(World world, int cy, boolean active) {
        BlockState bedrock = state(world, "bedrock", 0), endStone = state(world, "end_stone", 0), air = BlockState.of(world.registry().air(), 0);
        BlockState portal = state(world, "end_portal", 0);
        for (int x = -4; x <= 4; x++)
            for (int z = -4; z <= 4; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > 3.5) continue;
                for (int y = cy - 1; y <= cy + 32; y++) {
                    BlockState state;
                    if (y < cy) state = d <= 2.5 ? bedrock : endStone;
                    else if (y > cy) state = air;
                    else state = d > 2.5 ? bedrock : active ? portal : air;
                    world.setState(x, y, z, state);
                }
            }
        for (int i = 0; i < 4; i++) world.setState(0, cy + i, 0, bedrock);
        // Torch metadata: 1 east, 2 west, 3 south, 4 north.
        world.setState(1, cy + 2, 0, state(world, "torch", 1));
        world.setState(-1, cy + 2, 0, state(world, "torch", 2));
        world.setState(0, cy + 2, 1, state(world, "torch", 3));
        world.setState(0, cy + 2, -1, state(world, "torch", 4));
        for (int x = -4; x <= 4; x++)
            for (int z = -4; z <= 4; z++) world.notifyNeighbours(x, cy, z);
    }

    /**
     * The centre of the 3x3 hollow a frame at (x, y, z) borders, when all
     * twelve frames around it hold an eye (BlockEndPortalFrame.getOrCreatePortalShape);
     * null otherwise. shortcut: ignores which way the frames face.
     */
    public static int[] portalCentre(World world, int x, int y, int z) {
        for (int cx = x - 2; cx <= x + 2; cx++)
            for (int cz = z - 2; cz <= z + 2; cz++) {
                boolean complete = true;
                for (int dx = -2; dx <= 2 && complete; dx++)
                    for (int dz = -2; dz <= 2 && complete; dz++) {
                        boolean edge = Math.abs(dx) == 2 ^ Math.abs(dz) == 2;
                        if (!edge) continue;
                        BlockState state = world.getState(cx + dx, y, cz + dz);
                        complete = state.block().name().path().equals("end_portal_frame") && state.get("eye").equals("true");
                    }
                if (complete) return new int[]{cx, y, cz};
            }
        return null;
    }

    private static BlockState state(World world, String name, int meta) {
        return BlockState.of(world.registry().get("minecraft:" + name), meta);
    }
}
