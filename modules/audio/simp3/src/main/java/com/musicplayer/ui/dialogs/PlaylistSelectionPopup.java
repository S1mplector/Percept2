package com.musicplayer.ui.dialogs;

import java.util.Optional;

import com.musicplayer.data.models.Playlist;

import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Simple custom JavaFX modal for selecting a playlist.
 */
public final class PlaylistSelectionPopup {

    private PlaylistSelectionPopup() {}

    /**
     * Shows the modal and returns the selected playlist (or empty if cancelled).
     *
     * @param owner         window owner for modality
     * @param playlists     list of playlists to choose from
     * @return Optional with chosen playlist
     */
    public static Optional<Playlist> show(Window owner, ObservableList<Playlist> playlists) {
        Stage stage = new Stage();
        stage.setTitle("Add to Playlist");
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);

        VBox root = new VBox(10);
        root.setPadding(new Insets(10));

        // Header with icon
        HBox headerBox = new HBox(10);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        
        // Playlist icon
        Image playlistIconImg = new Image(PlaylistSelectionPopup.class.getResourceAsStream("/images/icons/playlist.png"));
        ImageView playlistIcon = new ImageView(playlistIconImg);
        playlistIcon.setFitWidth(40);
        playlistIcon.setFitHeight(40);
        
        Label header = new Label("Select a playlist:");
        header.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        
        headerBox.getChildren().addAll(playlistIcon, header);

        FilteredList<Playlist> filtered = new FilteredList<>(playlists, p -> true);

        // Search field with icon
        TextField searchField = new TextField();
        searchField.setPromptText("Search");
        searchField.textProperty().addListener((obs, oldV, newV) -> {
            String filter = newV == null ? "" : newV.toLowerCase().trim();
            filtered.setPredicate(pl -> filter.isEmpty() || pl.getName().toLowerCase().contains(filter));
        });

        Image searchIconImg = new Image(PlaylistSelectionPopup.class.getResourceAsStream("/images/icons/search.png"));
        ImageView searchIcon = new ImageView(searchIconImg);
        searchIcon.setFitWidth(16);
        searchIcon.setFitHeight(16);
        HBox searchBox = new HBox(5, searchIcon, searchField);
        searchBox.setAlignment(Pos.CENTER_LEFT);

        ListView<Playlist> listView = new ListView<>(filtered);
        listView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Playlist item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName());
            }
        });

        // Buttons
        HBox buttonBar = new HBox(10);
        buttonBar.setAlignment(Pos.CENTER_RIGHT);

        // Icon buttons for OK and Cancel
        ImageView okIv = new ImageView(new Image(PlaylistSelectionPopup.class.getResourceAsStream("/images/icons/ok.png")));
        okIv.setFitWidth(20);
        okIv.setFitHeight(20);
        Button ok = new Button();
        ok.setGraphic(okIv);

        ImageView cancelIv = new ImageView(new Image(PlaylistSelectionPopup.class.getResourceAsStream("/images/icons/cancel.png")));
        cancelIv.setFitWidth(20);
        cancelIv.setFitHeight(20);
        Button cancel = new Button();
        cancel.setGraphic(cancelIv);

        String buttonStyle = "-fx-background-color: transparent; -fx-border-color: transparent; -fx-padding: 0; -fx-focus-color: transparent; -fx-faint-focus-color: transparent;";
        ok.setStyle(buttonStyle);
        cancel.setStyle(buttonStyle);

        // Green glow hover effect similar to rescan button
        DropShadow glow = new DropShadow(20, Color.LIMEGREEN);
        ok.setOnMouseEntered(e -> okIv.setEffect(glow));
        ok.setOnMouseExited(e -> okIv.setEffect(null));
        cancel.setOnMouseEntered(e -> cancelIv.setEffect(glow));
        cancel.setOnMouseExited(e -> cancelIv.setEffect(null));

        ok.setDisable(true);

        listView.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> ok.setDisable(newSel == null));

        buttonBar.getChildren().addAll(ok, cancel);
        root.getChildren().addAll(headerBox, searchBox, listView, buttonBar);

        Scene scene = new Scene(root, 300, 400);
        
        // Apply CSS to remove focus highlighting from the ListView
        scene.getStylesheets().add(PlaylistSelectionPopup.class.getResource("/css/app.css").toExternalForm());
        
        stage.setScene(scene);

        final Playlist[] selected = new Playlist[1];

        ok.setOnAction(e -> {
            selected[0] = listView.getSelectionModel().getSelectedItem();
            stage.close();
        });
        cancel.setOnAction(e -> stage.close());

        stage.showAndWait();
        return Optional.ofNullable(selected[0]);
    }
} 