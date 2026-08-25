package com.jvn.scenerender.testkit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvn.core.scene2d.RenderFeature;
import com.jvn.core.scene2d.RenderTarget2D;
import org.junit.jupiter.api.Test;

class RecordingBlitter2DPixelAccessTest {

  @Test
  void advertisesPixelAccess() {
    RecordingBlitter2D blitter = new RecordingBlitter2D();
    assertTrue(blitter.supports(RenderFeature.PIXEL_ACCESS));
  }

  @Test
  void readPixelsArgbReturnsZeroedBufferOfCorrectSize() {
    RecordingBlitter2D blitter = new RecordingBlitter2D();
    RenderTarget2D target = blitter.createRenderTarget(3, 2, 1.0);

    int[] pixels = target.readPixelsArgb();

    assertEquals(6, pixels.length);
    assertArrayEquals(new int[6], pixels);
  }

  @Test
  void writePixelsArgbThenReadRoundTrips() {
    RecordingBlitter2D blitter = new RecordingBlitter2D();
    RenderTarget2D target = blitter.createRenderTarget(2, 2, 1.0);
    int[] source = {0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFFFFFFFF};

    target.writePixelsArgb(source);

    assertArrayEquals(source, target.readPixelsArgb());
  }

  @Test
  void writePixelsArgbRejectsWrongLength() {
    RecordingBlitter2D blitter = new RecordingBlitter2D();
    RenderTarget2D target = blitter.createRenderTarget(2, 2, 1.0);
    try {
      target.writePixelsArgb(new int[3]);
      throw new AssertionError("expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
      // expected
    }
  }
}
