package net.minecraft.block;

import net.minecraft.client.resources.ClasspathResourceManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockRegistryTest {
    @Test
    void discoversTheSuppliedBlockstatePack() {
        BlockRegistry registry = BlockRegistry.load(new ClasspathResourceManager());
        assertTrue(registry.size() > 300);
        assertEquals(0, registry.air().registryId());
        assertFalse(registry.get("minecraft:water").isSolid());
        assertEquals(-1f, registry.get("minecraft:bedrock").hardness());
    }
}
