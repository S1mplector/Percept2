package com.jvn.scenerender.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.jvn.core.vn.VnErrorOverlay;
import com.jvn.core.vn.VnScenario;
import com.jvn.core.vn.VnScene;
import com.jvn.scenerender.menu.MenuRenderer;
import com.jvn.scenerender.menu.MenuTheme;
import com.jvn.scenerender.testkit.RecordingBlitter2D;
import com.jvn.scenerender.vn.VnRenderer;

/**
 * Covers {@link SceneInputRouter#handleClick}'s VnScene-error-overlay
 * branch — the button-index (0/1/2, or -1) return-value contract that
 * FxLauncher/WebMain react to asymmetrically (FxLauncher forwards any
 * {@code >= 0} index to {@code handleRuntimeErrorButton} for all three
 * buttons; WebMain only acts on {@code == 0}, clearing the error). This
 * test file only locks in {@code handleClick}'s own return value — the two
 * platforms' differing reactions are out of scope (platform-specific,
 * exercised indirectly via each platform's own integration path).
 */
class SceneInputRouterVnErrorOverlayTest {

  @Test
  void returnsMinusOneWhenNoActiveError() {
    RecordingBlitter2D blitter = new RecordingBlitter2D();
    MenuRenderer menuRenderer = new MenuRenderer(blitter, MenuTheme.defaults());
    VnRenderer vnRenderer = new VnRenderer(blitter);
    SceneInputRouter router = new SceneInputRouter(menuRenderer, vnRenderer, new DefaultMenuSceneFactory());

    VnScene scene = new VnScene(VnScenario.builder("test").build());

    int result = router.handleClick(scene, null, 1280.0, 720.0, 640.0, 360.0);

    assertEquals(-1, result);
  }

  @Test
  void returnsMinusOneWhenActiveErrorButClickMissesAllButtons() {
    RecordingBlitter2D blitter = new RecordingBlitter2D();
    MenuRenderer menuRenderer = new MenuRenderer(blitter, MenuTheme.defaults());
    VnRenderer vnRenderer = new VnRenderer(blitter);
    SceneInputRouter router = new SceneInputRouter(menuRenderer, vnRenderer, new DefaultMenuSceneFactory());

    VnScene scene = new VnScene(VnScenario.builder("test").build());
    scene.setActiveError(VnErrorOverlay.runtimeError("boom", null));

    double w = 1280.0, h = 720.0;

    // Top-left corner of the canvas is above/left of the error overlay's
    // content panel (which starts with ~16-42px outer padding and the
    // button row sits near the bottom), so this reliably misses all three
    // buttons while an error genuinely IS active -- distinguishing this
    // from the "no active error at all" case above, which also returns -1
    // but for a structurally different reason (early-return before any
    // button geometry is even computed).
    int result = router.handleClick(scene, null, w, h, 2.0, 2.0);

    assertEquals(-1, result);
  }

  @Test
  void returnsClickedButtonIndexWhenActiveErrorHit() {
    double w = 1280.0, h = 720.0;

    // VnChoiceOverlayRenderer.renderErrorOverlay's button row sits near the
    // bottom of the overlay (buttonY = height - outerPadding - buttonH, with
    // outerPadding <= 42 and buttonH <= 44, so buttonY is always within the
    // last ~90px of a 720-tall canvas) and spans the right-hand portion of
    // the content width (buttonsStartX = contentX + (contentW - buttonsWidth),
    // three buttons of buttonW <= 164 each). Restricting the grid scan to
    // that band (rather than the full 1280x720 canvas at a fine step) keeps
    // this test fast and avoids re-rendering the (fairly expensive,
    // RecordingBlitter2D-recorded) overlay tens of thousands of times.
    for (int expectedIndex = 0; expectedIndex <= 2; expectedIndex++) {
      // Resolve real hit-test coordinates by scanning a grid of (x, y)
      // candidates and checking VnRenderer.renderErrorOverlay's own return
      // value directly (the same idiom SceneInputRouterMenuClickTest/
      // SceneInputRouterHoverTest use against MenuRenderer's hover-index
      // methods), rather than re-deriving VnChoiceOverlayRenderer's
      // internal button-layout geometry (outerPadding/buttonH/buttonY/
      // buttonW/buttonGap/buttonsStartX) by hand. A fresh
      // RecordingBlitter2D/VnRenderer per probe call keeps each recorded
      // draw-call list small (renderErrorOverlay draws are otherwise
      // accumulated call-over-call, which previously exhausted heap when
      // scanning at a fine step over the full canvas).
      double x = -1, y = -1;
      outer:
      for (double gy = h - 90.0; gy < h; gy += 4.0) {
        for (double gx = w * 0.35; gx < w; gx += 4.0) {
          RecordingBlitter2D probeBlitter = new RecordingBlitter2D();
          VnRenderer probeRenderer = new VnRenderer(probeBlitter);
          VnErrorOverlay probeError = VnErrorOverlay.runtimeError("boom", null);
          if (probeRenderer.renderErrorOverlay(probeError, w, h, gx, gy) == expectedIndex) {
            x = gx;
            y = gy;
            break outer;
          }
        }
      }
      assertTrue(x >= 0 && y >= 0,
          "failed to find coordinates hitting error-overlay button index " + expectedIndex + " via grid scan");

      RecordingBlitter2D blitter = new RecordingBlitter2D();
      MenuRenderer menuRenderer = new MenuRenderer(blitter, MenuTheme.defaults());
      VnRenderer vnRenderer = new VnRenderer(blitter);
      SceneInputRouter router = new SceneInputRouter(menuRenderer, vnRenderer, new DefaultMenuSceneFactory());
      VnScene scene = new VnScene(VnScenario.builder("test").build());
      scene.setActiveError(VnErrorOverlay.runtimeError("boom", null));

      int result = router.handleClick(scene, null, w, h, x, y);

      assertEquals(expectedIndex, result,
          "handleClick should return button index " + expectedIndex + " for a click at (" + x + ", " + y + ")");
    }
  }
}
