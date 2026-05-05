package com.jvn.scripting.jes.runtime;

import com.jvn.core.scene2d.Blitter2D;
import com.jvn.core.scene2d.Entity2D;

/**
 * Simple horizontal slider that emits a call when value changes.
 */
public class Slider2D extends Entity2D {
  private double width;
  private double height;
  private double min = 0;
  private double max = 1;
  private double value = 0;
  private String call;
  private final java.util.Map<String,Object> props = new java.util.HashMap<>();
  private boolean dragging;

  private double[] trackColor = new double[]{0.18, 0.18, 0.18, 0.9};
  private double[] fillColor = new double[]{0.3, 0.6, 0.9, 0.9};
  private double[] knobColor = new double[]{0.9, 0.9, 0.9, 1.0};

  public Slider2D(double w, double h) { this.width = w; this.height = h; }

  public void setSize(double w, double h) { this.width = w; this.height = h; }
  public double getWidth() { return width; }
  public double getHeight() { return height; }

  public void setRange(double min, double max) { this.min = min; this.max = max; clamp(); }
  public void setValue(double v) { this.value = v; clamp(); }
  public double getValue() { return value; }

  public void setCall(String call) { this.call = call; }
  public String getCall() { return call; }
  public java.util.Map<String,Object> getProps() { return props; }

  public void setColors(double[] track, double[] fill, double[] knob) {
    if (track != null && track.length >= 4) this.trackColor = track;
    if (fill != null && fill.length >= 4) this.fillColor = fill;
    if (knob != null && knob.length >= 4) this.knobColor = knob;
  }

  public void setDragging(boolean dragging) { this.dragging = dragging; }
  public boolean isDragging() { return dragging; }

  public double valueToPixel(double v) {
    double t = (v - min) / (max - min);
    t = Math.max(0, Math.min(1, t));
    return t * width;
  }

  public void setFromPixel(double px) {
    double t = Math.max(0, Math.min(1, px / width));
    this.value = min + t * (max - min);
  }

  private void clamp() {
    if (max <= min) max = min + 1e-6;
    if (value < min) value = min;
    if (value > max) value = max;
  }

  @Override
  public void render(Blitter2D b) {
    b.push();
    // track
    b.setFill(trackColor[0], trackColor[1], trackColor[2], trackColor[3]);
    b.fillRect(0, height * 0.35, width, height * 0.3);
    // fill
    double fx = valueToPixel(value);
    b.setFill(fillColor[0], fillColor[1], fillColor[2], fillColor[3]);
    b.fillRect(0, height * 0.35, fx, height * 0.3);
    // knob
    double kw = Math.max(height * 0.6, 8);
    double kh = kw;
    double kx = fx - kw * 0.5;
    double ky = (height - kh) * 0.5;
    b.setFill(knobColor[0], knobColor[1], knobColor[2], knobColor[3]);
    b.fillRect(kx, ky, kw, kh);
    b.pop();
  }
}
