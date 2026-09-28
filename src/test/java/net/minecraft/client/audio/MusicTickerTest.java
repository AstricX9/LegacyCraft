package net.minecraft.client.audio;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Background music timing (HYPOTHESIS: recalled 1.8.9 MusicTicker delays). */
final class MusicTickerTest {
    /** Plays nothing; a track "lasts" until {@link #finish} is called. */
    private static final class FakePlayer implements MusicTicker.Player {
        final List<String> started = new ArrayList<>();
        int playing, next = 1;
        @Override public int playMusic(String event) { started.add(event); return playing = next++; }
        @Override public boolean isPlaying(int handle) { return handle != 0 && handle == playing; }
        @Override public void stop(int handle) { if (handle == playing) playing = 0; }
        void finish() { playing = 0; }
    }

    @Test
    void theFirstTrackStartsAfterAHundredTicks() {
        FakePlayer player = new FakePlayer();
        MusicTicker ticker = new MusicTicker(player, new Random(1));
        for (int i = 0; i < 100; i++) ticker.tick(MusicTicker.Type.MENU);
        assertTrue(player.started.isEmpty());
        ticker.tick(MusicTicker.Type.MENU);
        assertEquals(List.of("music.menu"), player.started);
    }

    @Test
    void afterATrackEndsTheNextWaitsWithinTheTypesRange() {
        FakePlayer player = new FakePlayer();
        MusicTicker ticker = new MusicTicker(player, new Random(2));
        for (int i = 0; i <= 100; i++) ticker.tick(MusicTicker.Type.GAME);
        player.finish();
        ticker.tick(MusicTicker.Type.GAME);
        assertTrue(ticker.timeUntilNext() >= 12000 - 1 && ticker.timeUntilNext() <= 24000, "waits " + ticker.timeUntilNext());
    }

    @Test
    void switchingKindStopsTheTrackAndWaitsAtMostHalfTheNewMinimum() {
        FakePlayer player = new FakePlayer();
        MusicTicker ticker = new MusicTicker(player, new Random(3));
        for (int i = 0; i <= 100; i++) ticker.tick(MusicTicker.Type.MENU);
        ticker.tick(MusicTicker.Type.CREATIVE);
        assertEquals(0, player.playing, "the menu track stops");
        assertTrue(ticker.timeUntilNext() <= 600, "waits " + ticker.timeUntilNext());
        for (int i = 0; i <= 601; i++) ticker.tick(MusicTicker.Type.CREATIVE);
        assertEquals("music.game.creative", player.started.get(player.started.size() - 1));
    }
}
