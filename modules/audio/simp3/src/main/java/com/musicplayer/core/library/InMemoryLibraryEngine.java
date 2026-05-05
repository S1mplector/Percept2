package com.musicplayer.core.library;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.musicplayer.data.models.Album;
import com.musicplayer.data.models.Artist;
import com.musicplayer.data.models.Song;

/**
 * In-memory implementation of LibraryEngine.
 * Provides fast search and indexing capabilities using HashMap-based indexes.
 */
public class InMemoryLibraryEngine implements LibraryEngine {
    
    private final Set<Song> songs = new LinkedHashSet<>();
    private final Map<String, Set<Song>> titleIndex = new HashMap<>();
    private final Map<String, Set<Song>> artistIndex = new HashMap<>();
    private final Map<String, Set<Song>> albumIndex = new HashMap<>();
    private final Map<String, Set<Song>> genreIndex = new HashMap<>();
    
    @Override
    public void addSongs(List<Song> songsToAdd) {
        for (Song song : songsToAdd) {
            if (songs.add(song)) {
                indexSong(song);
            }
        }
    }
    
    @Override
    public boolean removeSong(Song song) {
        if (songs.remove(song)) {
            removeFromIndexes(song);
            return true;
        }
        return false;
    }
    
    @Override
    public List<Song> getAllSongs() {
        return new ArrayList<>(songs);
    }
    
    @Override
    public List<Album> getAllAlbums() {
        Map<String, Album> albumMap = new HashMap<>();
        for (Song song : songs) {
            if (song.getAlbum() != null && !song.getAlbum().trim().isEmpty()) {
                String artist = song.getArtist() != null ? song.getArtist() : "";
                String key = (song.getAlbum() + "||" + artist).toLowerCase();
                Album album = albumMap.get(key);
                if (album == null) {
                    album = new Album();
                    album.setTitle(song.getAlbum());
                    album.setArtistName(artist);
                    albumMap.put(key, album);
                }
                album.addSong(song);
            }
        }
        return new ArrayList<>(albumMap.values());
    }
    
    @Override
    public List<Artist> getAllArtists() {
        Map<String, Artist> artistMap = new HashMap<>();
        
        for (Song song : songs) {
            if (song.getArtist() != null && !song.getArtist().trim().isEmpty()) {
                String artistKey = song.getArtist().toLowerCase();
                if (!artistMap.containsKey(artistKey)) {
                    Artist artist = new Artist();
                    artist.setName(song.getArtist());
                    artistMap.put(artistKey, artist);
                }
            }
        }
        
        return new ArrayList<>(artistMap.values());
    }
    
    @Override
    public List<Song> searchSongsByTitle(String query) {
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>();
        }
        
        String lowerQuery = query.toLowerCase();
        return songs.stream()
                .filter(song -> song.getTitle() != null && 
                               song.getTitle().toLowerCase().contains(lowerQuery))
                .collect(Collectors.toList());
    }
    
    @Override
    public List<Song> searchSongsByArtist(String query) {
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>();
        }
        
        String lowerQuery = query.toLowerCase();
        return artistIndex.entrySet().stream()
                .filter(entry -> entry.getKey().contains(lowerQuery))
                .flatMap(entry -> entry.getValue().stream())
                .collect(Collectors.toList());
    }
    
    @Override
    public List<Song> searchSongsByAlbum(String query) {
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>();
        }
        
        String lowerQuery = query.toLowerCase();
        return albumIndex.entrySet().stream()
                .filter(entry -> entry.getKey().contains(lowerQuery))
                .flatMap(entry -> entry.getValue().stream())
                .collect(Collectors.toList());
    }
    
    @Override
    public List<Song> getSongsByArtist(Artist artist) {
        if (artist == null || artist.getName() == null) {
            return new ArrayList<>();
        }
        
        String artistKey = artist.getName().toLowerCase();
        Set<Song> artistSongs = artistIndex.get(artistKey);
        return artistSongs != null ? new ArrayList<>(artistSongs) : new ArrayList<>();
    }
    
    @Override
    public List<Song> getSongsByAlbum(Album album) {
        if (album == null || album.getTitle() == null) {
            return new ArrayList<>();
        }
        
        String albumKey = album.getTitle().toLowerCase();
        Set<Song> albumSongs = albumIndex.get(albumKey);
        if (albumSongs == null) return new ArrayList<>();
        String artistFilter = album.getArtistName();
        if (artistFilter == null || artistFilter.isBlank()) {
            return new ArrayList<>(albumSongs);
        }
        String artistKey = artistFilter.toLowerCase();
        List<Song> filtered = new ArrayList<>();
        for (Song s : albumSongs) {
            if (s.getArtist() != null && s.getArtist().toLowerCase().equals(artistKey)) {
                filtered.add(s);
            }
        }
        return filtered;
    }
    
    @Override
    public List<Song> getSongsByGenre(String genre) {
        if (genre == null || genre.trim().isEmpty()) {
            return new ArrayList<>();
        }
        
        String genreKey = genre.toLowerCase();
        Set<Song> genreSongs = genreIndex.get(genreKey);
        return genreSongs != null ? new ArrayList<>(genreSongs) : new ArrayList<>();
    }
    
    @Override
    public Set<String> getAllGenres() {
        return genreIndex.keySet().stream()
                .map(this::capitalizeFirst)
                .collect(Collectors.toSet());
    }
    
    @Override
    public void clearLibrary() {
        songs.clear();
        titleIndex.clear();
        artistIndex.clear();
        albumIndex.clear();
        genreIndex.clear();
    }
    
    @Override
    public int getSongCount() {
        return songs.size();
    }
    
    @Override
    public int getAlbumCount() {
        return getAllAlbums().size();
    }
    
    @Override
    public int getArtistCount() {
        return artistIndex.size();
    }
    
    @Override
    public void rebuildIndexes() {
        titleIndex.clear();
        artistIndex.clear();
        albumIndex.clear();
        genreIndex.clear();
        
        for (Song song : songs) {
            indexSong(song);
        }
    }
    
    /**
     * Indexes a song for fast searching.
     * 
     * @param song Song to index
     */
    private void indexSong(Song song) {
        // Index by title
        if (song.getTitle() != null) {
            String titleKey = song.getTitle().toLowerCase();
            titleIndex.computeIfAbsent(titleKey, k -> new HashSet<>()).add(song);
        }
        
        // Index by artist
        if (song.getArtist() != null) {
            String artistKey = song.getArtist().toLowerCase();
            artistIndex.computeIfAbsent(artistKey, k -> new HashSet<>()).add(song);
        }
        
        // Index by album
        if (song.getAlbum() != null) {
            String albumKey = song.getAlbum().toLowerCase();
            albumIndex.computeIfAbsent(albumKey, k -> new HashSet<>()).add(song);
        }
        
        // Index by genre
        if (song.getGenre() != null) {
            String genreKey = song.getGenre().toLowerCase();
            genreIndex.computeIfAbsent(genreKey, k -> new HashSet<>()).add(song);
        }
    }
    
    /**
     * Removes a song from all indexes.
     * 
     * @param song Song to remove from indexes
     */
    private void removeFromIndexes(Song song) {
        // Remove from title index
        if (song.getTitle() != null) {
            String titleKey = song.getTitle().toLowerCase();
            Set<Song> titleSongs = titleIndex.get(titleKey);
            if (titleSongs != null) {
                titleSongs.remove(song);
                if (titleSongs.isEmpty()) {
                    titleIndex.remove(titleKey);
                }
            }
        }
        
        // Remove from artist index
        if (song.getArtist() != null) {
            String artistKey = song.getArtist().toLowerCase();
            Set<Song> artistSongs = artistIndex.get(artistKey);
            if (artistSongs != null) {
                artistSongs.remove(song);
                if (artistSongs.isEmpty()) {
                    artistIndex.remove(artistKey);
                }
            }
        }
        
        // Remove from album index
        if (song.getAlbum() != null) {
            String albumKey = song.getAlbum().toLowerCase();
            Set<Song> albumSongs = albumIndex.get(albumKey);
            if (albumSongs != null) {
                albumSongs.remove(song);
                if (albumSongs.isEmpty()) {
                    albumIndex.remove(albumKey);
                }
            }
        }
        
        // Remove from genre index
        if (song.getGenre() != null) {
            String genreKey = song.getGenre().toLowerCase();
            Set<Song> genreSongs = genreIndex.get(genreKey);
            if (genreSongs != null) {
                genreSongs.remove(song);
                if (genreSongs.isEmpty()) {
                    genreIndex.remove(genreKey);
                }
            }
        }
    }
    
    /**
     * Capitalizes the first letter of a string.
     * 
     * @param str String to capitalize
     * @return Capitalized string
     */
    private String capitalizeFirst(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
