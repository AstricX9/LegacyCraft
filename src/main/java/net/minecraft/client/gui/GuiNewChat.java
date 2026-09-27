package net.minecraft.client.gui;

import net.minecraft.client.settings.GameSettings;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;

/**
 * The chat lines above the hotbar (1.8.9 GuiNewChat, HYPOTHESIS: recalled
 * layout). Messages are wrapped to the chat width and kept newest first, at
 * most 100; closed, a line shows for 10 seconds and fades out over the last
 * second, and at most the "unfocused height" worth of lines show; open,
 * every line within the "focused height" shows at full opacity and the
 * mouse wheel or Page Up/Down scrolls, with a scroll bar. The chat options
 * (visibility, colours, opacity, scale, width, heights) apply.
 */
public class GuiNewChat extends Gui {
    public static final int MAX_LINES = 100;
    private static final int VISIBLE_TICKS = 200;

    /** A chat line: its text, the HUD tick it arrived, an id for replaceable lines (0 = none), and whether players wrote it. */
    public record Line(String text, int tick, int id, boolean playerChat) {}

    private final GuiContext mc;
    private final List<String> sentMessages = new ArrayList<>();
    private final List<Line> messages = new ArrayList<>();
    private final List<Line> drawnLines = new ArrayList<>();
    private int scrollPos;
    private boolean isScrolled;

    public GuiNewChat(GuiContext mc) { this.mc = mc; }

    public void drawChat(int updateCounter, boolean chatOpen) {
        GameSettings settings = mc.settings();
        if (settings.chatVisibility == 2) return;
        List<Line> visible = visibleLines(settings);
        int maxLines = getLineCount(chatOpen);
        float opacity = settings.chatOpacity * 0.9f + 0.1f;
        if (visible.isEmpty()) return;
        float scale = settings.chatScale;
        int width = (int) Math.ceil(calculateChatboxWidth(settings.chatWidth) / scale);
        FontRenderer font = mc.font();
        glPushMatrix();
        glTranslatef(2f, 20f, 0f);
        glScalef(scale, scale, 1f);
        int shown = 0;
        for (int i = 0; i + scrollPos < visible.size() && i < maxLines; i++) {
            Line line = visible.get(i + scrollPos);
            int age = updateCounter - line.tick();
            if (age >= VISIBLE_TICKS && !chatOpen) continue;
            int alpha = chatOpen ? 255 : lineAlpha(age);
            alpha = (int) (alpha * opacity);
            shown++;
            if (alpha > 3) {
                int bottom = -i * 9;
                drawRect(0, bottom - 9, width + 4, bottom, alpha / 2 << 24);
                glEnable(GL_BLEND);
                String text = settings.chatColours ? line.text() : FontRenderer.stripFormattingCodes(line.text());
                font.drawStringWithShadow(text, 0, bottom - 8, 0xFFFFFF + (alpha << 24));
            }
        }
        if (chatOpen) {
            int lineHeight = FontRenderer.FONT_HEIGHT;
            glTranslatef(-3f, 0f, 0f);
            int total = visible.size() * lineHeight + visible.size();
            int drawn = shown * lineHeight + shown;
            int offset = scrollPos * drawn / visible.size();
            int barHeight = drawn * drawn / total;
            if (total != drawn) {
                int barAlpha = offset > 0 ? 170 : 96;
                int color = isScrolled ? 0xCC3333 : 0x3333AA;
                drawRect(0, -offset, 2, -offset - barHeight, color + (barAlpha << 24));
                drawRect(2, -offset, 1, -offset - barHeight, 0xCCCCCC + (barAlpha << 24));
            }
        }
        glPopMatrix();
        glColor4f(1, 1, 1, 1);
    }

    /** Full for 9 seconds, then fading quadratically to nothing at 10. */
    public static int lineAlpha(int age) {
        double fade = 1.0 - age / (double) VISIBLE_TICKS;
        fade *= 10.0;
        fade = Math.max(0.0, Math.min(1.0, fade));
        fade *= fade;
        return (int) (255.0 * fade);
    }

    private List<Line> visibleLines(GameSettings settings) {
        if (settings.chatVisibility != 1) return drawnLines;
        List<Line> system = new ArrayList<>();
        for (Line line : drawnLines) if (!line.playerChat()) system.add(line);
        return system;
    }

    public void clearChatMessages() {
        drawnLines.clear();
        messages.clear();
        sentMessages.clear();
    }

    public void printChatMessage(String text, int updateCounter) { printChatMessage(text, 0, updateCounter, false); }

    /** Adds a message; a non-zero id first removes the earlier message with that id (tab-completion lists). */
    public void printChatMessage(String text, int id, int updateCounter, boolean playerChat) {
        if (id != 0) deleteChatLine(id);
        System.out.println("[CHAT] " + FontRenderer.stripFormattingCodes(text));
        GameSettings settings = mc.settings();
        int width = (int) Math.floor(calculateChatboxWidth(settings.chatWidth) / settings.chatScale);
        List<String> wrapped = mc.font() == null ? List.of(text) : mc.font().listFormattedStringToWidth(text, width);
        boolean open = mc.currentScreen() instanceof GuiChat;
        for (String part : wrapped) {
            if (open && scrollPos > 0) {
                isScrolled = true;
                scroll(1);
            }
            drawnLines.add(0, new Line(part, updateCounter, id, playerChat));
        }
        while (drawnLines.size() > MAX_LINES) drawnLines.remove(drawnLines.size() - 1);
        messages.add(0, new Line(text, updateCounter, id, playerChat));
        while (messages.size() > MAX_LINES) messages.remove(messages.size() - 1);
    }

    public void deleteChatLine(int id) {
        drawnLines.removeIf(line -> line.id() == id);
        messages.removeIf(line -> line.id() == id);
    }

    public List<Line> drawnLines() { return List.copyOf(drawnLines); }
    public List<String> getSentMessages() { return sentMessages; }

    public void addToSentMessages(String message) {
        if (sentMessages.isEmpty() || !sentMessages.get(sentMessages.size() - 1).equals(message)) sentMessages.add(message);
    }

    public void resetScroll() {
        scrollPos = 0;
        isScrolled = false;
    }

    public void scroll(int amount) {
        scrollPos += amount;
        int size = drawnLines.size();
        if (scrollPos > size - getLineCount(true)) scrollPos = size - getLineCount(true);
        if (scrollPos <= 0) {
            scrollPos = 0;
            isScrolled = false;
        }
    }

    public int scrollPos() { return scrollPos; }

    public int getLineCount(boolean chatOpen) {
        GameSettings settings = mc.settings();
        return calculateChatboxHeight(chatOpen ? settings.chatHeightFocused : settings.chatHeightUnfocused) / 9;
    }

    public static int calculateChatboxWidth(float setting) { return (int) Math.floor(setting * 280f + 40f); }
    public static int calculateChatboxHeight(float setting) { return (int) Math.floor(setting * 160f + 20f); }
}
