package net.minecraft.client.renderer.block.model;

import net.minecraft.block.BlockRegistry;
import net.minecraft.client.resources.ClasspathResourceManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockModelManagerTest {
    @Test
    void resolvesBlockstateModelsParentsAndTextures() {
        ClasspathResourceManager resources = new ClasspathResourceManager();
        BlockRegistry blocks = BlockRegistry.load(resources);
        BlockModelManager models = new BlockModelManager(resources, blocks);

        BakedModel stone = models.getModel(blocks.get("minecraft:stone"));
        assertEquals(6, stone.quads().size());
        assertTrue(stone.quads().stream()
            .allMatch(quad -> quad.texture().toString().equals("minecraft:textures/blocks/stone.png")));

        BakedModel craftingTable = models.getModel(blocks.get("minecraft:crafting_table"));
        assertEquals(6, craftingTable.quads().size());
        assertTrue(craftingTable.quads().stream()
            .map(BakedQuad::texture)
            .map(Object::toString)
            .anyMatch(texture -> texture.endsWith("crafting_table_top.png")));
    }
}
