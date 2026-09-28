package net.minecraft.client.renderer.texture;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Atlas packing and .mcmeta animation schedules (HYPOTHESIS: recalled TextureAtlasSprite timing). */
final class TextureAtlasTest {
    private static JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }

    @Test
    void emptyAnimationSectionStepsThroughEveryFrameEachTick() {
        TextureAtlas.Animation animation = TextureAtlas.Animation.parse(json("{\"animation\":{}}"), 3);
        assertEquals(0, animation.current());
        assertTrue(animation.tick());
        assertEquals(1, animation.current());
        animation.tick();
        animation.tick();
        assertEquals(0, animation.current(), "wraps round");
    }

    @Test
    void frameTimeHoldsEachFrameForThatManyTicks() {
        TextureAtlas.Animation animation = TextureAtlas.Animation.parse(json("{\"animation\":{\"frametime\":3}}"), 16);
        assertFalse(animation.tick());
        assertFalse(animation.tick());
        assertTrue(animation.tick());
        assertEquals(1, animation.current());
    }

    @Test
    void frameListsPlayInTheirOwnOrderAndMayRepeat() {
        TextureAtlas.Animation animation = TextureAtlas.Animation.parse(
            json("{\"animation\":{\"frames\":[16,17,{\"index\":0,\"time\":2},0]}}"), 32);
        assertEquals(16, animation.current(), "fire_layer_0 starts on frame 16");
        animation.tick();
        assertEquals(17, animation.current());
        animation.tick();
        assertEquals(0, animation.current());
        assertFalse(animation.tick(), "frame 0 lasts two ticks");
        assertFalse(animation.tick(), "0 again: same picture, no upload");
        assertEquals(0, animation.current());
    }

    @Test
    void framesOutsideTheStripAreDropped() {
        assertNull(TextureAtlas.Animation.parse(json("{\"animation\":{\"frames\":[5]}}"), 2));
        assertNull(TextureAtlas.Animation.parse(json("{}"), 2));
    }

    @Test
    void interpolationBlendsColourButKeepsAlpha() {
        int mixed = TextureAtlas.mix(0x80000000, 0xffff00ff, 0.5);
        assertEquals(0x80, mixed >>> 24);
        assertEquals(0x7f, mixed >> 16 & 255);
        assertEquals(0, mixed >> 8 & 255);
    }

    @Test
    void thirtyTwoPixelSpritesKeepTheirSizeInTheAtlas() {
        List<int[]> sizes = new ArrayList<>();
        for (int i = 0; i < 40; i++) sizes.add(new int[]{16, 16});
        sizes.add(new int[]{32, 32});
        List<TextureAtlas.Slot> slots = TextureAtlas.pack(sizes);
        TextureAtlas.Slot flow = slots.get(40), atlas = slots.get(slots.size() - 1);
        assertEquals(32, flow.width());
        assertEquals(32, flow.height());
        for (int i = 0; i < 40; i++) {
            TextureAtlas.Slot slot = slots.get(i);
            boolean apart = slot.x() + slot.width() <= flow.x() || flow.x() + flow.width() <= slot.x()
                || slot.y() + slot.height() <= flow.y() || flow.y() + flow.height() <= slot.y();
            assertTrue(apart, "sprite " + i + " overlaps the flow sprite");
        }
        assertTrue(flow.y() + 32 <= atlas.y());
        assertEquals(Integer.bitCount(atlas.y()), 1, "power-of-two height");
    }
}
