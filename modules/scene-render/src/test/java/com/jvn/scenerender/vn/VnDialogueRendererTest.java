package com.jvn.scenerender.vn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvn.core.vn.text.TextParser;
import com.jvn.core.vn.text.TextSpan;
import com.jvn.scenerender.testkit.DrawCall;
import com.jvn.scenerender.testkit.RecordingBlitter2D;
import java.util.List;
import org.junit.jupiter.api.Test;

class VnDialogueRendererTest {

  @Test
  void drawStyledTextWrapsOntoMultipleLinesWhenTooWide() {
    RecordingBlitter2D blitter = new RecordingBlitter2D();
    VnDialogueRenderer renderer = new VnDialogueRenderer(blitter);
    VnFontSpec font = new VnFontSpec("SansSerif", 22.0, false);
    List<TextSpan> spans = TextParser.parse("A very long line of dialogue text that must wrap");

    renderer.drawStyledText(spans, TextParser.plainLength("A very long line of dialogue text that must wrap"),
        10, 10, 80, 0.0, font, /* default color */ 1.0, 1.0, 1.0, 1.0);

    long drawTextCalls = blitter.calls().stream().filter(c -> c.method().equals("drawText")).count();
    assertTrue(drawTextCalls > 1, "narrow maxWidth should force text onto more than one glyph-drawn position");
  }

  @Test
  void computeTextWidthDelegatesToBlitterMeasurement() {
    RecordingBlitter2D blitter = new RecordingBlitter2D();
    VnDialogueRenderer renderer = new VnDialogueRenderer(blitter);
    VnFontSpec font = new VnFontSpec("SansSerif", 20.0, false);

    double width = renderer.computeTextWidth("hello", font);

    assertEquals("hello".length() * 20.0 * 0.6, width, 1e-9);
    boolean measured = blitter.calls().stream().anyMatch(c -> c.method().equals("measureTextWidth"));
    assertTrue(measured);
  }

  @Test
  void drawsTextBoxAssetWhenAssetPathIsConfigured() {
    RecordingBlitter2D blitter = new RecordingBlitter2D();
    VnDialogueRenderer renderer = new VnDialogueRenderer(blitter);
    renderer.setTextBoxAssetPath("assets/ui/dialogue/textbox.png");

    renderer.renderStandardDialogueBackdropOnly(100, 100, 400, 120);

    boolean drewConfiguredAsset = blitter.calls().stream()
        .anyMatch(c -> c.method().equals("drawImage") && "assets/ui/dialogue/textbox.png".equals(c.args().get(0)));
    assertTrue(drewConfiguredAsset, "expected the configured text box asset path to be drawn via Blitter2D.drawImage");
  }
}
