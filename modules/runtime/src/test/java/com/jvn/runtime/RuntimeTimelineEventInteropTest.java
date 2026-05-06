package com.jvn.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.jvn.core.config.ApplicationConfig;
import com.jvn.core.engine.Engine;
import com.jvn.core.vn.CharacterPosition;
import com.jvn.core.vn.VnExternalCommand;
import com.jvn.core.vn.VnScenarioBuilder;
import com.jvn.core.vn.VnScene;
import com.jvn.scripting.jes.JesLoader;
import com.jvn.scripting.jes.runtime.JesScene2D;

class RuntimeTimelineEventInteropTest {

  @Test
  void scriptCallEventCueInvokesJesSceneHandler() throws Exception {
    Engine engine = new Engine(ApplicationConfig.builder().build());
    RuntimeVnInterop interop = new RuntimeVnInterop(engine);
    JesScene2D jes = JesLoader.load("""
        scene "Beat" {
          entity "s" { component Sprite2D { x: 0 y: 0 w: 1 h: 1 image: "a.png" } }
        }
        """);
    List<Map<String, Object>> calls = new ArrayList<>();
    jes.registerCall("spawnParticles", calls::add);
    engine.scenes().push(jes);

    VnScene owner = new VnScene(new VnScenarioBuilder("owner").end().build());
    interop.getBase().handle(new VnExternalCommand("jes_timeline_inline", """
        timeline {
          event "script_call" {
            handler: spawnParticles
            count: 12
            burst: true
          }
        }
        """), owner);

    owner.getState().updateTimelineRunners(1);

    assertEquals(1, calls.size());
    assertEquals(12, calls.get(0).get("count"));
    assertEquals(Boolean.TRUE, calls.get(0).get("burst"));
  }

  @Test
  void scriptCallEventCueCanRunVnsInteropCommand() {
    Engine engine = new Engine(ApplicationConfig.builder().build());
    RuntimeVnInterop interop = new RuntimeVnInterop(engine);
    VnScene vn = new VnScene(new VnScenarioBuilder("owner").end().build());
    vn.setInterop(interop);
    engine.scenes().push(vn);

    interop.getBase().handle(new VnExternalCommand("jes_timeline_inline", """
        timeline {
          event "script_call" {
            provider: var
            command: set affection 5
          }
        }
        """), vn);

    vn.getState().updateTimelineRunners(1);

    assertEquals(5, vn.getState().getVariables().get("affection"));
  }

  @Test
  void scriptCallEventCueCanInferVnsProviderFromCommand() {
    Engine engine = new Engine(ApplicationConfig.builder().build());
    RuntimeVnInterop interop = new RuntimeVnInterop(engine);
    VnScene vn = new VnScene(new VnScenarioBuilder("owner").end().build());
    engine.scenes().push(vn);

    interop.getBase().handle(new VnExternalCommand("jes_timeline_inline", """
        timeline {
          event "script_call" {
            command: var set courage 7
          }
        }
        """), vn);

    vn.getState().updateTimelineRunners(1);

    assertEquals(7, vn.getState().getVariables().get("courage"));
  }

  @Test
  void scriptCallEventCueCanDriveVnStageState() {
    Engine engine = new Engine(ApplicationConfig.builder().build());
    RuntimeVnInterop interop = new RuntimeVnInterop(engine);
    VnScene vn = new VnScene(new VnScenarioBuilder("owner").end().build());
    engine.scenes().push(vn);

    interop.getBase().handle(new VnExternalCommand("jes_timeline_inline", """
        timeline {
          event "script_call" {
            provider: vn
            command: show hero right smile
          }
        }
        """), vn);

    vn.getState().updateTimelineRunners(1);

    var slot = vn.getState().getVisibleCharacters().get(CharacterPosition.RIGHT);
    assertEquals("hero", slot.getCharacterId());
    assertEquals("smile", slot.getExpression());
  }
}
