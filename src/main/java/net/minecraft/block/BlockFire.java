package net.minecraft.block;

import net.minecraft.block.state.ActualState;
import net.minecraft.util.Direction;
import net.minecraft.world.IBlockAccess;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Fire (1.8.9 BlockFire, HYPOTHESIS: recalled flammability table). How
 * readily each block catches fire from a neighbour ("encouragement") and how
 * readily it burns away ("flammability"); fire stays where the block below
 * has a solid top or a neighbour can catch fire. With nothing to stand on,
 * it hangs on the sides of flammable neighbours: the "actual" state names
 * those sides and picks one of its alternative flame shapes by position.
 * The spreading itself is world logic (FireSpread).
 */
public class BlockFire extends Block {
    /** {encouragement, flammability} by block name (Blocks.init's setFireInfo calls). */
    private static final Map<String, int[]> FIRE_INFO = Map.ofEntries(
        Map.entry("planks", new int[]{5, 20}), Map.entry("double_wooden_slab", new int[]{5, 20}),
        Map.entry("wooden_slab", new int[]{5, 20}),
        Map.entry("fence_gate", new int[]{5, 20}), Map.entry("spruce_fence_gate", new int[]{5, 20}),
        Map.entry("birch_fence_gate", new int[]{5, 20}), Map.entry("jungle_fence_gate", new int[]{5, 20}),
        Map.entry("dark_oak_fence_gate", new int[]{5, 20}), Map.entry("acacia_fence_gate", new int[]{5, 20}),
        Map.entry("fence", new int[]{5, 20}), Map.entry("spruce_fence", new int[]{5, 20}),
        Map.entry("birch_fence", new int[]{5, 20}), Map.entry("jungle_fence", new int[]{5, 20}),
        Map.entry("dark_oak_fence", new int[]{5, 20}), Map.entry("acacia_fence", new int[]{5, 20}),
        Map.entry("oak_stairs", new int[]{5, 20}), Map.entry("birch_stairs", new int[]{5, 20}),
        Map.entry("spruce_stairs", new int[]{5, 20}), Map.entry("jungle_stairs", new int[]{5, 20}),
        Map.entry("acacia_stairs", new int[]{5, 20}), Map.entry("dark_oak_stairs", new int[]{5, 20}),
        Map.entry("log", new int[]{5, 5}), Map.entry("log2", new int[]{5, 5}),
        Map.entry("leaves", new int[]{30, 60}), Map.entry("leaves2", new int[]{30, 60}),
        Map.entry("bookshelf", new int[]{30, 20}), Map.entry("tnt", new int[]{15, 100}),
        Map.entry("tallgrass", new int[]{60, 100}), Map.entry("double_plant", new int[]{60, 100}),
        Map.entry("yellow_flower", new int[]{60, 100}), Map.entry("red_flower", new int[]{60, 100}),
        Map.entry("deadbush", new int[]{60, 100}), Map.entry("wool", new int[]{30, 60}),
        Map.entry("vine", new int[]{15, 100}), Map.entry("coal_block", new int[]{5, 5}),
        Map.entry("hay_block", new int[]{60, 20}), Map.entry("carpet", new int[]{60, 20}));

    public BlockFire(Properties properties) { super(properties); }

    /** How readily fire next to this block spreads onto it (0 for blocks that don't burn). */
    public static int encouragement(Block block) {
        int[] info = FIRE_INFO.get(block.name().path());
        return info == null ? 0 : info[0];
    }

    /** How readily this block burns away when fire reaches it. */
    public static int flammability(Block block) {
        int[] info = FIRE_INFO.get(block.name().path());
        return info == null ? 0 : info[1];
    }

    public static boolean canCatchFire(IBlockAccess world, int x, int y, int z) {
        return encouragement(world.getState(x, y, z).block()) > 0;
    }

    /** Some side, top or bottom neighbour could catch fire (canNeighborCatchFire). */
    public static boolean canNeighborCatchFire(IBlockAccess world, int x, int y, int z) {
        for (Direction side : Direction.values())
            if (canCatchFire(world, x + side.xOffset, y + side.yOffset, z + side.zOffset)) return true;
        return false;
    }

    /** Fire may be here: on a solid top, or beside something that burns (canPlaceBlockAt). */
    public static boolean canStayAt(IBlockAccess world, int x, int y, int z) {
        return y > 0 && hasSolidTop(world.getState(x, y - 1, z)) || canNeighborCatchFire(world, x, y, z);
    }

    @Override
    public ActualState actualState(IBlockAccess world, int x, int y, int z, BlockState state) {
        if (y <= 0 || hasSolidTop(world.getState(x, y - 1, z)) || canCatchFire(world, x, y - 1, z))
            return ActualState.withoutWorld(state);
        boolean alt = (x + y + z & 1) == 1;
        boolean flip = (x / 2 + y / 2 + z / 2 & 1) == 1;
        int upper = canCatchFire(world, x, y + 1, z) ? alt ? 1 : 2 : 0;
        SortedMap<String, String> values = new TreeMap<>(state.values());
        values.put("north", flag(canCatchFire(world, x, y, z - 1)));
        values.put("east", flag(canCatchFire(world, x + 1, y, z)));
        values.put("south", flag(canCatchFire(world, x, y, z + 1)));
        values.put("west", flag(canCatchFire(world, x - 1, y, z)));
        values.put("upper", String.valueOf(upper));
        values.put("flip", flag(flip));
        values.put("alt", flag(alt));
        return new ActualState(state, values);
    }
}
