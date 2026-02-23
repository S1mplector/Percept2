package com.musicplayer.ui.handlers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.musicplayer.data.models.Playlist;
import com.musicplayer.data.models.Song;
import com.musicplayer.services.AudioPlayerService;

import javafx.collections.ObservableList;

/**
 * Handles actions for playlists such as playing and shuffling.
 * Encapsulates the logic for playlist operations to keep UI components clean.
 */
public class PlaylistActionHandler {
    
    private final AudioPlayerService audioPlayerService;
    private final ObservableList<Song> songsList;
    private Runnable onPlaylistSelected;
    
    /**
     * Creates a new playlist action handler.
     * 
     * @param audioPlayerService The audio player service
     * @param songsList The observable list of songs to update
     */
    public PlaylistActionHandler(AudioPlayerService audioPlayerService, ObservableList<Song> songsList) {
        this.audioPlayerService = audioPlayerService;
        this.songsList = songsList;
    }
    
    /**
     * Sets a callback to be executed when a playlist is selected for playing.
     * 
     * @param callback The callback to execute
     */
    public void setOnPlaylistSelected(Runnable callback) {
        this.onPlaylistSelected = callback;
    }
    
    /**
     * Plays all songs from the playlist in order.
     * 
     * @param playlist The playlist to play
     */
    public void playPlaylist(Playlist playlist) {
        if (playlist == null || playlist.getSongs().isEmpty()) {
            return;
        }
        
        // Update the songs list with playlist songs
        songsList.clear();
        songsList.addAll(playlist.getSongs());
        
        // Set the playlist in audio player and start playing
        audioPlayerService.setPlaylist(songsList);
        audioPlayerService.playTrack(0);
        
        // Execute callback if set
        if (onPlaylistSelected != null) {
            onPlaylistSelected.run();
        }
    }
    
    /**
     * Shuffles and plays all songs from the playlist.
     * 
     * @param playlist The playlist to shuffle and play
     */
    public void shufflePlaylist(Playlist playlist) {
        if (playlist == null || playlist.getSongs().isEmpty()) {
            return;
        }
        
        // Create a copy of the songs and shuffle them
        List<Song> shuffledSongs = new ArrayList<>(playlist.getSongs());
        Collections.shuffle(shuffledSongs);
        
        // Update the songs list with shuffled songs
        songsList.clear();
        songsList.addAll(shuffledSongs);
        
        // Enable shuffle mode and start playing
        audioPlayerService.setShuffle(true);
        audioPlayerService.setPlaylist(songsList);
        audioPlayerService.playTrack(0);
        
        // Execute callback if set
        if (onPlaylistSelected != null) {
            onPlaylistSelected.run();
        }
    }
} 