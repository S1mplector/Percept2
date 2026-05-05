package com.musicplayer.core.playlist;

import com.musicplayer.data.models.Song;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AdvancedPlaylistEngineTest {

    @Test
    void repeat_one_mode_returns_same_song_on_next_without_advancing_or_history_growth() {
        // Given a playlist of three songs
        Song s1 = new Song(1, "One", "A", "Al", "G", 180, "/a/1.mp3", 1, 2001);
        Song s2 = new Song(2, "Two", "B", "Al", "G", 200, "/a/2.mp3", 2, 2002);
        Song s3 = new Song(3, "Three", "C", "Al", "G", 220, "/a/3.mp3", 3, 2003);
        List<Song> list = Arrays.asList(s1, s2, s3);

        AdvancedPlaylistEngine engine = new AdvancedPlaylistEngine();
        engine.setPlaylist(list);
        engine.setCurrentIndex(1); // current = s2
        engine.setRepeatMode(PlaylistEngine.RepeatMode.ONE);

        // When next() is called in repeat-one mode
        Song next = engine.next();

        // Then the same song should be returned and current index should remain the same
        assertEquals(s2, next, "Repeat ONE should return the current song");
        assertEquals(1, engine.getCurrentIndex(), "Index should not advance in repeat ONE mode");

        // And history should not grow because we didn't advance tracks
        assertTrue(engine.getHistory().isEmpty(), "History should remain empty when repeating the same song");
    }

    @Test
    void repeat_all_wraps_to_start_and_updates_history() {
        // Given a playlist of three songs
        Song s1 = new Song(1, "One", "A", "Al", "G", 180, "/a/1.mp3", 1, 2001);
        Song s2 = new Song(2, "Two", "B", "Al", "G", 200, "/a/2.mp3", 2, 2002);
        Song s3 = new Song(3, "Three", "C", "Al", "G", 220, "/a/3.mp3", 3, 2003);
        List<Song> list = Arrays.asList(s1, s2, s3);

        AdvancedPlaylistEngine engine = new AdvancedPlaylistEngine();
        engine.setPlaylist(list);
        engine.setCurrentIndex(2); // current = s3 (end of playlist)
        engine.setRepeatMode(PlaylistEngine.RepeatMode.ALL);

        // When next() is called at end with Repeat ALL, it should wrap
        Song next = engine.next();

        // Then we should be at the first song
        assertEquals(s1, next, "Repeat ALL should wrap to first song after last");
        assertEquals(0, engine.getCurrentIndex(), "Index should wrap to 0 in Repeat ALL mode");

        // History should record the previously current song (s3) at the front
        assertFalse(engine.getHistory().isEmpty(), "History should contain previous song");
        assertEquals(s3, engine.getHistory().get(0), "Most recent history entry should be the previously current song");
    }
}
