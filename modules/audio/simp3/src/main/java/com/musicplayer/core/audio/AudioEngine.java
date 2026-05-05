package com.musicplayer.core.audio;

import com.musicplayer.data.models.Song;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;

/**
 * Core audio engine interface that defines the contract for audio playback functionality.
 * This abstraction allows for different audio implementation backends while maintaining
 * the same interface for the rest of the application.
 */
public interface AudioEngine {
    
    /**
     * Loads a song for playback.
     * 
     * @param song The song to load
     * @return true if song was loaded successfully, false otherwise
     */
    boolean loadSong(Song song);
    
    /**
     * Starts or resumes playback.
     */
    void play();
    
    /**
     * Pauses playback.
     */
    void pause();
    
    /**
     * Stops playback completely.
     */
    void stop();
    
    /**
     * Seeks to a specific time position.
     * 
     * @param seconds Time position in seconds
     */
    void seek(double seconds);
    
    /**
     * Sets the volume level.
     * 
     * @param volume Volume level (0.0 to 1.0)
     */
    void setVolume(double volume);
    
    /**
     * Gets the current volume level.
     * 
     * @return Current volume (0.0 to 1.0)
     */
    double getVolume();
    
    /**
     * Gets the current playback time.
     * 
     * @return Current time in seconds
     */
    double getCurrentTime();
    
    /**
     * Gets the total duration of the current song.
     * 
     * @return Total duration in seconds
     */
    double getTotalTime();
    
    /**
     * Checks if audio is currently playing.
     * 
     * @return true if playing, false otherwise
     */
    boolean isPlaying();
    
    /**
     * Disposes of audio resources.
     */
    void dispose();
    
    // Observable properties for UI binding
    BooleanProperty playingProperty();
    DoubleProperty currentTimeProperty();
    DoubleProperty totalTimeProperty();
    DoubleProperty volumeProperty();
    ObjectProperty<Song> currentSongProperty();
    
    /**
     * Sets a callback for when the current song ends.
     * 
     * @param callback Runnable to execute when song ends
     */
    void setOnSongEnded(Runnable callback);
    
    /**
     * Sets a callback for when an error occurs.
     * 
     * @param callback Runnable to execute on error
     */
    void setOnError(Runnable callback);

    void setAudioSpectrumListener(javafx.scene.media.AudioSpectrumListener listener);
    
    /**
     * Gets the current audio spectrum listener.
     * 
     * @return The current audio spectrum listener, or null if none is set
     */
    javafx.scene.media.AudioSpectrumListener getAudioSpectrumListener();
}
