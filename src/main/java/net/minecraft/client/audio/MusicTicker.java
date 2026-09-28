package net.minecraft.client.audio;

import java.util.Random;

/**
 * Background music (1.8.9 MusicTicker, HYPOTHESIS: recalled delays): the
 * title screen, survival and creative each have a music event and a pause
 * range in ticks between tracks. The first track waits 100 ticks; after a
 * track ends the next waits a random time in the range; switching to
 * another kind stops the track and waits up to half the new minimum.
 */
public final class MusicTicker {
    public enum Type {
        MENU("music.menu", 20, 600), GAME("music.game", 12000, 24000), CREATIVE("music.game.creative", 1200, 3600);

        final String event;
        final int minDelay, maxDelay;
        Type(String event, int minDelay, int maxDelay) {
            this.event = event;
            this.minDelay = minDelay;
            this.maxDelay = maxDelay;
        }
    }

    /** What the ticker needs from the sound system. */
    public interface Player {
        int playMusic(String event);
        boolean isPlaying(int handle);
        void stop(int handle);
    }

    private final Player player;
    private final Random random;
    private Type current;
    private int handle;
    private int timeUntilNext = 100;

    public MusicTicker(Player player, Random random) {
        this.player = player;
        this.random = random;
    }

    /** One client tick with the music the game wants now. */
    public void tick(Type wanted) {
        if (current != null) {
            if (current != wanted) {
                player.stop(handle);
                timeUntilNext = range(0, wanted.minDelay / 2);
            }
            if (!player.isPlaying(handle)) {
                current = null;
                handle = 0;
                timeUntilNext = Math.min(range(wanted.minDelay, wanted.maxDelay), timeUntilNext);
            }
        }
        if (current == null && timeUntilNext-- <= 0) {
            handle = player.playMusic(wanted.event);
            current = wanted;
            timeUntilNext = Integer.MAX_VALUE;
        }
    }

    public int timeUntilNext() { return timeUntilNext; }
    public Type current() { return current; }

    private int range(int min, int max) { return min >= max ? min : random.nextInt(max - min + 1) + min; }
}
