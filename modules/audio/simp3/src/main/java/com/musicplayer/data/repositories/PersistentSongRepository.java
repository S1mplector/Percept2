package com.musicplayer.data.repositories;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.musicplayer.data.models.Song;
import com.musicplayer.data.storage.LibraryStorage;

/**
 * Persistent repository implementation for songs that uses LibraryStorage
 * to save and load data across application sessions.
 */
public class PersistentSongRepository implements SongRepository {

    private static final Logger log = LoggerFactory.getLogger(PersistentSongRepository.class);

    private final Map<Long, Song> songs = new HashMap<>();
    private final AtomicLong idCounter = new AtomicLong();
    private final LibraryStorage storage;
    private boolean isLoaded = false;
    private final Object lock = new Object();
    
    public PersistentSongRepository(LibraryStorage storage) {
        this.storage = storage;
        loadFromStorage();
    }
    
    /**
     * Loads songs from persistent storage into memory.
     */
    private void loadFromStorage() {
        synchronized (lock) {
            if (isLoaded) {
                return;
            }
            try {
                List<Song> savedSongs = storage.loadSongs();
                long maxId = 0;
                for (Song song : savedSongs) {
                    songs.put(song.getId(), song);
                    if (song.getId() > maxId) {
                        maxId = song.getId();
                    }
                }
                idCounter.set(maxId);
                isLoaded = true;
                log.debug("Loaded {} songs from storage", savedSongs.size());
            } catch (IOException e) {
                log.error("Failed to load songs from storage", e);
                isLoaded = true;
            }
        }
    }
    
    /**
     * Saves all songs to persistent storage.
     */
    private void saveToStorage() {
        synchronized (lock) {
            try {
                storage.saveSongs(new ArrayList<>(songs.values()));
            } catch (IOException e) {
                log.error("Failed to save songs to storage", e);
            }
        }
    }
    
    @Override
    public void save(Song song) {
        synchronized (lock) {
            loadFromStorage();
            if (song.getId() == 0) {
                song.setId(idCounter.incrementAndGet());
            }
            songs.put(song.getId(), song);
            saveToStorage();
        }
    }
    
    @Override
    public Song findById(long id) {
        synchronized (lock) {
            loadFromStorage();
            return songs.get(id);
        }
    }
    
    @Override
    public List<Song> findAll() {
        synchronized (lock) {
            loadFromStorage();
            return new ArrayList<>(songs.values());
        }
    }
    
    @Override
    public void delete(long id) {
        synchronized (lock) {
            loadFromStorage();
            if (songs.remove(id) != null) {
                saveToStorage();
            }
        }
    }
    
    /**
     * Clears all songs from the repository and storage.
     */
    public void clear() {
        synchronized (lock) {
            songs.clear();
            saveToStorage();
        }
    }
    
    /**
     * Forces a save of all current data to storage.
     */
    public void forceSave() {
        synchronized (lock) {
            saveToStorage();
        }
    }
    
    /**
     * Returns the number of songs in the repository.
     */
    public int size() {
        synchronized (lock) {
            loadFromStorage();
            return songs.size();
        }
    }
}
