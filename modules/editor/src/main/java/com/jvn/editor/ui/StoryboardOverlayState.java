package com.jvn.editor.ui;

import javafx.scene.image.Image;

public record StoryboardOverlayState(
    boolean enabled,
    Image image,
    double opacity,
    String sourcePath,
    boolean hideUi,
    FitMode fitMode,
    double runtimeWidth,
    double runtimeHeight,
    double storyboardWidth,
    double storyboardHeight,
    double scale,
    double offsetX,
    double offsetY,
    boolean cropEnabled,
    double cropX,
    double cropY,
    double cropWidth,
    double cropHeight) {

  private static final StoryboardOverlayState NONE =
      new StoryboardOverlayState(false, null, 0.35, null, false, FitMode.FIT, 0, 0, 0, 0, 1.0, 0, 0, false, 0, 0, 0, 0);

  public StoryboardOverlayState(
      boolean enabled,
      Image image,
      double opacity,
      String sourcePath,
      boolean hideUi) {
    this(enabled, image, opacity, sourcePath, hideUi, FitMode.FIT, 0, 0, 0, 0, 1.0, 0, 0, false, 0, 0, 0, 0);
  }

  public StoryboardOverlayState(
      boolean enabled,
      Image image,
      double opacity,
      String sourcePath,
      boolean hideUi,
      FitMode fitMode,
      double runtimeWidth,
      double runtimeHeight,
      double storyboardWidth,
      double storyboardHeight,
      double scale,
      double offsetX,
      double offsetY) {
    this(enabled, image, opacity, sourcePath, hideUi, fitMode, runtimeWidth, runtimeHeight, storyboardWidth, storyboardHeight, scale, offsetX, offsetY, false, 0, 0, 0, 0);
  }

  public StoryboardOverlayState {
    opacity = Double.isFinite(opacity) ? Math.max(0.0, Math.min(1.0, opacity)) : 0.35;
    fitMode = fitMode == null ? FitMode.FIT : fitMode;
    runtimeWidth = positiveOrZero(runtimeWidth);
    runtimeHeight = positiveOrZero(runtimeHeight);
    storyboardWidth = positiveOrZero(storyboardWidth);
    storyboardHeight = positiveOrZero(storyboardHeight);
    scale = Double.isFinite(scale) ? Math.max(0.05, Math.min(8.0, scale)) : 1.0;
    offsetX = Double.isFinite(offsetX) ? offsetX : 0.0;
    offsetY = Double.isFinite(offsetY) ? offsetY : 0.0;
    cropX = positiveOrZero(cropX);
    cropY = positiveOrZero(cropY);
    cropWidth = positiveOrZero(cropWidth);
    cropHeight = positiveOrZero(cropHeight);
    cropEnabled = cropEnabled && cropWidth > 0.0 && cropHeight > 0.0;
  }

  public static StoryboardOverlayState none() {
    return NONE;
  }

  public boolean hasImage() {
    return image != null && !image.isError();
  }

  private static double positiveOrZero(double value) {
    return Double.isFinite(value) && value > 0.0 ? value : 0.0;
  }

  public enum FitMode {
    FIT("Fit"),
    FILL("Fill"),
    STRETCH("Stretch"),
    ORIGINAL("Original");

    private final String label;

    FitMode(String label) {
      this.label = label;
    }

    public String label() {
      return label;
    }

    @Override
    public String toString() {
      return label;
    }

    public static FitMode parse(String raw) {
      if (raw == null || raw.isBlank()) return FIT;
      for (FitMode mode : values()) {
        if (mode.name().equalsIgnoreCase(raw.trim()) || mode.label.equalsIgnoreCase(raw.trim())) {
          return mode;
        }
      }
      return FIT;
    }
  }
}
