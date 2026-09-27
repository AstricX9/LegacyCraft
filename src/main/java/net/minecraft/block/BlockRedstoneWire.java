package net.minecraft.block;

import net.minecraft.block.state.ActualState;
import net.minecraft.util.Direction;
import net.minecraft.world.IBlockAccess;

import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/** Redstone dust: attaches sideways to power sources and runs up and down the sides of blocks. */
public class BlockRedstoneWire extends Block {
    /** Blocks that emit redstone power and therefore attach dust on their side. */
    private static final Set<String> POWER_SOURCES = Set.of(
        "minecraft:redstone_wire", "minecraft:redstone_torch", "minecraft:unlit_redstone_torch",
        "minecraft:lever", "minecraft:stone_button", "minecraft:wooden_button",
        "minecraft:stone_pressure_plate", "minecraft:wooden_pressure_plate",
        "minecraft:light_weighted_pressure_plate", "minecraft:heavy_weighted_pressure_plate",
        "minecraft:detector_rail", "minecraft:daylight_detector", "minecraft:daylight_detector_inverted",
        "minecraft:redstone_block", "minecraft:tripwire_hook", "minecraft:trapped_chest",
        "minecraft:unpowered_comparator", "minecraft:powered_comparator");

    public BlockRedstoneWire(Properties properties) { super(properties); }

    @Override
    public ActualState actualState(IBlockAccess world, int x, int y, int z, BlockState state) {
        SortedMap<String, String> values = new TreeMap<>(state.values());
        for (Direction side : Direction.HORIZONTALS) values.put(side.getName(), attachment(world, x, y, z, side));
        return new ActualState(state, values);
    }

    private String attachment(IBlockAccess world, int x, int y, int z, Direction side) {
        int nx = x + side.xOffset, nz = z + side.zOffset;
        BlockState neighbour = world.getState(nx, y, nz);
        if (connectsTo(neighbour, side)) return "side";
        if (!neighbour.block().isNormalCube() && world.getState(nx, y - 1, nz).block() == this) return "side";
        if (neighbour.block().isNormalCube() && !world.getState(x, y + 1, z).block().isNormalCube()
            && world.getState(nx, y + 1, nz).block() == this) return "up";
        return "none";
    }

    /** Repeaters only attach along their axis; other power sources attach from any side. */
    private static boolean connectsTo(BlockState state, Direction side) {
        String name = state.block().name().toString();
        if (name.equals("minecraft:unpowered_repeater") || name.equals("minecraft:powered_repeater"))
            return facing(state).axis() == side.axis();
        return POWER_SOURCES.contains(name);
    }

    @Override
    public boolean canStay(IBlockAccess world, int x, int y, int z, BlockState state) {
        BlockState below = world.getState(x, y - 1, z);
        return hasSolidTop(below) || below.block().name().path().equals("glowstone");
    }
}
