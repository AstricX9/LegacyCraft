package net.minecraft.testing;

import net.minecraft.block.BlockRegistry;
import net.minecraft.client.resources.ClasspathResourceManager;
import net.minecraft.item.ItemRegistry;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeRegistry;
import net.minecraft.world.gen.GenBlocks;

/**
 * The game data every test shares, loaded once per test run from the
 * bundled resources: blocks, biomes, items and world-generation block
 * tables. The registries are read-only after loading, so sharing them is
 * safe; tests that check the loaders themselves still call
 * {@code BlockRegistry.load} and friends directly.
 */
public final class GameData {
    private GameData() {}

    private static final class Holder {
        static final ClasspathResourceManager RESOURCES = new ClasspathResourceManager();
        static final BlockRegistry BLOCKS = BlockRegistry.load(RESOURCES);
        static final BiomeRegistry BIOMES = BiomeRegistry.load(RESOURCES, BLOCKS);
    }

    private static final class Items {
        static final ItemRegistry ITEMS = ItemRegistry.load(Holder.RESOURCES, Holder.BLOCKS);
    }

    private static final class Gen {
        static final GenBlocks GEN_BLOCKS = new GenBlocks(Holder.BLOCKS);
    }

    public static ClasspathResourceManager resources() { return Holder.RESOURCES; }
    public static BlockRegistry blocks() { return Holder.BLOCKS; }
    public static BiomeRegistry biomes() { return Holder.BIOMES; }
    public static ItemRegistry items() { return Items.ITEMS; }
    public static GenBlocks genBlocks() { return Gen.GEN_BLOCKS; }

    /** A fresh, empty world on the shared registries (chunks generate as they are asked for). */
    public static World newWorld(long seed) { return new World(blocks(), biomes(), seed); }
}
