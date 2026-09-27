package net.minecraft.block.state;

import net.minecraft.block.BlockRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.world.IBlockWorld;

import java.util.HashMap;
import java.util.Map;

/** A small in-memory world: every unset position is air. */
public final class TestBlockAccess implements IBlockWorld {
    private final BlockRegistry blocks;
    private final Map<String, BlockState> states = new HashMap<>();

    public TestBlockAccess(BlockRegistry blocks) { this.blocks = blocks; }

    public TestBlockAccess set(int x, int y, int z, BlockState state) {
        states.put(x + "," + y + "," + z, state);
        return this;
    }

    public TestBlockAccess set(int x, int y, int z, String block, int meta) {
        return set(x, y, z, BlockState.of(blocks.get(block), meta));
    }

    @Override
    public void setState(int x, int y, int z, BlockState state) { set(x, y, z, state); }

    @Override
    public BlockState getState(int x, int y, int z) {
        return states.getOrDefault(x + "," + y + "," + z, BlockState.of(blocks.air(), 0));
    }

    @Override
    public BlockRegistry registry() { return blocks; }

    public ActualState actual(int x, int y, int z) {
        BlockState state = getState(x, y, z);
        return state.block().actualState(this, x, y, z, state);
    }
}
