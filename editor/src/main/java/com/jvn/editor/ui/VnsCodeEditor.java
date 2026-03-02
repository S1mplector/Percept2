package com.jvn.editor.ui;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import javafx.application.Platform;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;

public class VnsCodeEditor extends BorderPane {
  private final CodeArea codeArea = new CodeArea();
  private final Label lintLabel = new Label("No issues");
  private CodeAutoCompleter completer;
  private File projectRoot;
  private List<Issue> issues = List.of();
  private int highlightedIssueLine = -1;
  private boolean highlightedIssueWarning = false;
  private Consumer<String> onTextChanged;
  private Consumer<String> onLaunchFromHere;
  private EditorSearchBar searchBar;
  private boolean searchBarVisible = false;

  private static final String COMMENT_PATTERN = "(?m)#.*$";
  private static final String STRING_PATTERN = "\"([^\\\\\"]|\\\\.)*\"";
  private static final String FORMAT_PATTERN = "\\{/?[bius]\\}|\\{color=[^}]*\\}|\\{/color\\}";
  private static final String DIRECTIVE_PATTERN = "@(?:scenario|character|background|charimg|charlayer|charpreset|label|define|include|var)\\b";
  private static final String CMD_OPEN_PATTERN =
      "\\[(?:show|hide|jump|end|wait|bg|background"
    + "|bgm_crossfade|bgm_fadeout|bgm_resume|bgm_pause|bgm_seek|bgm_stop|bgm"
    + "|audio_resume_all|audio_pause_all|audio_stop_all|audio|sfx_stop|sfx|voice_stop|voice|volume|textspeed|autodelay"
    + "|hud|save|quickload|skip|auto|ui|history|screen"
    + "|jes_push|jes_replace|jes_pop|jes_call|jes|java"
    + "|transition|menu|settings|mainmenu|load|goto"
    + "|set|inc|dec|flag|unflag|clear"
    + "|if|elif|else|endif|/if"
    + "|call|gosub|return|character|char|choice)\\b";
  private static final String ARROW_PATTERN = "->";
  private static final String SPEAKER_PATTERN = "(?m)^(?:[^\\s#:][^:]{0,30}):";
  private static final String CHOICE_MARK_PATTERN = "(?m)^\\s*>";
  private static final String TIMELINE_PATTERN = "(?m)^\\s*timeline\\b";
  private static final String VALUE_PATTERN =
      "\\b(?:left|right|center|far_left|far_right"
    + "|fade|dissolve|crossfade|slide_left|slide_right|wipe"
    + "|true|false|on|off|yes|no"
    + "|goto|loop|neutral)\\b";
  private static final String NUMBER_PATTERN = "-?\\b\\d+(?:\\.\\d+)?\\b";
  private static final String PUNCT_PATTERN = "[\\[\\]()>:,=]";

  private static final Pattern TOKEN_PATTERN = Pattern.compile(
      "(?<COMMENT>"    + COMMENT_PATTERN    + ")"
    + "|(?<STRING>"    + STRING_PATTERN     + ")"
    + "|(?<FORMAT>"    + FORMAT_PATTERN     + ")"
    + "|(?<DIRECTIVE>" + DIRECTIVE_PATTERN  + ")"
    + "|(?<CMDOPEN>"   + CMD_OPEN_PATTERN   + ")"
    + "|(?<ARROW>"     + ARROW_PATTERN      + ")"
    + "|(?<SPEAKER>"   + SPEAKER_PATTERN    + ")"
    + "|(?<CHOICEMK>"  + CHOICE_MARK_PATTERN + ")"
    + "|(?<TIMELINE>"  + TIMELINE_PATTERN   + ")"
    + "|(?<VALUEKW>"   + VALUE_PATTERN      + ")"
    + "|(?<NUMBER>"    + NUMBER_PATTERN     + ")"
    + "|(?<PUNCT>"     + PUNCT_PATTERN      + ")"
  );

  private static final Pattern LABEL_PATTERN = Pattern.compile("^\\s*(?:@label|label)\\s+(\\S+)\\s*$", Pattern.CASE_INSENSITIVE);
  private static final Pattern BG_DECL_PATTERN = Pattern.compile("^\\s*@background\\s+(\\S+)\\s+(.+)\\s*$", Pattern.CASE_INSENSITIVE);
  private static final Pattern COMMAND_PATTERN = Pattern.compile("^\\s*\\[(.+)]\\s*$");
  private static final Pattern CHOICE_IF_SUFFIX_PATTERN = Pattern.compile("^(.*)\\[if\\s+(.+)]\\s*$", Pattern.CASE_INSENSITIVE);
  private static final Pattern IF_GOTO_PATTERN = Pattern.compile("^(.+?)\\s+goto\\s+(\\S+)\\s*$", Pattern.CASE_INSENSITIVE);
  private static final Pattern PARSE_LINE_PATTERN = Pattern.compile("\\bat line (\\d+)\\b", Pattern.CASE_INSENSITIVE);

  public VnsCodeEditor() {
    codeArea.setParagraphGraphicFactory(this::makeLineNumberLabel);
    codeArea.textProperty().addListener((obs, oldText, newText) -> {
      String value = newText == null ? "" : newText;
      applyAnalysis(value);
      if (onTextChanged != null) onTextChanged.accept(value);
    });

    VirtualizedScrollPane<CodeArea> sp = new VirtualizedScrollPane<>(codeArea);
    setCenter(sp);

    var css = VnsCodeEditor.class.getResource("/com/jvn/editor/editor.css");
    if (css != null) {
      getStylesheets().add(css.toExternalForm());
      codeArea.getStylesheets().add(css.toExternalForm());
    }

    completer = new CodeAutoCompleter(codeArea, this::provideSuggestions);

    setupSearchBar();

    codeArea.setOnContextMenuRequested(e -> {
      ContextMenu menu = new ContextMenu();
      // Launch from here
      MenuItem launchItem = new MenuItem("Launch from here (F5)");
      launchItem.setOnAction(a -> launchFromHere());
      menu.getItems().add(launchItem);
      MenuItem launchStartItem = new MenuItem("Launch from start (Shift+F5)");
      launchStartItem.setOnAction(a -> { if (onLaunchFromHere != null) onLaunchFromHere.accept(null); });
      menu.getItems().add(launchStartItem);
      // Quick-fix items
      Issue issue = issueAt(codeArea.getCaretPosition());
      if (issue != null) {
        ContextMenu fixMenu = buildQuickFixMenu(issue);
        if (!fixMenu.getItems().isEmpty()) {
          menu.getItems().add(new javafx.scene.control.SeparatorMenuItem());
          menu.getItems().addAll(fixMenu.getItems());
        }
      }
      menu.show(codeArea, e.getScreenX(), e.getScreenY());
      e.consume();
    });

    addEventFilter(KeyEvent.KEY_PRESSED, e -> {
      if (e.getCode() == KeyCode.F5 && !e.isControlDown() && !e.isMetaDown()) {
        if (e.isShiftDown()) {
          if (onLaunchFromHere != null) onLaunchFromHere.accept(null);
        } else {
          launchFromHere();
        }
        e.consume();
      }
    });

    applyAnalysis("");
  }

  private void setupSearchBar() {
    searchBar = new EditorSearchBar();
    searchBar.setCodeArea(codeArea);
    searchBar.setOnClose(this::hideSearchBar);
    searchBar.setVisible(false);
    searchBar.setManaged(false);

    addEventFilter(KeyEvent.KEY_PRESSED, e -> {
      if ((e.isMetaDown() || e.isControlDown()) && e.getCode() == KeyCode.F) {
        showSearchBar();
        e.consume();
      } else if (e.getCode() == KeyCode.ESCAPE && searchBarVisible) {
        hideSearchBar();
        e.consume();
      }
    });
  }

  public void showSearchBar() {
    if (!searchBarVisible) {
      setTop(searchBar);
      searchBar.setVisible(true);
      searchBar.setManaged(true);
      searchBarVisible = true;
    }
    String selected = codeArea.getSelectedText();
    if (selected != null && !selected.isEmpty() && !selected.contains("\n")) {
      searchBar.setSearchText(selected);
    }
    searchBar.focus();
  }

  public void hideSearchBar() {
    if (searchBarVisible) {
      setTop(null);
      searchBar.setVisible(false);
      searchBar.setManaged(false);
      searchBarVisible = false;
      codeArea.requestFocus();
    }
  }

  public String getText() {
    return codeArea.getText();
  }

  public void setText(String s) {
    codeArea.replaceText(s == null ? "" : s);
  }

  public void setOnTextChanged(Consumer<String> listener) {
    this.onTextChanged = listener;
  }

  public void setOnLaunchFromHere(Consumer<String> listener) {
    this.onLaunchFromHere = listener;
  }

  private static final Pattern LABEL_SCAN_PATTERN = Pattern.compile("^\\s*@label\\s+(\\S+)", Pattern.CASE_INSENSITIVE);

  private void launchFromHere() {
    if (onLaunchFromHere == null) return;
    int cursorLine = codeArea.getCurrentParagraph(); // 0-based
    String text = codeArea.getText();
    if (text == null || text.isEmpty()) { onLaunchFromHere.accept(null); return; }
    String[] lines = text.split("\\n", -1);
    for (int i = Math.min(cursorLine, lines.length - 1); i >= 0; i--) {
      Matcher m = LABEL_SCAN_PATTERN.matcher(lines[i]);
      if (m.find()) {
        onLaunchFromHere.accept(m.group(1));
        return;
      }
    }
    onLaunchFromHere.accept(null);
  }

  public int getCurrentLine() {
    return codeArea.getCurrentParagraph();
  }

  public void setOnCaretLineChanged(Consumer<Integer> listener) {
    codeArea.currentParagraphProperty().addListener((obs, oldVal, newVal) -> {
      if (listener != null && newVal != null) listener.accept(newVal.intValue());
    });
  }

  public void goToLine(int oneBasedLine) {
    int paragraphCount = codeArea.getParagraphs().size();
    if (paragraphCount <= 0) return;
    int target = Math.max(0, Math.min(paragraphCount - 1, oneBasedLine - 1));
    codeArea.moveTo(target, 0);
    codeArea.requestFollowCaret();
    codeArea.requestFocus();
  }

  public void setProjectRoot(File root) {
    this.projectRoot = root;
    if (completer != null) completer.setProjectRoot(root);
    applyAnalysis(getText());
  }

  private Label makeLineNumberLabel(int line) {
    Label ln = new Label(String.format("%d", line + 1));
    ln.getStyleClass().add("lineno");
    if (line == highlightedIssueLine) {
      ln.getStyleClass().add(highlightedIssueWarning ? "lineno-warning" : "lineno-error");
    }
    return ln;
  }

  private void insertSnippet(String s) {
    int pos = codeArea.getCaretPosition();
    codeArea.insertText(pos, s);
  }

  private void applyAnalysis(String text) {
    issues = computeIssues(text == null ? "" : text);
    codeArea.setStyleSpans(0, computeHighlightingWithIssues(text == null ? "" : text, issues));
    refreshIssuePresentation();
  }

  private StyleSpans<Collection<String>> computeHighlightingWithIssues(String text, List<Issue> currentIssues) {
    List<Span> spans = new ArrayList<>();
    Matcher matcher = TOKEN_PATTERN.matcher(text);
    int last = 0;
    while (matcher.find()) {
      String styleClass =
          matcher.group("COMMENT")   != null ? "comment"       :
          matcher.group("STRING")    != null ? "string"        :
          matcher.group("FORMAT")    != null ? "vns-format"    :
          matcher.group("DIRECTIVE") != null ? "vns-directive" :
          matcher.group("CMDOPEN")   != null ? "vns-command"   :
          matcher.group("ARROW")     != null ? "vns-arrow"     :
          matcher.group("SPEAKER")   != null ? "vns-speaker"   :
          matcher.group("CHOICEMK")  != null ? "vns-choice"    :
          matcher.group("TIMELINE")  != null ? "vns-command"   :
          matcher.group("VALUEKW")   != null ? "vns-value"     :
          matcher.group("NUMBER")    != null ? "number"        :
          matcher.group("PUNCT")     != null ? "punct"         : null;
      spans.add(new Span(last, matcher.start(), Collections.emptyList()));
      spans.add(new Span(matcher.start(), matcher.end(), Collections.singletonList(styleClass)));
      last = matcher.end();
    }
    spans.add(new Span(last, text.length(), Collections.emptyList()));

    if (currentIssues != null) {
      for (Issue issue : currentIssues) {
        String cls = issue.warning ? "warning" : "error";
        spans = overlay(spans, issue.start, issue.end, cls);
      }
    }

    StyleSpansBuilder<Collection<String>> out = new StyleSpansBuilder<>();
    for (Span s : compress(spans)) {
      out.add(s.styles, Math.max(0, s.end - s.start));
    }
    return out.create();
  }

  private void refreshIssuePresentation() {
    int prevLine = highlightedIssueLine;
    highlightedIssueLine = -1;
    highlightedIssueWarning = false;

    if (prevLine >= 0 && prevLine < codeArea.getParagraphs().size()) {
      codeArea.setParagraphStyle(prevLine, Collections.emptyList());
    }

    Issue firstError = null;
    Issue firstWarning = null;
    int errors = 0;
    int warnings = 0;

    for (Issue issue : issues) {
      if (issue.warning) {
        warnings++;
        if (firstWarning == null) firstWarning = issue;
      } else {
        errors++;
        if (firstError == null) firstError = issue;
      }
    }

    if (errors == 0 && warnings == 0) {
      lintLabel.setText("No issues");
    } else {
      StringBuilder summary = new StringBuilder();
      if (errors > 0) summary.append(errors).append(errors == 1 ? " error" : " errors");
      if (warnings > 0) {
        if (summary.length() > 0) summary.append(", ");
        summary.append(warnings).append(warnings == 1 ? " warning" : " warnings");
      }
      Issue first = firstError != null ? firstError : firstWarning;
      if (first != null && first.message != null && !first.message.isBlank()) {
        summary.append(" - ").append(first.message);
      }
      lintLabel.setText(summary.toString());
    }

    Issue focusIssue = firstError != null ? firstError : firstWarning;
    if (focusIssue != null && focusIssue.line >= 0) {
      highlightedIssueLine = focusIssue.line;
      highlightedIssueWarning = focusIssue.warning;
      if (highlightedIssueLine < codeArea.getParagraphs().size()) {
        codeArea.setParagraphStyle(highlightedIssueLine, Collections.singleton(highlightedIssueWarning ? "warning-line" : "error-line"));
      }
    }

    Platform.runLater(() -> codeArea.setParagraphGraphicFactory(this::makeLineNumberLabel));
  }

  /**
   * Delegates to VnsScriptAnalyzer.analyze() to avoid duplicated lint logic.
   * Converts VnsScriptAnalyzer.Diagnostic to local Issue type for UI integration.
   */
  private List<Issue> computeIssues(String text) {
    VnsScriptAnalyzer.Analysis analysis = VnsScriptAnalyzer.analyze(text, projectRoot);
    List<Issue> out = new ArrayList<>();

    for (VnsScriptAnalyzer.Diagnostic diag : analysis.diagnostics()) {
      if (diag.warning()) {
        out.add(Issue.warning(
            diag.kind(),
            diag.message(),
            diag.start(),
            diag.end(),
            diag.line(),
            diag.label(),
            diag.assetPath(),
            diag.blockEnd()
        ));
      } else {
        out.add(Issue.error(
            diag.kind(),
            diag.message(),
            diag.start(),
            diag.end(),
            diag.line(),
            diag.label(),
            diag.assetPath(),
            diag.blockEnd()
        ));
      }
    }

    return out;
  }

  private List<Issue> computeUnreachableLabelIssues(String source,
                                                    Map<String, LabelDef> labels,
                                                    List<LabelRef> refs,
                                                    List<LineInfo> lines) {
    if (labels.isEmpty()) return List.of();

    List<LabelDef> orderedLabels = new ArrayList<>(labels.values());
    orderedLabels.sort((a, b) -> Integer.compare(a.line, b.line));

    Map<String, Set<String>> edges = new HashMap<>();
    for (LabelDef def : orderedLabels) {
      edges.put(def.name, new HashSet<>());
    }

    for (LabelDef def : orderedLabels) {
      int idx = orderedLabels.indexOf(def);
      int startLine = def.line + 1;
      int endLine = idx + 1 < orderedLabels.size() ? orderedLabels.get(idx + 1).line : lines.size();

      boolean terminal = false;
      for (int i = startLine; i < endLine; i++) {
        if (i < 0 || i >= lines.size()) continue;
        String trimmed = lines.get(i).trimmed();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;

        if (trimmed.startsWith(">")) {
          LabelRef cr = extractChoiceReference(lines.get(i), trimmed.substring(1).trim());
          if (cr != null && labels.containsKey(cr.label)) {
            edges.get(def.name).add(cr.label);
          }
          continue;
        }

        Matcher cm = COMMAND_PATTERN.matcher(trimmed);
        if (!cm.matches()) continue;
        String body = cm.group(1).trim();
        if (body.isEmpty()) continue;

        String[] parts = body.split("\\s+", 2);
        String cmd = parts[0].toLowerCase(Locale.ROOT);
        String arg = parts.length > 1 ? parts[1].trim() : "";

        if ("jump".equals(cmd)) {
          String tgt = firstToken(arg);
          if (labels.containsKey(tgt)) edges.get(def.name).add(tgt);
          terminal = true;
          break;
        }
        if ("end".equals(cmd)) {
          terminal = true;
          break;
        }
        if ("if".equals(cmd)) {
          Matcher m = IF_GOTO_PATTERN.matcher(arg);
          if (m.matches()) {
            String tgt = m.group(2).trim();
            if (labels.containsKey(tgt)) edges.get(def.name).add(tgt);
          }
          continue;
        }
        if ("choice".equals(cmd)) {
          String[] segs = arg.split("\\|");
          for (String seg : segs) {
            LabelRef cr = extractChoiceReference(lines.get(i), seg == null ? "" : seg.trim());
            if (cr != null && labels.containsKey(cr.label)) {
              edges.get(def.name).add(cr.label);
            }
          }
        }
      }

      if (!terminal && idx + 1 < orderedLabels.size()) {
        edges.get(def.name).add(orderedLabels.get(idx + 1).name);
      }
    }

    String start = orderedLabels.get(0).name;
    Set<String> reachable = new HashSet<>();
    ArrayDeque<String> queue = new ArrayDeque<>();
    queue.add(start);

    while (!queue.isEmpty()) {
      String current = queue.removeFirst();
      if (!reachable.add(current)) continue;
      for (String nxt : edges.getOrDefault(current, Set.of())) {
        if (!reachable.contains(nxt)) queue.addLast(nxt);
      }
    }

    List<Issue> out = new ArrayList<>();
    for (int i = 0; i < orderedLabels.size(); i++) {
      LabelDef def = orderedLabels.get(i);
      if (reachable.contains(def.name)) continue;

      int blockEnd = i + 1 < orderedLabels.size() ? orderedLabels.get(i + 1).tokenStart : source.length();
      out.add(Issue.warning(
          "unreachable_label",
          "Unreachable label: " + def.name,
          def.tokenStart,
          def.tokenEnd,
          def.line,
          def.name,
          null,
          blockEnd
      ));
    }

    return out;
  }

  private void addChoiceReference(LineInfo line, String segment, List<LabelRef> refs) {
    LabelRef ref = extractChoiceReference(line, segment);
    if (ref != null) refs.add(ref);
  }

  private LabelRef extractChoiceReference(LineInfo line, String segment) {
    if (segment == null || segment.isBlank()) return null;
    String work = segment.trim();

    Matcher suffix = CHOICE_IF_SUFFIX_PATTERN.matcher(work);
    if (suffix.matches()) {
      work = suffix.group(1).trim();
    }

    int arrow = work.indexOf("->");
    if (arrow < 0) return null;

    String right = work.substring(arrow + 2).trim();
    if (right.isEmpty()) return null;

    String target = firstToken(right);
    int targetInLine = safeIndexOf(line.text, target, 0);
    int start = line.start + targetInLine;
    return new LabelRef(target, start, start + target.length(), line.index);
  }

  private boolean assetExists(String path) {
    if (path == null || path.isBlank()) return false;
    File resolved = resolveAssetPath(path);
    return resolved != null && resolved.exists() && resolved.isFile();
  }

  private File resolveAssetPath(String rawPath) {
    if (rawPath == null || rawPath.isBlank()) return null;
    String normalized = rawPath.trim().replace('\\', '/');
    File direct = new File(normalized);
    if (direct.isAbsolute()) return direct;
    if (projectRoot == null) return null;

    List<String> candidates = new ArrayList<>();
    candidates.add(normalized);

    String rel = normalized;
    if (rel.startsWith("./")) rel = rel.substring(2);
    if (rel.startsWith("game/images/")) rel = rel.substring("game/images/".length());
    if (rel.startsWith("images/")) rel = rel.substring("images/".length());
    if (rel.startsWith("assets/")) rel = rel.substring("assets/".length());

    candidates.add("assets/" + rel);
    candidates.add("assets/images/" + rel);
    candidates.add("assets/backgrounds/" + rel);

    String fileName = rel.contains("/") ? rel.substring(rel.lastIndexOf('/') + 1) : rel;
    if (!fileName.isBlank()) {
      candidates.add("assets/backgrounds/" + fileName);
      candidates.add("assets/images/backgrounds/" + fileName);
      candidates.add("game/images/backgrounds/" + fileName);
    }

    for (String candidate : candidates) {
      File f = new File(projectRoot, candidate);
      if (f.exists()) return f;
    }
    return new File(projectRoot, normalized);
  }

  private List<String> listBackgroundAssets() {
    if (projectRoot == null) return List.of();
    List<String> files = new ArrayList<>();
    collectFiles(files, projectRoot.toPath().resolve("assets/backgrounds"));
    collectFiles(files, projectRoot.toPath().resolve("assets/images/backgrounds"));
    collectFiles(files, projectRoot.toPath().resolve("game/images/backgrounds"));
    files.sort(String.CASE_INSENSITIVE_ORDER);
    return files;
  }

  private void collectFiles(List<String> out, Path dir) {
    try {
      if (dir == null || !Files.exists(dir) || !Files.isDirectory(dir)) return;
      try (var stream = Files.walk(dir, 4)) {
        stream.filter(Files::isRegularFile).forEach(p -> {
          String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
          if (!(n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".webp"))) return;
          Path rel = projectRoot.toPath().relativize(p);
          out.add(rel.toString().replace('\\', '/'));
        });
      }
    } catch (Exception ignored) {
    }
  }

  private ContextMenu buildQuickFixMenu(Issue issue) {
    ContextMenu menu = new ContextMenu();
    if (issue == null) return menu;

    if ("undefined_label".equals(issue.kind) && issue.label != null) {
      MenuItem create = new MenuItem("Create label '" + issue.label + "'");
      create.setOnAction(e -> createMissingLabel(issue.label, issue));
      menu.getItems().add(create);

      List<String> labels = extractLabels(codeArea.getText());
      if (!labels.isEmpty()) {
        MenuItem replace = new MenuItem("Change to existing label...");
        replace.setOnAction(e -> replaceUndefinedLabel(issue, labels));
        menu.getItems().add(replace);
      }
    }

    if ("missing_asset".equals(issue.kind)) {
      List<String> assets = listBackgroundAssets();
      if (!assets.isEmpty()) {
        MenuItem choose = new MenuItem("Choose existing asset...");
        choose.setOnAction(e -> replaceAssetPath(issue, assets));
        menu.getItems().add(choose);
      }
    }

    if ("unreachable_label".equals(issue.kind) && issue.blockEnd > issue.start) {
      MenuItem removeBlock = new MenuItem("Delete unreachable block");
      removeBlock.setOnAction(e -> deleteIssueBlock(issue));
      menu.getItems().add(removeBlock);
    }

    return menu;
  }

  private void createMissingLabel(String label, Issue issue) {
    if (label == null || label.isBlank()) return;
    String text = codeArea.getText();
    List<LineInfo> lines = splitLines(text);
    int targetLine = issue != null ? issue.line : Math.max(0, codeArea.getCurrentParagraph());
    targetLine = Math.max(0, Math.min(targetLine, lines.size() - 1));

    int insertOffset = lineInsertOffset(lines, targetLine + 1);
    String insertion =
        System.lineSeparator() +
        "@label " + label + System.lineSeparator() +
        "narrator \"TODO: implement this branch.\"" + System.lineSeparator();
    codeArea.insertText(insertOffset, insertion);
  }

  private void replaceUndefinedLabel(Issue issue, List<String> labels) {
    if (issue == null || labels == null || labels.isEmpty()) return;
    ChoiceDialog<String> dialog = new ChoiceDialog<>(labels.get(0), labels);
    EditorTheme.apply(dialog);
    dialog.setHeaderText(null);
    dialog.setTitle("Replace Label");
    dialog.setContentText("Label:");
    var choice = dialog.showAndWait();
    choice.ifPresent(value -> replaceIssueRange(issue, value));
  }

  private void replaceAssetPath(Issue issue, List<String> assets) {
    if (issue == null || assets == null || assets.isEmpty()) return;
    ChoiceDialog<String> dialog = new ChoiceDialog<>(assets.get(0), assets);
    EditorTheme.apply(dialog);
    dialog.setHeaderText(null);
    dialog.setTitle("Replace Asset Path");
    dialog.setContentText("Asset:");
    var choice = dialog.showAndWait();
    choice.ifPresent(value -> replaceIssueRange(issue, value));
  }

  private Issue issueAt(int caret) {
    if (issues == null) return null;
    for (Issue issue : issues) {
      if (caret >= issue.start && caret <= issue.end) return issue;
    }
    return null;
  }

  private List<CodeAutoCompleter.Suggestion> provideSuggestions(CodeAutoCompleter.Context ctx) {
    String p = ctx.prefix == null ? "" : ctx.prefix;
    String pl = p.toLowerCase(Locale.ROOT);
    List<CodeAutoCompleter.Suggestion> out = new ArrayList<>();

    if (pl.startsWith("@")) {
      out.add(new CodeAutoCompleter.Suggestion("@scenario "));
      out.add(new CodeAutoCompleter.Suggestion("@character "));
      out.add(new CodeAutoCompleter.Suggestion("@background "));
      out.add(new CodeAutoCompleter.Suggestion("@charimg "));
      out.add(new CodeAutoCompleter.Suggestion("@charlayer "));
      out.add(new CodeAutoCompleter.Suggestion("@charpreset "));
      out.add(new CodeAutoCompleter.Suggestion("@label "));
      out.add(new CodeAutoCompleter.Suggestion("@var "));
      out.add(new CodeAutoCompleter.Suggestion("@define "));
      out.add(new CodeAutoCompleter.Suggestion("@include "));
    }

    if (pl.startsWith("[")) {
      out.add(new CodeAutoCompleter.Suggestion("[background "));
      out.add(new CodeAutoCompleter.Suggestion("[jump "));
      out.add(new CodeAutoCompleter.Suggestion("[bgm "));
      out.add(new CodeAutoCompleter.Suggestion("[bgm_crossfade "));
      out.add(new CodeAutoCompleter.Suggestion("[bgm_pause]"));
      out.add(new CodeAutoCompleter.Suggestion("[bgm_resume]"));
      out.add(new CodeAutoCompleter.Suggestion("[bgm_seek "));
      out.add(new CodeAutoCompleter.Suggestion("[bgm_stop]"));
      out.add(new CodeAutoCompleter.Suggestion("[sfx "));
      out.add(new CodeAutoCompleter.Suggestion("[sfx_stop]"));
      out.add(new CodeAutoCompleter.Suggestion("[voice "));
      out.add(new CodeAutoCompleter.Suggestion("[voice_stop]"));
      out.add(new CodeAutoCompleter.Suggestion("[audio_stop_all]"));
      out.add(new CodeAutoCompleter.Suggestion("[audio_pause_all]"));
      out.add(new CodeAutoCompleter.Suggestion("[audio_resume_all]"));
      out.add(new CodeAutoCompleter.Suggestion("[audio "));
      out.add(new CodeAutoCompleter.Suggestion("[set "));
      out.add(new CodeAutoCompleter.Suggestion("[if "));
      out.add(new CodeAutoCompleter.Suggestion("[elif "));
      out.add(new CodeAutoCompleter.Suggestion("[else]"));
      out.add(new CodeAutoCompleter.Suggestion("[endif]"));
      out.add(new CodeAutoCompleter.Suggestion("[call hud "));
      out.add(new CodeAutoCompleter.Suggestion("[java "));
      out.add(new CodeAutoCompleter.Suggestion("[mainmenu "));
      out.add(new CodeAutoCompleter.Suggestion("[load "));
      out.add(new CodeAutoCompleter.Suggestion("[goto "));
      out.add(new CodeAutoCompleter.Suggestion("[jes call "));
    }

    for (String lab : extractLabels(ctx.text)) {
      if (lab.toLowerCase(Locale.ROOT).startsWith(pl)) {
        out.add(new CodeAutoCompleter.Suggestion(lab));
      }
    }

    for (String id : CodeAutoCompleter.listAssetIds(projectRoot, "assets/backgrounds", ".png", ".jpg", ".jpeg", ".webp")) {
      String nm = id.contains("/") ? id.substring(id.lastIndexOf('/') + 1) : id;
      if (nm.toLowerCase(Locale.ROOT).startsWith(pl) || id.toLowerCase(Locale.ROOT).startsWith(pl)) {
        out.add(new CodeAutoCompleter.Suggestion(id));
      }
    }

    if (out.size() > 1) {
      Set<String> seen = new HashSet<>();
      out.removeIf(sug -> !seen.add(sug.insert));
    }
    return out;
  }

  private List<String> extractLabels(String text) {
    List<String> res = new ArrayList<>();
    if (text == null || text.isBlank()) return res;

    Matcher modern = Pattern.compile("(?m)^\\s*@label\\s+(\\S+)", Pattern.CASE_INSENSITIVE).matcher(text);
    while (modern.find()) {
      res.add(modern.group(1));
    }

    Matcher legacy = Pattern.compile("(?m)^\\s*label\\s+(\\S+)", Pattern.CASE_INSENSITIVE).matcher(text);
    while (legacy.find()) {
      String name = legacy.group(1);
      if (!res.contains(name)) res.add(name);
    }

    return res;
  }

  private int parseLineFromMessage(String message) {
    if (message == null || message.isBlank()) return -1;
    Matcher m = PARSE_LINE_PATTERN.matcher(message);
    if (!m.find()) return -1;
    try {
      return Integer.parseInt(m.group(1));
    } catch (Exception ignored) {
      return -1;
    }
  }

  private static List<LineInfo> splitLines(String text) {
    List<LineInfo> out = new ArrayList<>();
    if (text == null) {
      out.add(new LineInfo(0, 0, 0, ""));
      return out;
    }
    int lineIndex = 0;
    int start = 0;
    for (int i = 0; i <= text.length(); i++) {
      if (i == text.length() || text.charAt(i) == '\n') {
        String line = text.substring(start, i);
        out.add(new LineInfo(lineIndex, start, i, line));
        lineIndex++;
        start = i + 1;
      }
    }
    if (out.isEmpty()) out.add(new LineInfo(0, 0, 0, ""));
    return out;
  }

  private static int[] lineBounds(String text, int zeroBasedLine) {
    if (zeroBasedLine < 0) return new int[] {0, 0};
    int currentLine = 0;
    int start = 0;
    for (int i = 0; i <= text.length(); i++) {
      if (i == text.length() || text.charAt(i) == '\n') {
        if (currentLine == zeroBasedLine) {
          return new int[] {start, i};
        }
        currentLine++;
        start = i + 1;
      }
    }
    return new int[] {Math.max(0, text.length() - 1), text.length()};
  }

  private int lineInsertOffset(List<LineInfo> lines, int zeroBasedLine) {
    if (lines == null || lines.isEmpty()) return 0;
    int idx = Math.max(0, Math.min(zeroBasedLine, lines.size()));
    if (idx >= lines.size()) {
      return lines.get(lines.size() - 1).end;
    }
    return lines.get(idx).start;
  }

  private void replaceIssueRange(Issue issue, String replacement) {
    if (issue == null) return;
    String text = codeArea.getText();
    int start = Math.max(0, Math.min(issue.start, text.length()));
    int end = Math.max(start, Math.min(issue.end, text.length()));
    codeArea.replaceText(start, end, replacement != null ? replacement : "");
  }

  private void deleteIssueBlock(Issue issue) {
    if (issue == null) return;
    String text = codeArea.getText();
    List<LineInfo> lines = splitLines(text);
    if (lines.isEmpty()) return;

    int startLine = Math.max(0, Math.min(issue.line, lines.size() - 1));
    int endOffset = Math.max(issue.start, issue.blockEnd);
    endOffset = Math.max(0, Math.min(endOffset, text.length()));

    int endLine = startLine;
    for (LineInfo line : lines) {
      if (endOffset >= line.start && endOffset <= line.end) {
        endLine = line.index;
        break;
      }
      if (line.start <= endOffset) endLine = line.index;
    }
    endLine = Math.max(startLine, Math.min(endLine + 1, lines.size()));

    int startOffset = lines.get(startLine).start;
    int deleteTo = endLine >= lines.size()
        ? text.length()
        : lines.get(endLine).start;
    codeArea.replaceText(startOffset, deleteTo, "");
  }

  private static String firstToken(String value) {
    if (value == null) return "";
    String t = value.trim();
    if (t.isEmpty()) return "";
    int sp = t.indexOf(' ');
    return sp < 0 ? t : t.substring(0, sp);
  }

  private static int safeIndexOf(String text, String needle, int fallback) {
    if (text == null || needle == null || needle.isEmpty()) return fallback;
    int idx = text.indexOf(needle);
    return idx >= 0 ? idx : fallback;
  }

  private static List<Span> overlay(List<Span> base, int start, int end, String cls) {
    if (end <= start) return base;
    List<Span> out = new ArrayList<>();
    for (Span span : base) {
      if (span.end <= start || span.start >= end) {
        out.add(span);
        continue;
      }
      if (span.start < start) {
        out.add(new Span(span.start, start, span.styles));
      }
      int os = Math.max(span.start, start);
      int oe = Math.min(span.end, end);
      List<String> merged = new ArrayList<>(span.styles);
      if (!merged.contains(cls)) merged.add(cls);
      out.add(new Span(os, oe, merged));
      if (span.end > end) {
        out.add(new Span(end, span.end, span.styles));
      }
    }
    return out;
  }

  private static List<Span> compress(List<Span> spans) {
    if (spans.isEmpty()) return spans;
    List<Span> out = new ArrayList<>();
    Span current = spans.get(0);
    for (int i = 1; i < spans.size(); i++) {
      Span next = spans.get(i);
      if (current.end == next.start && current.styles.equals(next.styles)) {
        current = new Span(current.start, next.end, current.styles);
      } else {
        out.add(current);
        current = next;
      }
    }
    out.add(current);
    return out;
  }

  private static final class Span {
    final int start;
    final int end;
    final List<String> styles;

    Span(int start, int end, List<String> styles) {
      this.start = start;
      this.end = end;
      this.styles = styles;
    }
  }

  private static final class LineInfo {
    final int index;
    final int start;
    final int end;
    final String text;

    LineInfo(int index, int start, int end, String text) {
      this.index = index;
      this.start = start;
      this.end = end;
      this.text = text;
    }

    String trimmed() {
      return text == null ? "" : text.trim();
    }
  }

  private static final class LabelDef {
    final String name;
    final int line;
    final int tokenStart;
    final int tokenEnd;

    LabelDef(String name, int line, int tokenStart, int tokenEnd) {
      this.name = name;
      this.line = line;
      this.tokenStart = tokenStart;
      this.tokenEnd = tokenEnd;
    }
  }

  private static final class LabelRef {
    final String label;
    final int start;
    final int end;
    final int line;

    LabelRef(String label, int start, int end, int line) {
      this.label = label;
      this.start = start;
      this.end = end;
      this.line = line;
    }
  }

  private static final class Issue {
    final String kind;
    final String message;
    final int start;
    final int end;
    final int line;
    final boolean warning;
    final String label;
    final String assetPath;
    final int blockEnd;

    private Issue(String kind,
                  String message,
                  int start,
                  int end,
                  int line,
                  boolean warning,
                  String label,
                  String assetPath,
                  int blockEnd) {
      this.kind = kind;
      this.message = message;
      this.start = Math.max(0, start);
      this.end = Math.max(this.start, end);
      this.line = Math.max(0, line);
      this.warning = warning;
      this.label = label;
      this.assetPath = assetPath;
      this.blockEnd = blockEnd;
    }

    static Issue error(String kind,
                       String message,
                       int start,
                       int end,
                       int line,
                       String label,
                       String assetPath,
                       int blockEnd) {
      return new Issue(kind, message, start, end, line, false, label, assetPath, blockEnd);
    }

    static Issue warning(String kind,
                         String message,
                         int start,
                         int end,
                         int line,
                         String label,
                         String assetPath,
                         int blockEnd) {
      return new Issue(kind, message, start, end, line, true, label, assetPath, blockEnd);
    }
  }
}
