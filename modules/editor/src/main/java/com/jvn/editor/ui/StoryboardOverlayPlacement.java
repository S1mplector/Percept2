package com.jvn.editor.ui;

final class StoryboardOverlayPlacement {
  private StoryboardOverlayPlacement() {
  }

  static Rect compute(StoryboardOverlayState state, double viewportX, double viewportY, double viewportWidth, double viewportHeight) {
    if (state == null || !state.hasImage() || viewportWidth <= 0.0 || viewportHeight <= 0.0) {
      return new Rect(viewportX, viewportY, 0.0, 0.0);
    }

    double runtimeWidth = state.runtimeWidth() > 0.0 ? state.runtimeWidth() : viewportWidth;
    double runtimeHeight = state.runtimeHeight() > 0.0 ? state.runtimeHeight() : viewportHeight;
    double boardWidth = state.cropEnabled() && state.cropWidth() > 0.0
        ? effectiveCropWidth(state)
        : state.storyboardWidth() > 0.0 ? state.storyboardWidth() : state.image().getWidth();
    double boardHeight = state.cropEnabled() && state.cropHeight() > 0.0
        ? effectiveCropHeight(state)
        : state.storyboardHeight() > 0.0 ? state.storyboardHeight() : state.image().getHeight();
    runtimeWidth = runtimeWidth > 0.0 ? runtimeWidth : viewportWidth;
    runtimeHeight = runtimeHeight > 0.0 ? runtimeHeight : viewportHeight;
    boardWidth = boardWidth > 0.0 ? boardWidth : runtimeWidth;
    boardHeight = boardHeight > 0.0 ? boardHeight : runtimeHeight;

    double width;
    double height;
    switch (state.fitMode()) {
      case STRETCH -> {
        width = runtimeWidth;
        height = runtimeHeight;
      }
      case FILL -> {
        double scale = Math.max(runtimeWidth / boardWidth, runtimeHeight / boardHeight);
        width = boardWidth * scale;
        height = boardHeight * scale;
      }
      case ORIGINAL -> {
        width = boardWidth;
        height = boardHeight;
      }
      case FIT -> {
        double scale = Math.min(runtimeWidth / boardWidth, runtimeHeight / boardHeight);
        width = boardWidth * scale;
        height = boardHeight * scale;
      }
      default -> {
        width = runtimeWidth;
        height = runtimeHeight;
      }
    }

    double userScale = state.scale();
    width *= userScale;
    height *= userScale;
    double x = (runtimeWidth - width) / 2.0 + state.offsetX();
    double y = (runtimeHeight - height) / 2.0 + state.offsetY();

    double viewportScaleX = viewportWidth / runtimeWidth;
    double viewportScaleY = viewportHeight / runtimeHeight;
    return new Rect(
        viewportX + x * viewportScaleX,
        viewportY + y * viewportScaleY,
        width * viewportScaleX,
        height * viewportScaleY);
  }

  private static double effectiveCropWidth(StoryboardOverlayState state) {
    double imageWidth = state.image().getWidth();
    double sourceX = Math.max(0.0, Math.min(imageWidth, state.cropX()));
    return Math.max(0.0, Math.min(imageWidth - sourceX, state.cropWidth()));
  }

  private static double effectiveCropHeight(StoryboardOverlayState state) {
    double imageHeight = state.image().getHeight();
    double sourceY = Math.max(0.0, Math.min(imageHeight, state.cropY()));
    return Math.max(0.0, Math.min(imageHeight - sourceY, state.cropHeight()));
  }

  record Rect(double x, double y, double width, double height) {
  }
}
