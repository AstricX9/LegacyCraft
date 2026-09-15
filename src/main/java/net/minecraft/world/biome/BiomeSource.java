package net.minecraft.world.biome;

import net.minecraft.block.BlockRegistry;
import net.minecraft.util.ResourceLocation;

/** Deterministic climate sampler that selects a small legacy overworld biome set. */
public final class BiomeSource {
    private final long seed;
    private final Biome plains, forest, desert, taiga;

    public BiomeSource(BlockRegistry blocks, long seed) {
        this.seed = seed;
        plains = new Biome(id("plains"), .8f, .4f, 65, 7,
            blocks.get("minecraft:grass"), blocks.get("minecraft:dirt"), 110);
        forest = new Biome(id("forest"), .7f, .8f, 68, 9,
            blocks.get("minecraft:grass"), blocks.get("minecraft:dirt"), 28);
        desert = new Biome(id("desert"), 2f, 0f, 64, 5,
            blocks.get("minecraft:sand"), blocks.get("minecraft:sand"), 0);
        taiga = new Biome(id("taiga"), .25f, .8f, 70, 12,
            blocks.get("minecraft:grass"), blocks.get("minecraft:dirt"), 45);
    }

    public Biome sample(int blockX, int blockZ) {
        if (Math.hypot(blockX, blockZ) < 12) return plains;
        double temperature = noise(blockX, blockZ, 17);
        double rainfall = noise(blockX, blockZ, 53);
        if (temperature > .35 && rainfall < -.15) return desert;
        if (temperature < -.42) return taiga;
        if (rainfall > .30) return forest;
        return plains;
    }

    private double noise(int x, int z, int salt) {
        int cellX = Math.floorDiv(x, 96), cellZ = Math.floorDiv(z, 96);
        long value = seed + cellX * 341873128712L + cellZ * 132897987541L + salt * 31L;
        value = (value ^ value >>> 13) * 1274126177L;
        return ((value ^ value >>> 16) & 0xffff) / 32767.5 - 1;
    }
    private static ResourceLocation id(String path) { return new ResourceLocation("minecraft", path); }
}
