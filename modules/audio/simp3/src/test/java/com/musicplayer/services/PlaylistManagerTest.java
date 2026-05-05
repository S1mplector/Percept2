package com.musicplayer.services;

import com.musicplayer.data.models.Playlist;
import com.musicplayer.data.models.Song;
import com.musicplayer.data.repositories.InMemoryPlaylistRepository;
import com.musicplayer.data.repositories.PlaylistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PlaylistManagerTest {

    private PlaylistRepository repository;
    private PlaylistManager manager;

    @BeforeEach
    void setUp() {
        repository = new InMemoryPlaylistRepository();
        manager = new PlaylistManager(repository);
    }

    @Test
    void createPlaylist_success_and_validation() {
        Playlist p = manager.createPlaylist("Chill");
        assertNotNull(p);
        assertEquals("Chill", p.getName());
        assertTrue(p.getId() > 0);
        assertEquals(1, manager.getPlaylistCount());

        // empty name
        assertThrows(IllegalArgumentException.class, () -> manager.createPlaylist(" "));
        // duplicate name
        assertThrows(IllegalArgumentException.class, () -> manager.createPlaylist("Chill"));
    }

    @Test
    void renamePlaylist_behaviour() {
        Playlist p = manager.createPlaylist("Old");
        long id = p.getId();

        // invalid inputs
        assertFalse(manager.renamePlaylist(id, " "));
        assertFalse(manager.renamePlaylist(9999, "New"));

        // rename success
        assertTrue(manager.renamePlaylist(id, "NewName"));
        assertEquals("NewName", manager.getPlaylistById(id).getName());

        // conflict with existing name
        manager.createPlaylist("Taken");
        assertFalse(manager.renamePlaylist(id, "Taken"));
    }

    @Test
    void deletePlaylist_behaviour() {
        Playlist p1 = manager.createPlaylist("P1");
        Playlist p2 = manager.createPlaylist("P2");
        assertEquals(2, manager.getPlaylistCount());

        assertTrue(manager.deletePlaylist(p1.getId()));
        assertEquals(1, manager.getPlaylistCount());

        // deleting non-existing
        assertFalse(manager.deletePlaylist(9999));
        assertEquals(1, manager.getPlaylistCount());

        assertTrue(manager.deletePlaylist(p2.getId()));
        assertEquals(0, manager.getPlaylistCount());
    }

    @Test
    void add_and_remove_song_in_playlist() {
        Playlist p = manager.createPlaylist("S");
        long id = p.getId();

        Song s1 = createSong(1, "Song 1");
        Song s1Duplicate = createSong(1, "Song 1"); // equals() true with same fields
        Song s2 = createSong(2, "Song 2");

        assertTrue(manager.addSongToPlaylist(id, s1));
        assertFalse(manager.addSongToPlaylist(id, s1Duplicate)); // prevent duplicates
        assertTrue(manager.addSongToPlaylist(id, s2));

        Playlist updated = manager.getPlaylistById(id);
        assertEquals(2, updated.getSongs().size());
        assertTrue(updated.getSongs().contains(s1));
        assertTrue(updated.getSongs().contains(s2));

        assertTrue(manager.removeSongFromPlaylist(id, s1));
        assertFalse(manager.removeSongFromPlaylist(id, s1)); // already removed
        assertEquals(1, manager.getPlaylistById(id).getSongs().size());
    }

    @Test
    void get_by_name_and_initialize() {
        manager.createPlaylist("Focus");
        manager.createPlaylist("Workout");

        assertNotNull(manager.getPlaylistByName("Focus"));
        assertNull(manager.getPlaylistByName("Missing"));

        // initializePlaylists should be a no-op without callback and not throw
        assertDoesNotThrow(manager::initializePlaylists);
    }

    @Test
    void updatePlaylistSongs_reorders_songs() {
        Playlist p = manager.createPlaylist("Order");
        long id = p.getId();

        Song a = createSong(1, "A");
        Song b = createSong(2, "B");
        Song c = createSong(3, "C");

        assertTrue(manager.addSongToPlaylist(id, a));
        assertTrue(manager.addSongToPlaylist(id, b));
        assertTrue(manager.addSongToPlaylist(id, c));
        assertEquals(Arrays.asList(a, b, c), manager.getPlaylistById(id).getSongs());

        List<Song> newOrder = Arrays.asList(c, a);
        manager.updatePlaylistSongs(id, newOrder);
        assertEquals(newOrder, manager.getPlaylistById(id).getSongs());
    }

    private static Song createSong(long id, String title) {
        Song s = new Song();
        s.setId(id);
        s.setTitle(title);
        s.setArtist("Artist");
        s.setAlbum("Album");
        s.setGenre("Genre");
        s.setDuration(120);
        s.setFilePath("/tmp/" + title + ".mp3");
        s.setTrackNumber(1);
        s.setYear(2020);
        return s;
    }
}
