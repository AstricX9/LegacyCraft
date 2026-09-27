package net.minecraft.client.gui;

import java.util.function.Predicate;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

/**
 * Single-line text input with 1.8.9 editing behaviour (master context §63):
 * selection via shift, word jumps via ctrl, ctrl+A/C/V/X, a blinking cursor
 * ({@code _} at the end, a bar elsewhere), the {@code §} character refused,
 * and a default limit of 32 characters.
 *
 * <p>Editing logic is independent of OpenGL so it can be unit-tested; only
 * {@link #drawTextBox()} touches GL.
 */
public class GuiTextField extends Gui {
    public final int id;
    private final FontRenderer font;
    public int xPosition, yPosition;
    public final int width, height;
    private String text = "";
    private int maxStringLength = 32;
    private int cursorCounter;
    private boolean enableBackgroundDrawing = true;
    private boolean canLoseFocus = true;
    private boolean isFocused;
    private boolean isEnabled = true;
    private int lineScrollOffset;
    private int cursorPosition;
    private int selectionEnd;
    private int enabledColor = 0xE0E0E0, disabledColor = 0x707070;
    private boolean visible = true;
    private Predicate<String> validator = text -> true;
    private Runnable onChange = () -> {};
    private Clipboard clipboard = new Clipboard() {
        private String held = "";
        public String get() { return held; }
        public void set(String value) { held = value; }
    };

    /** Where ctrl+C/V go; the client wires this to the system clipboard. */
    public interface Clipboard { String get(); void set(String value); }

    public GuiTextField(int id, FontRenderer font, int x, int y, int width, int height) {
        this.id = id; this.font = font; this.xPosition = x; this.yPosition = y; this.width = width; this.height = height;
    }

    public void setClipboard(Clipboard clipboard) { this.clipboard = clipboard; }
    public void setValidator(Predicate<String> validator) { this.validator = validator; }
    public void setOnChange(Runnable onChange) { this.onChange = onChange; }

    public void updateCursorCounter() { cursorCounter++; }

    public void setText(String value) {
        if (!validator.test(value)) return;
        text = value.length() > maxStringLength ? value.substring(0, maxStringLength) : value;
        setCursorPositionEnd();
    }

    public String getText() { return text; }

    public String getSelectedText() {
        int start = Math.min(cursorPosition, selectionEnd), end = Math.max(cursorPosition, selectionEnd);
        return text.substring(start, end);
    }

    /** Vanilla's ChatAllowedCharacters rule: no section sign, no control characters, no DEL. */
    public static boolean isAllowedCharacter(char character) {
        return character != '§' && character >= ' ' && character != 127;
    }

    public static String filterAllowedCharacters(String input) {
        StringBuilder out = new StringBuilder();
        for (char character : input.toCharArray()) if (isAllowedCharacter(character)) out.append(character);
        return out.toString();
    }

    /** Replaces the selection (or inserts at the cursor) with {@code insertion}, respecting the length limit. */
    public void writeText(String insertion) {
        String filtered = filterAllowedCharacters(insertion);
        int start = Math.min(cursorPosition, selectionEnd), end = Math.max(cursorPosition, selectionEnd);
        int room = maxStringLength - text.length() + (end - start);
        String result = text.substring(0, start);
        int inserted;
        if (room < filtered.length()) {
            result += filtered.substring(0, Math.max(0, room));
            inserted = Math.max(0, room);
        } else {
            result += filtered;
            inserted = filtered.length();
        }
        result += text.substring(end);
        if (validator.test(result)) {
            text = result;
            moveCursorBy(start - selectionEnd + inserted);
            onChange.run();
        }
    }

    public void deleteWords(int count) {
        if (text.isEmpty()) return;
        if (selectionEnd != cursorPosition) writeText("");
        else deleteFromCursor(getNthWordFromCursor(count) - cursorPosition);
    }

    public void deleteFromCursor(int count) {
        if (text.isEmpty()) return;
        if (selectionEnd != cursorPosition) { writeText(""); return; }
        boolean backwards = count < 0;
        int start = backwards ? cursorPosition + count : cursorPosition;
        int end = backwards ? cursorPosition : cursorPosition + count;
        start = Math.max(0, start); end = Math.min(text.length(), end);
        String result = text.substring(0, start) + text.substring(end);
        if (validator.test(result)) {
            text = result;
            if (backwards) moveCursorBy(count);
            onChange.run();
        }
    }

    public int getNthWordFromCursor(int count) { return getNthWordFromPos(count, cursorPosition); }

    public int getNthWordFromPos(int count, int position) {
        boolean backwards = count < 0;
        int result = position;
        for (int i = 0; i < Math.abs(count); i++) {
            if (!backwards) {
                int length = text.length();
                result = text.indexOf(' ', result);
                if (result == -1) result = length;
                else while (result < length && text.charAt(result) == ' ') result++;
            } else {
                while (result > 0 && text.charAt(result - 1) == ' ') result--;
                while (result > 0 && text.charAt(result - 1) != ' ') result--;
            }
        }
        return result;
    }

    public void moveCursorBy(int amount) { setCursorPosition(selectionEnd + amount); }

    public void setCursorPosition(int position) {
        cursorPosition = Math.max(0, Math.min(text.length(), position));
        setSelectionPos(cursorPosition);
    }

    public void setCursorPositionZero() { setCursorPosition(0); }
    public void setCursorPositionEnd() { setCursorPosition(text.length()); }
    public int getCursorPosition() { return cursorPosition; }
    public int getSelectionEnd() { return selectionEnd; }

    /** Handles an editing/navigation key; returns whether the field consumed it. */
    public boolean keyPressed(int key, int modifiers) {
        if (!isFocused) return false;
        boolean ctrl = (modifiers & GLFW_MOD_CONTROL) != 0, shift = (modifiers & GLFW_MOD_SHIFT) != 0;
        if (ctrl && key == GLFW_KEY_A) { setCursorPositionEnd(); setSelectionPos(0); return true; }
        if (ctrl && key == GLFW_KEY_C) { clipboard.set(getSelectedText()); return true; }
        if (ctrl && key == GLFW_KEY_V) { if (isEnabled) writeText(clipboard.get()); return true; }
        if (ctrl && key == GLFW_KEY_X) {
            clipboard.set(getSelectedText());
            if (isEnabled) writeText("");
            return true;
        }
        switch (key) {
            case GLFW_KEY_BACKSPACE -> { if (isEnabled) { if (ctrl) deleteWords(-1); else deleteFromCursor(-1); } return true; }
            case GLFW_KEY_DELETE -> { if (isEnabled) { if (ctrl) deleteWords(1); else deleteFromCursor(1); } return true; }
            case GLFW_KEY_HOME -> { if (shift) setSelectionPos(0); else setCursorPositionZero(); return true; }
            case GLFW_KEY_END -> { if (shift) setSelectionPos(text.length()); else setCursorPositionEnd(); return true; }
            case GLFW_KEY_LEFT -> {
                if (shift) setSelectionPos(ctrl ? getNthWordFromPos(-1, selectionEnd) : selectionEnd - 1);
                else if (ctrl) setCursorPosition(getNthWordFromCursor(-1));
                else moveCursorBy(-1);
                return true;
            }
            case GLFW_KEY_RIGHT -> {
                if (shift) setSelectionPos(ctrl ? getNthWordFromPos(1, selectionEnd) : selectionEnd + 1);
                else if (ctrl) setCursorPosition(getNthWordFromCursor(1));
                else moveCursorBy(1);
                return true;
            }
            default -> { return false; }
        }
    }

    public boolean charTyped(char character) {
        if (!isFocused || !isAllowedCharacter(character)) return false;
        if (isEnabled) writeText(Character.toString(character));
        return true;
    }

    public void mouseClicked(int mouseX, int mouseY, int button) {
        boolean inside = mouseX >= xPosition && mouseX < xPosition + width && mouseY >= yPosition && mouseY < yPosition + height;
        if (canLoseFocus) setFocused(inside);
        if (isFocused && inside && button == 0) {
            int offset = mouseX - xPosition;
            if (enableBackgroundDrawing) offset -= 4;
            String visibleText = font.trimStringToWidth(text.substring(lineScrollOffset), getWidth());
            setCursorPosition(font.trimStringToWidth(visibleText, offset).length() + lineScrollOffset);
        }
    }

    public void drawTextBox() {
        if (!visible) return;
        if (enableBackgroundDrawing) {
            drawRect(xPosition - 1, yPosition - 1, xPosition + width + 1, yPosition + height + 1, 0xFFA0A0A0);
            drawRect(xPosition, yPosition, xPosition + width, yPosition + height, 0xFF000000);
        }
        int color = isEnabled ? enabledColor : disabledColor;
        int cursorOffset = cursorPosition - lineScrollOffset;
        int selectionOffset = selectionEnd - lineScrollOffset;
        String visibleText = font.trimStringToWidth(text.substring(lineScrollOffset), getWidth());
        boolean cursorVisible = cursorOffset >= 0 && cursorOffset <= visibleText.length();
        boolean blinkOn = isFocused && cursorCounter / 6 % 2 == 0 && cursorVisible;
        int left = enableBackgroundDrawing ? xPosition + 4 : xPosition;
        int top = enableBackgroundDrawing ? yPosition + (height - 8) / 2 : yPosition;
        int x = left;
        selectionOffset = Math.min(selectionOffset, visibleText.length());
        if (!visibleText.isEmpty()) {
            String beforeCursor = cursorVisible ? visibleText.substring(0, cursorOffset) : visibleText;
            x = left + font.drawStringWithShadow(beforeCursor, left, top, color) + 1;
        }
        boolean insertBar = cursorPosition < text.length() || text.length() >= maxStringLength;
        int cursorX = x;
        if (!cursorVisible) cursorX = cursorOffset > 0 ? left + width : left;
        else if (insertBar) { cursorX = x - 1; x--; }
        if (!visibleText.isEmpty() && cursorVisible && cursorOffset < visibleText.length())
            font.drawStringWithShadow(visibleText.substring(cursorOffset), x, top, color);
        if (blinkOn) {
            if (insertBar) drawRect(cursorX, top - 1, cursorX + 1, top + 1 + FontRenderer.FONT_HEIGHT, 0xFFD0D0D0);
            else font.drawStringWithShadow("_", cursorX, top, color);
        }
        if (selectionOffset != cursorOffset) {
            int selectionX = left + font.getStringWidth(visibleText.substring(0, Math.max(0, selectionOffset)));
            drawSelectionBox(cursorX, top - 1, selectionX - 1, top + 1 + FontRenderer.FONT_HEIGHT);
        }
    }

    /** Inverts the selected text's pixels the way vanilla does (logic op OR_REVERSE, blue). */
    private void drawSelectionBox(int startX, int startY, int endX, int endY) {
        if (startX < endX) { int swap = startX; startX = endX; endX = swap; }
        if (startY < endY) { int swap = startY; startY = endY; endY = swap; }
        if (endX > xPosition + width) endX = xPosition + width;
        if (startX > xPosition + width) startX = xPosition + width;
        glColor4f(0, 0, 1, 1);
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_COLOR_LOGIC_OP);
        glLogicOp(GL_OR_REVERSE);
        glBegin(GL_QUADS);
        glVertex2f(startX, endY); glVertex2f(endX, endY); glVertex2f(endX, startY); glVertex2f(startX, startY);
        glEnd();
        glDisable(GL_COLOR_LOGIC_OP);
        glEnable(GL_TEXTURE_2D);
        glColor4f(1, 1, 1, 1);
    }

    public void setMaxStringLength(int length) {
        maxStringLength = length;
        if (text.length() > length) text = text.substring(0, length);
    }

    public int getMaxStringLength() { return maxStringLength; }
    public boolean getEnableBackgroundDrawing() { return enableBackgroundDrawing; }
    public void setEnableBackgroundDrawing(boolean enabled) { enableBackgroundDrawing = enabled; }
    public void setTextColor(int color) { enabledColor = color; }
    public void setDisabledTextColour(int color) { disabledColor = color; }

    public void setFocused(boolean focused) {
        if (focused && !isFocused) cursorCounter = 0;
        isFocused = focused;
    }

    public boolean isFocused() { return isFocused; }
    public void setEnabled(boolean enabled) { isEnabled = enabled; }
    public void setCanLoseFocus(boolean canLoseFocus) { this.canLoseFocus = canLoseFocus; }
    public boolean getVisible() { return visible; }
    public void setVisible(boolean visible) { this.visible = visible; }

    /** Width available for text inside the border. */
    public int getWidth() { return enableBackgroundDrawing ? width - 8 : width; }

    /** Moves the selection end and scrolls so the cursor stays in view. */
    public void setSelectionPos(int position) {
        int length = text.length();
        position = Math.max(0, Math.min(length, position));
        selectionEnd = position;
        if (lineScrollOffset > length) lineScrollOffset = length;
        int available = getWidth();
        String visibleText = font.trimStringToWidth(text.substring(lineScrollOffset), available);
        int visibleEnd = visibleText.length() + lineScrollOffset;
        if (position == lineScrollOffset) lineScrollOffset -= trimFromEnd(text, available).length();
        if (position > visibleEnd) lineScrollOffset += position - visibleEnd;
        else if (position <= lineScrollOffset) lineScrollOffset -= lineScrollOffset - position;
        lineScrollOffset = Math.max(0, Math.min(length, lineScrollOffset));
    }

    public int getLineScrollOffset() { return lineScrollOffset; }

    /** The longest suffix of {@code value} that fits in {@code width} pixels. */
    private String trimFromEnd(String value, int width) {
        int used = 0, start = value.length();
        while (start > 0) {
            int advance = font.getStringWidth(value.substring(start - 1, start));
            if (used + advance > width) break;
            used += advance;
            start--;
        }
        return value.substring(start);
    }
}
