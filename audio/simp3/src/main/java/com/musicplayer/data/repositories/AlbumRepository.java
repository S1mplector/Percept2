package com.musicplayer.data.repositories;

import java.util.List;

import com.musicplayer.data.models.Album;

public interface AlbumRepository {
    void save(Album album);
    Album findById(long id);
    List<Album> findAll();
    void delete(long id);
}
