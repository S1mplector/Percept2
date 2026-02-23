package com.musicplayer.ui.components;

import com.musicplayer.data.models.Album;
import com.musicplayer.data.repositories.AlbumRepository;
import com.musicplayer.data.repositories.SongRepository;
import com.musicplayer.ui.util.AlbumArtLoader;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/**
 * UI card representing a single album.
 */
public class AlbumCard extends StackPane {

    private final Album album;
    private final AlbumRepository albumRepository;
    private final SongRepository songRepository;
    private boolean selected = false;

    public AlbumCard(Album album, AlbumRepository albumRepository, SongRepository songRepository) {
        this.album = album;
        this.albumRepository = albumRepository;
        this.songRepository = songRepository;
        setPrefSize(110, 130);
        setPadding(new Insets(5));
        setCursor(Cursor.HAND);
        setFocusTraversable(false);

        VBox content = new VBox(5);
        content.setAlignment(Pos.CENTER);

        // Create ImageView for album art
        ImageView coverView = new ImageView();
        coverView.setFitWidth(90);
        coverView.setFitHeight(90);
        coverView.setPreserveRatio(true);
        coverView.setSmooth(true);
        
        // Load album art from the album (checks custom art first, then metadata)
        AlbumArtLoader.loadAlbumArt(album)
            .thenAcceptAsync(image -> {
                coverView.setImage(image);
            }, Platform::runLater);

        Label titleLbl = new Label(album.getTitle());
        titleLbl.setWrapText(true);
        titleLbl.setMaxWidth(100);
        titleLbl.setAlignment(Pos.CENTER);
        titleLbl.setStyle("-fx-text-fill: white; -fx-font-size: 11px;");

        content.getChildren().addAll(coverView, titleLbl);
        
        // Create settings button in top-right corner
        Button settingsButton = createSettingsButton();
        
        // Create delete button in top-left corner
        Button deleteButton = createDeleteButton();
        
        // Add content and buttons to main container
        getChildren().addAll(content, settingsButton, deleteButton);
        
        getStyleClass().add("album-card");
        setStyle("-fx-background-color: #333333; -fx-background-radius: 5;");

        setOnMouseClicked(e -> {
            // Don't trigger selection if settings or delete button was clicked
            Button deleteBtn = (Button) getChildren().get(2); // Get the delete button
            if (e.getTarget() != settingsButton && !isDescendantOf(e.getTarget(), settingsButton) &&
                e.getTarget() != deleteBtn && !isDescendantOf(e.getTarget(), deleteBtn)) {
                toggleSelected();
            }
        });

        // Hover visual cue
        setOnMouseEntered(e -> {
            if (!isSelected()) setStyle("-fx-background-color: #444444; -fx-background-radius: 5;");
        });
        setOnMouseExited(e -> {
            if (!isSelected()) setStyle("-fx-background-color: #333333; -fx-background-radius: 5;");
        });
    }

    private Image getPlaceholderImage() {
        return new Image(AlbumCard.class.getResourceAsStream("/images/icons/album_placeholder.png"), 90, 90, true, true);
    }

    public Album getAlbum() {
        return album;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean sel) {
        this.selected = sel;
        if (sel) {
            // Green glow effect
            setEffect(new DropShadow(20, Color.LIMEGREEN));
        } else {
            setEffect(null);
        }
    }

    private void toggleSelected() {
        setSelected(!isSelected());
    }
    
    /**
     * Create the settings button positioned in the top-right corner.
     */
    private Button createSettingsButton() {
        Button settingsButton = new Button();
        settingsButton.setFocusTraversable(false);
        settingsButton.getStyleClass().add("icon-button");
        settingsButton.setStyle("-fx-background-color: transparent; -fx-border-color: transparent; -fx-padding: 0; -fx-focus-color: transparent; -fx-faint-focus-color: transparent;");
        settingsButton.setPrefSize(24, 24);
        
        // Add settings icon
        try {
            ImageView settingsIcon = new ImageView(new Image(
                getClass().getResourceAsStream("/images/icons/settings.png"), 12, 12, true, true));
            settingsButton.setGraphic(settingsIcon);
        } catch (Exception e) {
            // If icon not found, use text
            settingsButton.setText("⚙");
            settingsButton.setStyle(settingsButton.getStyle() + " -fx-font-size: 10px;");
        }
        
        // Position in top-right corner
        StackPane.setAlignment(settingsButton, Pos.TOP_RIGHT);
        StackPane.setMargin(settingsButton, new Insets(2, 2, 0, 0));
        
        // No custom hover handlers – inherit subtle hover from global .button:hover rule
        
        // Handle settings button click
        settingsButton.setOnAction(e -> openAlbumSettingsDialog());
        
        return settingsButton;
    }
    
    /**
     * Create the delete button positioned in the top-left corner.
     */
    private Button createDeleteButton() {
        Button deleteButton = new Button();
        deleteButton.setFocusTraversable(false);
        deleteButton.getStyleClass().add("icon-button");
        deleteButton.setStyle("-fx-background-color: transparent; -fx-border-color: transparent; -fx-padding: 0; -fx-focus-color: transparent; -fx-faint-focus-color: transparent;");
        deleteButton.setPrefSize(24, 24);
        
        // Add trash icon
        try {
            ImageView deleteIcon = new ImageView(new Image(
                getClass().getResourceAsStream("/images/icons/trash.png"), 12, 12, true, true));
            deleteButton.setGraphic(deleteIcon);
        } catch (Exception e) {
            // If icon not found, use text
            deleteButton.setText("🗑");
            deleteButton.setStyle(deleteButton.getStyle() + " -fx-font-size: 10px;");
        }
        
        // Position in top-left corner
        StackPane.setAlignment(deleteButton, Pos.TOP_LEFT);
        StackPane.setMargin(deleteButton, new Insets(2, 0, 0, 2));
        
        // No custom hover handlers – inherit subtle hover from global .button:hover rule
        
        // Handle delete button click
        deleteButton.setOnAction(e -> openDeleteConfirmationDialog());
        
        return deleteButton;
    }
    
    /**
     * Open the delete confirmation dialog.
     */
    private void openDeleteConfirmationDialog() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Album");
        alert.setHeaderText("Delete Album: " + album.getTitle());
        alert.setContentText("Are you sure you want to delete this album and all its songs?\n" +
                            "This action will permanently remove all music files from your computer.\n" +
                            "This cannot be undone.");
        
        // Apply custom CSS if available
        try {
            alert.getDialogPane().getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
        } catch (Exception e) {
            // CSS not found, continue without styling
        }
        
        alert.initOwner(getScene().getWindow());
        
        ButtonType deleteButtonType = new ButtonType("Delete", ButtonType.OK.getButtonData());
        ButtonType cancelButtonType = new ButtonType("Cancel", ButtonType.CANCEL.getButtonData());
        
        alert.getButtonTypes().setAll(deleteButtonType, cancelButtonType);
        
        alert.showAndWait().ifPresent(response -> {
            if (response == deleteButtonType) {
                deleteAlbum();
            }
        });
    }
    
    /**
     * Delete the album and all its associated files.
     */
    private void deleteAlbum() {
        try {
            // Delete physical files first
            deletePhysicalFiles();
            
            // Delete songs from the song repository
            for (com.musicplayer.data.models.Song song : album.getSongs()) {
                songRepository.delete(song.getId());
            }
            
            // Delete from album repository
            albumRepository.delete(album.getId());
            
            // Notify parent container to refresh the UI
            notifyParentOfDeletion();
            
            System.out.println("Album deleted successfully: " + album.getTitle());
            
        } catch (Exception e) {
            e.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Delete Error");
            alert.setHeaderText("Error deleting album");
            alert.setContentText("An error occurred while deleting the album: " + e.getMessage());
            alert.showAndWait();
        }
    }
    
    /**
     * Delete all physical files associated with this album.
     */
    private void deletePhysicalFiles() throws Exception {
        java.util.List<String> filesToDelete = new java.util.ArrayList<>();
        
        // Collect all song file paths
        for (com.musicplayer.data.models.Song song : album.getSongs()) {
            if (song.getFilePath() != null) {
                filesToDelete.add(song.getFilePath());
            }
        }
        
        // Delete custom album art if it exists
        if (album.getCoverArtPath() != null) {
            java.io.File coverArtFile = new java.io.File(album.getCoverArtPath());
            if (coverArtFile.exists() && coverArtFile.getParentFile().getName().equals("album-art")) {
                // Only delete if it's in our custom album-art directory
                filesToDelete.add(album.getCoverArtPath());
            }
        }
        
        // Delete all collected files
        for (String filePath : filesToDelete) {
            java.io.File file = new java.io.File(filePath);
            if (file.exists()) {
                if (!file.delete()) {
                    System.err.println("Failed to delete file: " + filePath);
                } else {
                    System.out.println("Deleted file: " + filePath);
                }
            }
        }
        
        // Try to delete empty directories
        deleteEmptyDirectories(filesToDelete);
    }
    
    /**
     * Delete empty directories after file deletion.
     */
    private void deleteEmptyDirectories(java.util.List<String> deletedFiles) {
        java.util.Set<String> directoriesToCheck = new java.util.HashSet<>();
        
        // Collect unique directories
        for (String filePath : deletedFiles) {
            java.io.File file = new java.io.File(filePath);
            String parentDir = file.getParent();
            if (parentDir != null) {
                directoriesToCheck.add(parentDir);
            }
        }
        
        // Try to delete empty directories
        for (String dirPath : directoriesToCheck) {
            java.io.File dir = new java.io.File(dirPath);
            if (dir.exists() && dir.isDirectory()) {
                String[] contents = dir.list();
                if (contents != null && contents.length == 0) {
                    if (dir.delete()) {
                        System.out.println("Deleted empty directory: " + dirPath);
                    }
                }
            }
        }
    }
    
    /**
     * Notify the parent container that this album has been deleted.
     */
    private void notifyParentOfDeletion() {
        // Find the parent AlbumGridView and request a refresh
        javafx.scene.Node parent = getParent();
        while (parent != null) {
            if (parent instanceof AlbumGridView) {
                ((AlbumGridView) parent).refreshAlbums();
                break;
            }
            parent = parent.getParent();
        }
    }
    
    /**
     * Check if a node is a descendant of another node.
     */
    private boolean isDescendantOf(Object target, javafx.scene.Node parent) {
        if (!(target instanceof javafx.scene.Node)) {
            return false;
        }
        
        javafx.scene.Node node = (javafx.scene.Node) target;
        while (node != null) {
            if (node == parent) {
                return true;
            }
            node = node.getParent();
        }
        return false;
    }
    
    /**
     * Open the album settings dialog which now shows statistics as well as edit options.
     */
    private void openAlbumSettingsDialog() {
        try {
            com.musicplayer.ui.dialogs.AlbumSettingsDialog dlg = new com.musicplayer.ui.dialogs.AlbumSettingsDialog(album);
            dlg.initOwner(getScene().getWindow());

            // When the dialog finishes with a save action we handle the result
            dlg.setOnSave(this::handleAlbumEdit);

            dlg.showAndWait();
        } catch (Exception ex) {
            ex.printStackTrace();
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Could not open album settings dialog");
            alert.setContentText("An error occurred while opening the album settings dialog.");
            alert.showAndWait();
        }
    }

    // Deprecated method kept for compatibility, now delegates to the new dialog
    private void openAlbumEditDialog() {
        openAlbumSettingsDialog();
    }
    
    /**
     * Handle the result of album editing.
     */
    private void handleAlbumEdit(com.musicplayer.ui.dialogs.AlbumSettingsDialog.AlbumEditResult result) {
        try {
            // Update album name if it changed
            if (!result.getNewName().equals(album.getTitle())) {
                // Physically rename the album directory and update all song paths
                try {
                    renameAlbumDirectory(result.getNewName());
                } catch (Exception ex) {
                    ex.printStackTrace();
                    javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
                    alert.setTitle("Rename Error");
                    alert.setHeaderText("Could not rename album directory");
                    alert.setContentText("The album name was changed, but the underlying folder could not be renamed: " + ex.getMessage());
                    alert.showAndWait();
                }

                // Update album object title after successful rename
                album.setTitle(result.getNewName());

                // Update the title label in the UI
                VBox content = (VBox) getChildren().get(0);
                Label titleLabel = (Label) content.getChildren().get(1);
                titleLabel.setText(album.getTitle());
            }
            
            // Handle album art changes
            if (result.getNewImageFile() != null) {
                // Save the custom album art
                saveCustomAlbumArt(result.getNewImageFile());
            } else if (result.isResetToDefault()) {
                // Reset to default album art (clear custom path)
                album.setCoverArtPath(null);
                
                // Refresh the album art display
                VBox content = (VBox) getChildren().get(0);
                ImageView coverView = (ImageView) content.getChildren().get(0);
                
                // Load album art using the updated loader that checks custom art first
                AlbumArtLoader.loadAlbumArt(album)
                    .thenAcceptAsync(image -> {
                        coverView.setImage(image);
                    }, Platform::runLater);
            }
            
            // Persist album and song changes
            albumRepository.save(album);
            album.getSongs().forEach(songRepository::save);
            
        } catch (Exception e) {
            e.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Error processing album edit");
            alert.setContentText("An error occurred while processing the album changes.");
            alert.showAndWait();
        }
    }
    
    /**
     * Save custom album art to the data directory and update the album.
     */
    private void saveCustomAlbumArt(java.io.File imageFile) {
        try {
            // Create custom album art directory if it doesn't exist
            java.nio.file.Path dataDir = java.nio.file.Paths.get(System.getProperty("user.home"), ".simp3", "album-art");
            java.nio.file.Files.createDirectories(dataDir);
            
            // Generate a unique filename based on album ID and title
            String sanitizedTitle = album.getTitle().replaceAll("[^a-zA-Z0-9]", "_");
            String extension = getFileExtension(imageFile.getName());
            String filename = "album_" + album.getId() + "_" + sanitizedTitle + "." + extension;
            java.nio.file.Path targetPath = dataDir.resolve(filename);
            
            // Copy the image file to the custom album art directory
            java.nio.file.Files.copy(imageFile.toPath(), targetPath, 
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            
            // Update the album with the new cover art path
            album.setCoverArtPath(targetPath.toString());
            
            // Update the UI to show the new image immediately
            VBox content = (VBox) getChildren().get(0);
            ImageView coverView = (ImageView) content.getChildren().get(0);
            
            // Load the new image
            Image newImage = new Image(targetPath.toUri().toString(), 90, 90, true, true);
            coverView.setImage(newImage);
            
            System.out.println("Custom album art saved: " + targetPath);
            
        } catch (Exception e) {
            e.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Error saving album art");
            alert.setContentText("Could not save the selected image: " + e.getMessage());
            alert.showAndWait();
        }
    }
    
    /**
     * Get the file extension from a filename.
     */
    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < filename.length() - 1) {
            return filename.substring(lastDotIndex + 1).toLowerCase();
        }
        return "png"; // Default extension
    }

    /**
     * Rename the directory containing this album's songs to match the new album name.
     * Updates all song file paths and cover art paths accordingly.
     */
    private void renameAlbumDirectory(String newAlbumName) throws Exception {
        // Determine old directory: based on the first song that has a file path
        java.io.File oldDir = null;
        for (com.musicplayer.data.models.Song s : album.getSongs()) {
            if (s.getFilePath() != null && !s.getFilePath().isBlank()) {
                oldDir = new java.io.File(s.getFilePath()).getParentFile();
                break;
            }
        }
        if (oldDir == null || !oldDir.exists()) {
            throw new IllegalStateException("Could not determine album directory");
        }

        // Create sanitized new directory name
        String sanitizedName = newAlbumName.replaceAll("[^a-zA-Z0-9 _()-]", "_").trim();
        java.io.File newDir = new java.io.File(oldDir.getParentFile(), sanitizedName);

        if (newDir.equals(oldDir)) {
            // Directory already matches desired name
            return;
        }

        // Perform the move (rename)
        java.nio.file.Files.move(oldDir.toPath(), newDir.toPath());

        // Update song file paths
        for (com.musicplayer.data.models.Song s : album.getSongs()) {
            String oldPath = s.getFilePath();
            if (oldPath != null && oldPath.startsWith(oldDir.getAbsolutePath())) {
                String relative = oldPath.substring(oldDir.getAbsolutePath().length());
                String newPath = newDir.getAbsolutePath() + relative;
                s.setFilePath(newPath);
            }
        }

        // Update cover art path if inside album directory
        if (album.getCoverArtPath() != null && album.getCoverArtPath().startsWith(oldDir.getAbsolutePath())) {
            String relative = album.getCoverArtPath().substring(oldDir.getAbsolutePath().length());
            album.setCoverArtPath(newDir.getAbsolutePath() + relative);
        }
    }
} 