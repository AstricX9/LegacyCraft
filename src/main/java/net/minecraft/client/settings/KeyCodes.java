package net.minecraft.client.settings;

import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Translates between GLFW key codes (what this client reads) and the LWJGL 2
 * keyboard codes vanilla 1.8.9 stores in {@code options.txt} and shows in the
 * Controls screen (master context §68, §114: backend substitution is allowed,
 * behavior substitution is not).
 *
 * <p>VERIFIED_FORMAT: LWJGL 2's {@code org.lwjgl.input.Keyboard} constants are
 * the DirectInput scan-code table, and its key names are those constants
 * without the {@code KEY_} prefix. Mouse buttons are stored by vanilla as
 * {@code button - 100}.
 */
public final class KeyCodes {
    /** LWJGL 2 code 0: an unbound key. */
    public static final int NONE = 0;
    private static final Map<Integer, Integer> GLFW_TO_LEGACY = new HashMap<>();
    private static final Map<Integer, Integer> LEGACY_TO_GLFW = new HashMap<>();
    private static final Map<Integer, String> LEGACY_NAMES = new HashMap<>();

    static {
        map(GLFW_KEY_ESCAPE, 1, "ESCAPE");
        for (int digit = 1; digit <= 9; digit++) map(GLFW_KEY_0 + digit, digit + 1, String.valueOf(digit));
        map(GLFW_KEY_0, 11, "0");
        map(GLFW_KEY_MINUS, 12, "MINUS"); map(GLFW_KEY_EQUAL, 13, "EQUALS");
        map(GLFW_KEY_BACKSPACE, 14, "BACK"); map(GLFW_KEY_TAB, 15, "TAB");
        String qwertyTop = "QWERTYUIOP";
        for (int i = 0; i < qwertyTop.length(); i++) letter(qwertyTop.charAt(i), 16 + i);
        map(GLFW_KEY_LEFT_BRACKET, 26, "LBRACKET"); map(GLFW_KEY_RIGHT_BRACKET, 27, "RBRACKET");
        map(GLFW_KEY_ENTER, 28, "RETURN"); map(GLFW_KEY_LEFT_CONTROL, 29, "LCONTROL");
        String home = "ASDFGHJKL";
        for (int i = 0; i < home.length(); i++) letter(home.charAt(i), 30 + i);
        map(GLFW_KEY_SEMICOLON, 39, "SEMICOLON"); map(GLFW_KEY_APOSTROPHE, 40, "APOSTROPHE");
        map(GLFW_KEY_GRAVE_ACCENT, 41, "GRAVE"); map(GLFW_KEY_LEFT_SHIFT, 42, "LSHIFT");
        map(GLFW_KEY_BACKSLASH, 43, "BACKSLASH");
        String bottom = "ZXCVBNM";
        for (int i = 0; i < bottom.length(); i++) letter(bottom.charAt(i), 44 + i);
        map(GLFW_KEY_COMMA, 51, "COMMA"); map(GLFW_KEY_PERIOD, 52, "PERIOD"); map(GLFW_KEY_SLASH, 53, "SLASH");
        map(GLFW_KEY_RIGHT_SHIFT, 54, "RSHIFT"); map(GLFW_KEY_KP_MULTIPLY, 55, "MULTIPLY");
        map(GLFW_KEY_LEFT_ALT, 56, "LMENU"); map(GLFW_KEY_SPACE, 57, "SPACE"); map(GLFW_KEY_CAPS_LOCK, 58, "CAPITAL");
        for (int f = 1; f <= 10; f++) map(GLFW_KEY_F1 + f - 1, 58 + f, "F" + f);
        map(GLFW_KEY_NUM_LOCK, 69, "NUMLOCK"); map(GLFW_KEY_SCROLL_LOCK, 70, "SCROLL");
        map(GLFW_KEY_KP_7, 71, "NUMPAD7"); map(GLFW_KEY_KP_8, 72, "NUMPAD8"); map(GLFW_KEY_KP_9, 73, "NUMPAD9");
        map(GLFW_KEY_KP_SUBTRACT, 74, "SUBTRACT");
        map(GLFW_KEY_KP_4, 75, "NUMPAD4"); map(GLFW_KEY_KP_5, 76, "NUMPAD5"); map(GLFW_KEY_KP_6, 77, "NUMPAD6");
        map(GLFW_KEY_KP_ADD, 78, "ADD");
        map(GLFW_KEY_KP_1, 79, "NUMPAD1"); map(GLFW_KEY_KP_2, 80, "NUMPAD2"); map(GLFW_KEY_KP_3, 81, "NUMPAD3");
        map(GLFW_KEY_KP_0, 82, "NUMPAD0"); map(GLFW_KEY_KP_DECIMAL, 83, "DECIMAL");
        map(GLFW_KEY_F11, 87, "F11"); map(GLFW_KEY_F12, 88, "F12");
        map(GLFW_KEY_F13, 100, "F13"); map(GLFW_KEY_F14, 101, "F14"); map(GLFW_KEY_F15, 102, "F15");
        map(GLFW_KEY_KP_EQUAL, 141, "NUMPADEQUALS"); map(GLFW_KEY_KP_ENTER, 156, "NUMPADENTER");
        map(GLFW_KEY_RIGHT_CONTROL, 157, "RCONTROL"); map(GLFW_KEY_KP_DIVIDE, 181, "DIVIDE");
        map(GLFW_KEY_PRINT_SCREEN, 183, "SYSRQ"); map(GLFW_KEY_RIGHT_ALT, 184, "RMENU");
        map(GLFW_KEY_PAUSE, 197, "PAUSE"); map(GLFW_KEY_HOME, 199, "HOME"); map(GLFW_KEY_UP, 200, "UP");
        map(GLFW_KEY_PAGE_UP, 201, "PRIOR"); map(GLFW_KEY_LEFT, 203, "LEFT"); map(GLFW_KEY_RIGHT, 205, "RIGHT");
        map(GLFW_KEY_END, 207, "END"); map(GLFW_KEY_DOWN, 208, "DOWN"); map(GLFW_KEY_PAGE_DOWN, 209, "NEXT");
        map(GLFW_KEY_INSERT, 210, "INSERT"); map(GLFW_KEY_DELETE, 211, "DELETE");
        map(GLFW_KEY_LEFT_SUPER, 219, "LMETA"); map(GLFW_KEY_RIGHT_SUPER, 220, "RMETA");
    }

    private KeyCodes() {}

    private static void letter(char letter, int legacy) { map(GLFW_KEY_A + (letter - 'A'), legacy, String.valueOf(letter)); }

    private static void map(int glfw, int legacy, String name) {
        GLFW_TO_LEGACY.put(glfw, legacy);
        LEGACY_TO_GLFW.put(legacy, glfw);
        LEGACY_NAMES.put(legacy, name);
    }

    /** The LWJGL 2 code for a GLFW key, or {@link #NONE} when LWJGL 2 had no equivalent. */
    public static int fromGlfwKey(int glfwKey) { return GLFW_TO_LEGACY.getOrDefault(glfwKey, NONE); }

    /** The legacy code vanilla stores for a mouse button (0 = left). */
    public static int fromMouseButton(int button) { return button - 100; }

    public static boolean isMouse(int legacyCode) { return legacyCode < 0; }

    /** The GLFW key for a legacy keyboard code, or {@link GLFW#GLFW_KEY_UNKNOWN}. */
    public static int toGlfwKey(int legacyCode) { return LEGACY_TO_GLFW.getOrDefault(legacyCode, GLFW_KEY_UNKNOWN); }

    /** The GLFW mouse button for a legacy mouse code. */
    public static int toMouseButton(int legacyCode) { return legacyCode + 100; }

    /** LWJGL 2's key name ("LSHIFT", "SPACE", "F3"...), or {@code null} for mouse/unknown codes. */
    public static String legacyName(int legacyCode) {
        if (legacyCode == NONE) return "NONE";
        return LEGACY_NAMES.get(legacyCode);
    }
}
