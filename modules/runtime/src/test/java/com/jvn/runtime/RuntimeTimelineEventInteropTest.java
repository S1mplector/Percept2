package com.jvn.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.jvn.core.animation.TimelineDrivenEntity;
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

  @Test
  void inlineTimelineLayerTargetsCreateRendererProxies() {
    Engine engine = new Engine(ApplicationConfig.builder().build());
    RuntimeVnInterop interop = new RuntimeVnInterop(engine);
    VnScene vn = new VnScene(new VnScenarioBuilder("owner").end().build());
    vn.setInterop(interop);
    engine.scenes().push(vn);
    vn.getState().showCharacter(CharacterPosition.CENTER, "john", "neutral");

    interop.getBase().handle(new VnExternalCommand("jes_timeline_inline", """
        timeline {
          move "john_neutral_body_default" {
            x: -724.8
            y: 1080
            dur: 1500
            easing: ease_in_expo
          }
        }
        """), vn);

    vn.getState().updateTimelineRunners(1500);

    var proxy = interop.getTimelineAccessor().getProxy("john_neutral_body_default");
    assertNotNull(proxy);
    assertEquals(-724.8, proxy.getX(), 0.001);
    assertEquals(1080.0, proxy.getY(), 0.001);
    TimelineDrivenEntity driven = assertInstanceOf(TimelineDrivenEntity.class, proxy);
    assertTrue(driven.hasTimelineX());
    assertTrue(driven.hasTimelineY());
  }

  @Test
  void inlineTimelineLayerTargetsPreserveUnkeyedAxes() {
    Engine engine = new Engine(ApplicationConfig.builder().build());
    RuntimeVnInterop interop = new RuntimeVnInterop(engine);
    VnScene vn = new VnScene(new VnScenarioBuilder("owner").end().build());
    vn.setInterop(interop);
    engine.scenes().push(vn);
    vn.getState().showCharacter(CharacterPosition.CENTER, "john", "neutral");

    interop.getBase().handle(new VnExternalCommand("jes_timeline_inline", """
        timeline {
          move "john_neutral_body_default" {
            x: -1684.8
            dur: 1500
            easing: ease_in_expo
          }
        }
        """), vn);

    vn.getState().updateTimelineRunners(1500);

    var proxy = interop.getTimelineAccessor().getProxy("john_neutral_body_default");
    assertNotNull(proxy);
    assertEquals(-1684.8, proxy.getX(), 0.001);
    assertEquals(0.0, proxy.getY(), 0.001);
    TimelineDrivenEntity driven = assertInstanceOf(TimelineDrivenEntity.class, proxy);
    assertTrue(driven.hasTimelineX());
    assertFalse(driven.hasTimelineY());
  }

  @Test
  void legacyInlineLayerMirrorDefaultsToCharacterFootPivot() {
    Engine engine = new Engine(ApplicationConfig.builder().build());
    RuntimeVnInterop interop = new RuntimeVnInterop(engine);
    VnScene vn = new VnScene(new VnScenarioBuilder("owner").end().build());
    vn.setInterop(interop);
    engine.scenes().push(vn);
    vn.getState().showCharacter(CharacterPosition.CENTER, "john", "neutral");

    interop.getBase().handle(new VnExternalCommand("jes_timeline_inline", """
        timeline {
          parallel {
            move "john_neutral_body_default" {
              x: 13.621935
              y: 0
              dur: 100
            }
            scale "john_neutral_body_default" {
              sx: -1
              dur: 100
            }
          }
        }
        """), vn);

    vn.getState().updateTimelineRunners(100);

    var proxy = interop.getTimelineAccessor().getProxy("john_neutral_body_default");
    assertNotNull(proxy);
    assertEquals(13.621935, proxy.getX(), 0.000001);
    assertEquals(-1.0, proxy.getScaleX(), 0.000001);
    assertEquals(0.5, proxy.getOriginX(), 0.000001);
    assertEquals(1.0, proxy.getOriginY(), 0.000001);
  }

  @Test
  void inlineTimelineCharacterTargetStillDrivesVnVisualOffset() {
    Engine engine = new Engine(ApplicationConfig.builder().build());
    RuntimeVnInterop interop = new RuntimeVnInterop(engine);
    VnScene vn = new VnScene(new VnScenarioBuilder("owner").end().build());
    vn.setInterop(interop);
    engine.scenes().push(vn);
    vn.getState().showCharacter(CharacterPosition.CENTER, "john", "neutral");

    interop.getBase().handle(new VnExternalCommand("jes_timeline_inline", """
        timeline {
          move "john" {
            x: -120
            y: 35
            dur: 100
          }
        }
        """), vn);

    vn.getState().updateTimelineRunners(100);

    var visual = vn.getState().getCharacterVisual(CharacterPosition.CENTER);
    assertNotNull(visual);
    assertEquals(-120.0, visual.getOffsetX(), 0.001);
    assertEquals(35.0, visual.getOffsetY(), 0.001);
  }
}
