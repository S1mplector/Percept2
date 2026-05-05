package com.musicplayer.ui.dialogs;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import com.musicplayer.data.models.Album;
import com.musicplayer.data.models.Song;
import com.musicplayer.ui.controllers.AlbumEditController;

import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * Combined "Album Settings" dialog that shows album statistics (read-only)
 * on top of the existing edit form (title & cover-art editing).
 */
public class AlbumSettingsDialog extends Stage {

    /**
     * Wrapped result identical to the result previously emitted by AlbumEditController.
     */
    public static class AlbumEditResult {
        private final String newName;
        private final java.io.File newImageFile;
        private final boolean resetToDefault;

        public AlbumEditResult(String newName, java.io.File newImageFile, boolean resetToDefault) {
            this.newName = newName;
            this.newImageFile = newImageFile;
            this.resetToDefault = resetToDefault;
        }

        public String getNewName() { return newName; }
        public java.io.File getNewImageFile() { return newImageFile; }
        public boolean isResetToDefault() { return resetToDefault; }
    }

    private final Album album;
    private Consumer<AlbumEditResult> onSave;

    public AlbumSettingsDialog(Album album) throws Exception {
        this.album = album;
        initModality(Modality.WINDOW_MODAL);
        setTitle("Album Settings - " + album.getTitle());

        VBox root = new VBox(12);
        root.setPadding(new Insets(20));

        // Stats section
        VBox statsBox = createStatsSection();

        // Edit section (load existing FXML)
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/album-edit.fxml"));
        DialogPane editPane = loader.load();
        AlbumEditController controller = loader.getController();
        controller.setAlbum(album);

        // Hook save/cancel from controller
        controller.setOnSave(res -> {
            if (onSave != null) {
                onSave.accept(new AlbumEditResult(res.getNewName(), res.getNewImageFile(), res.isResetToDefault()));
            }
            close();
        });
        Button saveBtn = controller.getSaveButton();
        Button cancelBtn = controller.getCancelButton();

        saveBtn.setOnAction(e -> controller.saveChanges());
        cancelBtn.setOnAction(e -> close());

        // Assemble root
        root.getChildren().addAll(statsBox, editPane);

        Scene scene = new Scene(root);
        // Apply same stylesheet if available
        try {
            scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
        } catch (Exception ignored) { }

        setScene(scene);
    }

    /**
     * Provide callback for save action.
     */
    public void setOnSave(Consumer<AlbumEditResult> onSave) {
        this.onSave = onSave;
    }

    private VBox createStatsSection() {
        int totalSongs = album.getSongs() != null ? album.getSongs().size() : 0;
        Map<String, Integer> formatCounts = new HashMap<>();
        Song mostPlayed = null;
        for (Song s : album.getSongs()) {
            // File format
            if (s.getFilePath() != null) {
                String ext = getExtension(s.getFilePath());
                formatCounts.merge(ext, 1, Integer::sum);
            }
            // Play count
            if (mostPlayed == null || s.getPlayCount() > mostPlayed.getPlayCount()) {
                mostPlayed = s;
            }
        }
        StringBuilder formatSummary = new StringBuilder();
        formatCounts.forEach((fmt, cnt) -> formatSummary.append(fmt.toUpperCase()).append(": ").append(cnt).append("  "));

        VBox box = new VBox(4);
        box.setAlignment(Pos.CENTER_LEFT);
        Label header = new Label("Album Statistics");
        header.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        box.getChildren().addAll(
                header,
                new Label("Total songs: " + totalSongs),
                new Label("Formats: " + (formatSummary.length() == 0 ? "N/A" : formatSummary.toString().trim())),
                new Label("Most played: " + (mostPlayed != null ? mostPlayed.getTitle() + " (" + mostPlayed.getPlayCount() + ")" : "N/A"))
        );
        return box;
    }

    private String getExtension(String path) {
        int idx = path.lastIndexOf('.');
        if (idx != -1 && idx < path.length() - 1) {
            return path.substring(idx + 1).toLowerCase();
        }
        return "";
    }

    // No additional overrides needed; we rely on Stage's built-in showAndWait and initOwner methods.
} 