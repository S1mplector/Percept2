package com.jvn.scripting.jes;

import com.jvn.scripting.jes.ast.JesAst;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class JesParserDiagnosticsTest {

  private JesAst.Program parse(String src) {
    List<JesToken> toks = new JesTokenizer(src).tokenize();
    return new JesParser(toks).parseProgram();
  }

  @Test
  public void parsesValidScene() {
    String src = """
      scene "Demo" {
        entity "p" { component Panel2D { x: 1 y: 2 w: 3 h: 4 fill: rgb(1,1,1,1) } }
        on key "D" do toggleDebug
        timeline { wait 100 }
      }
      """;
    JesAst.Program program = parse(src);
    assertEquals(1, program.scenes.size());
    assertEquals("Demo", program.scenes.get(0).name);
    assertEquals(1, program.scenes.get(0).timeline.size());
  }

  @Test
  public void rejectsUnknownTimelineAction() {
    String src = """
      scene "Demo" {
        timeline {
          fly "hero" { x: 1 }
        }
      }
      """;
    JesParseException ex = assertThrows(JesParseException.class, () -> parse(src));
    assertTrue(ex.getMessage().contains("Unknown timeline action"), ex.getMessage());
    assertTrue(ex.getLine() > 0);
    assertTrue(ex.getCol() > 0);
  }

  @Test
  public void rejectsUnknownComponentProperty() {
    String src = """
      scene "Demo" {
        entity "logo" {
          component Sprite2D { x: 1 bogus: 2 }
        }
      }
      """;
    JesParseException ex = assertThrows(JesParseException.class, () -> parse(src));
    assertTrue(ex.getMessage().contains("Unknown property 'bogus'"), ex.getMessage());
  }

  @Test
  public void tokenizerReportsUnexpectedCharacter() {
    JesParseException ex = assertThrows(JesParseException.class, () -> new JesTokenizer("scene \"X\" { @ }").tokenize());
    assertEquals(1, ex.getLine());
    assertTrue(ex.getCol() > 0);
    assertTrue(ex.getMessage().contains("@"));
  }
}
