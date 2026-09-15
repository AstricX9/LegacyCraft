package net.minecraft.world.biome;

import net.minecraft.block.Block;
import net.minecraft.util.ResourceLocation;

import java.util.Objects;

/** Immutable climate and terrain surface definition. */
public record Biome(ResourceLocation name, float temperature, float rainfall,
                    double baseHeight, double heightVariation, Block topBlock,
                    Block fillerBlock, int treeChance) {
    public Biome {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(topBlock, "topBlock");
        Objects.requireNonNull(fillerBlock, "fillerBlock");
        if (temperature < 0 || temperature > 2 || rainfall < 0 || rainfall > 1)
            throw new IllegalArgumentException("Climate value outside supported range");
        if (treeChance < 0) throw new IllegalArgumentException("treeChance must be non-negative");
    }
}
