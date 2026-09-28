package net.minecraft.world;

import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.block.Liquids;
import net.minecraft.util.Direction;

import java.util.EnumSet;
import java.util.Random;
import java.util.Set;

/**
 * Water and lava spreading, after 1.8.9's BlockDynamicLiquid and
 * BlockStaticLiquid (HYPOTHESIS, recalled; DEV-022). Resting liquid is the
 * "still" block; a neighbour change turns it into the "flowing" block, which
 * re-evaluates its level on scheduled ticks (water every 5 ticks, lava every
 * 30) and spreads: straight down if it can, else sideways towards the nearest
 * drop within 4 blocks (every direction if none), one level thinner per
 * block for water and two for lava. Two water sources beside a block over a
 * solid block or a water source make a new source. Lava beside water hardens
 * (a source to obsidian, level 4 or less to cobblestone) and lava flowing
 * down into water turns it to stone.
 */
final class LiquidFlow {
    static final int WATER_TICKS = 5, LAVA_TICKS = 30;

    private final World world;
    private final Block water, flowingWater, lava, flowingLava, obsidian, cobblestone, stone, air;
    private int adjacentSources;

    LiquidFlow(World world, BlockRegistry registry) {
        this.world = world;
        water = registry.get("minecraft:water");
        flowingWater = registry.get("minecraft:flowing_water");
        lava = registry.get("minecraft:lava");
        flowingLava = registry.get("minecraft:flowing_lava");
        obsidian = registry.get("minecraft:obsidian");
        cobblestone = registry.get("minecraft:cobblestone");
        stone = registry.get("minecraft:stone");
        air = registry.air();
    }

    static int tickRate(String material) { return material.equals("lava") ? LAVA_TICKS : WATER_TICKS; }

    boolean isFlowing(Block block) { return block == flowingWater || block == flowingLava; }

    private Block flowing(String material) { return material.equals("lava") ? flowingLava : flowingWater; }
    private Block still(String material) { return material.equals("lava") ? lava : water; }

    /** A liquid block was just placed (Block.onBlockAdded): flowing liquid schedules itself unless it hardened. */
    void added(int x, int y, int z, BlockState state) {
        String material = Liquids.liquid(state.block());
        if (material == null) return;
        if (checkForMixing(x, y, z, state)) return;
        if (isFlowing(state.block())) world.scheduleUpdate(x, y, z, state.block(), tickRate(material));
    }

    /** A neighbour changed (onNeighborBlockChange): still liquid starts flowing again. */
    void neighbourChanged(int x, int y, int z, BlockState state) {
        String material = Liquids.liquid(state.block());
        if (material == null || checkForMixing(x, y, z, state)) return;
        if (isFlowing(state.block())) return;
        Block flowing = flowing(material);
        world.setState(x, y, z, BlockState.of(flowing, state.metadata()));
        world.scheduleUpdate(x, y, z, flowing, tickRate(material));
    }

    /** BlockLiquid.checkForMixing: lava touching water (not from below) hardens. */
    private boolean checkForMixing(int x, int y, int z, BlockState state) {
        if (!Liquids.liquid(state.block()).equals("lava")) return false;
        boolean touchesWater = false;
        for (Direction side : Direction.values()) {
            if (side == Direction.DOWN) continue;
            if (world.getState(x + side.xOffset, y + side.yOffset, z + side.zOffset).block().material().equals("water")) {
                touchesWater = true;
                break;
            }
        }
        if (!touchesWater) return false;
        int level = state.metadata();
        if (level == 0) {
            world.setStateAndUpdate(x, y, z, BlockState.defaultOf(obsidian));
        } else if (level <= 4) {
            world.setStateAndUpdate(x, y, z, BlockState.defaultOf(cobblestone));
        } else {
            return false;
        }
        world.events().liquidsMixed(x, y, z);
        return true;
    }

    /** BlockDynamicLiquid.updateTick. */
    void tick(int x, int y, int z, BlockState state, Random random) {
        String material = Liquids.liquid(state.block());
        int level = state.metadata();
        int step = material.equals("lava") ? 2 : 1;
        int delay = tickRate(material);
        if (level > 0) {
            int lowest = -100;
            adjacentSources = 0;
            for (Direction side : Direction.HORIZONTALS)
                lowest = checkAdjacent(x + side.xOffset, y, z + side.zOffset, material, lowest);
            int next = lowest + step;
            if (next >= 8 || lowest < 0) next = -1;
            int above = Liquids.level(world, x, y + 1, z, material);
            if (above >= 0) next = above >= 8 ? above : above + 8;
            if (adjacentSources >= 2 && material.equals("water")) {
                BlockState below = world.getState(x, y - 1, z);
                if (Liquids.isSolid(below.block())) next = 0;
                else if (below.block().material().equals(material) && below.metadata() == 0) next = 0;
            }
            if (material.equals("lava") && level < 8 && next < 8 && next > level && random.nextInt(4) != 0) delay *= 4;
            if (next == level) {
                placeStill(x, y, z, material, level);
            } else {
                level = next;
                if (next < 0) {
                    world.setStateAndUpdate(x, y, z, BlockState.defaultOf(air));
                } else {
                    state = BlockState.of(state.block(), next);
                    world.setState(x, y, z, state);
                    world.scheduleUpdate(x, y, z, state.block(), delay);
                    world.notifyNeighbours(x, y, z);
                }
            }
        } else {
            placeStill(x, y, z, material, level);
        }
        BlockState below = world.getState(x, y - 1, z);
        if (canFlowInto(x, y - 1, z, below, material)) {
            if (material.equals("lava") && below.block().material().equals("water")) {
                world.setStateAndUpdate(x, y - 1, z, BlockState.defaultOf(stone));
                world.events().liquidsMixed(x, y - 1, z);
                return;
            }
            flowInto(x, y - 1, z, below, level >= 8 ? level : level + 8, material);
        } else if (level >= 0 && (level == 0 || isBlocked(x, y - 1, z))) {
            Set<Direction> directions = flowDirections(x, y, z, material);
            int spread = level >= 8 ? 1 : level + step;
            if (spread >= 8) return;
            for (Direction side : directions) {
                int nx = x + side.xOffset, nz = z + side.zOffset;
                flowInto(nx, y, nz, world.getState(nx, y, nz), spread, material);
            }
        }
    }

    private void placeStill(int x, int y, int z, String material, int level) {
        world.setState(x, y, z, BlockState.of(still(material), level));
    }

    private int checkAdjacent(int x, int y, int z, String material, int lowest) {
        int level = Liquids.level(world, x, y, z, material);
        if (level < 0) return lowest;
        if (level == 0) adjacentSources++;
        if (level >= 8) level = 0;
        return lowest >= 0 && level >= lowest ? lowest : level;
    }

    private void flowInto(int x, int y, int z, BlockState state, int level, String material) {
        if (!canFlowInto(x, y, z, state, material)) return;
        if (!state.block().isAir()) {
            if (material.equals("lava")) world.events().liquidsMixed(x, y, z);
            else world.events().washedAway(x, y, z, state);
        }
        world.setStateAndUpdate(x, y, z, BlockState.of(flowing(material), level));
    }

    /** Shortest distance (up to 4 steps) from here to a drop, not going back the way it came (func_176374_a). */
    private int distanceToDrop(int x, int y, int z, int distance, Direction from, String material) {
        int best = 1000;
        for (Direction side : Direction.HORIZONTALS) {
            if (side == from) continue;
            int nx = x + side.xOffset, nz = z + side.zOffset;
            BlockState state = world.getState(nx, y, nz);
            if (isBlocked(nx, y, nz) || state.block().material().equals(material) && state.metadata() <= 0) continue;
            if (!isBlocked(nx, y - 1, nz)) return distance;
            if (distance < 4) {
                int found = distanceToDrop(nx, y, nz, distance + 1, side.opposite(), material);
                if (found < best) best = found;
            }
        }
        return best;
    }

    /** The sides leading to the nearest drop; every open side when there is none (getPossibleFlowDirections). */
    private Set<Direction> flowDirections(int x, int y, int z, String material) {
        int best = 1000;
        Set<Direction> result = EnumSet.noneOf(Direction.class);
        for (Direction side : Direction.HORIZONTALS) {
            int nx = x + side.xOffset, nz = z + side.zOffset;
            BlockState state = world.getState(nx, y, nz);
            if (isBlocked(nx, y, nz) || state.block().material().equals(material) && state.metadata() <= 0) continue;
            int distance = isBlocked(nx, y - 1, nz) ? distanceToDrop(nx, y, nz, 1, side.opposite(), material) : 0;
            if (distance < best) result.clear();
            if (distance <= best) {
                result.add(side);
                best = distance;
            }
        }
        return result;
    }

    /** Liquid can't pass doors, signs, ladders, sugar cane, portals or anything that blocks movement. */
    private boolean isBlocked(int x, int y, int z) {
        Block block = world.getState(x, y, z).block();
        if (block instanceof BlockDoor) return true;
        String name = block.name().path();
        if (name.equals("standing_sign") || name.equals("ladder") || name.equals("reeds")) return true;
        return block.material().equals("portal") || Liquids.blocksMovement(block);
    }

    private boolean canFlowInto(int x, int y, int z, BlockState state, String material) {
        String other = state.block().material();
        return !other.equals(material) && !other.equals("lava") && !isBlocked(x, y, z);
    }
}
