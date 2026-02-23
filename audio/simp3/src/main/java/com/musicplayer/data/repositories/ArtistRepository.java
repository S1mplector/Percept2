package com.musicplayer.data.repositories;

import java.util.List;

import com.musicplayer.data.models.Artist;

public interface ArtistRepository {
    void save(Artist artist);
    Artist findById(long id);
    List<Artist> findAll();
    void delete(long id);
}
