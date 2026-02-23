package com.musicplayer.services;

import com.musicplayer.data.models.Song;
import com.musicplayer.data.repositories.InMemorySongRepository;
import com.musicplayer.data.repositories.SongRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ListeningStatsServiceTest {

    private SongRepository repo;
    private ListeningStatsService svc;

    @BeforeEach
    void setup() {
        repo = new InMemorySongRepository();
        svc = new ListeningStatsService(repo);
    }

    private static Song song(long id, String artist, String title) {
        Song s = new Song();
        s.setId(id);
        s.setArtist(artist);
        s.setTitle(title);
        s.setAlbum("Album");
        return s;
    }

    @Test
    void recordPlay_increments_and_updates_lastPlayed() {
        Song s = song(1, "A", "T1");
        assertEquals(0, s.getPlayCount());
        assertEquals(0, s.getLastPlayed());
        repo.save(s);

        svc.recordPlay(s);
        assertEquals(1, s.getPlayCount());
        assertTrue(s.getLastPlayed() > 0);

        // Persisted in repository
        Song loaded = repo.findById(1);
        assertNotNull(loaded);
        assertEquals(1, loaded.getPlayCount());
    }

    @Test
    void mostPlayed_and_topPlayedSongs_ordering() {
        Song a = song(1, "A", "T1"); a.setPlayCount(5);
        Song b = song(2, "B", "T2"); b.setPlayCount(10);
        Song c = song(3, "C", "T3"); c.setPlayCount(7);
        repo.save(a); repo.save(b); repo.save(c);

        assertEquals(b, svc.getMostPlayedSong());

        List<Song> top2 = svc.getTopPlayedSongs(2);
        assertEquals(2, top2.size());
        assertEquals(b, top2.get(0));
        assertEquals(c, top2.get(1));

        String display = svc.getMostPlayedSongDisplay();
        assertTrue(display.contains("B"));
        assertTrue(display.contains("T2"));
        assertTrue(display.contains("10"));
    }

    @Test
    void recentlyPlayed_ordering() {
        long now = System.currentTimeMillis();
        Song a = song(1, "A", "T1"); a.setLastPlayed(now - 10_000);
        Song b = song(2, "B", "T2"); b.setLastPlayed(now - 5_000);
        Song c = song(3, "C", "T3"); c.setLastPlayed(now - 20_000);
        repo.save(a); repo.save(b); repo.save(c);

        List<Song> recent = svc.getRecentlyPlayed(2);
        assertEquals(2, recent.size());
        assertEquals(b, recent.get(0));
        assertEquals(a, recent.get(1));
    }

    @Test
    void counts_today_week_month() {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now();
        long startOfDay = today.atStartOfDay(zone).toInstant().toEpochMilli();
        long startOfWeek = today.minusDays(today.getDayOfWeek().getValue() - 1)
                .atStartOfDay(zone).toInstant().toEpochMilli();
        long startOfMonth = today.withDayOfMonth(1).atStartOfDay(zone)
                .toInstant().toEpochMilli();

        Song todaySong = song(1, "A", "Today"); todaySong.setLastPlayed(startOfDay + 1);
        Song weekSong = song(2, "B", "Week"); weekSong.setLastPlayed(startOfWeek + 1);
        Song monthSong = song(3, "C", "Month"); monthSong.setLastPlayed(startOfMonth + 1);
        Song oldSong = song(4, "D", "Old"); oldSong.setLastPlayed(Instant.now().minusSeconds(60L * 60 * 24 * 40).toEpochMilli());
        repo.save(todaySong); repo.save(weekSong); repo.save(monthSong); repo.save(oldSong);

        assertEquals(1, svc.getSongsPlayedToday());
        // week: includes today + weekSong (>= startOfWeek)
        assertTrue(svc.getSongsPlayedThisWeek() >= 2);
        // month: includes those in week plus monthSong
        assertTrue(svc.getSongsPlayedThisMonth() >= 3);
        // ensure old is excluded from month
        assertTrue(svc.getSongsPlayedThisMonth() <= 3);
    }
}
