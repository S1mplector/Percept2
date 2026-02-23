package com.musicplayer.ui.util;

import com.musicplayer.data.models.Playlist;
import com.musicplayer.data.models.Song;
import com.musicplayer.services.PlaylistManager;
import com.musicplayer.ui.components.PinboardItem;
import com.musicplayer.ui.components.PinboardPanel;
import com.musicplayer.ui.dialogs.PlaylistSelectionPopup;

import javafx.beans.binding.Bindings;
import javafx.collections.ObservableList;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableRow;

/**
 * Utility class that attaches a context menu to {@link TableRow<Song>} entries
 * allowing users to add or remove the selected song from playlists.
 */
public final class SongContextMenuProvider {

    private SongContextMenuProvider() {
        // Utility class – no instantiation
    }

    /**
     * Attaches a context menu to the provided {@link TableRow}. The menu
     * enables adding the row's song to any existing playlist and removing it
     * from the currently selected playlist.
     *
     * @param row               The table row representing a song
     * @param playlists         Observable list of available playlists
     * @param playlistManager   Playlist manager for performing operations
     * @param playlistsListView ListView that shows playlists (used for current selection)
     * @param songsObservable   Observable list backing the songs TableView – refreshed on updates
     */
    public static void attachContextMenu(TableRow<Song> row,
                                         ObservableList<Playlist> playlists,
                                         PlaylistManager playlistManager,
                                         ListView<Playlist> playlistsListView,
                                         ObservableList<Song> songsObservable) {
        attachContextMenu(row, playlists, playlistManager, playlistsListView, songsObservable, null);
    }

    public static void attachContextMenu(TableRow<Song> row, ObservableList<Playlist> playlists,
                                       PlaylistManager playlistManager, ListView<Playlist> playlistsListView,
                                       ObservableList<Song> songsObservable, PinboardPanel pinboard) {

        ContextMenu contextMenu = new ContextMenu();

        // Add to playlist
        MenuItem addToPlaylistItem = new MenuItem("Add to Playlist…");
        addToPlaylistItem.setOnAction(e -> {
            Song song = row.getItem();
            if (song == null) {
                return;
            }

            if (playlists.isEmpty()) {
                Alert alert = new Alert(AlertType.INFORMATION);
                alert.setTitle("No Playlists");
                alert.setHeaderText("No playlists available");
                alert.setContentText("Please create a playlist first.");
                alert.showAndWait();
                return;
            }

            PlaylistSelectionPopup.show(row.getScene().getWindow(), playlists)
                    .ifPresent(playlist -> {
                        playlistManager.addSongToPlaylist(playlist.getId(), song);
                        if (playlist.equals(playlistsListView.getSelectionModel().getSelectedItem())) {
                            songsObservable.setAll(playlist.getSongs());
                        }
                    });
        });

        // Remove from playlist
        MenuItem removeFromPlaylistItem = new MenuItem("Remove from Playlist");
        removeFromPlaylistItem.setOnAction(e -> {
            Song song = row.getItem();
            Playlist selectedPlaylist = playlistsListView.getSelectionModel().getSelectedItem();
            if (song == null || selectedPlaylist == null) {
                return;
            }

            boolean removed = playlistManager.removeSongFromPlaylist(selectedPlaylist.getId(), song);
            if (removed) {
                songsObservable.setAll(selectedPlaylist.getSongs());
            }
        });

        contextMenu.getItems().addAll(addToPlaylistItem, removeFromPlaylistItem);

        // Pin album option
        Song song = row.getItem();
        if (pinboard != null && song != null && song.getAlbum() != null && !song.getAlbum().isEmpty()) {
            MenuItem pinAlbumItem = new MenuItem("Pin Album");
            pinAlbumItem.setOnAction(e -> {
                String albumId = "album-" + song.getAlbum().toLowerCase().replace(" ", "-");
                if (!pinboard.isPinned(albumId)) {
                    pinboard.addPinnedItem(albumId, song.getAlbum(), PinboardItem.ItemType.ALBUM, () -> {
                        // TODO: Navigate to album when clicked
                    });
                }
            });
            contextMenu.getItems().add(pinAlbumItem);
        }

        // Only show context menu for non-empty rows
        row.contextMenuProperty().bind(
                Bindings.when(row.emptyProperty())
                        .then((ContextMenu) null)
                        .otherwise(contextMenu));
    }
}
