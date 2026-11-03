package net.minecraft.block;

import net.minecraft.world.World;

import java.util.Random;

/**
 * 1.9 frosted ice from Frost Walker (BlockFrostedIce): ages 0-3 on its
 * scheduled ticks (20-40 ticks apart) and melts into water after age 3.
 * shortcut: always ages; vanilla only ages it when light > 11 - opacity or by chance with few frosted neighbours.
 */
public class BlockFrostedIce extends Block {
    public BlockFrostedIce(Properties properties) { super(properties); }

    public static int delay(Random random) { return 20 + random.nextInt(21); }

    /**
     * Frost Walker (EnchantmentFrostWalker.freezeNearby): still water with air
     * above, one block under the feet and within 2 + level blocks (at most
     * 16), becomes frosted ice. Returns how many blocks froze.
     */
    public static int freezeAround(World world, double px, double py, double pz, int level, Random random) {
        Block ice = world.registry().get("minecraft:frosted_ice");
        int r = Math.min(16, 2 + level), y = (int) Math.floor(py) - 1, frozen = 0;
        int cx = (int) Math.floor(px), cz = (int) Math.floor(pz);
        for (int x = cx - r; x <= cx + r; x++)
            for (int z = cz - r; z <= cz + r; z++) {
                double dx = x + 0.5 - px, dz = z + 0.5 - pz;
                if (dx * dx + dz * dz > r * r || !world.inBounds(x, y, z)) continue;
                BlockState state = world.getState(x, y, z);
                if (!state.block().name().path().equals("water") || state.metadata() != 0) continue;
                if (!world.getState(x, y + 1, z).block().isAir()) continue;
                world.setState(x, y, z, BlockState.of(ice, 0));
                world.scheduleUpdate(x, y, z, ice, 60 + random.nextInt(60));
                frozen++;
            }
        return frozen;
    }

    public void tick(World world, int x, int y, int z, BlockState state, Random random) {
        int age = Integer.parseInt(state.get("age"));
        if (age < 3) {
            world.setState(x, y, z, state.with("age", Integer.toString(age + 1)));
            world.scheduleUpdate(x, y, z, this, delay(random));
        } else {
            world.setStateAndUpdate(x, y, z, BlockState.of(world.registry().get("minecraft:water"), 0));
        }
    }
}
