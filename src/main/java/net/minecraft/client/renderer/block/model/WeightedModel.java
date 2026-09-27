package net.minecraft.client.renderer.block.model;

import java.util.List;

/**
 * The models listed for one blockstate variant. A single entry is used as-is;
 * a list is a weighted random pick keyed by block position, so the same
 * position always shows the same variant (grass and stone texture rotation).
 */
public record WeightedModel(List<Entry> entries) {
    public record Entry(BakedModel model, int weight) {}

    public WeightedModel {
        entries = List.copyOf(entries);
        if (entries.isEmpty()) throw new IllegalArgumentException("A variant needs at least one model");
        for (Entry entry : entries) if (entry.weight() < 1) throw new IllegalArgumentException("Variant weights must be positive");
    }

    public static WeightedModel of(BakedModel model) { return new WeightedModel(List.of(new Entry(model, 1))); }

    public BakedModel first() { return entries.get(0).model(); }

    public BakedModel select(int x, int y, int z) {
        if (entries.size() == 1) return first();
        int total = 0;
        for (Entry entry : entries) total += entry.weight();
        int roll = Math.abs((int) positionSeed(x, y, z) >> 16) % total;
        for (Entry entry : entries) {
            roll -= entry.weight();
            if (roll < 0) return entry.model();
        }
        return first();
    }

    /**
     * Per-position seed for random model variants. HYPOTHESIS: reproduces the
     * 1.8 coordinate hash (int multiply wraps before widening); it must be
     * confirmed by screenshot comparison (master context §85).
     */
    public static long positionSeed(int x, int y, int z) {
        long seed = (long) (x * 3129871) ^ (long) z * 116129781L ^ (long) y;
        return seed * seed * 42317861L + seed * 11L;
    }
}
