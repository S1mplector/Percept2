package com.musicplayer.services;

import java.util.List;

import com.musicplayer.core.audio.AudioEngine;
import com.musicplayer.core.audio.HybridAudioEngine;
import com.musicplayer.core.playlist.AdvancedPlaylistEngine;
import com.musicplayer.core.playlist.PlaylistEngine;
import com.musicplayer.data.models.Song;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;

/**
 * Enhanced AudioPlayerService that integrates AudioEngine and PlaylistEngine.
 * Provides comprehensive playback control with advanced playlist features.
 */
public class AudioPlayerService {
    private final AudioEngine audioEngine;
    private final PlaylistEngine playlistEngine;
    private Runnable customErrorCallback;
    private boolean suppressErrorDialogs = false;
    
    public AudioPlayerService() {
        this.audioEngine = new HybridAudioEngine();
        this.playlistEngine = new AdvancedPlaylistEngine();
        
        // Set up audio engine callbacks
        audioEngine.setOnSongEnded(this::handleSongEnded);
        audioEngine.setOnError(() -> {
            System.err.println("Audio playback error occurred");
            if (customErrorCallback != null && !suppressErrorDialogs) {
                customErrorCallback.run();
            }
            // Don't automatically advance to next song on error to prevent infinite loops
            // Let the user manually skip or handle the error
        });
    }
    
    /**
     * Handles song ended event by advancing to next track.
     */
    private void handleSongEnded() {
        Song nextSong = playlistEngine.next();
        if (nextSong != null) {
            if (audioEngine.loadSong(nextSong)) {
                audioEngine.play();
            }
        }
    }
    
    /**
     * Sets the current playlist for playback.
     * 
     * @param songs List of songs to set as playlist
     */
    public void setPlaylist(List<Song> songs) {
        playlistEngine.setPlaylist(songs);
    }
    
    /**
     * Plays the current song or resumes playback.
     */
    public void play() {
        audioEngine.play();
    }
    
    /**
     * Pauses the current playback.
     */
    public void pause() {
        audioEngine.pause();
    }
    
    /**
     * Toggles between play and pause.
     */
    public void togglePlayPause() {
        if (audioEngine.isPlaying()) {
            pause();
        } else {
            // If no song loaded, try to play current playlist song
            if (audioEngine.currentSongProperty().get() == null) {
                Song currentSong = playlistEngine.getCurrentSong();
                if (currentSong == null && !playlistEngine.isEmpty()) {
                    // Start with first song
                    currentSong = playlistEngine.next();
                }
                if (currentSong != null && audioEngine.loadSong(currentSong)) {
                    audioEngine.play();
                }
            } else {
                play();
            }
        }
    }
    
    /**
     * Stops the current playback.
     */
    public void stop() {
        audioEngine.stop();
    }
    
    /**
     * Plays a specific song from the current playlist.
     * 
     * @param song The song to play
     */
    public void playTrack(Song song) {
        if (song == null) {
            return;
        }
        
        // Set current song in playlist engine
        List<Song> playlist = playlistEngine.getPlaylist();
        int index = playlist.indexOf(song);
        if (index != -1) {
            playlistEngine.setCurrentIndex(index);
        }
        
        if (audioEngine.loadSong(song)) {
            audioEngine.play();
            // Ensure spectrum listener is active for visualizer updates
            refreshSpectrumListener();
        }
    }

    /**
     * Loads a specific song from the current playlist without starting playback.
     * Useful for restoring last session state to a paused position.
     *
     * @param song The song to load
     * @return true if loaded successfully
     */
    public boolean loadTrack(Song song) {
        if (song == null) {
            return false;
        }
        // Set current song in playlist engine
        List<Song> playlist = playlistEngine.getPlaylist();
        int index = playlist.indexOf(song);
        if (index != -1) {
            playlistEngine.setCurrentIndex(index);
        }
        boolean ok = audioEngine.loadSong(song);
        if (ok) {
            // Ensure spectrum listener is active for visualizer updates
            refreshSpectrumListener();
        }
        return ok;
    }
    
    /**
     * Plays the track at the specified index.
     * 
     * @param index Track index to play
     */
    public void playTrack(int index) {
        if (index >= 0 && index < playlistEngine.size()) {
            playlistEngine.setCurrentIndex(index);
            Song song = playlistEngine.getCurrentSong();
            if (song != null && audioEngine.loadSong(song)) {
                audioEngine.play();
            }
        }
    }
    
    /**
     * Skips to the next track in the playlist.
     */
    public void nextTrack() {
        Song nextSong = playlistEngine.next();
        if (nextSong != null && audioEngine.loadSong(nextSong)) {
            audioEngine.play();
        }
    }
    
    /**
     * Skips to the previous track in the playlist.
     */
    public void previousTrack() {
        Song prevSong = playlistEngine.previous();
        if (prevSong != null && audioEngine.loadSong(prevSong)) {
            audioEngine.play();
        }
    }
    
    /**
     * Seeks to a specific time position in the current track.
     * 
     * @param seconds Time position in seconds
     */
    public void seek(double seconds) {
        audioEngine.seek(seconds);
    }
    
    /**
     * Sets the volume level.
     * 
     * @param volume Volume level (0.0 to 1.0)
     */
    public void setVolume(double volume) {
        audioEngine.setVolume(volume);
    }
    
    // Playlist engine methods
    public void setShuffle(boolean shuffle) {
        playlistEngine.setShuffle(shuffle);
    }
    
    public boolean isShuffle() {
        return playlistEngine.isShuffle();
    }
    
    public void setRepeatMode(PlaylistEngine.RepeatMode mode) {
        playlistEngine.setRepeatMode(mode);
    }
    
    public PlaylistEngine.RepeatMode getRepeatMode() {
        return playlistEngine.getRepeatMode();
    }
    
    public void queueSong(Song song) {
        playlistEngine.queueSong(song);
    }
    
    public List<Song> getQueue() {
        return playlistEngine.getQueue();
    }
    
    public List<Song> getHistory() {
        return playlistEngine.getHistory();
    }
    
    /**
     * Disposes of the audio engine and playlist engine resources.
     */
    public void dispose() {
        audioEngine.dispose();
        playlistEngine.clear();
    }
    
    // Property getters for UI binding (delegate to audio engine)
    public BooleanProperty playingProperty() { 
        return audioEngine.playingProperty(); 
    }
    
    public DoubleProperty currentTimeProperty() { 
        return audioEngine.currentTimeProperty(); 
    }
    
    public DoubleProperty totalTimeProperty() { 
        return audioEngine.totalTimeProperty(); 
    }
    
    public DoubleProperty volumeProperty() { 
        return audioEngine.volumeProperty(); 
    }
    
    public ObjectProperty<Song> currentSongProperty() { 
        return audioEngine.currentSongProperty(); 
    }
    
    // Convenience getters
    public boolean isPlaying() { 
        return audioEngine.isPlaying(); 
    }
    
    public double getCurrentTime() { 
        return audioEngine.getCurrentTime(); 
    }
    
    public double getTotalTime() { 
        return audioEngine.getTotalTime(); 
    }
    
    public double getVolume() { 
        return audioEngine.getVolume(); 
    }
    
    public Song getCurrentSong() { 
        return audioEngine.currentSongProperty().get(); 
    }
    
    /**
     * Gets the current playlist.
     * 
     * @return Copy of the current playlist
     */
    public List<Song> getCurrentPlaylist() {
        return playlistEngine.getPlaylist();
    }
    
    /**
     * Gets the current track index.
     * 
     * @return Current track index, or -1 if no track is selected
     */
    public int getCurrentTrackIndex() {
        return playlistEngine.getCurrentIndex();
    }

    public void setAudioSpectrumListener(javafx.scene.media.AudioSpectrumListener listener) {
        audioEngine.setAudioSpectrumListener(listener);
    }

    public void setOnError(Runnable callback) {
        this.customErrorCallback = callback;
    }
    
    /**
     * Temporarily suppress error dialogs during library scanning operations.
     * This prevents recursive dialog windows when many files fail to load.
     */
    public void setSuppressErrorDialogs(boolean suppress) {
        this.suppressErrorDialogs = suppress;
    }
    
    /**
     * Check if error dialogs are currently suppressed.
     */
    public boolean isErrorDialogsSuppressed() {
        return suppressErrorDialogs;
    }
    
    /**
     * Refresh the spectrum listener to ensure visualizer updates properly.
     * This helps prevent visualizer freezing when songs are changed via different methods.
     */
    private void refreshSpectrumListener() {
        // Get current listener and reattach it to ensure proper connection
        javafx.scene.media.AudioSpectrumListener currentListener = audioEngine.getAudioSpectrumListener();
        if (currentListener != null) {
            audioEngine.setAudioSpectrumListener(null);
            audioEngine.setAudioSpectrumListener(currentListener);
        }
    }
}
