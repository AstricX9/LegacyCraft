package net.minecraft.world;

import net.minecraft.block.Block;
import net.minecraft.block.BlockFire;
import net.minecraft.block.BlockState;
import net.minecraft.world.biome.Biome;

import java.util.Random;

/**
 * Fire over time (1.8.9 BlockFire.updateTick / onBlockAdded, HYPOTHESIS:
 * recalled). Lit fire first tries to make a nether portal of the obsidian
 * frame around it; otherwise it needs a solid top below or a flammable
 * neighbour, and ticks every 30-39 ticks while the doFireTick rule is on.
 * Each tick it ages (0-15), goes out when nothing near it burns (on a solid
 * top once older than 3), may die at full age over a non-flammable block,
 * burns or catches its six neighbours by their flammability, and jumps to
 * air blocks up to one across and four up that touch something flammable,
 * more readily at higher difficulty and less in humid biomes. Rain puts out
 * fire open to the sky. Netherrack burns forever.
 */
final class FireSpread {
    static final int TICK_RATE = 30;
    private final World world;
    private final Block fire, air, netherrack;

    FireSpread(World world) {
        this.world = world;
        fire = world.registry().get("minecraft:fire");
        air = world.registry().air();
        netherrack = world.registry().get("minecraft:netherrack");
    }

    boolean isFire(Block block) { return block == fire; }

    /** Fire placed: a portal if it lights one, else it goes out if it can't stay or waits for its first tick. */
    void added(int x, int y, int z, Random random) {
        if (PortalShape.trySpawn(world, x, y, z)) return;
        if (!(y > 0 && Block.hasSolidTop(world.getState(x, y - 1, z))) && !BlockFire.canNeighborCatchFire(world, x, y, z))
            world.setStateAndUpdate(x, y, z, BlockState.defaultOf(air));
        else
            world.scheduleUpdate(x, y, z, fire, TICK_RATE + random.nextInt(10));
    }

    /** A neighbour changed: fire with no solid top and nothing flammable beside it goes out. */
    void neighbourChanged(int x, int y, int z) {
        if (!(y > 0 && Block.hasSolidTop(world.getState(x, y - 1, z))) && !BlockFire.canNeighborCatchFire(world, x, y, z))
            world.setStateAndUpdate(x, y, z, BlockState.defaultOf(air));
    }

    void tick(int x, int y, int z, BlockState state, Random random) {
        if (!world.doFireTick()) return;
        if (!BlockFire.canStayAt(world, x, y, z)) {
            // Vanilla carries on and removes it again below; stopping here ends the same way.
            world.setStateAndUpdate(x, y, z, BlockState.defaultOf(air));
            return;
        }
        boolean forever = y > 0 && world.get(x, y - 1, z) == netherrack;
        if (!forever && world.isRaining() && canDie(x, y, z)) {
            world.setStateAndUpdate(x, y, z, BlockState.defaultOf(air));
            return;
        }
        int age = state.metadata() & 15;
        if (age < 15) {
            state = BlockState.of(fire, age + random.nextInt(3) / 2);
            world.setState(x, y, z, state);
        }
        world.scheduleUpdate(x, y, z, fire, TICK_RATE + random.nextInt(10));
        if (!forever) {
            if (!BlockFire.canNeighborCatchFire(world, x, y, z)) {
                if (!(y > 0 && Block.hasSolidTop(world.getState(x, y - 1, z))) || age > 3)
                    world.setStateAndUpdate(x, y, z, BlockState.defaultOf(air));
                return;
            }
            if (!BlockFire.canCatchFire(world, x, y - 1, z) && age == 15 && random.nextInt(4) == 0) {
                world.setStateAndUpdate(x, y, z, BlockState.defaultOf(air));
                return;
            }
        }
        boolean humid = world.biomeAt(x, z).rainfall() > 0.85f;
        int humidity = humid ? -50 : 0;
        catchOnFire(x + 1, y, z, 300 + humidity, random, age);
        catchOnFire(x - 1, y, z, 300 + humidity, random, age);
        catchOnFire(x, y - 1, z, 250 + humidity, random, age);
        catchOnFire(x, y + 1, z, 250 + humidity, random, age);
        catchOnFire(x, y, z - 1, 300 + humidity, random, age);
        catchOnFire(x, y, z + 1, 300 + humidity, random, age);
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                for (int dy = -1; dy <= 4; dy++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    int bx = x + dx, by = y + dy, bz = z + dz;
                    if (by < 0 || by >= World.HEIGHT) continue;
                    int odds = 100;
                    if (dy > 1) odds += (dy - 1) * 100;
                    int encouragement = neighbourEncouragement(bx, by, bz);
                    if (encouragement <= 0) continue;
                    int chance = (encouragement + 40 + world.difficulty() * 7) / (age + 30);
                    if (humid) chance /= 2;
                    if (chance > 0 && random.nextInt(odds) <= chance && (!world.isRaining() || !canDie(bx, by, bz))) {
                        int newAge = Math.min(15, age + random.nextInt(5) / 4);
                        world.setStateAndUpdate(bx, by, bz, BlockState.of(fire, newAge));
                    }
                }
    }

    /** A neighbour burns away (or becomes fire itself) with a chance by its flammability. */
    private void catchOnFire(int x, int y, int z, int chance, Random random, int age) {
        if (y < 0 || y >= World.HEIGHT) return;
        BlockState state = world.getState(x, y, z);
        int flammability = BlockFire.flammability(state.block());
        if (random.nextInt(chance) >= flammability) return;
        if (random.nextInt(age + 10) < 5 && !canRainOn(x, y, z)) {
            world.setStateAndUpdate(x, y, z, BlockState.of(fire, Math.min(15, age + random.nextInt(5) / 4)));
        } else {
            world.setStateAndUpdate(x, y, z, BlockState.defaultOf(air));
        }
        // Burning TNT would prime it; there are no TNT entities yet (DEV-026).
    }

    /** The most encouragement next to an air block (getNeighborEncouragement), 0 when not air. */
    private int neighbourEncouragement(int x, int y, int z) {
        if (!world.get(x, y, z).isAir()) return 0;
        int best = 0;
        for (net.minecraft.util.Direction side : net.minecraft.util.Direction.values()) {
            int ny = y + side.yOffset;
            if (ny < 0 || ny >= World.HEIGHT) continue;
            best = Math.max(best, BlockFire.encouragement(world.get(x + side.xOffset, ny, z + side.zOffset)));
        }
        return best;
    }

    /** Rain reaches this block or one beside it (BlockFire.canDie). */
    private boolean canDie(int x, int y, int z) {
        return canRainOn(x, y, z) || canRainOn(x - 1, y, z) || canRainOn(x + 1, y, z) || canRainOn(x, y, z - 1) || canRainOn(x, y, z + 1);
    }

    /** World.canLightningStrike: raining, open to the sky, in a biome where it rains rather than snows. */
    private boolean canRainOn(int x, int y, int z) {
        if (!world.isRaining()) return false;
        Biome biome = world.biomeAt(x, z);
        if (!biome.rain() || biome.snow()) return false;
        for (int by = y + 1; by < World.HEIGHT; by++) {
            Block block = world.get(x, by, z);
            if (!block.isAir() && (block.isSolid() || net.minecraft.block.Liquids.isLiquid(block) || block.material().equals("leaves")))
                return false;
        }
        return true;
    }
}
