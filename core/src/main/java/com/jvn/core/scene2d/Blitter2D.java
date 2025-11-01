package com.jvn.core.scene2d;

public interface Blitter2D {
  void clear(double r, double g, double b, double a);

  void setFill(double r, double g, double b, double a);
  void setStroke(double r, double g, double b, double a);
  void setStrokeWidth(double w);

  void fillRect(double x, double y, double w, double h);
  void strokeRect(double x, double y, double w, double h);

  void fillCircle(double cx, double cy, double radius);
  void strokeCircle(double cx, double cy, double radius);

  void drawLine(double x1, double y1, double x2, double y2);

  void drawImage(String classpath, double x, double y, double w, double h);

  void drawText(String text, double x, double y, double size, boolean bold);
}
