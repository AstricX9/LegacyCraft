package net.minecraft.client.additions;

import net.minecraft.client.entity.Player;
import net.minecraft.client.settings.KeyBinding;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class AdditionsTest {
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
    void everythingStartsOffWhichIsTheFaithfulLook() {
        Additions additions = Additions.defaults();
        assertEquals(Additions.Look.FAITHFUL, additions.look());
        for (Addition a : additions.game()) assertFalse(additions.isOn(a.id()));
        additions.apply(Additions.Look.RECOMMENDED);
        assertEquals(Additions.Look.RECOMMENDED, additions.look());
        assertTrue(additions.isOn(Additions.TOGGLE_SPRINT));
        additions.set(Additions.FULL_BRIGHT, true);
        assertEquals(Additions.Look.CUSTOM, additions.look());
        additions.apply(Additions.Look.FAITHFUL);
        assertEquals(Additions.Look.FAITHFUL, additions.look());
    }

    @Test
    void savesToAdditionsTxtAndKeepsLinesItDoesNotKnow() throws Exception {
        Path file = Files.createTempDirectory("lc-additions").resolve("additions.txt");
        Files.writeString(file, "fullBright:true\nfromTheFuture:true\n");
        Additions additions = Additions.load(file);
        assertTrue(additions.isOn(Additions.FULL_BRIGHT));
        additions.set(Additions.COORDINATES, true);
        additions.save();
        List<String> lines = Files.readAllLines(file);
        assertTrue(lines.contains("fullBright:true"));
        assertTrue(lines.contains("coordinates:true"));
        assertTrue(lines.contains("toggleSprint:false"));
        assertTrue(lines.contains("fromTheFuture:true"));
        assertTrue(Additions.load(file).isOn(Additions.COORDINATES));
    }

    @Test
    void toggleSprintLatchesOnOnePressAndLetsGoOnTheNext() {
        KeyBinding sprint = new KeyBinding("key.sprint", 29, "key.categories.movement");
        KeyBinding sneak = new KeyBinding("key.sneak", 42, "key.categories.movement");
        ToggleKeys toggles = new ToggleKeys();
        Player.Input none = Player.Input.NONE;
        // Presses made while the addition is off are thrown away.
        KeyBinding.onTransition(List.of(sprint), 29, true);
        assertFalse(toggles.apply(none, sprint, sneak, false, false).sprint());
        assertFalse(toggles.apply(none, sprint, sneak, true, false).sprint());
        KeyBinding.onTransition(List.of(sprint), 29, true);
        assertTrue(toggles.apply(none, sprint, sneak, true, false).sprint());
        assertTrue(toggles.apply(none, sprint, sneak, true, false).sprint(), "stays on with the key up");
        KeyBinding.onTransition(List.of(sprint), 29, true);
        assertFalse(toggles.apply(none, sprint, sneak, true, false).sprint());
        KeyBinding.onTransition(List.of(sneak), 42, true);
        assertTrue(toggles.apply(none, sprint, sneak, true, true).sneak());
        assertFalse(toggles.apply(none, sprint, sneak, true, false).sneak(), "turning the addition off lets go");
    }
}
