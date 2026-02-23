package com.musicplayer.services;

import java.util.List;
import java.util.Set;

import com.musicplayer.core.library.InMemoryLibraryEngine;
import com.musicplayer.core.library.LibraryEngine;
import com.musicplayer.data.models.Album;
import com.musicplayer.data.models.Artist;
import com.musicplayer.data.models.Song;
import com.musicplayer.data.repositories.SongRepository;

/**
 * Enhanced LibraryService that uses both repository storage and LibraryEngine for advanced operations.
 * Provides a bridge between the core library engine and the data layer.
 */
public class LibraryService {
    private final SongRepository songRepository;
    private final LibraryEngine libraryEngine;

    public LibraryService(SongRepository songRepository) {
        this.songRepository = songRepository;
        this.libraryEngine = new InMemoryLibraryEngine();
        
        // Initialize library engine with existing songs
        List<Song> existingSongs = songRepository.findAll();
        if (!existingSongs.isEmpty()) {
            libraryEngine.addSongs(existingSongs);
        }
    }

    // Basic CRUD operations
    public void addSong(Song song) {
        songRepository.save(song);
        libraryEngine.addSongs(List.of(song));
    }
    
    public void addSongs(List<Song> songs) {
        for (Song song : songs) {
            songRepository.save(song);
        }
        libraryEngine.addSongs(songs);
    }

    public List<Song> getAllSongs() {
        return libraryEngine.getAllSongs();
    }

    public Song getSongById(long id) {
        return songRepository.findById(id);
    }

    public void removeSong(long id) {
        Song song = songRepository.findById(id);
        if (song != null) {
            songRepository.delete(id);
            libraryEngine.removeSong(song);
        }
    }
    
    public void removeSong(Song song) {
        if (song != null) {
            songRepository.delete(song.getId());
            libraryEngine.removeSong(song);
        }
    }
    
    // Advanced library operations using LibraryEngine
    public List<Album> getAllAlbums() {
        return libraryEngine.getAllAlbums();
    }
    
    public List<Artist> getAllArtists() {
        return libraryEngine.getAllArtists();
    }
    
    public Set<String> getAllGenres() {
        return libraryEngine.getAllGenres();
    }
    
    // Search operations
    public List<Song> searchSongsByTitle(String query) {
        return libraryEngine.searchSongsByTitle(query);
    }
    
    public List<Song> searchSongsByArtist(String query) {
        return libraryEngine.searchSongsByArtist(query);
    }
    
    public List<Song> searchSongsByAlbum(String query) {
        return libraryEngine.searchSongsByAlbum(query);
    }
    
    public List<Song> getSongsByArtist(Artist artist) {
        return libraryEngine.getSongsByArtist(artist);
    }
    
    public List<Song> getSongsByAlbum(Album album) {
        return libraryEngine.getSongsByAlbum(album);
    }
    
    public List<Song> getSongsByGenre(String genre) {
        return libraryEngine.getSongsByGenre(genre);
    }
    
    // Library statistics
    public int getSongCount() {
        return libraryEngine.getSongCount();
    }
    
    public int getAlbumCount() {
        return libraryEngine.getAlbumCount();
    }
    
    public int getArtistCount() {
        return libraryEngine.getArtistCount();
    }
    
    // Library management
    public void clearLibrary() {
        libraryEngine.clearLibrary();
        // Note: This doesn't clear the repository - only the search engine
        // For full clearing, you'd need to clear the repository too
    }
    
    public void rebuildIndexes() {
        libraryEngine.rebuildIndexes();
    }
    
    public void refreshLibrary() {
        // Reload library from repository
        libraryEngine.clearLibrary();
        List<Song> allSongs = songRepository.findAll();
        if (!allSongs.isEmpty()) {
            libraryEngine.addSongs(allSongs);
        }
    }
}
