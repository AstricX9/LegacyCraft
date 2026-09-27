package net.minecraft.client.gui;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class I18nTest {
    @Test
    void parsesLangFilesAndFormatsArguments() throws Exception {
        Map<String, String> table = new HashMap<>();
        I18n.parse(new ByteArrayInputStream(("# comment\nselectWorld.newWorld.copyOf=Copy of %s\n"
            + "options.languageWarning=Language translations may not be 100%% accurate\n\nbroken line\n")
            .getBytes(StandardCharsets.UTF_8)), table);
        I18n.set(table);
        assertEquals("Copy of Home", I18n.format("selectWorld.newWorld.copyOf", "Home"));
        assertEquals("Language translations may not be 100% accurate", I18n.format("options.languageWarning"));
        assertEquals("missing.key", I18n.format("missing.key"), "untranslated keys render as the key");
        assertEquals(2, table.size());
    }
}
