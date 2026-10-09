package net.minecraft.client.audio;

import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.ResourceLocation;

import java.util.Random;

/**
 * Plays sound events by name (1.8.9 SoundHandler/SoundManager, HYPOTHESIS):
 * a random variant of the event, at volume x the variant's volume x the
 * category's slider (clamped to 0-1; the master slider scales everything),
 * pitch x the variant's pitch (clamped to 0.5-2). A positional sound fades
 * out linearly over 16 blocks, or 16 x its volume when that is above 1.
 * Sounds that would be silent are not started, and neither are positional
 * sounds beyond their range of the listener (vanilla's server only sends a
 * sound to players within that range, so the client never hears the rest).
 */
public final class SoundHandler implements MusicTicker.Player {
    private final SoundRegistry registry;
    private final GameSettings settings;
    private final SoundSystem system;
    private final Random random = new Random();
    /** Where the listener was last placed; positional sounds are culled only once there is one. */
    private boolean hasListener;
    private double listenerX, listenerY, listenerZ;

    public SoundHandler(SoundRegistry registry, GameSettings settings, SoundSystem system) {
        this.registry = registry;
        this.settings = settings;
        this.system = system;
    }

    /** A sound in the world at a position (PositionedSoundRecord with linear attenuation). */
    public int play(String event, double x, double y, double z, float volume, float pitch) {
        float range = 16f * Math.max(1f, volume);
        if (!inRange(x, y, z, range)) return 0;
        return start(event, volume, pitch, x, y, z, range);
    }

    /** Whether a positional sound at (x, y, z) reaches the listener (always, before the listener is placed). */
    boolean inRange(double x, double y, double z, float range) {
        if (!hasListener) return true;
        double dx = x - listenerX, dy = y - listenerY, dz = z - listenerZ;
        return dx * dx + dy * dy + dz * dz <= (double) range * range;
    }

    /** A sound at the listener, such as a button click (volume 0.25, PositionedSoundRecord.create). */
    public int playUi(String event, float pitch) { return start(event, 0.25f, pitch, 0, 0, 0, 0); }

    @Override
    public int playMusic(String event) { return start(event, 1f, 1f, 0, 0, 0, 0); }

    @Override public boolean isPlaying(int handle) { return system.isPlaying(handle); }
    @Override public void stop(int handle) { system.stop(handle); }

    private int start(String event, float volume, float pitch, double x, double y, double z, float range) {
        SoundEvent sound = registry.get(new ResourceLocation(event));
        if (sound == null || !system.isReady()) return 0;
        SoundEvent.Variant variant = sound.choose(random);
        float gain = volume * variant.volume();
        if (Math.min(1f, gain * categoryGain(sound.category())) <= 0f) return 0;
        float finalPitch = Math.max(0.5f, Math.min(2f, pitch * variant.pitch()));
        return system.play(variant.file(), variant.stream(), sound.category(), gain, finalPitch, x, y, z, range,
            categoryGain(sound.category()));
    }

    /** The category slider; the master slider is applied to the whole output instead. */
    float categoryGain(String category) {
        return category == null || category.equals("master") ? 1f : settings.getSoundLevel(category);
    }

    /** Once a frame: listener at the player's eyes (x, y, z) looking along (lx, ly, lz); volumes follow the sliders. */
    public void update(double x, double y, double z, double lx, double ly, double lz) {
        hasListener = true;
        listenerX = x; listenerY = y; listenerZ = z;
        if (!system.isReady()) return;
        double horizontal = Math.sqrt(lx * lx + lz * lz);
        double hx = horizontal > 1e-6 ? lx / horizontal : 0, hz = horizontal > 1e-6 ? lz / horizontal : 1;
        system.setListener((float) x, (float) y, (float) z, (float) lx, (float) ly, (float) lz,
            (float) (-ly * hx), (float) horizontal, (float) (-ly * hz));
        system.setMasterGain(settings.getSoundLevel("master"));
        system.update(this::categoryGain);
    }

    /** Only volumes and streams, for menus with no player. */
    public void update() {
        hasListener = false;
        if (!system.isReady()) return;
        system.setMasterGain(settings.getSoundLevel("master"));
        system.update(this::categoryGain);
    }

    public void pauseAll() { system.pauseAll(); }
    public void resumeAll() { system.resumeAll(); }
    public void stopAll() { system.stopAll(); }
}
