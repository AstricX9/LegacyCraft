package net.minecraft.block;

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
 * <p>This class only carries the (block, metadata) identity. It does not yet
 * interpret what a given metadata value <i>means</i> for a given block
 * (facing, half, color, ...) — that per-block semantics is still open, see
 * {@code docs/compat/unknowns.yml}. Every existing call site that only knows
 * about {@code Block} keeps working unchanged and implicitly means metadata
 * 0; nothing currently sets a non-zero metadata, so this change is additive
 * plumbing, not yet a behavior change.
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
        return new BlockState(block, metadata);
    }

    public static BlockState defaultOf(Block block) { return of(block, 0); }

    public Block block() { return block; }
    public int metadata() { return metadata; }

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
