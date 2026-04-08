package com.jvn.editor.ui;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import com.jvn.scripting.jes.JesParseException;
import com.jvn.scripting.jes.JesParser;
import com.jvn.scripting.jes.JesToken;
import com.jvn.scripting.jes.JesTokenizer;

import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class JesCodeEditor extends BorderPane {
  public enum LintMode {
    JES_DOCUMENT,
    TIMELINE_BLOCK
  }

  private final CodeArea codeArea = new CodeArea();
  private CodeAutoCompleter completer;
  private File projectRoot;
  private final Label lintLabel = new Label();
  private int lastErrorLine = -1;
  private final List<String> cachedEntities = new ArrayList<>();
  private final List<String> cachedLabels = new ArrayList<>();
  private Consumer<String> onTextChanged;
  private boolean suppressTextChanged = false;
  private double fontSizePx = 13.0;
  private LintMode lintMode = LintMode.JES_DOCUMENT;

  private static final String[] KEYWORDS = new String[] {
    "scene","entity","component","on","key","do","timeline",
    "wait","move","depth","pivot","rotate","scale","fade","visible","expression","show","hide",
    "replace","event","property","cameraMove","cameraZoom","playAudio","call",
    // common props / literals
    "true","false","rgb","rgba","shape","circle","box",
    "static","sensor","text","image","align","additive"
  };

  // ── Structural keywords (purple bold) ──
  private static final String STRUCT_PATTERN = "\\b(?:scene|entity|component|tileset|map|layer|item|on|timeline)\\b";

  // ── Component types (gold) ──
  private static final String COMPTYPE_PATTERN = "\\b(?:Panel2D|Sprite2D|Label2D|ParticleEmitter2D|PhysicsBody2D"
    + "|Character2D|Stats|Inventory|Equipment|Ai2D|Button2D|Slider2D)\\b";

  // ── Timeline actions (blue) ──
  private static final String ACTION_PATTERN = "\\b(?:move|depth|pivot|rotate|scale|fade|visible|expression|show|hide|replace|event|property|walkToTile|wait|call"
    + "|cameraMove|cameraZoom|cameraShake|cameraFollow|damage|heal|waitForCall"
    + "|playAudio|stopAudio|emitParticles|setParallax|loop|parallel|label|jump)\\b";

  // ── Built-in functions (cyan) ──
  private static final String BUILTIN_PATTERN = "\\b(?:rgb|rgba)\\b";

  // ── Boolean literals (orange) ──
  private static final String BOOL_PATTERN = "\\b(?:true|false)\\b";

  // ── Binding sub-keywords ──
  private static final String SUBKW_PATTERN = "\\b(?:key|do)\\b";

  // ── Common value keywords ──
  private static final String VALUEKW_PATTERN = "\\b(?:shape|circle|box|static|sensor|additive|left|center|right)\\b";

  private static final String PAREN_PATTERN = "[(){}]";
  private static final String COLON_COMMA_PATTERN = "[:,]";
  private static final String STRING_PATTERN = "\"([^\\\\\"]|\\\\.)*\"";
  private static final String NUMBER_PATTERN = "-?\\b\\d+(?:\\.\\d+)?\\b";
  private static final String COMMENT_PATTERN = "//[^\\n]*";

  private static final Pattern PATTERN = Pattern.compile(
      "(?<COMMENT>"  + COMMENT_PATTERN  + ")"
    + "|(?<STRING>"  + STRING_PATTERN   + ")"
    + "|(?<BUILTIN>" + BUILTIN_PATTERN  + ")"
    + "|(?<STRUCT>"  + STRUCT_PATTERN   + ")"
    + "|(?<COMPTYPE>"+ COMPTYPE_PATTERN + ")"
    + "|(?<ACTION>"  + ACTION_PATTERN   + ")"
    + "|(?<BOOL>"    + BOOL_PATTERN     + ")"
    + "|(?<SUBKW>"   + SUBKW_PATTERN   + ")"
    + "|(?<VALUEKW>" + VALUEKW_PATTERN  + ")"
    + "|(?<NUMBER>"  + NUMBER_PATTERN   + ")"
    + "|(?<PAREN>"   + PAREN_PATTERN    + ")"
    + "|(?<PUNCT>"   + COLON_COMMA_PATTERN + ")"
  );

  public JesCodeEditor() {
    if (!codeArea.getStyleClass().contains("code-area")) {
      codeArea.getStyleClass().add("code-area");
    }
    codeArea.setParagraphGraphicFactory(line -> {
      Label ln = new Label(String.format("%d", line + 1));
      ln.getStyleClass().add("lineno");
      if (line == lastErrorLine) ln.getStyleClass().add("lineno-error");
      return ln;
    });
    codeArea.textProperty().addListener((obs, oldText, newText) -> {
      applyHighlighting(newText);
      lint(newText);
      cacheNames(newText);
      if (!suppressTextChanged && onTextChanged != null) {
        onTextChanged.accept(newText);
      }
    });
    applyHighlighting("");
    lint("");

    VirtualizedScrollPane<CodeArea> sp = new VirtualizedScrollPane<>(codeArea);
    VBox wrapper = new VBox(sp, lintLabel);
    VBox.setVgrow(sp, Priority.ALWAYS);
    sp.setMaxHeight(Double.MAX_VALUE);
    lintLabel.getStyleClass().add("lint-label");
    lintLabel.setText("Ready");
    setCenter(wrapper);

    String css = EditorTheme.stylesheetUrl();
    if (!css.isEmpty()) {
      getStylesheets().add(css);
      codeArea.getStylesheets().add(css);
    }

    completer = new CodeAutoCompleter(codeArea, ctx -> provideSuggestions(ctx));
    applyFontSize();
  }

  public String getText() { return codeArea.getText(); }
  public void setText(String s) {
    codeArea.replaceText(s == null ? "" : s);
    refreshSyntaxHighlighting();
  }
  public void setProjectRoot(File root) { this.projectRoot = root; if (completer != null) completer.setProjectRoot(root); }
  public void setTextNoEvent(String s) {
    try {
      suppressTextChanged = true;
      codeArea.replaceText(s == null ? "" : s);
      refreshSyntaxHighlighting();
    } finally {
      suppressTextChanged = false;
    }
  }
  public void setOnTextChanged(Consumer<String> listener) { this.onTextChanged = listener; }
  public void goToLine(int oneBasedLine) {
    int paragraphs = codeArea.getParagraphs().size();
    if (paragraphs <= 0) return;
    int target = Math.max(0, Math.min(paragraphs - 1, oneBasedLine - 1));
    codeArea.moveTo(target, 0);
    codeArea.requestFollowCaret();
    codeArea.requestFocus();
  }
  public void undo() { codeArea.undo(); }
  public void redo() { codeArea.redo(); }
  public void setFontSizePx(double fontSizePx) {
    this.fontSizePx = Math.max(8.0, Math.min(30.0, fontSizePx));
    applyFontSize();
  }

  public void setLintMode(LintMode mode) {
    this.lintMode = mode == null ? LintMode.JES_DOCUMENT : mode;
    lint(codeArea.getText());
  }

  public void setLintVisible(boolean visible) {
    lintLabel.setVisible(visible);
    lintLabel.setManaged(visible);
  }

  private void applyFontSize() {
    codeArea.setStyle("-fx-font-size: " + (int) fontSizePx + "px;");
  }

  private void applyHighlighting(String text) {
    codeArea.setStyleSpans(0, computeHighlighting(text == null ? "" : text));
  }

  public void refreshSyntaxHighlighting() {
    applyHighlighting(codeArea.getText());
  }

  private static StyleSpans<Collection<String>> computeHighlighting(String text) {
    Matcher matcher = PATTERN.matcher(text);
    int lastKwEnd = 0;
    StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
    while (matcher.find()) {
      String styleClass =
        matcher.group("COMMENT")  != null ? "comment"      :
        matcher.group("STRING")   != null ? "string"       :
        matcher.group("BUILTIN")  != null ? "jes-builtin"  :
        matcher.group("STRUCT")   != null ? "jes-struct"   :
        matcher.group("COMPTYPE") != null ? "jes-comptype" :
        matcher.group("ACTION")   != null ? "jes-action"   :
        matcher.group("BOOL")     != null ? "jes-bool"     :
        matcher.group("SUBKW")    != null ? "keyword"      :
        matcher.group("VALUEKW")  != null ? "jes-bool"     :
        matcher.group("NUMBER")   != null ? "number"       :
        matcher.group("PAREN")    != null ? "punct"        :
        matcher.group("PUNCT")    != null ? "punct"        : null;
      assert styleClass != null;
      spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);
      spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
      lastKwEnd = matcher.end();
    }
    spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
    return spansBuilder.create();
  }

  private List<CodeAutoCompleter.Suggestion> provideSuggestions(CodeAutoCompleter.Context ctx) {
    String p = ctx.prefix == null ? "" : ctx.prefix;
    String pl = p.toLowerCase();
    List<CodeAutoCompleter.Suggestion> out = new ArrayList<>();
    // keywords
    for (String kw : KEYWORDS) if (kw.startsWith(pl)) out.add(new CodeAutoCompleter.Suggestion(kw));
    // components and timeline actions
    for (String comp : List.of("Panel2D","Sprite2D","Label2D","ParticleEmitter2D","PhysicsBody2D","Character2D","Stats","Inventory","Equipment","Ai2D","Button2D","Slider2D")) {
      if (comp.toLowerCase().startsWith(pl)) out.add(new CodeAutoCompleter.Suggestion(comp));
    }
    for (String act : List.of("move","depth","pivot","rotate","scale","fade","visible","expression","show","hide","replace","event","property","walkToTile","cameraMove","cameraZoom","cameraShake","damage","heal","call","loop","parallel","waitForCall","emitParticles","cameraFollow","setParallax","playAudio","stopAudio","label","jump")) {
      if (act.startsWith(pl)) out.add(new CodeAutoCompleter.Suggestion(act));
    }
    for (String builtAction : List.of("toggleDebug","spawnCircle","spawnBox","moveHero","interact","attack")) {
      if (builtAction.toLowerCase().startsWith(pl)) out.add(new CodeAutoCompleter.Suggestion(builtAction));
    }
    for (String name : cachedEntities) if (name.toLowerCase().startsWith(pl)) out.add(new CodeAutoCompleter.Suggestion(name));
    for (String lab : cachedLabels) if (lab.toLowerCase().startsWith(pl)) out.add(new CodeAutoCompleter.Suggestion(lab));
    // if inside quotes and line hints an image value, suggest asset ids
    String line = currentLine(ctx.text, ctx.caret).toLowerCase();
    boolean wantsImage = line.contains("image") || line.contains("texture");
    if (wantsImage) {
      for (String dir : List.of("assets/ui", "assets/backgrounds", "assets/cg", "assets/characters")) {
        for (String id : CodeAutoCompleter.listAssetIds(projectRoot, dir, ".png", ".jpg", ".jpeg", ".webp")) {
          String nm = id.contains("/") ? id.substring(id.lastIndexOf('/')+1) : id;
          if (nm.toLowerCase().startsWith(pl) || id.toLowerCase().startsWith(pl)) out.add(new CodeAutoCompleter.Suggestion(id));
        }
      }
    }
    // de-dup
    if (out.size() > 1) {
      List<String> seen = new ArrayList<>();
      out.removeIf(sug -> { String k = sug.insert; if (seen.contains(k)) return true; seen.add(k); return false; });
    }
    return out;
  }

  private void lint(String text) {
    // Basic synchronous lint; fast enough for small scripts
    if (text == null) text = "";
    if (lintMode == LintMode.TIMELINE_BLOCK) {
      showLintMessage("Timeline block", -1);
      return;
    }
    try {
      List<JesToken> toks = new JesTokenizer(text).tokenize();
      new JesParser(toks).parseProgram();
      showLintMessage("No errors", -1);
    } catch (JesParseException ex) {
      showLintMessage(ex.getMessage(), ex.getLine());
    } catch (Exception ex) {
      showLintMessage("Error: " + ex.getMessage(), -1);
    }
  }

  private void showLintMessage(String msg, int errLine) {
    Platform.runLater(() -> {
      lintLabel.setText(msg == null ? "" : msg);
      clearErrorLine();
      if (errLine > 0) {
        lastErrorLine = errLine - 1; // CodeArea is 0-based
        if (lastErrorLine >= 0 && lastErrorLine < codeArea.getParagraphs().size()) {
          codeArea.setParagraphStyle(lastErrorLine, Collections.singleton("error-line"));
        }
      } else {
        lastErrorLine = -1;
      }
      codeArea.setParagraphGraphicFactory(line -> {
        Label ln = new Label(String.format("%d", line + 1));
        ln.getStyleClass().add("lineno");
        if (line == lastErrorLine) ln.getStyleClass().add("lineno-error");
        return ln;
      });
    });
  }

  private void clearErrorLine() {
    if (lastErrorLine >= 0 && lastErrorLine < codeArea.getParagraphs().size()) {
      codeArea.setParagraphStyle(lastErrorLine, Collections.emptyList());
    }
  }

  private void cacheNames(String text) {
    cachedEntities.clear();
    cachedLabels.clear();
    if (text == null) return;
    Matcher ent = Pattern.compile("entity\\s+\"([^\"]+)\"").matcher(text);
    while (ent.find()) {
      String name = ent.group(1);
      if (name != null && !name.isBlank()) cachedEntities.add(name);
    }
    Matcher lab = Pattern.compile("label\\s+\"([^\"]+)\"").matcher(text);
    while (lab.find()) {
      String name = lab.group(1);
      if (name != null && !name.isBlank()) cachedLabels.add(name);
    }
  }

  private static String currentLine(String text, int caret) {
    if (text == null) return "";
    int s = text.lastIndexOf('\n', Math.max(0, caret-1));
    int e = text.indexOf('\n', caret);
    if (s < 0) s = 0; else s = s + 1;
    if (e < 0) e = text.length();
    return text.substring(s, Math.min(e, text.length()));
  }
}
