package com.jvn.core.scene2d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class TextLayout2DTest {
  private static final TextStyle2D STYLE = new TextStyle2D(
      List.of("Missing", "TestMono"), 10, false, TextColor2D.inherit());

  @Test
  void wrapsWordsAndResolvesFontFallback() {
    MonospaceBlitter blitter = new MonospaceBlitter();
    TextLayout2D layout = blitter.layoutText(TextLayoutRequest.builder()
        .text("hello world", STYLE)
        .maxWidth(60)
        .locale(Locale.ENGLISH)
        .build());

    assertEquals(2, layout.lines().size());
    assertEquals("hello", lineText(layout.lines().get(0)));
    assertEquals("world", lineText(layout.lines().get(1)));
    assertEquals("TestMono", layout.lines().get(0).runs().get(0).style().fontFamilies().get(0));
  }

  @Test
  void characterFallbackWrapsLongUnbrokenAndCjkText() {
    MonospaceBlitter blitter = new MonospaceBlitter();
    TextLayout2D layout = blitter.layoutText(TextLayoutRequest.builder()
        .text("你好世界", STYLE)
        .maxWidth(20)
        .locale(Locale.CHINESE)
        .build());

    assertEquals(2, layout.lines().size());
    assertEquals("你好", lineText(layout.lines().get(0)));
    assertEquals("世界", lineText(layout.lines().get(1)));
  }

  @Test
  void limitsLinesAddsEllipsisAndProvidesHitTesting() {
    MonospaceBlitter blitter = new MonospaceBlitter();
    TextLayout2D layout = blitter.layoutText(TextLayoutRequest.builder()
        .text("one two three", STYLE)
        .maxWidth(50)
        .maxLines(1)
        .build());

    assertTrue(layout.truncated());
    assertTrue(lineText(layout.lines().get(0)).endsWith("…"));
    assertEquals(2, layout.hitTest(24, 5).characterIndex());
    assertEquals(20.0, layout.caretX(2), 0.001);
  }

  @Test
  void retainsStyledRunsAndDetectsRtlBaseDirection() {
    MonospaceBlitter blitter = new MonospaceBlitter();
    TextStyle2D red = STYLE.withColor(TextColor2D.rgba(1, 0, 0, 1));
    TextLayout2D layout = blitter.layoutText(TextLayoutRequest.builder()
        .addSpan("مرحبا ", red)
        .addSpan("بكم", STYLE)
        .maxWidth(200)
        .direction(TextDirection.AUTO)
        .build());

    assertTrue(layout.lines().get(0).rightToLeft());
    assertEquals(2, layout.lines().get(0).runs().size());
    blitter.drawTextLayout(layout, 0, 0);
    assertEquals(2, blitter.drawn.size());
    assertFalse(blitter.drawn.get(0).isEmpty());
  }

  private static String lineText(TextLayoutLine line) {
    StringBuilder text = new StringBuilder();
    for (TextLayoutRun run : line.runs()) text.append(run.text());
    return text.toString();
  }

  private static final class MonospaceBlitter implements Blitter2D {
    final List<String> drawn = new ArrayList<>();
    @Override public void clear(double r, double g, double b, double a) {}
    @Override public void setFill(double r, double g, double b, double a) {}
    @Override public void setStroke(double r, double g, double b, double a) {}
    @Override public void setStrokeWidth(double w) {}
    @Override public void setGlobalAlpha(double a) {}
    @Override public void setFont(String family, double size, boolean bold) {}
    @Override public void push() {}
    @Override public void pop() {}
    @Override public void translate(double x, double y) {}
    @Override public void rotateDeg(double degrees) {}
    @Override public void scale(double sx, double sy) {}
    @Override public void fillRect(double x, double y, double w, double h) {}
    @Override public void strokeRect(double x, double y, double w, double h) {}
    @Override public void fillCircle(double cx, double cy, double radius) {}
    @Override public void strokeCircle(double cx, double cy, double radius) {}
    @Override public void drawLine(double x1, double y1, double x2, double y2) {}
    @Override public void drawImage(String path, double x, double y, double w, double h) {}
    @Override public void drawImageRegion(String path, double sx, double sy, double sw, double sh,
        double dx, double dy, double dw, double dh) {}
    @Override public void drawText(String text, double x, double y, double size, boolean bold) { drawn.add(text); }
    @Override public double measureTextWidth(String text, double size, boolean bold) { return text.length() * 10.0; }
    @Override public TextFontMetrics2D measureTextMetrics(String text, String family, double size, boolean bold) {
      return new TextFontMetrics2D(text.length() * 10.0, 8.0, 2.0, 0.0);
    }
    @Override public boolean isFontAvailable(String family) { return "TestMono".equals(family); }
  }
}
