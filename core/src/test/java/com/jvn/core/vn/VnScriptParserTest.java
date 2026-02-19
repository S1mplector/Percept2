package com.jvn.core.vn;

import com.jvn.core.vn.script.VnScriptParser;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

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

  @Test
  public void parsesIncludesAndDefines() throws Exception {
    String script = """
      @scenario test_story
      @define hero Alice
      @include common.vns

      ${hero}: Main line.
      [end]
    """;
    String common = """
      @label start
      ${hero}: From include.
    """;
    Map<String, String> includes = Map.of("common.vns", common);
    VnScriptParser parser = new VnScriptParser();
    try (var in = new ByteArrayInputStream(script.getBytes(StandardCharsets.UTF_8))) {
      VnScenario scen = parser.parse(in, "main.vns", path -> {
        String content = includes.get(path);
        if (content == null) throw new IOException("missing include " + path);
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
      });
      assertEquals("test_story", scen.getId());
      assertTrue(scen.getNodes().size() >= 3);
      VnNode line = scen.getNodes().get(0);
      assertEquals("Alice", line.getDialogue().getSpeakerName());
      assertEquals("From include.", line.getDialogue().getText());
    }
  }

  @Test
  public void parsesLegacyWizardSyntax() throws Exception {
    String script = """
      # Legacy sample style used by early project templates
      label start
      narrator "Welcome to {b}My Visual Novel{/b}."
      narrator "This is a sample scene."
      [choice Continue->next | Exit->ending]
      
      label next
      narrator "You chose to continue."
      [jump ending]
      
      label ending
      narrator "{wave}The End{/wave}"
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    try (var in = new ByteArrayInputStream(script.getBytes(StandardCharsets.UTF_8))) {
      VnScenario scen = parser.parse(in);
      assertEquals("untitled", scen.getId());
      assertNotNull(scen.getLabelIndex("start"));
      assertNotNull(scen.getLabelIndex("next"));
      assertNotNull(scen.getLabelIndex("ending"));
      assertTrue(scen.getNodes().stream().anyMatch(n -> n.getType() == VnNodeType.CHOICE));
      assertTrue(scen.getNodes().stream().anyMatch(n -> n.getType() == VnNodeType.END));
    }
  }
}
