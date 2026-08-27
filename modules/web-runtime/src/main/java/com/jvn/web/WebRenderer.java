package com.jvn.web;

import java.util.ArrayDeque;
import java.util.Deque;
import org.teavm.jso.JSObject;
import org.teavm.jso.canvas.CanvasImageSource;
import org.teavm.jso.canvas.CanvasRenderingContext2D;
import org.teavm.jso.core.JSArray;
import org.teavm.jso.core.JSNumber;

import com.jvn.core.math.Capsule2;
import com.jvn.core.scene2d.Blitter2D;
import com.jvn.core.scene2d.RenderFeature;
import com.jvn.core.scene2d.RenderBlendMode;
import com.jvn.core.scene2d.RendererCapabilities;
import com.jvn.core.scene2d.RenderTarget2D;
import com.jvn.render.RenderSurface;

/**
 * HTML5 Canvas implementation of {@code Blitter2D} for web rendering.
 *
 * <p>This renderer draws to an HTML5 canvas element via TeaVM's JavaScript Object (JSO) bindings.</p>
 */
public class WebRenderer implements Blitter2D {
  public static final RendererCapabilities CAPABILITIES = RendererCapabilities.of(
      "Canvas 2D",
      RenderFeature.AFFINE_TRANSFORM,
      RenderFeature.VECTOR_PATHS,
      RenderFeature.ADVANCED_STROKE,
      RenderFeature.RECTANGULAR_CLIP,
      RenderFeature.POLYGONS,
      RenderFeature.TEXT_ALIGNMENT,
      RenderFeature.IMAGE_DIMENSIONS,
      RenderFeature.OFFSCREEN_RENDER_TARGETS,
      RenderFeature.PIXEL_ACCESS)
      .withBlendModes(
          RenderBlendMode.NORMAL,
          RenderBlendMode.ADDITIVE,
          RenderBlendMode.MULTIPLY,
          RenderBlendMode.SCREEN,
          RenderBlendMode.DESTINATION_IN);

  private final CanvasRenderingContext2D context;
  private final WebImageCache imageCache;
  private final double width;
  private final double height;
  private final Deque<RenderState> stateStack = new ArrayDeque<>();
  private RenderState state = new RenderState();

  private static class RenderState {
    String fillStyle = "#000000";
    String strokeStyle = "#000000";
    double lineWidth = 1.0;

    RenderState copy() {
      RenderState copy = new RenderState();
      copy.fillStyle = fillStyle;
      copy.strokeStyle = strokeStyle;
      copy.lineWidth = lineWidth;
      return copy;
    }
  }

  public WebRenderer(RenderSurface surface) {
    if (!(surface instanceof WebCanvasRenderSurface webSurface)) {
      throw new IllegalArgumentException("WebRenderer requires WebCanvasRenderSurface");
    }
    this.context = webSurface.getContext2D();
    this.width = surface.getWidth();
    this.height = surface.getHeight();
    this.context.scale(surface.getPixelScale(), surface.getPixelScale());
    this.imageCache = new WebImageCache();
  }

  /**
   * Offscreen constructor: builds a renderer directly around a detached canvas's context,
   * bypassing {@link WebCanvasRenderSurface}'s DOM-element-by-ID lookup (offscreen canvases
   * are never attached to the DOM). Shares the parent renderer's image cache so character-layer
   * images already loaded there don't need to reload per render target.
   */
  WebRenderer(CanvasRenderingContext2D context, double width, double height, double pixelScale,
      WebImageCache sharedImageCache) {
    this.context = context;
    this.width = width;
    this.height = height;
    this.context.scale(pixelScale, pixelScale);
    this.imageCache = sharedImageCache;
  }

  @Override
  public RendererCapabilities getCapabilities() { return CAPABILITIES; }

  @Override
  public void clear(double r, double g, double b, double a) {
    context.setFillStyle(rgbToCss(r, g, b, a));
    context.fillRect(0, 0, width, height);
    context.setFillStyle(state.fillStyle);
  }

  @Override
  public void setFill(double r, double g, double b, double a) {
    state.fillStyle = rgbToCss(r, g, b, a);
    context.setFillStyle(state.fillStyle);
  }

  @Override
  public void setStroke(double r, double g, double b, double a) {
    state.strokeStyle = rgbToCss(r, g, b, a);
    context.setStrokeStyle(state.strokeStyle);
  }

  @Override
  public void setStrokeWidth(double w) {
    state.lineWidth = w;
    context.setLineWidth(w);
  }

  @Override
  public void setGlobalAlpha(double a) {
    context.setGlobalAlpha(clamp01(a));
  }

  @Override
  public void setFont(String family, double size, boolean bold) {
    String font = (bold ? "bold " : "") + size + "px " + (family != null ? family : "Arial");
    context.setFont(font);
  }

  @Override
  public void push() {
    context.save();
    stateStack.push(state.copy());
  }

  @Override
  public void pop() {
    if (stateStack.isEmpty()) return;
    context.restore();
    state = stateStack.pop();
  }

  @Override
  public void translate(double x, double y) {
    context.translate(x, y);
  }

  @Override
  public void rotateDeg(double degrees) {
    context.rotate(Math.toRadians(degrees));
  }

  @Override
  public void scale(double sx, double sy) {
    context.scale(sx, sy);
  }

  @Override
  public void transform(double mxx, double myx, double mxy, double myy, double tx, double ty) {
    context.transform(mxx, myx, mxy, myy, tx, ty);
  }

  @Override
  public void fillRect(double x, double y, double w, double h) {
    context.fillRect(x, y, w, h);
  }

  @Override
  public void strokeRect(double x, double y, double w, double h) {
    context.strokeRect(x, y, w, h);
  }

  @Override
  public void fillCircle(double cx, double cy, double radius) {
    context.beginPath();
    context.arc(cx, cy, radius, 0, Math.PI * 2);
    context.fill();
  }

  @Override
  public void strokeCircle(double cx, double cy, double radius) {
    context.beginPath();
    context.arc(cx, cy, radius, 0, Math.PI * 2);
    context.stroke();
  }

  @Override
  public void drawLine(double x1, double y1, double x2, double y2) {
    context.beginPath();
    context.moveTo(x1, y1);
    context.lineTo(x2, y2);
    context.stroke();
  }

  @Override
  public void drawImage(String classpath, double x, double y, double w, double h) {
    if (classpath == null) return;
    CanvasImageSource img = imageCache.getOrLoad(classpath);
    if (img != null) {
      context.drawImage(img, x, y, w, h);
    }
  }

  @Override
  public void drawImageRegion(String classpath, double sx, double sy, double sw, double sh,
                              double dx, double dy, double dw, double dh) {
    if (classpath == null) return;
    CanvasImageSource img = imageCache.getOrLoad(classpath);
    if (img != null) {
      context.drawImage(img, sx, sy, sw, sh, dx, dy, dw, dh);
    }
  }

  @Override
  public java.util.Optional<double[]> imageDimensions(String classpath) {
    if (classpath == null) return java.util.Optional.empty();
    return imageCache.dimensionsOf(classpath);
  }

  @Override
  public RenderTarget2D createRenderTarget(double width, double height, double pixelScale) {
    return new WebRenderTarget2D(width, height, pixelScale, imageCache);
  }

  @Override
  public void drawRenderTarget(RenderTarget2D target, double x, double y, double width, double height) {
    if (!(target instanceof WebRenderTarget2D webTarget)) {
      throw new IllegalArgumentException("WebRenderer requires a web render target");
    }
    context.drawImage(webTarget.getCanvas(), x, y, width, height);
  }

  @Override
  public void drawText(String text, double x, double y, double size, boolean bold) {
    if (text == null) return;
    setFont("Arial", size, bold);
    context.fillText(text, x, y);
  }

  @Override
  public double measureTextWidth(String text, double size, boolean bold) {
    if (text == null) return 0;
    setFont("Arial", size, bold);
    return context.measureText(text).getWidth();
  }

  @Override
  public void setClipRect(double x, double y, double w, double h) {
    context.beginPath();
    context.rect(x, y, w, h);
    context.clip();
  }

  @Override
  public void setTextAlign(String hAlign, String vAlign) {
    if (hAlign != null) {
      context.setTextAlign(normalizeHorizontalTextAlign(hAlign));
    }
    if (vAlign != null) {
      context.setTextBaseline(normalizeVerticalTextAlign(vAlign));
    }
  }

  @Override
  public void setBlendMode(String mode) {
    if (mode != null) {
      String normalized = switch (mode.toLowerCase()) {
        case "normal" -> "source-over";
        case "add", "additive" -> "lighter";
        default -> mode.toLowerCase();
      };
      context.setGlobalCompositeOperation(normalized);
    }
  }

  @Override
  public void beginPath() {
    context.beginPath();
  }

  @Override
  public void moveTo(double x, double y) {
    context.moveTo(x, y);
  }

  @Override
  public void lineTo(double x, double y) {
    context.lineTo(x, y);
  }

  @Override
  public void closePath() {
    context.closePath();
  }

  @Override
  public void fillPath() {
    context.fill();
  }

  @Override
  public void strokePath() {
    context.stroke();
  }

  @Override
  public void setStrokeCap(String cap) {
    context.setLineCap(cap == null ? "square" : cap.toLowerCase());
  }

  @Override
  public void setStrokeJoin(String join) {
    context.setLineJoin(join == null ? "miter" : join.toLowerCase());
  }

  @Override
  public void setDash(double[] dashes, double phase) {
    double[] source = dashes == null ? new double[0] : dashes;
    JSArray<JSObject> values = new JSArray<>(source.length);
    for (int index = 0; index < source.length; index++) {
      values.set(index, JSNumber.valueOf(Math.max(0.0, source[index])));
    }
    context.setLineDash(values);
    context.setLineDashOffset(phase);
  }

  @Override
  public void fillPolygon(double[] xy) {
    if (drawPolygonPath(xy)) context.fill();
  }

  @Override
  public void strokePolygon(double[] xy) {
    if (drawPolygonPath(xy)) context.stroke();
  }

  @Override
  public void fillCapsule(Capsule2 capsule) {
    if (capsule == null) return;
    context.save();
    context.setStrokeStyle(state.fillStyle);
    context.setLineWidth(capsule.r * 2.0);
    context.setLineCap("round");
    drawLine(capsule.x1, capsule.y1, capsule.x2, capsule.y2);
    context.restore();
  }

  private boolean drawPolygonPath(double[] xy) {
    if (xy == null || xy.length < 6 || xy.length % 2 != 0) return false;
    context.beginPath();
    context.moveTo(xy[0], xy[1]);
    for (int i = 2; i < xy.length; i += 2) {
      context.lineTo(xy[i], xy[i + 1]);
    }
    context.closePath();
    return true;
  }

  static String rgbToCss(double r, double g, double b, double a) {
    int red = colorChannel(r);
    int green = colorChannel(g);
    int blue = colorChannel(b);
    double alpha = clamp01(a);
    if (alpha < 1.0) return "rgba(" + red + "," + green + "," + blue + "," + alpha + ")";
    return "rgb(" + red + "," + green + "," + blue + ")";
  }

  private static int colorChannel(double value) {
    return (int) Math.round(clamp01(value) * 255.0);
  }

  private static double clamp01(double value) {
    if (!Double.isFinite(value)) return 0.0;
    return Math.max(0.0, Math.min(1.0, value));
  }

  private static String normalizeHorizontalTextAlign(String align) {
    return switch (align.toLowerCase()) {
      case "left", "center", "right", "start", "end" -> align.toLowerCase();
      default -> "left";
    };
  }

  private static String normalizeVerticalTextAlign(String align) {
    return switch (align.toLowerCase()) {
      case "top", "hanging", "middle", "alphabetic", "ideographic", "bottom" ->
          align.toLowerCase();
      case "center" -> "middle";
      default -> "alphabetic";
    };
  }
}
