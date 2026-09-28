package net.minecraft.client.audio;

import net.minecraft.client.resources.ClasspathResourceManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The sound registry discovers the sound events bundled in the resource pack. */
final class SoundRegistryTest {
    @Test
    void discoversBundledSoundEvents() {
        SoundRegistry sounds = SoundRegistry.load(new ClasspathResourceManager());
        assertTrue(sounds.size() > 100);
        assertNotNull(sounds.get("minecraft:ambient.weather.rain"));
    }
}
