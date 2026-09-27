package net.minecraft.block;

import net.minecraft.block.state.ActualState;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Direction;
import net.minecraft.world.IBlockAccess;

import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/** Cobblestone walls: connect like fences, drop their post on uncovered straight runs, collide 1.5 high. */
public class BlockWall extends Block {
    public BlockWall(Properties properties) { super(properties); }

    @Override
    public ActualState actualState(IBlockAccess world, int x, int y, int z, BlockState state) {
        SortedMap<String, String> values = new TreeMap<>(state.values());
        boolean[] connected = new boolean[4];
        for (int i = 0; i < 4; i++) {
            Direction side = Direction.HORIZONTALS[i];
            Block other = neighbour(world, x, y, z, side).block();
            connected[i] = other == this || other instanceof BlockFenceGate || BlockFence.connectsAsSolid(other);
            values.put(side.getName(), flag(connected[i]));
        }
        boolean north = connected[0], east = connected[1], south = connected[2], west = connected[3];
        boolean straight = north && south && !east && !west || !north && !south && east && west;
        values.put("up", flag(!straight || !world.getState(x, y + 1, z).block().isAir()));
        return new ActualState(state, values);
    }

    @Override
    public AxisAlignedBB selectionBox(ActualState state, IBlockAccess world, int x, int y, int z) {
        return box(state, 1);
    }

    @Override
    public List<AxisAlignedBB> collisionBoxes(ActualState state, IBlockAccess world, int x, int y, int z) {
        return List.of(box(state, 1.5));
    }

    /** One box spanning the connections; {@code height} 1 gives the outline (a straight run is 13/16 high), 1.5 the collision. */
    private static AxisAlignedBB box(ActualState state, double height) {
        boolean north = isTrue(state, "north"), south = isTrue(state, "south");
        boolean west = isTrue(state, "west"), east = isTrue(state, "east");
        double minX = west ? 0 : .25, maxX = east ? 1 : .75, minZ = north ? 0 : .25, maxZ = south ? 1 : .75;
        boolean outline = height == 1;
        if (north && south && !west && !east) { minX = .3125; maxX = .6875; if (outline) height = .8125; }
        else if (!north && !south && west && east) { minZ = .3125; maxZ = .6875; if (outline) height = .8125; }
        return new AxisAlignedBB(minX, 0, minZ, maxX, height, maxZ);
    }
}
