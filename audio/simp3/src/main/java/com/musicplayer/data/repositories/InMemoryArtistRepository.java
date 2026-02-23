package com.musicplayer.data.repositories;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.musicplayer.data.models.Artist;

public class InMemoryArtistRepository implements ArtistRepository {
    private final Map<Long, Artist> artists = new HashMap<>();
    private final AtomicLong idCounter = new AtomicLong();

    @Override
    public void save(Artist artist) {
        if (artist.getId() == 0) {
            artist.setId(idCounter.incrementAndGet());
        }
        artists.put(artist.getId(), artist);
    }

    @Override
    public Artist findById(long id) {
        return artists.get(id);
    }

    @Override
    public List<Artist> findAll() {
        return new ArrayList<>(artists.values());
    }

    @Override
    public void delete(long id) {
        artists.remove(id);
    }
}
