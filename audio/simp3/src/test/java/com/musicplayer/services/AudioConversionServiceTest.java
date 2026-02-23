package com.musicplayer.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class AudioConversionServiceTest {

    @Test
    void convertible_and_compatibility_checks() {
        AudioConversionService svc = new AudioConversionService();
        assertTrue(svc.isConvertible("mp3"));
        assertTrue(svc.isConvertible("FlAc"));
        assertFalse(svc.isConvertible("wav")); // wav is compatible, not convertible input
        assertFalse(svc.isConvertible("txt"));

        assertTrue(svc.isJavaFXCompatible("wav"));
        assertTrue(svc.isJavaFXCompatible("AIFF"));
        assertFalse(svc.isJavaFXCompatible("mp3"));
    }

    @Test
    void analyzeDirectory_counts_convertible_and_skips_converted_folder(@TempDir Path temp) throws IOException {
        // Structure:
        // temp/
        //   album1/
        //     track1.mp3
        //     track2.flac
        //     cover.jpg
        //   album1-converted/ (should be skipped)
        //     track1_converted.wav
        //   album2/
        //     song1.ogg
        //     readme.txt
        //   misc.wav (already compatible)
        Path album1 = Files.createDirectories(temp.resolve("album1"));
        Path album1Converted = Files.createDirectories(temp.resolve("album1-converted"));
        Path album2 = Files.createDirectories(temp.resolve("album2"));

        Files.createFile(album1.resolve("track1.mp3"));
        Files.createFile(album1.resolve("track2.flac"));
        Files.createFile(album1.resolve("cover.jpg"));
        Files.createFile(album1Converted.resolve("track1_converted.wav"));
        Files.createFile(album2.resolve("song1.ogg"));
        Files.createFile(album2.resolve("readme.txt"));
        Files.createFile(temp.resolve("misc.wav"));

        AudioConversionService svc = new AudioConversionService();
        AudioConversionService.ConversionAnalysis analysis = svc.analyzeDirectory(temp.toFile());

        // totalAudioFiles counts only files with convertible extensions discovered by scanDirectoryForAudio
        // In our setup: mp3, flac, ogg are convertible and counted as audio files. wav is compatible and not added in scan.
        assertEquals(3, analysis.getTotalAudioFiles());
        assertEquals(3, analysis.getConvertibleFiles());
        assertEquals(3, analysis.getFilesToConvert().size());

        // Ensure -converted folder was ignored in scan
        for (File f : analysis.getFilesToConvert()) {
            assertFalse(f.getParentFile().getName().endsWith("-converted"));
        }

        // Directory counts should include album1 and album2 only
        assertTrue(analysis.getDirectoryCounts().keySet().stream().anyMatch(p -> p.endsWith("album1")));
        assertTrue(analysis.getDirectoryCounts().keySet().stream().anyMatch(p -> p.endsWith("album2")));
        assertFalse(analysis.getDirectoryCounts().keySet().stream().anyMatch(p -> p.endsWith("album1-converted")));
    }
}
