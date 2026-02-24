package com.jvn.editor.ui;

import java.awt.Desktop;
import java.io.File;
import java.util.List;
import java.util.function.Function;

import javafx.scene.control.TextField;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.stage.FileChooser;

/**
 * Shared helpers for asset picker UX across visual editors.
 */
final class AssetPickerSupport {
  private AssetPickerSupport() {
  }

  static void addAssetFilters(FileChooser chooser) {
    if (chooser == null) return;
    chooser.getExtensionFilters().setAll(
        new FileChooser.ExtensionFilter(
            "Common Assets",
            "*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif", "*.webp", "*.svg",
            "*.ogg", "*.mp3", "*.wav", "*.flac", "*.m4a",
            "*.mp4", "*.webm", "*.mov",
            "*.ttf", "*.otf",
            "*.json", "*.txt", "*.layout", "*.style", "*.menu", "*.vns"),
        new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif", "*.webp", "*.svg"),
        new FileChooser.ExtensionFilter("Audio", "*.ogg", "*.mp3", "*.wav", "*.flac", "*.m4a"),
        new FileChooser.ExtensionFilter("Video", "*.mp4", "*.webm", "*.mov"),
        new FileChooser.ExtensionFilter("Fonts", "*.ttf", "*.otf"),
        new FileChooser.ExtensionFilter("All Files", "*.*"));
  }

  static void installAssetDrop(TextField field, Function<File, String> toRelativePath) {
    if (field == null || toRelativePath == null) return;
    field.setPromptText("Drop an asset here or use browse/import");

    field.setOnDragOver(event -> {
      Dragboard db = event.getDragboard();
      if (db.hasFiles()) {
        event.acceptTransferModes(TransferMode.COPY);
      }
      event.consume();
    });
    field.setOnDragEntered(event -> {
      if (event.getDragboard().hasFiles() && !field.getStyleClass().contains("asset-drop-active")) {
        field.getStyleClass().add("asset-drop-active");
      }
      event.consume();
    });
    field.setOnDragExited(event -> {
      field.getStyleClass().remove("asset-drop-active");
      event.consume();
    });
    field.setOnDragDropped(event -> {
      boolean success = false;
      List<File> files = event.getDragboard().getFiles();
      if (files != null && !files.isEmpty()) {
        String mapped = toRelativePath.apply(files.get(0));
        if (mapped != null && !mapped.isBlank()) {
          field.setText(mapped);
          success = true;
        }
      }
      field.getStyleClass().remove("asset-drop-active");
      event.setDropCompleted(success);
      event.consume();
    });
  }

  static boolean revealFile(File file) {
    try {
      if (file == null) return false;
      File target = file;
      if (!target.exists()) {
        File parent = target.getParentFile();
        if (parent == null || !parent.exists()) return false;
        target = parent;
      }
      if (!Desktop.isDesktopSupported()) return false;
      File openTarget = target.isDirectory() ? target : target.getParentFile();
      if (openTarget == null || !openTarget.exists()) return false;
      Desktop.getDesktop().open(openTarget);
      return true;
    } catch (Exception ignored) {
      return false;
    }
  }
}

