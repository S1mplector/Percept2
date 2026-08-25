package com.jvn.scenerender.testkit;

import java.util.ArrayList;
import java.util.List;

import com.jvn.core.math.Capsule2;
import com.jvn.core.scene2d.Blitter2D;
import com.jvn.core.scene2d.RenderBlendMode;
import com.jvn.core.scene2d.RenderFeature;
import com.jvn.core.scene2d.RenderTarget2D;
import com.jvn.core.scene2d.RendererCapabilities;

public class RecordingBlitter2D implements Blitter2D {
  private final List<DrawCall> calls = new ArrayList<>();
  private static final RendererCapabilities CAPABILITIES = RendererCapabilities.of(
      "Recording Test Double",
      RenderFeature.AFFINE_TRANSFORM,
      RenderFeature.VECTOR_PATHS,
      RenderFeature.ADVANCED_STROKE,
      RenderFeature.RECTANGULAR_CLIP,
      RenderFeature.POLYGONS,
      RenderFeature.TEXT_ALIGNMENT,
      RenderFeature.LINEAR_GRADIENT,
      RenderFeature.RADIAL_GRADIENT,
      RenderFeature.BLUR,
      RenderFeature.OFFSCREEN_RENDER_TARGETS,
      RenderFeature.ALPHA_MASKS,
      RenderFeature.COLOR_MATRIX,
      RenderFeature.ARCS,
      RenderFeature.BLEND_MODES,
      RenderFeature.TEXT_LAYOUT)
      .withBlendModes(
          RenderBlendMode.NORMAL,
          RenderBlendMode.ADDITIVE,
          RenderBlendMode.MULTIPLY,
          RenderBlendMode.SCREEN,
          RenderBlendMode.DESTINATION_IN);

  public List<DrawCall> calls() {
    return List.copyOf(calls);
  }

  public void clearCalls() {
    calls.clear();
  }

  private void record(String method, Object... args) {
    calls.add(new DrawCall(method, List.of(args)));
  }

  @Override public RendererCapabilities getCapabilities() { return CAPABILITIES; }

  @Override public void clear(double r, double g, double b, double a) { record("clear", r, g, b, a); }
  @Override public void setFill(double r, double g, double b, double a) { record("setFill", r, g, b, a); }
  @Override public void setStroke(double r, double g, double b, double a) { record("setStroke", r, g, b, a); }
  @Override public void setStrokeWidth(double w) { record("setStrokeWidth", w); }
  @Override public void setGlobalAlpha(double a) { record("setGlobalAlpha", a); }
  @Override public void setFont(String family, double size, boolean bold) { record("setFont", family, size, bold); }
  @Override public void push() { record("push"); }
  @Override public void pop() { record("pop"); }
  @Override public void translate(double x, double y) { record("translate", x, y); }
  @Override public void rotateDeg(double degrees) { record("rotateDeg", degrees); }
  @Override public void scale(double sx, double sy) { record("scale", sx, sy); }
  @Override public void transform(double mxx, double myx, double mxy, double myy, double tx, double ty) {
    record("transform", mxx, myx, mxy, myy, tx, ty);
  }
  @Override public void fillRect(double x, double y, double w, double h) { record("fillRect", x, y, w, h); }
  @Override public void strokeRect(double x, double y, double w, double h) { record("strokeRect", x, y, w, h); }
  @Override public void fillCircle(double cx, double cy, double radius) { record("fillCircle", cx, cy, radius); }
  @Override public void strokeCircle(double cx, double cy, double radius) { record("strokeCircle", cx, cy, radius); }
  @Override public void drawLine(double x1, double y1, double x2, double y2) { record("drawLine", x1, y1, x2, y2); }
  @Override public void drawImage(String classpath, double x, double y, double w, double h) {
    record("drawImage", classpath, x, y, w, h);
  }
  @Override public void drawImageRegion(String classpath, double sx, double sy, double sw, double sh,
      double dx, double dy, double dw, double dh) {
    record("drawImageRegion", classpath, sx, sy, sw, sh, dx, dy, dw, dh);
  }
  @Override public void drawText(String text, double x, double y, double size, boolean bold) {
    record("drawText", text, x, y, size, bold);
  }
  @Override public double measureTextWidth(String text, double size, boolean bold) {
    record("measureTextWidth", text, size, bold);
    return text == null ? 0.0 : text.length() * size * 0.6;
  }
  @Override public void beginPath() { record("beginPath"); }
  @Override public void moveTo(double x, double y) { record("moveTo", x, y); }
  @Override public void lineTo(double x, double y) { record("lineTo", x, y); }
  @Override public void closePath() { record("closePath"); }
  @Override public void fillPath() { record("fillPath"); }
  @Override public void strokePath() { record("strokePath"); }
  @Override public void setStrokeCap(String cap) { record("setStrokeCap", cap); }
  @Override public void setStrokeJoin(String join) { record("setStrokeJoin", join); }
  @Override public void setMiterLimit(double limit) { record("setMiterLimit", limit); }
  @Override public void setDash(double[] dashes, double phase) { record("setDash", dashes, phase); }
  @Override public void setClipRect(double x, double y, double w, double h) { record("setClipRect", x, y, w, h); }
  @Override public void fillPolygon(double[] xy) { record("fillPolygon", (Object) xy); }
  @Override public void strokePolygon(double[] xy) { record("strokePolygon", (Object) xy); }
  @Override public void fillArc(double cx, double cy, double r, double startDeg, double sweepDeg) {
    record("fillArc", cx, cy, r, startDeg, sweepDeg);
  }
  @Override public void strokeArc(double cx, double cy, double r, double startDeg, double sweepDeg) {
    record("strokeArc", cx, cy, r, startDeg, sweepDeg);
  }
  @Override public void setFillLinearGradient(double x1, double y1, double x2, double y2,
      double[] positions, double[] colorsRgba) {
    record("setFillLinearGradient", x1, y1, x2, y2, positions, colorsRgba);
  }
  @Override public void setFillRadialGradient(double cx, double cy, double r,
      double[] positions, double[] colorsRgba) {
    record("setFillRadialGradient", cx, cy, r, positions, colorsRgba);
  }
  @Override public void setTextAlign(String hAlign, String vAlign) { record("setTextAlign", hAlign, vAlign); }
  @Override public void setBlendMode(String mode) { record("setBlendMode", mode); }
  @Override public void setColorMatrix(double[] matrix) { record("setColorMatrix", (Object) matrix); }
  @Override public void clearColorMatrix() { record("clearColorMatrix"); }
  @Override public void setBlurRadius(double radius) { record("setBlurRadius", radius); }
  @Override public void fillCapsule(Capsule2 capsule) { record("fillCapsule", capsule); }
  @Override public void strokeCapsule(Capsule2 capsule) { record("strokeCapsule", capsule); }

  @Override
  public RenderTarget2D createRenderTarget(double width, double height, double pixelScale) {
    record("createRenderTarget", width, height, pixelScale);
    return new RecordingRenderTarget2D(width, height, pixelScale, new RecordingBlitter2D());
  }

  @Override
  public void drawRenderTarget(RenderTarget2D target, double x, double y, double width, double height) {
    record("drawRenderTarget", target, x, y, width, height);
  }

  @Override
  public void applyAlphaMask(RenderTarget2D mask) {
    record("applyAlphaMask", mask);
  }

  private static final class RecordingRenderTarget2D implements RenderTarget2D {
    private final double width;
    private final double height;
    private final double pixelScale;
    private final Blitter2D blitter;
    private boolean valid = true;

    RecordingRenderTarget2D(double width, double height, double pixelScale, Blitter2D blitter) {
      this.width = width;
      this.height = height;
      this.pixelScale = pixelScale;
      this.blitter = blitter;
    }

    @Override public double getWidth() { return width; }
    @Override public double getHeight() { return height; }
    @Override public double getPixelScale() { return pixelScale; }
    @Override public Blitter2D getBlitter() { return blitter; }
    @Override public boolean isValid() { return valid; }
    @Override public void dispose() { valid = false; }
  }
}
