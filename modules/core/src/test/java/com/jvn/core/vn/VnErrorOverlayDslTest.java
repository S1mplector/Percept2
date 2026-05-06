package com.jvn.core.vn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VnErrorOverlayDslTest {

  @Test
  void genericDslParseErrorCarriesDslTitleLocationAndRawLine() {
    VnErrorOverlay overlay = VnErrorOverlay.dslParseError(
        "Puppeteer JES",
        "anim.timeline",
        3,
        11,
        "Expected ':' but found 'x'",
        "move \"hero\" { x 10 }",
        new IllegalArgumentException("Expected ':' but found 'x'"));

    assertEquals(VnErrorOverlay.ErrorType.DSL_PARSE_ERROR, overlay.getType());
    assertEquals("Puppeteer JES Parse Error", overlay.getTitle());
    assertEquals("anim.timeline", overlay.getSourceName());
    assertEquals(3, overlay.getLineNumber());
    assertEquals("move \"hero\" { x 10 }", overlay.getRawLine());
    assertTrue(overlay.getMessage().contains("column 11"));
    assertTrue(overlay.getLikelyCause().contains("syntax"));
  }

  @Test
  void jesParseErrorReadsLineAndColumnFromThrowableShape() {
    VnErrorOverlay overlay = VnErrorOverlay.jesParseError(
        "scene.jes",
        """
        scene "Demo" {
          timeline { event "x" { bad } }
        }
        """,
        new FakeJesParseException("Expected ':' but found '}'", 2, 27));

    assertEquals("JES Parse Error", overlay.getTitle());
    assertEquals(2, overlay.getLineNumber());
    assertEquals("  timeline { event \"x\" { bad } }", overlay.getRawLine());
    assertTrue(overlay.getMessage().contains("column 27"));
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
