package com.jvn.scripting.jes;

import com.jvn.scripting.jes.ast.JesAst;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class JesTimelineParseTest {
  private JesAst.Program parse(String src) {
    List<JesToken> toks = new JesTokenizer(src).tokenize();
    return new JesParser(toks).parseProgram();
  }

  @Test
  public void parsesParallelAndLoop() {
    String src = """
      scene "Demo" {
        timeline {
          parallel {
            move "a" { x: 10 dur: 100 }
            fade "b" { alpha: 0 dur: 50 }
          }
          loop 2 {
            wait 10
          }
        }
      }
      """;
    JesAst.Program p = parse(src);
    assertEquals(1, p.scenes.size());
    JesAst.TimelineAction par = p.scenes.get(0).timeline.get(0);
    assertEquals("parallel", par.type);
    assertEquals(2, par.children.size());
    JesAst.TimelineAction loop = p.scenes.get(0).timeline.get(1);
    assertEquals("loop", loop.type);
    assertEquals(1, loop.children.size());
    assertEquals(2.0, loop.props.get("count"));
  }

  @Test
  public void parsesCameraFollowAndEmitAndWaitForCall() {
    String src = """
      scene "Demo" {
        timeline {
          waitForCall "spawned"
          emitParticles "fx" { count: 5 }
          cameraFollow "hero" { lerp: 0.3 offsetX: 5 offsetY: -2 }
        }
      }
      """;
    JesAst.Program p = parse(src);
    var tl = p.scenes.get(0).timeline;
    assertEquals("waitForCall", tl.get(0).type);
    assertEquals("emitParticles", tl.get(1).type);
    assertEquals("fx", tl.get(1).target);
    assertEquals("cameraFollow", tl.get(2).type);
    assertEquals("hero", tl.get(2).target);
  }

  @Test
  public void parsesEventTimelineAction() {
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
    JesAst.Program p = parse(src);
    var action = p.scenes.get(0).timeline.get(0);
    assertEquals("event", action.type);
    assertEquals("script_call", action.target);
    assertEquals("spawnParticles", action.props.get("handler"));
    assertEquals(12.0, action.props.get("count"));
    assertEquals(Boolean.TRUE, action.props.get("burst"));
  }

  @Test
  public void parsesPuppeteerTimelineAliasesAndEvents() {
    String src = """
      scene "Demo" {
        timeline {
          rotate "hero" { rotation: 15 duration: 120 interp: hold easing: spring(220, 24, 1, 0) }
          scale "hero" { x: 1.2 y: 0.8 duration: 120 }
          depth "hero" { z: 4 duration: 1 }
          expression "hero" { value: smile }
          property "hero" { key: "effect.blur" value: 4 duration: 80 }
          playAudio "assets/audio/sfx/click.wav" { channel: sound fade_in: 10 }
        }
      }
      """;
    JesAst.Program p = parse(src);
    var tl = p.scenes.get(0).timeline;
    assertEquals("rotate", tl.get(0).type);
    assertEquals(15.0, tl.get(0).props.get("deg"));
    assertEquals(120.0, tl.get(0).props.get("dur"));
    assertEquals("spring(220, 24, 1, 0)", tl.get(0).props.get("easing"));
    assertEquals("scale", tl.get(1).type);
    assertEquals(1.2, tl.get(1).props.get("sx"));
    assertEquals(0.8, tl.get(1).props.get("sy"));
    assertEquals("depth", tl.get(2).type);
    assertEquals("event", tl.get(3).type);
    assertEquals("expression", tl.get(3).target);
    assertEquals("hero", tl.get(3).props.get("target"));
    assertEquals("property", tl.get(4).type);
    assertEquals("effect.blur", tl.get(4).props.get("key"));
    assertEquals(80.0, tl.get(4).props.get("dur"));
    assertEquals("playAudio", tl.get(5).type);
    assertEquals("assets/audio/sfx/click.wav", tl.get(5).props.get("id"));
    assertEquals(10.0, tl.get(5).props.get("fadeinms"));
  }

  @Test
  public void parsesCssStyleFunctionEasingLiteral() {
    String src = """
      scene "Demo" {
        timeline {
          move "hero" { x: 10 dur: 100 easing: cubic-bezier(0.22, 1, 0.36, 1) }
        }
      }
      """;
    JesAst.Program p = parse(src);
    var action = p.scenes.get(0).timeline.get(0);
    assertEquals("cubic-bezier(0.22, 1, 0.36, 1)", action.props.get("easing"));
  }
}
