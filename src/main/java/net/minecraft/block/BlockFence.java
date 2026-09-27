package net.minecraft.block;

import net.minecraft.block.state.ActualState;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Direction;
import net.minecraft.world.IBlockAccess;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/** Fences: connect to fences of their own material, gates and solid cubes; 1.5 blocks high to collide with. */
public class BlockFence extends Block {
    /** Materials vanilla marks translucent: never "opaque" for fence/wall connection purposes. */
    private static final Set<String> TRANSLUCENT_MATERIALS = Set.of("glass", "ice", "leaves", "tnt");

    public BlockFence(Properties properties) { super(properties); }

    @Override
    public ActualState actualState(IBlockAccess world, int x, int y, int z, BlockState state) {
        SortedMap<String, String> values = new TreeMap<>(state.values());
        for (Direction side : Direction.HORIZONTALS) {
            Block other = neighbour(world, x, y, z, side).block();
            boolean connects = other instanceof BlockFence ? other.material().equals(material())
                : other instanceof BlockFenceGate || connectsAsSolid(other);
            values.put(side.getName(), flag(connects));
        }
        return new ActualState(state, values);
    }

    /** Opaque full cubes that fences and walls attach to (not barriers, gourds or glass-like materials). */
    static boolean connectsAsSolid(Block other) {
        return !other.name().toString().equals("minecraft:barrier") && other.isOpaque() && other.isFullCube()
            && !TRANSLUCENT_MATERIALS.contains(other.material()) && !other.material().equals("gourd");
    }

    @Override
    public List<AxisAlignedBB> collisionBoxes(ActualState state, IBlockAccess world, int x, int y, int z) {
        boolean north = isTrue(state, "north"), south = isTrue(state, "south");
        boolean west = isTrue(state, "west"), east = isTrue(state, "east");
        List<AxisAlignedBB> boxes = new ArrayList<>();
        if (north || south) boxes.add(new AxisAlignedBB(.375, 0, north ? 0 : .375, .625, 1.5, south ? 1 : .625));
        if (west || east || !north && !south) boxes.add(new AxisAlignedBB(west ? 0 : .375, 0, .375, east ? 1 : .625, 1.5, .625));
        return boxes;
    }

    @Override
    public AxisAlignedBB selectionBox(ActualState state, IBlockAccess world, int x, int y, int z) {
        return unionAtHeight(collisionBoxes(state, world, x, y, z), 1);
    }

    /** Union of boxes cut to a height (fences and panes outline one block high). */
    static AxisAlignedBB unionAtHeight(List<AxisAlignedBB> boxes, double height) {
        AxisAlignedBB result = boxes.get(0);
        for (AxisAlignedBB box : boxes) result = result.union(box);
        return new AxisAlignedBB(result.minX(), result.minY(), result.minZ(), result.maxX(), height, result.maxZ());
    }
}
