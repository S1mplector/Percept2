package com.musicplayer.services;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.musicplayer.data.models.Song;

/**
 * Service for managing favorite songs with persistent storage.
 * Stores favorites as a set of song IDs in a JSON file.
 */
public class FavoritesService {
    
    private static final String FAVORITES_FILE = "favorites.json";
    /**
     * System property to override data directory location for persistence.
     * Matches the property used in SettingsService for consistency.
     */
    private static final String DATA_DIR_PROP = "simp3.data.dir";
    private static final String DEFAULT_DATA_DIR = "data";
    
    private final Set<Long> favoriteSongIds;
    private final ObjectMapper objectMapper;
    private final Path favoritesPath;
    
    public FavoritesService() {
        this.favoriteSongIds = new HashSet<>();
        this.objectMapper = new ObjectMapper();
        
        // Resolve data directory from system property or fallback to default "data"
        String dataDirPath = System.getProperty(DATA_DIR_PROP, DEFAULT_DATA_DIR);
        // Ensure data directory exists
        File dataDir = new File(dataDirPath);
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        
        this.favoritesPath = Paths.get(dataDirPath, FAVORITES_FILE);
        
        // Load existing favorites
        loadFavorites();
    }
    
    /**
     * Toggles the favorite status of a song.
     * 
     * @param song The song to toggle
     * @return true if the song is now favorited, false if unfavorited
     */
    public boolean toggleFavorite(Song song) {
        if (song == null) {
            return false;
        }
        
        boolean isFavorite;
        if (favoriteSongIds.contains(song.getId())) {
            favoriteSongIds.remove(song.getId());
            song.setFavorite(false);
            isFavorite = false;
        } else {
            favoriteSongIds.add(song.getId());
            song.setFavorite(true);
            isFavorite = true;
        }
        
        // Save immediately for persistence
        saveFavorites();
        
        return isFavorite;
    }
    
    /**
     * Checks if a song is favorited.
     * 
     * @param songId The ID of the song to check
     * @return true if the song is favorited
     */
    public boolean isFavorite(long songId) {
        return favoriteSongIds.contains(songId);
    }
    
    /**
     * Gets all favorited songs from a list of songs.
     * 
     * @param allSongs List of all songs
     * @return List of favorited songs
     */
    public List<Song> getFavoriteSongs(List<Song> allSongs) {
        return allSongs.stream()
                .filter(song -> favoriteSongIds.contains(song.getId()))
                .collect(Collectors.toList());
    }
    
    /**
     * Updates the favorite status of all songs in the list based on stored favorites.
     * 
     * @param songs List of songs to update
     */
    public void updateFavoriteStatus(List<Song> songs) {
        for (Song song : songs) {
            song.setFavorite(favoriteSongIds.contains(song.getId()));
        }
    }
    
    /**
     * Adds a song to favorites.
     * 
     * @param song The song to add
     */
    public void addFavorite(Song song) {
        if (song != null) {
            favoriteSongIds.add(song.getId());
            song.setFavorite(true);
            saveFavorites();
        }
    }
    
    /**
     * Removes a song from favorites.
     * 
     * @param song The song to remove
     */
    public void removeFavorite(Song song) {
        if (song != null) {
            favoriteSongIds.remove(song.getId());
            song.setFavorite(false);
            saveFavorites();
        }
    }
    
    /**
     * Gets the count of favorite songs.
     * 
     * @return Number of favorited songs
     */
    public int getFavoriteCount() {
        return favoriteSongIds.size();
    }
    
    /**
     * Clears all favorites.
     */
    public void clearFavorites() {
        favoriteSongIds.clear();
        saveFavorites();
    }
    
    /**
     * Loads favorites from persistent storage.
     */
    private void loadFavorites() {
        try {
            if (Files.exists(favoritesPath)) {
                Set<Long> loaded = objectMapper.readValue(
                    favoritesPath.toFile(),
                    new TypeReference<Set<Long>>() {}
                );
                favoriteSongIds.clear();
                favoriteSongIds.addAll(loaded);
                System.out.println("Loaded " + favoriteSongIds.size() + " favorite songs");
            }
        } catch (IOException e) {
            System.err.println("Error loading favorites: " + e.getMessage());
        }
    }
    
    /**
     * Saves favorites to persistent storage.
     */
    private void saveFavorites() {
        try {
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(favoritesPath.toFile(), favoriteSongIds);
        } catch (IOException e) {
            System.err.println("Error saving favorites: " + e.getMessage());
        }
    }
    
    /**
     * Forces a save of the current favorites.
     */
    public void forceSave() {
        saveFavorites();
    }
} 