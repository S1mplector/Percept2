package com.musicplayer.services;

import com.musicplayer.data.models.Playlist;
import com.musicplayer.data.models.Song;
import com.musicplayer.data.repositories.InMemoryPlaylistRepository;
import com.musicplayer.data.repositories.PlaylistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PlaylistServiceTest {

    private PlaylistRepository repo;
    private PlaylistService svc;

    @BeforeEach
    void setup() {
        repo = new InMemoryPlaylistRepository();
        svc = new PlaylistService(repo);
    }

    private static Playlist playlist(String name) {
        Playlist p = new Playlist();
        p.setName(name);
        return p;
    }

    private static Song song(long id, String title) {
        Song s = new Song();
        s.setId(id);
        s.setTitle(title);
        s.setArtist("Artist");
        s.setAlbum("Album");
        return s;
    }

    @Test
    void create_and_list_playlists() {
        Playlist p1 = playlist("P1");
        Playlist p2 = playlist("P2");

        svc.createPlaylist(p1);
        svc.createPlaylist(p2);

        List<Playlist> all = svc.getAllPlaylists();
        assertEquals(2, all.size());
        assertNotEquals(0, all.get(0).getId());
        assertNotEquals(0, all.get(1).getId());
    }

    @Test
    void add_and_remove_song_in_playlist() {
        Playlist p = playlist("My");
        svc.createPlaylist(p);
        long pid = repo.findAll().get(0).getId();

        Song a = song(1, "A");
        Song b = song(2, "B");

        svc.addSongToPlaylist(pid, a);
        svc.addSongToPlaylist(pid, b);
        Playlist loaded = repo.findById(pid);
        assertEquals(2, loaded.getSongs().size());
        assertTrue(loaded.getSongs().contains(a));
        assertTrue(loaded.getSongs().contains(b));

        svc.removeSongFromPlaylist(pid, a);
        Playlist after = repo.findById(pid);
        assertEquals(1, after.getSongs().size());
        assertFalse(after.getSongs().contains(a));
        assertTrue(after.getSongs().contains(b));
    }
}
