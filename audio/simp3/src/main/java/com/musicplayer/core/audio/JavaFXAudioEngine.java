package com.musicplayer.core.audio;

import java.io.File;

import com.musicplayer.data.models.Song;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;

/**
 * JavaFX-based implementation of the AudioEngine interface.
 * Uses JavaFX Media API for audio playback functionality.
 */
public class JavaFXAudioEngine implements AudioEngine {
    
    private MediaPlayer mediaPlayer;
    private Song currentSong;
    
    // Observable properties
    private final BooleanProperty playing = new SimpleBooleanProperty(false);
    private final DoubleProperty currentTime = new SimpleDoubleProperty(0.0);
    private final DoubleProperty totalTime = new SimpleDoubleProperty(0.0);
    private final DoubleProperty volume = new SimpleDoubleProperty(0.5);
    private final ObjectProperty<Song> currentSongProperty = new SimpleObjectProperty<>();
    
    // Spectrum listener
    private javafx.scene.media.AudioSpectrumListener spectrumListener;
    
    // Callbacks
    private Runnable onSongEndedCallback;
    private Runnable onErrorCallback;
    
    public JavaFXAudioEngine() {
        // Initialize volume listener
        volume.addListener((obs, oldVal, newVal) -> {
            if (mediaPlayer != null) {
                mediaPlayer.setVolume(newVal.doubleValue());
            }
        });
    }
    
    @Override
    public boolean loadSong(Song song) {
        if (song == null || song.getFilePath() == null) {
            return false;
        }
        
        File audioFile = new File(song.getFilePath());
        if (!audioFile.exists()) {
            System.err.println("Audio file not found: " + song.getFilePath());
            if (onErrorCallback != null) {
                onErrorCallback.run();
            }
            return false;
        }
        
        try {
            // Dispose of previous media player
            if (mediaPlayer != null) {
                mediaPlayer.dispose();
            }
            
            Media media = new Media(audioFile.toURI().toString());
            mediaPlayer = new MediaPlayer(media);
            
            // Configure spectrum analysis if a listener is provided
            mediaPlayer.setAudioSpectrumInterval(0.017); // ~60 FPS
            mediaPlayer.setAudioSpectrumNumBands(64);
            if (spectrumListener != null) {
                mediaPlayer.setAudioSpectrumListener(spectrumListener);
            }
            
            // Set up event handlers
            setupMediaPlayerEvents();
            
            this.currentSong = song;
            currentSongProperty.set(song);
            
            System.out.println("Loaded song: " + song.getTitle());
            return true;
            
        } catch (Exception e) {
            System.err.println("Error loading audio file: " + e.getMessage());
            if (onErrorCallback != null) {
                onErrorCallback.run();
            }
            return false;
        }
    }
    
    private void setupMediaPlayerEvents() {
        mediaPlayer.setOnReady(() -> {
            totalTime.set(mediaPlayer.getTotalDuration().toSeconds());
            mediaPlayer.setVolume(volume.get());
            System.out.println("Media ready - Duration: " + formatTime(getTotalTime()));
        });
        
        mediaPlayer.setOnPlaying(() -> {
            playing.set(true);
            System.out.println("Playing: " + (currentSong != null ? currentSong.getTitle() : "Unknown"));
        });
        
        mediaPlayer.setOnPaused(() -> {
            playing.set(false);
            System.out.println("Paused: " + (currentSong != null ? currentSong.getTitle() : "Unknown"));
        });
        
        mediaPlayer.setOnStopped(() -> {
            playing.set(false);
            System.out.println("Stopped: " + (currentSong != null ? currentSong.getTitle() : "Unknown"));
        });
        
        mediaPlayer.setOnEndOfMedia(() -> {
            playing.set(false);
            System.out.println("End of media: " + (currentSong != null ? currentSong.getTitle() : "Unknown"));
            if (onSongEndedCallback != null) {
                onSongEndedCallback.run();
            }
        });
        
        mediaPlayer.currentTimeProperty().addListener((obs, oldTime, newTime) -> {
            if (newTime != null) {
                currentTime.set(newTime.toSeconds());
            }
        });
        
        mediaPlayer.setOnError(() -> {
            System.err.println("Media player error: " + mediaPlayer.getError().getMessage());
            playing.set(false);
            if (onErrorCallback != null) {
                onErrorCallback.run();
            }
        });
    }
    
    @Override
    public void play() {
        if (mediaPlayer != null) {
            mediaPlayer.play();
        } else {
            System.err.println("No media loaded for playback");
        }
    }
    
    @Override
    public void pause() {
        if (mediaPlayer != null) {
            mediaPlayer.pause();
        }
    }
    
    @Override
    public void stop() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
        }
    }
    
    @Override
    public void seek(double seconds) {
        if (mediaPlayer != null && seconds >= 0 && seconds <= getTotalTime()) {
            mediaPlayer.seek(Duration.seconds(seconds));
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
        if (mediaPlayer != null) {
            mediaPlayer.dispose();
            mediaPlayer = null;
        }
        playing.set(false);
        currentTime.set(0.0);
        totalTime.set(0.0);
        currentSongProperty.set(null);
        currentSong = null;
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
    public void setAudioSpectrumListener(javafx.scene.media.AudioSpectrumListener listener) {
        this.spectrumListener = listener;
        if (mediaPlayer != null) {
            mediaPlayer.setAudioSpectrumListener(listener);
        }
    }
    
    @Override
    public javafx.scene.media.AudioSpectrumListener getAudioSpectrumListener() {
        return this.spectrumListener;
    }
    
    private String formatTime(double seconds) {
        int minutes = (int) seconds / 60;
        int secs = (int) seconds % 60;
        return String.format("%d:%02d", minutes, secs);
    }
}
