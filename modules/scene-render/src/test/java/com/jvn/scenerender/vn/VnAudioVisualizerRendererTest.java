package com.jvn.scenerender.vn;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvn.core.audio.AudioFacade;
import com.jvn.scenerender.testkit.RecordingBlitter2D;
import org.junit.jupiter.api.Test;

class VnAudioVisualizerRendererTest {

  @Test
  void disabledSettingsProduceNoDrawCalls() {
    RecordingBlitter2D blitter = new RecordingBlitter2D();
    VnAudioVisualizerRenderer renderer = new VnAudioVisualizerRenderer(blitter);
    VnAudioVisualizerRenderer.AudioVisualizerSettings settings = new VnAudioVisualizerRenderer.AudioVisualizerSettings(
        false, 32, "dynamic", "auto", "auto", 1.0, true, 0.18, 50);

    renderer.render(1280, 720, settings, null, (w, h) -> new double[]{0, h - 120, w, 120});

    assertTrue(blitter.calls().isEmpty());
  }

  @Test
  void nullAudioFacadeProducesNoDrawCalls() {
    RecordingBlitter2D blitter = new RecordingBlitter2D();
    VnAudioVisualizerRenderer renderer = new VnAudioVisualizerRenderer(blitter);
    VnAudioVisualizerRenderer.AudioVisualizerSettings settings = new VnAudioVisualizerRenderer.AudioVisualizerSettings(
        true, 32, "dynamic", "auto", "auto", 1.0, true, 0.18, 50);

    renderer.render(1280, 720, settings, null, (w, h) -> new double[]{0, h - 120, w, 120});

    assertTrue(blitter.calls().isEmpty(), "no audio facade means no spectrum data means no bars to draw");
  }

  @Test
  void freshSpectrumDataFromARealFacadeProducesBarDrawing() {
    RecordingBlitter2D blitter = new RecordingBlitter2D();
    VnAudioVisualizerRenderer renderer = new VnAudioVisualizerRenderer(blitter);
    VnAudioVisualizerRenderer.AudioVisualizerSettings settings = new VnAudioVisualizerRenderer.AudioVisualizerSettings(
        true, 8, "dynamic", "auto", "auto", 1.0, true, 0.18, 50);
    AudioFacade fakeFacade = new FakeLoudAudioFacade();

    // Multiple frames needed: bar levels ease toward target rather than snapping instantly.
    for (int i = 0; i < 30; i++) {
      renderer.render(1280, 720, settings, fakeFacade, (w, h) -> new double[]{0, h - 120, w, 120});
    }

    assertTrue(blitter.calls().stream().anyMatch(c -> c.method().equals("fillRect")),
        "expected at least one visualizer bar to be drawn after several frames of loud spectrum data");
  }

  private static final class FakeLoudAudioFacade implements AudioFacade {
    @Override
    public void playBgm(String trackId, boolean loop) {}

    @Override
    public void stopBgm() {}

    @Override
    public void playSfx(String sfxId) {}

    @Override
    public float[] getBgmSpectrumMagnitudes() {
      float[] loud = new float[16];
      java.util.Arrays.fill(loud, -5.0f); // near 0dB peak, well above the -60dB floor
      return loud;
    }

    @Override
    public long getBgmSpectrumUpdatedAtNanos() {
      return System.nanoTime();
    }
  }
}
