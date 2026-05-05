package com.musicplayer.data.repositories;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.musicplayer.data.models.Playlist;
import com.musicplayer.data.storage.LibraryStorage;

/**
 * Persistent repository implementation for playlists that uses LibraryStorage
 * to save and load data across application sessions.
 */
public class PersistentPlaylistRepository implements PlaylistRepository {
    
    private final Map<Long, Playlist> playlists = new HashMap<>();
    private final AtomicLong idCounter = new AtomicLong();
    private final LibraryStorage storage;
    private boolean isLoaded = false;
    
    public PersistentPlaylistRepository(LibraryStorage storage) {
        this.storage = storage;
        loadFromStorage();
    }
    
    /**
     * Loads playlists from persistent storage into memory.
     */
    private void loadFromStorage() {
        if (isLoaded) {
            return;
        }
        
        try {
            List<Playlist> savedPlaylists = storage.loadPlaylists();
            long maxId = 0;
            
            for (Playlist playlist : savedPlaylists) {
                playlists.put(playlist.getId(), playlist);
                if (playlist.getId() > maxId) {
                    maxId = playlist.getId();
                }
            }
            
            // Set the ID counter to continue from the highest existing ID
            idCounter.set(maxId);
            isLoaded = true;
            
            System.out.println("Loaded " + savedPlaylists.size() + " playlists from storage");
        } catch (IOException e) {
            System.err.println("Failed to load playlists from storage: " + e.getMessage());
            // Continue with empty repository if loading fails
            isLoaded = true;
        }
    }
    
    /**
     * Saves all playlists to persistent storage.
     */
    private void saveToStorage() {
        try {
            storage.savePlaylists(new ArrayList<>(playlists.values()));
        } catch (IOException e) {
            System.err.println("Failed to save playlists to storage: " + e.getMessage());
        }
    }
    
    @Override
    public void save(Playlist playlist) {
        loadFromStorage(); // Ensure data is loaded
        
        if (playlist.getId() == 0) {
            playlist.setId(idCounter.incrementAndGet());
        }
        playlists.put(playlist.getId(), playlist);
        saveToStorage();
    }
    
    @Override
    public Playlist findById(long id) {
        loadFromStorage(); // Ensure data is loaded
        return playlists.get(id);
    }
    
    @Override
    public List<Playlist> findAll() {
        loadFromStorage(); // Ensure data is loaded
        return new ArrayList<>(playlists.values());
    }
    
    @Override
    public void delete(long id) {
        loadFromStorage(); // Ensure data is loaded
        if (playlists.remove(id) != null) {
            saveToStorage();
        }
    }
    
    /**
     * Finds a playlist by its name.
     * Note: This is an additional convenience method not in the interface.
     */
    public Playlist findByName(String name) {
        loadFromStorage(); // Ensure data is loaded
        return playlists.values().stream()
                .filter(playlist -> playlist.getName().equals(name))
                .findFirst()
                .orElse(null);
    }
    
    /**
     * Clears all playlists from the repository and storage.
     */
    public void clear() {
        playlists.clear();
        saveToStorage();
    }
    
    /**
     * Forces a save of all current data to storage.
     */
    public void forceSave() {
        saveToStorage();
    }
}
