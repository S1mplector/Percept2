package com.musicplayer.ui.controllers;

import com.musicplayer.data.models.Song;

import javafx.collections.ObservableList;

/**
 * Interface for playback controller functionality.
 * Defines methods for audio playback controls, volume management, and seeking.
 */
public interface IPlaybackController {
    
    /**
     * Handle play/pause button action.
     */
    void handlePlayPause();
    
    /**
     * Handle previous track button action.
     */
    void handlePrevious();
    
    /**
     * Handle next track button action.
     */
    void handleNext();
    
    /**
     * Play a specific song from the current playlist.
     * @param song The song to play
     */
    void playSelectedSong(Song song);
    
    /**
     * Stop playback completely.
     */
    void stopPlayback();
    
    /**
     * Get information about the currently playing song.
     * @return String with current song info, or null if no song is playing
     */
    String getCurrentSongInfo();
    
    /**
     * Update the current playlist.
     * @param songs The new playlist
     */
    void updatePlaylist(ObservableList<Song> songs);
    
    /**
     * Set callback for when listening stats are updated.
     * @param callback The callback to execute
     */
    void setOnStatsUpdate(Runnable callback);
    
    /**
     * Set callback for when playback state changes.
     * @param callback The callback to execute
     */
    void setOnPlaybackStateChange(Runnable callback);
    
    /**
     * Cleanup resources when shutting down.
     */
    void cleanup();
}