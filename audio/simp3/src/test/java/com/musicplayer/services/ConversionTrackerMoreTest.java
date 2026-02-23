package com.musicplayer.services;

import com.musicplayer.data.models.Song;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ConversionTrackerMoreTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void redirectWorkingDir() {
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
    void removeConversionRecord_and_clearHistory_behaviour() throws IOException {
        ConversionTracker tracker = new ConversionTracker();

        Path dir = tempDir.resolve("Artist - Album");
        Path in = dir.resolve("t.wav");
        Path out = dir.resolve("t.mp3");
        Song s = song("Artist", "Album", "t", in);
        Files.writeString(out, "out");

        // record via Song API
        tracker.recordConversion(s, out.toFile(), "mp3");
        assertTrue(tracker.isAlbumConverted(s));
        assertNotNull(tracker.getConversionRecord(s));

        // remove record
        tracker.removeConversionRecord(s);
        assertFalse(tracker.isAlbumConverted(s));
        assertNull(tracker.getConversionRecord(s));

        // record again and then clear all
        tracker.recordConversion(s, out.toFile(), "mp3");
        assertTrue(tracker.isAlbumConverted(s));
        tracker.clearHistory();
        assertFalse(tracker.isAlbumConverted(s));
        assertTrue(tracker.getAllConversionRecords().isEmpty());
    }

    @Test
    void recordConversion_with_files_and_getAll_returns_copy() throws IOException {
        ConversionTracker tracker = new ConversionTracker();

        Path dir = tempDir.resolve("Band - Record");
        Path wav = dir.resolve("x.wav");
        Path mp3 = dir.resolve("x.mp3");
        Files.createDirectories(dir);
        Files.writeString(wav, "w");
        Files.writeString(mp3, "m");

        tracker.recordConversion(wav.toFile(), mp3.toFile(), "wav", "mp3");

        // maps should contain the album key
        Map<String, ConversionTracker.ConversionRecord> copy = tracker.getAllConversionRecords();
        assertEquals(1, copy.size());

        // mutate copy shouldn't affect tracker
        copy.clear();
        assertEquals(1, tracker.getAllConversionRecords().size());

        // stale cleanup when converted file removed for File-based check
        Files.deleteIfExists(mp3);
        assertFalse(tracker.isAlbumConverted(wav.toFile()));
    }
}
