package com.jvn.editor.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.VBox;

/**
 * Right-panel extension that shows the VNS scene snapshot at the current cursor line
 * and provides a button to launch Puppeteer with that context.
 */
public class PuppeteerLauncherPanel extends VBox {

  private static final Pattern LABEL_PATTERN = Pattern.compile("^\\s*(?:@label|label)\\s+(\\S+)", Pattern.CASE_INSENSITIVE);
  private static final Pattern BG_CMD_PATTERN = Pattern.compile("^\\s*\\[(?:bg|background)\\s+(\\S+)]", Pattern.CASE_INSENSITIVE);
  private static final Pattern BG_DECL_PATTERN = Pattern.compile("^\\s*@background\\s+(\\S+)", Pattern.CASE_INSENSITIVE);
  private static final Pattern SHOW_PATTERN = Pattern.compile("^\\s*\\[show\\s+(\\S+)\\s+(\\S+)(?:\\s+(\\S+))?]", Pattern.CASE_INSENSITIVE);
  private static final Pattern HIDE_PATTERN = Pattern.compile("^\\s*\\[hide\\s+(\\S+)]", Pattern.CASE_INSENSITIVE);
  private static final Pattern EXT_CHAR_SHOW = Pattern.compile("^\\s*@external\\s+char(?:acter)?\\s+(\\S+)\\s+show\\s+(\\S+)(?:\\s+(\\S+))?", Pattern.CASE_INSENSITIVE);
  private static final Pattern EXT_CHAR_HIDE = Pattern.compile("^\\s*@external\\s+char(?:acter)?\\s+(\\S+)\\s+hide", Pattern.CASE_INSENSITIVE);
  private static final Pattern EXT_CHAR_MOVE = Pattern.compile("^\\s*@external\\s+char(?:acter)?\\s+(\\S+)\\s+move\\s+(\\S+)", Pattern.CASE_INSENSITIVE);
  private static final Pattern EXT_CHAR_EXPR = Pattern.compile("^\\s*@external\\s+char(?:acter)?\\s+(\\S+)\\s+(?:expression|expr)\\s+(\\S+)", Pattern.CASE_INSENSITIVE);

  private final Label lblHeader;
  private final Label lblLine;
  private final Label lblLineText;
  private final Label lblLabel;
  private final Label lblBackground;
  private final VBox characterList;
  private final Button btnLaunch;

  private String currentSource = "";
  private int currentLine = 0;
  private Consumer<SceneSnapshot> onLaunch;

  public PuppeteerLauncherPanel() {
    setSpacing(8);
    setPadding(new Insets(12));
    setStyle("-fx-background-color: #1a1a1a;");

    lblHeader = new Label("Puppeteer Launcher");
    lblHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #e6e6e6;");

    lblLine = new Label("Line: —");
    lblLine.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 11px;");

    lblLineText = new Label("");
    lblLineText.setStyle("-fx-text-fill: #e6e6e6; -fx-font-family: monospace; -fx-font-size: 11px;");
    lblLineText.setWrapText(true);
    lblLineText.setMaxWidth(260);

    Label snapshotHeader = new Label("Scene Snapshot at Cursor");
    snapshotHeader.setStyle("-fx-font-weight: bold; -fx-text-fill: #4da3ff; -fx-font-size: 12px;");

    lblLabel = new Label("Label: —");
    lblLabel.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 11px;");

    lblBackground = new Label("Background: —");
    lblBackground.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 11px;");

    Label charsHeader = new Label("Visible Characters:");
    charsHeader.setStyle("-fx-text-fill: #e6e6e6; -fx-font-size: 11px; -fx-font-weight: bold;");

    characterList = new VBox(2);
    characterList.setPadding(new Insets(0, 0, 0, 8));

    btnLaunch = new Button("Launch Puppeteer Here");
    btnLaunch.setStyle("-fx-background-color: #4da3ff; -fx-text-fill: #121212; -fx-font-weight: bold;");
    btnLaunch.setMaxWidth(Double.MAX_VALUE);
    btnLaunch.setOnAction(e -> {
      if (onLaunch != null) onLaunch.accept(resolveSnapshot(currentSource, currentLine));
    });

    getChildren().addAll(
        lblHeader,
        new Separator(),
        lblLine,
        lblLineText,
        new Separator(),
        snapshotHeader,
        lblLabel,
        lblBackground,
        charsHeader,
        characterList,
        new Separator(),
        btnLaunch
    );

    updateEmpty();
  }

  public void setOnLaunch(Consumer<SceneSnapshot> handler) {
    this.onLaunch = handler;
  }

  public void setSource(String source) {
    this.currentSource = source != null ? source : "";
    refresh();
  }

  public void setCaretLine(int zeroBased) {
    this.currentLine = zeroBased;
    refresh();
  }

  public void clear() {
    currentSource = "";
    currentLine = 0;
    updateEmpty();
  }

  private void updateEmpty() {
    lblLine.setText("Line: —");
    lblLineText.setText("(no VNS file active)");
    lblLabel.setText("Label: —");
    lblBackground.setText("Background: —");
    characterList.getChildren().clear();
    Label none = new Label("—");
    none.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 11px;");
    characterList.getChildren().add(none);
    btnLaunch.setDisable(true);
  }

  private void refresh() {
    if (currentSource.isEmpty()) {
      updateEmpty();
      return;
    }

    String[] lines = currentSource.split("\n", -1);
    int lineIdx = Math.min(currentLine, lines.length - 1);
    lineIdx = Math.max(0, lineIdx);

    lblLine.setText("Line: " + (lineIdx + 1));
    String lineText = lines[lineIdx].trim();
    lblLineText.setText(lineText.length() > 80 ? lineText.substring(0, 80) + "…" : lineText);

    SceneSnapshot snap = resolveSnapshot(currentSource, lineIdx);

    lblLabel.setText("Label: " + (snap.currentLabel != null ? snap.currentLabel : "(before first label)"));
    lblBackground.setText("Background: " + (snap.backgroundId != null ? snap.backgroundId : "—"));

    characterList.getChildren().clear();
    if (snap.characters.isEmpty()) {
      Label none = new Label("(none visible)");
      none.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 11px;");
      characterList.getChildren().add(none);
    } else {
      for (CharacterEntry ch : snap.characters) {
        String text = ch.characterId + " @ " + ch.position;
        if (ch.expression != null && !ch.expression.equals("neutral")) {
          text += " [" + ch.expression + "]";
        }
        Label lbl = new Label(text);
        lbl.setStyle("-fx-text-fill: #f0b673; -fx-font-size: 11px; -fx-font-family: monospace;");
        characterList.getChildren().add(lbl);
      }
    }

    btnLaunch.setDisable(false);
  }

  // --- VNS Scene State Resolver ---

  static SceneSnapshot resolveSnapshot(String source, int upToLine) {
    String[] lines = source.split("\n", -1);
    int limit = Math.min(upToLine, lines.length - 1);

    String currentLabel = null;
    String backgroundId = null;
    Map<String, CharacterEntry> visible = new LinkedHashMap<>();

    for (int i = 0; i <= limit; i++) {
      String line = lines[i];
      String trimmed = line.trim();
      if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;

      Matcher m;

      // Label
      m = LABEL_PATTERN.matcher(line);
      if (m.find()) {
        currentLabel = m.group(1);
        continue;
      }

      // Background command [bg id] or [background id]
      m = BG_CMD_PATTERN.matcher(line);
      if (m.find()) {
        backgroundId = m.group(1);
        continue;
      }

      // @background declaration (just tracks the id)
      m = BG_DECL_PATTERN.matcher(line);
      if (m.find()) continue;

      // [show charId pos expression?]
      m = SHOW_PATTERN.matcher(line);
      if (m.find()) {
        String charId = m.group(1);
        String pos = m.group(2).toLowerCase(Locale.ROOT);
        String expr = m.group(3) != null ? m.group(3) : "neutral";
        visible.put(charId, new CharacterEntry(charId, pos, expr));
        continue;
      }

      // [hide charId]
      m = HIDE_PATTERN.matcher(line);
      if (m.find()) {
        visible.remove(m.group(1));
        continue;
      }

      // @external character <id> show <pos> [expr]
      m = EXT_CHAR_SHOW.matcher(line);
      if (m.find()) {
        String charId = m.group(1);
        String pos = m.group(2).toLowerCase(Locale.ROOT);
        String expr = m.group(3) != null ? m.group(3) : "neutral";
        visible.put(charId, new CharacterEntry(charId, pos, expr));
        continue;
      }

      // @external character <id> hide
      m = EXT_CHAR_HIDE.matcher(line);
      if (m.find()) {
        visible.remove(m.group(1));
        continue;
      }

      // @external character <id> move <pos>
      m = EXT_CHAR_MOVE.matcher(line);
      if (m.find()) {
        String charId = m.group(1);
        String pos = m.group(2).toLowerCase(Locale.ROOT);
        CharacterEntry existing = visible.get(charId);
        String expr = existing != null ? existing.expression : "neutral";
        visible.put(charId, new CharacterEntry(charId, pos, expr));
        continue;
      }

      // @external character <id> expr <expression>
      m = EXT_CHAR_EXPR.matcher(line);
      if (m.find()) {
        String charId = m.group(1);
        String expr = m.group(2);
        CharacterEntry existing = visible.get(charId);
        String pos = existing != null ? existing.position : "center";
        visible.put(charId, new CharacterEntry(charId, pos, expr));
      }
    }

    return new SceneSnapshot(currentLabel, backgroundId, new ArrayList<>(visible.values()), upToLine);
  }

  // --- Data classes ---

  public static class CharacterEntry {
    public final String characterId;
    public final String position;
    public final String expression;

    public CharacterEntry(String characterId, String position, String expression) {
      this.characterId = characterId;
      this.position = position;
      this.expression = expression;
    }
  }

  public static class SceneSnapshot {
    public final String currentLabel;
    public final String backgroundId;
    public final List<CharacterEntry> characters;
    public final int atLine;

    public SceneSnapshot(String currentLabel, String backgroundId, List<CharacterEntry> characters, int atLine) {
      this.currentLabel = currentLabel;
      this.backgroundId = backgroundId;
      this.characters = characters;
      this.atLine = atLine;
    }
  }
}
