package net.minecraft.item;

import net.minecraft.block.BlockRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.client.resources.ClasspathResourceManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The item registry loader: indexed per-item definitions and variant items' placed metadata. */
final class ItemRegistryTest {
    @Test
    void loadsIndexedPerItemDefinitions() {
        var resources = new ClasspathResourceManager();
        var blocks = BlockRegistry.load(resources);
        var items = ItemRegistry.load(resources, blocks);
        assertTrue(items.size() > 500);
        assertEquals(0, items.byId(0).registryId());
        assertEquals(1561, items.get("minecraft:diamond_sword").maxDamage());
        assertEquals(blocks.get("minecraft:stone"), items.get("minecraft:stone").placedBlock());
        assertEquals(blocks.get("minecraft:grass"), items.get("minecraft:grass").placedBlock());
        assertNotNull(items.get("minecraft:apple").model());
    }

    @Test
    void variantItemsPlaceTheirMetadata() {
        var resources = new ClasspathResourceManager();
        var blocks = BlockRegistry.load(resources);
        var items = ItemRegistry.load(resources, blocks);
        assertEquals(BlockState.of(blocks.get("minecraft:planks"), 2), items.get("minecraft:birch_planks").placedState());
        assertEquals(BlockState.of(blocks.get("minecraft:wool"), 14), items.get("minecraft:red_wool").placedState());
        // Player-placed leaves never decay: the item sets the no-decay bit.
        assertEquals(BlockState.of(blocks.get("minecraft:leaves2"), 5), items.get("minecraft:dark_oak_leaves").placedState());
        assertEquals(blocks.get("minecraft:wooden_door"), items.get("minecraft:oak_door").placedBlock());
        assertNull(items.get("minecraft:wheat").placedBlock(), "the wheat item is the crop drop, not seeds");
    }
}
