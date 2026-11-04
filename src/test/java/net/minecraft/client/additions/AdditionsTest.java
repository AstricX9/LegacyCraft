package net.minecraft.client.additions;

import net.minecraft.client.entity.Player;
import net.minecraft.client.settings.KeyBinding;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Catalog discovery, instance switches, and settings shared by the game and launcher. */
final class AdditionsTest {
    @Test
    void instanceFolderIsAuthoritativeAndRemovingDefinitionsKeepsTheirSavedChoices(@TempDir Path dir) throws Exception {
        Path catalog = Files.createDirectories(dir.resolve("additions"));
        Path definition = catalog.resolve("fullBright.json");
        String json;
        try (var in = AdditionsTest.class.getResourceAsStream("/additions/fullBright.json")) {
            json = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        json = json.replace("Full Bright", "Folder Full Bright");
        Files.writeString(definition, json);
        Files.writeString(catalog.resolve("_ignored.json"), "invalid JSON");
        Files.writeString(catalog.resolve("bad.json"), "invalid JSON");
        Files.createDirectory(catalog.resolve("notAFile.json"));
        Path settings = dir.resolve("additions.txt");
        Files.writeString(settings, "enabled:true\nfullBright:true\nfullBright.brightness:70\ncoordinates:true\n");
        Additions additions = Additions.load(settings);
        assertEquals(1, additions.game().size(), "an instance catalog replaces the installation catalog");
        assertEquals("Folder Full Bright", additions.find(Additions.FULL_BRIGHT).name());
        assertTrue(additions.isOn(Additions.FULL_BRIGHT));
        assertEquals(70, additions.number(Additions.FULL_BRIGHT, "brightness"));
        assertFalse(additions.isOn(Additions.COORDINATES));
        additions.save();
        assertTrue(Files.readString(settings).contains("coordinates:true"), "absent definitions keep their saved choices");

        Files.delete(definition);
        Additions removed = Additions.load(settings);
        assertTrue(removed.game().isEmpty(), "an empty folder does not restore bundled additions");
        assertFalse(removed.isOn(Additions.FULL_BRIGHT));
        removed.save();
        Files.writeString(definition, json);
        Additions restored = Additions.load(settings);
        assertTrue(restored.isOn(Additions.FULL_BRIGHT));
        assertEquals(70, restored.number(Additions.FULL_BRIGHT, "brightness"));
    }

    @Test
    void bundledCatalogIsAvailableWithoutLooseFilesAndDuplicateIdsAreSkipped(@TempDir Path dir) throws Exception {
        List<Addition> bundled = Additions.loadCatalog(dir.resolve("missing"));
        assertTrue(bundled.stream().anyMatch(a -> a.id().equals(Additions.LEGACY_CONSOLE_GRAPHICS)));
        assertTrue(bundled.stream().anyMatch(a -> a.scope() == Addition.Scope.LAUNCHER));
        Path catalog = Files.createDirectory(dir.resolve("catalog"));
        byte[] json;
        try (var in = AdditionsTest.class.getResourceAsStream("/additions/fullBright.json")) { json = in.readAllBytes(); }
        Files.write(catalog.resolve("first.json"), json);
        Files.write(catalog.resolve("second.json"), json);
        assertEquals(1, Additions.loadCatalog(catalog).size(), "one row per addition ID");
    }

    @Test
    void catalogCoversEveryCategoryAndEveryAdditionTheGameChecks() throws Exception {
        Additions additions = Additions.defaults();
        Set<String> ids = new HashSet<>();
        for (Addition.Category c : Addition.Category.values()) {
            if (c == Addition.Category.LAUNCHER) assertTrue(additions.game(c).isEmpty(), "launcher additions aren't the game's");
            else assertFalse(additions.game(c).isEmpty(), c + " has a game addition");
        }
        for (Addition a : additions.game()) assertTrue(ids.add(a.id()), "unique id " + a.id());
        for (var field : Additions.class.getFields())
            if (field.getType() == String.class) assertNotNull(additions.find((String) field.get(null)), field.getName() + " is in the catalog");
    }

    @Test
    void additionsStartOffAndTheMasterSwitchGatesEveryAddition() {
        Additions additions = Additions.defaults();
        assertFalse(additions.enabled());
        for (Addition a : additions.game()) assertFalse(additions.isOn(a.id()));
        additions.set(Additions.FULL_BRIGHT, true);
        additions.setEnabled(false);
        assertTrue(additions.isSwitchedOn(Additions.FULL_BRIGHT));
        assertFalse(additions.isOn(Additions.FULL_BRIGHT), "switched on, but Additions are off");
        additions.setEnabled(true);
        assertTrue(additions.isOn(Additions.FULL_BRIGHT));
    }

    @Test
    void savesToAdditionsTxtAndKeepsLinesItDoesNotKnow() throws Exception {
        Path file = Files.createTempDirectory("lc-additions").resolve("additions.txt");
        Files.writeString(file, "fullBright:true\nfromTheFuture:true\n");
        Additions additions = Additions.load(file);
        assertTrue(additions.enabled(), "a file from before the master switch counts as on when something is switched on");
        assertTrue(additions.isOn(Additions.FULL_BRIGHT));
        additions.set(Additions.COORDINATES, true);
        additions.save();
        List<String> lines = Files.readAllLines(file);
        assertEquals("enabled:true", lines.get(0));
        assertTrue(lines.contains("fullBright:true"));
        assertTrue(lines.contains("coordinates:true"));
        assertTrue(lines.contains("toggleSprint:false"));
        assertTrue(lines.contains("fromTheFuture:true"));
        Files.writeString(file, "enabled:false\nfullBright:true\n");
        assertFalse(Additions.load(file).isOn(Additions.FULL_BRIGHT));
        Files.writeString(file, "enabled:true\ncoordinates:true\n");
        assertTrue(Additions.load(file).isOn(Additions.COORDINATES));
    }

    @Test
    void theTemplateIsSkippedButShowsEveryKindOfSetting() throws Exception {
        assertNull(Additions.defaults().find("myAddition"), "files starting with _ aren't additions");
        Addition template;
        try (var in = AdditionsTest.class.getResourceAsStream("/additions/_template.json")) {
            template = Additions.parse(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
        }
        Set<Addition.Setting.Type> types = new HashSet<>();
        for (Addition.Setting s : template.settings()) types.add(s.type());
        assertEquals(Set.of(Addition.Setting.Type.values()), types);
    }

    @Test
    void settingsSaveAsAdditionDotSettingLinesAndStayValid() throws Exception {
        Path dir = Files.createTempDirectory("lc-additions");
        Path file = dir.resolve("additions.txt");
        Files.writeString(file, "enabled:true\nfullBright:true\nfullBright.brightness:banana\nfullBright.fromTheFuture:1\n");
        Additions additions = Additions.load(file);
        assertEquals("100", additions.setting(Additions.FULL_BRIGHT, "brightness"), "a value it can't use is the default");
        additions.setSetting(Additions.FULL_BRIGHT, "brightness", "54");
        assertEquals(50, additions.number(Additions.FULL_BRIGHT, "brightness"), "slider values snap to the step");
        additions.setSetting(Additions.FULL_BRIGHT, "brightness", "500");
        assertEquals("100", additions.setting(Additions.FULL_BRIGHT, "brightness"), "and stay in range");
        additions.setSetting(Additions.SOLID_CROSSHAIR, "color", "Green");
        additions.setSetting(Additions.SOLID_CROSSHAIR, "color", "Plaid");
        assertEquals("Green", additions.setting(Additions.SOLID_CROSSHAIR, "color"));
        additions.setSetting(Additions.COORDINATES, "showFacing", "false");
        additions.setSetting(Additions.SHADER_PACKS, "pack", "../secret.zip");
        assertEquals("", additions.setting(Additions.SHADER_PACKS, "pack"), "files come from the addition's own folder");
        additions.save();
        List<String> lines = Files.readAllLines(file);
        assertTrue(lines.contains("fullBright.brightness:100"));
        assertTrue(lines.contains("solidCrosshair.color:Green"));
        assertTrue(lines.contains("fullBright.fromTheFuture:1"), "settings a newer game knows are kept");
        Additions again = Additions.load(file);
        assertFalse(again.settingOn(Additions.COORDINATES, "showFacing"));
        again.resetSettings(Additions.COORDINATES);
        assertTrue(again.settingOn(Additions.COORDINATES, "showFacing"));
        assertNull(again.setting(Additions.COORDINATES, "nothing"));

        Addition.Setting pack = again.find(Additions.SHADER_PACKS).setting("pack");
        Files.createDirectories(dir.resolve("shaderpacks"));
        Files.writeString(dir.resolve("shaderpacks/b.zip"), "");
        Files.writeString(dir.resolve("shaderpacks/A.zip"), "");
        Files.writeString(dir.resolve("shaderpacks/notes.txt"), "");
        Files.createDirectories(dir.resolve("shaderpacks/Unzipped"));
        assertEquals(List.of("A.zip", "b.zip", "Unzipped"), again.files(pack));
    }

}
