package com.jvn.core.vn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * Covers {@code VnErrorOverlay}'s private {@code lineFromSource} helper (reached via
 * {@code jesParseError}), which splits source text on line boundaries to extract the
 * raw offending line for display. This is one of the two call sites migrated off the
 * TeaVM-unsupported {@code \R} regex shorthand onto an explicit {@code \r\n|\r|\n}
 * alternation; these tests are the before/after regression oracle for that change.
 */
class VnErrorOverlayTest {

  @Test
  void extractsRawLineFromLfOnlySource() {
    String source = "scene \"Demo\" {\n  timeline { event \"x\" { bad } }\n}\n";

    VnErrorOverlay overlay = VnErrorOverlay.jesParseError(
        "scene.jes", source, new FakeJesParseException("bad token", 2, 5));

    assertEquals("  timeline { event \"x\" { bad } }", overlay.getRawLine());
  }

  @Test
  void extractsRawLineFromCrLfSource() {
    String source = "scene \"Demo\" {\r\n  timeline { event \"x\" { bad } }\r\n}\r\n";

    VnErrorOverlay overlay = VnErrorOverlay.jesParseError(
        "scene.jes", source, new FakeJesParseException("bad token", 2, 5));

    assertEquals("  timeline { event \"x\" { bad } }", overlay.getRawLine());
  }

  @Test
  void extractsRawLineFromLoneCrSource() {
    String source = "scene \"Demo\" {\r  timeline { event \"x\" { bad } }\r}\r";

    VnErrorOverlay overlay = VnErrorOverlay.jesParseError(
        "scene.jes", source, new FakeJesParseException("bad token", 2, 5));

    assertEquals("  timeline { event \"x\" { bad } }", overlay.getRawLine());
  }

  @Test
  void extractsRawLineFromMixedLineEndingSource() {
    String source = "scene \"Demo\" {\r\n  timeline { event \"x\" { bad } }\n}\r";

    VnErrorOverlay overlay = VnErrorOverlay.jesParseError(
        "scene.jes", source, new FakeJesParseException("bad token", 2, 5));

    assertEquals("  timeline { event \"x\" { bad } }", overlay.getRawLine());
  }

  @Test
  void returnsNullRawLineWhenLineNumberIsOutOfRange() {
    String source = "scene \"Demo\" {\n}\n";

    VnErrorOverlay overlay = VnErrorOverlay.jesParseError(
        "scene.jes", source, new FakeJesParseException("bad token", 99, 1));

    assertNull(overlay.getRawLine());
  }

  public static final class FakeJesParseException extends RuntimeException {
    private final int line;
    private final int col;

    FakeJesParseException(String message, int line, int col) {
      super(message);
      this.line = line;
      this.col = col;
    }

    public int getLine() { return line; }
    public int getCol() { return col; }
  }
}
