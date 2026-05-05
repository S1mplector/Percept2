package com.jvn.editor.ui;

import javafx.scene.image.Image;

public record StoryboardOverlayState(
    boolean enabled,
    Image image,
    double opacity,
    String sourcePath,
    boolean hideUi) {

  private static final StoryboardOverlayState NONE =
      new StoryboardOverlayState(false, null, 0.35, null, false);

  public StoryboardOverlayState {
    opacity = Double.isFinite(opacity) ? Math.max(0.0, Math.min(1.0, opacity)) : 0.35;
  }

  public static StoryboardOverlayState none() {
    return NONE;
  }

  public boolean hasImage() {
    return image != null && !image.isError();
  }
}
