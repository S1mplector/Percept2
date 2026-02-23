package com.jvn.editor.ui;

import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

/**
 * Reusable syntax highlighter for JVN properties-based DSL files
 * (.menu, .layout, .style, dialogue.layout, .registry, .theme, etc.).
 *
 * Recognises:
 * <ul>
 *   <li>{@code # comment} lines → <b>comment</b></li>
 *   <li>Property keys (everything before {@code =}) → <b>prop-key</b></li>
 *   <li>Known DSL directives/keywords → <b>keyword</b></li>
 *   <li>Quoted strings → <b>string</b></li>
 *   <li>Numeric literals → <b>number</b></li>
 *   <li>Punctuation ({@code = , . :}) → <b>punct</b></li>
 * </ul>
 *
 * Usage:
 * <pre>{@code
 *   DslSyntaxHighlighter highlighter = DslSyntaxHighlighter.properties();
 *   highlighter.install(codeArea);
 * }</pre>
 */
public final class DslSyntaxHighlighter {

  private final Pattern pattern;

  private DslSyntaxHighlighter(Pattern pattern) {
    this.pattern = pattern;
  }

  /** Install this highlighter on a {@link CodeArea} so it re-highlights on every text change. */
  public void install(CodeArea codeArea) {
    codeArea.textProperty().addListener((obs, oldText, newText) -> {
      codeArea.setStyleSpans(0, computeHighlighting(newText == null ? "" : newText));
    });
    codeArea.setStyleSpans(0, computeHighlighting(codeArea.getText() == null ? "" : codeArea.getText()));
  }

  /** Compute style spans for the given text. */
  public StyleSpans<Collection<String>> computeHighlighting(String text) {
    Matcher matcher = pattern.matcher(text);
    int lastEnd = 0;
    StyleSpansBuilder<Collection<String>> builder = new StyleSpansBuilder<>();
    while (matcher.find()) {
      String styleClass =
          matcher.group("COMMENT")   != null ? "comment"  :
          matcher.group("STRING")    != null ? "string"   :
          matcher.group("NUMBER")    != null ? "number"   :
          matcher.group("KEYWORD")   != null ? "keyword"  :
          matcher.group("PROPKEY")   != null ? "prop-key" :
          matcher.group("PUNCT")     != null ? "punct"    : null;
      if (styleClass == null) continue;
      builder.add(Collections.emptyList(), matcher.start() - lastEnd);
      builder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
      lastEnd = matcher.end();
    }
    builder.add(Collections.emptyList(), text.length() - lastEnd);
    return builder.create();
  }

  // ── Factory: Properties DSL (menu/layout/style/dialogue/registry) ──

  private static final String[] PROP_KEYWORDS = {
      // Menu screen keys
      "layout", "layoutId", "defaultItemStyle", "wrapSelection", "title", "hints",
      // Item sub-keys
      "label", "action", "target", "enabled", "style", "iconPath",
      // Bounds
      "boundsX", "boundsY", "boundsWidth", "boundsHeight",
      // Button assets
      "bgAsset", "bgSelectedAsset", "bgDisabledAsset",
      "buttonAsset", "buttonSelectedAsset", "buttonDisabledAsset",
      // Slot preview
      "slotPreviewEnabled", "slotPreviewPlaceholderAsset", "slotPreviewFrameAsset",
      "slotPreviewX", "slotPreviewY", "slotPreviewWidth", "slotPreviewHeight",
      // Layout spec keys
      "listYStart", "lineHeight", "listWidthFactor", "textAlign", "hintsBottomMargin", "titleY",
      // Style spec keys
      "itemColor", "itemSelectedColor", "itemDisabledColor",
      "prefixColor", "prefixSelectedColor", "prefixDisabledColor",
      "fontFamily", "fontWeight", "fontSize",
      "buttonTextPaddingX", "buttonTextPaddingY",
      // Dialogue layout keys
      "textBoxAsset", "textBoxX", "textBoxY", "textBoxWidth", "textBoxHeight", "textBoxPadding",
      "nameBoxOffsetX", "nameBoxOffsetY", "nameBoxWidth", "nameBoxHeight",
      "dialoguePaddingLeft", "dialoguePaddingRight", "dialoguePaddingTop", "dialoguePaddingBottom",
      "choiceCenterX", "choiceYStart", "choiceWidthFactor", "choiceHeight", "choiceGap", "choiceTextPadding",
      // Button layout keys
      "menuId", "resolution", "menuType", "button\\.ids",
      "hoverAsset", "disabledAsset", "asset", "tag",
      // Registry keys
      "defaultMenu", "defaultScreen", "menus", "layouts", "styles",
      // Action types
      "new_game", "load_menu", "save_menu", "settings_menu", "quit",
      "back", "main_menu", "open_menu", "run_script", "noop",
      // General
      "true", "false", "default", "left", "center", "right"
  };

  private static final String PROP_KEYWORD_PAT = "\\b(" + String.join("|", PROP_KEYWORDS) + ")\\b";
  private static final String COMMENT_PAT      = "(?m)#[^\\n]*";
  private static final String STRING_PAT       = "\"([^\\\\\"]|\\\\.)*\"";
  private static final String NUMBER_PAT       = "(?<![\\w.])\\d+(?:\\.\\d+)?(?![\\w.])";
  private static final String PROPKEY_PAT      = "(?m)^[ \\t]*[\\w](?:[\\w.\\-])*(?=\\s*=)";
  private static final String PUNCT_PAT        = "[=,.:;]";

  private static final Pattern PROPERTIES_PATTERN = Pattern.compile(
      "(?<COMMENT>"  + COMMENT_PAT     + ")"
    + "|(?<STRING>"  + STRING_PAT      + ")"
    + "|(?<PROPKEY>" + PROPKEY_PAT     + ")"
    + "|(?<NUMBER>"  + NUMBER_PAT      + ")"
    + "|(?<KEYWORD>" + PROP_KEYWORD_PAT + ")"
    + "|(?<PUNCT>"   + PUNCT_PAT       + ")"
  );

  private static final DslSyntaxHighlighter PROPERTIES_INSTANCE = new DslSyntaxHighlighter(PROPERTIES_PATTERN);

  /**
   * Returns a highlighter for JVN properties-based DSL files.
   * Thread-safe, stateless singleton — safe to share across editors.
   */
  public static DslSyntaxHighlighter properties() {
    return PROPERTIES_INSTANCE;
  }
}
