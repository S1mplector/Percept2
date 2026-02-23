package com.musicplayer.services;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.musicplayer.data.models.Playlist;
import com.musicplayer.data.models.Song;
import com.musicplayer.data.repositories.PersistentPlaylistRepository;
import com.musicplayer.data.repositories.PlaylistRepository;

/**
 * Service class for managing playlists, including creation, deletion, and modification.
 * This class encapsulates all playlist operations and provides a clean interface
 * for the UI controllers.
 */
public class PlaylistManager {
    
    private final PlaylistRepository playlistRepository;
    
    // Callback for notifying when playlists change
    private Consumer<List<Playlist>> playlistUpdateCallback;
    
    public PlaylistManager(PlaylistRepository playlistRepository) {
        this.playlistRepository = playlistRepository;
        
        // If using persistent storage, log existing data
        if (playlistRepository instanceof PersistentPlaylistRepository) {
            PersistentPlaylistRepository persistentRepo = (PersistentPlaylistRepository) playlistRepository;
            List<Playlist> existingPlaylists = persistentRepo.findAll();
            if (!existingPlaylists.isEmpty()) {
                System.out.println("Found existing playlists: " + existingPlaylists.size());
            }
        }
    }
    
    /**
     * Initializes the playlist manager by loading existing data and notifying the callback.
     * Should be called after the UI is ready to receive updates.
     */
    public void initializePlaylists() {
        List<Playlist> existingPlaylists = getAllPlaylists();
        if (!existingPlaylists.isEmpty() && playlistUpdateCallback != null) {
            System.out.println("Initializing UI with " + existingPlaylists.size() + " existing playlists");
            playlistUpdateCallback.accept(existingPlaylists);
        }
    }
    
    /**
     * Sets a callback to be notified when playlists are updated.
     * 
     * @param callback Consumer that receives the updated list of playlists
     */
    public void setPlaylistUpdateCallback(Consumer<List<Playlist>> callback) {
        this.playlistUpdateCallback = callback;
    }
    
    /**
     * Creates a new playlist with the given name.
     * 
     * @param name The name of the new playlist
     * @return The created playlist
     */
    public Playlist createPlaylist(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Playlist name cannot be empty");
        }
        
        // Check if a playlist with this name already exists
        List<Playlist> existingPlaylists = getAllPlaylists();
        for (Playlist playlist : existingPlaylists) {
            if (playlist.getName().equals(name.trim())) {
                throw new IllegalArgumentException("A playlist with this name already exists");
            }
        }
        
        Playlist newPlaylist = new Playlist();
        newPlaylist.setName(name.trim());
        playlistRepository.save(newPlaylist);
        
        System.out.println("Created new playlist: " + name);
        
        // Notify callback if set
        notifyPlaylistUpdate();
        
        return newPlaylist;
    }
    
    /**
     * Renames an existing playlist.
     * 
     * @param playlistId The ID of the playlist to rename
     * @param newName The new name for the playlist
     * @return true if the playlist was renamed successfully, false otherwise
     */
    public boolean renamePlaylist(long playlistId, String newName) {
        if (newName == null || newName.trim().isEmpty()) {
            return false;
        }
        
        Playlist playlist = playlistRepository.findById(playlistId);
        if (playlist == null) {
            return false;
        }
        
        // Check if a playlist with this name already exists (excluding current playlist)
        List<Playlist> existingPlaylists = getAllPlaylists();
        for (Playlist existingPlaylist : existingPlaylists) {
            if (existingPlaylist.getId() != playlistId && existingPlaylist.getName().equals(newName.trim())) {
                return false; // Name already exists
            }
        }
        
        String oldName = playlist.getName();
        playlist.setName(newName.trim());
        playlistRepository.save(playlist);
        
        System.out.println("Renamed playlist '" + oldName + "' to '" + newName + "'");
        
        // Notify callback if set
        notifyPlaylistUpdate();
        
        return true;
    }
    
    /**
     * Deletes a playlist by its ID.
     * 
     * @param playlistId The ID of the playlist to delete
     * @return true if the playlist was deleted successfully, false otherwise
     */
    public boolean deletePlaylist(long playlistId) {
        Playlist playlist = playlistRepository.findById(playlistId);
        if (playlist == null) {
            return false;
        }
        
        String playlistName = playlist.getName();
        playlistRepository.delete(playlistId);
        
        System.out.println("Deleted playlist: " + playlistName);
        
        // Notify callback if set
        notifyPlaylistUpdate();
        
        return true;
    }
    
    /**
     * Adds a song to a playlist.
     * 
     * @param playlistId The ID of the playlist
     * @param song The song to add
     * @return true if the song was added successfully, false otherwise
     */
    public boolean addSongToPlaylist(long playlistId, Song song) {
        Playlist playlist = playlistRepository.findById(playlistId);
        if (playlist == null || song == null) {
            return false;
        }
        
        // Check if song is already in the playlist
        if (playlist.getSongs().contains(song)) {
            return false; // Song already in playlist
        }
        
        playlist.addSong(song);
        playlistRepository.save(playlist);
        
        System.out.println("Added song '" + song.getTitle() + "' to playlist '" + playlist.getName() + "'");
        
        // Notify callback if set
        notifyPlaylistUpdate();
        
        return true;
    }
    
    /**
     * Removes a song from a playlist.
     * 
     * @param playlistId The ID of the playlist
     * @param song The song to remove
     * @return true if the song was removed successfully, false otherwise
     */
    public boolean removeSongFromPlaylist(long playlistId, Song song) {
        Playlist playlist = playlistRepository.findById(playlistId);
        if (playlist == null || song == null) {
            return false;
        }
        
        boolean removed = playlist.getSongs().remove(song);
        if (removed) {
            playlistRepository.save(playlist);
            System.out.println("Removed song '" + song.getTitle() + "' from playlist '" + playlist.getName() + "'");
            
            // Notify callback if set
            notifyPlaylistUpdate();
        }
        
        return removed;
    }
    
    /**
     * Gets all playlists.
     * 
     * @return List of all playlists
     */
    public List<Playlist> getAllPlaylists() {
        return playlistRepository.findAll();
    }
    
    /**
     * Gets a playlist by its ID.
     * 
     * @param id The playlist ID
     * @return The playlist, or null if not found
     */
    public Playlist getPlaylistById(long id) {
        return playlistRepository.findById(id);
    }
    
    /**
     * Gets a playlist by its name.
     * 
     * @param name The playlist name
     * @return The playlist, or null if not found
     */
    public Playlist getPlaylistByName(String name) {
        if (playlistRepository instanceof PersistentPlaylistRepository) {
            return ((PersistentPlaylistRepository) playlistRepository).findByName(name);
        }
        
        // Fallback for non-persistent repositories
        return getAllPlaylists().stream()
                .filter(playlist -> playlist.getName().equals(name))
                .findFirst()
                .orElse(null);
    }
    
    /**
     * Gets the total number of playlists.
     * 
     * @return Number of playlists
     */
    public int getPlaylistCount() {
        return getAllPlaylists().size();
    }
    
    /**
     * Forces a save of all current playlist data to persistent storage (if supported).
     * This is useful for ensuring data is persisted before application shutdown.
     */
    public void forceSave() {
        if (playlistRepository instanceof PersistentPlaylistRepository) {
            ((PersistentPlaylistRepository) playlistRepository).forceSave();
        }
    }
    
    /**
     * Checks if there are existing playlists.
     * 
     * @return true if there are playlists, false otherwise
     */
    public boolean hasExistingPlaylists() {
        return getPlaylistCount() > 0;
    }
    
    /**
     * Clears all playlists.
     */
    public void clearAllPlaylists() {
        List<Playlist> allPlaylists = getAllPlaylists();
        for (Playlist playlist : allPlaylists) {
            playlistRepository.delete(playlist.getId());
        }
        
        System.out.println("Cleared all playlists");
        
        // Notify callback if set
        notifyPlaylistUpdate();
    }
    
    /**
     * Helper method to notify the callback about playlist updates.
     */
    private void notifyPlaylistUpdate() {
        if (playlistUpdateCallback != null) {
            // Run callback on JavaFX Application Thread
            javafx.application.Platform.runLater(() -> {
                playlistUpdateCallback.accept(getAllPlaylists());
            });
        }
    }

    /**
     * Updates song order in a playlist.
     * @param playlistId playlist id
     * @param newSongs new ordered list
     */
    public void updatePlaylistSongs(long playlistId, List<Song> newSongs) {
        Playlist playlist = playlistRepository.findById(playlistId);
        if (playlist == null) return;
        playlist.setSongs(new ArrayList<>(newSongs));
        playlistRepository.save(playlist);
        notifyPlaylistUpdate();
    }
}
