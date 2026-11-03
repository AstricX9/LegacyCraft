package net.minecraft.client.settings;

import net.minecraft.client.gui.I18n;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Client options, persisted to {@code options.txt} in vanilla 1.8.9's
 * {@code name:value} format (master context §63, §66, §68).
 *
 * <p>Key names, value encodings and defaults follow 1.8.9
 * (COMMUNITY_CORROBORATED / HYPOTHESIS until diffed against a real
 * {@code options.txt} — see docs/compat/unknowns.yml "options.txt"). Lines
 * this class does not understand are kept and written back unchanged, so a
 * vanilla options file survives a round trip through LegacyCraft.
 *
 * <p>Deliberate deviation: the default render distance is lower than vanilla's
 * 12 chunks until chunk generation moves off the render thread (DEV-009).
 */
public final class GameSettings {
    /** Every option a menu button or slider can be bound to (1.8.9's {@code GameSettings.Options} set). */
    public enum Options {
        INVERT_MOUSE("options.invertMouse", Kind.BOOLEAN),
        SENSITIVITY("options.sensitivity", 0, 1, 0),
        FOV("options.fov", 30, 110, 1),
        GAMMA("options.gamma", 0, 1, 0),
        SATURATION("options.saturation", 0, 1, 0),
        RENDER_DISTANCE("options.renderDistance", 2, 16, 1),
        VIEW_BOBBING("options.viewBobbing", Kind.BOOLEAN),
        ANAGLYPH("options.anaglyph", Kind.BOOLEAN),
        FRAMERATE_LIMIT("options.framerateLimit", 10, 260, 10),
        FBO_ENABLE("options.fboEnable", Kind.BOOLEAN),
        RENDER_CLOUDS("options.renderClouds", Kind.BOOLEAN),
        GRAPHICS("options.graphics", Kind.CYCLE),
        AMBIENT_OCCLUSION("options.ao", Kind.CYCLE),
        GUI_SCALE("options.guiScale", Kind.CYCLE),
        PARTICLES("options.particles", Kind.CYCLE),
        CHAT_VISIBILITY("options.chat.visibility", Kind.CYCLE),
        CHAT_COLOR("options.chat.color", Kind.BOOLEAN),
        CHAT_LINKS("options.chat.links", Kind.BOOLEAN),
        CHAT_OPACITY("options.chat.opacity", 0, 1, 0),
        CHAT_LINKS_PROMPT("options.chat.links.prompt", Kind.BOOLEAN),
        SNOOPER_ENABLED("options.snooper", Kind.BOOLEAN),
        USE_FULLSCREEN("options.fullscreen", Kind.BOOLEAN),
        ENABLE_VSYNC("options.vsync", Kind.BOOLEAN),
        USE_VBO("options.vbo", Kind.BOOLEAN),
        TOUCHSCREEN("options.touchscreen", Kind.BOOLEAN),
        CHAT_SCALE("options.chat.scale", 0, 1, 0),
        CHAT_WIDTH("options.chat.width", 0, 1, 0),
        CHAT_HEIGHT_FOCUSED("options.chat.height.focused", 0, 1, 0),
        CHAT_HEIGHT_UNFOCUSED("options.chat.height.unfocused", 0, 1, 0),
        MIPMAP_LEVELS("options.mipmapLevels", 0, 4, 1),
        FORCE_UNICODE_FONT("options.forceUnicodeFont", Kind.BOOLEAN),
        BLOCK_ALTERNATIVES("options.blockAlternatives", Kind.BOOLEAN),
        REDUCED_DEBUG_INFO("options.reducedDebugInfo", Kind.BOOLEAN),
        ENTITY_SHADOWS("options.entityShadows", Kind.BOOLEAN),
        REALMS_NOTIFICATIONS("options.realmsNotifications", Kind.BOOLEAN);

        enum Kind { FLOAT, BOOLEAN, CYCLE }
        private final String translation;
        private final Kind kind;
        private final float step;
        private float min, max;

        Options(String translation, Kind kind) { this(translation, kind, 0, 1, 0); }
        Options(String translation, float min, float max, float step) { this(translation, Kind.FLOAT, min, max, step); }
        Options(String translation, Kind kind, float min, float max, float step) {
            this.translation = translation; this.kind = kind; this.min = min; this.max = max; this.step = step;
        }

        public String getTranslation() { return translation; }
        public boolean isFloat() { return kind == Kind.FLOAT; }
        public boolean isBoolean() { return kind == Kind.BOOLEAN; }
        public float getMin() { return min; }
        public float getMax() { return max; }
        public void setMax(float max) { this.max = max; }

        /** Slider position (0-1) for a value, clamped and snapped to the option's step. */
        public float normalizeValue(float value) {
            return clamp((snapToStepClamp(value) - min) / (max - min), 0, 1);
        }

        /** Value for a slider position (0-1), snapped to the option's step. */
        public float denormalizeValue(float normalized) {
            return snapToStepClamp(min + (max - min) * clamp(normalized, 0, 1));
        }

        public float snapToStepClamp(float value) {
            if (step > 0) value = step * Math.round(value / step);
            return clamp(value, min, max);
        }
    }

    public static final String[] SOUND_CATEGORIES = {"master", "music", "record", "weather", "block", "hostile", "neutral", "player", "ambient"};
    public static final String[] MODEL_PARTS = {"cape", "jacket", "left_sleeve", "right_sleeve", "left_pants_leg", "right_pants_leg", "hat"};
    private static final String[] GUI_SCALES = {"options.guiScale.auto", "options.guiScale.small", "options.guiScale.normal", "options.guiScale.large"};
    private static final String[] PARTICLE_NAMES = {"options.particles.all", "options.particles.decreased", "options.particles.minimal"};
    private static final String[] AO_NAMES = {"options.ao.off", "options.ao.min", "options.ao.max"};
    private static final String[] CHAT_VISIBILITIES = {"options.chat.visibility.full", "options.chat.visibility.system", "options.chat.visibility.hidden"};
    public static final String[] DIFFICULTIES = {"options.difficulty.peaceful", "options.difficulty.easy", "options.difficulty.normal", "options.difficulty.hard"};

    public float mouseSensitivity = 0.5f;
    public boolean invertMouse;
    public int renderDistanceChunks = 4;
    public boolean viewBobbing = true;
    public boolean anaglyph;
    public boolean fboEnable = true;
    public int limitFramerate = 120;
    public boolean clouds = true;
    public boolean fancyGraphics = true;
    public int ambientOcclusion = 2;
    public List<String> resourcePacks = new ArrayList<>();
    public List<String> incompatibleResourcePacks = new ArrayList<>();
    public int chatVisibility;
    public boolean chatColours = true, chatLinks = true, chatLinksPrompt = true;
    public float chatOpacity = 1.0f;
    public boolean snooperEnabled = true;
    public boolean fullScreen;
    public boolean enableVsync = true;
    public boolean useVbo;
    public boolean allowBlockAlternatives = true;
    public boolean reducedDebugInfo;
    public boolean hideServerAddress;
    public boolean advancedItemTooltips;
    public boolean pauseOnLostFocus = true;
    public boolean touchscreen;
    public int overrideWidth, overrideHeight;
    public boolean heldItemTooltips = true;
    public float chatScale = 1.0f, chatWidth = 1.0f;
    public float chatHeightUnfocused = 0.44366196f, chatHeightFocused = 1.0f;
    public boolean showInventoryAchievementHint = true;
    public int mipmapLevels = 4;
    public int difficulty = 2;
    public String lastServer = "";
    public float fovSetting = 70f;
    public float gammaSetting;
    public float saturation;
    public int guiScale;
    public int particleSetting;
    public String language = "en_US";
    public boolean forceUnicodeFont;
    public boolean entityShadows = true;
    public boolean realmsNotifications = true;
    public boolean useNativeTransport = true;
    private final Map<String, Float> soundLevels = new LinkedHashMap<>();
    private final Map<String, Boolean> modelParts = new LinkedHashMap<>();

    // Runtime-only state (never saved), matching vanilla's transient fields.
    public boolean hideGUI;
    /** 0 first person, 1 behind the player, 2 in front (F5; not saved, as vanilla). */
    public int thirdPersonView;
    public boolean showDebugInfo;
    public boolean showDebugProfilerChart;
    public boolean showLagometer;
    /** F3+G chunk borders and F3+B hitboxes (LegacyCraft keeps these here; vanilla keeps them on renderers). */
    public boolean showChunkBorders, showHitboxes;

    public final KeyBinding keyBindForward = new KeyBinding("key.forward", 17, "key.categories.movement");
    public final KeyBinding keyBindLeft = new KeyBinding("key.left", 30, "key.categories.movement");
    public final KeyBinding keyBindBack = new KeyBinding("key.back", 31, "key.categories.movement");
    public final KeyBinding keyBindRight = new KeyBinding("key.right", 32, "key.categories.movement");
    public final KeyBinding keyBindJump = new KeyBinding("key.jump", 57, "key.categories.movement");
    public final KeyBinding keyBindSneak = new KeyBinding("key.sneak", 42, "key.categories.movement");
    public final KeyBinding keyBindSprint = new KeyBinding("key.sprint", 29, "key.categories.movement");
    public final KeyBinding keyBindInventory = new KeyBinding("key.inventory", 18, "key.categories.inventory");
    public final KeyBinding keyBindUseItem = new KeyBinding("key.use", -99, "key.categories.gameplay");
    public final KeyBinding keyBindDrop = new KeyBinding("key.drop", 16, "key.categories.gameplay");
    public final KeyBinding keyBindAttack = new KeyBinding("key.attack", -100, "key.categories.gameplay");
    public final KeyBinding keyBindPickBlock = new KeyBinding("key.pickItem", -98, "key.categories.gameplay");
    public final KeyBinding keyBindChat = new KeyBinding("key.chat", 20, "key.categories.multiplayer");
    public final KeyBinding keyBindPlayerList = new KeyBinding("key.playerlist", 15, "key.categories.multiplayer");
    public final KeyBinding keyBindCommand = new KeyBinding("key.command", 53, "key.categories.multiplayer");
    public final KeyBinding keyBindScreenshot = new KeyBinding("key.screenshot", 60, "key.categories.misc");
    public final KeyBinding keyBindTogglePerspective = new KeyBinding("key.togglePerspective", 63, "key.categories.misc");
    public final KeyBinding keyBindSmoothCamera = new KeyBinding("key.smoothCamera", 0, "key.categories.misc");
    public final KeyBinding keyBindFullscreen = new KeyBinding("key.fullscreen", 87, "key.categories.misc");
    public final KeyBinding keyBindSpectatorOutlines = new KeyBinding("key.spectatorOutlines", 0, "key.categories.misc");
    public final KeyBinding keyBindStreamStartStop = new KeyBinding("key.streamStartStop", 64, "key.categories.stream");
    public final KeyBinding keyBindStreamPauseUnpause = new KeyBinding("key.streamPauseUnpause", 65, "key.categories.stream");
    public final KeyBinding keyBindStreamCommercials = new KeyBinding("key.streamCommercial", 0, "key.categories.stream");
    public final KeyBinding keyBindStreamToggleMic = new KeyBinding("key.streamToggleMic", 0, "key.categories.stream");
    public final KeyBinding[] keyBindsHotbar = new KeyBinding[9];
    public final List<KeyBinding> keyBindings = new ArrayList<>();

    /** Lines of options.txt this version does not recognise, kept verbatim for the next save. */
    private final Map<String, String> unknownLines = new LinkedHashMap<>();

    public GameSettings() {
        for (String category : SOUND_CATEGORIES) soundLevels.put(category, 1.0f);
        for (String part : MODEL_PARTS) modelParts.put(part, true);
        for (int i = 0; i < 9; i++) keyBindsHotbar[i] = new KeyBinding("key.hotbar." + (i + 1), 2 + i, "key.categories.inventory");
        keyBindings.addAll(List.of(keyBindAttack, keyBindUseItem, keyBindForward, keyBindLeft, keyBindBack, keyBindRight,
            keyBindJump, keyBindSneak, keyBindSprint, keyBindDrop, keyBindInventory, keyBindChat, keyBindPlayerList,
            keyBindPickBlock, keyBindCommand, keyBindScreenshot, keyBindTogglePerspective, keyBindSmoothCamera,
            keyBindStreamStartStop, keyBindStreamPauseUnpause, keyBindStreamCommercials, keyBindStreamToggleMic,
            keyBindFullscreen, keyBindSpectatorOutlines));
        keyBindings.addAll(List.of(keyBindsHotbar));
        // 1.8.9 raises the render distance cap to 32 on a 64-bit JVM with at least 1 GB of heap.
        if (is64Bit() && Runtime.getRuntime().maxMemory() >= 1_000_000_000L) Options.RENDER_DISTANCE.setMax(32);
    }

    // ---- values used by the renderer/HUD -------------------------------------------------

    public int getGuiScale() { return guiScale; }
    public void setGuiScale(int guiScale) { this.guiScale = Math.max(0, Math.min(3, guiScale)); }
    public int getRenderDistanceBlocks() { return renderDistanceChunks * 16; }
    public float getFov() { return fovSetting; }
    public void setFov(float fov) { this.fovSetting = clamp(fov, 30f, 110f); }
    public boolean isUnicodeFont() { return forceUnicodeFont; }
    public void setUnicodeFont(boolean unicodeFont) { this.forceUnicodeFont = unicodeFont; }
    public boolean isVsync() { return enableVsync; }
    public void setVsync(boolean vsync) { this.enableVsync = vsync; }
    public float getSoundLevel(String category) { return soundLevels.getOrDefault(category, 1.0f); }
    public void setSoundLevel(String category, float level) { soundLevels.put(category, clamp(level, 0, 1)); }
    public boolean isModelPartEnabled(String part) { return modelParts.getOrDefault(part, true); }
    public void toggleModelPart(String part) { modelParts.put(part, !isModelPartEnabled(part)); }

    /** F3+F / F3+Shift+F: one chunk further or nearer, clamped to the slider's range. */
    public void changeRenderDistance(int delta) {
        setOptionFloatValue(Options.RENDER_DISTANCE, renderDistanceChunks + delta);
    }

    // ---- option access used by the menus -------------------------------------------------

    public float getOptionFloatValue(Options option) {
        return switch (option) {
            case SENSITIVITY -> mouseSensitivity;
            case FOV -> fovSetting;
            case GAMMA -> gammaSetting;
            case SATURATION -> saturation;
            case RENDER_DISTANCE -> renderDistanceChunks;
            case FRAMERATE_LIMIT -> limitFramerate;
            case CHAT_OPACITY -> chatOpacity;
            case CHAT_SCALE -> chatScale;
            case CHAT_WIDTH -> chatWidth;
            case CHAT_HEIGHT_FOCUSED -> chatHeightFocused;
            case CHAT_HEIGHT_UNFOCUSED -> chatHeightUnfocused;
            case MIPMAP_LEVELS -> mipmapLevels;
            default -> 0f;
        };
    }

    public void setOptionFloatValue(Options option, float value) {
        value = option.snapToStepClamp(value);
        switch (option) {
            case SENSITIVITY -> mouseSensitivity = value;
            case FOV -> fovSetting = value;
            case GAMMA -> gammaSetting = value;
            case SATURATION -> saturation = value;
            case RENDER_DISTANCE -> renderDistanceChunks = (int) value;
            case FRAMERATE_LIMIT -> limitFramerate = (int) value;
            case CHAT_OPACITY -> chatOpacity = value;
            case CHAT_SCALE -> chatScale = value;
            case CHAT_WIDTH -> chatWidth = value;
            case CHAT_HEIGHT_FOCUSED -> chatHeightFocused = value;
            case CHAT_HEIGHT_UNFOCUSED -> chatHeightUnfocused = value;
            case MIPMAP_LEVELS -> mipmapLevels = (int) value;
            default -> throw new IllegalArgumentException(option + " is not a slider option");
        }
    }

    public boolean getOptionOrdinalValue(Options option) {
        return switch (option) {
            case INVERT_MOUSE -> invertMouse;
            case VIEW_BOBBING -> viewBobbing;
            case ANAGLYPH -> anaglyph;
            case FBO_ENABLE -> fboEnable;
            case RENDER_CLOUDS -> clouds;
            case CHAT_COLOR -> chatColours;
            case CHAT_LINKS -> chatLinks;
            case CHAT_LINKS_PROMPT -> chatLinksPrompt;
            case SNOOPER_ENABLED -> snooperEnabled;
            case USE_FULLSCREEN -> fullScreen;
            case ENABLE_VSYNC -> enableVsync;
            case USE_VBO -> useVbo;
            case TOUCHSCREEN -> touchscreen;
            case FORCE_UNICODE_FONT -> forceUnicodeFont;
            case BLOCK_ALTERNATIVES -> allowBlockAlternatives;
            case REDUCED_DEBUG_INFO -> reducedDebugInfo;
            case ENTITY_SHADOWS -> entityShadows;
            case REALMS_NOTIFICATIONS -> realmsNotifications;
            default -> false;
        };
    }

    /** A click on a non-slider option button: flips a boolean or steps a cycle. */
    public void setOptionValue(Options option, int step) {
        switch (option) {
            case INVERT_MOUSE -> invertMouse = !invertMouse;
            case GUI_SCALE -> guiScale = (guiScale + step) & 3;
            case PARTICLES -> particleSetting = (particleSetting + step) % 3;
            case VIEW_BOBBING -> viewBobbing = !viewBobbing;
            case RENDER_CLOUDS -> clouds = !clouds;
            case FORCE_UNICODE_FONT -> forceUnicodeFont = !forceUnicodeFont;
            case FBO_ENABLE -> fboEnable = !fboEnable;
            case ANAGLYPH -> anaglyph = !anaglyph;
            case GRAPHICS -> fancyGraphics = !fancyGraphics;
            case AMBIENT_OCCLUSION -> ambientOcclusion = (ambientOcclusion + step) % 3;
            case CHAT_VISIBILITY -> chatVisibility = (chatVisibility + step) % 3;
            case CHAT_COLOR -> chatColours = !chatColours;
            case CHAT_LINKS -> chatLinks = !chatLinks;
            case CHAT_LINKS_PROMPT -> chatLinksPrompt = !chatLinksPrompt;
            case SNOOPER_ENABLED -> snooperEnabled = !snooperEnabled;
            case TOUCHSCREEN -> touchscreen = !touchscreen;
            case USE_FULLSCREEN -> fullScreen = !fullScreen;
            case ENABLE_VSYNC -> enableVsync = !enableVsync;
            case USE_VBO -> useVbo = !useVbo;
            case BLOCK_ALTERNATIVES -> allowBlockAlternatives = !allowBlockAlternatives;
            case REDUCED_DEBUG_INFO -> reducedDebugInfo = !reducedDebugInfo;
            case ENTITY_SHADOWS -> entityShadows = !entityShadows;
            case REALMS_NOTIFICATIONS -> realmsNotifications = !realmsNotifications;
            default -> throw new IllegalArgumentException(option + " is not a button option");
        }
    }

    /** The button/slider caption, e.g. "FOV: Normal" or "Render Distance: 8 chunks". */
    public String getKeyBinding(Options option) {
        String prefix = I18n.format(option.getTranslation()) + ": ";
        if (option.isFloat()) {
            float value = getOptionFloatValue(option);
            float normalized = option.normalizeValue(value);
            return switch (option) {
                case SENSITIVITY -> normalized == 0 ? prefix + I18n.format("options.sensitivity.min")
                    : normalized == 1 ? prefix + I18n.format("options.sensitivity.max") : prefix + (int) (normalized * 200) + "%";
                case FOV -> value == 70 ? prefix + I18n.format("options.fov.min")
                    : value == option.getMax() ? prefix + I18n.format("options.fov.max") : prefix + (int) value;
                case FRAMERATE_LIMIT -> value == option.getMax() ? prefix + I18n.format("options.framerateLimit.max")
                    : prefix + (int) value + " fps";
                case GAMMA -> normalized == 0 ? prefix + I18n.format("options.gamma.min")
                    : normalized == 1 ? prefix + I18n.format("options.gamma.max") : prefix + "+" + (int) (normalized * 100) + "%";
                case SATURATION -> prefix + (int) (normalized * 400) + "%";
                case CHAT_OPACITY -> prefix + (int) (normalized * 90 + 10) + "%";
                case CHAT_HEIGHT_UNFOCUSED, CHAT_HEIGHT_FOCUSED -> prefix + chatHeightPixels(normalized) + "px";
                case CHAT_WIDTH -> prefix + chatWidthPixels(normalized) + "px";
                case RENDER_DISTANCE -> prefix + (int) value + " chunks";
                case MIPMAP_LEVELS -> value == 0 ? prefix + I18n.format("options.off") : prefix + (int) value;
                default -> normalized == 0 ? prefix + I18n.format("options.off") : prefix + (int) (normalized * 100) + "%";
            };
        }
        if (option.isBoolean()) return prefix + I18n.format(getOptionOrdinalValue(option) ? "options.on" : "options.off");
        return switch (option) {
            case GUI_SCALE -> prefix + I18n.format(GUI_SCALES[guiScale]);
            case CHAT_VISIBILITY -> prefix + I18n.format(CHAT_VISIBILITIES[chatVisibility]);
            case PARTICLES -> prefix + I18n.format(PARTICLE_NAMES[particleSetting]);
            case AMBIENT_OCCLUSION -> prefix + I18n.format(AO_NAMES[ambientOcclusion]);
            case GRAPHICS -> prefix + I18n.format(fancyGraphics ? "options.graphics.fancy" : "options.graphics.fast");
            default -> prefix;
        };
    }

    /** HYPOTHESIS: GuiNewChat's 1.8.9 pixel mapping (width 40-320px, height 20-180px). */
    public static int chatWidthPixels(float normalized) { return (int) Math.floor(normalized * (320 - 40) + 40); }
    public static int chatHeightPixels(float normalized) { return (int) Math.floor(normalized * (180 - 20) + 20); }

    // ---- options.txt ---------------------------------------------------------------------

    public void load(Path file) throws IOException {
        if (!Files.isRegularFile(file)) return;
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                int colon = line.indexOf(':');
                if (colon <= 0) continue;
                String key = line.substring(0, colon), value = line.substring(colon + 1);
                try {
                    if (!apply(key, value)) unknownLines.put(key, value);
                } catch (RuntimeException malformed) {
                    // Vanilla logs and skips a malformed option line; the default stays.
                    System.err.println("Skipping bad option: " + line);
                }
            }
        }
    }

    private boolean apply(String key, String value) {
        switch (key) {
            case "invertYMouse" -> invertMouse = bool(value);
            case "mouseSensitivity" -> mouseSensitivity = parseFloat(value);
            case "fov" -> fovSetting = parseFloat(value) * 40f + 70f;
            case "gamma" -> gammaSetting = parseFloat(value);
            case "saturation" -> saturation = parseFloat(value);
            case "renderDistance" -> renderDistanceChunks = Integer.parseInt(value);
            case "guiScale" -> guiScale = Integer.parseInt(value);
            case "particles" -> particleSetting = Integer.parseInt(value);
            case "bobView" -> viewBobbing = bool(value);
            case "anaglyph3d" -> anaglyph = bool(value);
            case "maxFps" -> limitFramerate = Integer.parseInt(value);
            case "fboEnable" -> fboEnable = bool(value);
            case "difficulty" -> difficulty = Integer.parseInt(value);
            case "fancyGraphics" -> fancyGraphics = bool(value);
            case "ao" -> ambientOcclusion = value.equals("true") ? 2 : value.equals("false") ? 0 : Integer.parseInt(value);
            case "renderClouds" -> clouds = bool(value);
            case "resourcePacks" -> resourcePacks = parseStringList(value);
            case "incompatibleResourcePacks" -> incompatibleResourcePacks = parseStringList(value);
            case "lastServer" -> lastServer = value;
            case "lang" -> language = value;
            case "chatVisibility" -> chatVisibility = Integer.parseInt(value);
            case "chatColors" -> chatColours = bool(value);
            case "chatLinks" -> chatLinks = bool(value);
            case "chatLinksPrompt" -> chatLinksPrompt = bool(value);
            case "chatOpacity" -> chatOpacity = parseFloat(value);
            case "snooperEnabled" -> snooperEnabled = bool(value);
            case "fullscreen" -> fullScreen = bool(value);
            case "enableVsync" -> enableVsync = bool(value);
            case "useVbo" -> useVbo = bool(value);
            case "hideServerAddress" -> hideServerAddress = bool(value);
            case "advancedItemTooltips" -> advancedItemTooltips = bool(value);
            case "pauseOnLostFocus" -> pauseOnLostFocus = bool(value);
            case "touchscreen" -> touchscreen = bool(value);
            case "overrideWidth" -> overrideWidth = Integer.parseInt(value);
            case "overrideHeight" -> overrideHeight = Integer.parseInt(value);
            case "heldItemTooltips" -> heldItemTooltips = bool(value);
            case "chatHeightFocused" -> chatHeightFocused = parseFloat(value);
            case "chatHeightUnfocused" -> chatHeightUnfocused = parseFloat(value);
            case "chatScale" -> chatScale = parseFloat(value);
            case "chatWidth" -> chatWidth = parseFloat(value);
            case "showInventoryAchievementHint" -> showInventoryAchievementHint = bool(value);
            case "mipmapLevels" -> mipmapLevels = Integer.parseInt(value);
            case "forceUnicodeFont" -> forceUnicodeFont = bool(value);
            case "allowBlockAlternatives" -> allowBlockAlternatives = bool(value);
            case "reducedDebugInfo" -> reducedDebugInfo = bool(value);
            case "useNativeTransport" -> useNativeTransport = bool(value);
            case "entityShadows" -> entityShadows = bool(value);
            case "realmsNotifications" -> realmsNotifications = bool(value);
            default -> {
                if (key.startsWith("key_")) {
                    for (KeyBinding binding : keyBindings)
                        if (key.equals("key_" + binding.getKeyDescription())) { binding.setKeyCode(Integer.parseInt(value)); return true; }
                    return false;
                }
                if (key.startsWith("soundCategory_") && soundLevels.containsKey(key.substring(14))) {
                    soundLevels.put(key.substring(14), parseFloat(value));
                    return true;
                }
                if (key.startsWith("modelPart_") && modelParts.containsKey(key.substring(10))) {
                    modelParts.put(key.substring(10), bool(value));
                    return true;
                }
                return false;
            }
        }
        return true;
    }

    public void save(Path file) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        try (Writer out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            for (var entry : toLines().entrySet()) out.write(entry.getKey() + ":" + entry.getValue() + "\n");
        }
    }

    /** Every saved line in the order vanilla 1.8.9 writes them (HYPOTHESIS for the exact order). */
    public Map<String, String> toLines() {
        Map<String, String> lines = new LinkedHashMap<>();
        lines.put("invertYMouse", String.valueOf(invertMouse));
        lines.put("mouseSensitivity", String.valueOf(mouseSensitivity));
        lines.put("fov", String.valueOf((fovSetting - 70f) / 40f));
        lines.put("gamma", String.valueOf(gammaSetting));
        lines.put("saturation", String.valueOf(saturation));
        lines.put("renderDistance", String.valueOf(renderDistanceChunks));
        lines.put("guiScale", String.valueOf(guiScale));
        lines.put("particles", String.valueOf(particleSetting));
        lines.put("bobView", String.valueOf(viewBobbing));
        lines.put("anaglyph3d", String.valueOf(anaglyph));
        lines.put("maxFps", String.valueOf(limitFramerate));
        lines.put("fboEnable", String.valueOf(fboEnable));
        lines.put("difficulty", String.valueOf(difficulty));
        lines.put("fancyGraphics", String.valueOf(fancyGraphics));
        lines.put("ao", String.valueOf(ambientOcclusion));
        lines.put("renderClouds", String.valueOf(clouds));
        lines.put("resourcePacks", toJsonList(resourcePacks));
        lines.put("incompatibleResourcePacks", toJsonList(incompatibleResourcePacks));
        lines.put("lastServer", lastServer);
        lines.put("lang", language);
        lines.put("chatVisibility", String.valueOf(chatVisibility));
        lines.put("chatColors", String.valueOf(chatColours));
        lines.put("chatLinks", String.valueOf(chatLinks));
        lines.put("chatLinksPrompt", String.valueOf(chatLinksPrompt));
        lines.put("chatOpacity", String.valueOf(chatOpacity));
        lines.put("snooperEnabled", String.valueOf(snooperEnabled));
        lines.put("fullscreen", String.valueOf(fullScreen));
        lines.put("enableVsync", String.valueOf(enableVsync));
        lines.put("useVbo", String.valueOf(useVbo));
        lines.put("hideServerAddress", String.valueOf(hideServerAddress));
        lines.put("advancedItemTooltips", String.valueOf(advancedItemTooltips));
        lines.put("pauseOnLostFocus", String.valueOf(pauseOnLostFocus));
        lines.put("touchscreen", String.valueOf(touchscreen));
        lines.put("overrideWidth", String.valueOf(overrideWidth));
        lines.put("overrideHeight", String.valueOf(overrideHeight));
        lines.put("heldItemTooltips", String.valueOf(heldItemTooltips));
        lines.put("chatHeightFocused", String.valueOf(chatHeightFocused));
        lines.put("chatHeightUnfocused", String.valueOf(chatHeightUnfocused));
        lines.put("chatScale", String.valueOf(chatScale));
        lines.put("chatWidth", String.valueOf(chatWidth));
        lines.put("showInventoryAchievementHint", String.valueOf(showInventoryAchievementHint));
        lines.put("mipmapLevels", String.valueOf(mipmapLevels));
        lines.putAll(streamLinesKeptVerbatim());
        lines.put("forceUnicodeFont", String.valueOf(forceUnicodeFont));
        lines.put("allowBlockAlternatives", String.valueOf(allowBlockAlternatives));
        lines.put("reducedDebugInfo", String.valueOf(reducedDebugInfo));
        lines.put("useNativeTransport", String.valueOf(useNativeTransport));
        lines.put("entityShadows", String.valueOf(entityShadows));
        lines.put("realmsNotifications", String.valueOf(realmsNotifications));
        for (KeyBinding binding : keyBindings) lines.put("key_" + binding.getKeyDescription(), String.valueOf(binding.getKeyCode()));
        for (var level : soundLevels.entrySet()) lines.put("soundCategory_" + level.getKey(), String.valueOf(level.getValue()));
        for (var part : modelParts.entrySet()) lines.put("modelPart_" + part.getKey(), String.valueOf(part.getValue()));
        for (var unknown : unknownLines.entrySet()) lines.putIfAbsent(unknown.getKey(), unknown.getValue());
        return lines;
    }

    /** Twitch settings: LegacyCraft has no broadcast support, so these are only carried through. */
    private Map<String, String> streamLinesKeptVerbatim() {
        Map<String, String> stream = new LinkedHashMap<>();
        for (var entry : unknownLines.entrySet())
            if (entry.getKey().startsWith("stream")) stream.put(entry.getKey(), entry.getValue());
        return stream;
    }

    private static boolean bool(String value) { return value.equals("true"); }
    private static float parseFloat(String value) { return Float.parseFloat(value); }

    static List<String> parseStringList(String json) {
        List<String> values = new ArrayList<>();
        String body = json.trim();
        if (!body.startsWith("[") || !body.endsWith("]")) return values;
        body = body.substring(1, body.length() - 1).trim();
        if (body.isEmpty()) return values;
        for (String part : body.split(",")) {
            String value = part.trim();
            if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) value = value.substring(1, value.length() - 1);
            values.add(value.replace("\\\"", "\"").replace("\\\\", "\\"));
        }
        return values;
    }

    static String toJsonList(List<String> values) {
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) out.append(',');
            out.append('"').append(values.get(i).replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
        }
        return out.append(']').toString();
    }

    private static boolean is64Bit() {
        String model = System.getProperty("sun.arch.data.model", "");
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        return model.equals("64") || arch.contains("64");
    }

    private static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }

    /** Snapshot of the per-option enum values the menus care about (tests only). */
    Map<Options, String> captions() {
        Map<Options, String> captions = new EnumMap<>(Options.class);
        for (Options option : Options.values()) captions.put(option, getKeyBinding(option));
        return captions;
    }
}
