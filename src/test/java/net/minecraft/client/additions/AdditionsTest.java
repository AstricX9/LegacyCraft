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
