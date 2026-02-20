package com.jvn.editor.ui;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Focused VN asset browser for quick discovery and reuse of project assets. */
public class AssetBrowserView extends BorderPane {
  private final Label titleLabel = new Label("Asset Browser");
  private final Label rootLabel = new Label("No project loaded");
  private final TextField filterField = new TextField();
  private final ListView<AssetItem> listView = new ListView<>();

  private final ImageView previewImage = new ImageView();
  private final Label previewPath = new Label("Select an asset");
  private final Label previewMeta = new Label("");
  private final Button copyPathButton = new Button("Copy Path");
  private final Button openButton = new Button("Open");

  private final List<AssetItem> allItems = new ArrayList<>();
  private File projectRoot;
  private Consumer<File> onOpenAsset;

  public AssetBrowserView() {
    titleLabel.setStyle("-fx-font-weight: 700; -fx-font-size: 13px;");
    rootLabel.setStyle("-fx-text-fill: #99a0af;");

    filterField.setPromptText("Filter assets...");
    filterField.textProperty().addListener((obs, oldValue, newValue) -> applyFilter());

    listView.setCellFactory(lv -> new AssetCell());
    listView.setPlaceholder(new Label("No assets found"));
    listView.getSelectionModel().selectedItemProperty().addListener((obs, oldItem, newItem) -> showPreview(newItem));
    listView.setOnMouseClicked(e -> {
      if (e.getClickCount() < 2) return;
      AssetItem item = listView.getSelectionModel().getSelectedItem();
      if (item == null) return;
      openAsset(item.file());
    });

    previewImage.setPreserveRatio(true);
    previewImage.setFitHeight(150);
    previewImage.setFitWidth(260);
    previewImage.setSmooth(true);

    copyPathButton.setOnAction(e -> {
      AssetItem item = listView.getSelectionModel().getSelectedItem();
      if (item == null) return;
      ClipboardContent content = new ClipboardContent();
      content.putString(item.relativePath());
      Clipboard.getSystemClipboard().setContent(content);
    });

    openButton.setOnAction(e -> {
      AssetItem item = listView.getSelectionModel().getSelectedItem();
      if (item == null) return;
      openAsset(item.file());
    });

    VBox header = new VBox(6, titleLabel, rootLabel, filterField);
    header.setPadding(new Insets(10, 10, 8, 10));

    HBox previewActions = new HBox(8, copyPathButton, openButton);
    previewActions.setAlignment(Pos.CENTER_LEFT);

    VBox previewBox = new VBox(6, previewImage, previewPath, previewMeta, previewActions);
    previewBox.setPadding(new Insets(10));
    previewBox.setStyle("-fx-border-color: #2a2f3a; -fx-border-width: 1 0 0 0;");
    previewPath.setWrapText(true);
    previewMeta.setStyle("-fx-text-fill: #a4acba;");

    VBox center = new VBox(listView, previewBox);
    VBox.setVgrow(listView, Priority.ALWAYS);

    setTop(header);
    setCenter(center);
  }

  public void setOnOpenAsset(Consumer<File> onOpenAsset) {
    this.onOpenAsset = onOpenAsset;
  }

  public void setProjectRoot(File root) {
    this.projectRoot = root;
    refresh();
  }

  public void refresh() {
    allItems.clear();
    listView.getItems().clear();
    previewImage.setImage(null);
    previewPath.setText("Select an asset");
    previewMeta.setText("");

    if (projectRoot == null) {
      rootLabel.setText("No project loaded");
      return;
    }

    rootLabel.setText(projectRoot.getName());
    collectAssets(projectRoot.toPath().resolve("assets"));
    collectAssets(projectRoot.toPath().resolve("game/images"));

    allItems.sort(Comparator.comparing(AssetItem::relativePath, String.CASE_INSENSITIVE_ORDER));
    applyFilter();
  }

  private void collectAssets(Path base) {
    if (base == null || !Files.isDirectory(base)) return;
    try (var stream = Files.walk(base, 8)) {
      stream
          .filter(Files::isRegularFile)
          .forEach(path -> {
            try {
              File file = path.toFile();
              String rel = projectRoot.toPath().relativize(path).toString().replace('\\', '/');
              String type = typeFor(path.getFileName().toString());
              allItems.add(new AssetItem(file, rel, type));
            } catch (Exception ignored) {
            }
          });
    } catch (Exception ignored) {
    }
  }

  private void applyFilter() {
    String query = filterField.getText();
    String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    if (normalized.isEmpty()) {
      listView.setItems(FXCollections.observableArrayList(allItems));
      return;
    }

    List<AssetItem> filtered = new ArrayList<>();
    for (AssetItem item : allItems) {
      String haystack = (item.relativePath() + " " + item.type()).toLowerCase(Locale.ROOT);
      if (haystack.contains(normalized)) {
        filtered.add(item);
      }
    }
    listView.setItems(FXCollections.observableArrayList(filtered));
  }

  private void showPreview(AssetItem item) {
    if (item == null) {
      previewImage.setImage(null);
      previewPath.setText("Select an asset");
      previewMeta.setText("");
      return;
    }

    previewPath.setText(item.relativePath());
    long size = item.file().length();
    previewMeta.setText(item.type() + "  •  " + humanFileSize(size));

    if (isImage(item.file().getName())) {
      try {
        Image image = new Image(item.file().toURI().toString(), 260, 150, true, true, true);
        previewImage.setImage(image);
      } catch (Exception ex) {
        previewImage.setImage(null);
      }
    } else {
      previewImage.setImage(null);
    }
  }

  private void openAsset(File file) {
    if (file == null) return;
    if (onOpenAsset != null) {
      onOpenAsset.accept(file);
      return;
    }
    try {
      java.awt.Desktop.getDesktop().open(file);
    } catch (Exception ignored) {
    }
  }

  private String typeFor(String fileName) {
    String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
    if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp")) return "Image";
    if (lower.endsWith(".ogg") || lower.endsWith(".wav") || lower.endsWith(".mp3") || lower.endsWith(".flac")) return "Audio";
    if (lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".mov")) return "Video";
    if (lower.endsWith(".ttf") || lower.endsWith(".otf")) return "Font";
    if (lower.endsWith(".json") || lower.endsWith(".yaml") || lower.endsWith(".yml") || lower.endsWith(".toml")) return "Data";
    return "File";
  }

  private boolean isImage(String fileName) {
    String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
    return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp");
  }

  private String humanFileSize(long sizeBytes) {
    if (sizeBytes < 1024) return sizeBytes + " B";
    double kb = sizeBytes / 1024.0;
    if (kb < 1024) return String.format(Locale.ROOT, "%.1f KB", kb);
    double mb = kb / 1024.0;
    if (mb < 1024) return String.format(Locale.ROOT, "%.1f MB", mb);
    double gb = mb / 1024.0;
    return String.format(Locale.ROOT, "%.2f GB", gb);
  }

  private record AssetItem(File file, String relativePath, String type) {
  }

  private static final class AssetCell extends ListCell<AssetItem> {
    @Override
    protected void updateItem(AssetItem item, boolean empty) {
      super.updateItem(item, empty);
      if (empty || item == null) {
        setText(null);
        setStyle("");
        return;
      }
      setText(item.relativePath() + "  [" + item.type() + "]");
      setStyle("");
    }
  }
}
