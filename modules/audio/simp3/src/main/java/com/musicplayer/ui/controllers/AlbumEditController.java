package com.musicplayer.ui.controllers;

import java.io.File;
import java.util.function.Consumer;

import com.musicplayer.data.models.Album;
import com.musicplayer.ui.util.AlbumArtLoader;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/**
 * Controller for the album edit dialog.
 */
public class AlbumEditController {
    
    @FXML private TextField albumNameField;
    @FXML private ImageView currentAlbumArt;
    @FXML private Button selectImageButton;
    @FXML private Button resetImageButton;
    @FXML private Label selectedFileLabel;
    @FXML private HBox buttonBar;
    
    private Album album;
    private Button saveButton;
    private Button cancelButton;
    private File selectedImageFile;
    private Consumer<AlbumEditResult> onSave;
    
    /**
     * Result object containing the changes made to the album.
     */
    public static class AlbumEditResult {
        private final String newName;
        private final File newImageFile;
        private final boolean resetToDefault;
        
        public AlbumEditResult(String newName, File newImageFile, boolean resetToDefault) {
            this.newName = newName;
            this.newImageFile = newImageFile;
            this.resetToDefault = resetToDefault;
        }
        
        public String getNewName() { return newName; }
        public File getNewImageFile() { return newImageFile; }
        public boolean isResetToDefault() { return resetToDefault; }
    }
    
    /**
     * Initialize the controller.
     */
    @FXML
    public void initialize() {
        createCustomButtons();
    }
    
    /**
     * Set the album to edit and load its data.
     * @param album The album to edit
     */
    public void setAlbum(Album album) {
        this.album = album;
        loadAlbumData();
    }
    
    /**
     * Set the callback to execute when save is clicked.
     * @param onSave Callback that receives the edit result
     */
    public void setOnSave(Consumer<AlbumEditResult> onSave) {
        this.onSave = onSave;
    }
    
    /**
     * Get the save button for external handling.
     * @return The save button
     */
    public Button getSaveButton() {
        return saveButton;
    }
    
    /**
     * Get the cancel button for external handling.
     * @return The cancel button
     */
    public Button getCancelButton() {
        return cancelButton;
    }
    
    /**
     * Load the album data into the form.
     */
    private void loadAlbumData() {
        if (album != null) {
            albumNameField.setText(album.getTitle());
            
            // Load current album art
            if (album.getSongs() != null && !album.getSongs().isEmpty()) {
                AlbumArtLoader.loadAlbumArt(album.getSongs().get(0))
                    .thenAcceptAsync(image -> {
                        currentAlbumArt.setImage(image);
                    }, Platform::runLater);
            } else {
                currentAlbumArt.setImage(getPlaceholderImage());
            }
        }
    }
    
    /**
     * Create custom save and cancel buttons.
     */
    private void createCustomButtons() {
        // Create save button with icon
        saveButton = new Button("Save");
        try {
            ImageView saveIcon = new ImageView(new Image(
                getClass().getResourceAsStream("/images/icons/save.png"), 16, 16, true, true));
            saveButton.setGraphic(saveIcon);
        } catch (Exception e) {
            // Icon not found, continue without icon
        }
        saveButton.getStyleClass().add("icon-button");
        
        // Create cancel button with icon
        cancelButton = new Button("Cancel");
        try {
            ImageView cancelIcon = new ImageView(new Image(
                getClass().getResourceAsStream("/images/icons/cancel.png"), 16, 16, true, true));
            cancelButton.setGraphic(cancelIcon);
        } catch (Exception e) {
            // Icon not found, continue without icon
        }
        cancelButton.getStyleClass().add("icon-button");
        
        // Add buttons to button bar
        buttonBar.getChildren().addAll(saveButton, cancelButton);
        buttonBar.setAlignment(Pos.CENTER_RIGHT);
        buttonBar.setSpacing(10);
        buttonBar.setPadding(new Insets(10, 0, 0, 0));
    }
    
    /**
     * Handle selecting a new image file.
     */
    @FXML
    private void onSelectImage() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Album Art");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"),
            new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        
        Stage stage = (Stage) selectImageButton.getScene().getWindow();
        File file = fileChooser.showOpenDialog(stage);
        
        if (file != null) {
            selectedImageFile = file;
            selectedFileLabel.setText("Selected: " + file.getName());
            
            // Preview the selected image
            try {
                Image previewImage = new Image(file.toURI().toString(), 80, 80, true, true);
                currentAlbumArt.setImage(previewImage);
            } catch (Exception e) {
                selectedFileLabel.setText("Error loading image: " + file.getName());
            }
        }
    }
    
    /**
     * Handle resetting to default image.
     */
    @FXML
    private void onResetImage() {
        selectedImageFile = null;
        selectedFileLabel.setText("Will reset to default album art");
        currentAlbumArt.setImage(getPlaceholderImage());
    }
    
    /**
     * Save the changes and execute the callback.
     */
    public void saveChanges() {
        if (onSave != null) {
            String newName = albumNameField.getText().trim();
            boolean resetToDefault = selectedImageFile == null && !selectedFileLabel.getText().isEmpty();
            
            AlbumEditResult result = new AlbumEditResult(newName, selectedImageFile, resetToDefault);
            onSave.accept(result);
        }
    }
    
    /**
     * Get placeholder image.
     */
    private Image getPlaceholderImage() {
        return new Image(getClass().getResourceAsStream("/images/icons/album_placeholder.png"), 80, 80, true, true);
    }
}
