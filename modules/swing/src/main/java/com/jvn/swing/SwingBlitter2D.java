package com.jvn.swing;

import com.jvn.core.scene2d.Blitter2D;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Arc2D;
import java.awt.LinearGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.net.URL;
import javax.imageio.ImageIO;

public class SwingBlitter2D implements Blitter2D {
  private Graphics2D g2;
  private Paint fillPaint = new Color(255,255,255,255);
  private Color stroke = new Color(255,255,255,255);
  private float strokeWidth = 1f;
  private float alpha = 1f;
  private BasicStroke basicStroke = new BasicStroke(strokeWidth, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER);
  private final Deque<AffineTransform> transforms = new ArrayDeque<>();
  private final Deque<Composite> composites = new ArrayDeque<>();
  private final Deque<Shape> clips = new ArrayDeque<>();
  private Path2D currentPath = null;
  private String hAlign = "left";
  private String vAlign = "baseline";
  private int cacheCapacity = 128;
  private final Map<String, BufferedImage> imageCache = new LinkedHashMap<>(16, 0.75f, true) {
    @Override protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> eldest) { return size() > cacheCapacity; }
  };
  private final Set<String> missing = new HashSet<>();

  public SwingBlitter2D(Graphics2D g2) {
    this.g2 = (Graphics2D) g2.create();
    this.g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    this.g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
  }

  public void dispose() {
    if (g2 != null) g2.dispose();
  }

  public void setCacheCapacity(int capacity) { this.cacheCapacity = Math.max(16, capacity); }
  public void evict(String path) { if (path != null) { imageCache.remove(path); missing.remove(path); } }
  public void clearCache() { imageCache.clear(); missing.clear(); }

  @Override
  public void clear(double r, double g, double b, double a) {
    Composite old = g2.getComposite();
    g2.setComposite(AlphaComposite.Src);
    g2.setColor(new Color((float) r, (float) g, (float) b, (float) a));
    java.awt.Rectangle clip = g2.getClipBounds();
    if (clip != null) g2.fillRect(clip.x, clip.y, clip.width, clip.height);
    else {
      g2.fillRect(0, 0, Integer.MAX_VALUE / 4, Integer.MAX_VALUE / 4);
    }
    g2.setComposite(old);
  }

  @Override
  public void setFill(double r, double g, double b, double a) {
    fillPaint = new Color((float) r, (float) g, (float) b, (float) (a * alpha));
  }

  @Override
  public void setStroke(double r, double g, double b, double a) {
    stroke = new Color((float) r, (float) g, (float) b, (float) (a * alpha));
  }

  @Override
  public void setStrokeWidth(double w) {
    strokeWidth = (float) w;
    basicStroke = new BasicStroke(strokeWidth, basicStroke.getEndCap(), basicStroke.getLineJoin(), basicStroke.getMiterLimit(), basicStroke.getDashArray(), basicStroke.getDashPhase());
  }

  @Override
  public void setGlobalAlpha(double a) {
    alpha = (float) Math.max(0, Math.min(1, a));
    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
  }

  @Override
  public void setFont(String family, double size, boolean bold) {
    int style = bold ? Font.BOLD : Font.PLAIN;
    g2.setFont(new Font(family == null ? "SansSerif" : family, style, (int) Math.round(size)));
  }

  @Override
  public void push() {
    transforms.push(g2.getTransform());
    composites.push(g2.getComposite());
    clips.push(g2.getClip());
  }

  @Override
  public void pop() {
    if (!transforms.isEmpty()) g2.setTransform(transforms.pop());
    if (!composites.isEmpty()) g2.setComposite(composites.pop());
    if (!clips.isEmpty()) g2.setClip(clips.pop());
  }

  @Override
  public void translate(double x, double y) { g2.translate(x, y); }

  @Override
  public void rotateDeg(double degrees) { g2.rotate(Math.toRadians(degrees)); }

  @Override
  public void scale(double sx, double sy) { g2.scale(sx, sy); }

  @Override
  public void fillRect(double x, double y, double w, double h) {
    g2.setPaint(fillPaint);
    g2.fill(new java.awt.geom.Rectangle2D.Double(x, y, w, h));
  }

  @Override
  public void strokeRect(double x, double y, double w, double h) {
    g2.setColor(stroke);
    g2.setStroke(basicStroke);
    g2.draw(new java.awt.geom.Rectangle2D.Double(x, y, w, h));
  }

  @Override
  public void fillCircle(double cx, double cy, double radius) {
    g2.setPaint(fillPaint);
    double d = radius * 2;
    g2.fill(new Ellipse2D.Double(cx - radius, cy - radius, d, d));
  }

  @Override
  public void strokeCircle(double cx, double cy, double radius) {
    g2.setColor(stroke);
    g2.setStroke(basicStroke);
    double d = radius * 2;
    g2.draw(new Ellipse2D.Double(cx - radius, cy - radius, d, d));
  }

  @Override
  public void drawLine(double x1, double y1, double x2, double y2) {
    g2.setColor(stroke);
    g2.setStroke(basicStroke);
    g2.draw(new java.awt.geom.Line2D.Double(x1, y1, x2, y2));
  }

  @Override
  public void drawImage(String classpath, double x, double y, double w, double h) {
    BufferedImage img = loadImage(classpath);
    if (img == null) {
      reportMissing(classpath);
      drawMissingPlaceholder(x, y, w, h);
      return;
    }
    g2.drawImage(
      img,
      (int) Math.round(x),
      (int) Math.round(y),
      (int) Math.round(w),
      (int) Math.round(h),
      null
    );
  }

  @Override
  public void drawImageRegion(String classpath, double sx, double sy, double sw, double sh, double dx, double dy, double dw, double dh) {
    BufferedImage img = loadImage(classpath);
    if (img == null) {
      reportMissing(classpath);
      drawMissingPlaceholder(dx, dy, dw, dh);
      return;
    }
    if (sw <= 0 || sh <= 0 || dw == 0 || dh == 0) return;

    int x1 = (int) Math.round(sx);
    int y1 = (int) Math.round(sy);
    int x2 = x1 + (int) Math.round(sw);
    int y2 = y1 + (int) Math.round(sh);

    x1 = Math.max(0, x1); y1 = Math.max(0, y1);
    x2 = Math.min(img.getWidth(), x2); y2 = Math.min(img.getHeight(), y2);
    if (x2 <= x1 || y2 <= y1) return;

    BufferedImage sub = img.getSubimage(x1, y1, x2 - x1, y2 - y1);
    g2.drawImage(
      sub,
      (int) Math.round(dx),
      (int) Math.round(dy),
      (int) Math.round(dw),
      (int) Math.round(dh),
      null
    );
  }

  @Override
  public void drawText(String text, double x, double y, double size, boolean bold) {
    if (text == null) return;
    setFont(g2.getFont().getFamily(), size, bold);
    g2.setPaint(fillPaint);
    FontMetrics fm = g2.getFontMetrics();
    double tx = x;
    double tw = fm.stringWidth(text);
    if ("center".equalsIgnoreCase(hAlign)) tx = x - tw / 2.0;
    else if ("right".equalsIgnoreCase(hAlign)) tx = x - tw;
    double ty = y;
    if ("top".equalsIgnoreCase(vAlign)) ty = y + fm.getAscent();
    else if ("middle".equalsIgnoreCase(vAlign)) ty = y + (fm.getAscent() - fm.getDescent()) / 2.0;
    else if ("bottom".equalsIgnoreCase(vAlign)) ty = y - fm.getDescent();
    // baseline: keep y
    g2.drawString(text, (float) tx, (float) ty);
  }

  @Override
  public double measureTextWidth(String text, double size, boolean bold) {
    if (text == null) return 0;
    FontMetrics fm = g2.getFontMetrics(new Font(g2.getFont().getFamily(), bold ? Font.BOLD : Font.PLAIN, (int) Math.round(size)));
    return fm.stringWidth(text);
  }

  @Override
  public void setClipRect(double x, double y, double w, double h) {
    g2.setClip(new java.awt.geom.Rectangle2D.Double(x, y, w, h));
  }

  @Override
  public void setTextAlign(String hAlign, String vAlign) {
    if (hAlign != null) this.hAlign = hAlign;
    if (vAlign != null) this.vAlign = vAlign;
  }

  @Override
  public void setBlendMode(String mode) {
    if (mode == null || mode.isBlank() || "normal".equalsIgnoreCase(mode)) {
      g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
      return;
    }
    // AWT lacks true additive without custom composite; approximate with SrcOver.
    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
  }

  // Vector path extensions
  @Override
  public void beginPath() { currentPath = new Path2D.Double(); }

  @Override
  public void moveTo(double x, double y) { if (currentPath != null) currentPath.moveTo(x, y); }

  @Override
  public void lineTo(double x, double y) { if (currentPath != null) currentPath.lineTo(x, y); }

  @Override
  public void closePath() { if (currentPath != null) currentPath.closePath(); }

  @Override
  public void fillPath() { if (currentPath != null) { g2.setPaint(fillPaint); g2.fill(currentPath); } }

  @Override
  public void strokePath() { if (currentPath != null) { g2.setColor(stroke); g2.setStroke(basicStroke); g2.draw(currentPath); } }

  @Override
  public void setStrokeCap(String cap) {
    int c = switch (cap == null ? "square" : cap.toLowerCase()) {
      case "butt" -> BasicStroke.CAP_BUTT;
      case "round" -> BasicStroke.CAP_ROUND;
      default -> BasicStroke.CAP_SQUARE;
    };
    basicStroke = new BasicStroke(strokeWidth, c, basicStroke.getLineJoin(), basicStroke.getMiterLimit(), basicStroke.getDashArray(), basicStroke.getDashPhase());
  }

  @Override
  public void setStrokeJoin(String join) {
    int j = switch (join == null ? "miter" : join.toLowerCase()) {
      case "round" -> BasicStroke.JOIN_ROUND;
      case "bevel" -> BasicStroke.JOIN_BEVEL;
      default -> BasicStroke.JOIN_MITER;
    };
    basicStroke = new BasicStroke(strokeWidth, basicStroke.getEndCap(), j, basicStroke.getMiterLimit(), basicStroke.getDashArray(), basicStroke.getDashPhase());
  }

  @Override
  public void setMiterLimit(double limit) {
    basicStroke = new BasicStroke(strokeWidth, basicStroke.getEndCap(), basicStroke.getLineJoin(), (float) limit, basicStroke.getDashArray(), basicStroke.getDashPhase());
  }

  @Override
  public void setDash(double[] dashes, double phase) {
    float[] arr = null;
    if (dashes != null && dashes.length > 0) {
      arr = new float[dashes.length];
      for (int i = 0; i < dashes.length; i++) arr[i] = (float) dashes[i];
    }
    basicStroke = new BasicStroke(strokeWidth, basicStroke.getEndCap(), basicStroke.getLineJoin(), basicStroke.getMiterLimit(), arr, (float) phase);
  }

  @Override
  public void fillPolygon(double[] xy) {
    if (xy == null || xy.length < 4 || xy.length % 2 != 0) return;
    Path2D p = new Path2D.Double();
    p.moveTo(xy[0], xy[1]);
    for (int i = 2; i < xy.length; i += 2) p.lineTo(xy[i], xy[i+1]);
    p.closePath();
    g2.setPaint(fillPaint);
    g2.fill(p);
  }

  @Override
  public void strokePolygon(double[] xy) {
    if (xy == null || xy.length < 4 || xy.length % 2 != 0) return;
    Path2D p = new Path2D.Double();
    p.moveTo(xy[0], xy[1]);
    for (int i = 2; i < xy.length; i += 2) p.lineTo(xy[i], xy[i+1]);
    p.closePath();
    g2.setColor(stroke);
    g2.setStroke(basicStroke);
    g2.draw(p);
  }

  @Override
  public void fillArc(double cx, double cy, double r, double startDeg, double sweepDeg) {
    double d = r * 2;
    Arc2D arc = new Arc2D.Double(cx - r, cy - r, d, d, startDeg, sweepDeg, Arc2D.PIE);
    g2.setPaint(fillPaint);
    g2.fill(arc);
  }

  @Override
  public void strokeArc(double cx, double cy, double r, double startDeg, double sweepDeg) {
    double d = r * 2;
    Arc2D arc = new Arc2D.Double(cx - r, cy - r, d, d, startDeg, sweepDeg, Arc2D.OPEN);
    g2.setColor(stroke);
    g2.setStroke(basicStroke);
    g2.draw(arc);
  }

  @Override
  public void setFillLinearGradient(double x1, double y1, double x2, double y2, double[] positions, double[] colorsRgba) {
    if (positions == null || colorsRgba == null || positions.length * 4 != colorsRgba.length) return;
    float[] fr = new float[positions.length];
    Color[] cs = new Color[positions.length];
    for (int i = 0; i < positions.length; i++) {
      fr[i] = (float) positions[i];
      int base = i * 4;
      float r = (float) colorsRgba[base];
      float g = (float) colorsRgba[base+1];
      float b = (float) colorsRgba[base+2];
      float a = (float) (colorsRgba[base+3] * alpha);
      cs[i] = new Color(r, g, b, a);
    }
    fillPaint = new LinearGradientPaint((float) x1, (float) y1, (float) x2, (float) y2, fr, cs);
  }

  @Override
  public void setFillRadialGradient(double cx, double cy, double r, double[] positions, double[] colorsRgba) {
    if (positions == null || colorsRgba == null || positions.length * 4 != colorsRgba.length) return;
    float[] fr = new float[positions.length];
    Color[] cs = new Color[positions.length];
    for (int i = 0; i < positions.length; i++) {
      fr[i] = (float) positions[i];
      int base = i * 4;
      float rr = (float) colorsRgba[base];
      float gg = (float) colorsRgba[base+1];
      float bb = (float) colorsRgba[base+2];
      float aa = (float) (colorsRgba[base+3] * alpha);
      cs[i] = new Color(rr, gg, bb, aa);
    }
    fillPaint = new RadialGradientPaint(new Point((int) cx, (int) cy), (float) r, fr, cs);
  }

  private BufferedImage loadImage(String path) {
    if (path == null || path.isBlank()) return null;
    BufferedImage cached = imageCache.get(path);
    if (cached != null) return cached;
    try {
      ClassLoader loader = getClass().getClassLoader();
      URL url = loader.getResource(path);
      if (url == null) return null;
      BufferedImage img = ImageIO.read(url);
      if (img != null) imageCache.put(path, img);
      return img;
    } catch (Exception e) {
      return null;
    }
  }

  private void drawMissingPlaceholder(double x, double y, double w, double h) {
    Color oldColor = g2.getColor();
    Stroke oldStroke = g2.getStroke();
    g2.setColor(new Color(255, 0, 255, (int) (alpha * 255)));
    g2.fill(new java.awt.geom.Rectangle2D.Double(x, y, w, h));
    g2.setColor(new Color(0, 0, 0, (int) (alpha * 255)));
    g2.setStroke(new BasicStroke(Math.max(1f, (float) Math.min(w, h) * 0.05f)));
    g2.draw(new java.awt.geom.Line2D.Double(x, y, x + w, y + h));
    g2.draw(new java.awt.geom.Line2D.Double(x + w, y, x, y + h));
    g2.setColor(oldColor);
    g2.setStroke(oldStroke);
  }

  private void reportMissing(String path) {
    if (path == null || path.isBlank()) return;
    if (missing.add(path)) {
      System.err.println("Swing: missing image asset '" + path + "'");
    }
  }
}
