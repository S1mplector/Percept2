package com.jvn.fx.scene2d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvn.core.scene2d.RenderFeature;
import com.jvn.core.scene2d.RenderTarget2D;
import com.jvn.fx.testkit.FxToolkit;
import com.jvn.fx.testkit.FxToolkitExtension;
import javafx.scene.canvas.Canvas;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FxToolkitExtension.class)
class FxRenderTarget2DPixelAccessTest {

  @Test
  void advertisesPixelAccess() {
    assertTrue(FxBlitter2D.CAPABILITIES.supports(RenderFeature.PIXEL_ACCESS));
  }

  @Test
  void readPixelsArgbReturnsDrawnColor() throws Exception {
    FxBlitter2D blitter = FxToolkit.runFx(() -> new FxBlitter2D(new Canvas(4, 4).getGraphicsContext2D()));
    RenderTarget2D target = FxToolkit.runFx(() -> blitter.createRenderTarget(4, 4, 1.0));
    FxToolkit.runFx(() -> {
      target.getBlitter().setFill(1.0, 0.0, 0.0, 1.0);
      target.getBlitter().fillRect(0, 0, 4, 4);
      return null;
    });

    int[] argb = FxToolkit.runFx(target::readPixelsArgb);
    assertEquals(16, argb.length);
    int expected = (0xFF << 24) | (0xFF << 16);
    assertEquals(expected, argb[0]);
  }

  @Test
  void writePixelsArgbThenReadRoundTrips() throws Exception {
    FxBlitter2D blitter = FxToolkit.runFx(() -> new FxBlitter2D(new Canvas(2, 2).getGraphicsContext2D()));
    RenderTarget2D target = FxToolkit.runFx(() -> blitter.createRenderTarget(2, 2, 1.0));
    int[] source = new int[] {
        0xFFFF0000, 0xFF00FF00,
        0xFF0000FF, 0xFFFFFFFF
    };
    FxToolkit.runFx(() -> { target.writePixelsArgb(source); return null; });

    int[] roundTripped = FxToolkit.runFx(target::readPixelsArgb);
    assertEquals(source.length, roundTripped.length);
    for (int i = 0; i < source.length; i++) {
      assertEquals(source[i], roundTripped[i], "pixel " + i + " mismatch");
    }
  }
}
