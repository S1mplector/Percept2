package com.jvn.scenerender.testkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class RecordingBlitter2DTest {

  @Test
  void recordsFillRectCallWithArguments() {
    RecordingBlitter2D blitter = new RecordingBlitter2D();

    blitter.setFill(1.0, 0.0, 0.0, 1.0);
    blitter.fillRect(10.0, 20.0, 100.0, 50.0);

    assertEquals(2, blitter.calls().size());
    DrawCall fillRectCall = blitter.calls().get(1);
    assertEquals("fillRect", fillRectCall.method());
    assertEquals(List.of(10.0, 20.0, 100.0, 50.0), fillRectCall.args());
  }

  @Test
  void drawTextRecordsStringAndFontState() {
    RecordingBlitter2D blitter = new RecordingBlitter2D();

    blitter.setFont("SansSerif", 18.0, true);
    blitter.drawText("Resume", 40.0, 60.0, 18.0, true);

    assertTrue(blitter.calls().stream()
        .anyMatch(c -> c.method().equals("drawText") && c.args().get(0).equals("Resume")));
  }
}
