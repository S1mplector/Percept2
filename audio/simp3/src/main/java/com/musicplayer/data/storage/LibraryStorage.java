package com.musicplayer.data.storage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import com.musicplayer.data.models.Album;
import com.musicplayer.data.models.Artist;
import com.musicplayer.data.models.Playlist;
import com.musicplayer.data.models.Song;

/**
 * Interface for persisting and loading music library data.
 * This abstraction allows for different storage implementations
 * (JSON, XML, database, etc.) while maintaining a consistent API.
 */
public interface LibraryStorage {
    
    /**
     * Saves the list of songs to persistent storage.
     * 
     * @param songs The list of songs to save
     * @throws IOException If there's an error writing to storage
     */
    void saveSongs(List<Song> songs) throws IOException;
    
    /**
     * Loads the list of songs from persistent storage.
     * 
     * @return The list of saved songs, or empty list if none exist
     * @throws IOException If there's an error reading from storage
     */
    List<Song> loadSongs() throws IOException;
    
    /**
     * Saves the list of albums to persistent storage.
     * 
     * @param albums The list of albums to save
     * @throws IOException If there's an error writing to storage
     */
    void saveAlbums(List<Album> albums) throws IOException;
    
    /**
     * Loads the list of albums from persistent storage.
     * 
     * @return The list of saved albums, or empty list if none exist
     * @throws IOException If there's an error reading from storage
     */
    List<Album> loadAlbums() throws IOException;
    
    /**
     * Saves the list of artists to persistent storage.
     * 
     * @param artists The list of artists to save
     * @throws IOException If there's an error writing to storage
     */
    void saveArtists(List<Artist> artists) throws IOException;
    
    /**
     * Loads the list of artists from persistent storage.
     * 
     * @return The list of saved artists, or empty list if none exist
     * @throws IOException If there's an error reading from storage
     */
    List<Artist> loadArtists() throws IOException;
    
    /**
     * Saves the list of playlists to persistent storage.
     * 
     * @param playlists The list of playlists to save
     * @throws IOException If there's an error writing to storage
     */
    void savePlaylists(List<Playlist> playlists) throws IOException;
    
    /**
     * Loads the list of playlists from persistent storage.
     * 
     * @return The list of saved playlists, or empty list if none exist
     * @throws IOException If there's an error reading from storage
     */
    List<Playlist> loadPlaylists() throws IOException;
    
    /**
     * Gets the path to the data directory where files are stored.
     * 
     * @return The path to the data directory
     */
    Path getDataDirectory();
    
    /**
     * Checks if there is existing data in storage.
     * 
     * @return true if data exists, false otherwise
     */
    boolean hasExistingData();
}
