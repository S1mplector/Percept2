package com.musicplayer.core.library;

import java.util.List;
import java.util.Set;

import com.musicplayer.data.models.Album;
import com.musicplayer.data.models.Artist;
import com.musicplayer.data.models.Song;

/**
 * Interface defining core library management functionality.
 * Provides abstraction for library operations independent of storage implementation.
 */
public interface LibraryEngine {
    
    /**
     * Adds songs to the library.
     * 
     * @param songs List of songs to add
     */
    void addSongs(List<Song> songs);
    
    /**
     * Removes a song from the library.
     * 
     * @param song Song to remove
     * @return true if song was removed, false otherwise
     */
    boolean removeSong(Song song);
    
    /**
     * Gets all songs in the library.
     * 
     * @return List of all songs
     */
    List<Song> getAllSongs();
    
    /**
     * Gets all albums in the library.
     * 
     * @return List of all albums
     */
    List<Album> getAllAlbums();
    
    /**
     * Gets all artists in the library.
     * 
     * @return List of all artists
     */
    List<Artist> getAllArtists();
    
    /**
     * Searches for songs by title.
     * 
     * @param query Search query
     * @return List of matching songs
     */
    List<Song> searchSongsByTitle(String query);
    
    /**
     * Searches for songs by artist.
     * 
     * @param query Search query
     * @return List of matching songs
     */
    List<Song> searchSongsByArtist(String query);
    
    /**
     * Searches for songs by album.
     * 
     * @param query Search query
     * @return List of matching songs
     */
    List<Song> searchSongsByAlbum(String query);
    
    /**
     * Gets songs by a specific artist.
     * 
     * @param artist Artist to search for
     * @return List of songs by the artist
     */
    List<Song> getSongsByArtist(Artist artist);
    
    /**
     * Gets songs from a specific album.
     * 
     * @param album Album to search for
     * @return List of songs from the album
     */
    List<Song> getSongsByAlbum(Album album);
    
    /**
     * Gets songs by genre.
     * 
     * @param genre Genre to search for
     * @return List of songs in the genre
     */
    List<Song> getSongsByGenre(String genre);
    
    /**
     * Gets all genres in the library.
     * 
     * @return Set of all genres
     */
    Set<String> getAllGenres();
    
    /**
     * Clears all songs from the library.
     */
    void clearLibrary();
    
    /**
     * Gets the total number of songs in the library.
     * 
     * @return Number of songs
     */
    int getSongCount();
    
    /**
     * Gets the total number of albums in the library.
     * 
     * @return Number of albums
     */
    int getAlbumCount();
    
    /**
     * Gets the total number of artists in the library.
     * 
     * @return Number of artists
     */
    int getArtistCount();
    
    /**
     * Rebuilds the library indexes for improved search performance.
     */
    void rebuildIndexes();
}
