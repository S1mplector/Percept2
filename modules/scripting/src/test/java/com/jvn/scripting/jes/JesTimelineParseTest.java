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
}
