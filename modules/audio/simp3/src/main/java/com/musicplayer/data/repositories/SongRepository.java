package com.musicplayer.data.repositories;

import java.util.List;

import com.musicplayer.data.models.Song;

public interface SongRepository {
    void save(Song song);
    Song findById(long id);
    List<Song> findAll();
    void delete(long id);
}
