package net.minecraft.item;

import net.minecraft.block.BlockRegistry;
import net.minecraft.client.resources.ClasspathResourceManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemRegistryTest {
    @Test
    void loadsIndexedPerItemDefinitions() {
        var resources = new ClasspathResourceManager();
        var blocks = BlockRegistry.load(resources);
        var items = ItemRegistry.load(resources, blocks);
        assertTrue(items.size() > 500);
        assertEquals(0, items.byId(0).registryId());
        assertEquals(1561, items.get("minecraft:diamond_sword").maxDamage());
        assertEquals(blocks.get("minecraft:stone"), items.get("minecraft:stone").placedBlock());
        assertNotNull(items.get("minecraft:apple").model());
    }
}
