package net.minecraft.client.settings;

import net.minecraft.client.gui.I18n;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Game options: ranges, captions, slider steps, cycling buttons and the options.txt file. */
final class GameSettingsTest {
    @BeforeEach
    void english() {
        I18n.set(Map.ofEntries(
            Map.entry("options.fov", "FOV"), Map.entry("options.fov.min", "Normal"), Map.entry("options.fov.max", "Quake Pro"),
            Map.entry("options.renderDistance", "Render Distance"), Map.entry("options.framerateLimit", "Max Framerate"),
            Map.entry("options.framerateLimit.max", "Unlimited"), Map.entry("options.on", "ON"), Map.entry("options.off", "OFF"),
            Map.entry("options.viewBobbing", "View Bobbing"), Map.entry("options.guiScale", "GUI Scale"),
            Map.entry("options.guiScale.auto", "Auto"), Map.entry("options.guiScale.small", "Small"),
            Map.entry("options.sensitivity", "Sensitivity"), Map.entry("options.sensitivity.min", "*yawn*"),
            Map.entry("options.gamma", "Brightness"), Map.entry("options.gamma.min", "Moody"), Map.entry("options.mipmapLevels", "Mipmap Levels")));
    }

    @Test
    void renderDistanceStepsByOneChunkAndClampsToTheSliderRange() {
        GameSettings settings = new GameSettings();
        settings.renderDistanceChunks = 8;
        settings.changeRenderDistance(1);
        assertEquals(9, settings.renderDistanceChunks);
        assertEquals(144, settings.getRenderDistanceBlocks());
        settings.renderDistanceChunks = 2;
        settings.changeRenderDistance(-1);
        assertEquals(2, settings.renderDistanceChunks);
    }

    @Test
    void clampsFovAndGuiScaleToVanillaRanges() {
        GameSettings settings = new GameSettings();
        settings.setFov(500);
        settings.setGuiScale(99);
        assertEquals(110f, settings.getFov());
        assertEquals(3, settings.getGuiScale(), "1.8.9 GUI scale is Auto/Small/Normal/Large (0-3)");
        assertEquals(0, new GameSettings().getGuiScale(), "vanilla defaults to Auto");
    }

    @Test
    void captionsUseVanillaWording() {
        GameSettings settings = new GameSettings();
        assertEquals("FOV: Normal", settings.getKeyBinding(GameSettings.Options.FOV));
        settings.setOptionFloatValue(GameSettings.Options.FOV, 110);
        assertEquals("FOV: Quake Pro", settings.getKeyBinding(GameSettings.Options.FOV));
        settings.setOptionFloatValue(GameSettings.Options.FOV, 90.4f);
        assertEquals("FOV: 90", settings.getKeyBinding(GameSettings.Options.FOV));
        settings.renderDistanceChunks = 12;
        assertEquals("Render Distance: 12 chunks", settings.getKeyBinding(GameSettings.Options.RENDER_DISTANCE));
        assertEquals("Max Framerate: 120 fps", settings.getKeyBinding(GameSettings.Options.FRAMERATE_LIMIT));
        settings.setOptionFloatValue(GameSettings.Options.FRAMERATE_LIMIT, 260);
        assertEquals("Max Framerate: Unlimited", settings.getKeyBinding(GameSettings.Options.FRAMERATE_LIMIT));
        assertEquals("View Bobbing: ON", settings.getKeyBinding(GameSettings.Options.VIEW_BOBBING));
        assertEquals("GUI Scale: Auto", settings.getKeyBinding(GameSettings.Options.GUI_SCALE));
        assertEquals("Sensitivity: 100%", settings.getKeyBinding(GameSettings.Options.SENSITIVITY));
        settings.mouseSensitivity = 0;
        assertEquals("Sensitivity: *yawn*", settings.getKeyBinding(GameSettings.Options.SENSITIVITY));
        assertEquals("Brightness: Moody", settings.getKeyBinding(GameSettings.Options.GAMMA));
        settings.mipmapLevels = 0;
        assertEquals("Mipmap Levels: OFF", settings.getKeyBinding(GameSettings.Options.MIPMAP_LEVELS));
    }

    @Test
    void slidersSnapToTheirStep() {
        GameSettings.Options framerate = GameSettings.Options.FRAMERATE_LIMIT;
        assertEquals(120f, framerate.denormalizeValue(framerate.normalizeValue(123f)));
        assertEquals(10f, framerate.denormalizeValue(-5f));
        assertEquals(0f, GameSettings.Options.FOV.normalizeValue(30f));
    }

    @Test
    void cyclingButtonsWrapLikeVanilla() {
        GameSettings settings = new GameSettings();
        settings.guiScale = 3;
        settings.setOptionValue(GameSettings.Options.GUI_SCALE, 1);
        assertEquals(0, settings.guiScale);
        settings.setOptionValue(GameSettings.Options.AMBIENT_OCCLUSION, 1);
        assertEquals(0, settings.ambientOcclusion);
        settings.setOptionValue(GameSettings.Options.GRAPHICS, 1);
        assertFalse(settings.fancyGraphics);
    }

    @Test
    void optionsFileRoundTripsAndKeepsUnknownLines(Path directory) throws Exception {
        Path file = directory.resolve("options.txt");
        Files.writeString(file, String.join("\n",
            "invertYMouse:true", "fov:0.5", "renderDistance:9", "guiScale:2", "lang:de_DE",
            "key_key.jump:29", "soundCategory_music:0.25", "modelPart_hat:false",
            "streamKbps:0.5412844", "someFutureOption:42", "resourcePacks:[\"a.zip\",\"b\"]", ""));
        GameSettings settings = new GameSettings();
        settings.load(file);
        assertTrue(settings.invertMouse);
        assertEquals(90f, settings.fovSetting, 1e-4, "fov is stored normalized: 70 + 0.5 * 40");
        assertEquals(9, settings.renderDistanceChunks);
        assertEquals("de_DE", settings.language);
        assertEquals(29, settings.keyBindJump.getKeyCode());
        assertEquals(0.25f, settings.getSoundLevel("music"));
        assertFalse(settings.isModelPartEnabled("hat"));
        assertEquals(List.of("a.zip", "b"), settings.resourcePacks);

        settings.save(file);
        List<String> lines = Files.readAllLines(file);
        assertEquals("invertYMouse:true", lines.get(0), "vanilla's first line");
        assertTrue(lines.contains("fov:0.5"));
        assertTrue(lines.contains("key_key.jump:29"));
        assertTrue(lines.contains("streamKbps:0.5412844"), "Twitch options are carried through");
        assertTrue(lines.contains("someFutureOption:42"), "unknown lines survive a save");
        assertTrue(lines.contains("resourcePacks:[\"a.zip\",\"b\"]"));
        assertTrue(lines.contains("key_key.attack:-100"));
        assertTrue(lines.contains("key_key.hotbar.1:2"));
    }

    @Test
    void malformedLinesAreSkipped(Path directory) throws Exception {
        Path file = directory.resolve("options.txt");
        Files.writeString(file, "renderDistance:lots\nguiScale:1\n");
        GameSettings settings = new GameSettings();
        settings.load(file);
        assertEquals(4, settings.renderDistanceChunks);
        assertEquals(1, settings.guiScale);
    }
}
