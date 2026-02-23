package com.musicplayer.data.repositories;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.musicplayer.data.models.Song;

public class InMemorySongRepository implements SongRepository {
    private final Map<Long, Song> songs = new HashMap<>();
    private final AtomicLong idCounter = new AtomicLong();

    @Override
    public void save(Song song) {
        if (song.getId() == 0) {
            song.setId(idCounter.incrementAndGet());
        }
        songs.put(song.getId(), song);
    }

    @Override
    public Song findById(long id) {
        return songs.get(id);
    }

    @Override
    public List<Song> findAll() {
        return new ArrayList<>(songs.values());
    }

    @Override
    public void delete(long id) {
        songs.remove(id);
    }
}
