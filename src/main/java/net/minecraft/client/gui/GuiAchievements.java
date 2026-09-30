package net.minecraft.client.gui;

import net.minecraft.client.settings.KeyCodes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.stats.Achievement;
import net.minecraft.stats.AchievementList;
import net.minecraft.stats.StatFile;
import net.minecraft.util.ResourceLocation;

import java.util.List;
import java.util.Random;

import static org.lwjgl.opengl.GL11.*;

/**
 * The achievement map (1.8.9 GuiAchievements, HYPOTHESIS: recalled layout
 * and colours). A 224x155 window onto a board of 24-pixel cells, dragged
 * with the left button and zoomed out with the wheel (1x to 2x). The
 * backdrop is rows of block textures picked at random per player: sand at
 * the top, dirt and stone deeper, ores, bedrock at the bottom, each row
 * darker. Lines join each achievement to its parent (grey once earned, green
 * when next up, black otherwise, with an arrow at the child); icons sit in a
 * square frame (spiky for special ones), full brightness when earned,
 * pulsing when they can be earned next, and ever darker the more steps away
 * they are, hidden beyond four. Hovering shows the name and description, or
 * what it needs first.
 */
public class GuiAchievements extends GuiScreen {
    static final ResourceLocation BACKGROUND = GuiAchievementToast.BACKGROUND;
    private static final int IMAGE_WIDTH = 256, IMAGE_HEIGHT = 202;
    static final int MIN_X = AchievementList.MIN_COLUMN * 24 - 112, MIN_Y = AchievementList.MIN_ROW * 24 - 112;
    static final int MAX_X = AchievementList.MAX_COLUMN * 24 - 77, MAX_Y = AchievementList.MAX_ROW * 24 - 77;
    private final GuiScreen parent;
    /** Map offset (the top-left of the window in board pixels) and zoom. */
    double offsetX, offsetY;
    float zoom = 1f;
    private boolean buttonHeld;
    private int dragState, lastMouseX, lastMouseY;

    public GuiAchievements(GuiScreen parent) {
        this.parent = parent;
        Achievement first = AchievementList.get("openInventory");
        offsetX = first.column() * 24 - 141 / 2 - 12;
        offsetY = first.row() * 24 - 141 / 2;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        buttonList.add(new GuiButton(1, width / 2 + 24, height / 2 + 74, 80, 20, I18n.format("gui.done")));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 1) mc.displayGuiScreen(parent);
    }

    @Override
    public void keyPressed(int key, int modifiers) {
        int legacy = KeyCodes.fromGlfwKey(key);
        if (legacy != KeyCodes.NONE && legacy == mc.settings().keyBindInventory.getKeyCode()) {
            mc.displayGuiScreen(null);
            return;
        }
        super.keyPressed(key, modifiers);
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        if (button == 0) buttonHeld = true;
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int button) {
        super.mouseReleased(mouseX, mouseY, button);
        if (button == 0) buttonHeld = false;
    }

    /** The wheel zooms out (down) or in (up) by a quarter, keeping the window's centre in place. */
    @Override
    public void mouseScrolled(int mouseX, int mouseY, int direction) {
        float before = zoom;
        if (direction < 0) zoom += 0.25f;
        else if (direction > 0) zoom -= 0.25f;
        zoom = Math.max(1f, Math.min(2f, zoom));
        if (zoom != before) {
            offsetX -= (zoom * IMAGE_WIDTH - before * IMAGE_WIDTH) * 0.5f;
            offsetY -= (zoom * IMAGE_HEIGHT - before * IMAGE_HEIGHT) * 0.5f;
        }
        clamp();
    }

    /** While the left button is held inside the window, the board follows the pointer. */
    void drag(int mouseX, int mouseY) {
        if (!buttonHeld) {
            dragState = 0;
            return;
        }
        int left = (width - IMAGE_WIDTH) / 2 + 8, top = (height - IMAGE_HEIGHT) / 2 + 17;
        if (dragState <= 1 && mouseX >= left && mouseX < left + 224 && mouseY >= top && mouseY < top + 155) {
            if (dragState == 0) {
                dragState = 1;
            } else {
                offsetX -= (mouseX - lastMouseX) * zoom;
                offsetY -= (mouseY - lastMouseY) * zoom;
            }
            lastMouseX = mouseX;
            lastMouseY = mouseY;
        }
        clamp();
    }

    private void clamp() {
        offsetX = Math.max(MIN_X, Math.min(MAX_X - 1, offsetX));
        offsetY = Math.max(MIN_Y, Math.min(MAX_Y - 1, offsetY));
    }

    @Override
    public boolean doesGuiPauseGame() { return true; }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drag(mouseX, mouseY);
        drawDefaultBackground();
        Achievement hovered = drawBoard(mouseX, mouseY);
        int left = (width - IMAGE_WIDTH) / 2, top = (height - IMAGE_HEIGHT) / 2;
        glColor4f(1, 1, 1, 1);
        mc.textures().bind(BACKGROUND);
        drawTexturedModalRect(left, top, 0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);
        fontRendererObj.drawString(I18n.format("gui.achievements"), left + 15, top + 5, 0x404040);
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (hovered != null) drawTooltip(hovered, mouseX, mouseY);
    }

    /** Backdrop, lines and icons, clipped to the window; returns the achievement under the pointer. */
    private Achievement drawBoard(int mouseX, int mouseY) {
        StatFile stats = mc.stats() == null ? new StatFile() : mc.stats();
        int x = (int) Math.floor(offsetX), y = (int) Math.floor(offsetY);
        int left = (width - IMAGE_WIDTH) / 2, top = (height - IMAGE_HEIGHT) / 2;
        int mapLeft = left + 16, mapTop = top + 17;
        float scale = mc.displayWidth() / (float) Math.max(1, width);
        glEnable(GL_SCISSOR_TEST);
        glScissor(Math.round(mapLeft * scale), Math.round(mc.displayHeight() - (mapTop + 155) * scale),
            Math.round(224 * scale), Math.round(155 * scale));
        glPushMatrix();
        glTranslatef(mapLeft, mapTop, 0);
        glScalef(1f / zoom, 1f / zoom, 1f);
        glEnable(GL_TEXTURE_2D);
        drawBackdrop(x, y);
        long now = System.currentTimeMillis();
        boolean pulse = Math.sin(now % 600L / 600.0 * Math.PI * 2.0) > 0.6;
        for (Achievement achievement : AchievementList.ALL) {
            Achievement parent = AchievementList.parent(achievement);
            if (parent == null) continue;
            int cx = achievement.column() * 24 - x + 11, cy = achievement.row() * 24 - y + 11;
            int px = parent.column() * 24 - x + 11, py = parent.row() * 24 - y + 11;
            if (stats.distanceToUnlock(achievement) > 4) continue;
            int color = 0xFF000000;
            if (stats.has(achievement)) color = 0xFFA0A0A0;
            else if (stats.canUnlock(achievement)) color = pulse ? 0xFF00FF00 : 0xFF008000;
            drawHorizontalLine(cx, px, cy, color);
            drawVerticalLine(px, cy, py, color);
            glColor4f(1, 1, 1, 1);
            mc.textures().bind(BACKGROUND);
            if (cx > px) drawTexturedModalRect(cx - 11 - 7, cy - 5, 114, 234, 7, 11);
            else if (cx < px) drawTexturedModalRect(cx + 11, cy - 5, 107, 234, 7, 11);
            else if (cy > py) drawTexturedModalRect(cx - 5, cy - 11 - 7, 96, 234, 11, 7);
            else if (cy < py) drawTexturedModalRect(cx - 5, cy + 11, 96, 241, 11, 7);
        }
        Achievement hovered = null;
        float boardMouseX = (mouseX - mapLeft) * zoom, boardMouseY = (mouseY - mapTop) * zoom;
        boolean inWindow = mouseX >= mapLeft && mouseX < mapLeft + 224 && mouseY >= mapTop && mouseY < mapTop + 155;
        for (Achievement achievement : AchievementList.ALL) {
            int ax = achievement.column() * 24 - x, ay = achievement.row() * 24 - y;
            if (ax < -24 || ay < -24 || ax > 224 * zoom || ay > 155 * zoom) continue;
            int distance = stats.distanceToUnlock(achievement);
            float brightness;
            if (stats.has(achievement)) brightness = 1f;
            else if (stats.canUnlock(achievement)) brightness = Math.sin(now % 600L / 600.0 * Math.PI * 2.0) < 0.6 ? 0.6f : 0.8f;
            else if (distance < 3) brightness = 0.3f;
            else if (distance == 3) brightness = 0.2f;
            else if (distance == 4) brightness = 0.1f;
            else continue;
            glColor4f(brightness, brightness, brightness, 1);
            mc.textures().bind(BACKGROUND);
            drawTexturedModalRect(ax - 2, ay - 2, achievement.special() ? 26 : 0, 202, 26, 26);
            Item icon = mc.itemByName(achievement.icon());
            if (icon != null && mc.itemRenderer() != null) {
                mc.itemRenderer().guiBrightness = stats.canUnlock(achievement) ? 1f : 0.1f;
                mc.itemRenderer().renderItemAndEffectIntoGUI(new ItemStack(icon), ax + 3, ay + 3);
                mc.itemRenderer().guiBrightness = 1f;
            }
            glDisable(GL_DEPTH_TEST);
            glColor4f(1, 1, 1, 1);
            if (inWindow && boardMouseX >= ax && boardMouseX <= ax + 22 && boardMouseY >= ay && boardMouseY <= ay + 22)
                hovered = achievement;
        }
        glPopMatrix();
        glDisable(GL_SCISSOR_TEST);
        return hovered;
    }

    /** Rows of block textures, the same for a player every time (seeded by their name and the cell). */
    private void drawBackdrop(int x, int y) {
        int column = x + 288 >> 4, row = y + 288 >> 4;
        int shiftX = (x + 288) % 16, shiftY = (y + 288) % 16;
        Random random = new Random();
        float cell = 16f / zoom;
        for (int dy = 0; dy * cell - shiftY < 155f; dy++) {
            float shade = 0.6f - (row + dy) / 25f * 0.3f;
            glColor4f(shade, shade, shade, 1f);
            for (int dx = 0; dx * cell - shiftX < 224f; dx++) {
                random.setSeed(mc.playerName().hashCode() + column + dx + (row + dy) * 16L);
                glColor4f(shade, shade, shade, 1f);
                mc.drawBlockTexture(backdropBlock(random, row + dy), dx * 16 - shiftX, dy * 16 - shiftY, 16);
            }
        }
        glColor4f(1, 1, 1, 1);
    }

    /** Which block texture a backdrop cell shows, from its row (deeper rows are rarer, stonier). */
    static String backdropBlock(Random random, int row) {
        int roll = random.nextInt(1 + row) + row / 2;
        if (roll > 37 || row == 35) return "bedrock";
        if (roll == 22) return random.nextInt(2) == 0 ? "diamond_ore" : "redstone_ore";
        if (roll == 10) return "iron_ore";
        if (roll == 8) return "coal_ore";
        if (roll > 4) return "stone";
        if (roll > 0) return "dirt";
        return "sand";
    }

    private void drawTooltip(Achievement achievement, int mouseX, int mouseY) {
        StatFile stats = mc.stats() == null ? new StatFile() : mc.stats();
        String name = I18n.format(achievement.nameKey());
        int x = mouseX + 12, y = mouseY - 4;
        int distance = stats.distanceToUnlock(achievement);
        boolean available = stats.canUnlock(achievement);
        if (available) {
            int width = Math.max(fontRendererObj.getStringWidth(name), 120);
            List<String> lines = fontRendererObj.listFormattedStringToWidth(description(achievement), width);
            int height = lines.size() * 9;
            if (stats.has(achievement)) height += 12;
            drawGradientRect(x - 3, y - 3, x + width + 3, y + height + 3 + 12, 0xC0000000, 0xC0000000);
            for (int i = 0; i < lines.size(); i++) fontRendererObj.drawString(lines.get(i), x, y + 12 + i * 9, 0xFFA0A0A0);
            if (stats.has(achievement))
                fontRendererObj.drawStringWithShadow(I18n.format("achievement.taken"), x, y + height + 4, 0xFF9090FF);
        } else if (distance <= 3) {
            if (distance == 3) name = I18n.format("achievement.unknown");
            int width = Math.max(fontRendererObj.getStringWidth(name), 120);
            Achievement parent = AchievementList.parent(achievement);
            String needs = I18n.format("achievement.requires", I18n.format(parent.nameKey()));
            List<String> lines = fontRendererObj.listFormattedStringToWidth(needs, width);
            int height = lines.size() * 9;
            drawGradientRect(x - 3, y - 3, x + width + 3, y + height + 12 + 3, 0xC0000000, 0xC0000000);
            for (int i = 0; i < lines.size(); i++) fontRendererObj.drawString(lines.get(i), x, y + 12 + i * 9, 0xFF705050);
        } else {
            return;
        }
        int color = available ? (achievement.special() ? 0xFFFFFF80 : 0xFFFFFFFF) : (achievement.special() ? 0xFF808040 : 0xFF808080);
        fontRendererObj.drawStringWithShadow(name, x, y, color);
    }

    /** The description; Taking Inventory names the inventory key. */
    String description(Achievement achievement) {
        if (achievement.id().equals("openInventory")) return I18n.format(achievement.descriptionKey(), inventoryKeyName(mc));
        return I18n.format(achievement.descriptionKey());
    }

    public static String inventoryKeyName(GuiContext mc) {
        int code = mc.settings().keyBindInventory.getKeyCode();
        String name = KeyCodes.legacyName(code);
        if (name != null) return name;
        return KeyCodes.isMouse(code) ? "Button " + (code + 101) : "???";
    }
}
