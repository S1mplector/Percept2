package com.musicplayer.data.repositories;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.musicplayer.data.models.Album;

public class InMemoryAlbumRepository implements AlbumRepository {
    private final Map<Long, Album> albums = new HashMap<>();
    private final AtomicLong idCounter = new AtomicLong();

    @Override
    public void save(Album album) {
        if (album.getId() == 0) {
            album.setId(idCounter.incrementAndGet());
        }
        albums.put(album.getId(), album);
    }

    @Override
    public Album findById(long id) {
        return albums.get(id);
    }

    @Override
    public List<Album> findAll() {
        return new ArrayList<>(albums.values());
    }

    @Override
    public void delete(long id) {
        albums.remove(id);
    }
}
