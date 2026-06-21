package com.jvn.web;

import java.util.ArrayDeque;
import java.util.Deque;
import com.jvn.core.math.Capsule2;
import com.jvn.core.scene2d.Blitter2D;
import com.jvn.core.scene2d.RenderFeature;
import com.jvn.core.scene2d.RendererCapabilities;
import com.jvn.render.RenderSurface;

/**
 * HTML5 Canvas implementation of {@code Blitter2D} for web rendering.
 *
 * <p>This renderer draws to an HTML5 canvas element via TeaVM's JavaScript Object (JSO) bindings.</p>
 */
public class WebRenderer implements Blitter2D {
  public static final RendererCapabilities CAPABILITIES = RendererCapabilities.of(
      "WebGL/Canvas2D",
      RenderFeature.AFFINE_TRANSFORM,
      RenderFeature.VECTOR_PATHS,
      RenderFeature.ADVANCED_STROKE,
      RenderFeature.RECTANGULAR_CLIP,
      RenderFeature.POLYGONS,
      RenderFeature.TEXT_ALIGNMENT,
      RenderFeature.BLEND_MODES);

  private final RenderSurface surface;
  private final Object ctx; // CanvasRenderingContext2D
  private final WebImageCache imageCache;
  private double globalAlpha = 1.0;
  private final Deque<RenderState> stateStack = new ArrayDeque<>();
  private RenderState state = new RenderState();

  private static class RenderState {
    String fillStyle = "#000000";
    String strokeStyle = "#000000";
    double lineWidth = 1.0;
    double globalAlpha = 1.0;

    RenderState copy() {
      RenderState copy = new RenderState();
      copy.fillStyle = fillStyle;
      copy.strokeStyle = strokeStyle;
      copy.lineWidth = lineWidth;
      copy.globalAlpha = globalAlpha;
      return copy;
    }
  }

  public WebRenderer(RenderSurface surface) {
    this.surface = surface;
    this.ctx = getCanvasContext2D((WebCanvasRenderSurface) surface);
    this.imageCache = new WebImageCache();
  }

  @Override
  public RendererCapabilities getCapabilities() { return CAPABILITIES; }

  @Override
  public void clear(double r, double g, double b, double a) {
    String color = rgbToHex(r, g, b, a);
    setFillStyleNative(ctx, color);
    fillRectNative(ctx, 0, 0, surface.getWidth(), surface.getHeight());
  }

  @Override
  public void setFill(double r, double g, double b, double a) {
    state.fillStyle = rgbToHex(r, g, b, a);
    setFillStyleNative(ctx, state.fillStyle);
  }

  @Override
  public void setStroke(double r, double g, double b, double a) {
    state.strokeStyle = rgbToHex(r, g, b, a);
    setStrokeStyleNative(ctx, state.strokeStyle);
  }

  @Override
  public void setStrokeWidth(double w) {
    state.lineWidth = w;
    setLineWidthNative(ctx, w);
  }

  @Override
  public void setGlobalAlpha(double a) {
    globalAlpha = Math.max(0, Math.min(1, a));
    setGlobalAlphaNative(ctx, globalAlpha);
  }

  @Override
  public void setFont(String family, double size, boolean bold) {
    String font = (bold ? "bold " : "") + size + "px " + (family != null ? family : "Arial");
    setFontNative(ctx, font);
  }

  @Override
  public void push() {
    saveNative(ctx);
    stateStack.push(state.copy());
  }

  @Override
  public void pop() {
    restoreNative(ctx);
    state = stateStack.isEmpty() ? new RenderState() : stateStack.pop();
  }

  @Override
  public void translate(double x, double y) {
    translateNative(ctx, x, y);
  }

  @Override
  public void rotateDeg(double degrees) {
    rotateNative(ctx, Math.toRadians(degrees));
  }

  @Override
  public void scale(double sx, double sy) {
    scaleNative(ctx, sx, sy);
  }

  @Override
  public void transform(double mxx, double myx, double mxy, double myy, double tx, double ty) {
    transformNative(ctx, mxx, myx, mxy, myy, tx, ty);
  }

  @Override
  public void fillRect(double x, double y, double w, double h) {
    fillRectNative(ctx, x, y, w, h);
  }

  @Override
  public void strokeRect(double x, double y, double w, double h) {
    strokeRectNative(ctx, x, y, w, h);
  }

  @Override
  public void fillCircle(double cx, double cy, double radius) {
    beginPathNative(ctx);
    arcNative(ctx, cx, cy, radius, 0, Math.PI * 2);
    fillNative(ctx);
  }

  @Override
  public void strokeCircle(double cx, double cy, double radius) {
    beginPathNative(ctx);
    arcNative(ctx, cx, cy, radius, 0, Math.PI * 2);
    strokeNative(ctx);
  }

  @Override
  public void drawLine(double x1, double y1, double x2, double y2) {
    beginPathNative(ctx);
    moveToNative(ctx, x1, y1);
    lineToNative(ctx, x2, y2);
    strokeNative(ctx);
  }

  @Override
  public void drawImage(String classpath, double x, double y, double w, double h) {
    if (classpath == null) return;
    Object img = imageCache.getOrLoad(classpath);
    if (img != null) {
      drawImageNative(ctx, img, x, y, w, h);
    }
  }

  @Override
  public void drawImageRegion(String classpath, double sx, double sy, double sw, double sh,
                              double dx, double dy, double dw, double dh) {
    if (classpath == null) return;
    Object img = imageCache.getOrLoad(classpath);
    if (img != null) {
      drawImageRegionNative(ctx, img, sx, sy, sw, sh, dx, dy, dw, dh);
    }
  }

  @Override
  public void drawText(String text, double x, double y, double size, boolean bold) {
    if (text == null) return;
    setFont("Arial", size, bold);
    fillTextNative(ctx, text, x, y);
  }

  @Override
  public double measureTextWidth(String text, double size, boolean bold) {
    if (text == null) return 0;
    setFont("Arial", size, bold);
    return measureTextNative(ctx, text);
  }

  @Override
  public void setClipRect(double x, double y, double w, double h) {
    beginPathNative(ctx);
    rectNative(ctx, x, y, w, h);
    clipNative(ctx);
  }

  @Override
  public void setTextAlign(String hAlign, String vAlign) {
    if (hAlign != null) {
      setTextAlignNative(ctx, hAlign.toLowerCase());
    }
    if (vAlign != null) {
      setTextBaselineNative(ctx, vAlign.toLowerCase());
    }
  }

  @Override
  public void setBlendMode(String mode) {
    if (mode != null) {
      setGlobalCompositeOperationNative(ctx, mode.toLowerCase());
    }
  }

  @Override
  public void beginPath() {
    beginPathNative(ctx);
  }

  @Override
  public void moveTo(double x, double y) {
    moveToNative(ctx, x, y);
  }

  @Override
  public void lineTo(double x, double y) {
    lineToNative(ctx, x, y);
  }

  @Override
  public void closePath() {
    closePathNative(ctx);
  }

  @Override
  public void fillPath() {
    fillNative(ctx);
  }

  @Override
  public void strokePath() {
    strokeNative(ctx);
  }

  @Override
  public void setStrokeCap(String cap) {
    setLineCapNative(ctx, cap == null ? "square" : cap.toLowerCase());
  }

  @Override
  public void setStrokeJoin(String join) {
    setLineJoinNative(ctx, join == null ? "miter" : join.toLowerCase());
  }

  @Override
  public void setDash(double[] dashes, double phase) {
    setLineDashNative(ctx, dashes == null ? new double[0] : dashes, phase);
  }

  @Override
  public void fillPolygon(double[] xy) {
    if (drawPolygonPath(xy)) fillNative(ctx);
  }

  @Override
  public void strokePolygon(double[] xy) {
    if (drawPolygonPath(xy)) strokeNative(ctx);
  }

  @Override
  public void fillCapsule(Capsule2 capsule) {
    if (capsule == null) return;
    saveNative(ctx);
    setStrokeStyleNative(ctx, state.fillStyle);
    setLineWidthNative(ctx, capsule.r * 2.0);
    setLineCapNative(ctx, "round");
    drawLine(capsule.x1, capsule.y1, capsule.x2, capsule.y2);
    restoreNative(ctx);
  }

  private boolean drawPolygonPath(double[] xy) {
    if (xy == null || xy.length < 6 || xy.length % 2 != 0) return false;
    beginPathNative(ctx);
    moveToNative(ctx, xy[0], xy[1]);
    for (int i = 2; i < xy.length; i += 2) {
      lineToNative(ctx, xy[i], xy[i + 1]);
    }
    closePathNative(ctx);
    return true;
  }

  private String rgbToHex(double r, double g, double b, double a) {
    int ir = Math.round((float) (r * 255));
    int ig = Math.round((float) (g * 255));
    int ib = Math.round((float) (b * 255));
    int ia = Math.round((float) (a * 255));

    if (ia < 255) {
      return String.format("rgba(%d,%d,%d,%.3f)", ir, ig, ib, a);
    } else {
      return String.format("rgb(%d,%d,%d)", ir, ig, ib);
    }
  }

  // Canvas API native bindings
  private static native Object getCanvasContext2D(WebCanvasRenderSurface surface) /*-{
    var canvas = surface.@com.jvn.web.WebCanvasRenderSurface::getCanvasElement()();
    return canvas ? canvas.getContext('2d') : null;
  }-*/;

  private static native void setFillStyleNative(Object ctx, String style) /*-{
    ctx.fillStyle = style;
  }-*/;

  private static native void setStrokeStyleNative(Object ctx, String style) /*-{
    ctx.strokeStyle = style;
  }-*/;

  private static native void setLineWidthNative(Object ctx, double width) /*-{
    ctx.lineWidth = width;
  }-*/;

  private static native void setGlobalAlphaNative(Object ctx, double alpha) /*-{
    ctx.globalAlpha = alpha;
  }-*/;

  private static native void setFontNative(Object ctx, String font) /*-{
    ctx.font = font;
  }-*/;

  private static native void saveNative(Object ctx) /*-{ ctx.save(); }-*/;
  private static native void restoreNative(Object ctx) /*-{ ctx.restore(); }-*/;
  private static native void translateNative(Object ctx, double x, double y) /*-{ ctx.translate(x, y); }-*/;
  private static native void rotateNative(Object ctx, double radians) /*-{ ctx.rotate(radians); }-*/;
  private static native void scaleNative(Object ctx, double sx, double sy) /*-{ ctx.scale(sx, sy); }-*/;
  private static native void transformNative(Object ctx, double a, double b, double c, double d, double e, double f) /*-{
    ctx.transform(a, b, c, d, e, f);
  }-*/;

  private static native void fillRectNative(Object ctx, double x, double y, double w, double h) /*-{
    ctx.fillRect(x, y, w, h);
  }-*/;

  private static native void strokeRectNative(Object ctx, double x, double y, double w, double h) /*-{
    ctx.strokeRect(x, y, w, h);
  }-*/;

  private static native void beginPathNative(Object ctx) /*-{ ctx.beginPath(); }-*/;
  private static native void closePathNative(Object ctx) /*-{ ctx.closePath(); }-*/;
  private static native void arcNative(Object ctx, double cx, double cy, double r, double start, double end) /*-{
    ctx.arc(cx, cy, r, start, end);
  }-*/;

  private static native void fillNative(Object ctx) /*-{ ctx.fill(); }-*/;
  private static native void strokeNative(Object ctx) /*-{ ctx.stroke(); }-*/;

  private static native void moveToNative(Object ctx, double x, double y) /*-{ ctx.moveTo(x, y); }-*/;
  private static native void lineToNative(Object ctx, double x, double y) /*-{ ctx.lineTo(x, y); }-*/;
  private static native void rectNative(Object ctx, double x, double y, double w, double h) /*-{ ctx.rect(x, y, w, h); }-*/;
  private static native void clipNative(Object ctx) /*-{ ctx.clip(); }-*/;

  private static native void fillTextNative(Object ctx, String text, double x, double y) /*-{
    ctx.fillText(text, x, y);
  }-*/;

  private static native double measureTextNative(Object ctx, String text) /*-{
    return ctx.measureText(text).width;
  }-*/;

  private static native void setTextAlignNative(Object ctx, String align) /*-{
    ctx.textAlign = align;
  }-*/;

  private static native void setTextBaselineNative(Object ctx, String baseline) /*-{
    ctx.textBaseline = baseline;
  }-*/;

  private static native void setGlobalCompositeOperationNative(Object ctx, String op) /*-{
    ctx.globalCompositeOperation = op;
  }-*/;

  private static native void setLineCapNative(Object ctx, String cap) /*-{
    ctx.lineCap = cap;
  }-*/;

  private static native void setLineJoinNative(Object ctx, String join) /*-{
    ctx.lineJoin = join;
  }-*/;

  private static native void setLineDashNative(Object ctx, double[] dashes, double phase) /*-{
    ctx.setLineDash(dashes || []);
    ctx.lineDashOffset = phase;
  }-*/;

  private static native void drawImageNative(Object ctx, Object img, double x, double y, double w, double h) /*-{
    ctx.drawImage(img, x, y, w, h);
  }-*/;

  private static native void drawImageRegionNative(Object ctx, Object img, double sx, double sy, double sw, double sh,
                                                     double dx, double dy, double dw, double dh) /*-{
    ctx.drawImage(img, sx, sy, sw, sh, dx, dy, dw, dh);
  }-*/;
}
