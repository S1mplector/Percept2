package com.musicplayer.services;

import java.util.List;

import com.musicplayer.data.models.Playlist;
import com.musicplayer.data.models.Song;
import com.musicplayer.data.repositories.PlaylistRepository;

public class PlaylistService {
    private final PlaylistRepository playlistRepository;

    public PlaylistService(PlaylistRepository playlistRepository) {
        this.playlistRepository = playlistRepository;
    }

    public void createPlaylist(Playlist playlist) {
        playlistRepository.save(playlist);
    }

    public List<Playlist> getAllPlaylists() {
        return playlistRepository.findAll();
    }

    public void addSongToPlaylist(long playlistId, Song song) {
        Playlist playlist = playlistRepository.findById(playlistId);
        if (playlist != null) {
            playlist.getSongs().add(song);
            playlistRepository.save(playlist);
        }
    }

    public void removeSongFromPlaylist(long playlistId, Song song) {
        Playlist playlist = playlistRepository.findById(playlistId);
        if (playlist != null) {
            playlist.getSongs().remove(song);
            playlistRepository.save(playlist);
        }
    }
}
