package com.musicplayer.services;

import com.musicplayer.data.models.Song;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class FavoritesServiceTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setDataDir() {
        // Point FavoritesService to an isolated data directory inside the temp dir
        System.setProperty("simp3.data.dir", tempDir.resolve("data").toString());
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
    void toggling_favorite_persists_and_updates_flag() throws IOException {
        FavoritesService svc = new FavoritesService();

        Song s1 = song(1, "One");
        assertFalse(s1.isFavorite());
        assertFalse(svc.isFavorite(1));

        // Toggle to favorite
        assertTrue(svc.toggleFavorite(s1));
        assertTrue(s1.isFavorite());
        assertTrue(svc.isFavorite(1));

        // Verify persistence file exists and is non-empty
        Path favoritesFile = tempDir.resolve("data").resolve("favorites.json");
        assertTrue(Files.exists(favoritesFile));
        assertTrue(Files.size(favoritesFile) > 0);

        // Toggle back to not-favorite
        assertFalse(svc.toggleFavorite(s1));
        assertFalse(s1.isFavorite());
        assertFalse(svc.isFavorite(1));
    }

    @Test
    void add_remove_and_getFavorites_behaviour() {
        FavoritesService svc = new FavoritesService();
        Song s1 = song(1, "One");
        Song s2 = song(2, "Two");
        Song s3 = song(3, "Three");

        svc.addFavorite(s1);
        svc.addFavorite(s2);
        assertEquals(2, svc.getFavoriteCount());
        assertTrue(s1.isFavorite());
        assertTrue(s2.isFavorite());
        assertFalse(s3.isFavorite());

        List<Song> favs = svc.getFavoriteSongs(Arrays.asList(s1, s2, s3));
        assertEquals(2, favs.size());
        assertTrue(favs.containsAll(Arrays.asList(s1, s2)));

        svc.removeFavorite(s1);
        assertEquals(1, svc.getFavoriteCount());
        assertFalse(s1.isFavorite());
        assertTrue(svc.isFavorite(2));
    }

    @Test
    void updateFavoriteStatus_bulk_update() {
        FavoritesService svc = new FavoritesService();
        Song s1 = song(1, "One");
        Song s2 = song(2, "Two");
        Song s3 = song(3, "Three");

        // Only 1 and 3 are favorites
        svc.addFavorite(s1);
        svc.addFavorite(s3);

        // Reset flags then update from service state
        s1.setFavorite(false);
        s2.setFavorite(false);
        s3.setFavorite(false);

        svc.updateFavoriteStatus(Arrays.asList(s1, s2, s3));
        assertTrue(s1.isFavorite());
        assertFalse(s2.isFavorite());
        assertTrue(s3.isFavorite());
    }

    @Test
    void clearFavorites_clears_and_persists() throws IOException {
        FavoritesService svc = new FavoritesService();
        Song s1 = song(1, "One");
        Song s2 = song(2, "Two");
        svc.addFavorite(s1);
        svc.addFavorite(s2);
        assertEquals(2, svc.getFavoriteCount());

        // Ensure file exists before clearing
        Path favoritesFile = tempDir.resolve("data").resolve("favorites.json");
        assertTrue(Files.exists(favoritesFile));

        svc.clearFavorites();
        assertEquals(0, svc.getFavoriteCount());

        // File should still exist and be writable (force save does not change state)
        svc.forceSave();
        assertTrue(Files.exists(favoritesFile));
        assertTrue(Files.size(favoritesFile) >= 2); // should at least be an empty JSON array/object representation
    }

    @Test
    void persistence_across_instances() {
        FavoritesService svc1 = new FavoritesService();
        Song s1 = song(1, "One");
        Song s2 = song(2, "Two");
        svc1.addFavorite(s1);
        svc1.addFavorite(s2);
        assertEquals(2, svc1.getFavoriteCount());

        // New instance should load the same favorites
        FavoritesService svc2 = new FavoritesService();
        assertTrue(svc2.isFavorite(1));
        assertTrue(svc2.isFavorite(2));
        assertEquals(2, svc2.getFavoriteCount());
    }
}
