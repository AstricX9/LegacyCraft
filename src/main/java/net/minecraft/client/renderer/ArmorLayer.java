package net.minecraft.client.renderer;

import net.minecraft.client.gui.GuiTextures;
import net.minecraft.inventory.ArmorValues;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import static org.lwjgl.opengl.GL11.*;

/**
 * Worn armour drawn over the player (1.8.9 LayerBipedArmor; HYPOTHESIS:
 * recalled boxes and inflation). Each piece is a ModelBiped on a 64x32
 * armour texture, posed like the player: helmets, chestplates and boots use
 * {@code <material>_layer_1.png} on boxes grown by 1 pixel, leggings use
 * {@code _layer_2.png} grown by 0.5. A helmet covers the head (and the
 * headwear box, grown a further 0.5), a chestplate the body and both arms,
 * leggings the body and both legs, boots both legs. The left arm and leg
 * are the right ones mirrored. Leather is tinted with its default brown
 * (no dyeing yet) and then its untinted overlay is drawn on top. An
 * enchanted piece gets the scrolling glint over it ({@link Glint#armor}).
 */
public final class ArmorLayer {
    /** ItemArmor's default leather colour (10511680). */
    public static final int LEATHER_COLOR = 0xA06540;
    private static final float TEXTURE_WIDTH = 64f, TEXTURE_HEIGHT = 32f;

    private ArmorLayer() {}

    /** The texture name of an armour material ("golden" armour uses "gold"). */
    static String textureMaterial(String material) { return material.equals("golden") ? "gold" : material; }

    /** The texture for a worn piece (ItemArmor / LayerArmorBase.getArmorResource). */
    public static ResourceLocation texture(Item item, boolean overlay) {
        String material = ArmorValues.material(item);
        if (material == null) return null;
        boolean legs = ArmorValues.armorSlot(item) == 1;
        return new ResourceLocation("minecraft:textures/models/armor/" + textureMaterial(material) + "_layer_"
            + (legs ? 2 : 1) + (overlay ? "_overlay" : "") + ".png");
    }

    /**
     * Draws the armour in {@code armor} (0 boots .. 3 helmet) on the posed
     * {@code model}, in the model space {@link PlayerModel#render} uses, with
     * {@code light} as the base colour. Pumpkins and heads in the helmet slot
     * are blocks and are not drawn here.
     */
    public static void render(PlayerModel model, ItemStack[] armor, boolean sneaking, GuiTextures textures, float[] light,
                              float glintTicks) {
        glPushMatrix();
        if (sneaking) glTranslatef(0f, 0.2f, 0f);
        // LayerBipedArmor.doRenderLayer: helmet, chestplate, leggings, boots.
        for (int slot = 3; slot >= 0; slot--) {
            ItemStack stack = armor[slot];
            if (stack == null || !ArmorValues.isArmorPiece(stack.item())) continue;
            Item item = stack.item();
            boolean leather = "leather".equals(ArmorValues.material(item));
            textures.bind(texture(item, false));
            if (leather) {
                glColor4f(light[0] * ((LEATHER_COLOR >> 16 & 255) / 255f), light[1] * ((LEATHER_COLOR >> 8 & 255) / 255f),
                    light[2] * ((LEATHER_COLOR & 255) / 255f), 1f);
                renderPiece(model, slot);
                textures.bind(texture(item, true));
            }
            glColor4f(light[0], light[1], light[2], 1f);
            renderPiece(model, slot);
            if (net.minecraft.enchantment.EnchantmentHelper.hasGlint(stack)) {
                int piece = slot;
                Glint.armor(textures, glintTicks, () -> renderPiece(model, piece));
            }
        }
        glPopMatrix();
    }

    /** The boxes one armour slot covers (LayerBipedArmor.setModelPartVisible). */
    static void renderPiece(PlayerModel model, int slot) {
        float grow = slot == 1 ? 0.5f : 1f;
        switch (slot) {
            case 3 -> {
                part(model.head, false, -4, -8, -4, 8, 8, 8, 0, 0, grow);
                part(model.head, false, -4, -8, -4, 8, 8, 8, 32, 0, grow + 0.5f);
            }
            case 2 -> {
                body(model, grow);
                arms(model, grow);
            }
            case 1 -> {
                body(model, grow);
                legs(model, grow);
            }
            case 0 -> legs(model, grow);
            default -> {}
        }
    }

    private static void body(PlayerModel model, float grow) { part(model.body, false, -4, 0, -2, 8, 12, 4, 16, 16, grow); }

    private static void arms(PlayerModel model, float grow) {
        part(model.rightArm, false, -3, -2, -2, 4, 12, 4, 40, 16, grow);
        part(model.leftArm, true, -3, -2, -2, 4, 12, 4, 40, 16, grow);
    }

    private static void legs(PlayerModel model, float grow) {
        part(model.rightLeg, false, -2, 0, -2, 4, 12, 4, 0, 16, grow);
        part(model.leftLeg, true, -2, 0, -2, 4, 12, 4, 0, 16, grow);
    }

    /**
     * One box at a part's pivot and rotation. A mirrored box (ModelRenderer.mirror,
     * the left limbs) is the right limb's box reflected across x, which also
     * mirrors its texture.
     */
    private static void part(PlayerModel.Part part, boolean mirror, float x, float y, float z, float width, float height,
                             float depth, int u, int v, float grow) {
        glPushMatrix();
        part.transform();
        if (mirror) glScalef(-1f, 1f, 1f);
        glBegin(GL_QUADS);
        PlayerModel.box(x, y, z, width, height, depth, grow, u, v, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        glEnd();
        glPopMatrix();
    }
}
