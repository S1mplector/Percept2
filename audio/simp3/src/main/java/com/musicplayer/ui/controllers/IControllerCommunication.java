package com.musicplayer.ui.controllers;

import com.musicplayer.data.models.Song;

import javafx.collections.ObservableList;

/**
 * Interface for communication between controllers.
 * Defines callbacks and methods for inter-controller communication.
 */
public interface IControllerCommunication {
    
    /**
     * Called when listening statistics are updated.
     */
    void onListeningStatsUpdated();
    
    /**
     * Called when playback state changes (play/pause/stop).
     */
    void onPlaybackStateChanged();
    
    /**
     * Called when the current song changes.
     */
    void onCurrentSongChanged(Song newSong);
    
    /**
     * Called when the playlist is updated.
     */
    void onPlaylistUpdated(ObservableList<Song> playlist);
    
    /**
     * Request to play a specific song.
     */
    void requestPlaySong(Song song);
    
    /**
     * Request to refresh the song table view.
     */
    void requestRefreshSongTable();
    
    /**
     * Called when the visualizer needs to be updated.
     */
    void onVisualizerUpdateRequired();
}