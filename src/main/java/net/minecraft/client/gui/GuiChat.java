package net.minecraft.client.gui;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;

/**
 * The chat input line (1.8.9 GuiChat, HYPOTHESIS: recalled behaviour): a
 * borderless 100-character field on a translucent strip at the bottom,
 * opened empty by the chat key or with "/" by the command key. Enter sends
 * (trimmed, if not empty) and closes; Up/Down walk the messages sent this
 * session, keeping the unsent draft; Page Up/Down and the mouse wheel
 * scroll the chat (the wheel seven lines, one with Shift); Tab
 * completes commands, their arguments and the player name, listing several
 * matches in chat and cycling through them on further presses. The game
 * keeps running underneath.
 */
public class GuiChat extends GuiScreen {
    private static final int COMPLETION_LINE_ID = 1;
    private final String defaultText;
    private GuiTextField inputField;
    private String historyBuffer = "";
    private int sentHistoryCursor = -1;
    private List<String> completions = List.of();
    private int completionIndex;
    private boolean cycling;

    public GuiChat() { this(""); }
    public GuiChat(String defaultText) { this.defaultText = defaultText; }

    @Override public void initGui() {
        GuiNewChat chat = mc.chatGui();
        sentHistoryCursor = chat == null ? 0 : chat.getSentMessages().size();
        String previous = inputField == null ? defaultText : inputField.getText();
        inputField = new GuiTextField(0, fontRendererObj, 4, height - 12, width - 4, 12);
        inputField.setClipboard(new GuiTextField.Clipboard() {
            @Override public String get() { return mc.getClipboard(); }
            @Override public void set(String value) { mc.setClipboard(value); }
        });
        inputField.setMaxStringLength(100);
        inputField.setEnableBackgroundDrawing(false);
        inputField.setFocused(true);
        inputField.setText(previous);
        inputField.setCanLoseFocus(false);
    }

    @Override public void onGuiClosed() {
        GuiNewChat chat = mc.chatGui();
        if (chat != null) chat.resetScroll();
    }

    @Override public void updateScreen() { inputField.updateCursorCounter(); }

    @Override public boolean doesGuiPauseGame() { return false; }

    public String text() { return inputField.getText(); }

    @Override public void keyPressed(int key, int modifiers) {
        if (key == GLFW_KEY_TAB) {
            complete();
            return;
        }
        cycling = false;
        GuiNewChat chat = mc.chatGui();
        switch (key) {
            case GLFW_KEY_ESCAPE -> mc.displayGuiScreen(null);
            case GLFW_KEY_ENTER, GLFW_KEY_KP_ENTER -> {
                String message = inputField.getText().trim();
                if (!message.isEmpty()) {
                    if (chat != null) chat.addToSentMessages(message);
                    mc.sendChatMessage(message);
                }
                mc.displayGuiScreen(null);
            }
            case GLFW_KEY_UP -> getSentHistory(-1);
            case GLFW_KEY_DOWN -> getSentHistory(1);
            case GLFW_KEY_PAGE_UP -> { if (chat != null) chat.scroll(chat.getLineCount(true) - 1); }
            case GLFW_KEY_PAGE_DOWN -> { if (chat != null) chat.scroll(-(chat.getLineCount(true) - 1)); }
            default -> inputField.keyPressed(key, modifiers);
        }
    }

    @Override public void charTyped(char character) {
        cycling = false;
        inputField.charTyped(character);
    }

    @Override public void mouseScrolled(int mouseX, int mouseY, int direction) {
        GuiNewChat chat = mc.chatGui();
        if (chat == null || direction == 0) return;
        int amount = direction > 0 ? 1 : -1;
        if (!isShiftKeyDown()) amount *= 7;
        chat.scroll(amount);
    }

    @Override public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        inputField.mouseClicked(mouseX, mouseY, mouseButton);
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    /** Moves through the sent-message history; past the newest entry is the draft being typed. */
    public void getSentHistory(int direction) {
        GuiNewChat chat = mc.chatGui();
        if (chat == null) return;
        List<String> sent = chat.getSentMessages();
        int target = Math.max(0, Math.min(sent.size(), sentHistoryCursor + direction));
        if (target == sentHistoryCursor) return;
        if (target == sent.size()) {
            sentHistoryCursor = target;
            inputField.setText(historyBuffer);
        } else {
            if (sentHistoryCursor == sent.size()) historyBuffer = inputField.getText();
            inputField.setText(sent.get(target));
            sentHistoryCursor = target;
        }
    }

    private void complete() {
        int cursor = inputField.getCursorPosition();
        String text = inputField.getText();
        int wordStart = text.lastIndexOf(' ', Math.max(0, cursor - 1)) + 1;
        if (cursor == 0) wordStart = 0;
        if (cycling && !completions.isEmpty()) {
            replaceWord(wordStart, cursor, completions.get(completionIndex++ % completions.size()));
            return;
        }
        String word = text.substring(wordStart, cursor);
        completions = new ArrayList<>(mc.tabComplete(text.substring(0, cursor)));
        completionIndex = 0;
        if (completions.isEmpty()) return;
        String common = commonPrefix(completions);
        if (!common.isEmpty() && !common.equalsIgnoreCase(word)) {
            replaceWord(wordStart, cursor, common);
            return;
        }
        cycling = true;
        if (completions.size() > 1 && mc.chatGui() != null)
            mc.printChatLine(String.join(", ", completions), COMPLETION_LINE_ID);
        replaceWord(wordStart, cursor, completions.get(completionIndex++ % completions.size()));
    }

    private void replaceWord(int start, int end, String replacement) {
        inputField.setCursorPosition(end);
        inputField.deleteFromCursor(start - end);
        inputField.writeText(replacement);
    }

    static String commonPrefix(List<String> options) {
        String prefix = options.get(0);
        for (String option : options) {
            int length = 0;
            while (length < prefix.length() && length < option.length() && prefix.charAt(length) == option.charAt(length)) length++;
            prefix = prefix.substring(0, length);
        }
        return prefix;
    }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawRect(2, height - 14, width - 2, height - 2, Integer.MIN_VALUE);
        inputField.drawTextBox();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
