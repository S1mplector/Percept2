package com.jvn.fx.scene2d;

import java.net.URL;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.jvn.core.scene2d.Blitter2D;

import javafx.geometry.VPos;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.BlendMode;
import javafx.scene.effect.Effect;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

public class FxBlitter2D implements Blitter2D {
  private static final Logger log = Logger.getLogger(FxBlitter2D.class.getName());

  private static final double[] IDENTITY_COLOR_MATRIX = new double[] {
      1.0, 0.0, 0.0, 0.0, 0.0,
      0.0, 1.0, 0.0, 0.0, 0.0,
      0.0, 0.0, 1.0, 0.0, 0.0,
      0.0, 0.0, 0.0, 1.0, 0.0
  };

  private static final class RenderState {
    double[] colorMatrix = null;
    double blurRadius = 0.0;

    RenderState copy() {
      RenderState copy = new RenderState();
      copy.colorMatrix = colorMatrix != null ? colorMatrix.clone() : null;
      copy.blurRadius = blurRadius;
      return copy;
    }

    boolean hasColorMatrix() {
      if (colorMatrix == null || colorMatrix.length < IDENTITY_COLOR_MATRIX.length) return false;
      for (int i = 0; i < IDENTITY_COLOR_MATRIX.length; i++) {
        if (Math.abs(colorMatrix[i] - IDENTITY_COLOR_MATRIX[i]) > 1e-9) return true;
      }
      return false;
    }
  }

  private final GraphicsContext gc;
  private double viewportW = 0;
  private double viewportH = 0;
  private int cacheCapacity = 128;
  private final Map<String, Image> cache = new LinkedHashMap<>(16, 0.75f, true) {
    @Override protected boolean removeEldestEntry(Map.Entry<String, Image> eldest) { return size() > cacheCapacity; }
  };
  private final Map<String, Image> processedCache = new LinkedHashMap<>(16, 0.75f, true) {
    @Override protected boolean removeEldestEntry(Map.Entry<String, Image> eldest) { return size() > cacheCapacity; }
  };
  private final Set<String> missing = new HashSet<>();
  private final Deque<RenderState> stateStack = new ArrayDeque<>();
  private RenderState state = new RenderState();
  
  private final Map<String, MediaPlayer> videoPlayers = new LinkedHashMap<>();
  private final Map<String, MediaView> videoViews = new LinkedHashMap<>();
  private final Map<String, WritableImage> videoFrames = new LinkedHashMap<>();

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
  public void push() {
    gc.save();
    stateStack.push(state.copy());
  }

  @Override
  public void pop() {
    gc.restore();
    state = stateStack.isEmpty() ? new RenderState() : stateStack.pop();
    applyEffectState();
  }

  @Override
  public void translate(double x, double y) { gc.translate(x, y); }

  @Override
  public void rotateDeg(double degrees) { gc.rotate(degrees); }

  @Override
  public void scale(double sx, double sy) { gc.scale(sx, sy); }

  @Override
  public void transform(double mxx, double myx, double mxy, double myy, double tx, double ty) {
    gc.transform(mxx, myx, mxy, myy, tx, ty);
  }

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
    String lower = classpath.toLowerCase();
    if (lower.endsWith(".mp4") || lower.endsWith(".mov")) {
        drawVideo(classpath, x, y, w, h, -1, -1, -1, -1, false);
        return;
    }

    Image img = cache.computeIfAbsent(classpath, this::loadImage);
    if (img != null) {
      gc.drawImage(resolveProcessedImage(classpath, img), x, y, w, h);
    } else {
      reportMissing(classpath);
      drawMissingPlaceholder(x, y, w, h);
    }
  }

  @Override
  public void drawImageRegion(String classpath, double sx, double sy, double sw, double sh,
                              double dx, double dy, double dw, double dh) {
    if (classpath == null || classpath.isBlank()) return;
    String lower = classpath.toLowerCase();
    if (lower.endsWith(".mp4") || lower.endsWith(".mov")) {
        drawVideo(classpath, dx, dy, dw, dh, sx, sy, sw, sh, true);
        return;
    }

    Image img = cache.computeIfAbsent(classpath, this::loadImage);
    if (img != null) {
      gc.drawImage(resolveProcessedImage(classpath, img), sx, sy, sw, sh, dx, dy, dw, dh);
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

  @Override
  public void setColorMatrix(double[] matrix) {
    if (matrix == null || matrix.length < IDENTITY_COLOR_MATRIX.length) {
      clearColorMatrix();
      return;
    }
    state.colorMatrix = Arrays.copyOf(matrix, IDENTITY_COLOR_MATRIX.length);
  }

  @Override
  public void clearColorMatrix() {
    state.colorMatrix = null;
  }

  @Override
  public void setBlurRadius(double radius) {
    state.blurRadius = Math.max(0.0, radius);
    applyEffectState();
  }

  public void setCacheCapacity(int capacity) { this.cacheCapacity = Math.max(16, capacity); }
  public void evict(String path) {
    if (path != null) {
      cache.remove(path);
      processedCache.keySet().removeIf(key -> key.startsWith(path + "::"));
      missing.remove(path);
      MediaPlayer mp = videoPlayers.remove(path);
      if (mp != null) {
          mp.stop();
          mp.dispose();
      }
      videoViews.remove(path);
      videoFrames.remove(path);
    }
  }
  public void clearCache() {
    cache.clear();
    processedCache.clear();
    missing.clear();
    for (MediaPlayer mp : videoPlayers.values()) {
        mp.stop();
        mp.dispose();
    }
    videoPlayers.clear();
    videoViews.clear();
    videoFrames.clear();
  }

  private Image loadImage(String path) {
    String url = resolveMediaUrl(path);
    if (url != null) return new Image(url);
    return null;
  }

  private String resolveMediaUrl(String path) {
    try {
      URL u = getClass().getClassLoader().getResource(path);
      if (u != null) return u.toExternalForm();

      java.io.File f = new java.io.File(path);
      if (f.isAbsolute() && f.exists()) return f.toURI().toString();

      if (projectRoot != null) {
        java.io.File pf = new java.io.File(projectRoot, path);
        if (pf.exists()) return pf.toURI().toString();
      }
    } catch (Exception e) {
      log.log(Level.WARNING, "Failed to resolve asset path: " + path, e);
    }
    return null;
  }

  private java.io.File projectRoot;
  public void setProjectRoot(java.io.File root) { this.projectRoot = root; }

  private double clamp01(double v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }

  private void applyEffectState() {
    Effect effect = null;
    if (state.blurRadius > 1e-9) {
      effect = new GaussianBlur(Math.min(63.0, state.blurRadius));
    }
    gc.setEffect(effect);
  }

  private Image resolveProcessedImage(String path, Image source) {
    if (source == null || !state.hasColorMatrix()) return source;
    String cacheKey = buildProcessedKey(path, state.colorMatrix);
    return processedCache.computeIfAbsent(cacheKey, key -> applyColorMatrix(source, state.colorMatrix));
  }

  private String buildProcessedKey(String path, double[] matrix) {
    StringBuilder sb = new StringBuilder(path).append("::");
    for (int i = 0; i < IDENTITY_COLOR_MATRIX.length; i++) {
      if (i > 0) sb.append(',');
      sb.append(Math.round(matrix[i] * 100000.0) / 100000.0);
    }
    return sb.toString();
  }

  private WritableImage applyColorMatrix(Image source, double[] matrix) {
    int width = Math.max(1, (int) Math.round(source.getWidth()));
    int height = Math.max(1, (int) Math.round(source.getHeight()));
    WritableImage output = new WritableImage(width, height);
    PixelReader reader = source.getPixelReader();
    PixelWriter writer = output.getPixelWriter();
    if (reader == null || writer == null) return output;

    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        int argb = reader.getArgb(x, y);
        double a = ((argb >>> 24) & 0xff) / 255.0;
        double r = ((argb >>> 16) & 0xff) / 255.0;
        double g = ((argb >>> 8) & 0xff) / 255.0;
        double b = (argb & 0xff) / 255.0;

        double outR = clamp01(matrix[0] * r + matrix[1] * g + matrix[2] * b + matrix[3] * a + matrix[4]);
        double outG = clamp01(matrix[5] * r + matrix[6] * g + matrix[7] * b + matrix[8] * a + matrix[9]);
        double outB = clamp01(matrix[10] * r + matrix[11] * g + matrix[12] * b + matrix[13] * a + matrix[14]);
        double outA = clamp01(matrix[15] * r + matrix[16] * g + matrix[17] * b + matrix[18] * a + matrix[19]);

        int outArgb =
            ((int) Math.round(outA * 255.0) << 24)
                | ((int) Math.round(outR * 255.0) << 16)
                | ((int) Math.round(outG * 255.0) << 8)
                | (int) Math.round(outB * 255.0);
        writer.setArgb(x, y, outArgb);
      }
    }
    return output;
  }

  private void drawMissingPlaceholder(double x, double y, double w, double h) {
    gc.setFill(Color.color(1, 0, 1, 0.8)); // magenta box
    gc.fillRect(x, y, w, h);
    gc.setStroke(Color.color(0, 0, 0, 0.9));
    gc.setLineWidth(Math.max(1, Math.min(w, h) * 0.05));
    gc.strokeLine(x, y, x + w, y + h);
    gc.strokeLine(x + w, y, x, y + h);
  }

  private void drawVideo(String path, double dx, double dy, double dw, double dh, double sx, double sy, double sw, double sh, boolean isRegion) {
      MediaPlayer player = videoPlayers.get(path);
      if (player == null && !missing.contains(path)) {
          String url = resolveMediaUrl(path);
          if (url != null) {
              Media media = new Media(url);
              player = new MediaPlayer(media);
              player.setCycleCount(MediaPlayer.INDEFINITE);
              player.setMute(true);
              player.play();
              videoPlayers.put(path, player);
              
              MediaView view = new MediaView(player);
              videoViews.put(path, view);
          } else {
              reportMissing(path);
          }
      }
      
      if (player != null) {
          MediaView view = videoViews.get(path);
          Media media = player.getMedia();
          int vw = media.getWidth();
          int vh = media.getHeight();
          if (vw > 0 && vh > 0) {
              WritableImage frame = videoFrames.get(path);
              if (frame == null || frame.getWidth() != vw || frame.getHeight() != vh) {
                  frame = new WritableImage(vw, vh);
                  videoFrames.put(path, frame);
              }
              SnapshotParameters params = new SnapshotParameters();
              params.setFill(Color.TRANSPARENT);
              view.snapshot(params, frame);
              
              Image processed = resolveProcessedImage(path, frame);
              
              if (isRegion) {
                  gc.drawImage(processed, sx, sy, sw, sh, dx, dy, dw, dh);
              } else {
                  gc.drawImage(processed, dx, dy, dw, dh);
              }
              return;
          }
      }
      
      drawMissingPlaceholder(dx, dy, dw, dh);
  }

  private void reportMissing(String path) {
    if (path == null || path.isBlank()) return;
    if (missing.add(path)) {
      System.err.println("FX: missing image asset '" + path + "'");
    }
  }
}
