package com.jvn.scenerender.menu;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.jvn.core.menu.SettingsScene;
import com.jvn.core.vn.VnSettings;
import com.jvn.scenerender.testkit.RecordingBlitter2D;

class MenuRendererSettingsTest {

  @Test
  void rendersSettingsScreenBackground() {
    RecordingBlitter2D blitter = new RecordingBlitter2D();
    MenuRenderer renderer = new MenuRenderer(blitter, MenuTheme.defaults());
    // Adapted from the brief's `renderSettings(null, ...)` snippet: like renderSaveMenu/
    // renderLoadMenu (Task 6), renderSettings unconditionally dereferences `scene.getDisplayItems()`
    // partway through its body (a pre-existing characteristic of the original JavaFX code, not
    // something this task's migration introduces or is meant to fix), so a null scene throws
    // before reaching the slider/toggle code this task retrofits. Use a minimal real SettingsScene
    // instead, mirroring Task 6's SaveMenuScene adaptation.
    SettingsScene scene = new SettingsScene(new VnSettings());

    renderer.renderSettings(scene, 1280.0, 720.0);

    assertTrue(blitter.calls().stream().anyMatch(c ->
        c.method().equals("clear") || c.method().equals("fillRect") || c.method().equals("drawImage")),
        "expected some background draw call for the settings screen");
  }
}
