package net.minecraft.world.storage;

import net.minecraft.block.BlockRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.testing.GameData;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Saving and loading chunks, and refusing unknown block IDs. */
final class WorldStorageTest {
    @TempDir Path directory;

    @Test
    void persistsChunkDataAndGenerationIndexes() throws Exception {
        BlockRegistry blocks = GameData.blocks();
        BiomeRegistry biomes = GameData.biomes();
        World original = GameData.newWorld(42L);
        original.set(34, 90, -18, blocks.get("minecraft:brick_block"));
        var state = BlockState.of(blocks.byId(blocks.size() - 1), 15);
        original.setState(35, 90, -18, state);
        long index = original.getChunk(2, -2).generationIndex();
        WorldStorage.save(directory, original);

        assertTrue(WorldStorage.exists(directory));
        World restored = WorldStorage.load(directory, blocks, biomes);
        assertEquals(42L, restored.seed());
        assertEquals(index, restored.getChunk(2, -2).generationIndex());
        assertEquals(blocks.get("minecraft:brick_block"), restored.get(34, 90, -18));
        assertEquals(state, restored.getState(35, 90, -18));
    }

    @Test
    void persistsFurnaceContentsAndFire() throws Exception {
        BlockRegistry blocks = GameData.blocks();
        BiomeRegistry biomes = GameData.biomes();
        var recipes = net.minecraft.item.crafting.FurnaceRecipes.load(GameData.resources(), GameData.items());
        java.util.function.Function<String, net.minecraft.item.Item> items =
            name -> GameData.items().get(new net.minecraft.util.ResourceLocation("minecraft", name));
        World original = GameData.newWorld(42L);
        original.setSmelting(recipes, items);
        original.setState(3, 100, 4, BlockState.of(blocks.get("minecraft:furnace"), 2));
        var furnace = original.furnaceAt(3, 100, 4);
        furnace.set(net.minecraft.inventory.Furnace.INPUT, new net.minecraft.item.ItemStack(items.apply("iron_ore"), 7));
        furnace.set(net.minecraft.inventory.Furnace.FUEL, new net.minecraft.item.ItemStack(items.apply("coal"), 3));
        for (int i = 0; i < 250; i++) original.tick();
        WorldStorage.save(directory, original);

        World restored = WorldStorage.load(directory, blocks, seed -> {
            World world = new World(blocks, biomes, seed);
            world.setSmelting(recipes, items);
            return world;
        });
        var loaded = restored.furnaceAt(3, 100, 4);
        assertEquals(furnace.burnTime(), loaded.burnTime());
        assertEquals(furnace.cookTime(), loaded.cookTime());
        assertEquals(6, loaded.get(net.minecraft.inventory.Furnace.INPUT).count());
        assertEquals(2, loaded.get(net.minecraft.inventory.Furnace.FUEL).count());
        assertEquals(items.apply("iron_ingot"), loaded.get(net.minecraft.inventory.Furnace.OUTPUT).item());
    }

    @Test
    void rejectsPackedStatesWithUnknownBlockIds() throws Exception {
        BlockRegistry blocks = GameData.blocks();
        BiomeRegistry biomes = GameData.biomes();
        World world = GameData.newWorld(42L);
        world.getChunk(0, 0).set(0, 90, 0,
            (blocks.size() << BlockState.METADATA_BITS) | 15);
        WorldStorage.save(directory, world);

        IOException error = assertThrows(IOException.class, () -> WorldStorage.load(directory, blocks, biomes));
        assertTrue(error.getMessage().contains("Unknown block ID " + blocks.size()));
    }
}
