package com.musicplayer.core.playlist;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import com.musicplayer.data.models.Song;

/**
 * Advanced in-memory implementation of PlaylistEngine.
 * Provides shuffle, repeat modes, queue management, and playback history.
 */
public class AdvancedPlaylistEngine implements PlaylistEngine {
    
    private List<Song> originalPlaylist = new ArrayList<>();
    private List<Song> currentPlaylist = new ArrayList<>();
    private final List<Song> queue = new ArrayList<>();
    private final List<Song> history = new ArrayList<>();
    
    private int currentIndex = -1;
    private boolean shuffle = false;
    private RepeatMode repeatMode = RepeatMode.NONE;
    
    private final Random random = new Random();
    private final int maxHistorySize = 100;
    
    @Override
    public void setPlaylist(List<Song> songs) {
        this.originalPlaylist = new ArrayList<>(songs);
        this.currentPlaylist = new ArrayList<>(songs);
        this.currentIndex = -1;
        
        if (shuffle) {
            shufflePlaylist();
        }
    }
    
    @Override
    public List<Song> getPlaylist() {
        return new ArrayList<>(currentPlaylist);
    }
    
    @Override
    public void addSong(Song song) {
        originalPlaylist.add(song);
        currentPlaylist.add(song);
    }
    
    @Override
    public void insertSong(int index, Song song) {
        if (index >= 0 && index <= currentPlaylist.size()) {
            originalPlaylist.add(index, song);
            currentPlaylist.add(index, song);
            
            // Adjust current index if necessary
            if (index <= currentIndex) {
                currentIndex++;
            }
        }
    }
    
    @Override
    public boolean removeSong(Song song) {
        int index = currentPlaylist.indexOf(song);
        if (index != -1) {
            return removeSong(index) != null;
        }
        return false;
    }
    
    @Override
    public Song removeSong(int index) {
        if (index >= 0 && index < currentPlaylist.size()) {
            Song removed = currentPlaylist.remove(index);
            originalPlaylist.remove(removed);
            
            // Adjust current index
            if (index < currentIndex) {
                currentIndex--;
            } else if (index == currentIndex) {
                // Current song was removed
                if (currentIndex >= currentPlaylist.size()) {
                    currentIndex = currentPlaylist.isEmpty() ? -1 : 0;
                }
            }
            
            return removed;
        }
        return null;
    }
    
    @Override
    public int getCurrentIndex() {
        return currentIndex;
    }
    
    @Override
    public void setCurrentIndex(int index) {
        if (index >= -1 && index < currentPlaylist.size()) {
            this.currentIndex = index;
        }
    }
    
    @Override
    public Song getCurrentSong() {
        if (currentIndex >= 0 && currentIndex < currentPlaylist.size()) {
            return currentPlaylist.get(currentIndex);
        }
        return null;
    }
    
    @Override
    public Song getNextSong() {
        // Check queue first
        if (!queue.isEmpty()) {
            return queue.get(0);
        }
        
        if (currentPlaylist.isEmpty()) {
            return null;
        }
        
        // Handle repeat one
        if (repeatMode == RepeatMode.ONE && currentIndex >= 0) {
            return getCurrentSong();
        }
        
        int nextIndex = getNextIndex();
        if (nextIndex >= 0 && nextIndex < currentPlaylist.size()) {
            return currentPlaylist.get(nextIndex);
        }
        
        return null;
    }
    
    @Override
    public Song getPreviousSong() {
        if (currentPlaylist.isEmpty()) {
            return null;
        }
        
        // Handle repeat one
        if (repeatMode == RepeatMode.ONE && currentIndex >= 0) {
            return getCurrentSong();
        }
        
        int prevIndex = getPreviousIndex();
        if (prevIndex >= 0 && prevIndex < currentPlaylist.size()) {
            return currentPlaylist.get(prevIndex);
        }
        
        return null;
    }
    
    @Override
    public Song next() {
        // Play from queue first
        if (!queue.isEmpty()) {
            Song queuedSong = queue.remove(0);
            addToHistory(getCurrentSong());
            return queuedSong;
        }
        
        if (currentPlaylist.isEmpty()) {
            return null;
        }
        
        // Handle repeat one
        if (repeatMode == RepeatMode.ONE && currentIndex >= 0) {
            return getCurrentSong();
        }
        
        Song currentSong = getCurrentSong();
        if (currentSong != null) {
            addToHistory(currentSong);
        }
        
        currentIndex = getNextIndex();
        return getCurrentSong();
    }
    
    @Override
    public Song previous() {
        if (currentPlaylist.isEmpty()) {
            return null;
        }
        
        // Handle repeat one
        if (repeatMode == RepeatMode.ONE && currentIndex >= 0) {
            return getCurrentSong();
        }
        
        currentIndex = getPreviousIndex();
        return getCurrentSong();
    }
    
    @Override
    public void setShuffle(boolean shuffle) {
        this.shuffle = shuffle;
        
        if (shuffle) {
            shufflePlaylist();
        } else {
            resetOrder();
        }
    }
    
    @Override
    public boolean isShuffle() {
        return shuffle;
    }
    
    @Override
    public void setRepeatMode(RepeatMode mode) {
        this.repeatMode = mode != null ? mode : RepeatMode.NONE;
    }
    
    @Override
    public RepeatMode getRepeatMode() {
        return repeatMode;
    }
    
    @Override
    public void shufflePlaylist() {
        if (originalPlaylist.isEmpty()) {
            return;
        }
        
        Song currentSong = getCurrentSong();
        
        // Fisher-Yates shuffle
        currentPlaylist = new ArrayList<>(originalPlaylist);
        for (int i = currentPlaylist.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            Collections.swap(currentPlaylist, i, j);
        }
        
        // Update current index to maintain current song
        if (currentSong != null) {
            currentIndex = currentPlaylist.indexOf(currentSong);
        }
        
        shuffle = true;
    }
    
    @Override
    public void resetOrder() {
        Song currentSong = getCurrentSong();
        
        currentPlaylist = new ArrayList<>(originalPlaylist);
        
        // Update current index to maintain current song
        if (currentSong != null) {
            currentIndex = currentPlaylist.indexOf(currentSong);
        } else {
            currentIndex = -1;
        }
        
        shuffle = false;
    }
    
    @Override
    public void clear() {
        originalPlaylist.clear();
        currentPlaylist.clear();
        queue.clear();
        currentIndex = -1;
    }
    
    @Override
    public int size() {
        return currentPlaylist.size();
    }
    
    @Override
    public boolean isEmpty() {
        return currentPlaylist.isEmpty();
    }
    
    @Override
    public List<Song> getHistory() {
        return new ArrayList<>(history);
    }
    
    @Override
    public void clearHistory() {
        history.clear();
    }
    
    @Override
    public List<Song> getQueue() {
        return new ArrayList<>(queue);
    }
    
    @Override
    public void queueSong(Song song) {
        if (song != null) {
            queue.add(song);
        }
    }
    
    @Override
    public void clearQueue() {
        queue.clear();
    }
    
    /**
     * Gets the next index considering shuffle and repeat modes.
     * 
     * @return Next index, or -1 if none
     */
    private int getNextIndex() {
        if (currentPlaylist.isEmpty()) {
            return -1;
        }
        
        if (shuffle) {
            // In shuffle mode, pick random next song (but not current)
            if (currentPlaylist.size() == 1) {
                return repeatMode == RepeatMode.ALL ? 0 : -1;
            }
            
            int nextIndex;
            do {
                nextIndex = random.nextInt(currentPlaylist.size());
            } while (nextIndex == currentIndex && currentPlaylist.size() > 1);
            
            return nextIndex;
        } else {
            // Sequential mode
            int nextIndex = currentIndex + 1;
            
            if (nextIndex >= currentPlaylist.size()) {
                // End of playlist
                return repeatMode == RepeatMode.ALL ? 0 : -1;
            }
            
            return nextIndex;
        }
    }
    
    /**
     * Gets the previous index considering shuffle and repeat modes.
     * 
     * @return Previous index, or -1 if none
     */
    private int getPreviousIndex() {
        if (currentPlaylist.isEmpty()) {
            return -1;
        }
        
        if (shuffle) {
            // In shuffle mode, pick random previous song
            if (currentPlaylist.size() == 1) {
                return repeatMode == RepeatMode.ALL ? 0 : -1;
            }
            
            int prevIndex;
            do {
                prevIndex = random.nextInt(currentPlaylist.size());
            } while (prevIndex == currentIndex && currentPlaylist.size() > 1);
            
            return prevIndex;
        } else {
            // Sequential mode
            int prevIndex = currentIndex - 1;
            
            if (prevIndex < 0) {
                // Beginning of playlist
                return repeatMode == RepeatMode.ALL ? currentPlaylist.size() - 1 : -1;
            }
            
            return prevIndex;
        }
    }
    
    /**
     * Adds a song to the playback history.
     * 
     * @param song Song to add to history
     */
    private void addToHistory(Song song) {
        if (song == null) {
            return;
        }
        
        // Remove if already in history to avoid duplicates
        history.remove(song);
        
        // Add to beginning of history
        history.add(0, song);
        
        // Trim history to max size
        while (history.size() > maxHistorySize) {
            history.remove(history.size() - 1);
        }
    }
}
