package com.musicplayer.data.repositories;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.musicplayer.data.models.Album;
import com.musicplayer.data.storage.LibraryStorage;

/**
 * Persistent repository implementation for albums that uses LibraryStorage
 * to save and load data across application sessions.
 */
public class PersistentAlbumRepository implements AlbumRepository {
    
    private final Map<Long, Album> albums = new HashMap<>();
    private final AtomicLong idCounter = new AtomicLong();
    private final LibraryStorage storage;
    private boolean isLoaded = false;
    
    public PersistentAlbumRepository(LibraryStorage storage) {
        this.storage = storage;
        loadFromStorage();
    }
    
    /**
     * Loads albums from persistent storage into memory.
     */
    private void loadFromStorage() {
        if (isLoaded) {
            return;
        }
        
        try {
            List<Album> savedAlbums = storage.loadAlbums();
            long maxId = 0;
            
            for (Album album : savedAlbums) {
                albums.put(album.getId(), album);
                if (album.getId() > maxId) {
                    maxId = album.getId();
                }
            }
            
            // Set the ID counter to continue from the highest existing ID
            idCounter.set(maxId);
            isLoaded = true;
            
            System.out.println("Loaded " + savedAlbums.size() + " albums from storage");
        } catch (IOException e) {
            System.err.println("Failed to load albums from storage: " + e.getMessage());
            // Continue with empty repository if loading fails
            isLoaded = true;
        }
    }
    
    /**
     * Saves all albums to persistent storage.
     * What's meant by persistent storage is the root
     * folder that the user has set initially
     */
    private void saveToStorage() {
        try {
            storage.saveAlbums(new ArrayList<>(albums.values()));
        } catch (IOException e) {
            System.err.println("Failed to save albums to storage: " + e.getMessage());
        }
    }
    
    @Override
    public void save(Album album) {
        loadFromStorage(); // Ensure data is loaded
        
        if (album.getId() == 0) {
            album.setId(idCounter.incrementAndGet());
        }
        albums.put(album.getId(), album);
        saveToStorage();
    }
    
    @Override
    public Album findById(long id) {
        loadFromStorage(); // Ensure data is loaded
        return albums.get(id);
    }
    
    @Override
    public List<Album> findAll() {
        loadFromStorage(); // Ensure data is loaded
        return new ArrayList<>(albums.values());
    }
    
    @Override
    public void delete(long id) {
        loadFromStorage(); // Ensure data is loaded
        if (albums.remove(id) != null) {
            saveToStorage();
        }
    }
    
    /**
     * Finds an album by its title.
     * Note: This is an additional convenience method not in the interface.
     */
    public Album findByTitle(String title) {
        loadFromStorage(); // Ensure data is loaded
        return albums.values().stream()
                .filter(album -> album.getTitle().equals(title))
                .findFirst()
                .orElse(null);
    }
    
    /**
     * Clears all albums from the repository and storage.
     */
    public void clear() {
        albums.clear();
        saveToStorage();
    }
    
    /**
     * Forces a save of all current data to storage.
     */
    public void forceSave() {
        saveToStorage();
    }
}
