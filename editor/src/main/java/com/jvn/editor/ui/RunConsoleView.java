package com.jvn.editor.ui;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.regex.Pattern;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

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
    private final Button clearBtn = iconButton("icon-runtime-clear", "Clear output");
    private final Button stopBtn = iconButton("icon-runtime-stop", "Stop current build");

    private EngineState engineState = EngineState.BUILDING;
    private Process runningProcess;
    private ProcessStarter processStarter;
    private long startTime = System.currentTimeMillis();
    private int lineCount = 0;

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

    public RunConsoleView(String title) {
        // Header bar
        stateLabel.getStyleClass().add("run-console-state");
        stateLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        elapsedLabel.setStyle("-fx-text-fill: #888; -fx-font-size: 12px;");
        showAllToggle.setStyle("-fx-text-fill: #aaa; -fx-font-size: 11px;");
        showAllToggle.setSelected(false);
        showAllToggle.setOnAction(e -> rebuildOutput());

        runBtn.setOnAction(e -> rerunProcess());
        clearBtn.setOnAction(e -> clearOutput());
        stopBtn.setOnAction(e -> stopProcess());

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #ddd;");

        HBox leftHeader = new HBox(8, titleLabel, stateLabel, elapsedLabel);
        leftHeader.setAlignment(Pos.CENTER_LEFT);
        HBox rightHeader = new HBox(8, showAllToggle, runBtn, stopBtn, clearBtn);
        rightHeader.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(leftHeader, Priority.ALWAYS);

        HBox header = new HBox(leftHeader, rightHeader);
        header.setPadding(new Insets(6, 10, 6, 10));
        header.setStyle("-fx-background-color: #1e1e2e; -fx-border-color: #333; -fx-border-width: 0 0 1 0;");
        header.setAlignment(Pos.CENTER);
        HBox.setHgrow(rightHeader, Priority.NEVER);
        HBox.setHgrow(leftHeader, Priority.ALWAYS);

        // Output area
        outputFlow.setPadding(new Insets(8));
        outputFlow.setStyle("-fx-background-color: #0e0e16;");
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: #0e0e16; -fx-background-color: #0e0e16;");

        setTop(header);
        setCenter(scrollPane);
        setStyle("-fx-background-color: #0e0e16;");

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
            String color;
            switch (state) {
                case BUILDING:  emoji = ""; color = "#f0c040"; break;
                case STARTING:  emoji = "▶"; color = "#60c0ff"; break;
                case RUNNING:   emoji = "●"; color = "#40e060"; break;
                case STOPPED:   emoji = "■"; color = "#888"; break;
                case FAILED:    emoji = "✕"; color = "#ff5050"; break;
                default:        emoji = "?"; color = "#888"; break;
            }
            stateLabel.setText(emoji + " " + state.name());
            stateLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: " + color + ";");
            stopBtn.setDisable(state == EngineState.STOPPED || state == EngineState.FAILED);
            runBtn.setDisable(processStarter == null || !(state == EngineState.STOPPED || state == EngineState.FAILED));
        });
    }

    /** Append a raw line from the process. Handles filtering and coloring. */
    public void appendLine(String rawLine) {
        lineCount++;

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

        Platform.runLater(() -> {
            boolean isEngineMsg = ENGINE_MSG.matcher(rawLine).find();
            boolean isError = ERROR_LINE.matcher(rawLine).find();
            boolean isWarning = !isError && WARN_LINE.matcher(rawLine).find();
            boolean isNoise = GRADLE_NOISE.matcher(rawLine).find();

            // Always show engine messages, errors, warnings. Hide noise unless toggled.
            if (!showAllToggle.isSelected() && isNoise && !isEngineMsg && !isError) {
                return;
            }

            Text text = new Text(rawLine + "\n");
            if (isError) {
                text.setStyle("-fx-fill: #ff5050; -fx-font-family: 'Menlo', 'Consolas', monospace; -fx-font-size: 12px;");
            } else if (isWarning) {
                text.setStyle("-fx-fill: #f0c040; -fx-font-family: 'Menlo', 'Consolas', monospace; -fx-font-size: 12px;");
            } else if (isEngineMsg) {
                text.setStyle("-fx-fill: #e0e0e0; -fx-font-family: 'Menlo', 'Consolas', monospace; -fx-font-size: 12px;");
            } else if (isNoise) {
                text.setStyle("-fx-fill: #555; -fx-font-family: 'Menlo', 'Consolas', monospace; -fx-font-size: 11px;");
            } else {
                text.setStyle("-fx-fill: #aaa; -fx-font-family: 'Menlo', 'Consolas', monospace; -fx-font-size: 12px;");
            }
            outputFlow.getChildren().add(text);

            // Auto-scroll to bottom
            scrollPane.layout();
            scrollPane.setVvalue(1.0);

            // Update elapsed time
            long elapsed = (System.currentTimeMillis() - startTime) / 1000;
            elapsedLabel.setText(formatElapsed(elapsed));
        });
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
        text.setStyle("-fx-fill: #60c0ff; -fx-font-family: 'Menlo', 'Consolas', monospace; -fx-font-size: 12px; -fx-font-style: italic;");
        outputFlow.getChildren().add(text);
        scrollPane.layout();
        scrollPane.setVvalue(1.0);
    }

    private void clearOutput() {
        outputFlow.getChildren().clear();
    }

    private void rebuildOutput() {
        // The toggle changed; we can't reconstruct filtered lines from TextFlow easily,
        // so just note in the output that the filter changed
        appendInfoMessage(showAllToggle.isSelected() ? "Showing all build output" : "Filtering build output");
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

    private static String formatElapsed(long seconds) {
        if (seconds < 60) return seconds + "s";
        return (seconds / 60) + "m " + (seconds % 60) + "s";
    }
}
