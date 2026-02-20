package com.jvn.editor.ui;

import javafx.scene.layout.BorderPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.function.Consumer;

public class JavaCodeEditor extends BorderPane {
  private final CodeArea codeArea = new CodeArea();
  private Consumer<String> onTextChanged;
  private boolean suppressEvent = false;

  private static final String[] KEYWORDS = new String[] {
    "abstract","assert","boolean","break","byte","case","catch","char","class","const","continue",
    "default","do","double","else","enum","extends","final","finally","float","for","goto","if",
    "implements","import","instanceof","int","interface","long","native","new","package","private",
    "protected","public","return","short","static","strictfp","super","switch","synchronized","this",
    "throw","throws","transient","try","void","volatile","while"
  };

  private static final String KEYWORD_PATTERN = "\\b(" + String.join("|", KEYWORDS) + ")\\b";
  private static final String PAREN_PATTERN = "[(){}]";
  private static final String COLON_COMMA_PATTERN = "[;.,]";
  private static final String STRING_PATTERN = "\"([^\\\"]|\\\\.)*\"";
  private static final String NUMBER_PATTERN = "-?\\b\\d+(?:\\.\\d+)?\\b";
  private static final String SL_COMMENT_PATTERN = "//[^\\n]*";
  private static final String ML_COMMENT_PATTERN = "/\\*[^*]*\\*+(?:[^/*][^*]*\\*+)*/";

  private static final Pattern PATTERN = Pattern.compile(
      "(?<MLCOMMENT>" + ML_COMMENT_PATTERN + ")"
    + "|(?<SLCOMMENT>" + SL_COMMENT_PATTERN + ")"
    + "|(?<STRING>" + STRING_PATTERN + ")"
    + "|(?<NUMBER>" + NUMBER_PATTERN + ")"
    + "|(?<KEYWORD>" + KEYWORD_PATTERN + ")"
    + "|(?<PAREN>" + PAREN_PATTERN + ")"
    + "|(?<PUNCT>" + COLON_COMMA_PATTERN + ")"
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

  private void applyHighlighting(String text) {
    codeArea.setStyleSpans(0, computeHighlighting(text == null ? "" : text));
  }

  private static StyleSpans<Collection<String>> computeHighlighting(String text) {
    Matcher matcher = PATTERN.matcher(text);
    int lastKwEnd = 0;
    StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
    while (matcher.find()) {
      String styleClass =
        matcher.group("MLCOMMENT") != null ? "comment" :
        matcher.group("SLCOMMENT") != null ? "comment" :
        matcher.group("STRING") != null ? "string" :
        matcher.group("NUMBER") != null ? "number" :
        matcher.group("KEYWORD") != null ? "keyword" :
        matcher.group("PAREN") != null ? "punct" :
        matcher.group("PUNCT") != null ? "punct" : null;
      assert styleClass != null;
      spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);
      spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
      lastKwEnd = matcher.end();
    }
    spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
    return spansBuilder.create();
  }
}
