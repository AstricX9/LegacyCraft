package net.minecraft.client.settings;

import net.minecraft.client.gui.I18n;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

/** Key bindings: GLFW to LWJGL 2 key codes, display names, press queues and the controls-screen order. */
final class KeyBindingTest {
    @Test
    void mapsGlfwKeysToLwjgl2Codes() {
        assertEquals(17, KeyCodes.fromGlfwKey(GLFW_KEY_W));
        assertEquals(57, KeyCodes.fromGlfwKey(GLFW_KEY_SPACE));
        assertEquals(42, KeyCodes.fromGlfwKey(GLFW_KEY_LEFT_SHIFT));
        assertEquals(61, KeyCodes.fromGlfwKey(GLFW_KEY_F3));
        assertEquals(87, KeyCodes.fromGlfwKey(GLFW_KEY_F11));
        assertEquals(2, KeyCodes.fromGlfwKey(GLFW_KEY_1));
        assertEquals(11, KeyCodes.fromGlfwKey(GLFW_KEY_0));
        assertEquals(GLFW_KEY_E, KeyCodes.toGlfwKey(18));
        assertEquals(-100, KeyCodes.fromMouseButton(GLFW_MOUSE_BUTTON_LEFT));
    }

    @Test
    void displayNamesMatchLwjgl2AndTheMouseButtonKey() {
        I18n.set(Map.of("key.mouseButton", "Button %1$s"));
        assertEquals("W", KeyBinding.displayName(17));
        assertEquals("LSHIFT", KeyBinding.displayName(42));
        assertEquals("SPACE", KeyBinding.displayName(57));
        assertEquals("NONE", KeyBinding.displayName(0));
        assertEquals("Button 1", KeyBinding.displayName(-100));
        assertEquals("Button 2", KeyBinding.displayName(-99));
    }

    @Test
    void pressesQueueAndReleaseClearsHeldState() {
        KeyBinding drop = new KeyBinding("key.drop", 16, "key.categories.gameplay");
        List<KeyBinding> all = List.of(drop);
        KeyBinding.onTransition(all, 16, true);
        KeyBinding.onTransition(all, 16, false);
        KeyBinding.onTransition(all, 16, true);
        assertTrue(drop.isKeyDown());
        assertTrue(drop.isPressed());
        assertTrue(drop.isPressed());
        assertFalse(drop.isPressed());
        KeyBinding.unpressAll(all);
        assertFalse(drop.isKeyDown());
    }

    @Test
    void sortsByTranslatedCategoryThenDescription() {
        I18n.set(Map.of("key.categories.movement", "Movement", "key.categories.gameplay", "Gameplay",
            "key.jump", "Jump", "key.forward", "Walk Forwards", "key.attack", "Attack/Destroy"));
        List<KeyBinding> keys = new ArrayList<>(List.of(
            new KeyBinding("key.jump", 57, "key.categories.movement"),
            new KeyBinding("key.attack", -100, "key.categories.gameplay"),
            new KeyBinding("key.forward", 17, "key.categories.movement")));
        keys.sort(null);
        assertEquals(List.of("key.attack", "key.jump", "key.forward"),
            keys.stream().map(KeyBinding::getKeyDescription).toList());
    }
}
