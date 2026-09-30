package net.minecraft.client.renderer;

import net.minecraft.client.gui.GuiTextures;
import net.minecraft.util.ResourceLocation;

import static org.lwjgl.opengl.GL11.*;

/**
 * The enchantment glint (HYPOTHESIS: recalled constants): the purple
 * {@code misc/enchanted_item_glint.png} added over the item's own pixels
 * (depth test EQUAL, additive {@code SRC_COLOR, ONE} blending) in two
 * scrolling, tilted layers.
 */
public final class Glint {
    public static final ResourceLocation TEXTURE = new ResourceLocation("minecraft:textures/misc/enchanted_item_glint.png");

    private Glint() {}

    /**
     * RenderItem.renderEffect for an item whose texture coordinates span 0-1:
     * the glint in colour 0x8040CC, one layer scrolling across every 3
     * seconds turned -50 degrees, one every 4.873 seconds turned 10 degrees.
     * Vanilla scales atlas coordinates by 8; a 16-pixel sprite is a 32nd of
     * the atlas, so over 0-1 coordinates that is a scale of 1/4.
     */
    public static void item(GuiTextures textures, Runnable draw) {
        long time = System.currentTimeMillis();
        begin(textures);
        glColor4f(0x80 / 255f, 0x40 / 255f, 0xCC / 255f, 1f);
        glMatrixMode(GL_TEXTURE);
        glPushMatrix();
        glScalef(0.25f, 0.25f, 0.25f);
        glTranslatef((time % 3000L) / 3000f / 8f * 32f, 0f, 0f);
        glRotatef(-50f, 0f, 0f, 1f);
        glMatrixMode(GL_MODELVIEW);
        draw.run();
        glMatrixMode(GL_TEXTURE);
        glPopMatrix();
        glPushMatrix();
        glScalef(0.25f, 0.25f, 0.25f);
        glTranslatef(-(time % 4873L) / 4873f / 8f * 32f, 0f, 0f);
        glRotatef(10f, 0f, 0f, 1f);
        glMatrixMode(GL_MODELVIEW);
        draw.run();
        glMatrixMode(GL_TEXTURE);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
        end();
    }

    /**
     * LayerArmorBase.func_177183_a: two passes in colour (0.38, 0.19, 0.61)
     * over a third-size glint turned 30 and -30 degrees, scrolling with the
     * wearer's age ({@code ticks}).
     */
    public static void armor(GuiTextures textures, float ticks, Runnable draw) {
        begin(textures);
        for (int i = 0; i < 2; i++) {
            float f = 0.76f;
            glColor4f(0.5f * f, 0.25f * f, 0.8f * f, 1f);
            glMatrixMode(GL_TEXTURE);
            glLoadIdentity();
            float third = 1f / 3f;
            glScalef(third, third, third);
            glRotatef(30f - i * 60f, 0f, 0f, 1f);
            glTranslatef(0f, ticks * (0.001f + i * 0.003f) * 20f, 0f);
            glMatrixMode(GL_MODELVIEW);
            draw.run();
        }
        glMatrixMode(GL_TEXTURE);
        glLoadIdentity();
        glMatrixMode(GL_MODELVIEW);
        end();
    }

    private static boolean lighting, blend;

    private static void begin(GuiTextures textures) {
        lighting = glIsEnabled(GL_LIGHTING);
        blend = glIsEnabled(GL_BLEND);
        textures.bind(TEXTURE);
        glEnable(GL_BLEND);
        glDepthMask(false);
        glDepthFunc(GL_EQUAL);
        glDisable(GL_LIGHTING);
        glBlendFunc(GL_SRC_COLOR, GL_ONE);
    }

    private static void end() {
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        if (!blend) glDisable(GL_BLEND);
        if (lighting) glEnable(GL_LIGHTING);
        glDepthFunc(GL_LEQUAL);
        glDepthMask(true);
        glColor4f(1f, 1f, 1f, 1f);
    }
}
