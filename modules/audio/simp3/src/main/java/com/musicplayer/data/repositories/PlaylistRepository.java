package com.musicplayer.data.repositories;

import java.util.List;

import com.musicplayer.data.models.Playlist;

public interface PlaylistRepository {
    void save(Playlist playlist);
    Playlist findById(long id);
    List<Playlist> findAll();
    void delete(long id);
}
