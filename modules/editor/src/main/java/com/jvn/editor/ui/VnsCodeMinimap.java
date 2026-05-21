package com.jvn.editor.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.fxmisc.richtext.CodeArea;

import javafx.geometry.Insets;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

/**
 * VNS-aware script minimap.
 *
 * <p>This is intentionally not a VS Code-style text shrinker. It renders a
 * compact "script spine": labels, timeline blocks, dialogue beats, choices,
 * diagnostics, bookmarks, and the current viewport as semantic markers.</p>
 */
public final class VnsCodeMinimap extends StackPane {
  public record DiagnosticMarker(int line, boolean warning, String message) {}
  public record TimelineBlock(int startLine, int endLine, boolean folded) {}

  private enum LineKind {
    EMPTY,
    COMMENT,
    LABEL,
    DIRECTIVE,
    COMMAND,
    SPEAKER,
    CHOICE,
    TIMELINE,
    TEXT
  }

  private record LineSummary(LineKind kind, String label, String speaker, int length) {}

  private static final Pattern LABEL_PATTERN =
      Pattern.compile("^\\s*@label\\s+(\\S+)", Pattern.CASE_INSENSITIVE);
  private static final Pattern SPEAKER_PATTERN =
      Pattern.compile("^\\s*([^\\s#:][^:]{0,30}):");
  private static final Pattern TIMELINE_PATTERN =
      Pattern.compile("^\\s*timeline\\b", Pattern.CASE_INSENSITIVE);

  private final CodeArea codeArea;
  private final Canvas canvas = new Canvas(116, 100);
  private final Tooltip tooltip = new Tooltip("JVN script map");
  private String cachedText = "";
  private LineSummary[] cachedLines = new LineSummary[0];
  private List<DiagnosticMarker> diagnostics = List.of();
  private Set<Integer> bookmarks = Set.of();
  private List<TimelineBlock> timelines = List.of();
  private double lineHeight = 2.0;
  private double mapOffsetY = 0.0;

  private record VisibleRange(int startLine, int endLine) {}

  public VnsCodeMinimap(CodeArea codeArea) {
    this.codeArea = codeArea;
    getStyleClass().add("code-editor-minimap");
    setPadding(new Insets(0));
    setMinWidth(116);
    setPrefWidth(116);
    setMaxWidth(116);
    setMinHeight(0);
    getChildren().add(canvas);

    canvas.widthProperty().bind(widthProperty());
    canvas.heightProperty().bind(heightProperty());
    widthProperty().addListener((obs, oldValue, newValue) -> redraw());
    heightProperty().addListener((obs, oldValue, newValue) -> redraw());
    canvas.setOnMousePressed(this::navigate);
    canvas.setOnMouseDragged(this::navigate);
    canvas.setOnMouseMoved(this::updateTooltip);
    Tooltip.install(canvas, tooltip);
  }

  public void setSnapshot(String text,
                          Collection<DiagnosticMarker> diagnostics,
                          Collection<Integer> bookmarks,
                          Collection<TimelineBlock> timelines) {
    String safeText = text == null ? "" : text;
    if (!safeText.equals(cachedText)) {
      cachedText = safeText;
      cachedLines = analyzeLines(safeText);
    }
    this.diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    this.bookmarks = bookmarks == null ? Set.of() : new HashSet<>(bookmarks);
    this.timelines = timelines == null ? List.of() : List.copyOf(timelines);
  }

  public void redraw() {
    double width = canvas.getWidth();
    double height = canvas.getHeight();
    GraphicsContext gc = canvas.getGraphicsContext2D();
    gc.clearRect(0, 0, width, height);
    drawBackground(gc, width, height);

    int totalLines = cachedLines.length;
    if (totalLines == 0 || height <= 0 || width <= 0) {
      return;
    }

    lineHeight = Math.max(0.65, height / Math.max(1, totalLines));
    VisibleRange visibleRange = visibleRange(totalLines);
    mapOffsetY = computeMapOffset(height, totalLines, visibleRange);
    drawTimelineBands(gc, width, height, totalLines);
    drawScriptTexture(gc, width, height, totalLines);
    drawLabels(gc, width, totalLines);
    drawBookmarks(gc, width, totalLines);
    drawDiagnostics(gc, width, totalLines);
    drawViewport(gc, width, totalLines, visibleRange);
  }

  public int totalLines() {
    return cachedLines.length;
  }

  private void drawBackground(GraphicsContext gc, double width, double height) {
    boolean light = lightTheme();
    gc.setFill(Color.web(light ? "#f7f7f8" : "#08090b"));
    gc.fillRect(0, 0, width, height);
    gc.setFill(Color.web(light ? "#edf0f5" : "#11151c", light ? 0.88 : 0.82));
    gc.fillRect(9, 0, width - 22, height);
    gc.setFill(Color.web("#f0a23b", light ? 0.30 : 0.20));
    gc.fillRect(7, 0, 1.25, height);
    gc.setFill(Color.web("#7dcfff", light ? 0.20 : 0.13));
    gc.fillRect(width - 11, 0, 1.0, height);
  }

  private void drawTimelineBands(GraphicsContext gc, double width, double height, int totalLines) {
    for (TimelineBlock block : timelines) {
      int start = clampLine(block.startLine(), totalLines);
      int end = clampLine(block.endLine(), totalLines);
      if (end < start) continue;
      double y = yForLine(start);
      double h = Math.max(2.5, (end - start + 1) * lineHeight);
      if (y > height || y + h < 0) continue;

      boolean light = lightTheme();
      gc.setFill(Color.web(block.folded() ? "#f0a23b" : "#ff9f43",
          light ? (block.folded() ? 0.16 : 0.28) : (block.folded() ? 0.12 : 0.20)));
      gc.fillRoundRect(12, y, width - 28, h, 5, 5);
      gc.setFill(Color.web("#ffb45a", block.folded() ? 0.55 : 0.82));
      gc.fillRect(10, y, 3, h);
    }
  }

  private void drawScriptTexture(GraphicsContext gc, double width, double height, int totalLines) {
    double contentX = 16;
    double contentW = Math.max(20, width - 34);
    int firstLine = Math.max(0, (int) Math.floor(mapOffsetY / Math.max(0.1, lineHeight)) - 2);
    int lastLine = Math.min(totalLines - 1,
        (int) Math.ceil((mapOffsetY + height) / Math.max(0.1, lineHeight)) + 2);
    for (int i = firstLine; i <= lastLine; i++) {
      double y = yForLine(i);
      if (y + lineHeight < 0) continue;
      if (y > height) break;
      LineSummary line = cachedLines[i];
      if (line.kind() == LineKind.EMPTY) continue;

      Color color = lineColor(line);
      if (color.getOpacity() <= 0.0) continue;
      double h = Math.max(0.8, Math.min(2.4, lineHeight * 0.72));
      double w = lineWidth(line, contentW);

      if (line.kind() == LineKind.SPEAKER) {
        gc.setFill(speakerColor(line.speaker(), 0.72));
        gc.fillRect(contentX, y, 4, h);
        gc.setFill(color);
        gc.fillRoundRect(contentX + 6, y, w, h, 2, 2);
      } else if (line.kind() == LineKind.CHOICE) {
        gc.setFill(color);
        gc.fillRoundRect(contentX + 8, y, Math.max(10, w), h, 4, 4);
      } else {
        gc.setFill(color);
        gc.fillRect(contentX, y, w, h);
      }
    }
  }

  private void drawLabels(GraphicsContext gc, double width, int totalLines) {
    for (int i = 0; i < totalLines; i++) {
      LineSummary line = cachedLines[i];
      if (line.kind() != LineKind.LABEL) continue;
      double y = yForLine(i);
      if (y + lineHeight < 0 || y > canvas.getHeight()) continue;
      gc.setFill(Color.web("#f5c46b", 0.92));
      gc.fillRoundRect(3, y, width - 18, Math.max(2.2, lineHeight * 1.6), 4, 4);
      gc.setFill(Color.web("#0b0d10", 0.85));
      gc.fillRect(5, y + 0.8, Math.max(8, Math.min(width - 26, line.label().length() * 2.8)), 1.0);
    }
  }

  private void drawBookmarks(GraphicsContext gc, double width, int totalLines) {
    if (bookmarks.isEmpty()) return;
    gc.setFill(Color.web("#f8f8f2", 0.92));
    for (int line : bookmarks) {
      int clamped = clampLine(line, totalLines);
      double y = yForLine(clamped);
      if (y + lineHeight < 0 || y > canvas.getHeight()) continue;
      gc.fillRoundRect(width - 8, y, 5, Math.max(2.0, lineHeight * 1.2), 2, 2);
    }
  }

  private void drawDiagnostics(GraphicsContext gc, double width, int totalLines) {
    for (DiagnosticMarker marker : diagnostics) {
      int line = clampLine(marker.line(), totalLines);
      double y = yForLine(line);
      if (y + lineHeight < 0 || y > canvas.getHeight()) continue;
      gc.setFill(Color.web(marker.warning() ? "#ffcf66" : "#ff5f7e", 0.96));
      gc.fillOval(width - 17, y - 1, 6, 6);
    }
  }

  private void drawViewport(GraphicsContext gc, double width, int totalLines, VisibleRange visibleRange) {
    int start = visibleRange.startLine();
    int end = visibleRange.endLine();
    double y = yForLine(start);
    double h = Math.max(8, (end - start + 1) * lineHeight);
    boolean light = lightTheme();
    gc.setFill(Color.web(light ? "#111827" : "#e7edf6", light ? 0.07 : 0.10));
    gc.fillRoundRect(1, y, width - 3, h, 5, 5);
    gc.setStroke(Color.web(light ? "#111827" : "#e7edf6", light ? 0.36 : 0.62));
    gc.setLineWidth(1.2);
    gc.strokeRoundRect(1, y, width - 3, h, 5, 5);
    gc.setFill(Color.web("#7dcfff", light ? 0.88 : 0.72));
    gc.fillRect(0, y, 2, h);
  }

  private Color lineColor(LineSummary line) {
    boolean light = lightTheme();
    return switch (line.kind()) {
      case COMMENT -> Color.web("#667085", light ? 0.44 : 0.34);
      case DIRECTIVE -> Color.web("#8f55c8", light ? 0.76 : 0.70);
      case COMMAND -> Color.web("#2d6cdf", light ? 0.66 : 0.62);
      case SPEAKER -> Color.web("#b7791f", light ? 0.58 : 0.48);
      case CHOICE -> Color.web("#3c8d40", light ? 0.72 : 0.70);
      case TIMELINE -> Color.web("#e47a1f", light ? 0.92 : 0.85);
      case TEXT -> Color.web("#6b7280", light ? 0.42 : 0.32);
      case LABEL, EMPTY -> Color.TRANSPARENT;
    };
  }

  private boolean lightTheme() {
    return EditorTheme.theme() == EditorTheme.Theme.LIGHT;
  }

  private double lineWidth(LineSummary line, double maxWidth) {
    double normalized = Math.min(1.0, Math.max(0.12, line.length() / 88.0));
    return Math.max(5, maxWidth * normalized);
  }

  private Color speakerColor(String speaker, double opacity) {
    int hash = speaker == null ? 0 : Math.abs(speaker.toLowerCase(Locale.ROOT).hashCode());
    Color[] palette = {
        Color.web("#f5c46b", opacity),
        Color.web("#7dcfff", opacity),
        Color.web("#9ed67a", opacity),
        Color.web("#ff8fa3", opacity),
        Color.web("#d7b7ff", opacity)
    };
    return palette[hash % palette.length];
  }

  private void navigate(MouseEvent event) {
    int totalLines = cachedLines.length;
    if (totalLines == 0 || lineHeight <= 0) return;
    int targetLine = lineAtY(event.getY(), totalLines);
    codeArea.showParagraphAtTop(targetLine);
    codeArea.moveTo(targetLine, 0);
    codeArea.requestFocus();
    event.consume();
  }

  private void updateTooltip(MouseEvent event) {
    int totalLines = cachedLines.length;
    if (totalLines == 0 || lineHeight <= 0) {
      tooltip.setText("JVN script map");
      return;
    }
    int line = lineAtY(event.getY(), totalLines);
    LineSummary summary = cachedLines[line];
    String timelineSummary = timelineSummaryAt(line);
    StringBuilder text = new StringBuilder("Line ").append(line + 1);
    if (!timelineSummary.isBlank()) {
      text.append(timelineSummary);
    } else if (summary.kind() == LineKind.LABEL) {
      text.append(" | @label ").append(summary.label());
    } else if (summary.kind() == LineKind.SPEAKER) {
      text.append(" | dialogue: ").append(summary.speaker());
    } else {
      text.append(" | ").append(summary.kind().name().toLowerCase(Locale.ROOT));
    }
    tooltip.setText(text.toString());
  }

  private String timelineSummaryAt(int line) {
    for (TimelineBlock block : timelines) {
      if (line >= block.startLine() && line <= block.endLine()) {
        return " | timeline lines " + (block.startLine() + 1) + "-" + (block.endLine() + 1)
            + (block.folded() ? " | folded" : "");
      }
    }
    return "";
  }

  private int clampLine(int line, int totalLines) {
    if (totalLines <= 0) return 0;
    return Math.max(0, Math.min(line, totalLines - 1));
  }

  private VisibleRange visibleRange(int totalLines) {
    int start;
    int end;
    try {
      start = codeArea.firstVisibleParToAllParIndex();
      end = codeArea.lastVisibleParToAllParIndex();
    } catch (Exception ex) {
      start = Math.max(0, codeArea.getCurrentParagraph() - 10);
      end = Math.min(totalLines - 1, codeArea.getCurrentParagraph() + 30);
    }
    start = clampLine(start, totalLines);
    end = clampLine(end, totalLines);
    if (end < start) end = start;
    return new VisibleRange(start, end);
  }

  private double computeMapOffset(double height, int totalLines, VisibleRange visibleRange) {
    double fullHeight = totalLines * lineHeight;
    if (fullHeight <= height) return 0.0;
    double viewportCenter = ((visibleRange.startLine() + visibleRange.endLine() + 1) * 0.5) * lineHeight;
    double desired = viewportCenter - height * 0.5;
    return Math.max(0.0, Math.min(desired, fullHeight - height));
  }

  private double yForLine(int line) {
    return line * lineHeight - mapOffsetY;
  }

  private int lineAtY(double y, int totalLines) {
    return clampLine((int) ((y + mapOffsetY) / lineHeight), totalLines);
  }

  private static LineSummary[] analyzeLines(String text) {
    String[] lines = text == null || text.isEmpty() ? new String[0] : text.split("\\n", -1);
    LineSummary[] summaries = new LineSummary[lines.length];
    for (int i = 0; i < lines.length; i++) {
      summaries[i] = analyzeLine(lines[i]);
    }
    return summaries;
  }

  private static LineSummary analyzeLine(String raw) {
    String line = raw == null ? "" : raw.strip();
    if (line.isEmpty()) return new LineSummary(LineKind.EMPTY, "", "", 0);

    Matcher label = LABEL_PATTERN.matcher(line);
    if (label.find()) {
      return new LineSummary(LineKind.LABEL, label.group(1), "", line.length());
    }
    if (TIMELINE_PATTERN.matcher(line).find()) {
      return new LineSummary(LineKind.TIMELINE, "", "", line.length());
    }
    if (line.startsWith("#")) {
      return new LineSummary(LineKind.COMMENT, "", "", line.length());
    }
    if (line.startsWith("@")) {
      return new LineSummary(LineKind.DIRECTIVE, "", "", line.length());
    }
    if (line.startsWith("[")) {
      return new LineSummary(LineKind.COMMAND, "", "", line.length());
    }
    if (line.startsWith(">")) {
      return new LineSummary(LineKind.CHOICE, "", "", line.length());
    }
    Matcher speaker = SPEAKER_PATTERN.matcher(line);
    if (speaker.find()) {
      return new LineSummary(LineKind.SPEAKER, "", speaker.group(1).trim(), line.length());
    }
    return new LineSummary(LineKind.TEXT, "", "", line.length());
  }
}
