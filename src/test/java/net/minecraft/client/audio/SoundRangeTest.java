package net.minecraft.client.audio;

import net.minecraft.client.resources.ClasspathResourceManager;
import net.minecraft.client.settings.GameSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Positional sounds beyond their range of the listener aren't started (vanilla's server never sends them). */
final class SoundRangeTest {
    private final ClasspathResourceManager resources = new ClasspathResourceManager();
    private final SoundHandler sounds = new SoundHandler(SoundRegistry.load(resources), new GameSettings(), new SoundSystem(resources));

    @Test
    void everythingIsInRangeUntilTheListenerIsPlaced() {
        assertTrue(sounds.inRange(1000, 64, 1000, 16f));
    }

    @Test
    void soundsFartherThanTheirRangeAreCulled() {
        sounds.update(0, 64, 0, 0, 0, 1);
        assertTrue(sounds.inRange(10, 70, 10, 16f));
        assertFalse(sounds.inRange(20, 64, 0, 16f), "a swimming mob 20 blocks away");
        assertTrue(sounds.inRange(60, 64, 0, 64f), "loud sounds reach 16 x their volume");
    }

    @Test
    void menusWithNoPlayerDropTheListener() {
        sounds.update(0, 64, 0, 0, 0, 1);
        sounds.update();
        assertTrue(sounds.inRange(1000, 64, 1000, 16f));
    }
}
