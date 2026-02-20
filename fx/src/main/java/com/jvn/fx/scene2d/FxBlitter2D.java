package com.jvn.fx.scene2d;

import java.net.URL;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.jvn.core.scene2d.Blitter2D;

import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.BlendMode;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

public class FxBlitter2D implements Blitter2D {
  private final GraphicsContext gc;
  private double viewportW = 0;
  private double viewportH = 0;
  private int cacheCapacity = 128;
  private final Map<String, Image> cache = new LinkedHashMap<>(16, 0.75f, true) {
    @Override protected boolean removeEldestEntry(Map.Entry<String, Image> eldest) { return size() > cacheCapacity; }
  };
  private final Set<String> missing = new HashSet<>();

  public FxBlitter2D(GraphicsContext gc) {
    this.gc = gc;
  }

  public void setViewport(double w, double h) {
    this.viewportW = w;
    this.viewportH = h;
  }

  @Override
  public void clear(double r, double g, double b, double a) {
    gc.setFill(Color.color(clamp01(r), clamp01(g), clamp01(b), clamp01(a)));
    gc.fillRect(0, 0, viewportW, viewportH);
  }

  @Override
  public void setFill(double r, double g, double b, double a) {
    gc.setFill(Color.color(clamp01(r), clamp01(g), clamp01(b), clamp01(a)));
  }

  @Override
  public void setStroke(double r, double g, double b, double a) {
    gc.setStroke(Color.color(clamp01(r), clamp01(g), clamp01(b), clamp01(a)));
  }

  @Override
  public void setStrokeWidth(double w) { gc.setLineWidth(w); }

  @Override
  public void setGlobalAlpha(double a) { gc.setGlobalAlpha(clamp01(a)); }

  @Override
  public void setFont(String family, double size, boolean bold) {
    String fam = (family == null || family.isBlank()) ? "Arial" : family;
    gc.setFont(Font.font(fam, bold ? FontWeight.BOLD : FontWeight.NORMAL, size));
  }

  @Override
  public void push() { gc.save(); }

  @Override
  public void pop() { gc.restore(); }

  @Override
  public void translate(double x, double y) { gc.translate(x, y); }

  @Override
  public void rotateDeg(double degrees) { gc.rotate(degrees); }

  @Override
  public void scale(double sx, double sy) { gc.scale(sx, sy); }

  @Override
  public void fillRect(double x, double y, double w, double h) { gc.fillRect(x, y, w, h); }

  @Override
  public void strokeRect(double x, double y, double w, double h) { gc.strokeRect(x, y, w, h); }

  @Override
  public void fillCircle(double cx, double cy, double radius) {
    double d = radius * 2;
    gc.fillOval(cx - radius, cy - radius, d, d);
  }

  @Override
  public void strokeCircle(double cx, double cy, double radius) {
    double d = radius * 2;
    gc.strokeOval(cx - radius, cy - radius, d, d);
  }

  @Override
  public void drawLine(double x1, double y1, double x2, double y2) { gc.strokeLine(x1, y1, x2, y2); }

  @Override
  public void drawImage(String classpath, double x, double y, double w, double h) {
    if (classpath == null || classpath.isBlank()) return;
    Image img = cache.computeIfAbsent(classpath, this::loadImage);
    if (img != null) {
      gc.drawImage(img, x, y, w, h);
    } else {
      reportMissing(classpath);
      drawMissingPlaceholder(x, y, w, h);
    }
  }

  @Override
  public void drawImageRegion(String classpath, double sx, double sy, double sw, double sh,
                              double dx, double dy, double dw, double dh) {
    if (classpath == null || classpath.isBlank()) return;
    Image img = cache.computeIfAbsent(classpath, this::loadImage);
    if (img != null) {
      gc.drawImage(img, sx, sy, sw, sh, dx, dy, dw, dh);
    } else {
      reportMissing(classpath);
      drawMissingPlaceholder(dx, dy, dw, dh);
    }
  }

  @Override
  public void drawText(String text, double x, double y, double size, boolean bold) {
    if (text == null) return;
    Font cur = gc.getFont();
    String fam = (cur != null && cur.getFamily() != null && !cur.getFamily().isBlank()) ? cur.getFamily() : "Arial";
    gc.setFont(Font.font(fam, bold ? FontWeight.BOLD : FontWeight.NORMAL, size));
    gc.fillText(text, x, y);
  }

  @Override
  public double measureTextWidth(String text, double size, boolean bold) {
    if (text == null) return 0;
    Font cur = gc.getFont();
    String fam = (cur != null && cur.getFamily() != null && !cur.getFamily().isBlank()) ? cur.getFamily() : "Arial";
    javafx.scene.text.Text t = new javafx.scene.text.Text(text);
    t.setFont(Font.font(fam, bold ? FontWeight.BOLD : FontWeight.NORMAL, size));
    return t.getLayoutBounds().getWidth();
  }

  @Override
  public void setClipRect(double x, double y, double w, double h) {
    gc.beginPath();
    gc.rect(x, y, w, h);
    gc.closePath();
    gc.clip();
  }

  @Override
  public void setTextAlign(String hAlign, String vAlign) {
    if (hAlign != null) {
      switch (hAlign.toLowerCase()) {
        case "center" -> gc.setTextAlign(TextAlignment.CENTER);
        case "right" -> gc.setTextAlign(TextAlignment.RIGHT);
        default -> gc.setTextAlign(TextAlignment.LEFT);
      }
    }
    if (vAlign != null) {
      switch (vAlign.toLowerCase()) {
        case "top" -> gc.setTextBaseline(VPos.TOP);
        case "middle" -> gc.setTextBaseline(VPos.CENTER);
        case "bottom" -> gc.setTextBaseline(VPos.BOTTOM);
        default -> gc.setTextBaseline(VPos.BASELINE);
      }
    }
  }

  @Override
  public void setBlendMode(String mode) {
    if (mode == null || mode.isBlank()) {
      gc.setGlobalBlendMode(BlendMode.SRC_OVER);
      return;
    }
    switch (mode.toLowerCase()) {
      case "add": case "additive":
        gc.setGlobalBlendMode(BlendMode.ADD); break;
      case "multiply":
        gc.setGlobalBlendMode(BlendMode.MULTIPLY); break;
      case "screen":
        gc.setGlobalBlendMode(BlendMode.SCREEN); break;
      default:
        gc.setGlobalBlendMode(BlendMode.SRC_OVER);
    }
  }

  public void setCacheCapacity(int capacity) { this.cacheCapacity = Math.max(16, capacity); }
  public void evict(String path) { if (path != null) { cache.remove(path); missing.remove(path); } }
  public void clearCache() { cache.clear(); missing.clear(); }

  private Image loadImage(String path) {
    try {
      // 1. Classpath lookup
      URL u = getClass().getClassLoader().getResource(path);
      if (u != null) return new Image(u.toExternalForm());

      // 2. Absolute filesystem path
      java.io.File f = new java.io.File(path);
      if (f.isAbsolute() && f.exists()) {
        return new Image(f.toURI().toString());
      }

      // 3. Relative to project root (if set)
      if (projectRoot != null) {
        java.io.File pf = new java.io.File(projectRoot, path);
        if (pf.exists()) return new Image(pf.toURI().toString());
      }
    } catch (Exception e) { /* fall through */ }
    return null;
  }

  private java.io.File projectRoot;
  public void setProjectRoot(java.io.File root) { this.projectRoot = root; }

  private double clamp01(double v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }

  private void drawMissingPlaceholder(double x, double y, double w, double h) {
    gc.setFill(Color.color(1, 0, 1, 0.8)); // magenta box
    gc.fillRect(x, y, w, h);
    gc.setStroke(Color.color(0, 0, 0, 0.9));
    gc.setLineWidth(Math.max(1, Math.min(w, h) * 0.05));
    gc.strokeLine(x, y, x + w, y + h);
    gc.strokeLine(x + w, y, x, y + h);
  }

  private void reportMissing(String path) {
    if (path == null || path.isBlank()) return;
    if (missing.add(path)) {
      System.err.println("FX: missing image asset '" + path + "'");
    }
  }
}
