package com.jvn.editor.ui.actioneditor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PuppeteerAudioLibraryTest {

    @TempDir
    Path tempDir;

    @Test
    void scanFindsProjectAudioFiles() throws Exception {
        Path audioDir = tempDir.resolve("assets/audio");
        Files.createDirectories(audioDir.resolve("voice"));
        Files.writeString(audioDir.resolve("voice/hello.ogg"), "demo");
        Files.writeString(audioDir.resolve("theme.mp3"), "demo");
        Files.writeString(audioDir.resolve("readme.txt"), "ignore");

        List<PuppeteerAudioLibrary.AudioEntry> entries = PuppeteerAudioLibrary.scan(tempDir.toFile());

        assertEquals(2, entries.size());
        assertEquals("assets/audio/theme.mp3", entries.get(0).relativePath());
        assertEquals("assets/audio/voice/hello.ogg", entries.get(1).relativePath());
    }

    @Test
    void resolveUniqueImportTargetAppendsNumericSuffix() throws Exception {
        Path importDir = tempDir.resolve("assets/audio/puppeteer");
        Files.createDirectories(importDir);
        Files.createFile(importDir.resolve("cue.ogg"));
        Files.createFile(importDir.resolve("cue-2.ogg"));

        Path target = PuppeteerAudioLibrary.resolveUniqueImportTarget(importDir, "cue.ogg");

        assertEquals(importDir.resolve("cue-3.ogg"), target);
    }
}
