package com.jvn.core.scene2d;

public class Label2D extends Entity2D {
  private String text;
  private double size = 16;
  private boolean bold = false;
  private String fontFamily = "Arial";
  private double r = 1, g = 1, blue = 1, a = 1;
  public enum Align { LEFT, CENTER, RIGHT }
  private Align align = Align.LEFT;

  public Label2D(String text) { this.text = text; }

  public String getText() { return text; }
  public void setText(String t) { this.text = t; }
  public void setColor(double r, double g, double b, double a) { this.r = r; this.g = g; this.blue = b; this.a = a; }
  public void setFont(String family, double size, boolean bold) { this.fontFamily = family; this.size = size; this.bold = bold; }
  public void setAlign(Align a) { this.align = a == null ? Align.LEFT : a; }

  @Override
  public void render(Blitter2D b) {
    if (text == null) return;
    b.push();
    b.setGlobalAlpha(a);
    b.setFill(r, g, blue, 1);
    b.setFont(fontFamily, size, bold);
    double w = b.measureTextWidth(text, size, bold);
    double ox = 0;
    if (align == Align.CENTER) ox = -w / 2.0;
    else if (align == Align.RIGHT) ox = -w;
    b.drawText(text, ox, 0, size, bold);
    b.pop();
  }
}
