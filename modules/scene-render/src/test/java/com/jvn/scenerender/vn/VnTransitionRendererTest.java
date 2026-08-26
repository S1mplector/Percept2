package com.jvn.scenerender.vn;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvn.scenerender.testkit.RecordingBlitter2D;
import org.junit.jupiter.api.Test;

class VnTransitionRendererTest {

  @Test
  void crossfadeDrawsBothBackgroundsWithComplementaryAlpha() {
    RecordingBlitter2D blitter = new RecordingBlitter2D();
    VnTransitionRenderer renderer = new VnTransitionRenderer(blitter);

    renderer.renderCrossfadeBackground(
        fakeBackground("prev"), fakeBackground("cur"), 0.3f, 800, 600,
        bg -> bg == null ? null : "assets/bg/" + bg.getImagePath() + ".png");

    boolean drewPrev = blitter.calls().stream().anyMatch(c ->
        c.method().equals("drawImage") && "assets/bg/prev.png".equals(c.args().get(0)));
    boolean drewCur = blitter.calls().stream().anyMatch(c ->
        c.method().equals("drawImage") && "assets/bg/cur.png".equals(c.args().get(0)));
    assertTrue(drewPrev && drewCur);
  }

  @Test
  void wipeClipsTheIncomingBackgroundToTheWipeProgress() {
    RecordingBlitter2D blitter = new RecordingBlitter2D();
    VnTransitionRenderer renderer = new VnTransitionRenderer(blitter);

    renderer.renderWipeBackground(
        fakeBackground("prev"), fakeBackground("cur"), 0.5f, 800, 600,
        bg -> bg == null ? null : "assets/bg/" + bg.getImagePath() + ".png");

    boolean clipped = blitter.calls().stream().anyMatch(c -> c.method().equals("setClipRect"));
    assertTrue(clipped, "wipe transition should clip the incoming background to the wipe rectangle");
  }

  private static com.jvn.core.vn.VnBackground fakeBackground(String id) {
    return new com.jvn.core.vn.VnBackground(id, id);
  }
}
