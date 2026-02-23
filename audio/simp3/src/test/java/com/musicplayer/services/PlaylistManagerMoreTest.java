package com.musicplayer.services;

import com.musicplayer.data.models.Playlist;
import com.musicplayer.data.models.Song;
import com.musicplayer.data.repositories.InMemoryPlaylistRepository;
import com.musicplayer.data.repositories.PlaylistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PlaylistManagerMoreTest {

    private PlaylistRepository repository;
    private PlaylistManager manager;

    @BeforeEach
    void setup() {
        repository = new InMemoryPlaylistRepository();
        manager = new PlaylistManager(repository);
    }

    @Test
    void hasExistingPlaylists_and_clearAllPlaylists() {
        assertFalse(manager.hasExistingPlaylists());
        manager.createPlaylist("One");
        manager.createPlaylist("Two");
        assertTrue(manager.hasExistingPlaylists());
        assertEquals(2, manager.getPlaylistCount());

        manager.clearAllPlaylists();
        assertEquals(0, manager.getPlaylistCount());
        assertFalse(manager.hasExistingPlaylists());
    }

    @Test
    void forceSave_noop_for_non_persistent_repo() {
        // Should not throw even though repository is not PersistentPlaylistRepository
        assertDoesNotThrow(manager::forceSave);
    }

    @Test
    void add_remove_song_null_safety() {
        Playlist p = manager.createPlaylist("S");
        long id = p.getId();

        assertFalse(manager.addSongToPlaylist(id, null));
        assertFalse(manager.removeSongFromPlaylist(id, null));
        assertFalse(manager.addSongToPlaylist(9999, new Song()));
        assertFalse(manager.removeSongFromPlaylist(9999, new Song()));
    }
}
