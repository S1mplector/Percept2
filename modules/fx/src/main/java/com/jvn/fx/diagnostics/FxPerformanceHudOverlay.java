package com.jvn.fx.diagnostics;

import com.jvn.core.diagnostics.PerformanceHud;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Draws the F3 performance HUD over a JavaFX {@link GraphicsContext}.
 *
 * <p>Toggle visibility with {@link #setVisible(boolean)}.
 * Call {@link #render(GraphicsContext, double, double)} each frame — it no-ops when not visible.</p>
 */
public final class FxPerformanceHudOverlay {

  private static final Color BG = Color.rgb(0, 0, 0, 0.62);
  private static final Color FG = Color.rgb(220, 255, 220);
  private static final Color WARN = Color.rgb(255, 220, 80);
  private static final Font HUD_FONT = Font.font("Monospaced", FontWeight.NORMAL, 13);

  private static final double PAD = 8.0;
  private static final double LINE_H = 16.0;

  private final PerformanceHud hud;
  private boolean visible = false;

  public FxPerformanceHudOverlay(PerformanceHud hud) {
    this.hud = hud;
  }

  public void setVisible(boolean visible) { this.visible = visible; }
  public boolean isVisible() { return visible; }
  public void toggle() { this.visible = !this.visible; }

  /**
   * Draw the overlay onto {@code gc}. Safe to call every frame — skips rendering when not visible.
   */
  public void render(GraphicsContext gc, double width, double height) {
    if (!visible) return;

    double fps = hud.getFps();
    long heapMb = hud.getHeapMb();
    double hitRate = hud.getImageCacheHitRate();
    int timelines = hud.getActiveTimelines();

    String[] lines = {
        String.format("FPS: %.1f", fps),
        String.format("Heap: %d MB", heapMb),
        String.format("Cache hit: %s", Double.isNaN(hitRate) ? "n/a" : String.format("%.0f%%", hitRate * 100)),
        String.format("Timelines: %d", timelines)
    };

    double boxW = 160.0;
    double boxH = PAD * 2 + LINE_H * lines.length;
    double x = width - boxW - 8;
    double y = 8;

    gc.save();
    gc.setFill(BG);
    gc.fillRoundRect(x, y, boxW, boxH, 6, 6);
    gc.setFont(HUD_FONT);

    double ty = y + PAD + LINE_H - 3;
    for (String line : lines) {
      gc.setFill(fps < 30 && line.startsWith("FPS") ? WARN : FG);
      gc.fillText(line, x + PAD, ty);
      ty += LINE_H;
    }
    gc.restore();
  }
}
