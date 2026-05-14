package com.jvn.editor.ui;

import java.awt.Desktop;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Lightweight developer-mode log browser for the top chrome.
 */
public final class DeveloperLogPanel extends VBox {
  private static final long MAX_TAIL_BYTES = 160 * 1024L;
  private static final int MAX_LOG_FILES = 80;
  private static final DateTimeFormatter DISPLAY_TIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

  private final Supplier<List<Path>> contextRootsSupplier;
  private final TitledPane titledPane;
  private final ComboBox<LogFile> fileSelector = new ComboBox<>();
  private final TextArea logText = new TextArea();
  private final Label statusLabel = new Label("Expand to refresh logs");
  private final Button refreshButton = new Button("Refresh");
  private final Button copyButton = new Button("Copy");
  private final Button revealButton = new Button("Reveal");

  private Task<LogSnapshot> refreshTask;

  public DeveloperLogPanel(String title, Supplier<List<Path>> contextRootsSupplier) {
    this.contextRootsSupplier = contextRootsSupplier;
    getStyleClass().add("developer-log-panel");
    setFillWidth(true);
    setMaxWidth(Double.MAX_VALUE);

    fileSelector.setMaxWidth(Double.MAX_VALUE);
    fileSelector.setCellFactory(list -> createLogFileCell());
    fileSelector.setButtonCell(createLogFileCell());
    fileSelector.valueProperty().addListener((obs, oldValue, newValue) -> loadSelectedLog(newValue));

    refreshButton.setOnAction(e -> refresh());
    refreshButton.setTooltip(new Tooltip("Refresh the discovered log files"));
    copyButton.setOnAction(e -> copyVisibleLog());
    copyButton.setTooltip(new Tooltip("Copy the visible log text"));
    revealButton.setOnAction(e -> revealSelectedLog());
    revealButton.setTooltip(new Tooltip("Reveal the selected log file on disk"));

    HBox controls = new HBox(8, new Label("File"), fileSelector, refreshButton, copyButton, revealButton);
    controls.setAlignment(Pos.CENTER_LEFT);
    controls.setPadding(new Insets(8, 10, 4, 10));
    HBox.setHgrow(fileSelector, Priority.ALWAYS);

    logText.setEditable(false);
    logText.setWrapText(false);
    logText.setPrefRowCount(8);
    logText.setMinHeight(120);
    logText.setStyle("-fx-font-family: 'Menlo', 'Consolas', 'monospace'; -fx-font-size: 11px;");
    VBox.setVgrow(logText, Priority.ALWAYS);

    statusLabel.getStyleClass().add("developer-log-status");
    statusLabel.setPadding(new Insets(0, 10, 8, 10));

    VBox content = new VBox(6, controls, logText, statusLabel);
    content.setFillWidth(true);
    content.setPadding(new Insets(0, 0, 6, 0));

    titledPane = new TitledPane(title == null || title.isBlank() ? "Logs" : title, content);
    titledPane.setExpanded(false);
    titledPane.setMaxWidth(Double.MAX_VALUE);
    titledPane.expandedProperty().addListener((obs, wasExpanded, isExpanded) -> {
      if (Boolean.TRUE.equals(isExpanded)) refresh();
    });
    getChildren().add(titledPane);
  }

  public void refresh() {
    if (!Platform.isFxApplicationThread()) {
      Platform.runLater(this::refresh);
      return;
    }
    if (refreshTask != null && refreshTask.isRunning()) return;
    statusLabel.setText("Scanning logs...");
    refreshTask = new Task<>() {
      @Override
      protected LogSnapshot call() {
        List<LogFile> files = discoverLogFiles();
        return new LogSnapshot(files);
      }
    };
    refreshTask.setOnSucceeded(e -> applySnapshot(refreshTask.getValue()));
    refreshTask.setOnFailed(e -> {
      Throwable failure = refreshTask.getException();
      statusLabel.setText("Log scan failed: " + safeMessage(failure));
      fileSelector.setItems(FXCollections.emptyObservableList());
      logText.clear();
    });
    Thread thread = new Thread(refreshTask, "jvn-developer-log-scan");
    thread.setDaemon(true);
    thread.start();
  }

  private void applySnapshot(LogSnapshot snapshot) {
    List<LogFile> files = snapshot == null ? List.of() : snapshot.files();
    LogFile previous = fileSelector.getValue();
    fileSelector.setItems(FXCollections.observableArrayList(files));
    LogFile next = findSameFile(files, previous);
    if (next == null && !files.isEmpty()) next = files.get(0);
    fileSelector.setValue(next);
    if (next == null) {
      logText.setText("No log-like files were found in the current workspace, project, or user log folders.");
      statusLabel.setText("No logs found");
      return;
    }
    loadSelectedLog(next);
  }

  private List<LogFile> discoverLogFiles() {
    List<LogFile> files = new ArrayList<>();
    for (Path dir : candidateLogDirectories()) {
      if (dir == null || !Files.isDirectory(dir)) continue;
      try (var stream = Files.find(dir, 4,
          (path, attrs) -> attrs.isRegularFile() && isLogLike(path))) {
        stream.forEach(path -> {
          try {
            files.add(new LogFile(
                path.toAbsolutePath().normalize(),
                Files.getLastModifiedTime(path).toInstant(),
                Files.size(path)));
          } catch (Exception ignored) {
            // Skip files that disappear or cannot be statted while scanning.
          }
        });
      } catch (Exception ignored) {
        // A missing or unreadable log directory should not break the UI.
      }
    }
    return files.stream()
        .distinct()
        .sorted(Comparator
            .comparing(LogFile::modified).reversed()
            .thenComparing(file -> file.path().toString()))
        .limit(MAX_LOG_FILES)
        .toList();
  }

  private List<Path> candidateLogDirectories() {
    Set<Path> dirs = new LinkedHashSet<>();
    addLogDir(dirs, defaultUserLogDirectory());
    addContextRoot(dirs, Path.of(System.getProperty("user.dir", ".")));
    List<Path> roots = contextRootsSupplier == null ? List.of() : contextRootsSupplier.get();
    if (roots != null) {
      for (Path root : roots) {
        addContextRoot(dirs, root);
      }
    }
    return new ArrayList<>(dirs);
  }

  private static void addContextRoot(Set<Path> dirs, Path root) {
    if (root == null) return;
    Path normalized = root.toAbsolutePath().normalize();
    if (normalized.getFileName() != null
        && "logs".equalsIgnoreCase(normalized.getFileName().toString())) {
      addLogDir(dirs, normalized);
    }
    addLogDir(dirs, normalized.resolve(".jvn").resolve("logs"));
    addLogDir(dirs, normalized.resolve("logs"));
    addLogDir(dirs, normalized.resolve("build").resolve("tmp"));
    addLogDir(dirs, normalized.resolve(".jvn-gradle-user-home").resolve("daemon"));
  }

  private static void addLogDir(Set<Path> dirs, Path dir) {
    if (dir == null) return;
    dirs.add(dir.toAbsolutePath().normalize());
  }

  private static Path defaultUserLogDirectory() {
    String home = System.getProperty("user.home", "").trim();
    if (home.isEmpty()) return Path.of(".jvn", "logs");
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    if (os.contains("mac")) return Path.of(home, "Library", "Logs", "JVN");
    return Path.of(home, ".jvn", "logs");
  }

  private static boolean isLogLike(Path path) {
    if (path == null || path.getFileName() == null) return false;
    String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
    return name.endsWith(".log")
        || name.endsWith(".out")
        || name.endsWith(".err")
        || name.endsWith(".txt")
        || name.contains("crash");
  }

  private void loadSelectedLog(LogFile file) {
    if (file == null) {
      revealButton.setDisable(true);
      copyButton.setDisable(true);
      return;
    }
    try {
      logText.setText(readTail(file.path()));
      logText.positionCaret(logText.getLength());
      revealButton.setDisable(false);
      copyButton.setDisable(false);
      statusLabel.setText(file.statusText());
    } catch (Exception ex) {
      logText.setText("Could not read log:\n" + file.path() + "\n\n" + safeMessage(ex));
      statusLabel.setText("Read failed: " + file.path().getFileName());
    }
  }

  private static String readTail(Path path) throws Exception {
    long size = Files.size(path);
    long offset = Math.max(0L, size - MAX_TAIL_BYTES);
    try (InputStream in = Files.newInputStream(path)) {
      if (offset > 0) in.skipNBytes(offset);
      byte[] data = in.readAllBytes();
      String text = new String(data, StandardCharsets.UTF_8);
      if (offset > 0) {
        return "[showing last " + MAX_TAIL_BYTES / 1024 + " KB]\n\n" + text;
      }
      return text;
    }
  }

  private void copyVisibleLog() {
    ClipboardContent content = new ClipboardContent();
    content.putString(logText.getText() == null ? "" : logText.getText());
    Clipboard.getSystemClipboard().setContent(content);
    statusLabel.setText("Copied visible log text");
  }

  private void revealSelectedLog() {
    LogFile file = fileSelector.getValue();
    if (file == null || file.path().getParent() == null) return;
    try {
      Desktop.getDesktop().open(file.path().getParent().toFile());
      statusLabel.setText("Opened log folder");
    } catch (Exception ex) {
      statusLabel.setText("Could not open log folder: " + safeMessage(ex));
    }
  }

  private static ListCell<LogFile> createLogFileCell() {
    return new ListCell<>() {
      @Override
      protected void updateItem(LogFile item, boolean empty) {
        super.updateItem(item, empty);
        setText(empty || item == null ? "" : item.displayText());
      }
    };
  }

  private static LogFile findSameFile(List<LogFile> files, LogFile previous) {
    if (previous == null) return null;
    for (LogFile file : files) {
      if (file.path().equals(previous.path())) return file;
    }
    return null;
  }

  private static String safeMessage(Throwable ex) {
    if (ex == null) return "Unknown error";
    String message = ex.getMessage();
    return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message.trim();
  }

  private record LogSnapshot(List<LogFile> files) {}

  private record LogFile(Path path, Instant modified, long size) {
    private String displayText() {
      return path.getFileName() + " - " + DISPLAY_TIME.format(modified);
    }

    private String statusText() {
      return path + " (" + formatBytes(size) + ", modified " + DISPLAY_TIME.format(modified) + ")";
    }

    private static String formatBytes(long bytes) {
      if (bytes < 1024L) return bytes + " B";
      double kb = bytes / 1024.0;
      if (kb < 1024.0) return String.format(Locale.ROOT, "%.1f KB", kb);
      return String.format(Locale.ROOT, "%.1f MB", kb / 1024.0);
    }
  }
}
