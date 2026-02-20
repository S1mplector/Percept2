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

  @Test
  public void parsesIfElifElseControlFlowAndExecutesExpectedBranch() throws Exception {
    String script = """
      @label start
      [set courage 1]
      [if courage >= 2]
      narrator "High courage"
      [elif courage >= 1]
      narrator "Medium courage"
      [else]
      narrator "Low courage"
      [endif]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scenario;
    try (var in = new ByteArrayInputStream(script.getBytes(StandardCharsets.UTF_8))) {
      scenario = parser.parse(in);
    }

    VnScene scene = new VnScene(scenario);
    scene.setInterop(new DefaultVnInterop());
    scene.onEnter();

    assertFalse(scene.getState().getHistory().getEntries().isEmpty());
    VnHistory.HistoryEntry first = scene.getState().getHistory().getEntries().get(0);
    assertEquals("narrator", first.getSpeaker());
    assertEquals("Medium courage", first.getText());
  }

  @Test
  public void rejectsUnknownCommandsWithStrictDiagnostics() {
    String script = """
      @label start
      [mystery doSomething]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    IOException ex = assertThrows(IOException.class, () -> parser.parseFromString(script));
    assertTrue(ex.getMessage().contains("Unknown command [mystery]"));
  }

  @Test
  public void rejectsUndefinedLabels() {
    String script = """
      @label start
      [jump missing_label]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    IOException ex = assertThrows(IOException.class, () -> parser.parseFromString(script));
    assertTrue(ex.getMessage().contains("Undefined label 'missing_label'"));
  }

  @Test
  public void rejectsUnclosedIfBlocks() {
    String script = """
      @label start
      [if score > 0]
      narrator "still open"
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    IOException ex = assertThrows(IOException.class, () -> parser.parseFromString(script));
    assertTrue(ex.getMessage().contains("Unclosed [if] block"));
  }

  @Test
  public void supportsCharacterGlobalPositionCommands() throws Exception {
    String script = """
      @label start
      [char hero global on]
      [show hero center neutral]
      [char hero move right smile]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scenario;
    try (var in = new ByteArrayInputStream(script.getBytes(StandardCharsets.UTF_8))) {
      scenario = parser.parse(in);
    }

    VnScene scene = new VnScene(scenario);
    scene.setInterop(new DefaultVnInterop());
    scene.onEnter();

    assertTrue(scene.getState().getVisibleCharacters().containsKey(CharacterPosition.RIGHT));
    assertEquals("hero", scene.getState().getVisibleCharacters().get(CharacterPosition.RIGHT).getCharacterId());
    assertEquals("neutral", scene.getState().getVisibleCharacters().get(CharacterPosition.RIGHT).getExpression());

    scene.update(400);
    assertEquals("smile", scene.getState().getVisibleCharacters().get(CharacterPosition.RIGHT).getExpression());
    assertEquals(CharacterPosition.RIGHT, scene.getState().getCharacterDefinedPosition("hero"));
  }
}
