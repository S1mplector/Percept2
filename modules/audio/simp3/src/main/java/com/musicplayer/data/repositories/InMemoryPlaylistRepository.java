package com.musicplayer.data.repositories;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.musicplayer.data.models.Playlist;

public class InMemoryPlaylistRepository implements PlaylistRepository {
    private final Map<Long, Playlist> playlists = new HashMap<>();
    private final AtomicLong idCounter = new AtomicLong();

    @Override
    public void save(Playlist playlist) {
        if (playlist.getId() == 0) {
            playlist.setId(idCounter.incrementAndGet());
        }
        playlists.put(playlist.getId(), playlist);
    }

    @Override
    public Playlist findById(long id) {
        return playlists.get(id);
    }

    @Override
    public List<Playlist> findAll() {
        return new ArrayList<>(playlists.values());
    }

    @Override
    public void delete(long id) {
        playlists.remove(id);
    }
}
