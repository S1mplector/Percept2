package com.musicplayer.core.audio;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.musicplayer.data.models.Song;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.scene.media.AudioSpectrumListener;

/**
 * Hybrid audio engine that intelligently delegates to either JavaFXAudioEngine
 * or JavaZoomAudioEngine based on the audio format.
 * 
 * This engine provides seamless switching between engines while maintaining
 * consistent state and properly managing resources.
 */
public class HybridAudioEngine implements AudioEngine {
    
    private static final Logger LOGGER = Logger.getLogger(HybridAudioEngine.class.getName());
    
    // Supported formats for each engine
    private static final Set<String> JAVAFX_FORMATS = new HashSet<>(Arrays.asList(
        "mp3", "m4a", "mp4", "wav", "aiff"
    ));
    
    private static final Set<String> JAVAZOOM_FORMATS = new HashSet<>(Arrays.asList(
        "flac", "ogg", "opus", "wma"
    ));
    
    // Audio engines
    private final JavaFXAudioEngine javaFXEngine;
    private final JavaZoomAudioEngine javaZoomEngine;
    
    // Currently active engine
    private AudioEngine activeEngine;
    
    // Callbacks that need to be propagated to the active engine
    private Runnable onSongEndedCallback;
    private Runnable onErrorCallback;
    private AudioSpectrumListener spectrumListener;
    
    // Current volume to maintain across engine switches
    private double currentVolume = 0.5;
    
    public HybridAudioEngine() {
        this.javaFXEngine = new JavaFXAudioEngine();
        this.javaZoomEngine = new JavaZoomAudioEngine();
        
        // Set JavaFX as the default engine
        this.activeEngine = javaFXEngine;
        
        LOGGER.info("HybridAudioEngine initialized with JavaFX as default engine");
    }
    
    @Override
    public boolean loadSong(Song song) {
        if (song == null || song.getFilePath() == null) {
            LOGGER.warning("Cannot load null song or song with null file path");
            return false;
        }
        
        File audioFile = new File(song.getFilePath());
        if (!audioFile.exists()) {
            LOGGER.severe("Audio file not found: " + song.getFilePath());
            if (onErrorCallback != null) {
                onErrorCallback.run();
            }
            return false;
        }
        
        // Determine the appropriate engine based on file extension
        String fileExtension = getFileExtension(song.getFilePath()).toLowerCase();
        AudioEngine targetEngine = selectEngineForFormat(fileExtension);
        
        // Switch engines if necessary
        if (targetEngine != activeEngine) {
            switchEngine(targetEngine, fileExtension);
        }
        
        // Load the song with the active engine
        boolean loaded = activeEngine.loadSong(song);
        
        if (loaded) {
            LOGGER.info("Successfully loaded song with " + getEngineName(activeEngine) + 
                       " engine: " + song.getTitle() + " [" + fileExtension + "]");
        } else {
            LOGGER.severe("Failed to load song with " + getEngineName(activeEngine) + 
                         " engine: " + song.getTitle());
        }
        
        return loaded;
    }
    
    /**
     * Switches to a different audio engine, properly disposing of the previous one.
     */
    private void switchEngine(AudioEngine newEngine, String format) {
        LOGGER.info("Switching from " + getEngineName(activeEngine) + 
                   " to " + getEngineName(newEngine) + " for format: " + format);
        
        // Stop and dispose the current engine
        if (activeEngine != null) {
            if (activeEngine.isPlaying()) {
                activeEngine.stop();
            }
            activeEngine.dispose();
        }
        
        // Set the new active engine
        activeEngine = newEngine;
        
        // Restore callbacks and settings to the new engine
        if (onSongEndedCallback != null) {
            activeEngine.setOnSongEnded(onSongEndedCallback);
        }
        if (onErrorCallback != null) {
            activeEngine.setOnError(onErrorCallback);
        }
        if (spectrumListener != null) {
            activeEngine.setAudioSpectrumListener(spectrumListener);
        }
        
        // Restore volume
        activeEngine.setVolume(currentVolume);
        
        LOGGER.info("Engine switch completed. Active engine: " + getEngineName(activeEngine));
    }
    
    /**
     * Selects the appropriate engine based on the file format.
     * On Linux, prefer JavaZoom for MP3 due to JavaFX codec limitations.
     */
    private AudioEngine selectEngineForFormat(String format) {
        // On Linux, prefer JavaZoom for MP3 files due to JavaFX codec issues
        if ("mp3".equals(format) && System.getProperty("os.name").toLowerCase().contains("linux")) {
            LOGGER.info("Linux detected - using JavaZoom for MP3: " + format);
            return javaZoomEngine;
        }
        
        if (JAVAFX_FORMATS.contains(format)) {
            return javaFXEngine;
        } else if (JAVAZOOM_FORMATS.contains(format)) {
            return javaZoomEngine;
        } else {
            // Default to JavaFX for unknown formats
            LOGGER.warning("Unknown format: " + format + ". Defaulting to JavaFX engine.");
            return javaFXEngine;
        }
    }
    
    /**
     * Extracts the file extension from a file path.
     */
    private String getFileExtension(String filePath) {
        int lastDotIndex = filePath.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < filePath.length() - 1) {
            return filePath.substring(lastDotIndex + 1);
        }
        return "";
    }
    
    /**
     * Gets a human-readable name for the engine.
     */
    private String getEngineName(AudioEngine engine) {
        if (engine == javaFXEngine) {
            return "JavaFX";
        } else if (engine == javaZoomEngine) {
            return "JavaZoom";
        } else {
            return "Unknown";
        }
    }
    
    // Delegate all other methods to the active engine
    
    @Override
    public void play() {
        if (activeEngine != null) {
            activeEngine.play();
        } else {
            LOGGER.warning("No active engine available for play()");
        }
    }
    
    @Override
    public void pause() {
        if (activeEngine != null) {
            activeEngine.pause();
        }
    }
    
    @Override
    public void stop() {
        if (activeEngine != null) {
            activeEngine.stop();
        }
    }
    
    @Override
    public void seek(double seconds) {
        if (activeEngine != null) {
            activeEngine.seek(seconds);
        }
    }
    
    @Override
    public void setVolume(double volume) {
        this.currentVolume = volume;
        if (activeEngine != null) {
            activeEngine.setVolume(volume);
        }
    }
    
    @Override
    public double getVolume() {
        if (activeEngine != null) {
            return activeEngine.getVolume();
        }
        return currentVolume;
    }
    
    @Override
    public double getCurrentTime() {
        if (activeEngine != null) {
            return activeEngine.getCurrentTime();
        }
        return 0.0;
    }
    
    @Override
    public double getTotalTime() {
        if (activeEngine != null) {
            return activeEngine.getTotalTime();
        }
        return 0.0;
    }
    
    @Override
    public boolean isPlaying() {
        if (activeEngine != null) {
            return activeEngine.isPlaying();
        }
        return false;
    }
    
    @Override
    public void dispose() {
        LOGGER.info("Disposing HybridAudioEngine");
        
        // Dispose both engines
        if (javaFXEngine != null) {
            javaFXEngine.dispose();
        }
        if (javaZoomEngine != null) {
            javaZoomEngine.dispose();
        }
        
        activeEngine = null;
        onSongEndedCallback = null;
        onErrorCallback = null;
        spectrumListener = null;
        
        LOGGER.info("HybridAudioEngine disposed");
    }
    
    @Override
    public BooleanProperty playingProperty() {
        if (activeEngine != null) {
            return activeEngine.playingProperty();
        }
        // Return a property from one of the engines as fallback
        return javaFXEngine.playingProperty();
    }
    
    @Override
    public DoubleProperty currentTimeProperty() {
        if (activeEngine != null) {
            return activeEngine.currentTimeProperty();
        }
        return javaFXEngine.currentTimeProperty();
    }
    
    @Override
    public DoubleProperty totalTimeProperty() {
        if (activeEngine != null) {
            return activeEngine.totalTimeProperty();
        }
        return javaFXEngine.totalTimeProperty();
    }
    
    @Override
    public DoubleProperty volumeProperty() {
        if (activeEngine != null) {
            return activeEngine.volumeProperty();
        }
        return javaFXEngine.volumeProperty();
    }
    
    @Override
    public ObjectProperty<Song> currentSongProperty() {
        if (activeEngine != null) {
            return activeEngine.currentSongProperty();
        }
        return javaFXEngine.currentSongProperty();
    }
    
    @Override
    public void setOnSongEnded(Runnable callback) {
        this.onSongEndedCallback = callback;
        if (activeEngine != null) {
            activeEngine.setOnSongEnded(callback);
        }
    }
    
    @Override
    public void setOnError(Runnable callback) {
        this.onErrorCallback = callback;
        if (activeEngine != null) {
            activeEngine.setOnError(callback);
        }
    }
    
    @Override
    public void setAudioSpectrumListener(AudioSpectrumListener listener) {
        this.spectrumListener = listener;
        if (activeEngine != null) {
            activeEngine.setAudioSpectrumListener(listener);
        }
    }
    
    @Override
    public AudioSpectrumListener getAudioSpectrumListener() {
        return this.spectrumListener;
    }
}