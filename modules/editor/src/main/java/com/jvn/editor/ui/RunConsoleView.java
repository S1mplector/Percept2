package com.jvn.editor.ui;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import com.sun.management.OperatingSystemMXBean;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
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

    private final ListView<String> outputList = new ListView<>();
    private final ProgressIndicator launchProgressIndicator = new ProgressIndicator();
    private final Label launchActivityLabel = new Label();
    private final Label launchDetailLabel = new Label();
    private final Label stateLabel = new Label();
    private final Label elapsedLabel = new Label();
    private final ToggleButton showAllToggle = new ToggleButton("Build Output");
    private final Button runBtn = iconButton(CssIcon.runtimePlay(), "Run current build again");
    private final Button copyBtn = iconButton(CssIcon.runtimeCopy(), "Copy traceback to clipboard");
    private final Button clearBtn = iconButton(CssIcon.runtimeClear(), "Clear output");
    private final Button stopBtn = iconButton(CssIcon.runtimeStop(), "Stop current build");

    // Enhanced UI components
    private final TextField searchField = new TextField();
    private final ComboBox<String> logLevelFilter = new ComboBox<>(
        FXCollections.observableArrayList("All", "Engine", "Errors", "Warnings"));
    private final ToggleButton autoScrollBtn = new ToggleButton("Auto-scroll");
    private final ToggleButton wordWrapBtn = new ToggleButton("Wrap");
    private final Label lineCountLabel = new Label("0 lines");
    private final Label errorCountLabel = new Label("0 errors");
    private final Label warnCountLabel = new Label("0 warnings");
    private final PerfGraph perfGraph = new PerfGraph();
    private final Tooltip perfGraphTooltip = new Tooltip("CPU -- | JVN -- MB | FPS --");
    private final Label perfCpuValue = new Label("CPU --");
    private final Label perfJvmValue = new Label("JVN -- MB");
    private final Label perfFpsValue = new Label("FPS --");

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
    private final OperatingSystemMXBean osBean =
        ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    private final AnimationTimer perfHudTimer;
    private long lastPerfHudUpdateNs = -1L;
    private long lastFrameNs = -1L;
    private long lastPerfHudFrameNs = -1L;
    private double smoothedProcessCpu = Double.NaN;
    private double smoothedFps = Double.NaN;
    private final Set<String> compiledModules = Collections.synchronizedSet(new LinkedHashSet<>());
    private volatile boolean sawGradleBootstrap = false;
    private volatile boolean compileMilestoneAnnounced = false;
    private volatile boolean launchMilestoneAnnounced = false;
    private volatile boolean runtimeMilestoneAnnounced = false;
    private String launchToolLabel = "Gradle wrapper";
    private String launchTaskLabel = "";
    private String launchWorkspaceLabel = "";

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
    private static final Pattern RUNTIME_LOG_LINE = Pattern.compile(
        "^\\d{2}:\\d{2}:\\d{2}[.,]\\d{3}\\s+\\|?-?(TRACE|DEBUG|INFO|WARN|ERROR)\\b",
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

    private static final Color PERF_GRID_BG = Color.web("#111111");
    private static final Color PERF_GRID_LINE = Color.web("#2a2a2a");
    private static final Color PERF_CPU_COLOR = Color.web("#f27333");
    private static final Color PERF_JVM_COLOR = Color.web("#49a5ff");
    private static final Color PERF_FPS_COLOR = Color.web("#f4f4f4");
    private static final long PERF_HUD_UPDATE_INTERVAL_NS = 300_000_000L;
    private static final double PERF_CPU_SMOOTH_ALPHA = 0.28;
    private static final double PERF_FPS_SMOOTH_ALPHA = 0.20;
    private static final Pattern COMPILE_TASK = Pattern.compile("^> Task (:[^\\s]+):compileJava(?:\\s+.*)?$");

    public RunConsoleView(String title) {
        getStyleClass().add("run-console-root");
        launchActivityLabel.getStyleClass().add("run-console-launch-activity");
        launchDetailLabel.getStyleClass().add("run-console-launch-detail");
        launchDetailLabel.setWrapText(true);
        stateLabel.getStyleClass().add("run-console-state");
        elapsedLabel.getStyleClass().add("run-console-elapsed");

        // ─── Menu Bar ────────────────────────────────────────────────
        MenuBar menuBar = createMenuBar(title);

        // ─── Tool Bar ────────────────────────────────────────────────
        ToolBar toolBar = createToolBar();

        VBox topContainer = new VBox(menuBar, toolBar);
        topContainer.getStyleClass().add("run-console-top");
        setTop(topContainer);

        // ─── Output area ─────────────────────────────────────────────
        outputList.getStyleClass().add("run-console-output");
        outputList.setCellFactory(list -> new LogLineCell());
        outputList.setFocusTraversable(true);
        VBox centerBox = new VBox(8, createLaunchBanner(), outputList);
        centerBox.getStyleClass().add("run-console-content");
        VBox.setVgrow(outputList, Priority.ALWAYS);
        setCenter(centerBox);

        // ─── Status bar ──────────────────────────────────────────────
        HBox statusBar = createStatusBar();
        setBottom(statusBar);

        perfGraphTooltip.setShowDelay(javafx.util.Duration.millis(120));
        Tooltip.install(perfGraph.getCanvas(), perfGraphTooltip);

        perfCpuValue.getStyleClass().addAll("run-console-perf-value", "run-console-perf-value-cpu");
        perfJvmValue.getStyleClass().addAll("run-console-perf-value", "run-console-perf-value-jvm");
        perfFpsValue.getStyleClass().addAll("run-console-perf-value", "run-console-perf-value-fps");

        perfHudTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                updatePerfHud(now);
            }
        };
        perfHudTimer.start();

        setState(EngineState.BUILDING);
        refreshLaunchBanner();
    }

    private static Button iconButton(javafx.scene.layout.Region iconClass, String tooltipText) {
        Button btn = new Button();
        btn.getStyleClass().add("run-console-icon-btn");
        btn.setGraphic(iconClass);
        btn.setTooltip(new Tooltip(tooltipText));
        btn.setFocusTraversable(false);
        return btn;
    }

    public void setProcessStarter(ProcessStarter processStarter) {
        this.processStarter = processStarter;
        runBtn.setDisable(processStarter == null || !(engineState == EngineState.STOPPED || engineState == EngineState.FAILED));
    }

    public void setLaunchContext(String toolLabel, String taskLabel, String workspaceLabel) {
        launchToolLabel = toolLabel == null || toolLabel.isBlank() ? "Gradle wrapper" : toolLabel.trim();
        launchTaskLabel = taskLabel == null ? "" : taskLabel.trim();
        launchWorkspaceLabel = workspaceLabel == null ? "" : workspaceLabel.trim();
        refreshLaunchBanner();
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
        compiledModules.clear();
        sawGradleBootstrap = false;
        compileMilestoneAnnounced = false;
        launchMilestoneAnnounced = false;
        runtimeMilestoneAnnounced = false;
        setState(EngineState.BUILDING);
        appendInfoMessage("Preparing " + launchToolLabel + "...");
        refreshLaunchBanner();

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
        if (state != EngineState.RUNNING) {
            smoothedFps = Double.NaN;
            lastFrameNs = -1L;
        }
        this.engineState = state;
        Platform.runLater(() -> {
            String label;
            switch (state) {
                case BUILDING -> label = "Building";
                case STARTING -> label = "Starting";
                case RUNNING -> label = "Running";
                case STOPPED -> label = "Stopped";
                case FAILED -> label = "Failed";
                default -> label = "Unknown";
            }
            stateLabel.setText(label);
            updateStateClass(state);
            stopBtn.setDisable(state == EngineState.STOPPED || state == EngineState.FAILED);
            runBtn.setDisable(processStarter == null || !(state == EngineState.STOPPED || state == EngineState.FAILED));
            boolean spinnerActive = state == EngineState.BUILDING || state == EngineState.STARTING;
            launchProgressIndicator.setVisible(spinnerActive);
            launchProgressIndicator.setManaged(spinnerActive);
            refreshLaunchBanner();
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
        observeBuildProgress(rawLine);
        lineCount++;
        rawLineBuffer.add(rawLine);

        // Detect engine state transitions from output
        if (rawLine.contains("> Task") && rawLine.contains(":run")) {
            setState(EngineState.STARTING);
        }
        if (isEngineOutputLine(rawLine) && engineState == EngineState.STARTING) {
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

            outputList.getItems().add(rawLine);

            // Auto-scroll to bottom (unless user disabled it)
            if (autoScrollBtn.isSelected()) {
                scrollToBottom();
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
        boolean isEngineMsg = isEngineOutputLine(rawLine);
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

    private void scrollToBottom() {
        int size = outputList.getItems().size();
        if (size > 0) outputList.scrollTo(size - 1);
    }

    private static String classifyLine(String line) {
        if (line.startsWith("\u200B")) return "run-console-line-milestone";
        java.util.regex.Matcher rtm = RUNTIME_LOG_LINE.matcher(line);
        if (rtm.find()) {
            String level = rtm.group(1).toUpperCase();
            return switch (level) {
                case "ERROR" -> "run-console-line-error";
                case "WARN"  -> "run-console-line-warn";
                case "INFO"  -> "run-console-line-info";
                default       -> "run-console-line-engine";
            };
        }
        if (ERROR_LINE.matcher(line).find()) return "run-console-line-error";
        if (WARN_LINE.matcher(line).find()) return "run-console-line-warn";
        if (ENGINE_MSG.matcher(line).find()) return "run-console-line-engine";
        if (GRADLE_NOISE.matcher(line).find()) return "run-console-line-noise";
        return "run-console-line-normal";
    }

    private class LogLineCell extends ListCell<String> {
        private String currentClass = null;

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                if (currentClass != null) {
                    getStyleClass().remove(currentClass);
                    currentClass = null;
                }
                return;
            }
            setText(item);
            String newClass = classifyLine(item);
            if (!newClass.equals(currentClass)) {
                if (currentClass != null) getStyleClass().remove(currentClass);
                getStyleClass().add(newClass);
                currentClass = newClass;
            }
        }
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
        Runnable task = () -> {
            outputList.getItems().add("\u200B" + msg);
            scrollToBottom();
        };
        if (Platform.isFxApplicationThread()) task.run();
        else Platform.runLater(task);
    }

    private VBox createLaunchBanner() {
        launchProgressIndicator.setMaxSize(18, 18);
        launchProgressIndicator.setPrefSize(18, 18);
        launchProgressIndicator.setMouseTransparent(true);
        launchProgressIndicator.setStyle("-fx-progress-color: #e8d8ad;");
        VBox labels = new VBox(4, launchActivityLabel, launchDetailLabel);
        HBox row = new HBox(10, launchProgressIndicator, labels);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(labels, Priority.ALWAYS);
        VBox box = new VBox(row);
        box.getStyleClass().add("run-console-launch-shell");
        return box;
    }

    private void observeBuildProgress(String rawLine) {
        if (rawLine == null || rawLine.isBlank()) return;
        boolean changed = false;

        if (!sawGradleBootstrap && rawLine.startsWith("Starting a Gradle")) {
            sawGradleBootstrap = true;
            changed = true;
        }

        java.util.regex.Matcher compileMatcher = COMPILE_TASK.matcher(rawLine);
        if (compileMatcher.matches()) {
            String moduleName = compileMatcher.group(1);
            if (moduleName != null && compiledModules.add(moduleName)) {
                changed = true;
            }
            if (!compileMilestoneAnnounced) {
                compileMilestoneAnnounced = true;
                appendInfoMessage("Compiling submodules...");
            }
        }

        if (!launchMilestoneAnnounced && rawLine.startsWith("> Task ") && rawLine.contains(":run")) {
            launchMilestoneAnnounced = true;
            if (!compiledModules.isEmpty()) {
                appendInfoMessage("Compiling submodules OK");
            }
            appendInfoMessage("Launching...");
            changed = true;
        }

        if (!runtimeMilestoneAnnounced && isEngineOutputLine(rawLine) && engineState == EngineState.STARTING) {
            runtimeMilestoneAnnounced = true;
            appendInfoMessage("Runtime online.");
            changed = true;
        }

        if (changed) refreshLaunchBanner();
    }

    private void refreshLaunchBanner() {
        Runnable task = () -> {
            launchActivityLabel.setText(currentActivityText());
            launchDetailLabel.setText(currentDetailText());
        };
        if (Platform.isFxApplicationThread()) task.run();
        else Platform.runLater(task);
    }

    private String currentActivityText() {
        return switch (engineState) {
            case BUILDING -> compileMilestoneAnnounced ? "Compiling submodules..." : ("Preparing " + launchToolLabel + "...");
            case STARTING -> "Launching...";
            case RUNNING -> "Running.";
            case STOPPED -> "Finished.";
            case FAILED -> "Build failed.";
        };
    }

    private String currentDetailText() {
        if (engineState == EngineState.STARTING && !compiledModules.isEmpty()) {
            return "Compiling submodules OK";
        }
        if (engineState == EngineState.RUNNING) {
            return "Engine boot completed successfully.";
        }
        if (engineState == EngineState.STOPPED) {
            return "Process completed. You can re-run or inspect the log below.";
        }
        if (engineState == EngineState.FAILED) {
            return "Gradle reported a failure. Inspect the log below for the exact task and stack trace.";
        }
        if (!compiledModules.isEmpty()) {
            return compiledModules.size() + (compiledModules.size() == 1 ? " module ready." : " modules ready.");
        }
        if (sawGradleBootstrap) {
            return "Gradle daemon bootstrapped. Resolving build graph...";
        }
        String context = launchToolLabel;
        if (!launchTaskLabel.isBlank()) context += " -> " + launchTaskLabel;
        if (!launchWorkspaceLabel.isBlank()) context += " @ " + launchWorkspaceLabel;
        return context;
    }

    private static boolean isEngineOutputLine(String rawLine) {
        if (rawLine == null || rawLine.isBlank()) return false;
        return ENGINE_MSG.matcher(rawLine).find() || RUNTIME_LOG_LINE.matcher(rawLine).find();
    }

    private void copyTraceback() {
        String text = String.join("\n", outputList.getItems()).trim();
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
        outputList.getItems().clear();
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
        outputList.getItems().clear();
        for (String line : rawLineBuffer) {
            if (passesFilter(line)) {
                outputList.getItems().add(line);
            }
        }
        if (autoScrollBtn.isSelected()) {
            scrollToBottom();
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
        bar.setUseSystemMenuBar(isMac());

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
            if (wordWrapBtn.isSelected()) {
                outputList.setFixedCellSize(-1);
            } else {
                outputList.setFixedCellSize(20);
            }
            outputList.refresh();
        });

        MenuItem miScrollTop = new MenuItem("Scroll to Top");
        miScrollTop.setAccelerator(new KeyCodeCombination(KeyCode.HOME, KeyCombination.SHORTCUT_DOWN));
        miScrollTop.setOnAction(e -> { if (!outputList.getItems().isEmpty()) outputList.scrollTo(0); });

        MenuItem miScrollBottom = new MenuItem("Scroll to Bottom");
        miScrollBottom.setAccelerator(new KeyCodeCombination(KeyCode.END, KeyCombination.SHORTCUT_DOWN));
        miScrollBottom.setOnAction(e -> scrollToBottom());

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
            EditorDialogs.showTextBlock(
                getScene() != null ? getScene().getWindow() : null,
                "Console Shortcuts",
                "Run Console Keyboard Shortcuts",
                "Cmd+S ........... Save log to file\n" +
                "Cmd+Shift+C ..... Copy all output\n" +
                "Cmd+K ........... Clear output\n" +
                "Cmd+F ........... Focus search field\n" +
                "Cmd+R ........... Re-run build\n" +
                "Cmd+. ........... Stop process\n" +
                "Cmd+W ........... Close window\n" +
                "Cmd+Home ........ Scroll to top\n" +
                "Cmd+End ......... Scroll to bottom\n",
                "Close");
        });

        MenuItem miAbout = new MenuItem("About " + title);
        miAbout.setOnAction(e -> {
            EditorDialogs.showTextBlock(
                getScene() != null ? getScene().getWindow() : null,
                "About",
                title,
                "JVN Runtime Console\nLine buffer: " + rawLineBuffer.size()
                    + " lines\nErrors: " + errorCount + "  Warnings: " + warnCount,
                "Close");
        });

        helpMenu.getItems().addAll(miShortcuts, new SeparatorMenuItem(), miAbout);

        bar.getMenus().addAll(fileMenu, editMenu, viewMenu, runMenu, helpMenu);
        return bar;
    }

    // ─── Tool Bar ───────────────────────────────────────────────────────────

    private ToolBar createToolBar() {
        ToolBar bar = new ToolBar();
        bar.getStyleClass().add("run-console-toolbar");

        runBtn.getStyleClass().add("run-console-icon-btn-success");
        stopBtn.getStyleClass().add("run-console-icon-btn-danger");

        runBtn.setOnAction(e -> rerunProcess());
        stopBtn.setOnAction(e -> stopProcess());
        copyBtn.setOnAction(e -> copyTraceback());
        clearBtn.setOnAction(e -> clearOutput());

        showAllToggle.getStyleClass().add("run-console-toggle");
        showAllToggle.setSelected(false);
        showAllToggle.setOnAction(e -> rebuildOutput());
        showAllToggle.setFocusTraversable(false);
        showAllToggle.setTooltip(new Tooltip("Include full Gradle/build chatter"));

        autoScrollBtn.setSelected(true);
        autoScrollBtn.getStyleClass().add("run-console-toggle");
        autoScrollBtn.setTooltip(new Tooltip("Auto-scroll to latest output"));
        autoScrollBtn.setFocusTraversable(false);

        wordWrapBtn.setSelected(true);
        wordWrapBtn.getStyleClass().add("run-console-toggle");
        wordWrapBtn.setTooltip(new Tooltip("Toggle word wrapping"));
        wordWrapBtn.setFocusTraversable(false);
        wordWrapBtn.setOnAction(e -> {
            if (wordWrapBtn.isSelected()) {
                outputList.setFixedCellSize(-1);
            } else {
                outputList.setFixedCellSize(20);
            }
            outputList.refresh();
        });

        // Search field
        searchField.setPromptText("Search output...");
        searchField.setPrefWidth(240);
        searchField.getStyleClass().add("run-console-search-field");
        searchField.setTooltip(new Tooltip("Filter output by text (Cmd+F)"));
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            currentSearchTerm = newVal != null ? newVal.trim() : "";
            rebuildOutput();
        });

        // Log level filter
        logLevelFilter.setValue("All");
        logLevelFilter.setPrefWidth(132);
        logLevelFilter.getStyleClass().add("run-console-filter-combo");
        logLevelFilter.setTooltip(new Tooltip("Filter by log level"));
        logLevelFilter.setOnAction(e -> rebuildOutput());

        Label searchLabel = new Label("Filter:");
        searchLabel.getStyleClass().add("run-console-search-label");

        VBox perfValues = new VBox(2, perfCpuValue, perfJvmValue, perfFpsValue);
        perfValues.setAlignment(Pos.CENTER_LEFT);
        perfValues.getStyleClass().add("run-console-perf-values");

        HBox perfHudBox = new HBox(8, perfGraph.getCanvas(), perfValues);
        perfHudBox.setAlignment(Pos.CENTER_LEFT);

        StackPane perfGraphShell = new StackPane(perfHudBox);
        perfGraphShell.setAlignment(Pos.CENTER_LEFT);
        perfGraphShell.getStyleClass().add("run-console-perf-graph-shell");
        perfGraphShell.setMinWidth(Region.USE_PREF_SIZE);
        perfGraphShell.setPrefWidth(260);
        perfGraphShell.setMaxWidth(Region.USE_PREF_SIZE);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        bar.getItems().addAll(
            runBtn, stopBtn, new Separator(),
            clearBtn, copyBtn, new Separator(),
            perfGraphShell, new Separator(),
            showAllToggle, autoScrollBtn, wordWrapBtn, new Separator(),
            searchLabel, searchField, logLevelFilter,
            spacer, stateLabel, elapsedLabel
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
        lineCountLabel.getStyleClass().add("run-console-meta-chip");
        errorCountLabel.getStyleClass().add("run-console-meta-chip");
        warnCountLabel.getStyleClass().add("run-console-meta-chip");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label bufferLabel = new Label("Buffer: 0");
        bufferLabel.getStyleClass().add("run-console-meta-muted");
        bufferLabel.getStyleClass().add("run-console-meta-chip");

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

    public void dispose() {
        perfHudTimer.stop();
    }

    private void updatePerfHud(long nowNs) {
        // Throttle AnimationTimer to ~16.67ms (60fps) to avoid interfering with editor frame rate limiting.
        // Without this, the perfHudTimer can pull the shared JavaFX pulse faster, breaking the editor's FPS cap.
        if (lastPerfHudFrameNs > 0L && (nowNs - lastPerfHudFrameNs) < 16_667_000L) return;
        lastPerfHudFrameNs = nowNs;

        // Skip all work when the window is not on screen (minimized, hidden, or closed).
        // The timer stays alive so it resumes correctly if the window is restored.
        Scene scene = getScene();
        if (scene == null || scene.getWindow() == null || !scene.getWindow().isShowing()) return;

        // Only track per-frame FPS while the engine is actively running.
        // During BUILDING/STARTING the FPS measurement is meaningless (no game frame is running).
        if (engineState == EngineState.RUNNING) {
            if (lastFrameNs > 0L) {
                double instantFps = 1_000_000_000.0 / Math.max(1L, nowNs - lastFrameNs);
                smoothedFps = smoothRatio(smoothedFps, instantFps, PERF_FPS_SMOOTH_ALPHA);
            }
            lastFrameNs = nowNs;
        }

        if (lastPerfHudUpdateNs > 0L && (nowNs - lastPerfHudUpdateNs) < PERF_HUD_UPDATE_INTERVAL_NS) {
            return;
        }
        lastPerfHudUpdateNs = nowNs;

        double processCpu = Double.NaN;
        if (osBean != null) {
            processCpu = osBean.getProcessCpuLoad();
        }
        smoothedProcessCpu = smoothRatio(smoothedProcessCpu, processCpu, PERF_CPU_SMOOTH_ALPHA);

        MemoryUsage heap = memoryBean.getHeapMemoryUsage();
        MemoryUsage nonHeap = memoryBean.getNonHeapMemoryUsage();
        double heapUsedMb = Math.max(0.0, bytesToMb(heap == null ? -1L : heap.getUsed()));
        long heapMaxBytes = heap == null ? -1L : heap.getMax();
        if (heapMaxBytes <= 0L && heap != null) {
            heapMaxBytes = heap.getCommitted();
        }
        if (heapMaxBytes <= 0L) {
            heapMaxBytes = Runtime.getRuntime().maxMemory();
        }
        double heapMaxMb = Math.max(1.0, bytesToMb(heapMaxBytes));
        double nonHeapMb = Math.max(0.0, bytesToMb(nonHeap == null ? -1L : nonHeap.getUsed()));
        long nonHeapCeilingBytes = nonHeap == null ? -1L : nonHeap.getCommitted();
        if (nonHeapCeilingBytes <= 0L && nonHeap != null) {
            nonHeapCeilingBytes = nonHeap.getUsed();
        }
        double nonHeapCeilingMb = Math.max(nonHeapMb, bytesToMb(nonHeapCeilingBytes));
        double jvnUsedMb = Math.max(0.0, heapUsedMb + nonHeapMb);
        double jvnCeilingMb = Math.max(1.0, heapMaxMb + nonHeapCeilingMb);
        double cpuRatio = isRatioValid(smoothedProcessCpu) ? clamp01(smoothedProcessCpu) : 0.0;
        double cpuPercent = cpuRatio * 100.0;
        double fpsValue = Math.max(0.0, smoothedFps);

        String fpsText;
        double fpsSampleValue;
        if (engineState == EngineState.RUNNING && isRatioValid(smoothedFps)) {
            fpsText = String.format(Locale.ROOT, "FPS %.0f", fpsValue);
            fpsSampleValue = fpsValue;
        } else {
            fpsText = "FPS --";
            fpsSampleValue = 0.0;
        }

        perfGraphTooltip.setText(String.format(
            Locale.ROOT,
            "CPU %.0f%% | JVN %.0f MB | %s",
            cpuPercent,
            jvnUsedMb,
            fpsText));
        perfCpuValue.setText(String.format(Locale.ROOT, "CPU %.0f%%", cpuPercent));
        perfJvmValue.setText(String.format(Locale.ROOT, "JVN %.0f MB", jvnUsedMb));
        perfFpsValue.setText(fpsText);
        perfGraph.pushSample(cpuPercent, jvnUsedMb, fpsSampleValue, jvnCeilingMb);
    }

    private static final class PerfGraph {
        private final Canvas canvas = new Canvas(154, 34);
        private final double[] cpu = new double[120];
        private final double[] jvm = new double[120];
        private final double[] fps = new double[120];
        private final double[] jvmCeiling = new double[120];
        private int index = 0;
        private boolean filled = false;

        private PerfGraph() {
            canvas.setWidth(154);
            canvas.setHeight(34);
            redraw();
        }

        private Canvas getCanvas() {
            return canvas;
        }

        private void pushSample(double cpuPercent, double jvmMb, double fpsValue, double jvmCeilingMb) {
            int slot = index % cpu.length;
            cpu[slot] = sanitizeMetric(cpuPercent);
            jvm[slot] = sanitizeMetric(jvmMb);
            fps[slot] = sanitizeMetric(fpsValue);
            jvmCeiling[slot] = sanitizeMetric(jvmCeilingMb);
            index++;
            if (index >= cpu.length) filled = true;
            redraw();
        }

        private void redraw() {
            GraphicsContext g = canvas.getGraphicsContext2D();
            double w = canvas.getWidth();
            double h = canvas.getHeight();
            if (!Double.isFinite(w) || !Double.isFinite(h) || w <= 0 || h <= 0) return;

            g.setFill(PERF_GRID_BG);
            g.fillRect(0, 0, w, h);

            g.setStroke(PERF_GRID_LINE);
            g.setLineWidth(1.0);
            for (int row = 1; row < 4; row++) {
                double y = h * row / 4.0;
                g.strokeLine(0, y, w, y);
            }
            double stepX = w / 4.0;
            for (double x = stepX; x < w; x += stepX) {
                g.strokeLine(x, 0, x, h);
            }

            int samples = filled ? cpu.length : Math.min(index, cpu.length);
            if (samples <= 1) return;
            double scaleX = w / (cpu.length - 1.0);
            double cpuCeiling = Math.max(6.0, maxRecent(cpu, samples) * 1.35);
            double jvmMax = Math.max(96.0, maxRecent(jvm, samples) * 1.14);
            double jvmCeilingMax = Math.max(jvmMax, maxRecent(jvmCeiling, samples));
            double graphJvmCeiling = Math.min(jvmCeilingMax, Math.max(jvmMax, jvmMax * 1.45));
            double fpsCeiling = Math.max(60.0, maxRecent(fps, samples) * 1.08);

            g.setFill(PERF_JVM_COLOR.deriveColor(0, 1, 1, 0.22));
            g.beginPath();
            for (int i = 0; i < samples; i++) {
                int si = (index - samples + i + cpu.length) % cpu.length;
                double x = i * scaleX;
                double y = graphY(jvm[si], graphJvmCeiling, h);
                if (i == 0) g.moveTo(x, h);
                g.lineTo(x, y);
            }
            g.lineTo((samples - 1) * scaleX, h);
            g.closePath();
            g.fill();

            g.setStroke(PERF_JVM_COLOR.deriveColor(0, 1, 1, 0.92));
            g.setLineWidth(1.25);
            g.beginPath();
            for (int i = 0; i < samples; i++) {
                int si = (index - samples + i + cpu.length) % cpu.length;
                double x = i * scaleX;
                double y = graphY(jvm[si], graphJvmCeiling, h);
                if (i == 0) g.moveTo(x, y);
                else g.lineTo(x, y);
            }
            g.stroke();

            g.setStroke(PERF_CPU_COLOR.deriveColor(0, 1, 1, 0.92));
            g.setLineWidth(1.5);
            g.beginPath();
            for (int i = 0; i < samples; i++) {
                int si = (index - samples + i + cpu.length) % cpu.length;
                double x = i * scaleX;
                double y = graphY(cpu[si], cpuCeiling, h);
                if (i == 0) g.moveTo(x, y);
                else g.lineTo(x, y);
            }
            g.stroke();

            g.setStroke(PERF_FPS_COLOR.deriveColor(0, 1, 1, 0.9));
            g.setLineWidth(1.5);
            g.beginPath();
            for (int i = 0; i < samples; i++) {
                int si = (index - samples + i + cpu.length) % cpu.length;
                double x = i * scaleX;
                double y = graphY(fps[si], fpsCeiling, h);
                if (i == 0) g.moveTo(x, y);
                else g.lineTo(x, y);
            }
            g.stroke();
        }

        private static double sanitizeMetric(double value) {
            return Double.isFinite(value) && value > 0.0 ? value : 0.0;
        }

        private double maxRecent(double[] values, int samples) {
            double max = 0.0;
            for (int i = 0; i < samples; i++) {
                int si = (index - samples + i + values.length) % values.length;
                max = Math.max(max, sanitizeMetric(values[si]));
            }
            return max;
        }

        private double graphY(double value, double ceiling, double height) {
            double normalized = ceiling <= 0.0 ? 0.0 : Math.min(1.0, sanitizeMetric(value) / ceiling);
            double inset = 2.0;
            return inset + ((height - inset * 2.0) * (1.0 - normalized));
        }
    }

    private static double bytesToMb(long bytes) {
        return bytes < 0L ? -1.0 : bytes / (1024.0 * 1024.0);
    }

    private static boolean isRatioValid(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value) && value >= 0.0;
    }

    private static double smoothRatio(double current, double target, double alpha) {
        if (!isRatioValid(target)) return current;
        if (!isRatioValid(current)) return target;
        return current + ((target - current) * alpha);
    }

    private static double clamp01(double value) {
        if (!Double.isFinite(value)) return 0.0;
        if (value < 0.0) return 0.0;
        if (value > 1.0) return 1.0;
        return value;
    }

    private static boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase().contains("mac");
    }
}
