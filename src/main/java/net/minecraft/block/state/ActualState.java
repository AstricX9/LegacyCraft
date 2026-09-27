package net.minecraft.block.state;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;

import java.util.Collections;
import java.util.SortedMap;

/** A stored state plus every property value, including the ones derived from neighbours. */
public record ActualState(BlockState state, SortedMap<String, String> values) {
    public ActualState {
        values = Collections.unmodifiableSortedMap(values);
    }

    /** The actual state of a block with no world context: derived properties stay at their defaults. */
    public static ActualState withoutWorld(BlockState state) {
        return new ActualState(state, state.values());
    }

    public Block block() { return state.block(); }
    public String get(String property) {
        String value = values.get(property);
        if (value == null) throw new IllegalArgumentException(state.block() + " has no property " + property);
        return value;
    }
}
