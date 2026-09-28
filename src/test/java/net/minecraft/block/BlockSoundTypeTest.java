package net.minecraft.block;

import net.minecraft.testing.GameData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Which sound set blocks use (HYPOTHESIS: recalled 1.8.9 Block.SoundType assignments). */
final class BlockSoundTypeTest {
    private static BlockSoundType of(String name) { return BlockSoundType.of(GameData.blocks().get("minecraft:" + name)); }

    @Test
    void commonBlocksUseTheirMaterialsSounds() {
        assertEquals(BlockSoundType.STONE, of("stone"));
        assertEquals(BlockSoundType.WOOD, of("planks"));
        assertEquals(BlockSoundType.GRASS, of("grass"));
        assertEquals(BlockSoundType.GRAVEL, of("dirt"));
        assertEquals(BlockSoundType.SAND, of("sand"));
        assertEquals(BlockSoundType.CLOTH, of("wool"));
        assertEquals(BlockSoundType.GRASS, of("leaves"));
        assertEquals(BlockSoundType.SNOW, of("snow_layer"));
    }

    @Test
    void specialSetsBreakDifferentlyFromHowTheyStep() {
        assertEquals("dig.glass", of("glass").breakSound());
        assertEquals("step.stone", of("glass").stepSound());
        assertEquals("dig.wood", of("ladder").breakSound());
        assertEquals("step.ladder", of("ladder").stepSound());
        assertEquals("random.anvil_land", of("anvil").placeSound());
        assertEquals(1.5f, of("iron_block").frequency());
        assertEquals(BlockSoundType.WOOD, of("torch"));
    }

    /** Every event named is one 1.8.9's sounds.json defines (checked against the asset list). */
    @Test
    void everyBlockNamesARealSoundEvent() {
        java.util.Set<String> known = java.util.Set.of("dig.cloth", "dig.glass", "dig.grass", "dig.gravel", "dig.sand",
            "dig.snow", "dig.stone", "dig.wood", "step.cloth", "step.grass", "step.gravel", "step.ladder", "step.sand",
            "step.snow", "step.stone", "step.wood", "random.anvil_land", "mob.slime.big", "mob.slime.small");
        for (Block block : GameData.blocks().values()) {
            BlockSoundType type = BlockSoundType.of(block);
            for (String event : new String[]{type.breakSound(), type.stepSound(), type.placeSound()})
                assertTrue(known.contains(event), block + ": " + event);
        }
    }
}
