package com.jvn.editor.ui;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.StringConverter;

/**
 * Small in-editor path browser used by Reveal actions.
 *
 * <p>JavaFX exposes file and directory choosers, but it does not expose a
 * portable "reveal this path in Finder/Explorer" API. Keeping reveal inside
 * the editor avoids the platform and packaging failures of {@code Desktop.open}.
 */
public final class EditorPathExplorer {
  private EditorPathExplorer() {
  }

  public static boolean show(File target) {
    return show(null, target);
  }

  public static boolean show(Window owner, File target) {
    File directory = initialDirectory(target);
    if (directory == null) return false;
    File selection = initialSelection(target);
    Runnable open = () -> new ExplorerWindow(owner, directory, selection).show();
    if (Platform.isFxApplicationThread()) {
      open.run();
    } else {
      Platform.runLater(open);
    }
    return true;
  }

  static File initialDirectory(File target) {
    if (target == null) return null;
    File absolute = target.getAbsoluteFile();
    if (absolute.isDirectory()) return absolute;
    File parent = absolute.getParentFile();
    return parent != null && parent.isDirectory() ? parent : null;
  }

  static File initialSelection(File target) {
    if (target == null) return null;
    File absolute = target.getAbsoluteFile();
    return absolute.isFile() ? absolute : null;
  }

  private static final class ExplorerWindow {
    private final Stage stage = new Stage();
    private final TextField pathField = new TextField();
    private final ComboBox<File> roots = new ComboBox<>();
    private final ListView<File> entries = new ListView<>();
    private final Label status = new Label();
    private File directory;
    private File pendingSelection;
    private boolean updatingRoot;

    ExplorerWindow(Window owner, File directory, File selection) {
      this.directory = directory;
      this.pendingSelection = selection;
      if (owner != null) stage.initOwner(owner);
      stage.setTitle("JVN Path Explorer");
      stage.setMinWidth(620);
      stage.setMinHeight(400);
      stage.setScene(buildScene());
      refresh();
    }

    void show() {
      stage.show();
      stage.toFront();
    }

    private Scene buildScene() {
      roots.getItems().setAll(File.listRoots());
      roots.setConverter(new StringConverter<>() {
        @Override public String toString(File file) {
          return file == null ? "" : file.getAbsolutePath();
        }
        @Override public File fromString(String value) {
          return value == null || value.isBlank() ? null : new File(value);
        }
      });
      roots.setOnAction(e -> {
        if (!updatingRoot) navigate(roots.getValue(), null);
      });
      roots.setTooltip(new Tooltip("Filesystem root"));

      pathField.setOnAction(e -> navigatePath(pathField.getText()));
      HBox.setHgrow(pathField, Priority.ALWAYS);

      Button up = new Button("Up", CssIcon.arrowUp("#d6dbe5"));
      up.setOnAction(e -> navigate(directory == null ? null : directory.getParentFile(), null));
      Button refresh = new Button("Refresh", CssIcon.refresh("#d6dbe5"));
      refresh.setOnAction(e -> refresh());
      HBox toolbar = new HBox(8, roots, pathField, up, refresh);
      toolbar.setAlignment(Pos.CENTER_LEFT);
      toolbar.setPadding(new Insets(10));

      entries.setPlaceholder(new Label("This folder is empty or cannot be read."));
      entries.setCellFactory(list -> new ListCell<>() {
        @Override protected void updateItem(File file, boolean empty) {
          super.updateItem(file, empty);
          if (empty || file == null) {
            setText(null);
            setGraphic(null);
            return;
          }
          setText(file.getName().isBlank() ? file.getAbsolutePath() : file.getName());
          setGraphic(ProjectFileIcons.iconFor(ProjectFileIcons.kindFor(file, directory)));
        }
      });
      entries.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, selected) -> {
        status.setText(selected == null ? currentDirectoryText() : selected.getAbsolutePath());
      });
      entries.setOnMouseClicked(event -> {
        if (event.getButton() != MouseButton.PRIMARY || event.getClickCount() != 2) return;
        openSelectedDirectory();
      });
      entries.setOnKeyPressed(event -> {
        if (event.getCode() == KeyCode.ENTER) {
          openSelectedDirectory();
          event.consume();
        }
      });

      Button openFolder = new Button("Open Folder");
      openFolder.setOnAction(e -> openSelectedDirectory());
      Button copy = new Button("Copy Path");
      copy.setOnAction(e -> copySelectedPath());
      Button close = new Button("Close");
      close.setOnAction(e -> stage.close());
      HBox actions = new HBox(8, openFolder, copy, close);
      actions.setAlignment(Pos.CENTER_RIGHT);

      status.setWrapText(true);
      HBox.setHgrow(status, Priority.ALWAYS);
      HBox footer = new HBox(12, status, actions);
      footer.setAlignment(Pos.CENTER_LEFT);
      footer.setPadding(new Insets(10));

      VBox center = new VBox(entries);
      VBox.setVgrow(entries, Priority.ALWAYS);
      BorderPane root = new BorderPane(center);
      root.setTop(toolbar);
      root.setBottom(footer);
      root.getStyleClass().add("welcome-center-root");

      Scene scene = new Scene(root, 860, 560);
      scene.setOnKeyPressed(event -> {
        if (event.getCode() == KeyCode.ESCAPE) {
          stage.close();
          event.consume();
        }
      });
      EditorTheme.apply(scene);
      return scene;
    }

    private void navigatePath(String rawPath) {
      if (rawPath == null || rawPath.isBlank()) return;
      File target = new File(rawPath.trim()).getAbsoluteFile();
      if (target.isDirectory()) {
        navigate(target, null);
      } else if (target.isFile()) {
        navigate(target.getParentFile(), target);
      } else {
        status.setText("Path does not exist: " + target.getAbsolutePath());
      }
    }

    private void openSelectedDirectory() {
      File selected = entries.getSelectionModel().getSelectedItem();
      if (selected != null && selected.isDirectory()) navigate(selected, null);
    }

    private void navigate(File nextDirectory, File selection) {
      if (nextDirectory == null || !nextDirectory.isDirectory()) return;
      directory = nextDirectory.getAbsoluteFile();
      pendingSelection = selection;
      refresh();
    }

    private void refresh() {
      if (directory == null || !directory.isDirectory()) return;
      pathField.setText(directory.getAbsolutePath());
      selectMatchingRoot(directory);
      File[] listed = directory.listFiles();
      List<File> files = listed == null ? List.of() : Arrays.stream(listed)
          .sorted(Comparator.comparing(File::isFile)
              .thenComparing(File::getName, String.CASE_INSENSITIVE_ORDER))
          .toList();
      entries.setItems(FXCollections.observableArrayList(files));
      status.setText(files.size() + " item" + (files.size() == 1 ? "" : "s"));
      if (pendingSelection != null) {
        File wanted = pendingSelection.getAbsoluteFile();
        for (int i = 0; i < files.size(); i++) {
          if (!files.get(i).getAbsoluteFile().equals(wanted)) continue;
          entries.getSelectionModel().select(i);
          entries.scrollTo(i);
          break;
        }
        pendingSelection = null;
      }
    }

    private void selectMatchingRoot(File file) {
      for (File root : roots.getItems()) {
        if (!file.toPath().startsWith(root.toPath())) continue;
        updatingRoot = true;
        try {
          roots.getSelectionModel().select(root);
        } finally {
          updatingRoot = false;
        }
        return;
      }
    }

    private void copySelectedPath() {
      File selected = entries.getSelectionModel().getSelectedItem();
      String path = selected == null ? currentDirectoryText() : selected.getAbsolutePath();
      ClipboardContent content = new ClipboardContent();
      content.putString(path);
      Clipboard.getSystemClipboard().setContent(content);
      status.setText("Copied: " + path);
    }

    private String currentDirectoryText() {
      return directory == null ? "" : directory.getAbsolutePath();
    }
  }
}
