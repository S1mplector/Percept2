package com.musicplayer.ui.components;

import java.util.function.Consumer;

import com.musicplayer.data.models.Playlist;

import javafx.animation.FadeTransition;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.util.Duration;

/**
 * Custom ListCell for displaying playlists with play, shuffle, and delete functionality.
 */
public class PlaylistCell extends ListCell<Playlist> {
    
    private final HBox content;
    private final Label nameLabel;
    private final TextField nameField;
    private final Region spacer;
    private final Button playButton;
    private final Button shuffleButton;
    private final Button removeButton;
    private final ImageView playingIcon;
    private FadeTransition fade;
    
    private boolean isEditing = false;
    private Consumer<Playlist> onDelete;
    private Consumer<RenameRequest> onRename;
    private Consumer<Playlist> onPlay;
    private Consumer<Playlist> onShuffle;
    
    public PlaylistCell() {
        // Create UI components
        content = new HBox();
        content.setAlignment(Pos.CENTER_LEFT);
        content.setSpacing(5.0);
        
        nameLabel = new Label();
        nameLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(nameLabel, Priority.ALWAYS);
        
        nameField = new TextField();
        nameField.setVisible(false);
        nameField.setManaged(false);
        nameField.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(nameField, Priority.ALWAYS);
        
        spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        // Play button
        playButton = new Button();
        playButton.setPrefHeight(24.0);
        playButton.setPrefWidth(24.0);
        playButton.setStyle("-fx-background-color: transparent; -fx-border-color: transparent; " +
                            "-fx-padding: 0; -fx-focus-color: transparent; -fx-faint-focus-color: transparent;");
        
        try {
            ImageView playIcon = new ImageView(new Image(getClass().getResourceAsStream("/images/icons/playplaylist.png")));
            playIcon.setFitHeight(20.0);
            playIcon.setFitWidth(20.0);
            playIcon.setPreserveRatio(true);
            playButton.setGraphic(playIcon);
        } catch (Exception e) {
            playButton.setText("▶"); // Fallback
        }
        
        // Shuffle button
        shuffleButton = new Button();
        shuffleButton.setPrefHeight(24.0);
        shuffleButton.setPrefWidth(24.0);
        shuffleButton.setStyle("-fx-background-color: transparent; -fx-border-color: transparent; " +
                              "-fx-padding: 0; -fx-focus-color: transparent; -fx-faint-focus-color: transparent;");
        
        try {
            ImageView shuffleIcon = new ImageView(new Image(getClass().getResourceAsStream("/images/icons/shuffle.png")));
            shuffleIcon.setFitHeight(20.0);
            shuffleIcon.setFitWidth(20.0);
            shuffleIcon.setPreserveRatio(true);
            shuffleButton.setGraphic(shuffleIcon);
        } catch (Exception e) {
            shuffleButton.setText("🔀"); // Fallback
        }
        
        // Delete button with trash icon
        removeButton = new Button();
        removeButton.setPrefHeight(24.0);
        removeButton.setPrefWidth(24.0);
        removeButton.setStyle("-fx-background-color: transparent; -fx-border-color: transparent; " +
                            "-fx-padding: 0; -fx-focus-color: transparent; -fx-faint-focus-color: transparent;");
        
        // Load trash icon
        try {
            ImageView removeIcon = new ImageView(new Image(getClass().getResourceAsStream("/images/icons/trash.png")));
            removeIcon.setFitHeight(20.0);
            removeIcon.setFitWidth(20.0);
            removeIcon.setPreserveRatio(true);
            removeButton.setGraphic(removeIcon);
        } catch (Exception e) {
            removeButton.setText("🗑"); // Fallback text
        }
        
        playingIcon = new ImageView(new Image(getClass().getResourceAsStream("/images/icons/playing.png")));
        playingIcon.setFitHeight(20);
        playingIcon.setFitWidth(20);
        playingIcon.setOpacity(0);
        
        content.getChildren().add(0, playingIcon);
        content.getChildren().addAll(nameLabel, nameField, spacer, playButton, shuffleButton, removeButton);
        
        setupEventHandlers();
    }
    
    private void setupEventHandlers() {
        // Double-click to edit
        nameLabel.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                startEditing();
            }
        });
        
        // Play button action
        playButton.setOnAction(event -> {
            if (getItem() != null && onPlay != null) {
                onPlay.accept(getItem());
            }
        });
        
        // Shuffle button action
        shuffleButton.setOnAction(event -> {
            if (getItem() != null && onShuffle != null) {
                onShuffle.accept(getItem());
            }
        });
        
        // Remove button action
        removeButton.setOnAction(event -> {
            if (getItem() != null && onDelete != null) {
                onDelete.accept(getItem());
            }
        });
        
        // Text field event handlers
        nameField.setOnAction(event -> commitEdit());
        nameField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                stopEditing();
            }
        });
        
        nameField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (wasFocused && !isNowFocused && isEditing) {
                commitEdit();
            }
        });
        
        nameField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (wasFocused && !isNowFocused && isEditing) {
                commitEdit();
            }
        });
    }
    
    private void startEditing() {
        if (getItem() != null) {
            isEditing = true;
            nameField.setText(getItem().getName());
            nameLabel.setVisible(false);
            nameLabel.setManaged(false);
            nameField.setVisible(true);
            nameField.setManaged(true);
            nameField.requestFocus();
            nameField.selectAll();
        }
    }
    
    private void commitEdit() {
        if (isEditing && getItem() != null) {
            String newName = nameField.getText().trim();
            if (!newName.isEmpty() && !newName.equals(getItem().getName())) {
                if (onRename != null) {
                    onRename.accept(new RenameRequest(getItem(), newName));
                }
            }
            stopEditing();
        }
    }
    
    private void stopEditing() {
        isEditing = false;
        nameField.setVisible(false);
        nameField.setManaged(false);
        nameLabel.setVisible(true);
        nameLabel.setManaged(true);
    }
    
    public void updatePlaying(boolean isPlaying) {
        if (isPlaying) {
            if (fade == null) {
                fade = new FadeTransition(Duration.seconds(1), playingIcon);
                fade.setFromValue(0.2);
                fade.setToValue(1.0);
                fade.setAutoReverse(true);
                fade.setCycleCount(FadeTransition.INDEFINITE);
            }
            playingIcon.setOpacity(0.8);
            fade.play();
        } else {
            if (fade != null) {
                fade.stop();
            }
            playingIcon.setOpacity(0);
        }
    }
    
    @Override
    public void cancelEdit() {
        stopEditing();
        super.cancelEdit();
    }
    
    @Override
    protected void updateItem(Playlist playlist, boolean empty) {
        super.updateItem(playlist, empty);
        
        if (empty || playlist == null) {
            setGraphic(null);
            setText(null);
            updatePlaying(false);
        } else {
            nameLabel.setText(playlist.getName());
            nameField.setText(playlist.getName());
            setGraphic(content);
            setText(null);
        }
        
        // Cancel editing if switching items
        if (isEditing) {
            stopEditing();
        }
    }
    
    /**
     * Sets the callback for playlist deletion.
     */
    public void setOnDelete(Consumer<Playlist> onDelete) {
        this.onDelete = onDelete;
    }
    
    /**
     * Sets the callback for playlist renaming.
     */
    public void setOnRename(Consumer<RenameRequest> onRename) {
        this.onRename = onRename;
    }

    /**
     * Sets the callback for playing the playlist.
     */
    public void setOnPlay(Consumer<Playlist> onPlay) {
        this.onPlay = onPlay;
    }

    /**
     * Sets the callback for shuffling the playlist.
     */
    public void setOnShuffle(Consumer<Playlist> onShuffle) {
        this.onShuffle = onShuffle;
    }
    
    /**
     * Request to rename a playlist.
     */
    public static class RenameRequest {
        private final Playlist playlist;
        private final String newName;
        
        public RenameRequest(Playlist playlist, String newName) {
            this.playlist = playlist;
            this.newName = newName;
        }
        
        public Playlist getPlaylist() {
            return playlist;
        }
        
        public String getNewName() {
            return newName;
        }
    }
}
