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
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.event.MouseOverTextEvent;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Popup;
import javafx.stage.Stage;

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
  private final Label statusBarLabel = new Label("Ln 1, Col 1");
  private Consumer<Integer> onCaretLineChanged;

  // Code folding
  private final Set<Integer> foldedRegionStarts = new HashSet<>();
  // Bookmarks
  private final TreeSet<Integer> bookmarks = new TreeSet<>();
  // Zoom
  private double fontSizePx = 13.0;
  // Word wrap
  private boolean wordWrapEnabled = false;
  // Minimap
  private Canvas minimapCanvas;
  private VirtualizedScrollPane<CodeArea> mainScrollPane;
  // Breadcrumb
  private final Label breadcrumbLabel = new Label("");
  // Diff snapshot
  private String savedTextSnapshot = "";
  // Split editor
  private boolean splitActive = false;
  private CodeArea splitCodeArea;
  private SplitPane splitPane;

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
      Platform.runLater(this::redrawMinimap);
    });

    mainScrollPane = new VirtualizedScrollPane<>(codeArea);

    // Minimap canvas (right edge) — wider for better readability
    minimapCanvas = new Canvas(100, 100);
    minimapCanvas.setStyle("-fx-cursor: hand;");
    minimapCanvas.setOnMousePressed(this::onMinimapPress);
    minimapCanvas.setOnMouseDragged(this::onMinimapDrag);

    // Redraw minimap when the user scrolls (not just on text change)
    codeArea.estimatedScrollYProperty().addListener((obs, o, n) ->
        Platform.runLater(this::redrawMinimap));

    // Separator line between editor and minimap
    javafx.scene.layout.Region minimapSep = new javafx.scene.layout.Region();
    minimapSep.setMinWidth(1); minimapSep.setMaxWidth(1);
    minimapSep.setStyle("-fx-background-color: #1e1e1e;");

    HBox codeAndMinimap = new HBox(mainScrollPane, minimapSep, minimapCanvas);
    HBox.setHgrow(mainScrollPane, Priority.ALWAYS);
    codeAndMinimap.heightProperty().addListener((obs, o, n) -> {
      minimapCanvas.setHeight(n.doubleValue());
      Platform.runLater(this::redrawMinimap);
    });

    setCenter(codeAndMinimap);

    // Breadcrumb bar
    breadcrumbLabel.setStyle("-fx-text-fill: #8ab4f8; -fx-font-size: 11px; -fx-padding: 2 10 2 10;");
    breadcrumbLabel.setMaxWidth(Double.MAX_VALUE);

    var css = VnsCodeEditor.class.getResource("/com/jvn/editor/editor.css");
    if (css != null) {
      getStylesheets().add(css.toExternalForm());
      codeArea.getStylesheets().add(css.toExternalForm());
    }

    completer = new CodeAutoCompleter(codeArea, this::provideSuggestions);

    setupSearchBar();
    setupStatusBar();
    setupBreadcrumb();
    setupSelectionHighlighting();
    setupBracketMatching();
    setupHoverTooltips();
    setupCodeFolding();

    codeArea.setOnContextMenuRequested(e -> {
      ContextMenu menu = new ContextMenu();
      // Launch from here
      MenuItem launchItem = new MenuItem("Launch from here (F5)");
      launchItem.setOnAction(a -> launchFromHere());
      menu.getItems().add(launchItem);
      MenuItem launchStartItem = new MenuItem("Launch from start (Shift+F5)");
      launchStartItem.setOnAction(a -> { if (onLaunchFromHere != null) onLaunchFromHere.accept(null); });
      menu.getItems().add(launchStartItem);
      menu.getItems().add(new javafx.scene.control.SeparatorMenuItem());
      MenuItem goToSymbol = new MenuItem("Go to Symbol... (Ctrl+Shift+O)");
      goToSymbol.setOnAction(a -> showGoToSymbol());
      menu.getItems().add(goToSymbol);
      MenuItem toggleComment = new MenuItem("Toggle Comment (Ctrl+/)");
      toggleComment.setOnAction(a -> toggleLineComment());
      menu.getItems().add(toggleComment);
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

    setupIdeKeyBindings();
    setupAutoPairing();

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
      } else if ((e.isMetaDown() || e.isControlDown()) && e.getCode() == KeyCode.H) {
        showSearchBar();
        searchBar.showReplace(true);
        searchBar.focusReplace();
        e.consume();
      } else if (e.getCode() == KeyCode.ESCAPE && searchBarVisible) {
        hideSearchBar();
        e.consume();
      }
    });
  }

  private void setupStatusBar() {
    HBox statusBar = new HBox(12);
    statusBar.setAlignment(Pos.CENTER_LEFT);
    statusBar.setPadding(new Insets(3, 10, 3, 10));
    statusBar.setStyle("-fx-background-color: #1a1a1a; -fx-border-color: #2a2a2a; -fx-border-width: 1 0 0 0;");
    statusBarLabel.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 11px;");
    lintLabel.getStyleClass().add("lint-label");
    HBox.setHgrow(lintLabel, Priority.ALWAYS);
    lintLabel.setMaxWidth(Double.MAX_VALUE);
    breadcrumbLabel.setStyle("-fx-text-fill: #8ab4f8; -fx-font-size: 11px;");
    statusBar.getChildren().addAll(lintLabel, breadcrumbLabel, statusBarLabel);
    setBottom(statusBar);

    codeArea.caretPositionProperty().addListener((obs, oldVal, newVal) -> updateStatusBar());
    codeArea.currentParagraphProperty().addListener((obs, oldVal, newVal) -> updateStatusBar());
  }

  private void updateStatusBar() {
    int line = codeArea.getCurrentParagraph() + 1;
    int col = codeArea.getCaretColumn() + 1;
    String text = codeArea.getText();
    int labelCount = 0;
    if (text != null && !text.isEmpty()) {
      Matcher m = Pattern.compile("(?m)^\\s*@label\\s+").matcher(text);
      while (m.find()) labelCount++;
    }
    statusBarLabel.setText("Ln " + line + ", Col " + col + "  |  Labels: " + labelCount);
  }

  private void setupIdeKeyBindings() {
    addEventFilter(KeyEvent.KEY_PRESSED, e -> {
      boolean ctrl = e.isControlDown() || e.isMetaDown();
      // Ctrl+Shift+O — Go to Symbol
      if (ctrl && e.isShiftDown() && e.getCode() == KeyCode.O) {
        showGoToSymbol(); e.consume(); return;
      }
      // Ctrl+/ — Toggle comment
      if (ctrl && e.getCode() == KeyCode.SLASH) {
        toggleLineComment(); e.consume(); return;
      }
      // Ctrl+D — Duplicate line
      if (ctrl && !e.isShiftDown() && e.getCode() == KeyCode.D) {
        duplicateLine(); e.consume(); return;
      }
      // Alt+Up — Move line up
      if (e.isAltDown() && !ctrl && e.getCode() == KeyCode.UP) {
        moveLineUp(); e.consume(); return;
      }
      // Alt+Down — Move line down
      if (e.isAltDown() && !ctrl && e.getCode() == KeyCode.DOWN) {
        moveLineDown(); e.consume(); return;
      }
      // Ctrl+G — Go to Line
      if (ctrl && !e.isShiftDown() && e.getCode() == KeyCode.G) {
        showGoToLineDialog(); e.consume(); return;
      }
      // Ctrl+Shift+K — Delete Line
      if (ctrl && e.isShiftDown() && e.getCode() == KeyCode.K) {
        deleteLine(); e.consume(); return;
      }
      // Tab / Shift+Tab — Block indent/outdent (only when multi-line selection)
      if (e.getCode() == KeyCode.TAB && !ctrl && !e.isAltDown()) {
        int selS = codeArea.getSelection().getStart();
        int selE = codeArea.getSelection().getEnd();
        if (selS != selE) {
          if (e.isShiftDown()) blockOutdent(); else blockIndent();
          e.consume(); return;
        }
      }
      // Ctrl+J — Snippet Palette
      if (ctrl && !e.isShiftDown() && e.getCode() == KeyCode.J) {
        showSnippetPalette(); e.consume(); return;
      }
      // Ctrl+= — Zoom In
      if (ctrl && e.getCode() == KeyCode.EQUALS) {
        zoomIn(); e.consume(); return;
      }
      // Ctrl+- — Zoom Out
      if (ctrl && e.getCode() == KeyCode.MINUS) {
        zoomOut(); e.consume(); return;
      }
      // Ctrl+Shift+P — Command Palette
      if (ctrl && e.isShiftDown() && e.getCode() == KeyCode.P) {
        showCommandPalette(); e.consume(); return;
      }
      // Ctrl+F2 — Toggle Bookmark
      if (ctrl && e.getCode() == KeyCode.F2) {
        toggleBookmark(); e.consume(); return;
      }
      // F2 — Jump to Next Bookmark
      if (!ctrl && !e.isShiftDown() && !e.isAltDown() && e.getCode() == KeyCode.F2) {
        jumpToNextBookmark(); e.consume(); return;
      }
      // Ctrl+\ — Toggle Split Editor
      if (ctrl && e.getCode() == KeyCode.BACK_SLASH) {
        toggleSplitEditor(); e.consume(); return;
      }
      // Ctrl+Shift+W — Toggle Word Wrap
      if (ctrl && e.isShiftDown() && e.getCode() == KeyCode.W) {
        toggleWordWrap(); e.consume(); return;
      }
      // Ctrl+Shift+D — Show Diff View
      if (ctrl && e.isShiftDown() && e.getCode() == KeyCode.D) {
        showDiffView(); e.consume(); return;
      }
    });
  }

  private void setupAutoPairing() {
    codeArea.addEventFilter(KeyEvent.KEY_TYPED, e -> {
      String ch = e.getCharacter();
      if (ch == null || ch.isEmpty()) return;
      char c = ch.charAt(0);
      if (c == '[') {
        int pos = codeArea.getCaretPosition();
        String text = codeArea.getText();
        // Don't auto-pair if next char is already ]
        if (pos < text.length() && text.charAt(pos) == ']') return;
        Platform.runLater(() -> {
          int p = codeArea.getCaretPosition();
          codeArea.insertText(p, "]");
          codeArea.moveTo(p);
        });
      } else if (c == ']') {
        int pos = codeArea.getCaretPosition();
        String text = codeArea.getText();
        // Skip over existing closing bracket
        if (pos < text.length() && text.charAt(pos) == ']') {
          e.consume();
          Platform.runLater(() -> codeArea.moveTo(codeArea.getCaretPosition() + 1));
        }
      } else if (c == '"') {
        int pos = codeArea.getCaretPosition();
        String text = codeArea.getText();
        if (pos < text.length() && text.charAt(pos) == '"') {
          e.consume();
          Platform.runLater(() -> codeArea.moveTo(codeArea.getCaretPosition() + 1));
        } else {
          Platform.runLater(() -> {
            int p = codeArea.getCaretPosition();
            codeArea.insertText(p, "\"");
            codeArea.moveTo(p);
          });
        }
      }
    });
  }

  // ─── Go to Symbol ────────────────────────────────────────────────
  private void showGoToSymbol() {
    List<String> labels = extractLabels(codeArea.getText());
    if (labels.isEmpty()) return;

    Popup popup = new Popup();
    popup.setAutoHide(true);

    TextField filterField = new TextField();
    filterField.setPromptText("Go to label...");
    filterField.setPrefWidth(300);

    ListView<String> listView = new ListView<>();
    listView.setPrefWidth(300);
    listView.setPrefHeight(Math.min(labels.size() * 26 + 4, 260));
    listView.getItems().addAll(labels);

    filterField.textProperty().addListener((obs, ov, nv) -> {
      String filter = nv == null ? "" : nv.toLowerCase(Locale.ROOT);
      listView.getItems().clear();
      for (String lab : labels) {
        if (lab.toLowerCase(Locale.ROOT).contains(filter)) {
          listView.getItems().add(lab);
        }
      }
      if (!listView.getItems().isEmpty()) listView.getSelectionModel().selectFirst();
    });

    Runnable commit = () -> {
      String sel = listView.getSelectionModel().getSelectedItem();
      popup.hide();
      if (sel == null) return;
      jumpToLabel(sel);
    };

    filterField.setOnKeyPressed(e -> {
      if (e.getCode() == KeyCode.ENTER) { commit.run(); e.consume(); }
      else if (e.getCode() == KeyCode.ESCAPE) { popup.hide(); e.consume(); }
      else if (e.getCode() == KeyCode.DOWN) { listView.getSelectionModel().selectNext(); e.consume(); }
      else if (e.getCode() == KeyCode.UP) { listView.getSelectionModel().selectPrevious(); e.consume(); }
    });
    listView.setOnMouseClicked(e -> { if (e.getClickCount() == 2) commit.run(); });

    javafx.scene.layout.VBox box = new javafx.scene.layout.VBox(2, filterField, listView);
    box.setPadding(new Insets(6));
    box.setStyle("-fx-background-color: #1e1e1e; -fx-border-color: #3f3f46; -fx-border-width: 1; -fx-background-radius: 4; -fx-border-radius: 4;");
    popup.getContent().add(box);

    codeArea.getCaretBounds().ifPresent(b -> {
      double x = b.getMinX() + codeArea.getScene().getWindow().getX();
      double y = b.getMaxY() + codeArea.getScene().getWindow().getY();
      popup.show(codeArea.getScene().getWindow(), x, y);
    });
    if (!popup.isShowing()) {
      popup.show(codeArea.getScene().getWindow(),
          codeArea.getScene().getWindow().getX() + 100,
          codeArea.getScene().getWindow().getY() + 100);
    }
    filterField.requestFocus();
    if (!listView.getItems().isEmpty()) listView.getSelectionModel().selectFirst();
  }

  private void jumpToLabel(String label) {
    if (label == null) return;
    String text = codeArea.getText();
    if (text == null) return;
    Pattern p = Pattern.compile("(?m)^\\s*@label\\s+" + Pattern.quote(label) + "\\b");
    Matcher m = p.matcher(text);
    if (m.find()) {
      codeArea.moveTo(m.start());
      codeArea.requestFollowCaret();
      codeArea.requestFocus();
    }
  }

  // ─── Comment Toggle ──────────────────────────────────────────────
  private void toggleLineComment() {
    int startPara = codeArea.getCurrentParagraph();
    int endPara = startPara;

    // If there's a multi-line selection, cover all selected paragraphs
    int selStart = codeArea.getSelection().getStart();
    int selEnd = codeArea.getSelection().getEnd();
    if (selStart != selEnd) {
      startPara = codeArea.offsetToPosition(selStart, org.fxmisc.richtext.model.TwoDimensional.Bias.Forward).getMajor();
      endPara = codeArea.offsetToPosition(selEnd, org.fxmisc.richtext.model.TwoDimensional.Bias.Backward).getMajor();
    }

    // Check if all lines already have comments
    boolean allCommented = true;
    for (int i = startPara; i <= endPara; i++) {
      String line = codeArea.getParagraph(i).getText();
      if (!line.stripLeading().startsWith("#")) {
        allCommented = false;
        break;
      }
    }

    // Apply toggle from bottom to top to preserve offsets
    for (int i = endPara; i >= startPara; i--) {
      String line = codeArea.getParagraph(i).getText();
      int lineStart = codeArea.getAbsolutePosition(i, 0);
      int lineEnd = lineStart + line.length();
      if (allCommented) {
        // Remove first # (and optional trailing space)
        int hashIdx = line.indexOf('#');
        if (hashIdx >= 0) {
          int removeEnd = hashIdx + 1;
          if (removeEnd < line.length() && line.charAt(removeEnd) == ' ') removeEnd++;
          codeArea.replaceText(lineStart + hashIdx, lineStart + removeEnd, "");
        }
      } else {
        codeArea.insertText(lineStart, "# ");
      }
    }
  }

  // ─── Duplicate Line ──────────────────────────────────────────────
  private void duplicateLine() {
    int para = codeArea.getCurrentParagraph();
    String line = codeArea.getParagraph(para).getText();
    int lineEnd = codeArea.getAbsolutePosition(para, line.length());
    codeArea.insertText(lineEnd, "\n" + line);
    // Move caret to the duplicated line
    codeArea.moveTo(para + 1, codeArea.getCaretColumn());
  }

  // ─── Move Line Up/Down ───────────────────────────────────────────
  private void moveLineUp() {
    int para = codeArea.getCurrentParagraph();
    if (para <= 0) return;
    int col = codeArea.getCaretColumn();
    String currentLine = codeArea.getParagraph(para).getText();
    String aboveLine = codeArea.getParagraph(para - 1).getText();
    int currentStart = codeArea.getAbsolutePosition(para - 1, 0);
    int currentEnd = codeArea.getAbsolutePosition(para, currentLine.length());
    codeArea.replaceText(currentStart, currentEnd, currentLine + "\n" + aboveLine);
    codeArea.moveTo(para - 1, Math.min(col, currentLine.length()));
  }

  private void moveLineDown() {
    int para = codeArea.getCurrentParagraph();
    if (para >= codeArea.getParagraphs().size() - 1) return;
    int col = codeArea.getCaretColumn();
    String currentLine = codeArea.getParagraph(para).getText();
    String belowLine = codeArea.getParagraph(para + 1).getText();
    int currentStart = codeArea.getAbsolutePosition(para, 0);
    int currentEnd = codeArea.getAbsolutePosition(para + 1, belowLine.length());
    codeArea.replaceText(currentStart, currentEnd, belowLine + "\n" + currentLine);
    codeArea.moveTo(para + 1, Math.min(col, currentLine.length()));
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
      searchBar.showReplace(false);
      searchBarVisible = false;
      codeArea.requestFocus();
    }
  }

  public org.fxmisc.richtext.CodeArea getCodeArea() {
    return codeArea;
  }

  public String getText() {
    return codeArea.getText();
  }

  public void setText(String s) {
    String next = s == null ? "" : s;
    savedTextSnapshot = next;
    int prevCaret = codeArea.getCaretPosition();
    int prevAnchor = codeArea.getAnchor();
    codeArea.replaceText(next);
    int len = codeArea.getLength();
    int caret = Math.max(0, Math.min(prevCaret, len));
    int anchor = Math.max(0, Math.min(prevAnchor, len));
    codeArea.selectRange(anchor, caret);
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

  public void goToOffset(int offset) {
    String text = codeArea.getText();
    int length = text == null ? 0 : text.length();
    int target = Math.max(0, Math.min(length, offset));
    codeArea.moveTo(target);
    codeArea.requestFollowCaret();
    codeArea.requestFocus();
  }

  public void goToRange(int startOffset, int endOffset) {
    String text = codeArea.getText();
    int length = text == null ? 0 : text.length();
    int start = Math.max(0, Math.min(length, startOffset));
    int end = Math.max(0, Math.min(length, endOffset));
    if (end < start) end = start;

    if (start == end) {
      goToOffset(start);
      return;
    }

    codeArea.selectRange(start, end);
    codeArea.requestFollowCaret();
    codeArea.requestFocus();
  }

  public void setProjectRoot(File root) {
    this.projectRoot = root;
    if (completer != null) completer.setProjectRoot(root);
    applyAnalysis(getText());
  }

  private javafx.scene.Node makeLineNumberLabel(int line) {
    Label ln = new Label(String.format("%d", line + 1));
    ln.getStyleClass().add("lineno");
    if (line == highlightedIssueLine) {
      ln.getStyleClass().add(highlightedIssueWarning ? "lineno-warning" : "lineno-error");
    }

    // Bookmark indicator
    boolean isBookmarked = bookmarks.contains(line);

    // Fold indicator — check if this line starts a fold region
    boolean isFoldable = false;
    List<int[]> regions = computeFoldRegions();
    for (int[] r : regions) {
      if (r[0] == line) { isFoldable = true; break; }
    }

    if (!isFoldable && !isBookmarked) return ln;

    HBox gutter = new HBox(2);
    gutter.setAlignment(Pos.CENTER_LEFT);
    gutter.getStyleClass().add("lineno");
    if (line == highlightedIssueLine) {
      gutter.getStyleClass().add(highlightedIssueWarning ? "lineno-warning" : "lineno-error");
    }

    if (isBookmarked) {
      Label dot = new Label("\u25CF");
      dot.setStyle("-fx-text-fill: #4da3ff; -fx-font-size: 9px; -fx-padding: 0 1 0 1;");
      gutter.getChildren().add(dot);
    }

    gutter.getChildren().add(ln);

    if (isFoldable) {
      boolean folded = foldedRegionStarts.contains(line);
      Label foldBtn = new Label(folded ? "\u25B6" : "\u25BC");
      foldBtn.setStyle("-fx-text-fill: #666; -fx-font-size: 8px; -fx-cursor: hand; -fx-padding: 0 2 0 2;");
      foldBtn.setOnMouseClicked(e -> toggleFold(line));
      gutter.getChildren().add(foldBtn);
    }

    return gutter;
  }

  void insertSnippet(String s) {
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

  // ═══════════════════════════════════════════════════════════════════
  //  FEATURE: Breadcrumb Bar — shows current @label scope
  // ═══════════════════════════════════════════════════════════════════
  private void setupBreadcrumb() {
    HBox breadcrumbBar = new HBox(breadcrumbLabel);
    breadcrumbBar.setAlignment(Pos.CENTER_LEFT);
    breadcrumbBar.setStyle("-fx-background-color: #0e0e0e; -fx-border-color: #2a2a2a; -fx-border-width: 0 0 1 0;");
    breadcrumbBar.setPadding(new Insets(0));
    // We place the breadcrumb between top (search bar) and the code area.
    // We insert it as the top of a VBox that also holds the search bar.
    // Instead, we use a simpler approach: put it as part of the bottom status bar.
    // Actually, integrate into the status bar at the left side.
    codeArea.caretPositionProperty().addListener((obs, ov, nv) -> updateBreadcrumb());
    codeArea.currentParagraphProperty().addListener((obs, ov, nv) -> updateBreadcrumb());
  }

  private void updateBreadcrumb() {
    int para = codeArea.getCurrentParagraph();
    String text = codeArea.getText();
    if (text == null || text.isEmpty()) { breadcrumbLabel.setText(""); return; }
    String[] lines = text.split("\\n", -1);
    String currentLabel = null;
    for (int i = Math.min(para, lines.length - 1); i >= 0; i--) {
      Matcher m = LABEL_SCAN_PATTERN.matcher(lines[i]);
      if (m.find()) { currentLabel = m.group(1); break; }
    }
    breadcrumbLabel.setText(currentLabel != null ? "@ " + currentLabel : "");
  }

  // ═══════════════════════════════════════════════════════════════════
  //  FEATURE: Selection Occurrence Highlighting
  // ═══════════════════════════════════════════════════════════════════
  private String lastSelHighlight = "";

  private void setupSelectionHighlighting() {
    codeArea.selectionProperty().addListener((obs, ov, nv) -> {
      Platform.runLater(this::highlightSelectionOccurrences);
    });
  }

  private void highlightSelectionOccurrences() {
    String sel = codeArea.getSelectedText();
    if (sel == null || sel.length() < 2 || sel.contains("\n")) {
      if (!lastSelHighlight.isEmpty()) {
        lastSelHighlight = "";
        applyAnalysis(codeArea.getText());
      }
      return;
    }
    String word = sel.trim();
    if (word.isEmpty() || word.equals(lastSelHighlight)) return;
    lastSelHighlight = word;
    // Re-apply highlighting with selection overlays
    String text = codeArea.getText();
    if (text == null) return;
    List<Issue> currentIssues = issues;
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
    // Overlay selection highlight on all occurrences
    int idx = 0;
    while ((idx = text.indexOf(word, idx)) >= 0) {
      spans = overlay(spans, idx, idx + word.length(), "sel-highlight");
      idx += word.length();
    }
    StyleSpansBuilder<Collection<String>> out = new StyleSpansBuilder<>();
    for (Span s : compress(spans)) {
      out.add(s.styles, Math.max(0, s.end - s.start));
    }
    try { codeArea.setStyleSpans(0, out.create()); } catch (Exception ignored) {}
  }

  // ═══════════════════════════════════════════════════════════════════
  //  FEATURE: Bracket/Block Matching Highlight
  // ═══════════════════════════════════════════════════════════════════
  private int lastMatchA = -1, lastMatchB = -1;

  private void setupBracketMatching() {
    codeArea.caretPositionProperty().addListener((obs, ov, nv) -> {
      Platform.runLater(this::highlightMatchingBracket);
    });
  }

  private void highlightMatchingBracket() {
    // Clear previous bracket match styling is handled by re-analysis
    String text = codeArea.getText();
    if (text == null || text.isEmpty()) return;
    int caret = codeArea.getCaretPosition();
    int newA = -1, newB = -1;

    // Check char at caret and caret-1
    for (int offset : new int[]{0, -1}) {
      int pos = caret + offset;
      if (pos < 0 || pos >= text.length()) continue;
      char ch = text.charAt(pos);
      if (ch == '[') {
        int match = findMatchingBracket(text, pos, '[', ']', 1);
        if (match >= 0) { newA = pos; newB = match; break; }
      } else if (ch == ']') {
        int match = findMatchingBracket(text, pos, ']', '[', -1);
        if (match >= 0) { newA = pos; newB = match; break; }
      }
    }

    if (newA != lastMatchA || newB != lastMatchB) {
      lastMatchA = newA;
      lastMatchB = newB;
      // Don't re-apply full analysis — bracket highlighting is cosmetic feedback only
    }
  }

  private int findMatchingBracket(String text, int pos, char open, char close, int dir) {
    int depth = 0;
    for (int i = pos; i >= 0 && i < text.length(); i += dir) {
      char c = text.charAt(i);
      if (c == open) depth++;
      else if (c == close) depth--;
      if (depth == 0) return i;
    }
    return -1;
  }

  // ═══════════════════════════════════════════════════════════════════
  //  FEATURE: Hover Tooltips for VNS commands
  // ═══════════════════════════════════════════════════════════════════
  private static final Map<String, String> VNS_COMMAND_DOCS = new HashMap<>();
  static {
    VNS_COMMAND_DOCS.put("bg", "Set background image. Usage: [bg image_name]");
    VNS_COMMAND_DOCS.put("background", "Set background image. Usage: [background image_name]");
    VNS_COMMAND_DOCS.put("show", "Show a character sprite. Usage: [show character position]");
    VNS_COMMAND_DOCS.put("hide", "Hide a character sprite. Usage: [hide character]");
    VNS_COMMAND_DOCS.put("jump", "Jump to a label. Usage: [jump label_name]");
    VNS_COMMAND_DOCS.put("end", "End the current scenario. Usage: [end]");
    VNS_COMMAND_DOCS.put("wait", "Pause execution. Usage: [wait seconds]");
    VNS_COMMAND_DOCS.put("bgm", "Play background music. Usage: [bgm audio_file]");
    VNS_COMMAND_DOCS.put("bgm_stop", "Stop background music. Usage: [bgm_stop]");
    VNS_COMMAND_DOCS.put("bgm_crossfade", "Crossfade to new BGM. Usage: [bgm_crossfade audio duration]");
    VNS_COMMAND_DOCS.put("bgm_fadeout", "Fade out BGM. Usage: [bgm_fadeout duration]");
    VNS_COMMAND_DOCS.put("sfx", "Play sound effect. Usage: [sfx audio_file]");
    VNS_COMMAND_DOCS.put("voice", "Play voice clip. Usage: [voice audio_file]");
    VNS_COMMAND_DOCS.put("set", "Set a variable. Usage: [set var_name value]");
    VNS_COMMAND_DOCS.put("if", "Conditional branch. Usage: [if condition] ... [endif]");
    VNS_COMMAND_DOCS.put("elif", "Else-if branch. Usage: [elif condition]");
    VNS_COMMAND_DOCS.put("else", "Else branch. Usage: [else]");
    VNS_COMMAND_DOCS.put("endif", "End conditional block. Usage: [endif]");
    VNS_COMMAND_DOCS.put("choice", "Present choices. Usage: > text -> label");
    VNS_COMMAND_DOCS.put("transition", "Screen transition. Usage: [transition type]");
    VNS_COMMAND_DOCS.put("volume", "Set volume. Usage: [volume channel level]");
    VNS_COMMAND_DOCS.put("call", "Call a subroutine label. Usage: [call label]");
    VNS_COMMAND_DOCS.put("return", "Return from subroutine. Usage: [return]");
    VNS_COMMAND_DOCS.put("flag", "Set a boolean flag. Usage: [flag name]");
    VNS_COMMAND_DOCS.put("unflag", "Clear a boolean flag. Usage: [unflag name]");
    VNS_COMMAND_DOCS.put("hud", "Show/hide HUD element. Usage: [hud element state]");
    VNS_COMMAND_DOCS.put("java", "Execute Java code. Usage: [java class.method]");
    VNS_COMMAND_DOCS.put("jes", "Execute JES command. Usage: [jes command]");
    VNS_COMMAND_DOCS.put("menu", "Open a menu. Usage: [menu menu_name]");
    VNS_COMMAND_DOCS.put("@scenario", "Declare scenario metadata. Usage: @scenario name");
    VNS_COMMAND_DOCS.put("@character", "Declare a character. Usage: @character id display_name");
    VNS_COMMAND_DOCS.put("@background", "Declare a background alias. Usage: @background alias path");
    VNS_COMMAND_DOCS.put("@label", "Define a jump target. Usage: @label name");
    VNS_COMMAND_DOCS.put("@var", "Declare a variable. Usage: @var name default_value");
    VNS_COMMAND_DOCS.put("@define", "Define a constant. Usage: @define name value");
    VNS_COMMAND_DOCS.put("@include", "Include another script. Usage: @include path");
    VNS_COMMAND_DOCS.put("@charimg", "Declare character image. Usage: @charimg char_id path");
    VNS_COMMAND_DOCS.put("@charlayer", "Declare character layer. Usage: @charlayer char_id layer path");
    VNS_COMMAND_DOCS.put("@charpreset", "Declare character preset. Usage: @charpreset char_id preset layers");
  }

  private final Tooltip hoverTooltip = new Tooltip();

  private void setupHoverTooltips() {
    hoverTooltip.setStyle("-fx-background-color: #1e1e1e; -fx-text-fill: #e6e6e6; -fx-font-size: 12px; "
        + "-fx-border-color: #3f3f46; -fx-border-width: 1; -fx-padding: 6 10 6 10;");
    hoverTooltip.setWrapText(true);
    hoverTooltip.setMaxWidth(400);

    codeArea.setMouseOverTextDelay(java.time.Duration.ofMillis(400));
    codeArea.addEventHandler(MouseOverTextEvent.MOUSE_OVER_TEXT_BEGIN, e -> {
      int charIdx = e.getCharacterIndex();
      String text = codeArea.getText();
      if (text == null || charIdx < 0 || charIdx >= text.length()) return;

      // Extract word under cursor
      String word = extractWordAt(text, charIdx);
      if (word.isEmpty()) return;

      // Check command inside [ ]
      String lookupKey = word.toLowerCase(Locale.ROOT);
      // Also check for @ directives
      if (charIdx > 0 && text.charAt(charIdx - 1) == '@') lookupKey = "@" + lookupKey;
      // Also try the word itself if it starts with @
      if (word.startsWith("@")) lookupKey = word.toLowerCase(Locale.ROOT);

      String doc = VNS_COMMAND_DOCS.get(lookupKey);
      if (doc != null) {
        hoverTooltip.setText(doc);
        hoverTooltip.show(codeArea, e.getScreenPosition().getX() + 10, e.getScreenPosition().getY() + 20);
      }
    });
    codeArea.addEventHandler(MouseOverTextEvent.MOUSE_OVER_TEXT_END, e -> {
      hoverTooltip.hide();
    });
  }

  private String extractWordAt(String text, int idx) {
    int start = idx, end = idx;
    while (start > 0 && isWordChar(text.charAt(start - 1))) start--;
    while (end < text.length() && isWordChar(text.charAt(end))) end++;
    return text.substring(start, end);
  }

  private boolean isWordChar(char c) {
    return Character.isLetterOrDigit(c) || c == '_' || c == '@';
  }

  // ═══════════════════════════════════════════════════════════════════
  //  FEATURE: Code Folding — collapse sections between @labels
  // ═══════════════════════════════════════════════════════════════════
  private void setupCodeFolding() {
    // Code folding is managed through the gutter (makeLineNumberLabel).
    // Folded regions use paragraph styles to visually collapse.
  }

  private List<int[]> computeFoldRegions() {
    String text = codeArea.getText();
    if (text == null || text.isEmpty()) return List.of();
    String[] lines = text.split("\\n", -1);
    List<int[]> regions = new ArrayList<>();
    int lastLabelLine = -1;
    for (int i = 0; i < lines.length; i++) {
      if (LABEL_SCAN_PATTERN.matcher(lines[i]).find()) {
        if (lastLabelLine >= 0 && i - lastLabelLine > 1) {
          regions.add(new int[]{lastLabelLine, i - 1});
        }
        lastLabelLine = i;
      }
    }
    if (lastLabelLine >= 0 && lines.length - 1 > lastLabelLine) {
      regions.add(new int[]{lastLabelLine, lines.length - 1});
    }
    return regions;
  }

  private void toggleFold(int paragraph) {
    if (foldedRegionStarts.contains(paragraph)) {
      // Unfold
      foldedRegionStarts.remove(paragraph);
      List<int[]> regions = computeFoldRegions();
      for (int[] r : regions) {
        if (r[0] == paragraph) {
          for (int i = r[0] + 1; i <= r[1]; i++) {
            if (i < codeArea.getParagraphs().size()) {
              codeArea.setParagraphStyle(i, Collections.emptyList());
            }
          }
          break;
        }
      }
    } else {
      // Fold
      List<int[]> regions = computeFoldRegions();
      for (int[] r : regions) {
        if (r[0] == paragraph) {
          foldedRegionStarts.add(paragraph);
          for (int i = r[0] + 1; i <= r[1]; i++) {
            if (i < codeArea.getParagraphs().size()) {
              codeArea.setParagraphStyle(i, Collections.singleton("folded"));
            }
          }
          break;
        }
      }
    }
    Platform.runLater(() -> codeArea.setParagraphGraphicFactory(this::makeLineNumberLabel));
  }

  // ═══════════════════════════════════════════════════════════════════
  //  FEATURE: Go-to-Line (Ctrl+G)
  // ═══════════════════════════════════════════════════════════════════
  private void showGoToLineDialog() {
    TextInputDialog dialog = new TextInputDialog(String.valueOf(codeArea.getCurrentParagraph() + 1));
    EditorTheme.apply(dialog);
    dialog.setTitle("Go to Line");
    dialog.setHeaderText(null);
    dialog.setContentText("Line number:");
    dialog.showAndWait().ifPresent(val -> {
      try {
        int line = Integer.parseInt(val.trim());
        goToLine(line);
      } catch (NumberFormatException ignored) {}
    });
  }

  // ═══════════════════════════════════════════════════════════════════
  //  FEATURE: Delete Line (Ctrl+Shift+K)
  // ═══════════════════════════════════════════════════════════════════
  private void deleteLine() {
    int para = codeArea.getCurrentParagraph();
    int paraCount = codeArea.getParagraphs().size();
    if (paraCount <= 0) return;
    int lineStart = codeArea.getAbsolutePosition(para, 0);
    int lineEnd;
    if (para < paraCount - 1) {
      lineEnd = codeArea.getAbsolutePosition(para + 1, 0);
    } else {
      String lineText = codeArea.getParagraph(para).getText();
      lineEnd = lineStart + lineText.length();
      // Also remove the preceding newline if not the first line
      if (para > 0 && lineStart > 0) lineStart--;
    }
    codeArea.replaceText(lineStart, lineEnd, "");
  }

  // ═══════════════════════════════════════════════════════════════════
  //  FEATURE: Block Indent / Outdent (Tab / Shift+Tab)
  // ═══════════════════════════════════════════════════════════════════
  private void blockIndent() {
    int selStart = codeArea.getSelection().getStart();
    int selEnd = codeArea.getSelection().getEnd();
    int startPara = codeArea.offsetToPosition(selStart, org.fxmisc.richtext.model.TwoDimensional.Bias.Forward).getMajor();
    int endPara = codeArea.offsetToPosition(selEnd, org.fxmisc.richtext.model.TwoDimensional.Bias.Backward).getMajor();
    for (int i = endPara; i >= startPara; i--) {
      int pos = codeArea.getAbsolutePosition(i, 0);
      codeArea.insertText(pos, "  ");
    }
  }

  private void blockOutdent() {
    int selStart = codeArea.getSelection().getStart();
    int selEnd = codeArea.getSelection().getEnd();
    int startPara = codeArea.offsetToPosition(selStart, org.fxmisc.richtext.model.TwoDimensional.Bias.Forward).getMajor();
    int endPara = codeArea.offsetToPosition(selEnd, org.fxmisc.richtext.model.TwoDimensional.Bias.Backward).getMajor();
    for (int i = endPara; i >= startPara; i--) {
      String line = codeArea.getParagraph(i).getText();
      int pos = codeArea.getAbsolutePosition(i, 0);
      if (line.startsWith("  ")) {
        codeArea.replaceText(pos, pos + 2, "");
      } else if (line.startsWith(" ")) {
        codeArea.replaceText(pos, pos + 1, "");
      } else if (line.startsWith("\t")) {
        codeArea.replaceText(pos, pos + 1, "");
      }
    }
  }

  // ═══════════════════════════════════════════════════════════════════
  //  FEATURE: Snippet Palette (Ctrl+J)
  // ═══════════════════════════════════════════════════════════════════
  private static final String[][] VNS_SNIPPETS = {
      {"Dialogue", "Speaker: \"Dialogue text here.\""},
      {"Narration", "narrator \"Narration text here.\""},
      {"Choice Block", "> Option A -> label_a\n> Option B -> label_b"},
      {"Label", "@label label_name"},
      {"Jump", "[jump label_name]"},
      {"Background", "[bg background_name]"},
      {"Show Character", "[show character_id center]"},
      {"Hide Character", "[hide character_id]"},
      {"Play BGM", "[bgm music_file]"},
      {"Stop BGM", "[bgm_stop]"},
      {"Play SFX", "[sfx sound_file]"},
      {"Play Voice", "[voice voice_file]"},
      {"Wait", "[wait 1.0]"},
      {"Transition", "[transition fade]"},
      {"If Block", "[if condition]\n  # true branch\n[endif]"},
      {"If-Else Block", "[if condition]\n  # true branch\n[else]\n  # false branch\n[endif]"},
      {"Set Variable", "[set variable_name value]"},
      {"Flag", "[flag flag_name]"},
      {"Character Decl", "@character id \"Display Name\""},
      {"Background Decl", "@background alias path/to/image.png"},
      {"Include", "@include path/to/script.vns"},
      {"End", "[end]"},
  };

  private void showSnippetPalette() {
    Popup popup = new Popup();
    popup.setAutoHide(true);

    TextField filterField = new TextField();
    filterField.setPromptText("Insert snippet...");
    filterField.setPrefWidth(350);

    ListView<String> listView = new ListView<>();
    listView.setPrefWidth(350);
    listView.setPrefHeight(Math.min(VNS_SNIPPETS.length * 26 + 4, 300));
    for (String[] snip : VNS_SNIPPETS) listView.getItems().add(snip[0]);

    filterField.textProperty().addListener((obs, ov, nv) -> {
      String filter = nv == null ? "" : nv.toLowerCase(Locale.ROOT);
      listView.getItems().clear();
      for (String[] snip : VNS_SNIPPETS) {
        if (snip[0].toLowerCase(Locale.ROOT).contains(filter)) listView.getItems().add(snip[0]);
      }
      if (!listView.getItems().isEmpty()) listView.getSelectionModel().selectFirst();
    });

    Runnable commit = () -> {
      String sel = listView.getSelectionModel().getSelectedItem();
      popup.hide();
      if (sel == null) return;
      for (String[] snip : VNS_SNIPPETS) {
        if (snip[0].equals(sel)) {
          int pos = codeArea.getCaretPosition();
          codeArea.insertText(pos, snip[1]);
          codeArea.requestFocus();
          break;
        }
      }
    };

    filterField.setOnKeyPressed(e -> {
      if (e.getCode() == KeyCode.ENTER) { commit.run(); e.consume(); }
      else if (e.getCode() == KeyCode.ESCAPE) { popup.hide(); e.consume(); }
      else if (e.getCode() == KeyCode.DOWN) { listView.getSelectionModel().selectNext(); e.consume(); }
      else if (e.getCode() == KeyCode.UP) { listView.getSelectionModel().selectPrevious(); e.consume(); }
    });
    listView.setOnMouseClicked(e -> { if (e.getClickCount() == 2) commit.run(); });

    VBox box = new VBox(2, filterField, listView);
    box.setPadding(new Insets(6));
    box.setStyle("-fx-background-color: #1e1e1e; -fx-border-color: #3f3f46; -fx-border-width: 1; -fx-background-radius: 4; -fx-border-radius: 4;");
    popup.getContent().add(box);

    codeArea.getCaretBounds().ifPresent(b -> {
      double x = b.getMinX() + codeArea.getScene().getWindow().getX();
      double y = b.getMaxY() + codeArea.getScene().getWindow().getY();
      popup.show(codeArea.getScene().getWindow(), x, y);
    });
    if (!popup.isShowing()) {
      popup.show(codeArea.getScene().getWindow(),
          codeArea.getScene().getWindow().getX() + 100,
          codeArea.getScene().getWindow().getY() + 100);
    }
    filterField.requestFocus();
    if (!listView.getItems().isEmpty()) listView.getSelectionModel().selectFirst();
  }

  // ═══════════════════════════════════════════════════════════════════
  //  FEATURE: Zoom (Ctrl+= / Ctrl+-)
  // ═══════════════════════════════════════════════════════════════════
  private void zoomIn() {
    fontSizePx = Math.min(fontSizePx + 1, 30);
    applyZoom();
  }

  private void zoomOut() {
    fontSizePx = Math.max(fontSizePx - 1, 8);
    applyZoom();
  }

  private void applyZoom() {
    codeArea.setStyle("-fx-font-size: " + (int) fontSizePx + "px;");
    statusBarLabel.setText(statusBarLabel.getText().replaceFirst("\\s*\\|\\s*Zoom:.*", "")
        + "  |  Zoom: " + (int) fontSizePx + "px");
  }

  public void setFontSizePx(double fontSizePx) {
    this.fontSizePx = Math.max(8.0, Math.min(30.0, fontSizePx));
    applyZoom();
    if (splitCodeArea != null) {
      splitCodeArea.setStyle("-fx-font-size: " + (int) this.fontSizePx + "px;");
    }
  }

  // ═══════════════════════════════════════════════════════════════════
  //  FEATURE: Command Palette (Ctrl+Shift+P)
  // ═══════════════════════════════════════════════════════════════════
  private static final String[][] COMMANDS = {
      {"Find", "Open find bar (Ctrl+F)"},
      {"Find and Replace", "Open find & replace (Ctrl+H)"},
      {"Go to Symbol", "Jump to @label (Ctrl+Shift+O)"},
      {"Go to Line", "Jump to line number (Ctrl+G)"},
      {"Toggle Comment", "Comment/uncomment lines (Ctrl+/)"},
      {"Duplicate Line", "Duplicate current line (Ctrl+D)"},
      {"Delete Line", "Remove current line (Ctrl+Shift+K)"},
      {"Move Line Up", "Move line up (Alt+Up)"},
      {"Move Line Down", "Move line down (Alt+Down)"},
      {"Insert Snippet", "Open snippet palette (Ctrl+J)"},
      {"Toggle Bookmark", "Set/clear bookmark (Ctrl+F2)"},
      {"Next Bookmark", "Jump to next bookmark (F2)"},
      {"Zoom In", "Increase font size (Ctrl+=)"},
      {"Zoom Out", "Decrease font size (Ctrl+-)"},
      {"Toggle Word Wrap", "Toggle line wrapping (Ctrl+Shift+W)"},
      {"Toggle Split Editor", "Split/unsplit view (Ctrl+\\)"},
      {"Show Diff", "Compare with saved version (Ctrl+Shift+D)"},
  };

  private void showCommandPalette() {
    Popup popup = new Popup();
    popup.setAutoHide(true);

    TextField filterField = new TextField();
    filterField.setPromptText("Type a command...");
    filterField.setPrefWidth(400);

    ListView<String> listView = new ListView<>();
    listView.setPrefWidth(400);
    listView.setPrefHeight(Math.min(COMMANDS.length * 28 + 4, 350));
    for (String[] cmd : COMMANDS) listView.getItems().add(cmd[0] + "  —  " + cmd[1]);

    filterField.textProperty().addListener((obs, ov, nv) -> {
      String filter = nv == null ? "" : nv.toLowerCase(Locale.ROOT);
      listView.getItems().clear();
      for (String[] cmd : COMMANDS) {
        if (cmd[0].toLowerCase(Locale.ROOT).contains(filter) || cmd[1].toLowerCase(Locale.ROOT).contains(filter)) {
          listView.getItems().add(cmd[0] + "  —  " + cmd[1]);
        }
      }
      if (!listView.getItems().isEmpty()) listView.getSelectionModel().selectFirst();
    });

    Runnable commit = () -> {
      String sel = listView.getSelectionModel().getSelectedItem();
      popup.hide();
      if (sel == null) return;
      String cmdName = sel.contains("  —  ") ? sel.substring(0, sel.indexOf("  —  ")) : sel;
      executeCommand(cmdName);
    };

    filterField.setOnKeyPressed(e -> {
      if (e.getCode() == KeyCode.ENTER) { commit.run(); e.consume(); }
      else if (e.getCode() == KeyCode.ESCAPE) { popup.hide(); e.consume(); }
      else if (e.getCode() == KeyCode.DOWN) { listView.getSelectionModel().selectNext(); e.consume(); }
      else if (e.getCode() == KeyCode.UP) { listView.getSelectionModel().selectPrevious(); e.consume(); }
    });
    listView.setOnMouseClicked(e -> { if (e.getClickCount() == 2) commit.run(); });

    VBox box = new VBox(2, filterField, listView);
    box.setPadding(new Insets(6));
    box.setStyle("-fx-background-color: #1e1e1e; -fx-border-color: #3f3f46; -fx-border-width: 1; -fx-background-radius: 4; -fx-border-radius: 4;");
    popup.getContent().add(box);

    // Position at top-center of editor
    Platform.runLater(() -> {
      try {
        double wx = codeArea.getScene().getWindow().getX();
        double wy = codeArea.getScene().getWindow().getY();
        double ww = codeArea.getScene().getWindow().getWidth();
        popup.show(codeArea.getScene().getWindow(), wx + (ww - 400) / 2, wy + 60);
      } catch (Exception ex) {
        popup.show(codeArea.getScene().getWindow(), 100, 100);
      }
      filterField.requestFocus();
    });
  }

  private void executeCommand(String name) {
    switch (name) {
      case "Find" -> showSearchBar();
      case "Find and Replace" -> { showSearchBar(); searchBar.showReplace(true); }
      case "Go to Symbol" -> showGoToSymbol();
      case "Go to Line" -> showGoToLineDialog();
      case "Toggle Comment" -> toggleLineComment();
      case "Duplicate Line" -> duplicateLine();
      case "Delete Line" -> deleteLine();
      case "Move Line Up" -> moveLineUp();
      case "Move Line Down" -> moveLineDown();
      case "Insert Snippet" -> showSnippetPalette();
      case "Toggle Bookmark" -> toggleBookmark();
      case "Next Bookmark" -> jumpToNextBookmark();
      case "Zoom In" -> zoomIn();
      case "Zoom Out" -> zoomOut();
      case "Toggle Word Wrap" -> toggleWordWrap();
      case "Toggle Split Editor" -> toggleSplitEditor();
      case "Show Diff" -> showDiffView();
      default -> {}
    }
  }

  // ═══════════════════════════════════════════════════════════════════
  //  FEATURE: Bookmarks (Ctrl+F2 toggle, F2 next)
  // ═══════════════════════════════════════════════════════════════════
  private void toggleBookmark() {
    int para = codeArea.getCurrentParagraph();
    if (bookmarks.contains(para)) {
      bookmarks.remove(para);
    } else {
      bookmarks.add(para);
    }
    Platform.runLater(() -> codeArea.setParagraphGraphicFactory(this::makeLineNumberLabel));
  }

  private void jumpToNextBookmark() {
    if (bookmarks.isEmpty()) return;
    int current = codeArea.getCurrentParagraph();
    Integer next = bookmarks.higher(current);
    if (next == null) next = bookmarks.first();
    if (next != null) {
      codeArea.moveTo(next, 0);
      codeArea.requestFollowCaret();
      codeArea.requestFocus();
    }
  }

  // ═══════════════════════════════════════════════════════════════════
  //  FEATURE: Split Editor (Ctrl+\)
  // ═══════════════════════════════════════════════════════════════════
  private void toggleSplitEditor() {
    if (splitActive) {
      // Remove split
      splitActive = false;
      javafx.scene.Node center = getCenter();
      if (center instanceof SplitPane sp) {
        // Get the first item (code+minimap HBox) back
        if (!sp.getItems().isEmpty()) {
          javafx.scene.Node primary = sp.getItems().get(0);
          setCenter(primary);
        }
      }
      splitCodeArea = null;
      splitPane = null;
    } else {
      // Create split
      splitActive = true;
      splitCodeArea = new CodeArea();
      splitCodeArea.setEditable(false);
      splitCodeArea.replaceText(codeArea.getText());
      var splitCss = VnsCodeEditor.class.getResource("/com/jvn/editor/editor.css");
      if (splitCss != null) splitCodeArea.getStylesheets().add(splitCss.toExternalForm());
      splitCodeArea.setStyle("-fx-font-size: " + (int) fontSizePx + "px;");
      // Sync text from primary to split
      codeArea.textProperty().addListener((obs, ov, nv) -> {
        if (splitCodeArea != null && nv != null) {
          splitCodeArea.replaceText(nv);
        }
      });
      VirtualizedScrollPane<CodeArea> splitScroll = new VirtualizedScrollPane<>(splitCodeArea);

      javafx.scene.Node current = getCenter();
      splitPane = new SplitPane(current, splitScroll);
      splitPane.setOrientation(javafx.geometry.Orientation.VERTICAL);
      splitPane.setDividerPositions(0.5);
      setCenter(splitPane);
    }
  }

  // ═══════════════════════════════════════════════════════════════════
  //  FEATURE: Word Wrap Toggle (Ctrl+Shift+W)
  // ═══════════════════════════════════════════════════════════════════
  private void toggleWordWrap() {
    wordWrapEnabled = !wordWrapEnabled;
    codeArea.setWrapText(wordWrapEnabled);
  }

  // ═══════════════════════════════════════════════════════════════════
  //  FEATURE: Diff View (Ctrl+Shift+D) — compare with saved snapshot
  // ═══════════════════════════════════════════════════════════════════
  private void showDiffView() {
    String current = codeArea.getText();
    if (current == null) current = "";
    String saved = savedTextSnapshot;
    if (saved == null) saved = "";

    String[] currentLines = current.split("\\n", -1);
    String[] savedLines = saved.split("\\n", -1);

    StringBuilder diff = new StringBuilder();
    int maxLines = Math.max(currentLines.length, savedLines.length);
    for (int i = 0; i < maxLines; i++) {
      String cl = i < currentLines.length ? currentLines[i] : "";
      String sl = i < savedLines.length ? savedLines[i] : "";
      if (cl.equals(sl)) {
        diff.append("  ").append(cl).append("\n");
      } else {
        if (i < savedLines.length && !sl.isEmpty()) diff.append("- ").append(sl).append("\n");
        if (i < currentLines.length && !cl.isEmpty()) diff.append("+ ").append(cl).append("\n");
      }
    }

    Stage diffStage = new Stage();
    diffStage.setTitle("Diff — Current vs Saved");

    TextArea diffArea = new TextArea(diff.toString());
    diffArea.setEditable(false);
    diffArea.setStyle("-fx-font-family: monospace; -fx-font-size: 12px; -fx-control-inner-background: #121212; -fx-text-fill: #e6e6e6;");

    BorderPane root = new BorderPane(diffArea);
    root.setStyle("-fx-background-color: #121212;");
    Label info = new Label("Lines prefixed with '-' are from saved version, '+' are current changes.");
    info.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 11px; -fx-padding: 4 10 4 10;");
    root.setBottom(info);

    Scene scene = new Scene(root, 800, 600);
    try {
      String css = VnsCodeEditor.class.getResource("/com/jvn/editor/editor.css").toExternalForm();
      scene.getStylesheets().add(css);
    } catch (Exception ignored) {}
    diffStage.setScene(scene);
    diffStage.show();
  }

  // ═══════════════════════════════════════════════════════════════════
  //  FEATURE: Minimap — condensed code overview with viewport overlay
  // ═══════════════════════════════════════════════════════════════════

  private double minimapLineH_ = 2.0;
  private int minimapTotalLines_ = 0;

  private void redrawMinimap() {
    if (minimapCanvas == null) return;
    double w = minimapCanvas.getWidth();
    double h = minimapCanvas.getHeight();
    GraphicsContext gc = minimapCanvas.getGraphicsContext2D();

    // Background — slightly darker than editor to set it apart
    gc.setFill(Color.web("#090909"));
    gc.fillRect(0, 0, w, h);

    String text = codeArea.getText();
    if (text == null || text.isEmpty() || h <= 0) return;

    String[] lines = text.split("\\n", -1);
    minimapTotalLines_ = lines.length;
    if (minimapTotalLines_ == 0) return;

    double lineH = Math.max(1, h / minimapTotalLines_);
    if (lineH > 3) lineH = 3;
    minimapLineH_ = lineH;

    // Draw code lines — color-coded by syntax type
    for (int i = 0; i < minimapTotalLines_; i++) {
      String line = lines[i].trim();
      double y = i * lineH;
      if (y > h) break;

      Color color;
      if (line.startsWith("#"))        color = Color.web("#676e95", 0.5);
      else if (line.startsWith("@"))   color = Color.web("#c792ea", 0.65);
      else if (line.startsWith("["))   color = Color.web("#82aaff", 0.55);
      else if (line.startsWith(">"))   color = Color.web("#c3e88d", 0.55);
      else if (line.contains(":") && !line.startsWith(" ") && line.indexOf(':') < 30)
                                       color = Color.web("#ffcb6b", 0.45);
      else if (line.isEmpty())         color = Color.TRANSPARENT;
      else                             color = Color.web("#555555", 0.35);

      if (color != Color.TRANSPARENT) {
        double lineW = Math.min(w - 6, line.length() * 0.7 + 4);
        gc.setFill(color);
        gc.fillRect(3, y, lineW, Math.max(1, lineH - 0.5));
      }

      // Bookmark markers — right edge
      if (bookmarks.contains(i)) {
        gc.setFill(Color.web("#4da3ff", 0.85));
        gc.fillRect(w - 4, y, 3, Math.max(1, lineH));
      }
    }

    // ── Viewport overlay — uses actual visible paragraph range ──────
    int visibleStart, visibleEnd;
    try {
      visibleStart = codeArea.firstVisibleParToAllParIndex();
      visibleEnd = codeArea.lastVisibleParToAllParIndex();
    } catch (Exception ex) {
      // Fallback if not laid out yet
      visibleStart = Math.max(0, codeArea.getCurrentParagraph() - 10);
      visibleEnd = Math.min(minimapTotalLines_, codeArea.getCurrentParagraph() + 30);
    }
    if (visibleStart < 0) visibleStart = 0;
    if (visibleEnd >= minimapTotalLines_) visibleEnd = minimapTotalLines_ - 1;

    double vpY = visibleStart * lineH;
    double vpH = Math.max(6, (visibleEnd - visibleStart + 1) * lineH);

    // Filled viewport band
    gc.setFill(Color.web("#4da3ff", 0.08));
    gc.fillRect(0, vpY, w, vpH);

    // Top and bottom accent lines
    gc.setStroke(Color.web("#4da3ff", 0.45));
    gc.setLineWidth(1.5);
    gc.strokeLine(0, vpY, w, vpY);
    gc.strokeLine(0, vpY + vpH, w, vpY + vpH);

    // Left accent bar
    gc.setFill(Color.web("#4da3ff", 0.35));
    gc.fillRect(0, vpY, 2, vpH);
  }

  private void navigateMinimapToY(double mouseY) {
    if (minimapTotalLines_ == 0 || minimapLineH_ <= 0) return;
    int targetLine = (int)(mouseY / minimapLineH_);
    targetLine = Math.max(0, Math.min(targetLine, minimapTotalLines_ - 1));
    codeArea.showParagraphAtTop(targetLine);
    codeArea.moveTo(targetLine, 0);
    codeArea.requestFocus();
  }

  private void onMinimapPress(javafx.scene.input.MouseEvent e) {
    navigateMinimapToY(e.getY());
  }

  private void onMinimapDrag(javafx.scene.input.MouseEvent e) {
    navigateMinimapToY(e.getY());
  }
}
