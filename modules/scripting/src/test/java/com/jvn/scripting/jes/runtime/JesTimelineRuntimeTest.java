package com.jvn.scripting.jes.runtime;

import com.jvn.core.graphics.Camera2D;
import com.jvn.core.scene2d.Sprite2D;
import com.jvn.scripting.jes.JesParser;
import com.jvn.scripting.jes.JesTokenizer;
import com.jvn.scripting.jes.ast.JesAst;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class JesTimelineRuntimeTest {
  private JesAst.SceneDecl sceneFrom(String src) {
    List<com.jvn.scripting.jes.JesToken> toks = new JesTokenizer(src).tokenize();
    JesAst.Program prog = new JesParser(toks).parseProgram();
    return prog.scenes.get(0);
  }

  @Test
  public void waitForCallThenMove() throws Exception {
    String src = """
      scene "Demo" {
        entity "s" { component Sprite2D { x: 0 y: 0 w: 10 h: 10 image: "a.png" } }
        timeline { waitForCall "go" move "s" { x: 10 y: 0 dur: 100 } }
      }
      """;
    JesScene2D js = com.jvn.scripting.jes.JesLoader.load(src);
    Sprite2D s = (Sprite2D) js.find("s");
    js.update(16);
    assertEquals(0.0, s.getX(), 1e-6); // still waiting
    js.invokeCall("go", Map.of());
    js.update(50);
    js.update(60); // finish move
    assertEquals(10.0, s.getX(), 1e-3);
  }

  @Test
  public void parallelRunsChildrenAndAudioAction() throws Exception {
    String src = """
      scene "Demo" {
        entity "a" { component Sprite2D { x: 0 y: 0 w: 1 h: 1 image: "a.png" } }
        timeline {
          parallel {
            move "a" { x: 5 dur: 50 }
            playAudio "bgm"
          }
        }
      }
      """;
    JesScene2D js = com.jvn.scripting.jes.JesLoader.load(src);
    Sprite2D a = (Sprite2D) js.find("a");
    List<String> actions = new ArrayList<>();
    js.setActionHandler((name, props) -> actions.add(name));
    js.update(60);
    assertEquals(5.0, a.getX(), 1e-3);
    assertTrue(actions.contains("playAudio"));
  }

  @Test
  public void cameraFollowOffsets() throws Exception {
    String src = """
      scene "Demo" {
        entity "hero" { component Sprite2D { x: 3 y: 4 w: 1 h: 1 image: "a.png" } }
        timeline { cameraFollow "hero" { lerp: 1 offsetX: 2 offsetY: -1 } }
      }
      """;
    JesScene2D js = com.jvn.scripting.jes.JesLoader.load(src);
    Camera2D cam = new Camera2D();
    js.setCamera(cam);
    js.update(16);
    assertEquals(5.0, cam.getX(), 1e-6); // 3 + 2
    assertEquals(3.0, cam.getY(), 1e-6); // 4 - 1
  }

  @Test
  public void eventTimelineActionInvokesNamedCall() throws Exception {
    String src = """
      scene "Demo" {
        timeline {
          event "script_call" {
            handler: spawnParticles
            count: 12
            burst: true
          }
        }
      }
      """;
    JesScene2D js = com.jvn.scripting.jes.JesLoader.load(src);
    List<Map<String,Object>> calls = new ArrayList<>();
    js.registerCall("spawnParticles", calls::add);

    js.update(16);

    assertEquals(1, calls.size());
    assertEquals(12.0, calls.get(0).get("count"));
    assertEquals(Boolean.TRUE, calls.get(0).get("burst"));
  }

  @Test
  public void genericEventTimelineActionInvokesMatchingCall() throws Exception {
    String src = """
      scene "Demo" {
        timeline {
          event "doorOpened" { id: "north" }
        }
      }
      """;
    JesScene2D js = com.jvn.scripting.jes.JesLoader.load(src);
    List<Map<String,Object>> calls = new ArrayList<>();
    js.registerCall("doorOpened", calls::add);

    js.update(16);

    assertEquals(1, calls.size());
    assertEquals("north", calls.get(0).get("id"));
  }

  @Test
  public void parallelEventTimelineActionRunsAsAsyncChild() throws Exception {
    String src = """
      scene "Demo" {
        entity "a" { component Sprite2D { x: 0 y: 0 w: 1 h: 1 image: "a.png" } }
        timeline {
          parallel {
            move "a" { x: 5 dur: 50 }
            event "script_call" { handler: beat index: 1 }
          }
        }
      }
      """;
    JesScene2D js = com.jvn.scripting.jes.JesLoader.load(src);
    List<Map<String,Object>> calls = new ArrayList<>();
    js.registerCall("beat", calls::add);

    js.update(60);

    assertEquals(1, calls.size());
    assertEquals(1.0, calls.get(0).get("index"));
  }

  @Test
  public void puppeteerDepthAndPropertyActionsApplyAtRuntime() throws Exception {
    String src = """
      scene "Demo" {
        entity "a" { component Sprite2D { x: 0 y: 0 w: 1 h: 1 image: "a.png" } }
        timeline {
          depth "a" { z: 7 duration: 50 }
          property "a" { key: "effect.blur" value: 6 duration: 50 }
          brightness "a" { value: 0.5 duration: 50 }
        }
      }
      """;
    JesScene2D js = com.jvn.scripting.jes.JesLoader.load(src);
    Sprite2D a = (Sprite2D) js.find("a");

    js.update(60);
    assertEquals(7.0, a.getZ(), 1e-3);
    js.update(60);
    assertEquals(6.0, a.readCustomProperty("effect.blur"), 1e-3);
    js.update(60);
    assertEquals(0.5, a.getBrightness(), 1e-3);
  }
}
