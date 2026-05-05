package com.musicplayer.services;

import java.io.File;
import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.musicplayer.data.models.Settings;

/**
 * Service for managing application settings.
 */
public class SettingsService {
    
    /**
     * System property to override data directory location for settings persistence.
     * Useful for tests to isolate filesystem effects.
     */
    private static final String DATA_DIR_PROP = "simp3.data.dir";

    /** Settings file resolved at runtime (default: data/settings.json) */
    private final File settingsFile;
    private final ObjectMapper objectMapper;
    private Settings settings;
    
    public SettingsService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        // Register JavaTimeModule to handle LocalDateTime serialization
        this.objectMapper.registerModule(new JavaTimeModule());
        // Disable writing dates as timestamps
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // Be lenient when reading enums so we don't drop the whole file on unknown/empty values
        this.objectMapper.configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true);
        this.objectMapper.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);

        // Resolve data directory from system property or fallback to default "data"
        String dataDirPath = System.getProperty(DATA_DIR_PROP, "data");
        this.settingsFile = new File(dataDirPath, "settings.json");
        loadSettings();
    }
    
    /**
     * Load settings from file or create default settings.
     */
    private void loadSettings() {
        if (settingsFile.exists()) {
            try {
                settings = objectMapper.readValue(settingsFile, Settings.class);
                System.out.println("Loaded settings from file");
            } catch (IOException e) {
                System.err.println("Failed to load settings: " + e.getMessage());
                settings = new Settings(); // Use defaults
            }
        } else {
            settings = new Settings(); // Use defaults
            saveSettings(); // Save defaults to file
        }
        
        // Always normalize visualizer color mode after loading (handles both new Settings and loaded ones)
        normalizeVisualizerColorMode();
    }
    
    /**
     * Normalize visualizer color mode if missing/invalid.
     * This ensures the setting is always properly initialized from file data or sensible defaults.
     */
    private void normalizeVisualizerColorMode() {
        if (settings.getVisualizerColorMode() == null) {
            boolean needsSave = false;
            
            // Try to read the raw value from file if it exists
            if (settingsFile.exists()) {
                try {
                    JsonNode root = objectMapper.readTree(settingsFile);
                    JsonNode modeNode = root.get("visualizerColorMode");
                    JsonNode solidNode = root.get("visualizerSolidColor");
                    
                    if (modeNode != null && modeNode.isTextual()) {
                        String raw = modeNode.asText();
                        if (raw != null && !raw.isBlank()) {
                            try {
                                settings.setVisualizerColorMode(Settings.VisualizerColorMode.valueOf(raw));
                            } catch (IllegalArgumentException iae) {
                                // Invalid enum value, fall through to inference logic
                            }
                        }
                    }
                    
                    // If still null, infer from presence of a solid color value
                    if (settings.getVisualizerColorMode() == null) {
                        if (solidNode != null && solidNode.isTextual() && !solidNode.asText("").isBlank()) {
                            settings.setVisualizerColorMode(Settings.VisualizerColorMode.SOLID_COLOR);
                        } else {
                            settings.setVisualizerColorMode(Settings.VisualizerColorMode.GRADIENT_CYCLING);
                        }
                        needsSave = true;
                    }
                } catch (IOException ioe) {
                    // File read failed, apply sensible default
                    settings.setVisualizerColorMode(Settings.VisualizerColorMode.GRADIENT_CYCLING);
                    needsSave = true;
                }
            } else {
                // No file exists, use default
                settings.setVisualizerColorMode(Settings.VisualizerColorMode.GRADIENT_CYCLING);
                needsSave = true;
            }
            
            // Persist normalization if we made changes
            if (needsSave) {
                saveSettings();
            }
        }
    }
    
    /**
     * Save current settings to file.
     */
    public void saveSettings() {
        File dataDir = settingsFile.getParentFile();
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        
        try {
            objectMapper.writeValue(settingsFile, settings);
            System.out.println("Settings saved to file");
        } catch (IOException e) {
            System.err.println("Failed to save settings: " + e.getMessage());
        }
    }
    
    /**
     * Get the current settings.
     * @return Current settings
     */
    public Settings getSettings() {
        return settings;
    }
    
    /**
     * Update visualizer enabled state.
     * @param enabled Whether visualizer should be enabled
     */
    public void setVisualizerEnabled(boolean enabled) {
        settings.setVisualizerEnabled(enabled);
        saveSettings();
    }
    
    /**
     * Update visualizer color mode.
     * @param colorMode The color mode to use
     */
    public void setVisualizerColorMode(Settings.VisualizerColorMode colorMode) {
        settings.setVisualizerColorMode(colorMode);
        saveSettings();
    }
    
    /**
     * Update visualizer solid color.
     * @param color The color in hex format (e.g., "#FF0000")
     */
    public void setVisualizerSolidColor(String color) {
        settings.setVisualizerSolidColor(color);
        saveSettings();
    }
    
    /**
     * Update mini player position.
     * @param x The X coordinate
     * @param y The Y coordinate
     */
    public void setMiniPlayerPosition(double x, double y) {
        settings.setMiniPlayerX(x);
        settings.setMiniPlayerY(y);
        saveSettings();
    }
    
    /**
     * Get mini player X position.
     * @return X coordinate or -1 if not set
     */
    public double getMiniPlayerX() {
        return settings.getMiniPlayerX();
    }
    
    /**
     * Get mini player Y position.
     * @return Y coordinate or -1 if not set
     */
    public double getMiniPlayerY() {
        return settings.getMiniPlayerY();
    }
    
    /**
     * Update mini player pinned state.
     * @param pinned Whether the mini player should be always on top
     */
    public void setMiniPlayerPinned(boolean pinned) {
        settings.setMiniPlayerPinned(pinned);
        saveSettings();
    }
    
    /**
     * Get mini player pinned state.
     * @return true if mini player is pinned (always on top)
     */
    public boolean isMiniPlayerPinned() {
        return settings.isMiniPlayerPinned();
    }

    // Resume playback settings
    public boolean isResumeOnStartup() {
        return settings.isResumeOnStartup();
    }

    public void setResumeOnStartup(boolean resume) {
        settings.setResumeOnStartup(resume);
        saveSettings();
    }

    public long getLastSongId() {
        return settings.getLastSongId();
    }

    public void setLastSongId(long songId) {
        settings.setLastSongId(songId);
        saveSettings();
    }

    public double getLastPositionSeconds() {
        return settings.getLastPositionSeconds();
    }

    public void setLastPositionSeconds(double seconds) {
        settings.setLastPositionSeconds(seconds);
        saveSettings();
    }

    public double getLastVolume() {
        return settings.getLastVolume();
    }

    public void setLastVolume(double volume) {
        settings.setLastVolume(volume);
        saveSettings();
    }
}