package net.minecraft.world.biome;

import net.minecraft.block.BlockState;
import net.minecraft.util.ResourceLocation;

import java.util.Objects;

/**
 * One 1.8.9 biome: its climate, terrain shape and surface, its colour
 * overrides and how much it decorates. Grass and foliage colours normally
 * come from the vanilla colour maps via {@link #temperature}/{@link #rainfall};
 * a few biomes override or tint them (swamp, mesa, roofed forest).
 *
 * @param depth base height above sea level (vanilla "depth"), scale how hilly it is
 * @param grassColor fixed grass colour, or -1 to use the colour map
 * @param grassMix colour mixed into the map colour (roofed forest), or -1
 */
public record Biome(int id, ResourceLocation name, float temperature, float rainfall, double depth, double scale,
                    BlockState topBlock, BlockState fillerBlock, boolean snow, boolean rain,
                    int grassColor, int foliageColor, int grassMix, int waterColor,
                    int treesPerChunk, int grassPerChunk, int flowersPerChunk, int cactiPerChunk, int mushroomsPerChunk) {
    public Biome {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(topBlock, "topBlock");
        Objects.requireNonNull(fillerBlock, "fillerBlock");
        if (temperature < -1 || temperature > 2 || rainfall < 0 || rainfall > 1)
            throw new IllegalArgumentException("Climate value outside supported range for " + name);
    }

    /** Whether snow falls (and water freezes) here: vanilla treats anything below 0.15 as cold. */
    public boolean isCold() { return snow || temperature < .15f; }
}
