package net.minecraft.block;

import java.util.SortedMap;
import java.util.TreeMap;

/**
 * A pre-flattening vanilla "block state": a {@link Block} plus its 0-15
 * metadata/data value (master context §11.4, §15.1 — orientation, variant,
 * growth stage, slab half, wool color, and so on all live here).
 *
 * <p>This is the identity of what actually sits in a world position or an
 * item stack for a placeable block — vanilla legacy storage packs both into
 * a single value (block ID in the high bits, metadata in the low 4), which
 * {@link #packed()} / {@link #unpack(BlockRegistry, int)} mirror exactly so
 * {@link net.minecraft.world.World} can keep using its existing {@code short}
 * chunk storage unchanged.
 *
 * <p>What a metadata value <i>means</i> is defined per block by its
 * {@link net.minecraft.block.state.BlockStateDefinition}: {@link #values()}
 * decodes the stored properties and {@link #with(String, String)} re-encodes.
 * Properties derived from neighbours (fence connections, stair shape...) are
 * not stored; see {@link Block#actualState}.
 */
public final class BlockState {
    /** Vanilla legacy metadata is a nibble: 0-15. */
    public static final int METADATA_BITS = 4;
    public static final int MAX_METADATA = (1 << METADATA_BITS) - 1;

    private final Block block;
    private final int metadata;

    private BlockState(Block block, int metadata) {
        this.block = block;
        this.metadata = metadata;
    }

    public static BlockState of(Block block, int metadata) {
        if (block == null) throw new NullPointerException("block");
        if (metadata < 0 || metadata > MAX_METADATA) {
            throw new IllegalArgumentException("Metadata " + metadata + " is out of range for "
                + block.name() + " (must be 0-" + MAX_METADATA + ")");
        }
        BlockState state = block.states[metadata];
        if (state == null) block.states[metadata] = state = new BlockState(block, metadata);
        return state;
    }

    /** The raw metadata-0 state, which is what legacy storage holds for a bare block ID. See {@link Block#defaultState()}. */
    public static BlockState defaultOf(Block block) { return of(block, 0); }

    public Block block() { return block; }
    public int metadata() { return metadata; }

    /** Stored property values decoded from the metadata (derived properties at their defaults). */
    public SortedMap<String, String> values() { return block.stateDefinition().decode(metadata); }

    public String get(String property) {
        String value = values().get(property);
        if (value == null) throw new IllegalArgumentException(block + " has no property " + property);
        return value;
    }

    /** A copy with one stored property changed; derived properties cannot be stored. */
    public BlockState with(String property, String value) {
        var definition = block.stateDefinition();
        if (!definition.hasProperty(property) || !definition.properties().get(property).contains(value))
            throw new IllegalArgumentException(block + " has no state " + property + "=" + value);
        if (!definition.isStored(property))
            throw new IllegalArgumentException(block + " property " + property + " is derived from neighbours, not stored");
        SortedMap<String, String> updated = new TreeMap<>(values());
        updated.put(property, value);
        return of(block, definition.encode(updated));
    }

    /** Legacy extended-storage packing: {@code (registryId << 4) | metadata}. */
    public int packed() { return (block.registryId() << METADATA_BITS) | metadata; }

    public static BlockState unpack(BlockRegistry registry, int packed) {
        return of(registry.byId(packed >>> METADATA_BITS), packed & MAX_METADATA);
    }

    @Override public boolean equals(Object other) {
        return other instanceof BlockState state && state.block == block && state.metadata == metadata;
    }
    @Override public int hashCode() { return block.registryId() * 31 + metadata; }
    @Override public String toString() { return metadata == 0 ? block.toString() : block + "[meta=" + metadata + "]"; }
}
