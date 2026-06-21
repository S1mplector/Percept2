package com.jvn.ios;

import java.util.ArrayDeque;
import java.util.Deque;

import com.jvn.core.assets.AssetManager;
import com.jvn.core.scene2d.Blitter2D;
import com.jvn.core.scene2d.RenderDiagnostics;
import com.jvn.core.scene2d.RenderFeature;
import com.jvn.core.scene2d.RendererCapabilities;
import com.jvn.render.RenderSurface;

/**
 * iOS implementation of {@code Blitter2D} using CoreGraphics.
 *
 * <p>This renderer draws to an iOS graphics context via Multi-OS Engine reflection bindings.</p>
 */
public class IosRenderer implements Blitter2D {
  public static final RendererCapabilities CAPABILITIES = RendererCapabilities.baseline("iOS CoreGraphics");

  private final RenderSurface surface;
  private final Object context; // CGContext
  private final IosImageCache imageCache;
  private final Deque<RenderState> stateStack = new ArrayDeque<>();
  private RenderState state = new RenderState();

  private static class RenderState {
    double fillRed = 0.0;
    double fillGreen = 0.0;
    double fillBlue = 0.0;
    double fillAlpha = 1.0;
    double strokeRed = 0.0;
    double strokeGreen = 0.0;
    double strokeBlue = 0.0;
    double strokeAlpha = 1.0;
    double strokeWidth = 1.0;
    double globalAlpha = 1.0;
    String fontFamily = "Arial";
    double fontSize = 12.0;
    boolean bold = false;

    RenderState copy() {
      RenderState copy = new RenderState();
      copy.fillRed = fillRed;
      copy.fillGreen = fillGreen;
      copy.fillBlue = fillBlue;
      copy.fillAlpha = fillAlpha;
      copy.strokeRed = strokeRed;
      copy.strokeGreen = strokeGreen;
      copy.strokeBlue = strokeBlue;
      copy.strokeAlpha = strokeAlpha;
      copy.strokeWidth = strokeWidth;
      copy.globalAlpha = globalAlpha;
      copy.fontFamily = fontFamily;
      copy.fontSize = fontSize;
      copy.bold = bold;
      return copy;
    }
  }

  public IosRenderer(RenderSurface surface, AssetManager assetManager) {
    this.surface = surface;
    this.imageCache = new IosImageCache(assetManager);
    if (surface instanceof IosRenderSurface iosSurface) {
      this.context = createGraphicsContext(iosSurface.getView());
    } else {
      throw new IllegalArgumentException("IosRenderer requires IosRenderSurface");
    }
  }

  @Override
  public RendererCapabilities getCapabilities() { return CAPABILITIES; }

  @Override
  public void clear(double r, double g, double b, double a) {
    setFillColor(r, g, b, a);
    fillRectNative(context, 0, 0, surface.getWidth(), surface.getHeight());
  }

  @Override
  public void setFill(double r, double g, double b, double a) {
    state.fillRed = r;
    state.fillGreen = g;
    state.fillBlue = b;
    state.fillAlpha = a;
    setFillColorNative(context, r, g, b, a * state.globalAlpha);
  }

  @Override
  public void setStroke(double r, double g, double b, double a) {
    state.strokeRed = r;
    state.strokeGreen = g;
    state.strokeBlue = b;
    state.strokeAlpha = a;
    setStrokeColorNative(context, r, g, b, a * state.globalAlpha);
  }

  @Override
  public void setStrokeWidth(double w) {
    state.strokeWidth = w;
    setLineWidthNative(context, w);
  }

  @Override
  public void setGlobalAlpha(double a) {
    state.globalAlpha = Math.max(0, Math.min(1, a));
    // Reapply fill and stroke with new alpha
    setFillColorNative(context, state.fillRed, state.fillGreen, state.fillBlue,
        state.fillAlpha * state.globalAlpha);
    setStrokeColorNative(context, state.strokeRed, state.strokeGreen, state.strokeBlue,
        state.strokeAlpha * state.globalAlpha);
  }

  @Override
  public void setFont(String family, double size, boolean bold) {
    state.fontFamily = (family != null && !family.isEmpty()) ? family : "Arial";
    state.fontSize = size;
    state.bold = bold;
  }

  @Override
  public void push() {
    saveGraphicsStateNative(context);
    stateStack.push(state.copy());
  }

  @Override
  public void pop() {
    restoreGraphicsStateNative(context);
    state = stateStack.isEmpty() ? new RenderState() : stateStack.pop();
  }

  @Override
  public void translate(double x, double y) {
    translateNative(context, x, y);
  }

  @Override
  public void rotateDeg(double degrees) {
    // iOS uses radians; convert from degrees
    rotateNative(context, Math.toRadians(degrees));
  }

  @Override
  public void scale(double sx, double sy) {
    scaleNative(context, sx, sy);
  }

  @Override
  public void transform(double mxx, double myx, double mxy, double myy, double tx, double ty) {
    RenderDiagnostics.unsupported(this, RenderFeature.AFFINE_TRANSFORM, "transform");
  }

  @Override
  public void fillRect(double x, double y, double w, double h) {
    fillRectNative(context, x, y, x + w, y + h);
  }

  @Override
  public void strokeRect(double x, double y, double w, double h) {
    strokeRectNative(context, x, y, x + w, y + h);
  }

  @Override
  public void fillCircle(double cx, double cy, double radius) {
    fillEllipseNative(context, cx, cy, radius, radius);
  }

  @Override
  public void strokeCircle(double cx, double cy, double radius) {
    strokeEllipseNative(context, cx, cy, radius, radius);
  }

  @Override
  public void drawLine(double x1, double y1, double x2, double y2) {
    drawLineNative(context, x1, y1, x2, y2);
  }

  @Override
  public void drawImage(String classpath, double x, double y, double w, double h) {
    if (classpath == null) return;
    Object image = imageCache.getOrLoad(classpath);
    if (image != null) {
      drawImageNative(context, image, x, y, w, h);
    }
  }

  @Override
  public void drawImageRegion(String classpath, double sx, double sy, double sw, double sh,
                              double dx, double dy, double dw, double dh) {
    if (classpath == null) return;
    Object image = imageCache.getOrLoad(classpath);
    if (image != null) {
      drawImageRegionNative(context, image, sx, sy, sw, sh, dx, dy, dw, dh);
    }
  }

  @Override
  public void drawText(String text, double x, double y, double size, boolean bold) {
    if (text == null) return;
    drawTextNative(context, text, x, y, size, state.fillRed, state.fillGreen,
        state.fillBlue, state.fillAlpha);
  }

  @Override
  public double measureTextWidth(String text, double size, boolean bold) {
    if (text == null) return 0;
    return measureTextNative(context, text, size);
  }

  @Override
  public void setClipRect(double x, double y, double w, double h) {
    RenderDiagnostics.unsupported(this, RenderFeature.RECTANGULAR_CLIP, "setClipRect");
  }

  @Override
  public void setTextAlign(String hAlign, String vAlign) {
    RenderDiagnostics.unsupported(this, RenderFeature.TEXT_ALIGNMENT, "setTextAlign");
  }

  @Override
  public void setBlendMode(String mode) {
    RenderDiagnostics.unsupported(this, RenderFeature.BLEND_MODES, "setBlendMode");
  }

  private void setFillColor(double r, double g, double b, double a) {
    setFillColorNative(context, r, g, b, a);
  }

  // iOS CoreGraphics native bindings (via Multi-OS Engine)
  private static native Object createGraphicsContext(Object view) /*-{
    // Would use MOE reflection to get UIGraphicsGetCurrentContext()
    // This is called within the view's drawRect: method
    return null;
  }-*/;

  private static native void setFillColorNative(Object context, double r, double g, double b, double a) /*-{
    // Would call CGContextSetRGBFillColor(context, r, g, b, a)
  }-*/;

  private static native void setStrokeColorNative(Object context, double r, double g, double b, double a) /*-{
    // Would call CGContextSetRGBStrokeColor(context, r, g, b, a)
  }-*/;

  private static native void setLineWidthNative(Object context, double width) /*-{
    // Would call CGContextSetLineWidth(context, width)
  }-*/;

  private static native void saveGraphicsStateNative(Object context) /*-{
    // Would call CGContextSaveGState(context)
  }-*/;

  private static native void restoreGraphicsStateNative(Object context) /*-{
    // Would call CGContextRestoreGState(context)
  }-*/;

  private static native void translateNative(Object context, double x, double y) /*-{
    // Would call CGContextTranslateCTM(context, x, y)
  }-*/;

  private static native void rotateNative(Object context, double radians) /*-{
    // Would call CGContextRotateCTM(context, radians)
  }-*/;

  private static native void scaleNative(Object context, double sx, double sy) /*-{
    // Would call CGContextScaleCTM(context, sx, sy)
  }-*/;

  private static native void transformNative(Object context, double a, double b, double c, double d, double tx, double ty) /*-{
    // Would call CGContextConcatCTM(context, CGAffineTransform{a, b, c, d, tx, ty})
  }-*/;

  private static native void fillRectNative(Object context, double x1, double y1, double x2, double y2) /*-{
    // Would call CGContextFillRect(context, CGRect{x1, y1, x2-x1, y2-y1})
  }-*/;

  private static native void strokeRectNative(Object context, double x1, double y1, double x2, double y2) /*-{
    // Would call CGContextStrokeRect(context, CGRect{x1, y1, x2-x1, y2-y1})
  }-*/;

  private static native void fillEllipseNative(Object context, double cx, double cy, double rx, double ry) /*-{
    // Would call CGContextFillEllipseInRect(context, CGRect{cx-rx, cy-ry, rx*2, ry*2})
  }-*/;

  private static native void strokeEllipseNative(Object context, double cx, double cy, double rx, double ry) /*-{
    // Would call CGContextStrokeEllipseInRect(context, CGRect{cx-rx, cy-ry, rx*2, ry*2})
  }-*/;

  private static native void drawLineNative(Object context, double x1, double y1, double x2, double y2) /*-{
    // Would call CGContextBeginPath, CGContextMoveToPoint, CGContextAddLineToPoint, CGContextStrokePath
  }-*/;

  private static native void drawTextNative(Object context, String text, double x, double y, double size, double r, double g, double b, double a) /*-{
    // Would use NSString and UIFont with drawAtPoint:withAttributes:
  }-*/;

  private static native double measureTextNative(Object context, String text, double size) /*-{
    // Would use NSString.boundingRectWithSize:options:attributes:context: to measure
    return 0.0;
  }-*/;

  private static native void clipRectNative(Object context, double x1, double y1, double x2, double y2) /*-{
    // Would call CGContextClipToRect(context, CGRect{x1, y1, x2-x1, y2-y1})
  }-*/;

  private static native void setBlendModeNative(Object context, String mode) /*-{
    // Would call CGContextSetBlendMode with kCGBlendModeNormal, kCGBlendModeMultiply, etc.
  }-*/;

  private static native void drawImageNative(Object context, Object image, double x, double y, double w, double h) /*-{
    // Would call [uiImage drawInRect:CGRect{x, y, w, h}]
  }-*/;

  private static native void drawImageRegionNative(Object context, Object image, double sx, double sy, double sw, double sh,
                                                     double dx, double dy, double dw, double dh) /*-{
    // Would extract source region from image and draw to destination rect
  }-*/;
}
