package com.musicplayer.data.repositories;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.musicplayer.data.models.Artist;
import com.musicplayer.data.storage.LibraryStorage;

/**
 * Persistent repository implementation for artists that uses LibraryStorage
 * to save and load data across application sessions.
 */
public class PersistentArtistRepository implements ArtistRepository {
    
    private final Map<Long, Artist> artists = new HashMap<>();
    private final AtomicLong idCounter = new AtomicLong();
    private final LibraryStorage storage;
    private boolean isLoaded = false;
    
    public PersistentArtistRepository(LibraryStorage storage) {
        this.storage = storage;
        loadFromStorage();
    }
    
    /**
     * Loads artists from persistent storage into memory.
     */
    private void loadFromStorage() {
        if (isLoaded) {
            return;
        }
        
        try {
            List<Artist> savedArtists = storage.loadArtists();
            long maxId = 0;
            
            for (Artist artist : savedArtists) {
                artists.put(artist.getId(), artist);
                if (artist.getId() > maxId) {
                    maxId = artist.getId();
                }
            }
            
            // Set the ID counter to continue from the highest existing ID
            idCounter.set(maxId);
            isLoaded = true;
            
            System.out.println("Loaded " + savedArtists.size() + " artists from storage");
        } catch (IOException e) {
            System.err.println("Failed to load artists from storage: " + e.getMessage());
            // Continue with empty repository if loading fails
            isLoaded = true;
        }
    }
    
    /**
     * Saves all artists to persistent storage.
     */
    private void saveToStorage() {
        try {
            storage.saveArtists(new ArrayList<>(artists.values()));
        } catch (IOException e) {
            System.err.println("Failed to save artists to storage: " + e.getMessage());
        }
    }
    
    @Override
    public void save(Artist artist) {
        loadFromStorage(); // Ensure data is loaded
        
        if (artist.getId() == 0) {
            artist.setId(idCounter.incrementAndGet());
        }
        artists.put(artist.getId(), artist);
        saveToStorage();
    }
    
    @Override
    public Artist findById(long id) {
        loadFromStorage(); // Ensure data is loaded
        return artists.get(id);
    }
    
    @Override
    public List<Artist> findAll() {
        loadFromStorage(); // Ensure data is loaded
        return new ArrayList<>(artists.values());
    }
    
    @Override
    public void delete(long id) {
        loadFromStorage(); // Ensure data is loaded
        if (artists.remove(id) != null) {
            saveToStorage();
        }
    }
    
    /**
     * Finds an artist by their name.
     * Note: This is an additional convenience method not in the interface.
     */
    public Artist findByName(String name) {
        loadFromStorage(); // Ensure data is loaded
        return artists.values().stream()
                .filter(artist -> artist.getName().equals(name))
                .findFirst()
                .orElse(null);
    }
    
    /**
     * Clears all artists from the repository and storage.
     */
    public void clear() {
        artists.clear();
        saveToStorage();
    }
    
    /**
     * Forces a save of all current data to storage.
     */
    public void forceSave() {
        saveToStorage();
    }
}
