package com.jvn.scenerender.vn;

import com.jvn.core.scene2d.Blitter2D;
import com.jvn.core.scene2d.RenderDiagnostics;

/**
 * Draws a visible, labeled placeholder for an image asset that failed to load, so a missing
 * layer is obvious during development instead of silently disappearing. Always logs via
 * {@link RenderDiagnostics#missingAsset} regardless of mode; only draws the visible box when
 * developer mode is enabled, so release builds stay visually undisturbed.
 *
 * <p>{@code Blitter2D}-based replacement for {@code com.jvn.fx.scene2d.MissingAssetPlaceholder},
 * which cannot be reused here since it is bound to JavaFX's {@code GraphicsContext}.
 */
public final class BlitterMissingAssetPlaceholder {
  private BlitterMissingAssetPlaceholder() {}

  public static void report(Blitter2D blitter, String path, String context,
                             double x, double y, double w, double h) {
    RenderDiagnostics.missingAsset(path, context);
    if (Boolean.getBoolean("jvn.fx.developerMode")) {
      draw(blitter, path, context, x, y, w, h);
    }
  }

  private static void draw(Blitter2D blitter, String path, String context,
                            double x, double y, double w, double h) {
    blitter.setFill(1, 0, 1, 0.8);
    blitter.fillRect(x, y, w, h);
    blitter.setStroke(0, 0, 0, 0.9);
    blitter.setStrokeWidth(Math.max(1, Math.min(w, h) * 0.05));
    blitter.drawLine(x, y, x + w, y + h);
    blitter.drawLine(x + w, y, x, y + h);

    String label = labelFor(path, context);
    double fontSize = Math.max(8, Math.min(w, h) * 0.08);
    blitter.setFill(1, 1, 1, 1);
    blitter.setFont("SansSerif", fontSize, false);
    blitter.drawText(label, x + w / 2, y + h / 2, fontSize, false);
  }

  static String labelFor(String path, String context) {
    if (context == null || context.isBlank()) return path;
    return context + " (" + path + ")";
  }
}
