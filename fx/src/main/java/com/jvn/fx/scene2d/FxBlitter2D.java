package com.jvn.fx.scene2d;

import com.jvn.core.scene2d.Blitter2D;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class FxBlitter2D implements Blitter2D {
  private final GraphicsContext gc;
  private double viewportW = 0;
  private double viewportH = 0;
  private final Map<String, Image> cache = new HashMap<>();

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
    if (img != null) gc.drawImage(img, x, y, w, h);
  }

  @Override
  public void drawImageRegion(String classpath, double sx, double sy, double sw, double sh,
                              double dx, double dy, double dw, double dh) {
    if (classpath == null || classpath.isBlank()) return;
    Image img = cache.computeIfAbsent(classpath, this::loadImage);
    if (img != null) {
      gc.drawImage(img, sx, sy, sw, sh, dx, dy, dw, dh);
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

  private Image loadImage(String path) {
    try {
      URL u = getClass().getClassLoader().getResource(path);
      if (u == null) return null;
      return new Image(u.toExternalForm());
    } catch (Exception e) { return null; }
  }

  private double clamp01(double v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }
}
