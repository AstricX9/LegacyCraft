package net.minecraft.client.renderer;

import net.minecraft.util.ResourceLocation;

import static org.lwjgl.opengl.GL11.*;

/**
 * The enchanting table's book (1.8.9 ModelBook; HYPOTHESIS: recalled boxes),
 * on the 64x32 {@code entity/enchanting_table_book.png}: two covers, a spine,
 * two page blocks and two flipping pages, all hinged on the spine. The
 * caller binds the texture and sets up the pose; units are model pixels as
 * in {@link PlayerModel}.
 */
public final class BookModel {
    public static final ResourceLocation TEXTURE = new ResourceLocation("minecraft:textures/entity/enchanting_table_book.png");
    private static final float TW = 64f, TH = 32f;

    private BookModel() {}

    /**
     * ModelBook.render after setRotationAngles: {@code ticks} sways the covers
     * a little, {@code flipRight} and {@code flipLeft} (0-1) turn the two
     * loose pages across, and {@code open} (0-1) spreads the covers.
     */
    public static void render(float ticks, float flipRight, float flipLeft, float open) {
        float spread = ((float) Math.sin(ticks * 0.02f) * 0.1f + 1.25f) * open;
        float pagesX = (float) Math.sin(spread);
        // coverRight at (0,0,-1) turned PI + spread, coverLeft at (0,0,1) turned -spread.
        part(0f, 0f, -1f, (float) Math.PI + spread, -6f, -5f, 0f, 6, 10, 0, 0, 0);
        part(0f, 0f, 1f, -spread, 0f, -5f, 0f, 6, 10, 0, 16, 0);
        part(pagesX, 0f, 0f, spread, 0f, -4f, -0.99f, 5, 8, 1, 0, 10);
        part(pagesX, 0f, 0f, -spread, 0f, -4f, -0.01f, 5, 8, 1, 12, 10);
        part(pagesX, 0f, 0f, spread - spread * 2f * flipRight, 0f, -4f, 0f, 5, 8, 0, 24, 10);
        part(pagesX, 0f, 0f, spread - spread * 2f * flipLeft, 0f, -4f, 0f, 5, 8, 0, 24, 10);
        part(0f, 0f, 0f, (float) Math.PI / 2f, -1f, -5f, 0f, 2, 10, 0, 12, 0);
    }

    /**
     * The page-flip fraction for one loose page (TileEntityEnchantmentTableRenderer
     * and GuiEnchantment): the flip position's fractional part stretched by
     * 1.6 and shifted -0.3, clamped to 0-1. {@code offset} is 0.25 for the
     * right page and 0.75 for the left.
     */
    public static float flipFraction(float flip, float offset) {
        float f = flip + offset;
        f = (f - (int) f) * 1.6f - 0.3f;
        return Math.max(0f, Math.min(1f, f));
    }

    private static void part(float pivotX, float pivotY, float pivotZ, float angleY, float x, float y, float z,
                             int width, int height, int depth, int u, int v) {
        glPushMatrix();
        glTranslatef(pivotX / 16f, pivotY / 16f, pivotZ / 16f);
        if (angleY != 0f) glRotatef((float) Math.toDegrees(angleY), 0f, 1f, 0f);
        glBegin(GL_QUADS);
        PlayerModel.box(x, y, z, width, height, depth, 0f, u, v, TW, TH);
        glEnd();
        glPopMatrix();
    }
}
