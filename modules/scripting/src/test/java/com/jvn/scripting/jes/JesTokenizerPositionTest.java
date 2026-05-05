package com.jvn.scripting.jes;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for {@link JesTokenizer} position tracking and charset handling.
 */
public class JesTokenizerPositionTest {

  @Test
  public void singleCharTokensReportTheirActualColumn() {
    // The opening brace is at column 8 (1-indexed): s c e n e   "  X  "  _ {
    //                                              1 2 3 4 5 6  7 8 9 10 11 12
    List<JesToken> toks = new JesTokenizer("scene \"X\" {\n}\n").tokenize();
    JesToken lbrace = toks.stream().filter(t -> t.type == JesTokenType.LBRACE).findFirst().orElseThrow();
    assertEquals(1, lbrace.line);
    // The actual column of '{' in the source string is 11.
    assertEquals(11, lbrace.col, "LBRACE column should point at '{', not the char after it");
  }

  @Test
  public void newlinesInsideStringLiteralUpdateLineNumber() {
    String src = "scene \"hello\\nworld\" {\n}";
    // \\n is an ESCAPED newline in JES source — the literal characters '\\' and 'n'.
    // The tokenizer should produce a string token holding "hello\nworld" with no line drift.
    List<JesToken> toks = new JesTokenizer(src).tokenize();
    assertEquals(1, toks.get(1).line);
  }

  @Test
  public void rawNewlineInsideStringLiteralAdvancesLineCounter() {
    // Embedded raw newline inside the string spans 2 source lines; the '@' that follows
    // sits on line 3, and that's where the tokenizer should report the unexpected character.
    String src = "scene \"a\nb\" {\n@}";
    JesParseException ex = assertThrows(JesParseException.class, () -> new JesTokenizer(src).tokenize());
    assertEquals(3, ex.getLine(), "embedded newline in string should advance the line counter");
  }

  @Test
  public void unterminatedStringPointsAtOpeningQuote() {
    String src = "scene \"never closed\n  more text";
    JesParseException ex = assertThrows(JesParseException.class, () -> new JesTokenizer(src).tokenize());
    assertEquals(1, ex.getLine(), "unterminated string should report the opening quote's line");
    assertTrue(ex.getMessage().contains("Unterminated"));
  }

  @Test
  public void utf8InputStreamRoundTripsNonAsciiIdentifiersAndStrings() throws Exception {
    String src = "scene \"日本語\" { }\n";
    List<JesToken> toks = JesTokenizer.tokenize(
        new ByteArrayInputStream(src.getBytes(StandardCharsets.UTF_8)));
    JesToken str = toks.stream().filter(t -> t.type == JesTokenType.STRING).findFirst().orElseThrow();
    assertEquals("日本語", str.lexeme, "InputStream tokenization must use UTF-8, not platform default");
  }

  @Test
  public void escapeSequencesAreDecoded() {
    List<JesToken> toks = new JesTokenizer("scene \"a\\tb\\\\c\\\"d\" {}").tokenize();
    JesToken str = toks.stream().filter(t -> t.type == JesTokenType.STRING).findFirst().orElseThrow();
    assertEquals("a\tb\\c\"d", str.lexeme);
  }
}
