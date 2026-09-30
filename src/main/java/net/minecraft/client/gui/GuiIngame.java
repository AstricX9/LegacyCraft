package net.minecraft.client.gui;

import net.minecraft.inventory.InventoryPlayer;
import net.minecraft.item.ItemStack;

import static org.lwjgl.opengl.GL11.*;

/**
 * The in-game HUD of 1.8.9 (master context §21): the hotbar with its items
 * and the selection frame, the crosshair, and in Survival/Adventure the
 * hearts, armor, food and experience bar; the held item's name rises above
 * the hotbar for two seconds after switching. Creative hides the survival
 * rows, and the item name sits 14px lower there. Spectator draws no hotbar.
 *
 * <p>Positions and sprite coordinates follow vanilla's GuiIngame layout
 * (HYPOTHESIS until screenshot-compared). Hearts blink white for a second
 * after a change while the player is protected from damage and shake at 2
 * hearts or fewer; bubbles show the air left under water. There is no
 * mount or boss bar yet (DEV-015).
 */
public class GuiIngame extends Gui {
    /** What the HUD shows about the player. */
    public record Status(boolean survivalHud, boolean spectator, boolean hardcore, float health, int food,
                         int armor, int experienceLevel, float experience, boolean underwater, int air, int hurtResistantTime) {
        public Status(boolean survivalHud, boolean spectator, boolean hardcore, float health, int food, int armor,
                      int experienceLevel, float experience) {
            this(survivalHud, spectator, hardcore, health, food, armor, experienceLevel, experience, false, 300, 0);
        }
    }

    /** The heart blink state (GuiIngame.playerHealth / lastPlayerHealth / healthUpdateCounter / lastSystemTime). */
    private int playerHealth, lastPlayerHealth;
    private long healthUpdateCounter, lastSystemTime;
    private final java.util.Random rand = new java.util.Random();

    private final GuiContext mc;
    private int remainingHighlightTicks;
    private ItemStack highlightingItemStack;
    private final GuiNewChat chat;
    private int updateCounter;

    public GuiIngame(GuiContext mc) {
        this.mc = mc;
        this.chat = new GuiNewChat(mc);
    }

    public GuiNewChat getChatGUI() { return chat; }
    /** HUD ticks so far; chat lines age against it. */
    public int updateCounter() { return updateCounter; }

    /** Draws the chat 48 pixels above the bottom of the screen, fully opaque while the chat screen is open. */
    public void renderChat(int height, boolean chatOpen) {
        glPushMatrix();
        glTranslatef(0f, height - 48, 0f);
        chat.drawChat(updateCounter, chatOpen);
        glPopMatrix();
    }

    /** One 20 Hz tick: restart the item-name fade when the held item changes, else count it down. */
    public void updateTick(InventoryPlayer inventory) {
        updateCounter++;
        ItemStack held = inventory.getCurrentItem();
        if (held == null) {
            remainingHighlightTicks = 0;
        } else if (highlightingItemStack != null && held.item() == highlightingItemStack.item()
            && (held.item().isDamageable() || held.damage() == highlightingItemStack.damage())) {
            if (remainingHighlightTicks > 0) remainingHighlightTicks--;
        } else {
            remainingHighlightTicks = 40;
        }
        highlightingItemStack = held;
    }

    public int remainingHighlightTicks() { return remainingHighlightTicks; }

    /** Alpha (0-255) of the held item's name: full for the first 30 ticks, then fading over half a second. */
    public static int highlightAlpha(int remainingTicks) { return Math.min(255, (int) (remainingTicks * 256.0f / 10.0f)); }

    /** The GUI-space y of the held item name: 59px above the bottom, 14px lower without the survival rows. */
    public static int highlightY(int screenHeight, boolean survivalHud) { return screenHeight - 59 + (survivalHud ? 0 : 14); }

    /** Pixel width of the experience bar fill for a progress fraction. */
    public static int experienceFill(float experience) { return (int) (experience * 183.0f); }

    /**
     * Draws the HUD. {@code crosshair} is false while the F3 screen draws its
     * axis crosshair in its place.
     */
    public void renderGameOverlay(int width, int height, InventoryPlayer inventory, Status status, boolean crosshair) {
        glDisable(GL_DEPTH_TEST);
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(1, 1, 1, 1);
        if (!status.spectator()) renderHotbar(width, height, inventory);

        mc.textures().bind(GuiTextures.ICONS);
        glColor4f(1, 1, 1, 1);
        if (crosshair) {
            glBlendFunc(GL_ONE_MINUS_DST_COLOR, GL_ONE_MINUS_SRC_COLOR);
            drawTexturedModalRect(width / 2 - 7, height / 2 - 7, 0, 0, 16, 16);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        }
        if (status.survivalHud()) {
            renderPlayerStats(width, height, status);
            renderExpBar(width, height, status);
        }
        if (mc.settings().heldItemTooltips && !status.spectator()) renderSelectedItem(width, height, status.survivalHud());
        glColor4f(1, 1, 1, 1);
    }

    private void renderHotbar(int width, int height, InventoryPlayer inventory) {
        mc.textures().bind(GuiTextures.WIDGETS);
        int center = width / 2;
        drawTexturedModalRect(center - 91, height - 22, 0, 0, 182, 22);
        drawTexturedModalRect(center - 91 - 1 + inventory.currentItem * 20, height - 22 - 1, 0, 22, 24, 22);
        if (mc.itemRenderer() == null) return;
        glClear(GL_DEPTH_BUFFER_BIT);
        for (int slot = 0; slot < InventoryPlayer.HOTBAR_SIZE; slot++) {
            ItemStack stack = inventory.mainInventory[slot];
            if (stack == null) continue;
            int x = center - 90 + slot * 20 + 2, y = height - 16 - 3;
            mc.itemRenderer().renderItemAndEffectIntoGUI(stack, x, y);
            mc.itemRenderer().renderItemOverlays(mc.font(), stack, x, y);
        }
        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(1, 1, 1, 1);
    }

    /** Hearts bottom-left above the hotbar, food bottom-right, armor above the hearts (icons.png). */
    private void renderPlayerStats(int width, int height, Status status) {
        mc.textures().bind(GuiTextures.ICONS);
        glColor4f(1, 1, 1, 1);
        int health = (int) Math.ceil(status.health());
        int left = width / 2 - 91, right = width / 2 + 91, top = height - 39;
        int armor = status.armor();
        if (armor > 0) {
            for (int i = 0; i < 10; i++) {
                int x = left + i * 8, y = top - 10;
                if (i * 2 + 1 < armor) drawTexturedModalRect(x, y, 34, 9, 9, 9);
                else if (i * 2 + 1 == armor) drawTexturedModalRect(x, y, 25, 9, 9, 9);
                else drawTexturedModalRect(x, y, 16, 9, 9, 9);
            }
        }
        // GuiIngame.renderPlayerStats: a change while protected from damage blinks the hearts (20 ticks when
        // hurt, 10 when healed), showing the previous health in white; at 4 health or less they shake.
        long now = System.currentTimeMillis();
        if (health < playerHealth && status.hurtResistantTime() > 0) {
            lastSystemTime = now;
            healthUpdateCounter = updateCounter + 20;
        } else if (health > playerHealth && status.hurtResistantTime() > 0) {
            lastSystemTime = now;
            healthUpdateCounter = updateCounter + 10;
        }
        if (now - lastSystemTime > 1000L) {
            playerHealth = health;
            lastPlayerHealth = health;
            lastSystemTime = now;
        }
        playerHealth = health;
        int previous = lastPlayerHealth;
        boolean blink = healthUpdateCounter > updateCounter && (healthUpdateCounter - updateCounter) / 3L % 2L == 1L;
        rand.setSeed(updateCounter * 312871L);
        int heartV = status.hardcore() ? 45 : 0;
        for (int i = 9; i >= 0; i--) {
            int x = left + i * 8, y = top;
            if (health <= 4) y += rand.nextInt(2);
            drawTexturedModalRect(x, y, 16 + (blink ? 9 : 0), heartV, 9, 9);
            if (blink) {
                if (i * 2 + 1 < previous) drawTexturedModalRect(x, y, 16 + 54, heartV, 9, 9);
                if (i * 2 + 1 == previous) drawTexturedModalRect(x, y, 16 + 63, heartV, 9, 9);
            }
            if (i * 2 + 1 < health) drawTexturedModalRect(x, y, 16 + 36, heartV, 9, 9);
            if (i * 2 + 1 == health) drawTexturedModalRect(x, y, 16 + 45, heartV, 9, 9);
        }
        for (int i = 0; i < 10; i++) {
            int x = right - i * 8 - 9;
            drawTexturedModalRect(x, top, 16, 27, 9, 9);
            if (i * 2 + 1 < status.food()) drawTexturedModalRect(x, top, 16 + 36, 27, 9, 9);
            else if (i * 2 + 1 == status.food()) drawTexturedModalRect(x, top, 16 + 45, 27, 9, 9);
        }
        // Air bubbles above the food while the eyes are under water: full ones, then one popping.
        if (status.underwater()) {
            int air = status.air();
            int full = (int) Math.ceil((air - 2) * 10.0 / 300.0);
            int popping = (int) Math.ceil(air * 10.0 / 300.0) - full;
            for (int i = 0; i < full + popping; i++)
                drawTexturedModalRect(right - i * 8 - 9, top - 10, i < full ? 16 : 25, 18, 9, 9);
        }
    }

    /** The green bar above the hotbar and the level number in 0x80FF20 with a black outline. */
    private void renderExpBar(int width, int height, Status status) {
        mc.textures().bind(GuiTextures.ICONS);
        glColor4f(1, 1, 1, 1);
        int x = width / 2 - 91, y = height - 32 + 3;
        drawTexturedModalRect(x, y, 0, 64, 182, 5);
        int fill = experienceFill(status.experience());
        if (fill > 0) drawTexturedModalRect(x, y, 0, 69, fill, 5);
        if (status.experienceLevel() > 0) {
            FontRenderer font = mc.font();
            String level = String.valueOf(status.experienceLevel());
            int textX = (width - font.getStringWidth(level)) / 2, textY = height - 31 - 4;
            font.drawString(level, textX + 1, textY, 0);
            font.drawString(level, textX - 1, textY, 0);
            font.drawString(level, textX, textY + 1, 0);
            font.drawString(level, textX, textY - 1, 0);
            font.drawString(level, textX, textY, 0x80FF20);
        }
    }

    private void renderSelectedItem(int width, int height, boolean survivalHud) {
        if (remainingHighlightTicks <= 0 || highlightingItemStack == null) return;
        String name = mc.stackName(highlightingItemStack);
        if (highlightingItemStack.hasCustomName()) name = FontRenderer.FORMAT_CHAR + "o" + name;
        int alpha = highlightAlpha(remainingHighlightTicks);
        if (alpha <= 0) return;
        int x = (width - mc.font().getStringWidth(name)) / 2;
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        mc.font().drawStringWithShadow(name, x, highlightY(height, survivalHud), 0xFFFFFF | alpha << 24);
    }
}
