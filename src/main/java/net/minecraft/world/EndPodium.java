package net.minecraft.world;

import net.minecraft.block.BlockState;

/**
 * The End's exit portal (1.8.9 EntityDragon.generatePortal, HYPOTHESIS:
 * recalled), left where the dragon dies at y 64: a bedrock bowl of radius
 * 3.5 with end portal in its 2.5 centre, the air above cleared, a four-high
 * bedrock pillar with a torch on each side two up, and the dragon egg on top.
 * Also finds a completed ring of end portal frames.
 */
public final class EndPodium {
    private EndPodium() {}

    public static void generatePortal(World world, int px, int py, int pz) {
        BlockState bedrock = state(world, "bedrock", 0), air = BlockState.of(world.registry().air(), 0);
        BlockState portal = state(world, "end_portal", 0);
        for (int y = -1; y <= 32; y++)
            for (int x = -4; x <= 4; x++)
                for (int z = -4; z <= 4; z++) {
                    double d = x * x + z * z;
                    if (d > 12.25) continue;
                    BlockState state;
                    if (y < 0) {
                        if (d > 6.25) continue;
                        state = bedrock;
                    } else if (y > 0) state = air;
                    else state = d > 6.25 ? bedrock : portal;
                    world.setState(px + x, py + y, pz + z, state);
                }
        for (int i = 0; i < 4; i++) world.setState(px, py + i, pz, bedrock);
        // Torch metadata: 1 east, 2 west, 3 south, 4 north (each facing away from the pillar).
        world.setState(px + 1, py + 2, pz, state(world, "torch", 1));
        world.setState(px - 1, py + 2, pz, state(world, "torch", 2));
        world.setState(px, py + 2, pz + 1, state(world, "torch", 3));
        world.setState(px, py + 2, pz - 1, state(world, "torch", 4));
        world.setState(px, py + 4, pz, state(world, "dragon_egg", 0));
        for (int x = -4; x <= 4; x++)
            for (int z = -4; z <= 4; z++) world.notifyNeighbours(px + x, py, pz + z);
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
