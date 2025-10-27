package com.jvn.core.vn;

import com.jvn.core.vn.script.VnScriptParser;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class VnScriptParserTest {
  @Test
  public void parsesMinimalScript() throws Exception {
    String script = """
      @scenario test_story
      @character alice "Alice"
      @background room game/images/bg_room.png

      [background room]
      Alice: Hello there!
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    try (var in = new ByteArrayInputStream(script.getBytes(StandardCharsets.UTF_8))) {
      VnScenario scen = parser.parse(in);
      assertEquals("test_story", scen.getId());
      assertNotNull(scen.getBackground("room"));
      assertNotNull(scen.getCharacter("alice"));
      assertTrue(scen.getNodes().size() >= 3);

      VnNode first = scen.getNodes().get(0);
      assertEquals(VnNodeType.BACKGROUND, first.getType());
      VnNode second = scen.getNodes().get(1);
      assertEquals(VnNodeType.DIALOGUE, second.getType());
      assertEquals("Alice", second.getDialogue().getSpeakerName());
      assertTrue(second.getDialogue().getText().startsWith("Hello"));
    }
  }
}
