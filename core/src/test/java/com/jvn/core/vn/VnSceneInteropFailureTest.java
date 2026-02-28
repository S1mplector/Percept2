package com.jvn.core.vn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.jvn.core.audio.AudioFacade;

class VnSceneInteropFailureTest {

  @Test
  void externalInteropFailureShowsHudAndKeepsProcessing() {
    VnScenario scenario = new VnScenarioBuilder("interop_external_failure")
        .external("var", "set hp 10")
        .dialogue("Narrator", "After external")
        .end()
        .build();

    VnScene scene = new VnScene(scenario);
    scene.setInterop((command, vnScene) -> {
      throw new IllegalStateException("boom");
    });

    scene.onEnter();

    assertEquals(1, scene.getState().getCurrentNodeIndex());
    assertEquals("VN external [var] failed: IllegalStateException: boom", scene.getState().getHudMessage());
  }

  @Test
  void preflightInteropFailureShowsHudMessage() {
    VnScenario scenario = new VnScenarioBuilder("interop_preflight_failure")
        .external("var", "set hp 10")
        .end()
        .build();

    VnScene scene = new VnScene(scenario);
    scene.setInterop((command, vnScene) -> {
      throw new RuntimeException("preflight exploded");
    });

    scene.preflightState(1);

    String hud = scene.getState().getHudMessage();
    assertNotNull(hud);
    assertTrue(hud.startsWith("VN preflight [var] failed: RuntimeException"));
  }

  @Test
  void preflightAppliesAudioAndUiStateBeforeTargetLabel() {
    VnScenario scenario = new VnScenarioBuilder("preflight_runtime_state")
        .playBgm("theme", true)
        .external("ui", "visualizer on bars=24")
        .dialogue("Narrator", "Start")
        .end()
        .build();

    FakeAudio audio = new FakeAudio();
    VnScene scene = new VnScene(scenario);
    scene.setAudioFacade(audio);
    scene.setInterop(new DefaultVnInterop());

    scene.preflightState(2);

    assertEquals("theme", audio.lastBgmTrack);
    assertEquals(true, audio.lastBgmLoop);
    assertEquals(Boolean.TRUE, scene.getState().getVariable("ui.audioVisualizer"));
    assertEquals(24, scene.getState().getVariable("ui.audioVisualizerBars"));
  }

  @Test
  void preflightSkipsNonStatefulInteropProviders() {
    VnScenario scenario = new VnScenarioBuilder("preflight_provider_filter")
        .external("menu", "main")
        .dialogue("Narrator", "After")
        .end()
        .build();

    VnScene scene = new VnScene(scenario);
    scene.setInterop((command, vnScene) -> {
      throw new IllegalStateException("should not be called for menu");
    });

    scene.preflightState(1);

    assertNull(scene.getState().getHudMessage());
  }

  private static final class FakeAudio implements AudioFacade {
    private String lastBgmTrack;
    private boolean lastBgmLoop;

    @Override
    public void playBgm(String trackId, boolean loop) {
      lastBgmTrack = trackId;
      lastBgmLoop = loop;
    }

    @Override
    public void stopBgm() {
    }

    @Override
    public void playSfx(String sfxId) {
    }
  }
}
