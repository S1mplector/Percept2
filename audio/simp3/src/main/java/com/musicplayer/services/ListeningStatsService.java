package com.musicplayer.services;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import com.musicplayer.data.models.Song;
import com.musicplayer.data.repositories.SongRepository;

/**
 * Service for tracking and managing listening statistics.
 * Tracks play counts, calculates statistics, and manages listening history.
 */
public class ListeningStatsService {
    
    private final SongRepository songRepository;
    
    public ListeningStatsService(SongRepository songRepository) {
        this.songRepository = songRepository;
    }
    
    /**
     * Records that a song has been played.
     * Increments play count and updates last played timestamp.
     * 
     * @param song The song that was played
     */
    public void recordPlay(Song song) {
        if (song == null) return;
        
        song.incrementPlayCount();
        songRepository.save(song);
    }
    
    /**
     * Gets the total number of songs played today.
     * 
     * @return Number of songs played today
     */
    public int getSongsPlayedToday() {
        LocalDate today = LocalDate.now();
        long startOfDay = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        
        return (int) songRepository.findAll().stream()
            .filter(song -> song.getLastPlayed() >= startOfDay)
            .count();
    }
    
    /**
     * Gets the total number of songs played this week.
     * 
     * @return Number of songs played this week
     */
    public int getSongsPlayedThisWeek() {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.minusDays(today.getDayOfWeek().getValue() - 1);
        long startOfWeek = weekStart.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        
        return (int) songRepository.findAll().stream()
            .filter(song -> song.getLastPlayed() >= startOfWeek)
            .count();
    }
    
    /**
     * Gets the total number of songs played this month.
     * 
     * @return Number of songs played this month
     */
    public int getSongsPlayedThisMonth() {
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        long startOfMonth = monthStart.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        
        return (int) songRepository.findAll().stream()
            .filter(song -> song.getLastPlayed() >= startOfMonth)
            .count();
    }
    
    /**
     * Gets the most played song.
     * 
     * @return The most played song, or null if no songs have been played
     */
    public Song getMostPlayedSong() {
        return songRepository.findAll().stream()
            .filter(song -> song.getPlayCount() > 0)
            .max(Comparator.comparingInt(Song::getPlayCount))
            .orElse(null);
    }
    
    /**
     * Gets the top N most played songs.
     * 
     * @param limit Maximum number of songs to return
     * @return List of most played songs
     */
    public List<Song> getTopPlayedSongs(int limit) {
        return songRepository.findAll().stream()
            .filter(song -> song.getPlayCount() > 0)
            .sorted(Comparator.comparingInt(Song::getPlayCount).reversed())
            .limit(limit)
            .collect(Collectors.toList());
    }
    
    /**
     * Gets recently played songs.
     * 
     * @param limit Maximum number of songs to return
     * @return List of recently played songs
     */
    public List<Song> getRecentlyPlayed(int limit) {
        return songRepository.findAll().stream()
            .filter(song -> song.getLastPlayed() > 0)
            .sorted(Comparator.comparingLong(Song::getLastPlayed).reversed())
            .limit(limit)
            .collect(Collectors.toList());
    }
    
    /**
     * Gets the total play count across all songs.
     * 
     * @return Total number of plays
     */
    public int getTotalPlayCount() {
        return songRepository.findAll().stream()
            .mapToInt(Song::getPlayCount)
            .sum();
    }
    
    /**
     * Formats the most played song information for display.
     * 
     * @return Formatted string with song info and play count
     */
    public String getMostPlayedSongDisplay() {
        Song mostPlayed = getMostPlayedSong();
        if (mostPlayed == null) {
            return null;
        }
        return String.format("%s - %s (%d plays)", 
            mostPlayed.getArtist(), 
            mostPlayed.getTitle(), 
            mostPlayed.getPlayCount());
    }
} 