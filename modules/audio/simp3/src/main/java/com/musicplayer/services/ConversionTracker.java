package com.musicplayer.services;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.musicplayer.data.models.Song;

/**
 * Tracks audio conversion history to avoid repeatedly prompting users
 * to convert albums that have already been converted.
 */
public class ConversionTracker {
    
    private static final Logger LOGGER = Logger.getLogger(ConversionTracker.class.getName());
    private static final String CONVERSION_HISTORY_FILE = "conversion-history.json";
    
    private final ObjectMapper objectMapper;
    private final Path historyFilePath;
    private Map<String, ConversionRecord> conversionHistory;
    
    public ConversionTracker() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.historyFilePath = Paths.get(CONVERSION_HISTORY_FILE);
        this.conversionHistory = new HashMap<>();
        loadConversionHistory();
    }
    
    /**
     * Record representing a completed conversion.
     */
    public static class ConversionRecord {
        private String albumKey;
        private String originalFormat;
        private String convertedFormat;
        private String originalFilePath;
        private String convertedFilePath;
        
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime conversionDate;
        
        public ConversionRecord() {}
        
        public ConversionRecord(String albumKey, String originalFormat, String convertedFormat,
                              String originalFilePath, String convertedFilePath) {
            this.albumKey = albumKey;
            this.originalFormat = originalFormat;
            this.convertedFormat = convertedFormat;
            this.originalFilePath = originalFilePath;
            this.convertedFilePath = convertedFilePath;
            this.conversionDate = LocalDateTime.now();
        }
        
        // Getters and setters
        public String getAlbumKey() { return albumKey; }
        public void setAlbumKey(String albumKey) { this.albumKey = albumKey; }
        
        public String getOriginalFormat() { return originalFormat; }
        public void setOriginalFormat(String originalFormat) { this.originalFormat = originalFormat; }
        
        public String getConvertedFormat() { return convertedFormat; }
        public void setConvertedFormat(String convertedFormat) { this.convertedFormat = convertedFormat; }
        
        public String getOriginalFilePath() { return originalFilePath; }
        public void setOriginalFilePath(String originalFilePath) { this.originalFilePath = originalFilePath; }
        
        public String getConvertedFilePath() { return convertedFilePath; }
        public void setConvertedFilePath(String convertedFilePath) { this.convertedFilePath = convertedFilePath; }
        
        public LocalDateTime getConversionDate() { return conversionDate; }
        public void setConversionDate(LocalDateTime conversionDate) { this.conversionDate = conversionDate; }
        
        @Override
        public String toString() {
            return String.format("ConversionRecord{albumKey='%s', %s->%s, date=%s}", 
                albumKey, originalFormat, convertedFormat, 
                conversionDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        }
    }
    
    /**
     * Generate a unique key for an album based on artist and album name.
     */
    private String generateAlbumKey(Song song) {
        String artist = song.getArtist() != null ? song.getArtist().trim() : "Unknown Artist";
        String album = song.getAlbum() != null ? song.getAlbum().trim() : "Unknown Album";
        return (artist + " - " + album).toLowerCase().replaceAll("[^a-z0-9\\s-]", "");
    }
    
    /**
     * Generate album key from file path by extracting directory name.
     */
    private String generateAlbumKeyFromPath(File file) {
        File parentDir = file.getParentFile();
        if (parentDir != null) {
            return parentDir.getName().toLowerCase().replaceAll("[^a-z0-9\\s-]", "");
        }
        return file.getName().toLowerCase().replaceAll("[^a-z0-9\\s-]", "");
    }
    
    /**
     * Check if an album has already been converted.
     */
    public boolean isAlbumConverted(Song song) {
        String albumKey = generateAlbumKey(song);
        return conversionHistory.containsKey(albumKey);
    }
    
    /**
     * Check if a file's album has already been converted.
     */
    public boolean isAlbumConverted(File file) {
        String albumKey = generateAlbumKeyFromPath(file);
        ConversionRecord record = conversionHistory.get(albumKey);

        if (record == null) {
            return false;
        }

        // Verify that the converted file still exists on disk; if not, clear record
        if (record.getConvertedFilePath() == null || !(new File(record.getConvertedFilePath()).exists())) {
            // Converted file missing – purge stale record
            conversionHistory.remove(albumKey);
            saveConversionHistory();
            return false;
        }
        return true;
    }
    
    /**
     * Record a completed conversion.
     */
    public void recordConversion(File originalFile, File convertedFile, String originalFormat, String convertedFormat) {
        String albumKey = generateAlbumKeyFromPath(originalFile);
        
        ConversionRecord record = new ConversionRecord(
            albumKey, 
            originalFormat, 
            convertedFormat,
            originalFile.getAbsolutePath(),
            convertedFile.getAbsolutePath()
        );
        
        conversionHistory.put(albumKey, record);
        saveConversionHistory();
        
        LOGGER.info("Recorded conversion: " + record);
    }
    
    /**
     * Record conversion for a song.
     */
    public void recordConversion(Song originalSong, File convertedFile, String convertedFormat) {
        String albumKey = generateAlbumKey(originalSong);
        String originalFormat = getFileExtension(originalSong.getFilePath());
        
        ConversionRecord record = new ConversionRecord(
            albumKey,
            originalFormat,
            convertedFormat,
            originalSong.getFilePath(),
            convertedFile.getAbsolutePath()
        );
        
        conversionHistory.put(albumKey, record);
        saveConversionHistory();
        
        LOGGER.info("Recorded conversion: " + record);
    }
    
    /**
     * Filter out files from albums that have already been converted.
     */
    public List<File> filterUnconvertedFiles(List<File> files) {
        return files.stream()
            .filter(file -> !isAlbumConverted(file))
            .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * Filter out songs from albums that have already been converted.
     */
    public List<Song> filterUnconvertedSongs(List<Song> songs) {
        return songs.stream()
            .filter(song -> !isAlbumConverted(song))
            .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * Get conversion record for an album.
     */
    public ConversionRecord getConversionRecord(Song song) {
        String albumKey = generateAlbumKey(song);
        return conversionHistory.get(albumKey);
    }
    
    /**
     * Remove conversion record (for manual re-conversion).
     */
    public void removeConversionRecord(Song song) {
        String albumKey = generateAlbumKey(song);
        conversionHistory.remove(albumKey);
        saveConversionHistory();
        LOGGER.info("Removed conversion record for album: " + albumKey);
    }
    
    /**
     * Get all conversion records.
     */
    public Map<String, ConversionRecord> getAllConversionRecords() {
        return new HashMap<>(conversionHistory);
    }
    
    /**
     * Clear all conversion history.
     */
    public void clearHistory() {
        conversionHistory.clear();
        saveConversionHistory();
        LOGGER.info("Cleared all conversion history");
    }
    
    /**
     * Load conversion history from JSON file.
     */
    private void loadConversionHistory() {
        try {
            if (Files.exists(historyFilePath)) {
                String json = Files.readString(historyFilePath);
                TypeReference<Map<String, ConversionRecord>> typeRef = new TypeReference<Map<String, ConversionRecord>>() {};
                conversionHistory = objectMapper.readValue(json, typeRef);
                LOGGER.info("Loaded " + conversionHistory.size() + " conversion records");
            } else {
                conversionHistory = new HashMap<>();
                LOGGER.info("No existing conversion history found, starting fresh");
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to load conversion history: " + e.getMessage(), e);
            conversionHistory = new HashMap<>();
        }
    }
    
    /**
     * Save conversion history to JSON file.
     */
    private void saveConversionHistory() {
        try {
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(conversionHistory);
            Files.writeString(historyFilePath, json);
            LOGGER.fine("Saved conversion history with " + conversionHistory.size() + " records");
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to save conversion history: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get file extension from filename.
     */
    private String getFileExtension(String fileName) {
        if (fileName == null) return "";
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(lastDot + 1).toLowerCase() : "";
    }
}
