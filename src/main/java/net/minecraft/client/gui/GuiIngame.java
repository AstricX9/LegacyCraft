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
 * hearts or fewer; bubbles show the air left under water. The boss bar
 * shows while a boss was drawn in the last 100 frames. There is no mount
 * bar yet (DEV-015).
 */
public class GuiIngame extends Gui {
    /** What the HUD shows about the player. */
    /**
     * What the HUD shows. {@code saturation} shakes the food bar when it is
     * gone; {@code absorption} adds golden hearts; the effect flags pick the
     * heart and food icons (poison green, wither black, hunger green) and
     * regeneration's travelling heart.
     */
    public record Status(boolean survivalHud, boolean spectator, boolean hardcore, float health, int food,
                         int armor, int experienceLevel, float experience, boolean underwater, int air, int hurtResistantTime,
                         float saturation, float absorption, boolean poisoned, boolean withered, boolean regenerating,
                         boolean hungry) {
        public Status(boolean survivalHud, boolean spectator, boolean hardcore, float health, int food, int armor,
                      int experienceLevel, float experience, boolean underwater, int air, int hurtResistantTime) {
            this(survivalHud, spectator, hardcore, health, food, armor, experienceLevel, experience, underwater, air,
                hurtResistantTime, 5f, 0f, false, false, false, false);
        }

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
    /** BossStatus: the boss's name and health share, and frames left to show the bar. */
    private String bossName;
    private float bossHealthScale;
    private int bossStatusTime;

    /** BossStatus.setBossStatus, called whenever a boss is drawn. */
    public void setBossStatus(String name, float healthScale) {
        bossName = name;
        bossHealthScale = healthScale;
        bossStatusTime = 100;
    }

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
        if (crosshair && mc.additions().isOn(net.minecraft.client.additions.Additions.SOLID_CROSSHAIR)) {
            // Solid Crosshair: a dark outline (the cross shifted a pixel each way), then the cross in white.
            glColor4f(0.1f, 0.1f, 0.1f, 1);
            for (int[] d : new int[][] {{-1, 0}, {1, 0}, {0, -1}, {0, 1}})
                drawTexturedModalRect(width / 2 - 7 + d[0], height / 2 - 7 + d[1], 0, 0, 16, 16);
            glColor4f(1, 1, 1, 1);
            drawTexturedModalRect(width / 2 - 7, height / 2 - 7, 0, 0, 16, 16);
        } else if (crosshair) {
            glBlendFunc(GL_ONE_MINUS_DST_COLOR, GL_ONE_MINUS_SRC_COLOR);
            drawTexturedModalRect(width / 2 - 7, height / 2 - 7, 0, 0, 16, 16);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        }
        renderBossHealth(width);
        if (status.survivalHud()) {
            renderPlayerStats(width, height, status);
            renderExpBar(width, height, status);
        }
        if (mc.settings().heldItemTooltips && !status.spectator()) renderSelectedItem(width, height, status.survivalHud());
        glColor4f(1, 1, 1, 1);
    }

    /** GuiIngame.renderBossHealth: a 182-pixel bar 12 pixels from the top, the name above it. */
    private void renderBossHealth(int width) {
        if (bossName == null || bossStatusTime <= 0) return;
        bossStatusTime--;
        int left = width / 2 - 91, top = 12;
        int filled = (int) (bossHealthScale * 183f);
        drawTexturedModalRect(left, top, 0, 74, 182, 5);
        drawTexturedModalRect(left, top, 0, 74, 182, 5);
        if (filled > 0) drawTexturedModalRect(left, top, 0, 79, filled, 5);
        mc.font().drawStringWithShadow(bossName, width / 2 - mc.font().getStringWidth(bossName) / 2, top - 10, 0xFFFFFF);
        glColor4f(1, 1, 1, 1);
        mc.textures().bind(GuiTextures.ICONS);
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

    /**
     * Hearts bottom-left above the hotbar, food bottom-right, armor above the
     * hearts (icons.png). Absorption adds golden hearts after the red ones,
     * in more rows when they don't fit, rows drawing closer together as they
     * pile up (at least 3 pixels apart); the armour and air rows move up above them.
     */
    private void renderPlayerStats(int width, int height, Status status) {
        mc.textures().bind(GuiTextures.ICONS);
        glColor4f(1, 1, 1, 1);
        int health = (int) Math.ceil(status.health());
        int left = width / 2 - 91, right = width / 2 + 91, top = height - 39;
        float maxHealth = 20f, absorption = status.absorption();
        int rows = (int) Math.ceil((maxHealth + absorption) / 2f / 10f);
        int rowHeight = Math.max(10 - (rows - 2), 3);
        int armorY = top - (rows - 1) * rowHeight - 10;
        int regenHeart = status.regenerating() ? updateCounter % (int) Math.ceil(maxHealth + 5f) : -1;
        int armor = status.armor();
        if (armor > 0) {
            for (int i = 0; i < 10; i++) {
                int x = left + i * 8, y = armorY;
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
        int heartU = 16 + (status.poisoned() ? 36 : status.withered() ? 72 : 0);
        float absorptionLeft = absorption;
        for (int i = (int) Math.ceil((maxHealth + absorption) / 2f) - 1; i >= 0; i--) {
            int row = (int) Math.ceil((i + 1) / 10f) - 1;
            int x = left + i % 10 * 8, y = top - row * rowHeight;
            if (health <= 4) y += rand.nextInt(2);
            if (i == regenHeart) y -= 2;
            drawTexturedModalRect(x, y, 16 + (blink ? 9 : 0), heartV, 9, 9);
            if (blink) {
                if (i * 2 + 1 < previous) drawTexturedModalRect(x, y, heartU + 54, heartV, 9, 9);
                if (i * 2 + 1 == previous) drawTexturedModalRect(x, y, heartU + 63, heartV, 9, 9);
            }
            if (absorptionLeft > 0f) {
                if (absorptionLeft == absorption && absorption % 2f == 1f) drawTexturedModalRect(x, y, heartU + 153, heartV, 9, 9);
                else drawTexturedModalRect(x, y, heartU + 144, heartV, 9, 9);
                absorptionLeft -= 2f;
            } else {
                if (i * 2 + 1 < health) drawTexturedModalRect(x, y, heartU + 36, heartV, 9, 9);
                if (i * 2 + 1 == health) drawTexturedModalRect(x, y, heartU + 45, heartV, 9, 9);
            }
        }
        // Food: green shanks with Hunger; with no saturation left the bar jitters, faster the emptier it is.
        int food = status.food();
        int foodU = 16 + (status.hungry() ? 36 : 0), foodBackground = status.hungry() ? 13 : 0;
        for (int i = 0; i < 10; i++) {
            int x = right - i * 8 - 9, y = top;
            if (status.saturation() <= 0f && updateCounter % (food * 3 + 1) == 0) y = top + (rand.nextInt(3) - 1);
            drawTexturedModalRect(x, y, 16 + foodBackground * 9, 27, 9, 9);
            if (i * 2 + 1 < food) drawTexturedModalRect(x, y, foodU + 36, 27, 9, 9);
            else if (i * 2 + 1 == food) drawTexturedModalRect(x, y, foodU + 45, 27, 9, 9);
        }
        // Air bubbles above the food while the eyes are under water: full ones, then one popping.
        if (status.underwater()) {
            int air = status.air();
            int full = (int) Math.ceil((air - 2) * 10.0 / 300.0);
            int popping = (int) Math.ceil(air * 10.0 / 300.0) - full;
            for (int i = 0; i < full + popping; i++)
                drawTexturedModalRect(right - i * 8 - 9, armorY, i < full ? 16 : 25, 18, 9, 9);
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
