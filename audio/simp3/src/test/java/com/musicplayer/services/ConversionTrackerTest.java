package com.musicplayer.services;

import com.musicplayer.data.models.Song;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ConversionTrackerTest {

    @TempDir
    Path tempDir;

    private Path originalUserDir;

    @BeforeEach
    void setUp() {
        // Redirect working directory so ConversionTracker writes conversion-history.json under tempDir
        originalUserDir = Path.of(System.getProperty("user.dir"));
        System.setProperty("user.dir", tempDir.toString());
    }

    private static Song song(String artist, String album, String title, Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, "dummy");
        Song s = new Song();
        s.setArtist(artist);
        s.setAlbum(album);
        s.setTitle(title);
        s.setFilePath(file.toAbsolutePath().toString());
        return s;
    }

    @Test
    void record_and_query_by_song_and_file_with_stale_cleanup() throws IOException {
        ConversionTracker tracker = new ConversionTracker();

        Path albumDir = tempDir.resolve("Artist - Album");
        Path originalFile = albumDir.resolve("track1.wav");
        Song s = song("Artist", "Album", "Track 1", originalFile);

        assertFalse(tracker.isAlbumConverted(s));
        assertFalse(tracker.isAlbumConverted(originalFile.toFile()));

        // Record conversion (by Song API)
        Path converted = albumDir.resolve("track1.mp3");
        Files.writeString(converted, "converted");
        tracker.recordConversion(s, converted.toFile(), "mp3");

        assertTrue(tracker.isAlbumConverted(s));
        assertTrue(tracker.isAlbumConverted(originalFile.toFile()));

        // Delete converted file to simulate stale record and verify auto-cleanup on file check
        Files.deleteIfExists(converted);
        assertFalse(tracker.isAlbumConverted(originalFile.toFile()));
        // After cleanup, song-based query should also be false
        assertFalse(tracker.isAlbumConverted(s));
    }

    @Test
    void filter_helpers_exclude_converted_items() throws IOException {
        ConversionTracker tracker = new ConversionTracker();

        Path aDir = tempDir.resolve("A - First");
        Path bDir = tempDir.resolve("B - Second");
        Path aFile = aDir.resolve("a.flac");
        Path bFile = bDir.resolve("b.flac");
        Song aSong = song("A", "First", "a", aFile);
        Song bSong = song("B", "Second", "b", bFile);

        // Convert album A
        Path aOut = aDir.resolve("a.mp3");
        Files.writeString(aOut, "out");
        tracker.recordConversion(aSong, aOut.toFile(), "mp3");

        // Files API
        List<File> filteredFiles = tracker.filterUnconvertedFiles(Arrays.asList(aFile.toFile(), bFile.toFile()));
        assertEquals(1, filteredFiles.size());
        assertEquals(bFile.toFile(), filteredFiles.get(0));

        // Songs API
        List<com.musicplayer.data.models.Song> filteredSongs = tracker.filterUnconvertedSongs(Arrays.asList(aSong, bSong));
        assertEquals(1, filteredSongs.size());
        assertEquals(bSong, filteredSongs.get(0));
    }
}
