package com.jvn.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.jvn.core.vn.VnScenario;
import com.jvn.core.vn.VnScenarioLoader;
import com.jvn.core.vn.VnScene;
import com.jvn.scenerender.testkit.RecordingBlitter2D;
import com.jvn.scenerender.vn.VnRenderer;
import org.junit.jupiter.api.Test;

class WebLauncherVnSceneTest {

  @Test
  void fixtureSceneRendersThroughVnRendererWithoutThrowing() throws Exception {
    VnScenario scenario = new VnScenarioLoader().load("story/web_fixture.vns");
    VnScene vnScene = new VnScene(scenario);
    vnScene.setAudioFacade(new NoopAudioFacade());
    vnScene.onEnter();

    RecordingBlitter2D blitter = new RecordingBlitter2D();
    VnRenderer vnRenderer = new VnRenderer(blitter);

    vnRenderer.render(vnScene.getState(), vnScene.getScenario(), 960.0, 540.0);

    assertNotNull(blitter.calls());
    assertFalse(blitter.calls().isEmpty(),
        "expected VnRenderer to issue at least one Blitter2D call for the fixture scene");
  }
}
