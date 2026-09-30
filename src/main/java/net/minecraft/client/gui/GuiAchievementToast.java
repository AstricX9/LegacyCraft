package net.minecraft.client.gui;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import java.util.List;

import static org.lwjgl.opengl.GL11.*;

/**
 * The achievement pop-up in the top-right corner (1.8.9 GuiAchievement,
 * HYPOTHESIS: recalled timing): "Achievement get!" over the name, the icon
 * at the left, sliding in and out over 3 seconds. A hint (the "Taking
 * Inventory" reminder shown to a new player) slides in and stays, showing
 * the name and the wrapped description, until something replaces it.
 */
public final class GuiAchievementToast {
    static final ResourceLocation BACKGROUND = new ResourceLocation("minecraft:textures/gui/achievement/achievement_background.png");
    private String title, description;
    private Item icon;
    private long notificationTime;
    private boolean permanent;

    /** An achievement just earned (displayAchievement). */
    public void display(String name, Item icon, long now) {
        title = I18n.format("achievement.get");
        description = name;
        this.icon = icon;
        notificationTime = now;
        permanent = false;
    }

    /** A standing hint (displayUnformattedAchievement): shown 2.5 s later, then stays. */
    public void displayHint(String name, String text, Item icon, long now) {
        title = name;
        description = text;
        this.icon = icon;
        notificationTime = now + 2500;
        permanent = true;
    }

    public void clear() {
        notificationTime = 0;
        permanent = false;
    }

    public boolean isShowingHint() { return notificationTime != 0 && permanent; }

    /**
     * How far the toast has slid down (0 hidden, 1 fully in) at a time, or
     * -1 when it has finished: in over the first eighth of its 3 seconds, out
     * over the last eighth, easing with the fourth power.
     */
    public double slide(long now) {
        if (notificationTime == 0) return -1;
        double progress = (now - notificationTime) / 3000.0;
        if (!permanent) {
            if (progress < 0 || progress > 1) {
                if (progress > 1) notificationTime = 0;
                return -1;
            }
        } else if (progress > 0.5) {
            progress = 0.5;
        }
        if (progress < 0) return -1;
        double hidden = progress * 2;
        if (hidden > 1) hidden = 2 - hidden;
        hidden = 1 - hidden * 4;
        if (hidden < 0) hidden = 0;
        hidden = hidden * hidden;
        hidden = hidden * hidden;
        return 1 - hidden;
    }

    /** Draws over everything; the caller has set up the GUI projection. */
    public void draw(GuiContext mc, int scaledWidth, long now) {
        double shown = slide(now);
        if (shown < 0) return;
        int x = scaledWidth - 160, y = -(int) ((1 - shown) * 36);
        glDisable(GL_DEPTH_TEST);
        glDepthMask(false);
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(1, 1, 1, 1);
        mc.textures().bind(BACKGROUND);
        Gui.drawTexturedModalRect(x, y, 96, 202, 160, 32);
        FontRenderer font = mc.font();
        if (permanent) {
            List<String> lines = font.listFormattedStringToWidth(description, 120);
            for (int i = 0; i < lines.size(); i++) font.drawString(lines.get(i), x + 30, y + 7 + i * 9, 0xFFFFFFFF);
        } else {
            font.drawString(title, x + 30, y + 7, 0xFFFFFF00);
            font.drawString(description, x + 30, y + 18, 0xFFFFFFFF);
        }
        if (icon != null && mc.itemRenderer() != null) mc.itemRenderer().renderItemAndEffectIntoGUI(new ItemStack(icon), x + 8, y + 8);
        glDepthMask(true);
        glColor4f(1, 1, 1, 1);
    }
}
