package com.musicplayer.ui.util;

import com.musicplayer.data.models.Playlist;
import com.musicplayer.data.models.Song;

import javafx.collections.transformation.FilteredList;
import javafx.scene.control.TextField;

/**
 * Utility class to encapsulate search filtering logic for songs and playlists.
 */
public final class SearchManager {

    private SearchManager() {}

    public static void bindSongSearch(TextField searchField,
                                      FilteredList<Song> filteredSongs) {
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            String filter = newVal == null ? "" : newVal.toLowerCase().trim();
            filteredSongs.setPredicate(song -> {
                if (song == null) return false;
                if (filter.isEmpty()) return true;
                
                // Check if the search term matches title, artist, or album
                String title = song.getTitle() != null ? song.getTitle().toLowerCase() : "";
                String artist = song.getArtist() != null ? song.getArtist().toLowerCase() : "";
                String album = song.getAlbum() != null ? song.getAlbum().toLowerCase() : "";
                
                return title.contains(filter) ||
                       artist.contains(filter) ||
                       album.contains(filter);
            });
        });
    }

    public static void bindPlaylistSearch(TextField searchField,
                                          FilteredList<Playlist> filteredPlaylists) {
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            String filter = newVal == null ? "" : newVal.toLowerCase().trim();
            filteredPlaylists.setPredicate(pl -> filter.isEmpty() || pl.getName().toLowerCase().contains(filter));
        });
    }
} 