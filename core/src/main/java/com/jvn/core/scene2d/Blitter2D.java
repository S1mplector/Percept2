package com.jvn.core.scene2d;

public interface Blitter2D {
  void clear(double r, double g, double b, double a);

  void setFill(double r, double g, double b, double a);
  void setStroke(double r, double g, double b, double a);
  void setStrokeWidth(double w);
  void setGlobalAlpha(double a);
  void setFont(String family, double size, boolean bold);

  void push();
  void pop();
  void translate(double x, double y);
  void rotateDeg(double degrees);
  void scale(double sx, double sy);

  void fillRect(double x, double y, double w, double h);
  void strokeRect(double x, double y, double w, double h);

  void fillCircle(double cx, double cy, double radius);
  void strokeCircle(double cx, double cy, double radius);

  void drawLine(double x1, double y1, double x2, double y2);

  void drawImage(String classpath, double x, double y, double w, double h);
  void drawImageRegion(String classpath, double sx, double sy, double sw, double sh,
                       double dx, double dy, double dw, double dh);

  void drawText(String text, double x, double y, double size, boolean bold);
  double measureTextWidth(String text, double size, boolean bold);
}

