package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.Test;

class StoryboardOverlayPlacementTest {
  @Test
  void fitLetterboxesDifferentStoryboardAspectInsideRuntimeViewport() {
    StoryboardOverlayState state = new StoryboardOverlayState(
        true,
        new WritableImage(1024, 1024),
        0.5,
        "storyboard/frame.png",
        false,
        StoryboardOverlayState.FitMode.FIT,
        1920,
        1080,
        1024,
        1024,
        1.0,
        0,
        0);

    StoryboardOverlayPlacement.Rect rect =
        StoryboardOverlayPlacement.compute(state, 10, 20, 960, 540);

    assertEquals(220.0, rect.x(), 0.001);
    assertEquals(20.0, rect.y(), 0.001);
    assertEquals(540.0, rect.width(), 0.001);
    assertEquals(540.0, rect.height(), 0.001);
  }

  @Test
  void stretchFillsRuntimeViewportAndScalesIntoPreviewViewport() {
    StoryboardOverlayState state = new StoryboardOverlayState(
        true,
        new WritableImage(800, 600),
        0.5,
        "storyboard/frame.png",
        false,
        StoryboardOverlayState.FitMode.STRETCH,
        1920,
        1080,
        800,
        600,
        1.0,
        0,
        0);

    StoryboardOverlayPlacement.Rect rect =
        StoryboardOverlayPlacement.compute(state, 10, 20, 960, 540);

    assertEquals(10.0, rect.x(), 0.001);
    assertEquals(20.0, rect.y(), 0.001);
    assertEquals(960.0, rect.width(), 0.001);
    assertEquals(540.0, rect.height(), 0.001);
  }

  @Test
  void fillCropsDifferentStoryboardAspectAcrossRuntimeViewport() {
    StoryboardOverlayState state = new StoryboardOverlayState(
        true,
        new WritableImage(1024, 1024),
        0.5,
        "storyboard/frame.png",
        false,
        StoryboardOverlayState.FitMode.FILL,
        1920,
        1080,
        1024,
        1024,
        1.0,
        0,
        0);

    StoryboardOverlayPlacement.Rect rect =
        StoryboardOverlayPlacement.compute(state, 10, 20, 960, 540);

    assertEquals(10.0, rect.x(), 0.001);
    assertEquals(-190.0, rect.y(), 0.001);
    assertEquals(960.0, rect.width(), 0.001);
    assertEquals(960.0, rect.height(), 0.001);
  }

  @Test
  void offsetIsAppliedInRuntimePixelsBeforeViewportScaling() {
    StoryboardOverlayState state = new StoryboardOverlayState(
        true,
        new WritableImage(1920, 1080),
        0.5,
        "storyboard/frame.png",
        false,
        StoryboardOverlayState.FitMode.FIT,
        1920,
        1080,
        1920,
        1080,
        1.0,
        100,
        -50);

    StoryboardOverlayPlacement.Rect rect =
        StoryboardOverlayPlacement.compute(state, 0, 0, 960, 540);

    assertEquals(50.0, rect.x(), 0.001);
    assertEquals(-25.0, rect.y(), 0.001);
    assertEquals(960.0, rect.width(), 0.001);
    assertEquals(540.0, rect.height(), 0.001);
  }

  @Test
  void cropDimensionsDriveFitPlacementWhenCropIsEnabled() {
    StoryboardOverlayState state = new StoryboardOverlayState(
        true,
        new WritableImage(1000, 1000),
        0.5,
        "storyboard/frame.png",
        false,
        StoryboardOverlayState.FitMode.FIT,
        1920,
        1080,
        1000,
        1000,
        1.0,
        0,
        0,
        true,
        100,
        100,
        500,
        250);

    StoryboardOverlayPlacement.Rect rect =
        StoryboardOverlayPlacement.compute(state, 0, 0, 960, 540);

    assertEquals(0.0, rect.x(), 0.001);
    assertEquals(30.0, rect.y(), 0.001);
    assertEquals(960.0, rect.width(), 0.001);
    assertEquals(480.0, rect.height(), 0.001);
  }

  @Test
  void cropPlacementUsesClampedSourceDimensions() {
    StoryboardOverlayState state = new StoryboardOverlayState(
        true,
        new WritableImage(1000, 1000),
        0.5,
        "storyboard/frame.png",
        false,
        StoryboardOverlayState.FitMode.FIT,
        1000,
        1000,
        1000,
        1000,
        1.0,
        0,
        0,
        true,
        800,
        100,
        500,
        250);

    StoryboardOverlayPlacement.Rect rect =
        StoryboardOverlayPlacement.compute(state, 0, 0, 1000, 1000);

    assertEquals(100.0, rect.x(), 0.001);
    assertEquals(0.0, rect.y(), 0.001);
    assertEquals(800.0, rect.width(), 0.001);
    assertEquals(1000.0, rect.height(), 0.001);
  }
}
