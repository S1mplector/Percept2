package com.jvn.editor.ui;

import java.util.EnumMap;
import java.util.Map;
import java.util.prefs.Preferences;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Menu;
import javafx.scene.paint.Color;

/** Camera-style composition guides shared by every editor preview surface. */
public final class CompositionGuideOverlay extends Canvas {
  public static final double PHI = (1.0 + Math.sqrt(5.0)) / 2.0;

  public enum Guide {
    THIRDS("Rule of Thirds", false),
    GOLDEN_RATIO("Golden Ratio Grid", false),
    GOLDEN_SPIRAL("Golden Spiral", false),
    DIAGONALS("Diagonal Method", false),
    CENTER("Center Crosshair", false),
    SAFE_AREAS("Action / Title Safe Areas", false);

    private final String label;
    private final boolean defaultEnabled;

    Guide(String label, boolean defaultEnabled) {
      this.label = label;
      this.defaultEnabled = defaultEnabled;
    }
  }

  private static final Preferences PREFS = Preferences.userNodeForPackage(CompositionGuideOverlay.class);
  private static final Map<Guide, BooleanProperty> ENABLED = new EnumMap<>(Guide.class);

  static {
    for (Guide guide : Guide.values()) {
      ENABLED.put(guide, new SimpleBooleanProperty(
          PREFS.getBoolean("compositionGuide." + guide.name(), guide.defaultEnabled)));
      ENABLED.get(guide).addListener((obs, oldValue, enabled) ->
          PREFS.putBoolean("compositionGuide." + guide.name(), enabled));
    }
  }

  public CompositionGuideOverlay() {
    setManaged(false);
    setMouseTransparent(true);
    setPickOnBounds(false);
    widthProperty().addListener((obs, oldValue, value) -> draw());
    heightProperty().addListener((obs, oldValue, value) -> draw());
    for (BooleanProperty property : ENABLED.values()) {
      property.addListener((obs, oldValue, value) -> draw());
    }
  }

  @Override
  public boolean isResizable() {
    return true;
  }

  @Override
  public void resize(double width, double height) {
    setWidth(Math.max(0.0, width));
    setHeight(Math.max(0.0, height));
  }

  public static Menu createMenu() {
    Menu menu = new Menu("Composition Guides");
    for (Guide guide : Guide.values()) {
      CheckMenuItem item = new CheckMenuItem(guide.label);
      item.selectedProperty().bindBidirectional(ENABLED.get(guide));
      menu.getItems().add(item);
    }
    return menu;
  }

  static double[] goldenGridFractions() {
    double edge = 1.0 / (PHI * PHI);
    return new double[] {edge, 1.0 - edge};
  }

  private void draw() {
    double width = getWidth();
    double height = getHeight();
    GraphicsContext gc = getGraphicsContext2D();
    gc.clearRect(0, 0, width, height);
    if (width <= 1.0 || height <= 1.0) return;

    gc.save();
    gc.setLineWidth(1.0);
    if (enabled(Guide.THIRDS)) drawGrid(gc, width, height, new double[] {1.0 / 3.0, 2.0 / 3.0}, Color.web("#ffffff", 0.72));
    if (enabled(Guide.GOLDEN_RATIO)) drawGrid(gc, width, height, goldenGridFractions(), Color.web("#ffd45c", 0.82));
    if (enabled(Guide.DIAGONALS)) drawDiagonals(gc, width, height);
    if (enabled(Guide.CENTER)) drawCenter(gc, width, height);
    if (enabled(Guide.SAFE_AREAS)) drawSafeAreas(gc, width, height);
    if (enabled(Guide.GOLDEN_SPIRAL)) drawGoldenSpiral(gc, width, height);
    gc.restore();
  }

  private static boolean enabled(Guide guide) {
    return ENABLED.get(guide).get();
  }

  private static void drawGrid(GraphicsContext gc, double width, double height, double[] fractions, Color color) {
    gc.setStroke(color);
    for (double fraction : fractions) {
      strokeLine(gc, width * fraction, 0.0, width * fraction, height);
      strokeLine(gc, 0.0, height * fraction, width, height * fraction);
    }
  }

  private static void drawDiagonals(GraphicsContext gc, double width, double height) {
    gc.setStroke(Color.web("#71d7ff", 0.70));
    double length = Math.min(width, height);
    strokeLine(gc, 0, 0, length, length);
    strokeLine(gc, width, 0, width - length, length);
    strokeLine(gc, 0, height, length, height - length);
    strokeLine(gc, width, height, width - length, height - length);
  }

  private static void drawCenter(GraphicsContext gc, double width, double height) {
    gc.setStroke(Color.web("#ff6f91", 0.84));
    double radius = Math.max(8.0, Math.min(width, height) * 0.025);
    strokeLine(gc, width / 2.0 - radius, height / 2.0, width / 2.0 + radius, height / 2.0);
    strokeLine(gc, width / 2.0, height / 2.0 - radius, width / 2.0, height / 2.0 + radius);
  }

  private static void drawSafeAreas(GraphicsContext gc, double width, double height) {
    gc.setStroke(Color.web("#7dff9d", 0.68));
    strokeInsetRect(gc, width, height, 0.05); // modern action-safe boundary
    gc.setStroke(Color.web("#ffcf70", 0.72));
    strokeInsetRect(gc, width, height, 0.10); // modern title-safe boundary
  }

  private static void strokeInsetRect(GraphicsContext gc, double width, double height, double inset) {
    double x = width * inset;
    double y = height * inset;
    gc.strokeRect(snap(x), snap(y), Math.max(0.0, width - 2.0 * x), Math.max(0.0, height - 2.0 * y));
  }

  /** Draws the exact logarithmic golden spiral, growing by phi every quarter turn. */
  private static void drawGoldenSpiral(GraphicsContext gc, double width, double height) {
    gc.setStroke(Color.web("#ffd45c", 0.92));
    gc.setLineWidth(1.35);
    double margin = Math.min(width, height) * 0.035;
    double availableWidth = Math.max(1.0, width - margin * 2.0);
    double availableHeight = Math.max(1.0, height - margin * 2.0);
    double turns = 2.75;
    double thetaMax = turns * Math.PI * 2.0;
    double growth = Math.log(PHI) / (Math.PI / 2.0);
    double maximumRadius = Math.min(availableWidth, availableHeight) * 0.49;
    double a = maximumRadius / Math.exp(growth * thetaMax);
    double centerX = width / 2.0;
    double centerY = height / 2.0;
    int segments = Math.max(240, (int) Math.ceil(thetaMax * 24.0));
    gc.beginPath();
    for (int i = 0; i <= segments; i++) {
      double theta = thetaMax * i / segments;
      double radius = a * Math.exp(growth * theta);
      double x = centerX + radius * Math.cos(theta);
      double y = centerY - radius * Math.sin(theta);
      if (i == 0) gc.moveTo(x, y); else gc.lineTo(x, y);
    }
    gc.stroke();
  }

  private static void strokeLine(GraphicsContext gc, double x1, double y1, double x2, double y2) {
    gc.strokeLine(snap(x1), snap(y1), snap(x2), snap(y2));
  }

  private static double snap(double value) {
    return Math.floor(value) + 0.5;
  }
}
