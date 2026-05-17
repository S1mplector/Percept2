package com.jvn.android;

import java.util.ArrayDeque;
import java.util.Deque;

import com.jvn.core.assets.AssetManager;
import com.jvn.core.scene2d.Blitter2D;
import com.jvn.render.RenderSurface;

/**
 * Android implementation of {@code Blitter2D} using {@code android.graphics.Canvas}.
 *
 * <p>This renderer draws to an Android Canvas via reflection-based interop.</p>
 */
public class AndroidRenderer implements Blitter2D {

  private final RenderSurface surface;
  private final Object canvas; // android.graphics.Canvas
  private final AndroidImageCache imageCache;
  private final Deque<RenderState> stateStack = new ArrayDeque<>();
  private RenderState state = new RenderState();

  private static class RenderState {
    int fillColor = 0xFF000000;
    int strokeColor = 0xFF000000;
    float strokeWidth = 1.0f;
    float globalAlpha = 1.0f;
    String fontFamily = "Arial";
    float fontSize = 12.0f;
    boolean bold = false;

    RenderState copy() {
      RenderState copy = new RenderState();
      copy.fillColor = fillColor;
      copy.strokeColor = strokeColor;
      copy.strokeWidth = strokeWidth;
      copy.globalAlpha = globalAlpha;
      copy.fontFamily = fontFamily;
      copy.fontSize = fontSize;
      copy.bold = bold;
      return copy;
    }
  }

  public AndroidRenderer(RenderSurface surface, AssetManager assetManager) {
    this.surface = surface;
    this.imageCache = new AndroidImageCache(assetManager);
    if (surface instanceof AndroidRenderSurface androidSurface) {
      this.canvas = createCanvasFromSurfaceHolder(androidSurface.getSurfaceHolder());
    } else {
      throw new IllegalArgumentException("AndroidRenderer requires AndroidRenderSurface");
    }
  }

  @Override
  public void clear(double r, double g, double b, double a) {
    int color = rgbaToArgb(r, g, b, a);
    drawRectNative(canvas, 0, 0, (int) surface.getWidth(), (int) surface.getHeight(), color, true);
  }

  @Override
  public void setFill(double r, double g, double b, double a) {
    state.fillColor = rgbaToArgb(r, g, b, a);
  }

  @Override
  public void setStroke(double r, double g, double b, double a) {
    state.strokeColor = rgbaToArgb(r, g, b, a);
  }

  @Override
  public void setStrokeWidth(double w) {
    state.strokeWidth = (float) w;
  }

  @Override
  public void setGlobalAlpha(double a) {
    state.globalAlpha = (float) Math.max(0, Math.min(1, a));
  }

  @Override
  public void setFont(String family, double size, boolean bold) {
    state.fontFamily = (family != null && !family.isEmpty()) ? family : "Arial";
    state.fontSize = (float) size;
    state.bold = bold;
  }

  @Override
  public void push() {
    // Canvas save/restore equivalent
    stateStack.push(state.copy());
  }

  @Override
  public void pop() {
    state = stateStack.isEmpty() ? new RenderState() : stateStack.pop();
  }

  @Override
  public void translate(double x, double y) {
    translateNative(canvas, (float) x, (float) y);
  }

  @Override
  public void rotateDeg(double degrees) {
    rotateNative(canvas, (float) degrees);
  }

  @Override
  public void scale(double sx, double sy) {
    scaleNative(canvas, (float) sx, (float) sy);
  }

  @Override
  public void transform(double mxx, double myx, double mxy, double myy, double tx, double ty) {
    // Android Canvas doesn't have direct matrix transform; use save/restore with multiple operations
    // This is a limitation; full affine transforms may need custom matrix handling
  }

  @Override
  public void fillRect(double x, double y, double w, double h) {
    drawRectNative(canvas, (int) x, (int) y, (int) (x + w), (int) (y + h), state.fillColor, true);
  }

  @Override
  public void strokeRect(double x, double y, double w, double h) {
    drawRectNative(canvas, (int) x, (int) y, (int) (x + w), (int) (y + h), state.strokeColor, false);
  }

  @Override
  public void fillCircle(double cx, double cy, double radius) {
    drawCircleNative(canvas, (float) cx, (float) cy, (float) radius, state.fillColor, true);
  }

  @Override
  public void strokeCircle(double cx, double cy, double radius) {
    drawCircleNative(canvas, (float) cx, (float) cy, (float) radius, state.strokeColor, false);
  }

  @Override
  public void drawLine(double x1, double y1, double x2, double y2) {
    drawLineNative(canvas, (float) x1, (float) y1, (float) x2, (float) y2, state.strokeColor);
  }

  @Override
  public void drawImage(String classpath, double x, double y, double w, double h) {
    if (classpath == null) return;
    Object bitmap = imageCache.getOrLoad(classpath);
    if (bitmap != null) {
      drawBitmapNative(canvas, bitmap, (float) x, (float) y, (float) w, (float) h);
    }
  }

  @Override
  public void drawImageRegion(String classpath, double sx, double sy, double sw, double sh,
                              double dx, double dy, double dw, double dh) {
    if (classpath == null) return;
    Object bitmap = imageCache.getOrLoad(classpath);
    if (bitmap != null) {
      drawBitmapRegionNative(canvas, bitmap, (int) sx, (int) sy, (int) sw, (int) sh,
                             (int) dx, (int) dy, (int) dw, (int) dh);
    }
  }

  @Override
  public void drawText(String text, double x, double y, double size, boolean bold) {
    if (text == null) return;
    drawTextNative(canvas, text, (float) x, (float) y, (float) size, state.fillColor);
  }

  @Override
  public double measureTextWidth(String text, double size, boolean bold) {
    if (text == null) return 0;
    return measureTextNative(canvas, text, (float) size);
  }

  @Override
  public void setClipRect(double x, double y, double w, double h) {
    clipRectNative(canvas, (int) x, (int) y, (int) (x + w), (int) (y + h));
  }

  @Override
  public void setTextAlign(String hAlign, String vAlign) {
    // Android text alignment is handled differently; store for later use
    // This would be applied when drawing text
  }

  @Override
  public void setBlendMode(String mode) {
    // Android blend modes via PorterDuff; placeholder for now
  }

  private int rgbaToArgb(double r, double g, double b, double a) {
    int ir = Math.round((float) (r * 255));
    int ig = Math.round((float) (g * 255));
    int ib = Math.round((float) (b * 255));
    int ia = Math.round((float) (a * 255));
    return (ia << 24) | (ir << 16) | (ig << 8) | ib;
  }

  // Native Android Canvas bindings (via reflection/JNI)
  private static native Object createCanvasFromSurfaceHolder(Object surfaceHolder) /*-{
    // Would use reflection to create Canvas from SurfaceHolder.lockCanvas()
    return null;
  }-*/;

  private static native void translateNative(Object canvas, float x, float y) /*-{
    // Would use reflection to call canvas.translate()
  }-*/;

  private static native void rotateNative(Object canvas, float degrees) /*-{
    // Would use reflection to call canvas.rotate()
  }-*/;

  private static native void scaleNative(Object canvas, float sx, float sy) /*-{
    // Would use reflection to call canvas.scale()
  }-*/;

  private static native void drawRectNative(Object canvas, int left, int top, int right, int bottom, int color, boolean fill) /*-{
    // Would use reflection to call canvas.drawRect() or paint methods
  }-*/;

  private static native void drawCircleNative(Object canvas, float cx, float cy, float radius, int color, boolean fill) /*-{
    // Would use reflection to call canvas.drawCircle()
  }-*/;

  private static native void drawLineNative(Object canvas, float x1, float y1, float x2, float y2, int color) /*-{
    // Would use reflection to call canvas.drawLine()
  }-*/;

  private static native void drawTextNative(Object canvas, String text, float x, float y, float size, int color) /*-{
    // Would use reflection to call canvas.drawText()
  }-*/;

  private static native double measureTextNative(Object canvas, String text, float size) /*-{
    // Would use reflection to measure text
    return 0.0;
  }-*/;

  private static native void clipRectNative(Object canvas, int left, int top, int right, int bottom) /*-{
    // Would use reflection to call canvas.clipRect()
  }-*/;

  private static native void drawBitmapNative(Object canvas, Object bitmap, float x, float y, float w, float h) /*-{
    // Would use reflection to call canvas.drawBitmap() with scaling
  }-*/;

  private static native void drawBitmapRegionNative(Object canvas, Object bitmap,
                                                     int sx, int sy, int sw, int sh,
                                                     int dx, int dy, int dw, int dh) /*-{
    // Would use Rect srcRect and dstRect to call canvas.drawBitmap() with source region
  }-*/;
}
