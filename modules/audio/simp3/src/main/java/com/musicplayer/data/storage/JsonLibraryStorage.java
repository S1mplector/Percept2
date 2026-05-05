package com.musicplayer.data.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.musicplayer.data.models.Song;
import com.musicplayer.data.models.Album;
import com.musicplayer.data.models.Artist;
import com.musicplayer.data.models.Playlist;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.ArrayList;

/**
 * JSON-based storage implementation for persisting music library data.
 * This class handles saving and loading songs, albums, artists, and playlists
 * to/from JSON files in the user's data directory.
 */
public class JsonLibraryStorage implements LibraryStorage {
    
    private static final String APP_DATA_DIR = "SiMP3";
    private static final String SONGS_FILE = "songs.json";
    private static final String ALBUMS_FILE = "albums.json";
    private static final String ARTISTS_FILE = "artists.json";
    private static final String PLAYLISTS_FILE = "playlists.json";
    
    private final ObjectMapper objectMapper;
    private final Path dataDirectory;
    
    public JsonLibraryStorage() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        
        // Create data directory in user's AppData/Local or equivalent
        String userHome = System.getProperty("user.home");
        String osName = System.getProperty("os.name").toLowerCase();
        
        Path baseDir;
        if (osName.contains("win")) {
            String appData = System.getenv("LOCALAPPDATA");
            if (appData != null) {
                baseDir = Paths.get(appData);
            } else {
                baseDir = Paths.get(userHome, "AppData", "Local");
            }
        } else if (osName.contains("mac")) {
            baseDir = Paths.get(userHome, "Library", "Application Support");
        } else {
            // Linux and other Unix-like systems
            String xdgDataHome = System.getenv("XDG_DATA_HOME");
            if (xdgDataHome != null) {
                baseDir = Paths.get(xdgDataHome);
            } else {
                baseDir = Paths.get(userHome, ".local", "share");
            }
        }
        
        this.dataDirectory = baseDir.resolve(APP_DATA_DIR);
        
        // Create the directory if it doesn't exist
        try {
            Files.createDirectories(dataDirectory);
        } catch (IOException e) {
            System.err.println("Failed to create data directory: " + e.getMessage());
            throw new RuntimeException("Cannot create application data directory", e);
        }
    }
    
    @Override
    public void saveSongs(List<Song> songs) throws IOException {
        Path target = dataDirectory.resolve(SONGS_FILE);
        writeJsonAtomically(target, songs);
    }
    
    @Override
    public List<Song> loadSongs() throws IOException {
        File songsFile = dataDirectory.resolve(SONGS_FILE).toFile();
        if (!songsFile.exists()) {
            return new ArrayList<>();
        }
        
        try {
            return objectMapper.readValue(songsFile, new TypeReference<List<Song>>() {});
        } catch (IOException e) {
            System.err.println("Failed to load songs from storage: " + e.getMessage());
            // Return empty list if file is corrupted
            return new ArrayList<>();
        }
    }
    
    @Override
    public void saveAlbums(List<Album> albums) throws IOException {
        Path target = dataDirectory.resolve(ALBUMS_FILE);
        writeJsonAtomically(target, albums);
    }
    
    @Override
    public List<Album> loadAlbums() throws IOException {
        File albumsFile = dataDirectory.resolve(ALBUMS_FILE).toFile();
        if (!albumsFile.exists()) {
            return new ArrayList<>();
        }
        
        try {
            return objectMapper.readValue(albumsFile, new TypeReference<List<Album>>() {});
        } catch (IOException e) {
            System.err.println("Failed to load albums from storage: " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public void saveArtists(List<Artist> artists) throws IOException {
        Path target = dataDirectory.resolve(ARTISTS_FILE);
        writeJsonAtomically(target, artists);
    }
    
    @Override
    public List<Artist> loadArtists() throws IOException {
        File artistsFile = dataDirectory.resolve(ARTISTS_FILE).toFile();
        if (!artistsFile.exists()) {
            return new ArrayList<>();
        }
        
        try {
            return objectMapper.readValue(artistsFile, new TypeReference<List<Artist>>() {});
        } catch (IOException e) {
            System.err.println("Failed to load artists from storage: " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public void savePlaylists(List<Playlist> playlists) throws IOException {
        Path target = dataDirectory.resolve(PLAYLISTS_FILE);
        writeJsonAtomically(target, playlists);
    }
    
    @Override
    public List<Playlist> loadPlaylists() throws IOException {
        File playlistsFile = dataDirectory.resolve(PLAYLISTS_FILE).toFile();
        if (!playlistsFile.exists()) {
            return new ArrayList<>();
        }
        
        try {
            return objectMapper.readValue(playlistsFile, new TypeReference<List<Playlist>>() {});
        } catch (IOException e) {
            System.err.println("Failed to load playlists from storage: " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public Path getDataDirectory() {
        return dataDirectory;
    }
    
    @Override
    public boolean hasExistingData() {
        return dataDirectory.resolve(SONGS_FILE).toFile().exists();
    }

    private void writeJsonAtomically(Path target, Object value) throws IOException {
        Files.createDirectories(target.getParent());
        Path temp = target.resolveSibling(target.getFileName().toString() + ".tmp");
        objectMapper.writeValue(temp.toFile(), value);
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
