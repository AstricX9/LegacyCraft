package net.minecraft.block;

import net.minecraft.util.Direction;
import net.minecraft.world.IBlockWorld;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Neighbour notification (master context §16): after a block changes, each of
 * its six neighbours is asked {@link Block#afterNeighborChange}. Blocks that
 * lost their support break (turn to air; a {@link Popped} listener can drop
 * their items), which notifies their
 * own neighbours in turn, so both halves of a door go together and torches pop
 * off a removed wall. The rules themselves live in each block class.
 */
public final class BlockUpdates {
    /** Upper bound on cascaded changes from one edit, as a guard against update loops. */
    private static final int MAX_CHANGES = 4096;

    private BlockUpdates() {}

    /** Told about each block that broke because it lost its support. */
    public interface Popped { void popped(int x, int y, int z, BlockState previous); }

    /** Lets the neighbours of a changed position react, cascading through whatever breaks. */
    public static void afterChange(IBlockWorld world, int x, int y, int z) { afterChange(world, x, y, z, null); }

    /** As {@link #afterChange(IBlockWorld, int, int, int)}, reporting blocks that break to {@code popped}. */
    public static void afterChange(IBlockWorld world, int x, int y, int z, Popped popped) {
        Deque<int[]> changed = new ArrayDeque<>();
        changed.add(new int[]{x, y, z});
        int budget = MAX_CHANGES;
        while (!changed.isEmpty() && budget > 0) {
            int[] position = changed.poll();
            for (Direction side : Direction.values()) {
                int nx = position[0] + side.xOffset, ny = position[1] + side.yOffset, nz = position[2] + side.zOffset;
                if (ny < 0 || ny >= 256) continue;
                BlockState state = world.getState(nx, ny, nz);
                if (state.block().isAir()) continue;
                BlockState reacted = state.block().afterNeighborChange(world, nx, ny, nz, state);
                if (!reacted.equals(state)) {
                    world.setState(nx, ny, nz, reacted);
                    if (popped != null && reacted.block().isAir()) popped.popped(nx, ny, nz, state);
                    changed.add(new int[]{nx, ny, nz});
                    budget--;
                } else {
                    world.neighbourChanged(nx, ny, nz, state);
                }
            }
        }
    }
}
