package com.jvn.fx.scene2d;

import com.jvn.core.scene2d.RenderDiagnostics;
import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

/**
 * Draws a visible, labeled placeholder for an image asset that failed to load, so a missing
 * layer is obvious during development instead of silently disappearing. Always logs via
 * {@link RenderDiagnostics#missingAsset} regardless of mode; only draws the visible box when
 * developer mode is enabled, so release builds stay visually undisturbed.
 */
public final class MissingAssetPlaceholder {
  private static final boolean DEVELOPER_MODE = Boolean.getBoolean("jvn.fx.developerMode");

  private MissingAssetPlaceholder() {}

  /** Reports the missing asset and, in developer mode, draws a labeled placeholder box for it. */
  public static void report(GraphicsContext gc, String path, String context,
                             double x, double y, double w, double h) {
    RenderDiagnostics.missingAsset(path, context);
    if (shouldDraw(DEVELOPER_MODE)) {
      draw(gc, path, context, x, y, w, h);
    }
  }

  static boolean shouldDraw(boolean developerMode) {
    return developerMode;
  }

  static String labelFor(String path, String context) {
    if (context == null || context.isBlank()) return path;
    return context + " (" + path + ")";
  }

  private static void draw(GraphicsContext gc, String path, String context,
                            double x, double y, double w, double h) {
    gc.setFill(Color.color(1, 0, 1, 0.8));
    gc.fillRect(x, y, w, h);
    gc.setStroke(Color.color(0, 0, 0, 0.9));
    gc.setLineWidth(Math.max(1, Math.min(w, h) * 0.05));
    gc.strokeLine(x, y, x + w, y + h);
    gc.strokeLine(x + w, y, x, y + h);

    String label = labelFor(path, context);
    gc.setFill(Color.WHITE);
    gc.setTextAlign(TextAlignment.CENTER);
    gc.setTextBaseline(VPos.CENTER);
    gc.setFont(Font.font(Math.max(8, Math.min(w, h) * 0.08)));
    gc.fillText(label, x + w / 2, y + h / 2, w);
  }
}
