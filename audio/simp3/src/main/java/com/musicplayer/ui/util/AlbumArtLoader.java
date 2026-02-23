package com.musicplayer.ui.util;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.images.Artwork;

import com.musicplayer.data.models.Song;

import javafx.scene.image.Image;

/**
 * Utility class for loading album artwork from audio file metadata.
 * Provides asynchronous loading with fallback to default placeholder.
 */
public class AlbumArtLoader {
    
    private static final Logger LOGGER = Logger.getLogger(AlbumArtLoader.class.getName());
    
    // Default album art placeholder
    private static final Image DEFAULT_ALBUM_ART = new Image(
        AlbumArtLoader.class.getResourceAsStream("/images/icons/album_placeholder.png")
    );
    
    // Cache for recently loaded album art (optional enhancement)
    // private static final Map<String, Image> albumArtCache = new ConcurrentHashMap<>();
    // private static final int MAX_CACHE_SIZE = 50;
    
    /**
     * Load album artwork from a song's file metadata asynchronously.
     * 
     * @param song The song to load album art for
     * @return CompletableFuture containing the loaded image or default placeholder
     */
    public static CompletableFuture<Image> loadAlbumArt(Song song) {
        if (song == null || song.getFilePath() == null) {
            return CompletableFuture.completedFuture(DEFAULT_ALBUM_ART);
        }
        
        // Optional: Check cache first
        // String cacheKey = song.getArtist() + "-" + song.getAlbum();
        // Image cached = albumArtCache.get(cacheKey);
        // if (cached != null) {
        //     return CompletableFuture.completedFuture(cached);
        // }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                File audioFile = new File(song.getFilePath());
                if (!audioFile.exists()) {
                    LOGGER.log(Level.FINE, "Audio file not found: " + song.getFilePath());
                    return DEFAULT_ALBUM_ART;
                }
                
                // Read audio file metadata
                AudioFile f = AudioFileIO.read(audioFile);
                Tag tag = f.getTag();
                
                if (tag != null) {
                    Artwork artwork = tag.getFirstArtwork();
                    if (artwork != null) {
                        byte[] imageData = artwork.getBinaryData();
                        if (imageData != null && imageData.length > 0) {
                            Image albumArt = new Image(new ByteArrayInputStream(imageData));
                            
                            // Optional: Add to cache
                            // addToCache(cacheKey, albumArt);
                            
                            return albumArt;
                        }
                    }
                }
                
                LOGGER.log(Level.FINE, "No album art found in metadata for: " + song.getTitle());
                
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to load album art for: " + song.getFilePath(), e);
            }
            
            return DEFAULT_ALBUM_ART;
        });
    }
    
    /**
     * Load album artwork for an Album, checking for custom album art first.
     * 
     * @param album The album to load album art for
     * @return CompletableFuture that will complete with the loaded image
     */
    public static CompletableFuture<Image> loadAlbumArt(com.musicplayer.data.models.Album album) {
        if (album == null) {
            return CompletableFuture.completedFuture(DEFAULT_ALBUM_ART);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // First check if album has custom cover art path
                if (album.getCoverArtPath() != null && !album.getCoverArtPath().trim().isEmpty()) {
                    java.io.File customArtFile = new java.io.File(album.getCoverArtPath());
                    if (customArtFile.exists() && customArtFile.isFile()) {
                        Image customImage = new Image(customArtFile.toURI().toString(), 90, 90, true, true);
                        if (!customImage.isError()) {
                            return customImage;
                        }
                    }
                }
                
                // Fallback to loading from first song's metadata
                if (album.getSongs() != null && !album.getSongs().isEmpty()) {
                    com.musicplayer.data.models.Song firstSong = album.getSongs().get(0);
                    // Extract the album art loading logic from the original method
                    java.io.File audioFile = new java.io.File(firstSong.getFilePath());
                    if (audioFile.exists()) {
                        // Read audio file metadata
                        org.jaudiotagger.audio.AudioFile f = org.jaudiotagger.audio.AudioFileIO.read(audioFile);
                        org.jaudiotagger.tag.Tag tag = f.getTag();
                        
                        if (tag != null) {
                            org.jaudiotagger.tag.images.Artwork artwork = tag.getFirstArtwork();
                            if (artwork != null) {
                                byte[] imageData = artwork.getBinaryData();
                                if (imageData != null && imageData.length > 0) {
                                    return new Image(new java.io.ByteArrayInputStream(imageData));
                                }
                            }
                        }
                    }
                }
                
                // No songs or custom art available
                return DEFAULT_ALBUM_ART;
                
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error loading album art for album: " + album.getTitle(), e);
                return DEFAULT_ALBUM_ART;
            }
        });
    }

    /**
     * Load album artwork synchronously (blocking).
     * Use sparingly - prefer the async version for UI responsiveness.
     * 
     * @param song The song to load album art for
     * @return The loaded image or default placeholder
     */
    public static Image loadAlbumArtSync(Song song) {
        try {
            return loadAlbumArt(song).get();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error loading album art synchronously", e);
            return DEFAULT_ALBUM_ART;
        }
    }
    
    /**
     * Get the default album art placeholder image.
     * 
     * @return The default placeholder image
     */
    public static Image getDefaultAlbumArt() {
        return DEFAULT_ALBUM_ART;
    }
    
    /**
     * Optional: Add image to cache with size limit
     */
    // private static void addToCache(String key, Image image) {
    //     if (albumArtCache.size() >= MAX_CACHE_SIZE) {
    //         // Simple eviction - remove first entry
    //         Iterator<String> it = albumArtCache.keySet().iterator();
    //         if (it.hasNext()) {
    //             albumArtCache.remove(it.next());
    //         }
    //     }
    //     albumArtCache.put(key, image);
    // }
    
    /**
     * Optional: Clear the album art cache
     */
    // public static void clearCache() {
    //     albumArtCache.clear();
    // }
}