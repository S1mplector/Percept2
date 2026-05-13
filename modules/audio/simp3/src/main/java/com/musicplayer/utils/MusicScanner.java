package com.musicplayer.utils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.musicplayer.data.models.Song;

/**
 * Utility class for scanning music folders and extracting metadata from audio files.
 */
public class MusicScanner {

    private static final Logger log = LoggerFactory.getLogger(MusicScanner.class);
    
    private static final String[] SUPPORTED_EXTENSIONS = {
        ".mp3", ".m4a", ".flac", ".wav", ".ogg", ".opus", 
        ".aac", ".wma", ".mp4", ".m4b", ".aif", ".aiff"
    };
    
    /**
     * Scans a directory recursively for music files and extracts their metadata.
     * 
     * @param directory The directory to scan
     * @return List of Song objects with extracted metadata
     */
    public static List<Song> scanDirectory(File directory) {
        List<Song> songs = new ArrayList<>();
        
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            log.warn("Invalid directory provided for scanning: {}", directory);
            return songs;
        }

        log.debug("Starting recursive scan of: {}", directory.getAbsolutePath());
        scanDirectoryRecursive(directory, songs, 0);
        log.debug("Scan completed. Found {} music files total.", songs.size());
        return songs;
    }
    
    public static Song scanFile(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return null;
        }
        if (!isSupportedAudioFile(file)) {
            return null;
        }
        return extractMetadata(file);
    }
    
    private static void scanDirectoryRecursive(File directory, List<Song> songs, int depth) {
        File[] files = directory.listFiles();
        if (files == null) {
            log.warn("Could not read directory: {}", directory.getAbsolutePath());
            return;
        }

        log.debug("Scanning: {} ({} items)", directory.getName(), files.length);

        for (File file : files) {
            // Skip hidden files and system directories
            if (file.isHidden() || file.getName().startsWith(".")) {
                continue;
            }

            if (file.isDirectory()) {
                // Recursively scan subdirectories
                log.debug("Entering subdirectory: {}", file.getName());
                scanDirectoryRecursive(file, songs, depth + 1);
            } else if (file.isFile() && isSupportedAudioFile(file)) {
                // Extract metadata from audio file
                log.debug("Found music file: {}", file.getName());
                Song song = extractMetadata(file);
                if (song != null) {
                    songs.add(song);
                } else {
                    log.warn("Failed to extract metadata from: {}", file.getName());
                }
            }
        }
    }
    
    private static boolean isSupportedAudioFile(File file) {
        String fileName = file.getName().toLowerCase();
        return Arrays.stream(SUPPORTED_EXTENSIONS)
                .anyMatch(fileName::endsWith);
    }
    
    private static Song extractMetadata(File file) {
        try {
            AudioFile audioFile = AudioFileIO.read(file);
            Tag tag = audioFile.getTag();
            
            Song song = new Song();
            song.setFilePath(file.getAbsolutePath());
            
            // Extract basic metadata
            if (tag != null) {
                song.setTitle(getTagValue(tag, FieldKey.TITLE, file.getName()));
                song.setArtist(getTagValue(tag, FieldKey.ARTIST, "Unknown Artist"));
                String albumTag = getTagValue(tag, FieldKey.ALBUM, "Unknown Album");
                if (albumTag.equals("Unknown Album") || albumTag.isBlank()) {
                    albumTag = file.getParentFile() != null ? file.getParentFile().getName() : "Unknown Album";
                }
                song.setAlbum(albumTag);
                song.setGenre(getTagValue(tag, FieldKey.GENRE, "Unknown"));
                
                String trackStr = tag.getFirst(FieldKey.TRACK);
                if (!trackStr.isEmpty()) {
                    try {
                        String trackNumber = trackStr.split("/")[0];
                        song.setTrackNumber(Integer.parseInt(trackNumber));
                    } catch (NumberFormatException e) {
                        song.setTrackNumber(0);
                    }
                }
                String yearStr = null;
                try { yearStr = tag.getFirst(FieldKey.YEAR); } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
            }
                if (yearStr != null && !yearStr.isBlank()) {
                    String y = yearStr.trim();
                    if (y.length() >= 4) {
                        String y4 = y.substring(0, 4).replaceAll("[^0-9]", "");
                        try { song.setYear(Integer.parseInt(y4)); } catch (NumberFormatException ignored) {}
                    } else {
                        try { song.setYear(Integer.parseInt(y)); } catch (NumberFormatException ignored) {}
                    }
                }
            } else {
                // Fallback if no tags are available
                song.setTitle(getFileNameWithoutExtension(file));
                song.setArtist("Unknown Artist");
                song.setAlbum("Unknown Album");
                song.setGenre("Unknown");
                song.setTrackNumber(0);
            }
            
            // Get duration from audio file
            if (audioFile.getAudioHeader() != null) {
                song.setDuration(audioFile.getAudioHeader().getTrackLength());
            }
            if (song.getTrackNumber() == 0) {
                String n = file.getName();
                int i = 0; while (i < n.length() && Character.isDigit(n.charAt(i))) i++;
                if (i > 0) {
                    try { song.setTrackNumber(Integer.parseInt(n.substring(0, i))); } catch (NumberFormatException ignored) {}
                }
            }
            if (song.getArtist() == null || song.getArtist().isBlank() || "Unknown Artist".equalsIgnoreCase(song.getArtist())) {
                File p = file.getParentFile();
                File pp = p != null ? p.getParentFile() : null;
                if (pp != null) {
                    song.setArtist(pp.getName());
                }
            }
            
            return song;
            
        } catch (Exception e) {
            log.error("Error reading metadata from file: {}", file.getAbsolutePath(), e);

            // Create a basic song object with file information
            Song song = new Song();
            song.setFilePath(file.getAbsolutePath());
            song.setTitle(getFileNameWithoutExtension(file));
            song.setArtist("Unknown Artist");
            song.setAlbum("Unknown Album");
            song.setGenre("Unknown");
            song.setDuration(0);
            song.setTrackNumber(0);
            
            return song;
        }
    }
    
    private static String getTagValue(Tag tag, FieldKey field, String defaultValue) {
        try {
            String value = tag.getFirst(field);
            return (value != null && !value.trim().isEmpty()) ? value.trim() : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }
    
    private static String getFileNameWithoutExtension(File file) {
        String fileName = file.getName();
        int lastDotIndex = fileName.lastIndexOf('.');
        return (lastDotIndex > 0) ? fileName.substring(0, lastDotIndex) : fileName;
    }
}
