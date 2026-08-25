package com.jvn.scenerender.menu;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.jvn.scenerender.testkit.RecordingBlitter2D;

class MenuRendererBackgroundTest {

  @Test
  void rendersMainMenuBackgroundClearWhenNoThemeImage() {
    RecordingBlitter2D blitter = new RecordingBlitter2D();
    MenuRenderer renderer = new MenuRenderer(blitter, MenuTheme.defaults());

    renderer.renderMainMenu(null, 1280.0, 720.0);

    assertTrue(blitter.calls().stream().anyMatch(c -> c.method().equals("clear")),
        "expected a clear() call when no theme background image is configured");
  }
}
