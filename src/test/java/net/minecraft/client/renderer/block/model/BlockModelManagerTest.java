package net.minecraft.client.renderer.block.model;

import net.minecraft.block.BlockRegistry;
import net.minecraft.client.resources.ClasspathResourceManager;
import net.minecraft.testing.GameData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The block model manager resolves blockstate variants, model parents and textures into baked quads. */
final class BlockModelManagerTest {
    @Test
    void resolvesBlockstateModelsParentsAndTextures() {
        ClasspathResourceManager resources = GameData.resources();
        BlockRegistry blocks = GameData.blocks();
        BlockModelManager models = new BlockModelManager(resources, blocks);

        BakedModel stone = models.getModel(blocks.get("minecraft:stone").defaultState());
        assertEquals(6, stone.quads().size());
        assertTrue(stone.quads().stream()
            .allMatch(quad -> quad.texture().toString().equals("minecraft:textures/blocks/stone.png")));

        BakedModel craftingTable = models.getModel(blocks.get("minecraft:crafting_table").defaultState());
        assertEquals(6, craftingTable.quads().size());
        assertTrue(craftingTable.quads().stream()
            .map(BakedQuad::texture)
            .map(Object::toString)
            .anyMatch(texture -> texture.endsWith("crafting_table_top.png")));
    }
}
