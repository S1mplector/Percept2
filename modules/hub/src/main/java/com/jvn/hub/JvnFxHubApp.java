package com.jvn.hub;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public final class JvnFxHubApp extends Application {

  private final AtomicReference<Process> runningProcess = new AtomicReference<>();
  private Path projectRoot;
  private Label statusLabel;
  private Label detailLabel;
  private TextArea logArea;
  private final List<Button> actionButtons = new ArrayList<>();
  private CheckBox safeMode;
  private CheckBox developerMode;
  private HubGradleOptions options = HubGradleOptions.standard(false, false);

  @Override public void start(Stage stage) {
    projectRoot = JvnFxHubLauncher.projectRoot();
    BorderPane root = new BorderPane();
    root.getStyleClass().add("hub-shell");
    root.setTop(header(stage));
    root.setCenter(body());

    Scene scene = new Scene(root, 1020, 720);
    var css = JvnFxHubApp.class.getResource("/com/jvn/hub/fx-hub.css");
    if (css != null) scene.getStylesheets().add(css.toExternalForm());
    stage.setTitle("JVN Engine Hub");
    stage.setMinWidth(860);
    stage.setMinHeight(620);
    stage.setScene(scene);
    stage.setOnCloseRequest(e -> cancelRunning());
    stage.show();
    refreshIncomingCount();
  }

  private VBox header(Stage stage) {
    Label title = new Label("JVN Engine Hub");
    title.getStyleClass().add("hub-title");
    Label subtitle = new Label("Modern command center for launches, builds, updates, and workspace recovery.");
    subtitle.getStyleClass().add("hub-subtitle");
    VBox titleBlock = new VBox(3, title, subtitle);

    statusLabel = new Label("Ready");
    statusLabel.getStyleClass().add("hub-status");
    Button classic = smallButton("Classic Hub");
    classic.setTooltip(new Tooltip("Switch future launches back to Classic Hub and open it now."));
    classic.setOnAction(e -> {
      if (runningProcess.get() != null) {
        appendLog("[hub] finish or cancel the running task before switching hub views.");
        return;
      }
      HubUiPreferences.saveMode(HubUiMode.CLASSIC);
      Platform.setImplicitExit(false);
      stage.hide();
      JvnHub.runClassic(projectRoot);
    });
    Button about = smallButton("About");
    about.setOnAction(e -> showAbout());

    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    HBox row = new HBox(12, titleBlock, spacer, statusLabel, about, classic);
    row.setAlignment(Pos.CENTER_LEFT);
    VBox header = new VBox(row);
    header.getStyleClass().add("hub-header");
    return header;
  }

  private ScrollPane body() {
    safeMode = new CheckBox("Safe Mode");
    developerMode = new CheckBox("Developer Mode");
    safeMode.getStyleClass().add("hub-toggle");
    developerMode.getStyleClass().add("hub-toggle");
    safeMode.selectedProperty().addListener((obs, oldValue, value) -> updateOptions());
    developerMode.selectedProperty().addListener((obs, oldValue, value) -> updateOptions());

    Button gradleOptions = actionButton("Gradle Options", "Configure developer Gradle flags.");
    gradleOptions.setOnAction(e -> showGradleOptions());

    FlowPane modeRow = new FlowPane(10, 8, safeMode, developerMode, gradleOptions);
    modeRow.setAlignment(Pos.CENTER_LEFT);
    modeRow.getStyleClass().add("hub-mode-row");

    FlowPane overview = new FlowPane(10, 10,
        statCard("Workspace", projectRoot.getFileName() == null ? projectRoot.toString() : projectRoot.getFileName().toString()),
        statCard("Update Channel", HubUpdateTarget.REMOTE_REF),
        statCard("View", "JavaFX Preview"),
        statCard("Fallback", "Classic Hub"));
    overview.getStyleClass().add("hub-overview");

    GridPane actions = new GridPane();
    actions.setHgap(10);
    actions.setVgap(10);
    addAction(actions, 0, 0, "Run Editor", "Launch the full JVN editor.", () -> runGradle(":editor:run", "Run Editor"));
    addAction(actions, 1, 0, "Run Launcher", "Launch the project launcher.", () -> runGradle(":editor:runLauncher", "Run Launcher"));
    addAction(actions, 0, 1, "Build All", "Compile every module.", () -> runGradle("build", "Build All"));
    addAction(actions, 1, 1, "Run Tests", "Execute the test suite.", () -> runGradle("test", "Run Tests"));
    addAction(actions, 0, 2, "Build Shortcuts", "Install native OS shortcuts.", this::installShortcuts);
    addAction(actions, 1, 2, "Update Engine", "Pull and rebase from origin/stable.", this::updateEngine);
    addAction(actions, 0, 3, "Help Center", "Open the editor Help Center.", () -> runGradle(":editor:runHelpCenter", "Help Center"));
    addAction(actions, 1, 3, "Cancel Task", "Stop the running task.", this::cancelRunning, false);

    detailLabel = new Label("Choose an action. Output appears below.");
    detailLabel.getStyleClass().add("hub-card-detail");
    logArea = new TextArea();
    logArea.getStyleClass().add("hub-log");
    logArea.setEditable(false);
    logArea.setWrapText(false);
    logArea.setPrefRowCount(15);

    VBox actionCard = card("Command Deck", "Primary engine operations with the same backend as Classic Hub.", actions);
    VBox activityCard = card("Live Console", "Process output, update checks, and task status.", new VBox(8, detailLabel, logArea));
    VBox content = new VBox(12, hero(), overview, modeRow, actionCard, activityCard);
    content.getStyleClass().add("hub-body");
    ScrollPane scroll = new ScrollPane(content);
    scroll.setFitToWidth(true);
    scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
    return scroll;
  }

  private void addAction(GridPane grid, int col, int row, String title, String detail, Runnable action) {
    addAction(grid, col, row, title, detail, action, true);
  }

  private void addAction(
      GridPane grid,
      int col,
      int row,
      String title,
      String detail,
      Runnable action,
      boolean disableWhileRunning) {
    Button button = actionButton(title, detail, disableWhileRunning);
    button.setOnAction(e -> action.run());
    button.setMaxWidth(Double.MAX_VALUE);
    GridPane.setHgrow(button, Priority.ALWAYS);
    grid.add(button, col, row);
  }

  private VBox hero() {
    Label kicker = new Label("New Hub Preview");
    kicker.getStyleClass().add("hub-kicker");
    Label headline = new Label("A cleaner control room for JVN");
    headline.getStyleClass().add("hub-headline");
    Label copy = new Label("Launch the editor, build the engine, pull stable updates, install shortcuts, and fall back to Classic Hub without leaving this window.");
    copy.getStyleClass().add("hub-hero-copy");
    copy.setWrapText(true);
    VBox hero = new VBox(6, kicker, headline, copy);
    hero.getStyleClass().add("hub-hero");
    return hero;
  }

  private VBox statCard(String label, String value) {
    Label labelNode = new Label(label);
    labelNode.getStyleClass().add("hub-stat-label");
    Label valueNode = new Label(value);
    valueNode.getStyleClass().add("hub-stat-value");
    VBox card = new VBox(4, labelNode, valueNode);
    card.getStyleClass().add("hub-stat");
    card.setMinWidth(170);
    return card;
  }

  private Button actionButton(String text, String tooltip) {
    return actionButton(text, tooltip, true);
  }

  private Button actionButton(String text, String tooltip, boolean disableWhileRunning) {
    Button button = new Button(text);
    button.getStyleClass().add("hub-action");
    button.setTooltip(new Tooltip(tooltip));
    if (disableWhileRunning) actionButtons.add(button);
    return button;
  }

  private Button smallButton(String text) {
    Button button = new Button(text);
    button.getStyleClass().add("hub-action");
    return button;
  }

  private VBox card(String title, String detail, javafx.scene.Node content) {
    Label titleLabel = new Label(title);
    titleLabel.getStyleClass().add("hub-card-title");
    Label detailLabel = new Label(detail);
    detailLabel.getStyleClass().add("hub-card-detail");
    VBox box = new VBox(10, titleLabel, detailLabel, content);
    box.getStyleClass().add("hub-card");
    return box;
  }

  private void updateOptions() {
    options = new HubGradleOptions(
        developerMode.isSelected(),
        safeMode.isSelected(),
        options.stacktrace(),
        options.infoLogging(),
        options.debugLogging(),
        options.offline(),
        options.refreshDependencies(),
        options.noBuildCache(),
        options.noDaemon(),
        options.extraArgs());
  }

  private void runGradle(String task, String label) {
    runCommand(label, HubCommandFactory.gradleTask(projectRoot, task, options));
  }

  private void updateEngine() {
    if (!confirmUpdatePreflight()) return;
    runCommand("Update Engine", HubCommandFactory.updateEngine(options.safeMode()));
  }

  private boolean confirmUpdatePreflight() {
    if (!Files.isDirectory(projectRoot.resolve(".git"))) {
      return confirm(
          "Git Status Unavailable",
          "This checkout does not expose a .git directory.",
          "The hub can still try the stable update command, but it cannot preflight local changes first.");
    }
    runQuiet(HubCommandFactory.fetchStable(), 45);
    String status = runCapture(List.of("git", "status", "--porcelain=v1", "--branch"), 10).strip();
    boolean hasLocalChanges = status.lines().anyMatch(line -> !line.startsWith("##"));
    boolean interrupted = Files.isDirectory(projectRoot.resolve(".git/rebase-merge"))
        || Files.isDirectory(projectRoot.resolve(".git/rebase-apply"))
        || Files.isRegularFile(projectRoot.resolve(".git/MERGE_HEAD"))
        || Files.isRegularFile(projectRoot.resolve(".git/CHERRY_PICK_HEAD"));
    if (!hasLocalChanges && !interrupted) return true;

    StringBuilder detail = new StringBuilder();
    if (interrupted) {
      detail.append("Git appears to have an interrupted merge, rebase, or cherry-pick state.\n\n");
    }
    if (hasLocalChanges) {
      detail.append(options.safeMode()
          ? "Safe Mode will run update with --autostash for tracked local changes.\n\n"
          : "Local changes are present. Classic Hub has the fuller recovery flow; this preview can continue only if you choose to proceed.\n\n");
      detail.append(limitLines(status, 12));
    }
    return confirm("Confirm Engine Update", "Review local checkout state before pulling " + HubUpdateTarget.REMOTE_REF + ".", detail.toString());
  }

  private boolean confirm(String title, String header, String detail) {
    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
    alert.setTitle(title);
    alert.setHeaderText(header);
    alert.setContentText(detail);
    styleDialog(alert);
    return alert.showAndWait()
        .filter(type -> type == ButtonType.OK)
        .isPresent();
  }

  private void installShortcuts() {
    HubShortcutCommand shortcut = HubCommandFactory.shortcutInstaller(projectRoot);
    Path script = shortcut.script();
    List<String> cmd = shortcut.command();
    if (!Files.isRegularFile(script)) {
      setStatus("Shortcut installer missing", "Missing " + script.toAbsolutePath());
      return;
    }
    runCommand("Build Shortcuts", cmd);
  }

  private void runCommand(String label, List<String> command) {
    if (runningProcess.get() != null) {
      appendLog("[hub] a task is already running; cancel it before starting another.");
      return;
    }
    setButtonsEnabled(false);
    setStatus("Running: " + label, String.join(" ", command));
    appendLog("$ " + String.join(" ", command));
    Task<Integer> task = new Task<>() {
      @Override protected Integer call() {
        ProcessBuilder pb = new ProcessBuilder(command).directory(projectRoot.toFile()).redirectErrorStream(true);
        if (isPackagedGradleHome(projectRoot.resolve(".jvn-gradle-user-home")) && isGradleWrapperCommand(command)) {
          pb.environment().put("GRADLE_USER_HOME", projectRoot.resolve(".jvn-gradle-user-home").toAbsolutePath().toString());
        }
        try {
          Process process = pb.start();
          runningProcess.set(process);
          try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
              String captured = line;
              Platform.runLater(() -> appendLog(captured));
            }
          }
          return process.waitFor();
        } catch (IOException ex) {
          Platform.runLater(() -> appendLog("[hub] failed to start process: " + ex.getMessage()));
          return -1;
        } catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
          return -1;
        }
      }

      @Override protected void succeeded() {
        int exit = getValue() == null ? -1 : getValue();
        runningProcess.set(null);
        setButtonsEnabled(true);
        setStatus((exit == 0 ? "Done: " : "Failed: ") + label, "Exit " + exit);
        if ("Update Engine".equals(label)) refreshIncomingCount();
      }
    };
    Thread thread = new Thread(task, "jvn-fx-hub-" + label.toLowerCase(Locale.ROOT).replace(' ', '-'));
    thread.setDaemon(true);
    thread.start();
  }

  private void cancelRunning() {
    Process process = runningProcess.getAndSet(null);
    if (process == null) return;
    process.descendants().forEach(ProcessHandle::destroy);
    process.destroy();
    appendLog("[hub] cancelled running task.");
    setButtonsEnabled(true);
  }

  private void refreshIncomingCount() {
    Task<Integer> task = new Task<>() {
      @Override protected Integer call() {
        runQuiet(HubCommandFactory.fetchStable(), 45);
        String output = runCapture(HubCommandFactory.incomingCount(), 10);
        try {
          return Math.max(0, Integer.parseInt(output.strip()));
        } catch (Exception ignored) {
          return -1;
        }
      }

      @Override protected void succeeded() {
        int count = getValue() == null ? -1 : getValue();
        if (count > 0) {
          statusLabel.setText(count + " update" + (count == 1 ? "" : "s") + " from " + HubUpdateTarget.REMOTE_REF);
        } else if (count == 0) {
          statusLabel.setText("Up to date with " + HubUpdateTarget.REMOTE_REF);
        } else {
          statusLabel.setText("Update status unavailable");
        }
      }
    };
    Thread thread = new Thread(task, "jvn-fx-hub-update-check");
    thread.setDaemon(true);
    thread.start();
  }

  private void showGradleOptions() {
    TextField extra = new TextField(options.extraArgs());
    extra.getStyleClass().add("hub-field");
    CheckBox stacktrace = optionBox("Stacktrace", options.stacktrace());
    CheckBox info = optionBox("Info logging", options.infoLogging());
    CheckBox debug = optionBox("Debug logging", options.debugLogging());
    CheckBox offline = optionBox("Offline mode", options.offline());
    CheckBox refresh = optionBox("Refresh dependencies", options.refreshDependencies());
    CheckBox noCache = optionBox("No build cache", options.noBuildCache());
    CheckBox noDaemon = optionBox("No daemon", options.noDaemon());
    VBox content = new VBox(8, stacktrace, info, debug, offline, refresh, noCache, noDaemon, new Label("Extra arguments"), extra);
    content.setPadding(new Insets(8));
    Alert alert = new Alert(Alert.AlertType.NONE);
    alert.setTitle("Gradle Options");
    alert.getDialogPane().setContent(content);
    styleDialog(alert);
    ButtonType apply = new ButtonType("Apply", ButtonBar.ButtonData.OK_DONE);
    alert.getButtonTypes().setAll(apply, ButtonType.CANCEL);
    alert.showAndWait().ifPresent(type -> {
      if (type == apply) {
        options = new HubGradleOptions(
            developerMode.isSelected(),
            safeMode.isSelected(),
            stacktrace.isSelected(),
            info.isSelected(),
            debug.isSelected(),
            offline.isSelected(),
            refresh.isSelected() && !offline.isSelected(),
            noCache.isSelected(),
            noDaemon.isSelected(),
            extra.getText() == null ? "" : extra.getText().trim());
        setStatus("Gradle options updated", HubCommandFactory.developerGradleOptions(options).toString());
      }
    });
  }

  private CheckBox optionBox(String text, boolean selected) {
    CheckBox box = new CheckBox(text);
    box.setSelected(selected);
    box.getStyleClass().add("hub-toggle");
    return box;
  }

  private void showAbout() {
    Alert alert = new Alert(Alert.AlertType.INFORMATION);
    alert.setTitle("JVN Engine Hub");
    alert.setHeaderText("JVN Engine Hub");
    alert.setContentText("View: JavaFX preview\nWorkspace: " + projectRoot.toAbsolutePath()
        + "\nUpdates: " + HubUpdateTarget.REMOTE_REF
        + "\nPreference file: " + HubUiPreferences.preferenceFile());
    styleDialog(alert);
    alert.showAndWait();
  }

  private void styleDialog(Alert alert) {
    var css = JvnFxHubApp.class.getResource("/com/jvn/hub/fx-hub.css");
    if (css != null) alert.getDialogPane().getStylesheets().add(css.toExternalForm());
    alert.getDialogPane().getStyleClass().add("hub-dialog");
  }

  private void setButtonsEnabled(boolean enabled) {
    actionButtons.forEach(button -> button.setDisable(!enabled));
  }

  private void setStatus(String status, String detail) {
    statusLabel.setText(status);
    detailLabel.setText(detail == null || detail.isBlank() ? status : detail);
  }

  private void appendLog(String line) {
    if (line == null) return;
    logArea.appendText(line + "\n");
  }

  private String runCapture(List<String> command, long timeoutSeconds) {
    try {
      Process process = new ProcessBuilder(command).directory(projectRoot.toFile()).redirectErrorStream(true).start();
      String output;
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
        output = reader.lines().reduce("", (a, b) -> a + b + "\n");
      }
      process.waitFor(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS);
      return output == null ? "" : output;
    } catch (Exception ignored) {
      return "";
    }
  }

  private void runQuiet(List<String> command, long timeoutSeconds) {
    try {
      Process process = new ProcessBuilder(command).directory(projectRoot.toFile()).redirectErrorStream(true).start();
      process.waitFor(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS);
    } catch (Exception ignored) {
      // Non-critical background refresh.
    }
  }

  private static String limitLines(String text, int maxLines) {
    if (text == null || text.isBlank()) return "";
    List<String> lines = text.lines().limit(maxLines + 1L).toList();
    if (lines.size() <= maxLines) return String.join("\n", lines);
    return String.join("\n", lines.subList(0, maxLines)) + "\n...";
  }

  private static boolean isGradleWrapperCommand(List<String> command) {
    if (command.isEmpty()) return false;
    Path executable = Path.of(command.get(0)).getFileName();
    if (executable == null) return false;
    String name = executable.toString();
    return name.equals("gradlew") || name.equals("gradlew.bat");
  }

  private static boolean isPackagedGradleHome(Path gradleHome) {
    return Files.isDirectory(gradleHome)
        && Files.isRegularFile(gradleHome.resolve(".jvn-packaged-gradle-cache.properties"));
  }
}
