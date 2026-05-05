package com.musicplayer.core.audio;

import java.io.File;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.musicplayer.data.models.Song;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.media.AudioSpectrumListener;
import javazoom.jlgui.basicplayer.BasicController;
import javazoom.jlgui.basicplayer.BasicPlayer;
import javazoom.jlgui.basicplayer.BasicPlayerEvent;
import javazoom.jlgui.basicplayer.BasicPlayerException;
import javazoom.jlgui.basicplayer.BasicPlayerListener;

/**
 * JavaZoom BasicPlayer-based implementation of the AudioEngine interface.
 * Supports MP3, OGG, FLAC, and other formats through SPI plugins.
 * Provides JavaFX property bindings for UI integration.
 */
public class JavaZoomAudioEngine implements AudioEngine, BasicPlayerListener {
    
    private static final Logger LOGGER = Logger.getLogger(JavaZoomAudioEngine.class.getName());
    
    private BasicPlayer player;
    private Song currentSong;
    
    // Observable properties for JavaFX binding
    private final BooleanProperty playing = new SimpleBooleanProperty(false);
    private final DoubleProperty currentTime = new SimpleDoubleProperty(0.0);
    private final DoubleProperty totalTime = new SimpleDoubleProperty(0.0);
    private final DoubleProperty volume = new SimpleDoubleProperty(0.5);
    private final ObjectProperty<Song> currentSongProperty = new SimpleObjectProperty<>();
    
    // Callbacks
    private Runnable onSongEndedCallback;
    private Runnable onErrorCallback;
    
    // State tracking
    private long audioDataLength = 0;
    private long currentBytes = 0;
    private double currentVolume = 0.5;
    private boolean isSeeking = false;
    
    // Audio spectrum listener (not directly supported by BasicPlayer)
    private AudioSpectrumListener spectrumListener;
    
    public JavaZoomAudioEngine() {
        initializePlayer();
        
        // Initialize volume listener
        volume.addListener((obs, oldVal, newVal) -> {
            if (player != null) {
                try {
                    // BasicPlayer uses gain in range -80.0 to 6.0206 dB
                    // Convert 0.0-1.0 to gain value
                    double gain = convertVolumeToGain(newVal.doubleValue());
                    player.setGain(gain);
                    currentVolume = newVal.doubleValue();
                } catch (BasicPlayerException e) {
                    LOGGER.log(Level.WARNING, "Failed to set volume", e);
                }
            }
        });
    }
    
    private void initializePlayer() {
        player = new BasicPlayer();
        player.addBasicPlayerListener(this);
        LOGGER.info("JavaZoom BasicPlayer initialized");
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
                Platform.runLater(onErrorCallback);
            }
            return false;
        }
        
        try {
            // Stop current playback if any
            if (player.getStatus() != BasicPlayer.STOPPED) {
                player.stop();
            }
            
            // Open the new audio file
            player.open(audioFile);
            
            this.currentSong = song;
            Platform.runLater(() -> currentSongProperty.set(song));
            
            // Reset time properties
            Platform.runLater(() -> {
                currentTime.set(0.0);
                totalTime.set(0.0);
            });
            
            LOGGER.info("Loaded song: " + song.getTitle() + " [" + song.getFilePath() + "]");
            return true;
            
        } catch (BasicPlayerException e) {
            LOGGER.log(Level.SEVERE, "Error loading audio file: " + song.getFilePath(), e);
            if (onErrorCallback != null) {
                Platform.runLater(onErrorCallback);
            }
            return false;
        }
    }
    
    @Override
    public void play() {
        if (player != null) {
            try {
                int status = player.getStatus();
                if (status == BasicPlayer.PAUSED) {
                    player.resume();
                } else if (status == BasicPlayer.STOPPED || status == BasicPlayer.OPENED) {
                    player.play();
                }
                LOGGER.fine("Play command executed");
            } catch (BasicPlayerException e) {
                LOGGER.log(Level.SEVERE, "Error during playback", e);
                if (onErrorCallback != null) {
                    Platform.runLater(onErrorCallback);
                }
            }
        } else {
            LOGGER.warning("No player instance available for playback");
        }
    }
    
    @Override
    public void pause() {
        if (player != null) {
            try {
                if (player.getStatus() == BasicPlayer.PLAYING) {
                    player.pause();
                    LOGGER.fine("Playback paused");
                }
            } catch (BasicPlayerException e) {
                LOGGER.log(Level.WARNING, "Error pausing playback", e);
            }
        }
    }
    
    @Override
    public void stop() {
        if (player != null) {
            try {
                player.stop();
                Platform.runLater(() -> {
                    currentTime.set(0.0);
                    playing.set(false);
                });
                LOGGER.fine("Playback stopped");
            } catch (BasicPlayerException e) {
                LOGGER.log(Level.WARNING, "Error stopping playback", e);
            }
        }
    }
    
    @Override
    public void seek(double seconds) {
        if (player != null && audioDataLength > 0 && seconds >= 0 && seconds <= getTotalTime()) {
            try {
                isSeeking = true;
                long bytesToSkip = (long) ((seconds / getTotalTime()) * audioDataLength);
                player.seek(bytesToSkip);
                Platform.runLater(() -> currentTime.set(seconds));
                LOGGER.fine("Seeking to: " + formatTime(seconds));
            } catch (BasicPlayerException e) {
                LOGGER.log(Level.WARNING, "Error seeking to position", e);
            } finally {
                isSeeking = false;
            }
        }
    }
    
    @Override
    public void setVolume(double volume) {
        this.volume.set(Math.max(0.0, Math.min(1.0, volume)));
    }
    
    @Override
    public double getVolume() {
        return volume.get();
    }
    
    @Override
    public double getCurrentTime() {
        return currentTime.get();
    }
    
    @Override
    public double getTotalTime() {
        return totalTime.get();
    }
    
    @Override
    public boolean isPlaying() {
        return playing.get();
    }
    
    @Override
    public void dispose() {
        if (player != null) {
            try {
                player.stop();
            } catch (BasicPlayerException e) {
                LOGGER.log(Level.WARNING, "Error stopping player during disposal", e);
            }
            player.removeBasicPlayerListener(this);
            player = null;
        }
        
        Platform.runLater(() -> {
            playing.set(false);
            currentTime.set(0.0);
            totalTime.set(0.0);
            currentSongProperty.set(null);
        });
        
        currentSong = null;
        audioDataLength = 0;
        currentBytes = 0;
        
        LOGGER.info("Audio engine disposed");
    }
    
    @Override
    public BooleanProperty playingProperty() {
        return playing;
    }
    
    @Override
    public DoubleProperty currentTimeProperty() {
        return currentTime;
    }
    
    @Override
    public DoubleProperty totalTimeProperty() {
        return totalTime;
    }
    
    @Override
    public DoubleProperty volumeProperty() {
        return volume;
    }
    
    @Override
    public ObjectProperty<Song> currentSongProperty() {
        return currentSongProperty;
    }
    
    @Override
    public void setOnSongEnded(Runnable callback) {
        this.onSongEndedCallback = callback;
    }
    
    @Override
    public void setOnError(Runnable callback) {
        this.onErrorCallback = callback;
    }
    
    @Override
    public void setAudioSpectrumListener(AudioSpectrumListener listener) {
        // BasicPlayer doesn't support spectrum analysis directly
        // Store the listener in case we implement FFT analysis in the future
        this.spectrumListener = listener;
        LOGGER.info("Audio spectrum listener set (not implemented for BasicPlayer)");
    }
    
    @Override
    public AudioSpectrumListener getAudioSpectrumListener() {
        return this.spectrumListener;
    }
    
    // BasicPlayerListener implementation
    
    @Override
    public void opened(Object stream, Map properties) {
        LOGGER.fine("Stream opened");
        
        // Extract audio properties
        if (properties != null) {
            if (properties.containsKey("audio.length.bytes")) {
                Object lengthObj = properties.get("audio.length.bytes");
                if (lengthObj instanceof Long) {
                    audioDataLength = ((Long) lengthObj).longValue();
                } else if (lengthObj instanceof Integer) {
                    audioDataLength = ((Integer) lengthObj).longValue();
                }
            }
            
            if (properties.containsKey("duration")) {
                Object durationObj = properties.get("duration");
                long durationMicroseconds = 0;
                if (durationObj instanceof Long) {
                    durationMicroseconds = ((Long) durationObj).longValue();
                } else if (durationObj instanceof Integer) {
                    durationMicroseconds = ((Integer) durationObj).longValue();
                }
                double durationSeconds = durationMicroseconds / 1_000_000.0;
                Platform.runLater(() -> totalTime.set(durationSeconds));
                LOGGER.fine("Duration: " + formatTime(durationSeconds));
            }
            
            // Log format information
            if (properties.containsKey("audio.type")) {
                LOGGER.info("Audio format: " + properties.get("audio.type"));
            }
            if (properties.containsKey("audio.samplerate.hz")) {
                LOGGER.fine("Sample rate: " + properties.get("audio.samplerate.hz") + " Hz");
            }
            if (properties.containsKey("audio.channels")) {
                LOGGER.fine("Channels: " + properties.get("audio.channels"));
            }
        }
    }
    
    @Override
    public void progress(int bytesread, long microseconds, byte[] pcmdata, Map properties) {
        if (!isSeeking) {
            currentBytes = bytesread;
            double seconds = microseconds / 1_000_000.0;
            Platform.runLater(() -> currentTime.set(seconds));
        }
    }
    
    @Override
    public void stateUpdated(BasicPlayerEvent event) {
        int code = event.getCode();
        
        switch (code) {
            case BasicPlayerEvent.PLAYING:
                Platform.runLater(() -> playing.set(true));
                LOGGER.fine("State: PLAYING");
                break;
                
            case BasicPlayerEvent.PAUSED:
                Platform.runLater(() -> playing.set(false));
                LOGGER.fine("State: PAUSED");
                break;
                
            case BasicPlayerEvent.STOPPED:
                Platform.runLater(() -> {
                    playing.set(false);
                    currentTime.set(0.0);
                });
                LOGGER.fine("State: STOPPED");
                break;
                
            case BasicPlayerEvent.EOM:
                Platform.runLater(() -> {
                    playing.set(false);
                    if (onSongEndedCallback != null) {
                        onSongEndedCallback.run();
                    }
                });
                LOGGER.fine("End of media reached");
                break;
                
            case BasicPlayerEvent.OPENING:
                LOGGER.fine("State: OPENING");
                break;
                
            case BasicPlayerEvent.OPENED:
                LOGGER.fine("State: OPENED");
                break;
                
            case BasicPlayerEvent.SEEKING:
                LOGGER.fine("State: SEEKING");
                break;
                
            case BasicPlayerEvent.SEEKED:
                LOGGER.fine("State: SEEKED");
                break;
        }
    }
    
    @Override
    public void setController(BasicController controller) {
        // Not used in this implementation
    }
    
    // Helper methods
    
    /**
     * Converts volume from 0.0-1.0 range to gain in dB.
     * BasicPlayer uses gain range from -80.0 to 6.0206 dB.
     */
    private double convertVolumeToGain(double volume) {
        if (volume <= 0.0) {
            return -80.0; // Minimum gain (mute)
        }
        
        // Convert linear volume to logarithmic gain
        // Using a logarithmic scale for more natural volume control
        double gain = 20.0 * Math.log10(volume);
        
        // Clamp to BasicPlayer's gain range
        return Math.max(-80.0, Math.min(6.0206, gain));
    }
    
    /**
     * Formats time in seconds to MM:SS format.
     */
    private String formatTime(double seconds) {
        int minutes = (int) seconds / 60;
        int secs = (int) seconds % 60;
        return String.format("%d:%02d", minutes, secs);
    }
}