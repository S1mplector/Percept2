package com.musicplayer.services;

import com.musicplayer.core.library.LibraryEngine;
import com.musicplayer.data.models.Album;
import com.musicplayer.data.models.Artist;
import com.musicplayer.data.models.Song;
import com.musicplayer.data.repositories.InMemorySongRepository;
import com.musicplayer.data.repositories.SongRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class LibraryServiceTest {

    private SongRepository repo;
    private LibraryService svc;

    @BeforeEach
    void setup() {
        repo = new InMemorySongRepository();
        svc = new LibraryService(repo);
    }

    private static Song song(long id, String title, String artist, String album, String genre) {
        Song s = new Song();
        s.setId(id);
        s.setTitle(title);
        s.setArtist(artist);
        s.setAlbum(album);
        s.setGenre(genre);
        return s;
    }

    @Test
    void add_and_get_songs_reflect_in_engine_and_repo() {
        Song a = song(1, "One", "ArtistA", "Alb1", "Rock");
        Song b = song(2, "Two", "ArtistB", "Alb2", "Pop");

        svc.addSong(a);
        svc.addSong(b);

        // Engine view
        List<Song> all = svc.getAllSongs();
        assertEquals(2, all.size());
        assertTrue(all.contains(a));
        assertTrue(all.contains(b));

        // Repo view
        assertNotNull(svc.getSongById(1));
        assertNotNull(svc.getSongById(2));
    }

    @Test
    void addSongs_bulk_and_statistics() {
        List<Song> batch = Arrays.asList(
                song(1, "One", "ArtistA", "Alb1", "Rock"),
                song(2, "Two", "ArtistA", "Alb1", "Rock"),
                song(3, "Three", "ArtistB", "Alb2", "Pop")
        );
        svc.addSongs(batch);

        assertEquals(3, svc.getSongCount());
        assertTrue(svc.getAlbumCount() >= 2);
        assertTrue(svc.getArtistCount() >= 2);

        // Genres
        Set<String> genres = svc.getAllGenres();
        assertTrue(genres.contains("Rock"));
        assertTrue(genres.contains("Pop"));
    }

    @Test
    void search_functions_and_filters() {
        svc.addSongs(Arrays.asList(
                song(1, "Hello World", "Adele", "25", "Pop"),
                song(2, "World on Fire", "Sarah", "Shine", "Pop"),
                song(3, "Thunderstruck", "ACDC", "The Razors Edge", "Rock")
        ));

        List<Song> byTitle = svc.searchSongsByTitle("world");
        assertEquals(2, byTitle.size());

        List<Song> byArtist = svc.searchSongsByArtist("acdc");
        assertEquals(1, byArtist.size());
        assertEquals("Thunderstruck", byArtist.get(0).getTitle());

        List<Song> byAlbum = svc.searchSongsByAlbum("25");
        assertEquals(1, byAlbum.size());
        assertEquals("Hello World", byAlbum.get(0).getTitle());

        List<Song> byGenre = svc.getSongsByGenre("Pop");
        assertEquals(2, byGenre.size());
    }

    @Test
    void remove_song_by_id_and_instance() {
        Song a = song(1, "One", "ArtistA", "Alb1", "Rock");
        Song b = song(2, "Two", "ArtistB", "Alb2", "Pop");
        svc.addSongs(Arrays.asList(a, b));
        assertEquals(2, svc.getSongCount());

        // Remove by id
        svc.removeSong(1);
        assertEquals(1, svc.getSongCount());
        assertNull(svc.getSongById(1));

        // Remove by instance
        svc.removeSong(b);
        assertEquals(0, svc.getSongCount());
    }

    @Test
    void refreshLibrary_rebuilds_from_repo() {
        svc.addSongs(Arrays.asList(
                song(1, "One", "A", "Alb1", "Pop"),
                song(2, "Two", "A", "Alb1", "Pop")
        ));
        assertEquals(2, svc.getSongCount());

        // Clear engine only
        svc.clearLibrary();
        assertEquals(0, svc.getSongCount());

        // Refresh should repopulate from repo
        svc.refreshLibrary();
        assertEquals(2, svc.getSongCount());
    }
}
