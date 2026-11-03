package net.minecraft.client.renderer;

import net.minecraft.client.entity.Player;
import net.minecraft.client.entity.PlayerAnimation;
import net.minecraft.client.gui.GuiTextures;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import static org.lwjgl.opengl.GL11.*;

/**
 * The first-person hand and held item (1.8.9 ItemRenderer.renderItemInFirstPerson,
 * HYPOTHESIS: recalled offsets and curves): drawn over the world after the
 * depth buffer is cleared, with a fixed 70° field of view, the view bob,
 * lights fixed to the world, the light at the player's eyes, a slight
 * sway as the hand trails the view, the attack swing, and the lower-and-
 * raise animation when the held item changes. A blocking sword is turned
 * flat across the view instead of swinging (doBlockTransformations). An
 * empty hand shows the skin's right arm.
 */
public final class FirstPersonRenderer {
    public static final ResourceLocation STEVE = new ResourceLocation("minecraft:textures/entity/steve.png");
    private final GuiTextures textures;
    private final ItemRenderer items;
    private final LocalSkin skin;
    private final PlayerModel model;
    private ItemStack itemToRender;
    private int equippedSlot = -1;
    private float equippedProgress, prevEquippedProgress;

    public FirstPersonRenderer(GuiTextures textures, ItemRenderer items, LocalSkin skin) {
        this(textures, items, skin, false);
    }

    public FirstPersonRenderer(GuiTextures textures, ItemRenderer items, LocalSkin skin, boolean slimArms) {
        this.model = new PlayerModel(slimArms);
        this.textures = textures;
        this.items = items;
        this.skin = skin;
    }

    /**
     * Once per tick: the hand drops (0.4 per tick) while the held stack is
     * a different item or slot, swaps to it once nearly down, then rises.
     */
    public void tick(ItemStack held, int slot) {
        prevEquippedProgress = equippedProgress;
        boolean same = equippedSlot == slot && held == itemToRender;
        if (itemToRender == null && held == null) same = true;
        if (held != null && itemToRender != null && held != itemToRender && held.item() == itemToRender.item()
            && held.damage() == itemToRender.damage()) {
            itemToRender = held;
            same = true;
        }
        float target = same ? 1f : 0f;
        equippedProgress += Math.max(-0.4f, Math.min(0.4f, target - equippedProgress));
        if (equippedProgress < 0.1f) {
            itemToRender = held;
            equippedSlot = slot;
        }
    }

    /** Drops the hand to the bottom at once, to rise again over the next ticks (resetEquippedProgress2: starting to use an item). */
    public void resetEquippedProgress() { equippedProgress = 0f; }

    /** Resets to fully raised (entering a world, respawning). */
    public void reset(ItemStack held, int slot) {
        itemToRender = held;
        equippedSlot = slot;
        equippedProgress = prevEquippedProgress = 1f;
    }

    /**
     * Draws the hand. The caller has set the projection and an identity
     * modelview with the view bob applied; {@code light} is the lightmap
     * colour at the eyes.
     */
    public void render(Player player, PlayerAnimation animation, float partialTicks, float[] light) {
        float equip = 1f - PlayerAnimation.lerp(prevEquippedProgress, equippedProgress, partialTicks);
        float swing = animation.swingProgress(partialTicks);
        float yaw = PlayerAnimation.vanillaYaw(player);
        // Lights turned with the view (pitch, then the player's yaw, as vanilla does for the hand).
        glPushMatrix();
        glRotatef(player.pitch, 1, 0, 0);
        glRotatef(yaw, 0, 1, 0);
        StandardLighting.enable();
        glPopMatrix();
        float armPitch = PlayerAnimation.lerp(animation.prevArmPitch, animation.armPitch, partialTicks);
        float armYaw = PlayerAnimation.lerp(animation.prevArmYaw, animation.armYaw, partialTicks);
        glRotatef((player.pitch - armPitch) * 0.1f, 1, 0, 0);
        glRotatef((yaw - armYaw) * 0.1f, 0, 1, 0);
        glEnable(GL_DEPTH_TEST);
        glDepthMask(true);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glEnable(GL_ALPHA_TEST);
        glAlphaFunc(GL_GREATER, 0.1f);
        glPushMatrix();
        net.minecraft.item.Item.UseAction using = itemToRender != null && player.isUsingItem()
            ? itemToRender.item().useAction() : null;
        if (using == net.minecraft.item.Item.UseAction.EAT || using == net.minecraft.item.Item.UseAction.DRINK) {
            performDrinking(player, partialTicks);
            transformFirstPersonItem(equip, 0f);
            items.renderHeld(itemToRender, ItemRenderer.Held.FIRST_PERSON, light[0], light[1], light[2]);
        } else if (using == net.minecraft.item.Item.UseAction.BOW) {
            // ItemRenderer.doBowTransformations: the bow tilts in and is drawn back, trembling at full draw.
            transformFirstPersonItem(equip, 0f);
            glRotatef(-18f, 0, 0, 1);
            glRotatef(-12f, 0, 1, 0);
            glRotatef(-8f, 1, 0, 0);
            glTranslatef(-0.9f, 0.2f, 0f);
            float drawn = 72000 - (player.itemInUseCount() - partialTicks + 1f);
            float power = drawn / 20f;
            power = (power * power + power * 2f) / 3f;
            if (power > 1f) power = 1f;
            if (power > 0.1f) {
                float tremble = (float) Math.sin((drawn - 0.1f) * 1.3f) * (power - 0.1f);
                glTranslatef(0f, tremble * 0.01f, 0f);
            }
            glTranslatef(0f, 0f, power * 0.1f);
            glScalef(1f, 1f, 1f + power * 0.2f);
            items.renderHeld(itemToRender, ItemRenderer.Held.FIRST_PERSON, light[0], light[1], light[2]);
        } else if (itemToRender != null && player.isBlocking()) {
            transformFirstPersonItem(equip, 0f);
            glTranslatef(-0.5f, 0.2f, 0f);
            glRotatef(30f, 0, 1, 0);
            glRotatef(-80f, 1, 0, 0);
            glRotatef(60f, 0, 1, 0);
            items.renderHeld(itemToRender, ItemRenderer.Held.FIRST_PERSON, light[0], light[1], light[2]);
        } else if (itemToRender != null) {
            float root = (float) Math.sqrt(swing);
            glTranslatef(-0.4f * (float) Math.sin(root * Math.PI), 0.2f * (float) Math.sin(root * Math.PI * 2),
                -0.2f * (float) Math.sin(swing * Math.PI));
            transformFirstPersonItem(equip, swing);
            items.renderHeld(itemToRender, ItemRenderer.Held.FIRST_PERSON, light[0], light[1], light[2]);
        } else if (!player.dead) {
            renderArm(equip, swing, light);
        }
        glPopMatrix();
        StandardLighting.disable();
        glColor4f(1, 1, 1, 1);
    }

    /**
     * ItemRenderer.performDrinking: the item swings up to the mouth over the
     * first fifth of the 32 ticks and bobs there (|cos| x 0.1, every 4 ticks).
     */
    private static void performDrinking(Player player, float partialTicks) {
        float left = player.itemInUseCount() - partialTicks + 1f;
        float progress = left / 32f;
        float bob = Math.abs((float) Math.cos(left / 4f * (float) Math.PI) * 0.1f);
        if (progress >= 0.8f) bob = 0f;
        glTranslatef(0f, bob, 0f);
        float raise = 1f - (float) Math.pow(progress, 27.0);
        glTranslatef(raise * 0.6f, raise * -0.5f, 0f);
        glRotatef(raise * 90f, 0, 1, 0);
        glRotatef(raise * 10f, 1, 0, 0);
        glRotatef(raise * 30f, 0, 0, 1);
    }

    private static void transformFirstPersonItem(float equip, float swing) {
        glTranslatef(0.56f, -0.52f, -0.72f);
        glTranslatef(0f, equip * -0.6f, 0f);
        glRotatef(45f, 0, 1, 0);
        float squared = (float) Math.sin(swing * swing * Math.PI);
        float root = (float) Math.sin(Math.sqrt(swing) * Math.PI);
        glRotatef(squared * -20f, 0, 1, 0);
        glRotatef(root * -20f, 0, 0, 1);
        glRotatef(root * -80f, 1, 0, 0);
        glScalef(0.4f, 0.4f, 0.4f);
    }

    private void renderArm(float equip, float swing, float[] light) {
        float root = (float) Math.sqrt(swing);
        glTranslatef(-0.3f * (float) Math.sin(root * Math.PI), 0.4f * (float) Math.sin(root * Math.PI * 2),
            -0.4f * (float) Math.sin(swing * Math.PI));
        glTranslatef(0.64000005f, -0.6f, -0.71999997f);
        glTranslatef(0f, equip * -0.6f, 0f);
        glRotatef(45f, 0, 1, 0);
        float squared = (float) Math.sin(swing * swing * Math.PI);
        float rootSin = (float) Math.sin(root * Math.PI);
        glRotatef(rootSin * 70f, 0, 1, 0);
        glRotatef(squared * -20f, 0, 0, 1);
        skin.bind(textures);
        glTranslatef(-1f, 3.6f, 3.5f);
        glRotatef(120f, 0, 0, 1);
        glRotatef(200f, 1, 0, 0);
        glRotatef(-135f, 0, 1, 0);
        glTranslatef(5.6f, 0f, 0f);
        glDisable(GL_CULL_FACE);
        glEnable(GL_TEXTURE_2D);
        glColor4f(light[0], light[1], light[2], 1f);
        model.renderRightArmAtRest();
    }
}
