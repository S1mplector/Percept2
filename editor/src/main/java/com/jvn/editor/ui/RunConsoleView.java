package com.jvn.editor.ui;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.Node;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.FileChooser;

/**
 * A run console that filters verbose Gradle output and presents
 * friendly engine-state messages with color-coded output.
 */
public class RunConsoleView extends BorderPane {

    public enum EngineState { BUILDING, STARTING, RUNNING, STOPPED, FAILED }

    @FunctionalInterface
    public interface ProcessStarter {
        Process start() throws Exception;
    }

    private final TextFlow outputFlow = new TextFlow();
    private final ScrollPane scrollPane = new ScrollPane(outputFlow);
    private final Label stateLabel = new Label();
    private final Label elapsedLabel = new Label();
    private final CheckBox showAllToggle = new CheckBox("Show build output");
    private final Button runBtn = iconButton("icon-runtime-run", "Run current build again");
    private final Button copyBtn = iconButton("icon-runtime-copy", "Copy traceback to clipboard");
    private final Button clearBtn = iconButton("icon-runtime-clear", "Clear output");
    private final Button stopBtn = iconButton("icon-runtime-stop", "Stop current build");

    // Enhanced UI components
    private final TextField searchField = new TextField();
    private final ComboBox<String> logLevelFilter = new ComboBox<>(
        FXCollections.observableArrayList("All", "Engine", "Errors", "Warnings"));
    private final ToggleButton autoScrollBtn = new ToggleButton("Auto-scroll");
    private final ToggleButton wordWrapBtn = new ToggleButton("Wrap");
    private final Label lineCountLabel = new Label("0 lines");
    private final Label errorCountLabel = new Label("0 errors");
    private final Label warnCountLabel = new Label("0 warnings");

    // Raw line buffer for search/filter replay
    private final List<String> rawLineBuffer = new ArrayList<>();

    private EngineState engineState = EngineState.BUILDING;
    private Process runningProcess;
    private ProcessStarter processStarter;
    private long startTime = System.currentTimeMillis();
    private int lineCount = 0;
    private int errorCount = 0;
    private int warnCount = 0;
    private String currentSearchTerm = "";

    // Patterns for Gradle noise lines we suppress by default
    private static final Pattern GRADLE_NOISE = Pattern.compile(
        "^(> Task |> Configure |BUILD SUCCESSFUL|BUILD FAILED|Deprecated Gradle|" +
        "\\d+ actionable|See the profiling|Starting a Gradle|To honour the JVM|" +
        "Starting process|Successfully started|> Connecting to|Generating |" +
        "Note: |Download |Execution optimizations|> Building|" +
        "\\s*$)" // blank lines
    );

    // Patterns for engine lifecycle messages we always surface
    private static final Pattern ENGINE_MSG = Pattern.compile(
        "^\\[(JVN|Engine|Scene|Audio|Script|VN|Menu|Runtime|Init|Asset|Error|WARN|INFO)\\]",
        Pattern.CASE_INSENSITIVE
    );

    // Patterns for errors and warnings
    private static final Pattern ERROR_LINE = Pattern.compile(
        "(Exception|Error|FAILED|error:|fatal:|\\bat line \\d+)",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern WARN_LINE = Pattern.compile(
        "(Warning|WARN|deprecated|could not)",
        Pattern.CASE_INSENSITIVE
    );

    private static final String STATE_BUILDING_CLASS = "run-console-state-building";
    private static final String STATE_STARTING_CLASS = "run-console-state-starting";
    private static final String STATE_RUNNING_CLASS = "run-console-state-running";
    private static final String STATE_STOPPED_CLASS = "run-console-state-stopped";
    private static final String STATE_FAILED_CLASS = "run-console-state-failed";
    private static final String COUNTER_ERROR_ACTIVE_CLASS = "run-console-meta-alert";
    private static final String COUNTER_WARN_ACTIVE_CLASS = "run-console-meta-warn";

    private static final String LOG_COLOR_ERROR = "#f38ba8";
    private static final String LOG_COLOR_WARNING = "#f0b673";
    private static final String LOG_COLOR_ENGINE = "#dbe4f0";
    private static final String LOG_COLOR_NOISE = "#6b7381";
    private static final String LOG_COLOR_NORMAL = "#b5bfd0";
    private static final String LOG_COLOR_INFO = "#8ab4f8";

    public RunConsoleView(String title) {
        getStyleClass().add("run-console-root");

        // ─── Menu Bar ────────────────────────────────────────────────
        MenuBar menuBar = createMenuBar(title);

        // ─── Tool Bar ────────────────────────────────────────────────
        ToolBar toolBar = createToolBar();

        // ─── Header info row ─────────────────────────────────────────
        stateLabel.getStyleClass().add("run-console-state");
        elapsedLabel.getStyleClass().add("run-console-elapsed");

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("run-console-title");

        HBox infoRow = new HBox(10, titleLabel, stateLabel, elapsedLabel);
        infoRow.setAlignment(Pos.CENTER_LEFT);
        infoRow.setPadding(new Insets(4, 10, 4, 10));
        infoRow.getStyleClass().add("run-console-info-row");

        VBox topContainer = new VBox(menuBar, toolBar, infoRow);
        setTop(topContainer);

        // ─── Output area ─────────────────────────────────────────────
        outputFlow.setPadding(new Insets(8));
        outputFlow.getStyleClass().add("run-console-output-flow");
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("run-console-output-scroll");
        setCenter(scrollPane);

        // ─── Status bar ──────────────────────────────────────────────
        HBox statusBar = createStatusBar();
        setBottom(statusBar);

        setState(EngineState.BUILDING);
    }

    private static Button iconButton(String iconClass, String tooltipText) {
        Button btn = new Button();
        btn.getStyleClass().add("run-console-icon-btn");
        Region icon = new Region();
        icon.getStyleClass().addAll("icon", iconClass);
        btn.setGraphic(icon);
        btn.setTooltip(new Tooltip(tooltipText));
        btn.setFocusTraversable(false);
        return btn;
    }

    public void setProcessStarter(ProcessStarter processStarter) {
        this.processStarter = processStarter;
        runBtn.setDisable(processStarter == null || !(engineState == EngineState.STOPPED || engineState == EngineState.FAILED));
    }

    public void startProcess(Process process) {
        attachProcess(process);
    }

    private void attachProcess(Process process) {
        this.runningProcess = process;
        startTime = System.currentTimeMillis();
        lineCount = 0;
        errorCount = 0;
        warnCount = 0;
        rawLineBuffer.clear();
        setState(EngineState.BUILDING);

        Thread reader = new Thread(() -> {
            Process active = process;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(active.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) {
                    appendLine(line);
                }
            } catch (Exception ignored) {}

            int exitCode = -1;
            try {
                exitCode = active.waitFor();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            onProcessExit(exitCode);
        }, "jvn-run-console-reader");
        reader.setDaemon(true);
        reader.start();
    }

    public void setState(EngineState state) {
        this.engineState = state;
        Platform.runLater(() -> {
            String emoji;
            switch (state) {
                case BUILDING -> emoji = "◌";
                case STARTING -> emoji = "▶";
                case RUNNING -> emoji = "●";
                case STOPPED -> emoji = "■";
                case FAILED -> emoji = "✕";
                default -> emoji = "?";
            }
            stateLabel.setText(emoji + " " + state.name());
            updateStateClass(state);
            stopBtn.setDisable(state == EngineState.STOPPED || state == EngineState.FAILED);
            runBtn.setDisable(processStarter == null || !(state == EngineState.STOPPED || state == EngineState.FAILED));
        });
    }

    private void updateStateClass(EngineState state) {
        setClassEnabled(stateLabel, STATE_BUILDING_CLASS, state == EngineState.BUILDING);
        setClassEnabled(stateLabel, STATE_STARTING_CLASS, state == EngineState.STARTING);
        setClassEnabled(stateLabel, STATE_RUNNING_CLASS, state == EngineState.RUNNING);
        setClassEnabled(stateLabel, STATE_STOPPED_CLASS, state == EngineState.STOPPED);
        setClassEnabled(stateLabel, STATE_FAILED_CLASS, state == EngineState.FAILED);
    }

    private static void setClassEnabled(Node node, String styleClass, boolean enabled) {
        if (node == null || styleClass == null || styleClass.isBlank()) return;
        var classes = node.getStyleClass();
        if (enabled) {
            if (!classes.contains(styleClass)) classes.add(styleClass);
        } else {
            classes.remove(styleClass);
        }
    }

    /** Append a raw line from the process. Handles filtering and coloring. */
    public void appendLine(String rawLine) {
        lineCount++;
        rawLineBuffer.add(rawLine);

        // Detect engine state transitions from output
        if (rawLine.contains("> Task") && rawLine.contains(":run")) {
            setState(EngineState.STARTING);
        }
        if (ENGINE_MSG.matcher(rawLine).find() && engineState == EngineState.STARTING) {
            setState(EngineState.RUNNING);
        }
        if (rawLine.contains("BUILD FAILED")) {
            setState(EngineState.FAILED);
        }

        // Track error/warning counts
        boolean isErr = ERROR_LINE.matcher(rawLine).find();
        boolean isWarn = !isErr && WARN_LINE.matcher(rawLine).find();
        if (isErr) errorCount++;
        if (isWarn) warnCount++;

        Platform.runLater(() -> {
            if (!passesFilter(rawLine)) return;

            Text text = styleText(rawLine);
            outputFlow.getChildren().add(text);

            // Auto-scroll to bottom (unless user disabled it)
            if (autoScrollBtn.isSelected()) {
                scrollPane.layout();
                scrollPane.setVvalue(1.0);
            }

            // Update counters
            long elapsed = (System.currentTimeMillis() - startTime) / 1000;
            elapsedLabel.setText(formatElapsed(elapsed));
            lineCountLabel.setText(lineCount + " lines");
            errorCountLabel.setText(errorCount + " errors");
            warnCountLabel.setText(warnCount + " warnings");
            updateCounterBadgeStyles();
        });
    }

    private void updateCounterBadgeStyles() {
        setClassEnabled(errorCountLabel, COUNTER_ERROR_ACTIVE_CLASS, errorCount > 0);
        setClassEnabled(warnCountLabel, COUNTER_WARN_ACTIVE_CLASS, warnCount > 0);
    }

    /** Check if a line passes the current filter settings. */
    private boolean passesFilter(String rawLine) {
        boolean isEngineMsg = ENGINE_MSG.matcher(rawLine).find();
        boolean isError = ERROR_LINE.matcher(rawLine).find();
        boolean isWarning = !isError && WARN_LINE.matcher(rawLine).find();
        boolean isNoise = GRADLE_NOISE.matcher(rawLine).find();

        // Log level filter
        String level = logLevelFilter.getValue();
        if ("Engine".equals(level) && !isEngineMsg) return false;
        if ("Errors".equals(level) && !isError) return false;
        if ("Warnings".equals(level) && !isWarning && !isError) return false;

        // Show-all toggle (suppress noise unless enabled)
        if (!showAllToggle.isSelected() && isNoise && !isEngineMsg && !isError) return false;

        // Search filter
        if (!currentSearchTerm.isEmpty()) {
            if (!rawLine.toLowerCase().contains(currentSearchTerm.toLowerCase())) return false;
        }

        return true;
    }

    /** Style a raw line based on its content type. */
    private static Text styleText(String rawLine) {
        boolean isEngineMsg = ENGINE_MSG.matcher(rawLine).find();
        boolean isError = ERROR_LINE.matcher(rawLine).find();
        boolean isWarning = !isError && WARN_LINE.matcher(rawLine).find();
        boolean isNoise = GRADLE_NOISE.matcher(rawLine).find();

        Text text = new Text(rawLine + "\n");
        if (isError) {
            text.setStyle("-fx-fill: " + LOG_COLOR_ERROR + "; -fx-font-family: 'Menlo', 'Consolas', monospace; -fx-font-size: 12px;");
        } else if (isWarning) {
            text.setStyle("-fx-fill: " + LOG_COLOR_WARNING + "; -fx-font-family: 'Menlo', 'Consolas', monospace; -fx-font-size: 12px;");
        } else if (isEngineMsg) {
            text.setStyle("-fx-fill: " + LOG_COLOR_ENGINE + "; -fx-font-family: 'Menlo', 'Consolas', monospace; -fx-font-size: 12px;");
        } else if (isNoise) {
            text.setStyle("-fx-fill: " + LOG_COLOR_NOISE + "; -fx-font-family: 'Menlo', 'Consolas', monospace; -fx-font-size: 11px;");
        } else {
            text.setStyle("-fx-fill: " + LOG_COLOR_NORMAL + "; -fx-font-family: 'Menlo', 'Consolas', monospace; -fx-font-size: 12px;");
        }
        return text;
    }

    /** Called when the process exits. */
    public void onProcessExit(int exitCode) {
        Platform.runLater(() -> {
            if (exitCode == 0) {
                setState(EngineState.STOPPED);
                appendInfoMessage("Process exited normally.");
            } else {
                setState(EngineState.FAILED);
                appendInfoMessage("Process exited with code " + exitCode);
            }
            long elapsed = (System.currentTimeMillis() - startTime) / 1000;
            elapsedLabel.setText(formatElapsed(elapsed) + " (finished)");
        });
    }

    private void appendInfoMessage(String msg) {
        Text text = new Text("── " + msg + " ──\n");
        text.setStyle("-fx-fill: " + LOG_COLOR_INFO + "; -fx-font-family: 'Menlo', 'Consolas', monospace; -fx-font-size: 12px; -fx-font-style: italic;");
        outputFlow.getChildren().add(text);
        scrollPane.layout();
        scrollPane.setVvalue(1.0);
    }

    private void copyTraceback() {
        StringBuilder sb = new StringBuilder();
        for (var node : outputFlow.getChildren()) {
            if (node instanceof Text t) {
                sb.append(t.getText());
            }
        }
        String text = sb.toString().trim();
        if (text.isEmpty()) {
            appendInfoMessage("Nothing to copy.");
            return;
        }
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
        appendInfoMessage("Copied " + text.lines().count() + " lines to clipboard.");
    }

    private void clearOutput() {
        outputFlow.getChildren().clear();
        rawLineBuffer.clear();
        lineCount = 0;
        errorCount = 0;
        warnCount = 0;
        lineCountLabel.setText("0 lines");
        errorCountLabel.setText("0 errors");
        warnCountLabel.setText("0 warnings");
        updateCounterBadgeStyles();
    }

    private void rebuildOutput() {
        outputFlow.getChildren().clear();
        for (String line : rawLineBuffer) {
            if (passesFilter(line)) {
                outputFlow.getChildren().add(styleText(line));
            }
        }
        if (autoScrollBtn.isSelected()) {
            scrollPane.layout();
            scrollPane.setVvalue(1.0);
        }
    }

    private void stopProcess() {
        if (runningProcess != null && runningProcess.isAlive()) {
            appendInfoMessage("Stopping process...");
            stopBtn.setDisable(true);
            runningProcess.destroyForcibly();
        }
    }

    private void rerunProcess() {
        if (processStarter == null) return;
        if (runningProcess != null && runningProcess.isAlive()) {
            appendInfoMessage("Process is still running.");
            return;
        }
        try {
            appendInfoMessage("Re-running build...");
            Process process = processStarter.start();
            attachProcess(process);
        } catch (Exception ex) {
            setState(EngineState.FAILED);
            appendInfoMessage("Failed to start process: " + ex.getMessage());
        }
    }

    // ─── Menu Bar ────────────────────────────────────────────────────────────

    private MenuBar createMenuBar(String title) {
        MenuBar bar = new MenuBar();
        bar.getStyleClass().add("run-console-menubar");

        // — File menu —
        Menu fileMenu = new Menu("File");
        MenuItem miSaveLog = new MenuItem("Save Log...");
        miSaveLog.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN));
        miSaveLog.setOnAction(e -> saveLogToFile());

        MenuItem miCopyAll = new MenuItem("Copy All");
        miCopyAll.setAccelerator(new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));
        miCopyAll.setOnAction(e -> copyTraceback());

        MenuItem miCopyErrors = new MenuItem("Copy Errors Only");
        miCopyErrors.setOnAction(e -> copyFilteredLines(true, false));

        MenuItem miCopyWarnings = new MenuItem("Copy Errors + Warnings");
        miCopyWarnings.setOnAction(e -> copyFilteredLines(true, true));

        MenuItem miClose = new MenuItem("Close");
        miClose.setAccelerator(new KeyCodeCombination(KeyCode.W, KeyCombination.SHORTCUT_DOWN));
        miClose.setOnAction(e -> {
            javafx.stage.Stage stage = (javafx.stage.Stage) getScene().getWindow();
            if (stage != null) stage.close();
        });

        fileMenu.getItems().addAll(miSaveLog, new SeparatorMenuItem(),
            miCopyAll, miCopyErrors, miCopyWarnings, new SeparatorMenuItem(), miClose);

        // — Edit menu —
        Menu editMenu = new Menu("Edit");
        MenuItem miClear = new MenuItem("Clear Output");
        miClear.setAccelerator(new KeyCodeCombination(KeyCode.K, KeyCombination.SHORTCUT_DOWN));
        miClear.setOnAction(e -> clearOutput());

        MenuItem miFind = new MenuItem("Find...");
        miFind.setAccelerator(new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN));
        miFind.setOnAction(e -> {
            searchField.requestFocus();
            searchField.selectAll();
        });

        editMenu.getItems().addAll(miClear, new SeparatorMenuItem(), miFind);

        // — View menu —
        Menu viewMenu = new Menu("View");

        MenuItem miShowAll = new MenuItem("Toggle Build Output");
        miShowAll.setOnAction(e -> {
            showAllToggle.setSelected(!showAllToggle.isSelected());
            rebuildOutput();
        });

        MenuItem miAutoScroll = new MenuItem("Toggle Auto-Scroll");
        miAutoScroll.setOnAction(e -> autoScrollBtn.setSelected(!autoScrollBtn.isSelected()));

        MenuItem miWordWrap = new MenuItem("Toggle Word Wrap");
        miWordWrap.setOnAction(e -> {
            wordWrapBtn.setSelected(!wordWrapBtn.isSelected());
            scrollPane.setFitToWidth(wordWrapBtn.isSelected());
        });

        MenuItem miScrollTop = new MenuItem("Scroll to Top");
        miScrollTop.setAccelerator(new KeyCodeCombination(KeyCode.HOME, KeyCombination.SHORTCUT_DOWN));
        miScrollTop.setOnAction(e -> scrollPane.setVvalue(0));

        MenuItem miScrollBottom = new MenuItem("Scroll to Bottom");
        miScrollBottom.setAccelerator(new KeyCodeCombination(KeyCode.END, KeyCombination.SHORTCUT_DOWN));
        miScrollBottom.setOnAction(e -> {
            scrollPane.layout();
            scrollPane.setVvalue(1.0);
        });

        viewMenu.getItems().addAll(miShowAll, miAutoScroll, miWordWrap,
            new SeparatorMenuItem(), miScrollTop, miScrollBottom);

        // — Run menu —
        Menu runMenu = new Menu("Run");
        MenuItem miRerun = new MenuItem("Re-run");
        miRerun.setAccelerator(new KeyCodeCombination(KeyCode.R, KeyCombination.SHORTCUT_DOWN));
        miRerun.setOnAction(e -> rerunProcess());

        MenuItem miStop = new MenuItem("Stop");
        miStop.setAccelerator(new KeyCodeCombination(KeyCode.PERIOD, KeyCombination.SHORTCUT_DOWN));
        miStop.setOnAction(e -> stopProcess());

        runMenu.getItems().addAll(miRerun, miStop);

        // — Help menu —
        Menu helpMenu = new Menu("Help");
        MenuItem miShortcuts = new MenuItem("Keyboard Shortcuts");
        miShortcuts.setOnAction(e -> {
            Alert dlg = new Alert(Alert.AlertType.INFORMATION);
            EditorTheme.apply(dlg);
            dlg.setTitle("Console Shortcuts");
            dlg.setHeaderText("Run Console Keyboard Shortcuts");
            dlg.setContentText(
                "Cmd+S ........... Save log to file\n" +
                "Cmd+Shift+C ..... Copy all output\n" +
                "Cmd+K ........... Clear output\n" +
                "Cmd+F ........... Focus search field\n" +
                "Cmd+R ........... Re-run build\n" +
                "Cmd+. ........... Stop process\n" +
                "Cmd+W ........... Close window\n" +
                "Cmd+Home ........ Scroll to top\n" +
                "Cmd+End ......... Scroll to bottom\n");
            dlg.getDialogPane().setPrefWidth(380);
            dlg.showAndWait();
        });

        MenuItem miAbout = new MenuItem("About " + title);
        miAbout.setOnAction(e -> {
            Alert about = new Alert(Alert.AlertType.INFORMATION);
            EditorTheme.apply(about);
            about.setTitle("About");
            about.setHeaderText(title);
            about.setContentText("JVN Runtime Console\nLine buffer: " + rawLineBuffer.size()
                + " lines\nErrors: " + errorCount + "  Warnings: " + warnCount);
            about.showAndWait();
        });

        helpMenu.getItems().addAll(miShortcuts, new SeparatorMenuItem(), miAbout);

        bar.getMenus().addAll(fileMenu, editMenu, viewMenu, runMenu, helpMenu);
        return bar;
    }

    // ─── Tool Bar ───────────────────────────────────────────────────────────

    private ToolBar createToolBar() {
        ToolBar bar = new ToolBar();
        bar.getStyleClass().add("run-console-toolbar");

        runBtn.setOnAction(e -> rerunProcess());
        stopBtn.setOnAction(e -> stopProcess());
        copyBtn.setOnAction(e -> copyTraceback());
        clearBtn.setOnAction(e -> clearOutput());

        showAllToggle.getStyleClass().add("run-console-toggle");
        showAllToggle.setSelected(false);
        showAllToggle.setOnAction(e -> rebuildOutput());

        autoScrollBtn.setSelected(true);
        autoScrollBtn.getStyleClass().add("run-console-toggle");
        autoScrollBtn.setTooltip(new Tooltip("Auto-scroll to latest output"));
        autoScrollBtn.setFocusTraversable(false);

        wordWrapBtn.setSelected(true);
        wordWrapBtn.getStyleClass().add("run-console-toggle");
        wordWrapBtn.setTooltip(new Tooltip("Toggle word wrapping"));
        wordWrapBtn.setFocusTraversable(false);
        wordWrapBtn.setOnAction(e -> scrollPane.setFitToWidth(wordWrapBtn.isSelected()));

        // Search field
        searchField.setPromptText("Search output...");
        searchField.setPrefWidth(180);
        searchField.getStyleClass().add("run-console-search-field");
        searchField.setTooltip(new Tooltip("Filter output by text (Cmd+F)"));
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            currentSearchTerm = newVal != null ? newVal.trim() : "";
            rebuildOutput();
        });

        // Log level filter
        logLevelFilter.setValue("All");
        logLevelFilter.getStyleClass().add("run-console-filter-combo");
        logLevelFilter.setTooltip(new Tooltip("Filter by log level"));
        logLevelFilter.setOnAction(e -> rebuildOutput());

        Label searchLabel = new Label("Filter:");
        searchLabel.getStyleClass().add("run-console-search-label");

        bar.getItems().addAll(
            runBtn, stopBtn, new Separator(),
            clearBtn, copyBtn, new Separator(),
            showAllToggle, autoScrollBtn, wordWrapBtn, new Separator(),
            searchLabel, searchField, logLevelFilter
        );
        return bar;
    }

    // ─── Status Bar ─────────────────────────────────────────────────────────

    private HBox createStatusBar() {
        HBox bar = new HBox(12);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(3, 10, 3, 10));
        bar.getStyleClass().add("run-console-status-bar");

        lineCountLabel.getStyleClass().add("run-console-meta");
        errorCountLabel.getStyleClass().add("run-console-meta");
        warnCountLabel.getStyleClass().add("run-console-meta");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label bufferLabel = new Label("Buffer: 0");
        bufferLabel.getStyleClass().add("run-console-meta-muted");

        // Update buffer label periodically via the existing append cycle
        lineCountLabel.textProperty().addListener((o, ov, nv) ->
            bufferLabel.setText("Buffer: " + rawLineBuffer.size()));

        bar.getChildren().addAll(lineCountLabel, errorCountLabel, warnCountLabel, spacer, bufferLabel);
        updateCounterBadgeStyles();
        return bar;
    }

    // ─── File Operations ────────────────────────────────────────────────────

    private void saveLogToFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Log");
        chooser.setInitialFileName("jvn-runtime.log");
        chooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Log files", "*.log", "*.txt"),
            new FileChooser.ExtensionFilter("All files", "*.*"));
        File file = chooser.showSaveDialog(getScene().getWindow());
        if (file == null) return;

        try (PrintWriter pw = new PrintWriter(file)) {
            for (String line : rawLineBuffer) {
                pw.println(line);
            }
            appendInfoMessage("Saved " + rawLineBuffer.size() + " lines to " + file.getName());
        } catch (Exception ex) {
            appendInfoMessage("Failed to save log: " + ex.getMessage());
        }
    }

    private void copyFilteredLines(boolean includeErrors, boolean includeWarnings) {
        StringBuilder sb = new StringBuilder();
        for (String line : rawLineBuffer) {
            boolean isErr = ERROR_LINE.matcher(line).find();
            boolean isWarn = !isErr && WARN_LINE.matcher(line).find();
            if (includeErrors && isErr) sb.append(line).append("\n");
            if (includeWarnings && isWarn) sb.append(line).append("\n");
        }
        String text = sb.toString().trim();
        if (text.isEmpty()) {
            appendInfoMessage("No matching lines to copy.");
            return;
        }
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
        appendInfoMessage("Copied " + text.lines().count() + " lines to clipboard.");
    }

    private static String formatElapsed(long seconds) {
        if (seconds < 60) return seconds + "s";
        return (seconds / 60) + "m " + (seconds % 60) + "s";
    }
}
