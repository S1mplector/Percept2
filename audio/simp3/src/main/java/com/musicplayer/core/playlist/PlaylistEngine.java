package com.musicplayer.core.playlist;

import java.util.List;

import com.musicplayer.data.models.Song;

/**
 * Interface defining advanced playlist management functionality.
 * Provides abstraction for playlist operations including shuffle, repeat modes, and queue management.
 */
public interface PlaylistEngine {
    
    /**
     * Enumeration of repeat modes.
     */
    enum RepeatMode {
        NONE,    // No repeat
        ONE,     // Repeat current song
        ALL      // Repeat entire playlist
    }
    
    /**
     * Sets the current playlist.
     * 
     * @param songs List of songs for the playlist
     */
    void setPlaylist(List<Song> songs);
    
    /**
     * Gets the current playlist.
     * 
     * @return List of songs in current playlist
     */
    List<Song> getPlaylist();
    
    /**
     * Adds a song to the end of the playlist.
     * 
     * @param song Song to add
     */
    void addSong(Song song);
    
    /**
     * Inserts a song at a specific position.
     * 
     * @param index Position to insert at
     * @param song Song to insert
     */
    void insertSong(int index, Song song);
    
    /**
     * Removes a song from the playlist.
     * 
     * @param song Song to remove
     * @return true if song was removed
     */
    boolean removeSong(Song song);
    
    /**
     * Removes a song at a specific index.
     * 
     * @param index Index of song to remove
     * @return The removed song, or null if index invalid
     */
    Song removeSong(int index);
    
    /**
     * Gets the current song index.
     * 
     * @return Current song index, or -1 if none
     */
    int getCurrentIndex();
    
    /**
     * Sets the current song index.
     * 
     * @param index Index to set as current
     */
    void setCurrentIndex(int index);
    
    /**
     * Gets the current song.
     * 
     * @return Current song, or null if none
     */
    Song getCurrentSong();
    
    /**
     * Gets the next song in the playlist.
     * Respects shuffle and repeat modes.
     * 
     * @return Next song, or null if none
     */
    Song getNextSong();
    
    /**
     * Gets the previous song in the playlist.
     * Respects shuffle and repeat modes.
     * 
     * @return Previous song, or null if none
     */
    Song getPreviousSong();
    
    /**
     * Advances to the next song.
     * 
     * @return Next song, or null if none
     */
    Song next();
    
    /**
     * Goes back to the previous song.
     * 
     * @return Previous song, or null if none
     */
    Song previous();
    
    /**
     * Enables or disables shuffle mode.
     * 
     * @param shuffle true to enable shuffle
     */
    void setShuffle(boolean shuffle);
    
    /**
     * Checks if shuffle mode is enabled.
     * 
     * @return true if shuffle is enabled
     */
    boolean isShuffle();
    
    /**
     * Sets the repeat mode.
     * 
     * @param mode Repeat mode to set
     */
    void setRepeatMode(RepeatMode mode);
    
    /**
     * Gets the current repeat mode.
     * 
     * @return Current repeat mode
     */
    RepeatMode getRepeatMode();
    
    /**
     * Shuffles the current playlist.
     * Uses Fisher-Yates shuffle algorithm.
     */
    void shufflePlaylist();
    
    /**
     * Resets the playlist to its original order.
     */
    void resetOrder();
    
    /**
     * Clears the entire playlist.
     */
    void clear();
    
    /**
     * Gets the playlist size.
     * 
     * @return Number of songs in playlist
     */
    int size();
    
    /**
     * Checks if the playlist is empty.
     * 
     * @return true if playlist is empty
     */
    boolean isEmpty();
    
    /**
     * Gets the playback history.
     * 
     * @return List of recently played songs
     */
    List<Song> getHistory();
    
    /**
     * Clears the playback history.
     */
    void clearHistory();
    
    /**
     * Gets the upcoming queue.
     * 
     * @return List of queued songs
     */
    List<Song> getQueue();
    
    /**
     * Adds a song to the queue (to be played next).
     * 
     * @param song Song to queue
     */
    void queueSong(Song song);
    
    /**
     * Clears the queue.
     */
    void clearQueue();
}
