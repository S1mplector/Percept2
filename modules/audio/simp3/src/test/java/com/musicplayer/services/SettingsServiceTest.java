package com.musicplayer.services;

import com.musicplayer.data.models.Settings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class SettingsServiceTest {

    @TempDir
    Path tempDir;
    
    private String originalDataDir;

    @BeforeEach
    void setDataDir() {
        // Save original system property
        originalDataDir = System.getProperty("simp3.data.dir");
        // Point SettingsService to an isolated data directory inside the temp dir
        System.setProperty("simp3.data.dir", tempDir.resolve("data").toString());
    }
    
    @AfterEach
    void restoreDataDir() {
        // Restore original system property
        if (originalDataDir != null) {
            System.setProperty("simp3.data.dir", originalDataDir);
        } else {
            System.clearProperty("simp3.data.dir");
        }
    }

    @Test
    void loads_defaults_and_persists_on_first_run() throws IOException {
        Path dataDir = tempDir.resolve("data");
        Path settingsFile = dataDir.resolve("settings.json");
        assertFalse(Files.exists(settingsFile));

        SettingsService svc = new SettingsService();
        // On first run, service saves defaults (with solid color) then normalizes,
        // inferring SOLID_COLOR when only visualizerSolidColor is present in file
        Settings s = svc.getSettings();
        assertTrue(s.isVisualizerEnabled());
        assertEquals(Settings.VisualizerColorMode.SOLID_COLOR, s.getVisualizerColorMode());

        // constructor should have saved defaults
        assertTrue(Files.exists(settingsFile), "settings.json should be created on first load");

        // Modify and persist
        svc.setVisualizerEnabled(false);
        svc.setVisualizerColorMode(Settings.VisualizerColorMode.SOLID_COLOR);
        svc.setVisualizerSolidColor("#FF0000");
        assertTrue(Files.size(settingsFile) > 0);

        // New instance should load what was saved from file (this is the key behavior we want)
        SettingsService svc2 = new SettingsService();
        Settings loaded = svc2.getSettings();
        assertFalse(loaded.isVisualizerEnabled());
        assertEquals(Settings.VisualizerColorMode.SOLID_COLOR, loaded.getVisualizerColorMode());
        assertEquals("#FF0000", loaded.getVisualizerSolidColor());
    }

    @Test
    void mini_player_position_and_pin_are_persisted() {
        SettingsService svc = new SettingsService();
        svc.setMiniPlayerPosition(123.45, 67.89);
        svc.setMiniPlayerPinned(false);

        SettingsService reload = new SettingsService();
        assertEquals(123.45, reload.getMiniPlayerX());
        assertEquals(67.89, reload.getMiniPlayerY());
        assertFalse(reload.isMiniPlayerPinned());
    }
}
