package com.jvn.editor.ui;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Right-panel extension that shows the VNS scene snapshot at the current cursor line
 * and provides a button to launch Puppeteer with that context.
 */
public class PuppeteerLauncherPanel extends VBox {

  private static final Pattern LABEL_PATTERN = Pattern.compile("^\\s*(?:@label|label)\\s+(\\S+)", Pattern.CASE_INSENSITIVE);
  private static final Pattern INCLUDE_PATTERN = Pattern.compile("^\\s*@include\\s+(.+)$", Pattern.CASE_INSENSITIVE);
  private static final Pattern BG_CMD_PATTERN = Pattern.compile("^\\s*\\[(?:bg|background)\\s+(\\S+)]", Pattern.CASE_INSENSITIVE);
  private static final Pattern BG_DECL_PATTERN = Pattern.compile("^\\s*@background\\s+(\\S+)\\s+(.+)", Pattern.CASE_INSENSITIVE);
  private static final Pattern CHARIMG_PATTERN = Pattern.compile("^\\s*@charimg\\s+(\\S+)\\s+(\\S+)\\s+(.+)", Pattern.CASE_INSENSITIVE);
  private static final Pattern CHARLAYER_PATTERN = Pattern.compile("^\\s*@charlayer\\s+(\\S+)\\s+(\\S+)\\s+(.+)", Pattern.CASE_INSENSITIVE);
  private static final Pattern CHARPRESET_PATTERN = Pattern.compile("^\\s*@charpreset\\s+(\\S+)\\s+(\\S+)\\s+(.+)", Pattern.CASE_INSENSITIVE);
  private static final Pattern EXT_CHAR_SHOW = Pattern.compile("^\\s*@external\\s+char(?:acter)?\\s+(\\S+)\\s+show\\s+(\\S+)(?:\\s+(\\S+))?", Pattern.CASE_INSENSITIVE);
  private static final Pattern EXT_CHAR_HIDE = Pattern.compile("^\\s*@external\\s+char(?:acter)?\\s+(\\S+)\\s+hide", Pattern.CASE_INSENSITIVE);
  private static final Pattern EXT_CHAR_MOVE = Pattern.compile("^\\s*@external\\s+char(?:acter)?\\s+(\\S+)\\s+move\\s+(\\S+)", Pattern.CASE_INSENSITIVE);
  private static final Pattern EXT_CHAR_EXPR = Pattern.compile("^\\s*@external\\s+char(?:acter)?\\s+(\\S+)\\s+(?:expression|expr)\\s+(\\S+)", Pattern.CASE_INSENSITIVE);
  private static final Set<String> KNOWN_POSITIONS = Set.of("far_left", "left", "center", "right", "far_right");

  private final Label lblHeader;
  private final Label lblLine;
  private final Label lblLineText;
  private final Label lblLabel;
  private final Label lblBackground;
  private final Label lblSummary;
  private final VBox characterList;
  private final VBox diagnosticsList;
  private final Button btnLaunch;
  private final Button btnLaunchLabelStart;

  private String currentSource = "";
  private int currentLine = 0;
  private File projectRoot;
  private File activeScriptFile;
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

    lblSummary = new Label("Snapshot: —");
    lblSummary.setStyle("-fx-text-fill: #8fc0ff; -fx-font-size: 11px;");

    Label charsHeader = new Label("Visible Characters:");
    charsHeader.setStyle("-fx-text-fill: #e6e6e6; -fx-font-size: 11px; -fx-font-weight: bold;");

    characterList = new VBox(2);
    characterList.setPadding(new Insets(0, 0, 0, 8));

    Label diagnosticsHeader = new Label("Snapshot Diagnostics:");
    diagnosticsHeader.setStyle("-fx-text-fill: #e6e6e6; -fx-font-size: 11px; -fx-font-weight: bold;");

    diagnosticsList = new VBox(2);
    diagnosticsList.setPadding(new Insets(0, 0, 0, 8));

    btnLaunch = new Button("Launch @ Cursor");
    btnLaunch.setStyle("-fx-background-color: #4da3ff; -fx-text-fill: #121212; -fx-font-weight: bold;");
    btnLaunch.setMaxWidth(Double.MAX_VALUE);
    btnLaunch.setTooltip(new javafx.scene.control.Tooltip("Launch Puppeteer with scene snapshot from the current cursor line"));
    btnLaunch.setOnAction(e -> {
      if (onLaunch != null) onLaunch.accept(buildSnapshot(currentLine));
    });

    btnLaunchLabelStart = new Button("Launch @ Label Start");
    btnLaunchLabelStart.setStyle("-fx-background-color: #2d3240; -fx-text-fill: #d2e6ff; -fx-font-weight: bold;");
    btnLaunchLabelStart.setMaxWidth(Double.MAX_VALUE);
    btnLaunchLabelStart.setTooltip(new javafx.scene.control.Tooltip("Launch Puppeteer from the active label start line"));
    btnLaunchLabelStart.setOnAction(e -> {
      if (onLaunch == null) return;
      int labelStartLine = resolveActiveLabelStartLine(currentSource, currentLine);
      onLaunch.accept(buildSnapshot(labelStartLine));
    });

    HBox actionRow = new HBox(6, btnLaunch, btnLaunchLabelStart);
    HBox.setHgrow(btnLaunch, Priority.ALWAYS);
    HBox.setHgrow(btnLaunchLabelStart, Priority.ALWAYS);

    getChildren().addAll(
        lblHeader,
        new Separator(),
        lblLine,
        lblLineText,
        new Separator(),
        snapshotHeader,
        lblLabel,
        lblBackground,
        lblSummary,
        charsHeader,
        characterList,
        new Separator(),
        diagnosticsHeader,
        diagnosticsList,
        new Separator(),
        actionRow
    );

    updateEmpty();
  }

  public void setOnLaunch(Consumer<SceneSnapshot> handler) {
    this.onLaunch = handler;
  }

  public void setProjectRoot(File projectRoot) {
    this.projectRoot = projectRoot;
  }

  public void setActiveScriptFile(File activeScriptFile) {
    this.activeScriptFile = activeScriptFile;
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
    lblSummary.setText("Snapshot: —");
    characterList.getChildren().clear();
    Label none = new Label("—");
    none.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 11px;");
    characterList.getChildren().add(none);
    diagnosticsList.getChildren().clear();
    Label hint = new Label("Open a .vns file to enable launch actions.");
    hint.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 11px;");
    diagnosticsList.getChildren().add(hint);
    btnLaunch.setDisable(true);
    btnLaunchLabelStart.setDisable(true);
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

    SceneSnapshot snap = buildSnapshot(lineIdx);

    lblLabel.setText("Label: " + (snap.currentLabel != null ? snap.currentLabel : "(before first label)"));
    lblBackground.setText("Background: " + (snap.backgroundId != null ? snap.backgroundId : "—"));
    lblSummary.setText("Snapshot: " + snap.characters.size() + " character(s) • source line " + (lineIdx + 1));

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

    diagnosticsList.getChildren().clear();
    List<String> diagnostics = buildDiagnostics(snap);
    if (diagnostics.isEmpty()) {
      Label ok = new Label("All visible snapshot assets are mapped.");
      ok.setStyle("-fx-text-fill: #8bd17c; -fx-font-size: 11px;");
      diagnosticsList.getChildren().add(ok);
    } else {
      for (String msg : diagnostics) {
        Label warn = new Label("• " + msg);
        warn.setStyle("-fx-text-fill: #f0b673; -fx-font-size: 11px;");
        warn.setWrapText(true);
        diagnosticsList.getChildren().add(warn);
      }
    }

    btnLaunch.setDisable(false);
    btnLaunchLabelStart.setDisable(false);
  }

  // --- VNS Scene State Resolver ---

  static SceneSnapshot resolveSnapshot(String source, int upToLine) {
    return resolveSnapshot(source, upToLine, null, null);
  }

  static SceneSnapshot resolveSnapshot(
      String source,
      int upToLine,
      String sourceName,
      IncludeSourceResolver includeResolver
  ) {
    String[] lines = source.split("\n", -1);
    int limit = Math.max(0, Math.min(upToLine, lines.length - 1));

    String currentLabel = null;
    String backgroundId = null;
    Map<String, CharacterEntry> visible = new LinkedHashMap<>();
    Map<String, String> bgPaths = new LinkedHashMap<>();
    Map<String, String> charImgPaths = new LinkedHashMap<>();
    Map<String, Map<String, String>> charLayerPaths = new LinkedHashMap<>();

    collectDeclarations(
        source,
        limit,
        sourceName,
        includeResolver,
        bgPaths,
        charImgPaths,
        charLayerPaths,
        new HashSet<>());

    for (int i = 0; i <= limit; i++) {
      String line = lines[i];
      String commandLine = stripInlineComment(line);
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

      // @background declaration — capture id → path mapping
      m = BG_DECL_PATTERN.matcher(line);
      if (m.find()) {
        bgPaths.put(m.group(1), m.group(2).trim());
        continue;
      }

      // @charimg declaration — capture charId+expression → path mapping
      m = CHARIMG_PATTERN.matcher(line);
      if (m.find()) {
        charImgPaths.put(m.group(1) + "/" + m.group(2), m.group(3).trim());
        continue;
      }

      // @charlayer declaration — capture charId/layerId -> path mapping
      m = CHARLAYER_PATTERN.matcher(line);
      if (m.find()) {
        String charId = m.group(1);
        String layerId = m.group(2);
        String path = m.group(3).trim();
        charLayerPaths.computeIfAbsent(charId, k -> new LinkedHashMap<>()).put(layerId, path);
        continue;
      }

      // @charpreset declaration — resolve $layer references into an @charimg-style mapping
      m = CHARPRESET_PATTERN.matcher(line);
      if (m.find()) {
        String charId = m.group(1);
        String expr = m.group(2);
        String spec = m.group(3).trim();
        String resolved = resolvePresetSpec(charLayerPaths, charId, spec);
        if (!resolved.isBlank()) {
          charImgPaths.put(charId + "/" + expr, resolved);
        }
        continue;
      }

      // [show charId pos expression?]
      CharacterEntry showEntry = parseShowCommand(commandLine);
      if (showEntry != null) {
        visible.put(showEntry.characterId, showEntry);
        continue;
      }

      // [hide charId]
      String hideCharacterId = parseHideCommand(commandLine);
      if (hideCharacterId != null) {
        visible.remove(hideCharacterId);
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

    return new SceneSnapshot(currentLabel, backgroundId, new ArrayList<>(visible.values()), limit, bgPaths, charImgPaths);
  }

  private SceneSnapshot buildSnapshot(int lineIdx) {
    return resolveSnapshot(
        currentSource,
        lineIdx,
        activeScriptFile == null ? null : activeScriptFile.getAbsolutePath(),
        this::resolveIncludeSource);
  }

  static int resolveActiveLabelStartLine(String source, int upToLine) {
    if (source == null || source.isBlank()) return 0;
    String[] lines = source.split("\n", -1);
    int limit = Math.max(0, Math.min(upToLine, lines.length - 1));
    int latestLabelLine = 0;
    for (int i = 0; i <= limit; i++) {
      Matcher m = LABEL_PATTERN.matcher(lines[i]);
      if (m.find()) latestLabelLine = i;
    }
    return latestLabelLine;
  }

  private static List<String> buildDiagnostics(SceneSnapshot snapshot) {
    List<String> out = new ArrayList<>();
    if (snapshot == null) return out;

    if (snapshot.backgroundId != null && !snapshot.hasBackgroundPathMapping()) {
      out.add("Background '" + snapshot.backgroundId + "' has no @background path mapping.");
    }

    for (CharacterEntry ch : snapshot.characters) {
      if (ch == null || ch.characterId == null || ch.characterId.isBlank()) continue;
      if (!snapshot.hasCharacterPathMapping(ch.characterId, ch.expression)) {
        String expr = (ch.expression == null || ch.expression.isBlank()) ? "neutral" : ch.expression;
        out.add("Character '" + ch.characterId + "' expression '" + expr + "' has no @charimg/@charpreset mapping.");
      }
      if (ch.position == null || !KNOWN_POSITIONS.contains(ch.position.toLowerCase(Locale.ROOT))) {
        out.add("Character '" + ch.characterId + "' uses position '" + ch.position + "' (Puppeteer launch falls back to center).");
      }
    }
    return out;
  }

  private static CharacterEntry parseShowCommand(String line) {
    List<String> tokens = bracketTokens(line);
    if (tokens.size() < 2 || !"show".equalsIgnoreCase(tokens.get(0))) return null;
    String charId = tokens.get(1);
    if (charId == null || charId.isBlank()) return null;

    String position = null;
    String expression = null;
    for (int i = 2; i < tokens.size(); i++) {
      String token = tokens.get(i);
      if (token == null || token.isBlank()) continue;
      String lower = token.toLowerCase(Locale.ROOT);
      if ("at".equals(lower)) {
        if (i + 1 < tokens.size()) {
          String atValue = tokens.get(++i);
          if (atValue != null && !atValue.isBlank()) {
            String atLower = atValue.toLowerCase(Locale.ROOT);
            if (isKnownPosition(atLower) && position == null) {
              position = atLower;
            } else if (expression == null) {
              expression = atValue;
            }
          }
        }
        continue;
      }
      if (position == null && isKnownPosition(lower)) {
        position = lower;
        continue;
      }
      if (expression == null) expression = token;
    }

    if (position == null) position = "center";
    if (expression == null || expression.isBlank()) expression = "neutral";
    return new CharacterEntry(charId, position, expression);
  }

  private static String parseHideCommand(String line) {
    List<String> tokens = bracketTokens(line);
    if (tokens.size() < 2 || !"hide".equalsIgnoreCase(tokens.get(0))) return null;
    String charId = tokens.get(1);
    return (charId == null || charId.isBlank()) ? null : charId;
  }

  private static List<String> bracketTokens(String line) {
    String raw = stripInlineComment(line).trim();
    if (raw.length() < 2 || raw.charAt(0) != '[' || raw.charAt(raw.length() - 1) != ']') {
      return List.of();
    }
    String inner = raw.substring(1, raw.length() - 1).trim();
    if (inner.isEmpty()) return List.of();
    return Arrays.stream(inner.split("\\s+"))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .toList();
  }

  private static String stripInlineComment(String line) {
    if (line == null || line.isBlank()) return "";
    int comment = line.indexOf('#');
    return comment >= 0 ? line.substring(0, comment) : line;
  }

  private static boolean isKnownPosition(String value) {
    return value != null && KNOWN_POSITIONS.contains(value.toLowerCase(Locale.ROOT));
  }

  private static void collectDeclarations(
      String source,
      int maxLineInclusive,
      String sourceName,
      IncludeSourceResolver includeResolver,
      Map<String, String> bgPaths,
      Map<String, String> charImgPaths,
      Map<String, Map<String, String>> charLayerPaths,
      Set<String> includeStack
  ) {
    if (source == null || source.isBlank()) return;
    String normalizedSource = normalizeSourceName(sourceName);
    if (!includeStack.add(normalizedSource)) return;
    try {
      String[] lines = source.split("\n", -1);
      int limit = maxLineInclusive < 0 ? lines.length - 1 : Math.min(maxLineInclusive, lines.length - 1);
      for (int i = 0; i <= limit; i++) {
        String line = lines[i];
        String trimmed = stripInlineComment(line).trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;

        Matcher includeMatcher = INCLUDE_PATTERN.matcher(trimmed);
        if (includeMatcher.matches() && includeResolver != null) {
          String includePath = stripQuotes(includeMatcher.group(1).trim());
          if (!includePath.isEmpty()) {
            try {
              ResolvedInclude resolved = includeResolver.resolve(normalizedSource, includePath);
              if (resolved != null && resolved.sourceText() != null && !resolved.sourceText().isBlank()) {
                collectDeclarations(
                    resolved.sourceText(),
                    -1,
                    resolved.sourceName(),
                    includeResolver,
                    bgPaths,
                    charImgPaths,
                    charLayerPaths,
                    includeStack);
              }
            } catch (IOException ignored) {
            }
          }
          continue;
        }

        Matcher backgroundMatcher = BG_DECL_PATTERN.matcher(trimmed);
        if (backgroundMatcher.matches()) {
          bgPaths.put(backgroundMatcher.group(1), backgroundMatcher.group(2).trim());
          continue;
        }

        Matcher charImgMatcher = CHARIMG_PATTERN.matcher(trimmed);
        if (charImgMatcher.matches()) {
          charImgPaths.put(
              charImgMatcher.group(1) + "/" + charImgMatcher.group(2),
              charImgMatcher.group(3).trim());
          continue;
        }

        Matcher charLayerMatcher = CHARLAYER_PATTERN.matcher(trimmed);
        if (charLayerMatcher.matches()) {
          String charId = charLayerMatcher.group(1);
          String layerId = charLayerMatcher.group(2);
          String path = charLayerMatcher.group(3).trim();
          charLayerPaths.computeIfAbsent(charId, k -> new LinkedHashMap<>()).put(layerId, path);
          continue;
        }

        Matcher charPresetMatcher = CHARPRESET_PATTERN.matcher(trimmed);
        if (charPresetMatcher.matches()) {
          String charId = charPresetMatcher.group(1);
          String expr = charPresetMatcher.group(2);
          String spec = charPresetMatcher.group(3).trim();
          String resolved = resolvePresetSpec(charLayerPaths, charId, spec);
          if (!resolved.isBlank()) {
            charImgPaths.put(charId + "/" + expr, resolved);
          }
        }
      }
    } finally {
      includeStack.remove(normalizedSource);
    }
  }

  private static String resolvePresetSpec(Map<String, Map<String, String>> layersByCharacter,
                                          String characterId,
                                          String spec) {
    if (spec == null || spec.isBlank()) return "";
    String[] tokens = spec.split("\\|");
    List<String> resolved = new ArrayList<>();
    for (String token : tokens) {
      if (token == null) continue;
      String part = token.trim();
      if (part.isEmpty()) continue;
      if (part.startsWith("$")) {
        LayerRef ref = parseLayerRef(part.substring(1).trim(), characterId);
        Map<String, String> layerMap = layersByCharacter.get(ref.characterId);
        if (layerMap == null) continue;
        String path = layerMap.get(ref.layerId);
        if (path == null || path.isBlank()) continue;
        resolved.add(path.trim());
      } else {
        resolved.add(part);
      }
    }
    return String.join(" | ", resolved);
  }

  private static LayerRef parseLayerRef(String rawRef, String defaultCharacterId) {
    String ref = rawRef == null ? "" : rawRef.trim();
    if (ref.isEmpty()) return new LayerRef(defaultCharacterId, "");
    String characterId = defaultCharacterId;
    String layerId = ref;

    int colon = ref.indexOf(':');
    int dot = ref.indexOf('.');
    int sep = colon >= 0 ? colon : dot;
    if (colon >= 0 && dot >= 0) {
      sep = Math.min(colon, dot);
    }
    if (sep > 0) {
      characterId = ref.substring(0, sep).trim();
      layerId = ref.substring(sep + 1).trim();
    }
    return new LayerRef(characterId, layerId);
  }

  private ResolvedInclude resolveIncludeSource(String sourceName, String includePath) throws IOException {
    String normalized = includePath == null ? "" : includePath.trim().replace('\\', '/');
    if (normalized.isBlank()) {
      throw new IOException("Include path is empty");
    }

    File anchorFile = activeScriptFile;
    if (sourceName != null && !sourceName.isBlank() && !"<script>".equals(sourceName)) {
      File candidate = new File(sourceName);
      if (candidate.isFile()) anchorFile = candidate;
    }
    if (anchorFile == null) {
      throw new IOException("Include resolver unavailable");
    }

    File root = projectRoot;
    if (root == null) {
      root = resolveWorkspaceRoot(anchorFile);
    }
    if (root == null) {
      throw new IOException("Project root unavailable");
    }

    Path rootPath = root.toPath().toAbsolutePath().normalize();
    Path scriptsRoot = ScriptEditorWorkspaceModel.resolveScriptsRoot(root);
    if (scriptsRoot == null) {
      scriptsRoot = rootPath.resolve("scripts");
    }
    scriptsRoot = scriptsRoot.toAbsolutePath().normalize();

    List<Path> candidates = new ArrayList<>();
    if (normalized.startsWith("/")) {
      candidates.add(scriptsRoot.resolve(normalized.substring(1)));
    } else {
      Path sourcePath = anchorFile.toPath().toAbsolutePath().normalize();
      Path sourceParent = sourcePath.getParent();
      if (sourceParent != null) {
        candidates.add(sourceParent.resolve(normalized));
      }
      candidates.add(scriptsRoot.resolve(normalized));
    }
    candidates.add(rootPath.resolve(normalized));

    for (Path candidate : candidates) {
      Path resolved = candidate.toAbsolutePath().normalize();
      if (!resolved.startsWith(rootPath)) continue;
      if (Files.isRegularFile(resolved)) {
        return new ResolvedInclude(resolved.toString(), Files.readString(resolved, StandardCharsets.UTF_8));
      }
    }
    throw new IOException("Included script not found: " + includePath);
  }

  private static File resolveWorkspaceRoot(File scriptFile) {
    if (scriptFile == null) return null;
    Path current = scriptFile.toPath().toAbsolutePath().normalize().getParent();
    while (current != null) {
      Path name = current.getFileName();
      if (name != null && "scripts".equalsIgnoreCase(name.toString())) {
        return current.getParent() == null ? current.toFile() : current.getParent().toFile();
      }
      current = current.getParent();
    }
    return scriptFile.getParentFile();
  }

  private static String normalizeSourceName(String sourceName) {
    if (sourceName == null || sourceName.isBlank()) return "<script>";
    return sourceName.replace('\\', '/');
  }

  private static String stripQuotes(String value) {
    if (value == null) return "";
    String trimmed = value.trim();
    if (trimmed.length() >= 2) {
      char first = trimmed.charAt(0);
      char last = trimmed.charAt(trimmed.length() - 1);
      if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
        return trimmed.substring(1, trimmed.length() - 1).trim();
      }
    }
    return trimmed;
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
    public final Map<String, String> backgroundPaths;
    public final Map<String, String> characterImagePaths;

    public SceneSnapshot(String currentLabel, String backgroundId, List<CharacterEntry> characters, int atLine,
                         Map<String, String> backgroundPaths, Map<String, String> characterImagePaths) {
      this.currentLabel = currentLabel;
      this.backgroundId = backgroundId;
      this.characters = characters;
      this.atLine = atLine;
      this.backgroundPaths = backgroundPaths;
      this.characterImagePaths = characterImagePaths;
    }

    public String resolveBackgroundPath() {
      return backgroundId != null ? backgroundPaths.getOrDefault(backgroundId, backgroundId) : null;
    }

    public boolean hasBackgroundPathMapping() {
      return backgroundId != null && backgroundPaths.containsKey(backgroundId);
    }

    public boolean hasCharacterPathMapping(String characterId, String expression) {
      if (characterId == null || characterId.isBlank()) return false;
      if (expression != null && characterImagePaths.containsKey(characterId + "/" + expression)) {
        return true;
      }
      if (characterImagePaths.containsKey(characterId + "/neutral")) {
        return true;
      }
      for (String key : characterImagePaths.keySet()) {
        if (key != null && key.startsWith(characterId + "/")) return true;
      }
      return false;
    }

    public String resolveCharacterPath(String characterId, String expression) {
      if (expression != null) {
        String key = characterId + "/" + expression;
        if (characterImagePaths.containsKey(key)) return characterImagePaths.get(key);
      }
      String neutralKey = characterId + "/neutral";
      if (characterImagePaths.containsKey(neutralKey)) return characterImagePaths.get(neutralKey);
      for (Map.Entry<String, String> e : characterImagePaths.entrySet()) {
        if (e.getKey().startsWith(characterId + "/")) return e.getValue();
      }
      return characterId;
    }
  }

  @FunctionalInterface
  interface IncludeSourceResolver {
    ResolvedInclude resolve(String sourceName, String includePath) throws IOException;
  }

  record ResolvedInclude(String sourceName, String sourceText) {}

  private record LayerRef(String characterId, String layerId) {}
}
