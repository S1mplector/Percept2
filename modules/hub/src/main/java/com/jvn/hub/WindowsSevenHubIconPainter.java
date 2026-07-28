package com.jvn.hub;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Windows 7-era chrome artwork for the Engine Hub.
 *
 * <p>The restrained material palette is deliberately shared by every command:
 * black, white, neutral silver, and small JVN-orange functional highlights.</p>
 */
final class WindowsSevenHubIconPainter {
  private static final Color BLACK = Color.decode("#070809");
  private static final Color GRAPHITE = Color.decode("#24282b");
  private static final Color SILVER_DARK = Color.decode("#60666a");
  private static final Color SILVER = Color.decode("#aeb5b9");
  private static final Color SILVER_LIGHT = Color.decode("#e1e6e8");
  private static final Color WHITE = Color.decode("#ffffff");
  private static final Color ORANGE_DARK = Color.decode("#a94708");
  private static final Color ORANGE = Color.decode("#ff932e");
  private static final Color ORANGE_LIGHT = Color.decode("#ffd09a");

  private WindowsSevenHubIconPainter() {}

  static boolean paint(Graphics2D source, String kind) {
    if (source == null || kind == null || kind.isBlank()) return false;
    Graphics2D g = (Graphics2D) source.create();
    try {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
      g.setStroke(stroke(1.2f));
      switch (kind) {
        case "EDITOR" -> paintEditor(g);
        case "LAUNCHER" -> paintLauncher(g, false);
        case "LAUNCHER_MAINTENANCE" -> paintLauncher(g, true);
        case "BUILD" -> paintBuild(g);
        case "SHORTCUT" -> paintShortcut(g);
        case "UPDATE" -> paintUpdate(g);
        case "TESTS" -> paintTests(g);
        case "OPTIONS" -> paintOptions(g);
        case "MORE" -> paintMore(g);
        case "CANCEL" -> paintCancel(g);
        case "QUIT" -> paintQuit(g);
        case "DEVELOPER" -> paintDeveloper(g, false);
        case "DEVELOPER_ACTIVE" -> paintDeveloper(g, true);
        case "SAFE" -> paintSafe(g, false);
        case "SAFE_ACTIVE" -> paintSafe(g, true);
        case "DIAGNOSTICS" -> paintDiagnostics(g);
        case "ABOUT" -> paintAbout(g);
        case "DOCUMENTATION" -> paintDocumentation(g);
        default -> {
          return false;
        }
      }
      return true;
    } finally {
      g.dispose();
    }
  }

  private static void paintEditor(Graphics2D g) {
    chromeTile(g, 1.5f, 2f, 21f, 15.5f, 3.4f);
    g.setPaint(new LinearGradientPaint(0, 5, 0, 16,
        new float[] {0f, 0.2f, 1f},
        new Color[] {GRAPHITE, BLACK, Color.decode("#121416")}));
    g.fill(new RoundRectangle2D.Float(3.5f, 4.1f, 17f, 11.2f, 2.1f, 2.1f));
    g.setColor(SILVER_DARK);
    g.draw(new RoundRectangle2D.Float(3.5f, 4.1f, 17f, 11.2f, 2.1f, 2.1f));
    g.setColor(ORANGE);
    g.setStroke(stroke(1.45f));
    Path2D code = new Path2D.Float();
    code.moveTo(8.2, 7.1); code.lineTo(5.8, 9.7); code.lineTo(8.2, 12.3);
    code.moveTo(12.1, 7.1); code.lineTo(14.5, 9.7); code.lineTo(12.1, 12.3);
    g.draw(code);
    g.setColor(WHITE);
    line(g, 10.9f, 6.8f, 9.5f, 12.6f);
    g.setPaint(new LinearGradientPaint(0, 16, 0, 22,
        new float[] {0f, 0.55f, 1f}, new Color[] {WHITE, SILVER, SILVER_DARK}));
    g.fillRoundRect(9, 16, 6, 3, 2, 2);
    g.fillRoundRect(5, 19, 14, 3, 3, 3);
    indicator(g, 18.2f, 13.2f, 1.15f);
  }

  private static void paintLauncher(Graphics2D g, boolean maintenance) {
    chromeOrb(g);
    Path2D rocket = new Path2D.Float();
    rocket.moveTo(13.3, 4.2);
    rocket.curveTo(17.4, 5.7, 19.1, 8.7, 18.7, 12.1);
    rocket.lineTo(14.2, 16.7);
    rocket.lineTo(10.1, 16.2);
    rocket.lineTo(6.6, 19.5);
    rocket.lineTo(7.1, 14.1);
    rocket.lineTo(11.5, 9.8);
    rocket.curveTo(11.7, 7.2, 12.1, 5.2, 13.3, 4.2);
    rocket.closePath();
    g.setPaint(new LinearGradientPaint(7, 5, 18, 18,
        new float[] {0f, 0.45f, 1f}, new Color[] {WHITE, SILVER_LIGHT, SILVER_DARK}));
    g.fill(rocket);
    g.setColor(BLACK);
    g.draw(rocket);
    g.setPaint(new RadialGradientPaint(new Point2D.Float(15.2f, 9.2f), 2.6f,
        new float[] {0f, 0.42f, 1f}, new Color[] {ORANGE_LIGHT, ORANGE, ORANGE_DARK}));
    g.fillOval(13, 7, 4, 4);
    g.setColor(WHITE);
    g.drawOval(13, 7, 4, 4);
    Path2D flame = new Path2D.Float();
    flame.moveTo(7.8, 16.6); flame.lineTo(6.4, 22.3); flame.lineTo(11.2, 18.2); flame.closePath();
    g.setPaint(new LinearGradientPaint(7, 17, 7, 23,
        new float[] {0f, 0.55f, 1f}, new Color[] {ORANGE_LIGHT, ORANGE, ORANGE_DARK}));
    g.fill(flame);
    if (maintenance) {
      g.setColor(ORANGE);
      g.fillOval(2, 2, 7, 7);
      g.setColor(BLACK);
      g.setStroke(stroke(1.35f));
      line(g, 5.5f, 3.4f, 5.5f, 6.2f);
      g.fillOval(5, 7, 1, 1);
    }
  }

  private static void paintBuild(Graphics2D g) {
    chromeTile(g, 1.5f, 7f, 21f, 14.5f, 4f);
    g.setColor(BLACK);
    g.fillRoundRect(3, 10, 17, 9, 2, 2);
    g.setPaint(new LinearGradientPaint(0, 10, 0, 19,
        new float[] {0f, 0.5f, 1f}, new Color[] {SILVER_LIGHT, SILVER, SILVER_DARK}));
    for (int row = 0; row < 2; row++) {
      for (int col = 0; col < 3; col++) {
        g.fillRoundRect(4 + col * 5, 11 + row * 4, 4, 3, 1, 1);
      }
    }
    g.setColor(ORANGE);
    g.fillRoundRect(14, 15, 4, 3, 1, 1);
    Graphics2D tool = (Graphics2D) g.create();
    tool.rotate(-0.70, 15, 8);
    tool.setPaint(new LinearGradientPaint(10, 3, 20, 11,
        new float[] {0f, 0.48f, 1f}, new Color[] {WHITE, SILVER, GRAPHITE}));
    tool.fillRoundRect(11, 2, 11, 5, 2, 2);
    tool.setColor(BLACK);
    tool.drawRoundRect(11, 2, 10, 4, 2, 2);
    tool.setPaint(new LinearGradientPaint(0, 7, 0, 20,
        new float[] {0f, 0.35f, 1f}, new Color[] {ORANGE_LIGHT, ORANGE, ORANGE_DARK}));
    tool.fillRoundRect(15, 6, 3, 14, 2, 2);
    tool.dispose();
  }

  private static void paintShortcut(Graphics2D g) {
    shadow(g, 3, 2, 18, 18, 4);
    g.setPaint(chromeGradient(2, 20));
    g.fillRoundRect(3, 2, 17, 18, 4, 4);
    g.setColor(WHITE);
    g.drawRoundRect(3, 2, 16, 17, 4, 4);
    g.setColor(BLACK);
    g.fillRoundRect(5, 5, 12, 11, 2, 2);
    g.setColor(SILVER);
    g.setStroke(stroke(1.25f));
    g.drawLine(7, 8, 14, 8);
    g.drawLine(7, 11, 12, 11);
    g.setColor(ORANGE);
    g.setStroke(stroke(2.0f));
    g.drawLine(9, 18, 19, 8);
    g.drawLine(14, 8, 19, 8);
    g.drawLine(19, 8, 19, 13);
    indicator(g, 4.4f, 18.7f, 1.25f);
  }

  private static void paintUpdate(Graphics2D g) {
    chromeOrb(g);
    g.setColor(BLACK);
    g.setStroke(stroke(2.5f));
    g.draw(new Arc2D.Float(5, 5, 14, 14, 28, 140, Arc2D.OPEN));
    g.fillPolygon(new int[] {5, 4, 9}, new int[] {8, 14, 11}, 3);
    g.setColor(ORANGE);
    g.draw(new Arc2D.Float(5, 5, 14, 14, 208, 140, Arc2D.OPEN));
    g.fillPolygon(new int[] {19, 20, 15}, new int[] {16, 10, 13}, 3);
    g.setColor(WHITE);
    g.setStroke(stroke(0.85f));
    g.drawArc(6, 6, 12, 12, 210, 135);
  }

  private static void paintTests(Graphics2D g) {
    chromeTile(g, 3, 2, 18, 20, 3.5f);
    g.setColor(BLACK);
    g.fillRoundRect(5, 5, 14, 14, 2, 2);
    g.setPaint(new LinearGradientPaint(0, 6, 0, 18,
        new float[] {0f, 1f}, new Color[] {WHITE, SILVER_LIGHT}));
    g.fillRoundRect(6, 6, 12, 12, 1, 1);
    g.setColor(GRAPHITE);
    g.setStroke(stroke(1.05f));
    g.drawLine(12, 9, 16, 9);
    g.drawLine(12, 14, 16, 14);
    g.setColor(ORANGE);
    g.setStroke(stroke(1.6f));
    line(g, 7.5f, 9, 9, 10.5f); line(g, 9, 10.5f, 11, 8);
    line(g, 7.5f, 14, 9, 15.5f); line(g, 9, 15.5f, 11, 13);
    g.setPaint(chromeGradient(2, 7));
    g.fillRoundRect(8, 2, 8, 5, 2, 2);
  }

  private static void paintOptions(Graphics2D g) {
    chromeTile(g, 2, 3, 20, 18, 4f);
    int[] ys = {8, 12, 16};
    int[] knobs = {9, 16, 12};
    for (int i = 0; i < ys.length; i++) {
      g.setColor(BLACK);
      g.setStroke(stroke(2.2f));
      g.drawLine(5, ys[i], 19, ys[i]);
      g.setPaint(new RadialGradientPaint(new Point2D.Float(knobs[i] - 0.6f, ys[i] - 0.8f), 3.2f,
          new float[] {0f, 0.45f, 1f}, new Color[] {ORANGE_LIGHT, ORANGE, ORANGE_DARK}));
      g.fillOval(knobs[i] - 2, ys[i] - 2, 4, 4);
      g.setColor(WHITE);
      g.drawOval(knobs[i] - 2, ys[i] - 2, 3, 3);
    }
  }

  private static void paintMore(Graphics2D g) {
    chromeTile(g, 2, 3, 20, 18, 4f);
    for (int i = 0; i < 3; i++) {
      int y = 7 + i * 5;
      g.setColor(BLACK);
      g.fillRoundRect(5, y - 1, 14, 3, 3, 3);
      g.setColor(SILVER_LIGHT);
      g.drawLine(8, y, 17, y);
      indicator(g, 6.2f + i * 4.8f, y, 1.15f);
    }
  }

  private static void paintCancel(Graphics2D g) {
    shadow(g, 4.5f, 4.5f, 15f, 15f, 4.5f);
    g.setPaint(new LinearGradientPaint(0, 4.5f, 0, 19.5f,
        new float[] {0f, 0.16f, 0.52f, 1f},
        new Color[] {WHITE, SILVER_LIGHT, SILVER_DARK, BLACK}));
    g.fill(new RoundRectangle2D.Float(4.5f, 4.5f, 15f, 15f, 4.5f, 4.5f));
    g.setColor(WHITE);
    g.setStroke(stroke(0.9f));
    g.draw(new RoundRectangle2D.Float(4.5f, 4.5f, 14.4f, 14.4f, 4.5f, 4.5f));
    g.setColor(BLACK);
    g.fillRoundRect(7, 7, 10, 10, 3, 3);
    g.setColor(SILVER_DARK);
    g.drawRoundRect(7, 7, 9, 9, 3, 3);
    g.setPaint(new LinearGradientPaint(6, 10, 18, 14,
        new float[] {0f, 0.5f, 1f}, new Color[] {ORANGE_DARK, ORANGE_LIGHT, ORANGE}));
    g.fill(new RoundRectangle2D.Float(9, 10.5f, 6, 3, 3, 3));
  }

  private static void paintQuit(Graphics2D g) {
    shadow(g, 4, 4, 16, 16, 16);
    g.setPaint(new RadialGradientPaint(new Point2D.Float(8f, 6.5f), 17f,
        new float[] {0f, 0.34f, 0.70f, 1f},
        new Color[] {WHITE, SILVER_LIGHT, SILVER_DARK, BLACK}));
    g.fill(new Ellipse2D.Float(4, 4, 16, 16));
    g.setColor(WHITE);
    g.setStroke(stroke(0.9f));
    g.draw(new Ellipse2D.Float(4, 4, 15.4f, 15.4f));
    g.setColor(BLACK);
    g.fillOval(6, 6, 12, 12);
    g.setColor(SILVER_DARK);
    g.drawOval(6, 6, 11, 11);
    g.setColor(ORANGE);
    g.setStroke(stroke(2.15f));
    g.draw(new Arc2D.Float(7.5f, 7.5f, 9, 9, 42, 276, Arc2D.OPEN));
    line(g, 12, 5.5f, 12, 11.5f);
  }

  private static void paintDeveloper(Graphics2D g, boolean active) {
    chromeOrb(g);
    g.setColor(BLACK);
    g.fillOval(5, 5, 14, 14);
    g.setStroke(stroke(1.75f));
    g.setColor(SILVER_LIGHT);
    g.drawLine(9, 7, 6, 12); g.drawLine(6, 12, 9, 17);
    g.drawLine(15, 7, 18, 12); g.drawLine(18, 12, 15, 17);
    g.setColor(ORANGE);
    g.drawLine(14, 6, 10, 18);
    if (active) {
      g.setStroke(stroke(1.6f));
      g.drawArc(2, 2, 20, 20, 205, 245);
      indicator(g, 19.7f, 5.3f, 1.25f);
    }
  }

  private static void paintSafe(Graphics2D g, boolean active) {
    shadow(g, 3, 2, 18, 20, 7);
    Path2D shield = new Path2D.Float();
    shield.moveTo(12, 2);
    shield.curveTo(16, 5, 19, 5, 21, 6);
    shield.lineTo(20, 13);
    shield.curveTo(19, 18, 15, 21, 12, 22);
    shield.curveTo(9, 21, 5, 18, 4, 13);
    shield.lineTo(3, 6);
    shield.curveTo(7, 5, 9, 4, 12, 2);
    shield.closePath();
    g.setPaint(new LinearGradientPaint(4, 3, 19, 22,
        new float[] {0f, 0.28f, 0.65f, 1f},
        new Color[] {WHITE, SILVER_LIGHT, SILVER, GRAPHITE}));
    g.fill(shield);
    g.setColor(BLACK);
    g.draw(shield);
    Path2D inset = new Path2D.Float();
    inset.moveTo(12, 5); inset.lineTo(18, 8); inset.lineTo(17, 13);
    inset.curveTo(16, 16, 14, 18, 12, 19); inset.curveTo(9, 17, 7, 15, 7, 12);
    inset.lineTo(6, 8); inset.closePath();
    g.setColor(BLACK);
    g.fill(inset);
    g.setColor(active ? ORANGE : SILVER_LIGHT);
    g.setStroke(stroke(2f));
    line(g, 8.5f, 12, 11, 14.5f); line(g, 11, 14.5f, 16, 9.5f);
    if (active) indicator(g, 18.8f, 5.5f, 1.1f);
  }

  private static void paintDiagnostics(Graphics2D g) {
    chromeTile(g, 2, 3, 20, 18, 4f);
    g.setColor(BLACK);
    g.fillRoundRect(4, 6, 16, 11, 2, 2);
    g.setColor(SILVER_DARK);
    g.drawRoundRect(4, 6, 15, 10, 2, 2);
    g.setColor(ORANGE);
    g.setStroke(stroke(1.65f));
    Path2D pulse = new Path2D.Float();
    pulse.moveTo(5.5, 12); pulse.lineTo(8.5, 12); pulse.lineTo(10.3, 8.2);
    pulse.lineTo(13.1, 15.5); pulse.lineTo(15.1, 10.8); pulse.lineTo(18.5, 10.8);
    g.draw(pulse);
    indicator(g, 18.7f, 5.2f, 0.9f);
  }

  private static void paintAbout(Graphics2D g) {
    chromeOrb(g);
    g.setColor(BLACK);
    g.fillOval(6, 6, 12, 12);
    g.setColor(SILVER_DARK);
    g.drawOval(6, 6, 11, 11);
    g.setColor(SILVER_LIGHT);
    g.setStroke(stroke(2.25f));
    g.drawLine(12, 11, 12, 16);
    g.setColor(ORANGE);
    g.fill(new Ellipse2D.Float(10.7f, 7.5f, 2.6f, 2.6f));
    g.setColor(WHITE);
    g.fill(new Ellipse2D.Float(11.1f, 7.8f, 0.8f, 0.8f));
  }

  private static void paintDocumentation(Graphics2D g) {
    shadow(g, 2, 4, 20, 17, 3);
    Path2D book = new Path2D.Float();
    book.moveTo(2, 5); book.curveTo(6, 3.5, 9, 4, 12, 6);
    book.curveTo(15, 4, 18, 3.5, 22, 5); book.lineTo(21, 20);
    book.curveTo(17, 18.5, 15, 19, 12, 21); book.curveTo(9, 19, 7, 18.5, 3, 20);
    book.closePath();
    g.setPaint(new LinearGradientPaint(2, 4, 22, 21,
        new float[] {0f, 0.42f, 0.72f, 1f},
        new Color[] {WHITE, SILVER_LIGHT, SILVER, GRAPHITE}));
    g.fill(book);
    g.setColor(BLACK);
    g.draw(book);
    g.drawLine(12, 6, 12, 20);
    g.setStroke(stroke(0.9f));
    g.drawLine(5, 9, 10, 9); g.drawLine(5, 12, 10, 12);
    g.drawLine(14, 9, 19, 9); g.drawLine(14, 12, 19, 12);
    g.setColor(ORANGE);
    g.fillPolygon(new int[] {14, 17, 17, 15}, new int[] {14, 14, 21, 19}, 4);
  }

  private static void chromeTile(Graphics2D g, float x, float y, float width, float height, float arc) {
    shadow(g, x, y, width, height, arc);
    g.setPaint(chromeGradient(y, y + height));
    g.fill(new RoundRectangle2D.Float(x, y, width, height, arc, arc));
    g.setColor(WHITE);
    g.draw(new RoundRectangle2D.Float(x, y, width - 0.6f, height - 0.6f, arc, arc));
    g.setColor(new Color(0, 0, 0, 150));
    g.draw(new RoundRectangle2D.Float(x + 1, y + 1, width - 2.5f, height - 2.5f, arc - 0.8f, arc - 0.8f));
    glassHighlight(g, x + 1, y + 1, width - 2, Math.max(4, height * 0.44f), arc - 0.5f);
  }

  private static void chromeOrb(Graphics2D g) {
    shadow(g, 3, 3, 18, 18, 18);
    g.setPaint(new RadialGradientPaint(new Point2D.Float(8f, 6f), 19f,
        new float[] {0f, 0.26f, 0.58f, 0.82f, 1f},
        new Color[] {WHITE, SILVER_LIGHT, SILVER, SILVER_DARK, BLACK}));
    g.fill(new Ellipse2D.Float(3, 3, 18, 18));
    g.setColor(WHITE);
    g.drawOval(3, 3, 17, 17);
    g.setColor(new Color(0, 0, 0, 155));
    g.drawOval(4, 4, 15, 15);
    g.setPaint(new LinearGradientPaint(0, 4, 0, 11,
        new float[] {0f, 1f}, new Color[] {new Color(255, 255, 255, 205), new Color(255, 255, 255, 10)}));
    g.fillOval(6, 4, 12, 7);
  }

  private static LinearGradientPaint chromeGradient(float startY, float endY) {
    return new LinearGradientPaint(0, startY, 0, Math.max(startY + 1, endY),
        new float[] {0f, 0.11f, 0.42f, 0.55f, 0.78f, 1f},
        new Color[] {WHITE, SILVER_LIGHT, SILVER, GRAPHITE, SILVER_DARK, BLACK});
  }

  private static void glassHighlight(Graphics2D g, float x, float y, float width, float height, float arc) {
    g.setPaint(new LinearGradientPaint(0, y, 0, y + Math.max(1, height),
        new float[] {0f, 0.52f, 1f},
        new Color[] {new Color(255, 255, 255, 210), new Color(255, 255, 255, 74), new Color(255, 255, 255, 0)}));
    g.fill(new RoundRectangle2D.Float(x, y, width, height, arc, arc));
  }

  private static void indicator(Graphics2D g, float centerX, float centerY, float radius) {
    g.setPaint(new RadialGradientPaint(new Point2D.Float(centerX - radius * 0.35f, centerY - radius * 0.35f),
        Math.max(0.1f, radius * 1.2f),
        new float[] {0f, 0.45f, 1f}, new Color[] {WHITE, ORANGE, ORANGE_DARK}));
    g.fill(new Ellipse2D.Float(centerX - radius, centerY - radius, radius * 2, radius * 2));
    g.setColor(BLACK);
    g.setStroke(stroke(0.6f));
    g.draw(new Ellipse2D.Float(centerX - radius, centerY - radius, radius * 2, radius * 2));
  }

  private static void shadow(Graphics2D g, float x, float y, float width, float height, float arc) {
    g.setColor(new Color(0, 0, 0, 48));
    g.fill(new RoundRectangle2D.Float(x - 1, y + 1, width + 2, height + 3, arc + 2, arc + 2));
    g.setColor(new Color(0, 0, 0, 96));
    g.fill(new RoundRectangle2D.Float(x, y + 2, width, height + 1, arc + 1, arc + 1));
  }

  private static BasicStroke stroke(float width) {
    return new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
  }

  private static void line(Graphics2D g, float x1, float y1, float x2, float y2) {
    g.draw(new Line2D.Float(x1, y1, x2, y2));
  }
}
