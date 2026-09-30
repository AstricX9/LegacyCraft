package net.minecraft.client.renderer;

import net.minecraft.item.TestItems;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Which armour texture a worn piece uses (HYPOTHESIS: recalled LayerArmorBase.getArmorResource). */
final class ArmorLayerTest {
    private static String texture(String item, boolean overlay) {
        var location = ArmorLayer.texture(TestItems.item(item, 1, 100), overlay);
        return location == null ? null : location.toString();
    }

    @Test
    void leggingsUseTheSecondLayerAndEverythingElseTheFirst() {
        assertEquals("minecraft:textures/models/armor/iron_layer_1.png", texture("iron_helmet", false));
        assertEquals("minecraft:textures/models/armor/diamond_layer_1.png", texture("diamond_chestplate", false));
        assertEquals("minecraft:textures/models/armor/chainmail_layer_2.png", texture("chainmail_leggings", false));
        assertEquals("minecraft:textures/models/armor/iron_layer_1.png", texture("iron_boots", false));
    }

    @Test
    void goldenArmourUsesTheGoldTexturesAndLeatherHasAnOverlay() {
        assertEquals("minecraft:textures/models/armor/gold_layer_2.png", texture("golden_leggings", false));
        assertEquals("minecraft:textures/models/armor/leather_layer_1_overlay.png", texture("leather_helmet", true));
        assertNull(texture("pumpkin", false));
    }
}
