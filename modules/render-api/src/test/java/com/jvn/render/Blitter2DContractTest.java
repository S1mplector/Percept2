package com.jvn.render;

import com.jvn.core.scene2d.Blitter2D;
import com.jvn.core.scene2d.RenderBlendMode;
import com.jvn.core.scene2d.RenderFeature;
import com.jvn.core.scene2d.RendererCapabilities;
import com.jvn.testkit.render.Blitter2DContract;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class Blitter2DContractTest {

  @Test
  void recordingBlitterSatisfiesSharedSmokeContract() {
    Blitter2DContract.assertSmokeContract(RecordingBlitter::new);
  }

  @Test
  void imageRegionContractPreservesSourceAndDestinationRectangles() {
    RecordingBlitter b = new RecordingBlitter();

    b.drawImageRegion("atlas.png", 1, 2, 3, 4, 5, 6, 7, 8);

    assertTrue(
        b.calls.contains("drawImageRegion:atlas.png:1.0,2.0,3.0,4.0->5.0,6.0,7.0,8.0"),
        "drawImageRegion should preserve source and destination rectangles");
  }

  @Test
  void typedOperationsAreCheckedAndDispatched() {
    RecordingBlitter b = new RecordingBlitter();

    b.setBlendMode(RenderBlendMode.MULTIPLY);

    assertTrue(b.calls.contains("blend:multiply"));
    assertTrue(b.supports(RenderFeature.BLEND_MODES));
  }

  private static final class RecordingBlitter implements Blitter2D {
    private final List<String> calls = new ArrayList<>();
    private int depth;

    @Override public RendererCapabilities getCapabilities() {
      return RendererCapabilities.of(
          "Recording",
          RenderFeature.AFFINE_TRANSFORM,
          RenderFeature.RECTANGULAR_CLIP,
          RenderFeature.TEXT_ALIGNMENT,
          RenderFeature.BLEND_MODES);
    }

    @Override public void clear(double r, double g, double b, double a) {
      calls.add("clear");
    }

    @Override public void setFill(double r, double g, double b, double a) {
      calls.add("fill");
    }

    @Override public void setStroke(double r, double g, double b, double a) {
      calls.add("stroke");
    }

    @Override public void setStrokeWidth(double w) {
      calls.add("strokeWidth");
    }

    @Override public void setGlobalAlpha(double a) {
      calls.add("alpha");
    }

    @Override public void setFont(String family, double size, boolean bold) {
      calls.add("font");
    }

    @Override public void push() {
      depth++;
      calls.add("push");
    }

    @Override public void pop() {
      if (depth > 0) depth--;
      calls.add("pop");
    }

    @Override public void translate(double x, double y) {
      calls.add("translate");
    }

    @Override public void rotateDeg(double degrees) {
      calls.add("rotate");
    }

    @Override public void scale(double sx, double sy) {
      calls.add("scale");
    }

    @Override public void transform(double mxx, double myx, double mxy, double myy, double tx, double ty) {
      calls.add("transform");
    }

    @Override public void fillRect(double x, double y, double w, double h) {
      calls.add("fillRect");
    }

    @Override public void strokeRect(double x, double y, double w, double h) {
      calls.add("strokeRect");
    }

    @Override public void fillCircle(double cx, double cy, double radius) {
      calls.add("fillCircle");
    }

    @Override public void strokeCircle(double cx, double cy, double radius) {
      calls.add("strokeCircle");
    }

    @Override public void drawLine(double x1, double y1, double x2, double y2) {
      calls.add("line");
    }

    @Override public void drawImage(String classpath, double x, double y, double w, double h) {
      calls.add("drawImage:" + classpath);
    }

    @Override
    public void drawImageRegion(String classpath, double sx, double sy, double sw, double sh,
                                double dx, double dy, double dw, double dh) {
      calls.add("drawImageRegion:" + classpath + ":" + sx + "," + sy + "," + sw + "," + sh
          + "->" + dx + "," + dy + "," + dw + "," + dh);
    }

    @Override public void drawText(String text, double x, double y, double size, boolean bold) {
      calls.add("text");
    }

    @Override public double measureTextWidth(String text, double size, boolean bold) {
      return text == null ? 0.0 : text.length() * size * (bold ? 0.7 : 0.6);
    }

    @Override public void setClipRect(double x, double y, double w, double h) {
      calls.add("clip");
    }

    @Override public void setTextAlign(String hAlign, String vAlign) {
      calls.add("align");
    }

    @Override public void setBlendMode(String mode) {
      calls.add("blend:" + mode);
    }
  }
}
