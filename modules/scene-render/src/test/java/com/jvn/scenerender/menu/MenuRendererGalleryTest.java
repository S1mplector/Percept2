package com.jvn.scenerender.menu;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.jvn.scenerender.testkit.RecordingBlitter2D;

class MenuRendererGalleryTest {

  @Test
  void rendersGalleryBackground() {
    RecordingBlitter2D blitter = new RecordingBlitter2D();
    MenuRenderer renderer = new MenuRenderer(blitter, MenuTheme.defaults());

    renderer.renderGallery(null, 1280.0, 720.0);

    assertTrue(!blitter.calls().isEmpty(), "expected at least one draw call for the gallery screen");
  }
}
