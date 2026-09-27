package net.minecraft.client.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

class GuiTextFieldTest {
    private static GuiTextField field() {
        GuiTextField field = new GuiTextField(0, new FontRenderer(null), 0, 0, 200, 20);
        field.setFocused(true);
        return field;
    }

    @Test
    void typingRespectsTheDefault32CharacterLimit() {
        GuiTextField field = field();
        for (int i = 0; i < 40; i++) field.charTyped('a');
        assertEquals(32, field.getText().length());
    }

    @Test
    void refusesSectionSignsAndControlCharacters() {
        GuiTextField field = field();
        field.writeText("a§b\tc\u007fd");
        assertEquals("abcd", field.getText());
        assertFalse(field.charTyped('§'));
    }

    @Test
    void backspaceAndWordDeletion() {
        GuiTextField field = field();
        field.setText("New World two");
        field.keyPressed(GLFW_KEY_BACKSPACE, 0);
        assertEquals("New World tw", field.getText());
        field.keyPressed(GLFW_KEY_BACKSPACE, GLFW_MOD_CONTROL);
        assertEquals("New World ", field.getText());
        field.keyPressed(GLFW_KEY_HOME, 0);
        field.keyPressed(GLFW_KEY_DELETE, GLFW_MOD_CONTROL);
        assertEquals("World ", field.getText());
    }

    @Test
    void shiftSelectionIsReplacedByTyping() {
        GuiTextField field = field();
        field.setText("abcdef");
        field.keyPressed(GLFW_KEY_LEFT, GLFW_MOD_SHIFT);
        field.keyPressed(GLFW_KEY_LEFT, GLFW_MOD_SHIFT);
        assertEquals("ef", field.getSelectedText());
        field.charTyped('X');
        assertEquals("abcdX", field.getText());
        assertEquals(5, field.getCursorPosition());
    }

    @Test
    void ctrlAThenCopyAndPasteUseTheClipboard() {
        GuiTextField field = field();
        StringBuilder clipboard = new StringBuilder();
        field.setClipboard(new GuiTextField.Clipboard() {
            public String get() { return clipboard.toString(); }
            public void set(String value) { clipboard.setLength(0); clipboard.append(value); }
        });
        field.setText("seed123");
        field.keyPressed(GLFW_KEY_A, GLFW_MOD_CONTROL);
        field.keyPressed(GLFW_KEY_C, GLFW_MOD_CONTROL);
        assertEquals("seed123", clipboard.toString());
        field.keyPressed(GLFW_KEY_END, 0);
        field.keyPressed(GLFW_KEY_V, GLFW_MOD_CONTROL);
        assertEquals("seed123seed123", field.getText());
    }

    @Test
    void unfocusedFieldIgnoresInput() {
        GuiTextField field = field();
        field.setFocused(false);
        assertFalse(field.charTyped('a'));
        assertEquals("", field.getText());
    }
}
