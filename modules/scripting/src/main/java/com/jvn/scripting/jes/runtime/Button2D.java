package com.jvn.scripting.jes.runtime;

import com.jvn.core.scene2d.Blitter2D;
import com.jvn.core.scene2d.Entity2D;

/**
 * Simple rectangular button with hover/press states that fires a JES call.
 */
public class Button2D extends Entity2D {
  private double width;
  private double height;
  private String text = "";
  private String call;
  private final java.util.Map<String,Object> props = new java.util.HashMap<>();

  private double[] normal = new double[]{0.18, 0.18, 0.18, 0.9};
  private double[] hover = new double[]{0.24, 0.24, 0.24, 0.95};
  private double[] pressed = new double[]{0.3, 0.3, 0.3, 1.0};
  private double[] textColor = new double[]{0.9, 0.9, 0.9, 1.0};
  private double fontSize = 14;

  private boolean hovered;
  private boolean down;

  public Button2D(double w, double h) {
    this.width = w;
    this.height = h;
  }

  public void setSize(double w, double h) { this.width = w; this.height = h; }
  public double getWidth() { return width; }
  public double getHeight() { return height; }

  public void setText(String t) { this.text = t == null ? "" : t; }
  public String getText() { return text; }

  public void setCall(String call) { this.call = call; }
  public String getCall() { return call; }
  public java.util.Map<String,Object> getProps() { return props; }

  public void setColors(double[] normal, double[] hover, double[] pressed, double[] textColor) {
    if (normal != null && normal.length >= 4) this.normal = normal;
    if (hover != null && hover.length >= 4) this.hover = hover;
    if (pressed != null && pressed.length >= 4) this.pressed = pressed;
    if (textColor != null && textColor.length >= 4) this.textColor = textColor;
  }

  public void setFontSize(double size) { this.fontSize = size; }
  public void setHovered(boolean h) { this.hovered = h; }
  public void setDown(boolean d) { this.down = d; }
  public boolean isHovered() { return hovered; }
  public boolean isDown() { return down; }

  @Override
  public void render(Blitter2D b) {
    double[] c = down ? pressed : (hovered ? hover : normal);
    b.push();
    b.setFill(c[0], c[1], c[2], c[3]);
    b.fillRect(0, 0, width, height);
    // text centered
    if (text != null && !text.isBlank()) {
      b.setFill(textColor[0], textColor[1], textColor[2], textColor[3]);
      b.setFont("SansSerif", fontSize, false);
      double tw = b.measureTextWidth(text, fontSize, false);
      double tx = (width - tw) * 0.5;
      double ty = (height + fontSize * 0.5) * 0.5;
      b.drawText(text, tx, ty, fontSize, false);
    }
    b.pop();
  }
}
