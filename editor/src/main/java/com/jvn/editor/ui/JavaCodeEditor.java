package com.jvn.editor.ui;

import java.util.Collection;
import java.util.Collections;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;

public class JavaCodeEditor extends BorderPane {
  private final CodeArea codeArea = new CodeArea();
  private Consumer<String> onTextChanged;
  private boolean suppressEvent = false;
  private EditorSearchBar searchBar;
  private boolean searchBarVisible = false;
  private boolean dslMode = false;

  private static final String[] KEYWORDS = new String[] {
    "abstract","assert","break","case","catch","class","const","continue",
    "default","do","else","enum","extends","final","finally","for","goto","if",
    "implements","import","instanceof","interface","native","new","package","private",
    "protected","public","return","static","strictfp","super","switch","synchronized","this",
    "throw","throws","transient","try","volatile","while"
  };

  private static final String KEYWORD_PATTERN = "\\b(" + String.join("|", KEYWORDS) + ")\\b";
  private static final String ANNOTATION_PATTERN = "@[A-Za-z_]\\w*";
  private static final String TYPE_PATTERN = "\\b(?:boolean|byte|char|short|int|long|float|double|void"
    + "|String|Object|List|Map|Set|Optional|File|Path|var)\\b";
  private static final String CONSTANT_PATTERN = "\\b(?:true|false|null)\\b";
  private static final String PAREN_PATTERN = "[(){}\\[\\]]";
  private static final String COLON_COMMA_PATTERN = "[;.,]";
  private static final String STRING_PATTERN = "\"([^\\\\\"]|\\\\.)*\"";
  private static final String CHAR_PATTERN = "'([^\\\\']|\\\\.)'";
  private static final String NUMBER_PATTERN = "-?\\b\\d+(?:\\.\\d+)?[fFdDlL]?\\b";
  private static final String SL_COMMENT_PATTERN = "//[^\\n]*";
  private static final String ML_COMMENT_PATTERN = "/\\*[^*]*\\*+(?:[^/*][^*]*\\*+)*/";

  private static final Pattern PATTERN = Pattern.compile(
      "(?<MLCOMMENT>"   + ML_COMMENT_PATTERN   + ")"
    + "|(?<SLCOMMENT>"  + SL_COMMENT_PATTERN   + ")"
    + "|(?<STRING>"     + STRING_PATTERN        + ")"
    + "|(?<CHARLITERAL>"+ CHAR_PATTERN          + ")"
    + "|(?<ANNOTATION>" + ANNOTATION_PATTERN    + ")"
    + "|(?<CONSTANT>"   + CONSTANT_PATTERN      + ")"
    + "|(?<TYPE>"       + TYPE_PATTERN          + ")"
    + "|(?<KEYWORD>"    + KEYWORD_PATTERN       + ")"
    + "|(?<NUMBER>"     + NUMBER_PATTERN        + ")"
    + "|(?<PAREN>"      + PAREN_PATTERN         + ")"
    + "|(?<PUNCT>"      + COLON_COMMA_PATTERN   + ")"
  );

  public JavaCodeEditor() {
    codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
    codeArea.textProperty().addListener((obs, oldText, newText) -> {
      applyHighlighting(newText);
      if (!suppressEvent && onTextChanged != null) onTextChanged.accept(newText);
    });
    applyHighlighting("");

    VirtualizedScrollPane<CodeArea> sp = new VirtualizedScrollPane<>(codeArea);
    setCenter(sp);

    var css = JavaCodeEditor.class.getResource("/com/jvn/editor/editor.css");
    if (css != null) {
      getStylesheets().add(css.toExternalForm());
      codeArea.getStylesheets().add(css.toExternalForm());
    }

    setupSearchBar();
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

  public String getText() { return codeArea.getText(); }
  public void setText(String s) { codeArea.replaceText(s == null ? "" : s); }
  public void setTextNoEvent(String s) {
    try {
      suppressEvent = true;
      setText(s);
    } finally {
      suppressEvent = false;
    }
  }
  public void setOnTextChanged(Consumer<String> c) { this.onTextChanged = c; }

  /**
   * Switch this editor to DSL properties-based syntax highlighting
   * instead of Java keyword highlighting. Call once after construction.
   */
  public void useDslHighlighting() {
    dslMode = true;
    applyHighlighting(codeArea.getText());
  }

  private void applyHighlighting(String text) {
    String safe = text == null ? "" : text;
    if (dslMode) {
      codeArea.setStyleSpans(0, DslSyntaxHighlighter.properties().computeHighlighting(safe));
    } else {
      codeArea.setStyleSpans(0, computeHighlighting(safe));
    }
  }

  private static StyleSpans<Collection<String>> computeHighlighting(String text) {
    Matcher matcher = PATTERN.matcher(text);
    int lastKwEnd = 0;
    StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
    while (matcher.find()) {
      String styleClass =
        matcher.group("MLCOMMENT")   != null ? "comment"    :
        matcher.group("SLCOMMENT")   != null ? "comment"    :
        matcher.group("STRING")      != null ? "string"     :
        matcher.group("CHARLITERAL") != null ? "string"     :
        matcher.group("ANNOTATION")  != null ? "annotation" :
        matcher.group("CONSTANT")    != null ? "constant"   :
        matcher.group("TYPE")        != null ? "type"       :
        matcher.group("KEYWORD")     != null ? "keyword"    :
        matcher.group("NUMBER")      != null ? "number"     :
        matcher.group("PAREN")       != null ? "punct"      :
        matcher.group("PUNCT")       != null ? "punct"      : null;
      assert styleClass != null;
      spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);
      spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
      lastKwEnd = matcher.end();
    }
    spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
    return spansBuilder.create();
  }
}
