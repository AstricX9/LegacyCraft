package net.minecraft.world;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.Direction;

/**
 * A nether portal frame (1.8.9 BlockPortal.Size, HYPOTHESIS: recalled). From
 * a spot inside, it drops to the lowest empty block (at most 21 down), walks
 * left to the obsidian side, then measures the width (2-21) along the bottom
 * obsidian row and the height (3-21) up to a full obsidian top, checking the
 * obsidian columns on both sides. The inside may hold only air, fire or
 * portal. X-axis frames run east-west, Z-axis ones north-south.
 */
final class PortalShape {
    private final World world;
    private final char axis;
    private final Direction right, left;
    private final Block obsidian, portal;
    private int[] bottomLeft;
    private int width, height, portalBlocks;

    PortalShape(World world, int x, int y, int z, char axis) {
        this.world = world;
        this.axis = axis;
        this.obsidian = world.registry().get("minecraft:obsidian");
        this.portal = world.registry().get("minecraft:portal");
        if (axis == 'x') {
            right = Direction.EAST;
            left = Direction.WEST;
        } else {
            right = Direction.NORTH;
            left = Direction.SOUTH;
        }
        int startY = y;
        while (y > startY - 21 && y > 0 && isEmpty(world.get(x, y - 1, z))) y--;
        int toEdge = distanceUntilEdge(x, y, z, left) - 1;
        if (toEdge >= 0) {
            bottomLeft = new int[]{x + left.xOffset * toEdge, y, z + left.zOffset * toEdge};
            width = distanceUntilEdge(bottomLeft[0], bottomLeft[1], bottomLeft[2], right);
            if (width < 2 || width > 21) bottomLeft = null;
        }
        if (bottomLeft != null) height = measureHeight();
    }

    private boolean isEmpty(Block block) { return block.isAir() || block.material().equals("fire") || block == portal; }

    /** Steps along a direction while the inside is empty over obsidian; the count if obsidian stops it, else 0. */
    private int distanceUntilEdge(int x, int y, int z, Direction direction) {
        int i;
        for (i = 0; i < 22; i++) {
            int bx = x + direction.xOffset * i, bz = z + direction.zOffset * i;
            if (!isEmpty(world.get(bx, y, bz)) || world.get(bx, y - 1, bz) != obsidian) break;
        }
        return world.get(x + direction.xOffset * i, y, z + direction.zOffset * i) == obsidian ? i : 0;
    }

    private int measureHeight() {
        outer:
        for (height = 0; height < 21; height++) {
            for (int i = 0; i < width; i++) {
                int bx = bottomLeft[0] + right.xOffset * i, by = bottomLeft[1] + height, bz = bottomLeft[2] + right.zOffset * i;
                Block block = world.get(bx, by, bz);
                if (!isEmpty(block)) break outer;
                if (block == portal) portalBlocks++;
                if (i == 0) {
                    if (world.get(bx + left.xOffset, by, bz + left.zOffset) != obsidian) break outer;
                } else if (i == width - 1) {
                    if (world.get(bx + right.xOffset, by, bz + right.zOffset) != obsidian) break outer;
                }
            }
        }
        for (int i = 0; i < width; i++) {
            if (world.get(bottomLeft[0] + right.xOffset * i, bottomLeft[1] + height, bottomLeft[2] + right.zOffset * i) != obsidian) {
                height = 0;
                break;
            }
        }
        if (height >= 3 && height <= 21) return height;
        bottomLeft = null;
        width = 0;
        height = 0;
        return 0;
    }

    boolean isValid() { return bottomLeft != null && width >= 2 && width <= 21 && height >= 3 && height <= 21; }
    int width() { return width; }
    int height() { return height; }
    int portalBlocks() { return portalBlocks; }

    /** Fills the inside with portal blocks along the axis, without neighbour updates. */
    void place() {
        BlockState state = portal.defaultState().with("axis", String.valueOf(axis));
        for (int i = 0; i < width; i++)
            for (int j = 0; j < height; j++)
                world.setState(bottomLeft[0] + right.xOffset * i, bottomLeft[1] + j, bottomLeft[2] + right.zOffset * i, state);
    }

    /** BlockPortal.func_176548_d: an empty frame around this spot, east-west first, fills with portal. */
    static boolean trySpawn(World world, int x, int y, int z) {
        PortalShape shape = new PortalShape(world, x, y, z, 'x');
        if (shape.isValid() && shape.portalBlocks == 0) {
            shape.place();
            return true;
        }
        shape = new PortalShape(world, x, y, z, 'z');
        if (shape.isValid() && shape.portalBlocks == 0) {
            shape.place();
            return true;
        }
        return false;
    }

    /**
     * BlockPortal.onNeighborBlockChange: a portal block whose frame is no
     * longer whole and full goes out, shattering with the glass break sound
     * and particles (as the user observed; DEV-027).
     */
    static void neighbourChanged(World world, int x, int y, int z, BlockState state) {
        String axis = state.get("axis");
        if (axis == null || !(axis.equals("x") || axis.equals("z"))) return;
        PortalShape shape = new PortalShape(world, x, y, z, axis.charAt(0));
        if (!shape.isValid() || shape.portalBlocks < shape.width * shape.height) {
            world.setStateAndUpdate(x, y, z, BlockState.defaultOf(world.registry().air()));
            world.events().shattered(x, y, z, state);
        }
    }
}
