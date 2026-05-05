package com.jvn.core.vn;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.jvn.core.vn.script.VnScriptParser;

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
  public void parsesCharacterLayerPresetsIntoLayeredCharimgExpressions() throws Exception {
    String script = """
      @scenario layered_demo
      @character lavender "Lavender"
      @charlayer lavender base assets/demo/characters/lavender/base/lavender_test_sprite_base.png
      @charlayer lavender eyes_neutral assets/demo/characters/lavender/eyes/lavender_test_sprite_eyes_neutral.png
      @charlayer lavender mouth_smile assets/demo/characters/lavender/mouth/lavender_test_sprite_mouth_smile.png
      @charpreset lavender talking $base | $eyes_neutral | $mouth_smile

      @label start
      [show lavender center talking]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);

    VnCharacter lavender = scen.getCharacter("lavender");
    assertNotNull(lavender);
    assertEquals(
        "assets/demo/characters/lavender/base/lavender_test_sprite_base.png"
            + " | assets/demo/characters/lavender/eyes/lavender_test_sprite_eyes_neutral.png"
            + " | assets/demo/characters/lavender/mouth/lavender_test_sprite_mouth_smile.png",
        lavender.getExpressionPath("talking"));
  }

  @Test
  public void parsesGroupedLayerReferencesAndPresetComposition() throws Exception {
    String script = """
      @scenario layered_demo
      @character lavender "Lavender"
      @charlayer lavender base assets/demo/characters/lavender/base/lavender_test_sprite_base.png
      @charlayer lavender body_school assets/demo/characters/lavender/body/lavender_test_sprite_body_school.png
      @charlayer lavender eyes_half_closed assets/demo/characters/lavender/eyes/lavender_test_sprite_eyes_half_closed.png
      @charlayer lavender mouth_smile assets/demo/characters/lavender/mouth/lavender_test_sprite_mouth_smile.png
      @charpreset lavender neutral $base | $body=school | $eyes=half_closed
      @charpreset lavender talking @neutral | $mouth=smile

      @label start
      [show lavender center talking]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);

    VnCharacter lavender = scen.getCharacter("lavender");
    assertNotNull(lavender);
    assertEquals(
        "assets/demo/characters/lavender/base/lavender_test_sprite_base.png"
            + " | assets/demo/characters/lavender/body/lavender_test_sprite_body_school.png"
            + " | assets/demo/characters/lavender/eyes/lavender_test_sprite_eyes_half_closed.png"
            + " | assets/demo/characters/lavender/mouth/lavender_test_sprite_mouth_smile.png",
        lavender.getExpressionPath("talking"));
  }

  @Test
  public void showCommandAcceptsExplicitPresetReference() throws Exception {
    String script = """
      @scenario layered_demo
      @character lavender "Lavender"
      @charlayer lavender base assets/demo/characters/lavender/base/lavender_test_sprite_base.png
      @charlayer lavender eyes_neutral assets/demo/characters/lavender/eyes/lavender_test_sprite_eyes_neutral.png
      @charlayer lavender mouth_smile assets/demo/characters/lavender/mouth/lavender_test_sprite_mouth_smile.png
      @charpreset lavender talking $base | $eyes_neutral | $mouth_smile

      @label start
      [show lavender center @talking]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);
    VnNode show = scen.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.SHOW)
        .findFirst().orElseThrow();
    assertEquals("talking", show.getShowExpression());
  }

  @Test
  public void showCommandSupportsInlineCompositePresetAndLayer() throws Exception {
    String script = """
      @scenario layered_demo
      @character lavender "Lavender"
      @charlayer lavender base assets/demo/characters/lavender/base/lavender_test_sprite_base.png
      @charlayer lavender eyes_neutral assets/demo/characters/lavender/eyes/lavender_test_sprite_eyes_neutral.png
      @charlayer lavender mouth_smile assets/demo/characters/lavender/mouth/lavender_test_sprite_mouth_smile.png
      @charlayer lavender glasses assets/demo/characters/lavender/props/lavender_glasses.png
      @charpreset lavender talking $base | $eyes_neutral | $mouth_smile

      @label start
      [show lavender center @talking+$glasses]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);
    VnNode show = scen.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.SHOW)
        .findFirst().orElseThrow();
    VnCharacter lavender = scen.getCharacter("lavender");
    assertNotNull(lavender);
    assertTrue(show.getShowExpression().startsWith("__inline_"));
    assertEquals(
        "assets/demo/characters/lavender/base/lavender_test_sprite_base.png"
            + " | assets/demo/characters/lavender/eyes/lavender_test_sprite_eyes_neutral.png"
            + " | assets/demo/characters/lavender/mouth/lavender_test_sprite_mouth_smile.png"
            + " | assets/demo/characters/lavender/props/lavender_glasses.png",
        lavender.getExpressionPath(show.getShowExpression()));
  }

  @Test
  public void moveCommandSupportsInlineCompositeLayerSpec() throws Exception {
    String script = """
      @scenario layered_demo
      @character lavender "Lavender"
      @charlayer lavender base assets/demo/characters/lavender/base/lavender_test_sprite_base.png
      @charlayer lavender eyes_neutral assets/demo/characters/lavender/eyes/lavender_test_sprite_eyes_neutral.png
      @charlayer lavender mouth_smile assets/demo/characters/lavender/mouth/lavender_test_sprite_mouth_smile.png

      @label start
      [move lavender center $base+$eyes_neutral+$mouth_smile ease_out_back 500]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);
    VnNode move = scen.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.MOVE)
        .findFirst().orElseThrow();
    VnCharacter lavender = scen.getCharacter("lavender");
    assertNotNull(lavender);
    assertTrue(move.getShowExpression().startsWith("__inline_"));
    assertEquals(
        "assets/demo/characters/lavender/base/lavender_test_sprite_base.png"
            + " | assets/demo/characters/lavender/eyes/lavender_test_sprite_eyes_neutral.png"
            + " | assets/demo/characters/lavender/mouth/lavender_test_sprite_mouth_smile.png",
        lavender.getExpressionPath(move.getShowExpression()));
    assertEquals(com.jvn.core.animation.Easing.Type.EASE_OUT_BACK, move.getMoveEasingType());
    assertEquals(500, move.getMoveDurationMs());
  }

  @Test
  public void moveCommandAcceptsCamelCaseEasingAliases() throws Exception {
    String script = """
      @label start
      [move hero center easeInOut 500]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scenario = parser.parseFromString(script);
    VnNode move = scenario.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.MOVE)
        .findFirst()
        .orElseThrow();

    assertEquals(com.jvn.core.animation.Easing.Type.EASE_IN_OUT_QUAD, move.getMoveEasingType());
    assertEquals(500, move.getMoveDurationMs());
    assertEquals("neutral", move.getShowExpression());
  }

  @Test
  public void showCommandSupportsGroupedInlineCompositeLayerSpec() throws Exception {
    String script = """
      @scenario layered_demo
      @character lavender "Lavender"
      @charlayer lavender base assets/demo/characters/lavender/base/lavender_test_sprite_base.png
      @charlayer lavender eyes_half_closed assets/demo/characters/lavender/eyes/lavender_test_sprite_eyes_half_closed.png
      @charlayer lavender mouth_smile assets/demo/characters/lavender/mouth/lavender_test_sprite_mouth_smile.png

      @label start
      [show lavender center $base+$eyes=half_closed+$mouth=smile]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);
    VnNode show = scen.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.SHOW)
        .findFirst().orElseThrow();
    VnCharacter lavender = scen.getCharacter("lavender");
    assertNotNull(lavender);
    assertTrue(show.getShowExpression().startsWith("__inline_"));
    assertEquals(
        "assets/demo/characters/lavender/base/lavender_test_sprite_base.png"
            + " | assets/demo/characters/lavender/eyes/lavender_test_sprite_eyes_half_closed.png"
            + " | assets/demo/characters/lavender/mouth/lavender_test_sprite_mouth_smile.png",
        lavender.getExpressionPath(show.getShowExpression()));
  }

  @Test
  public void showCommandSupportsNamedOptions() throws Exception {
    String script = """
      @scenario layered_demo
      @character lavender "Lavender"
      @charlayer lavender base assets/demo/characters/lavender/base/lavender_test_sprite_base.png
      @charlayer lavender eyes_neutral assets/demo/characters/lavender/eyes/lavender_test_sprite_eyes_neutral.png
      @charlayer lavender mouth_smile assets/demo/characters/lavender/mouth/lavender_test_sprite_mouth_smile.png
      @charpreset lavender talking $base | $eyes_neutral | $mouth_smile

      @label start
      [show lavender pos=center expr=@talking layer=25]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scenario = parser.parseFromString(script);
    VnNode show = scenario.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.SHOW)
        .findFirst()
        .orElseThrow();

    assertEquals(CharacterPosition.CENTER, show.getShowPosition());
    assertEquals("talking", show.getShowExpression());
    assertEquals(Integer.valueOf(25), show.getShowLayerOrder());
  }

  @Test
  public void characterInteropExpressionCommandSupportsInlineCompositePresetAndLayer() throws Exception {
    String script = """
      @scenario layered_demo
      @character lavender "Lavender"
      @charlayer lavender base assets/demo/characters/lavender/base/lavender_test_sprite_base.png
      @charlayer lavender eyes_neutral assets/demo/characters/lavender/eyes/lavender_test_sprite_eyes_neutral.png
      @charlayer lavender mouth_smile assets/demo/characters/lavender/mouth/lavender_test_sprite_mouth_smile.png
      @charlayer lavender glasses assets/demo/characters/lavender/props/lavender_glasses.png
      @charpreset lavender talking $base | $eyes_neutral | $mouth_smile

      @label start
      [char lavender global on]
      [show lavender center @talking]
      [char lavender expression @talking+$glasses]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scenario = parser.parseFromString(script);
    VnScene scene = new VnScene(scenario);
    scene.setInterop(new DefaultVnInterop());
    scene.onEnter();

    String expr = scene.getState().getCharacterExpression("lavender");
    assertNotNull(expr);
    assertTrue(expr.startsWith("__inline_"));
    assertEquals(
        "assets/demo/characters/lavender/base/lavender_test_sprite_base.png"
            + " | assets/demo/characters/lavender/eyes/lavender_test_sprite_eyes_neutral.png"
            + " | assets/demo/characters/lavender/mouth/lavender_test_sprite_mouth_smile.png"
            + " | assets/demo/characters/lavender/props/lavender_glasses.png",
        scenario.getCharacter("lavender").getExpressionPath(expr));
  }

  @Test
  public void rejectsCharacterPresetWithUnknownLayerReference() {
    String script = """
      @scenario layered_demo
      @charlayer lavender base assets/demo/characters/lavender/base/lavender_test_sprite_base.png
      @charpreset lavender broken $base | $eyes_neutral
      @label start
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    IOException ex = assertThrows(IOException.class, () -> parser.parseFromString(script));
    assertTrue(ex.getMessage().contains("Unknown @charlayer reference '$eyes_neutral'"));
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
  public void parsesPhoneInteropCommandsAsExternalNodes() throws Exception {
    String script = """
      @scenario phone_demo
      @label start
      [phone contact ll name="Lily" avatar="assets/phone/lily.png"]
      [phone message mc_lily ll "You awake?" time=08:14]
      [phone chat mc_lily]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scenario = parser.parseFromString(script);

    List<VnNode> externals = scenario.getNodes().stream()
        .filter(node -> node.getType() == VnNodeType.EXTERNAL)
        .toList();

    assertEquals(3, externals.size());
    assertEquals("phone", externals.get(0).getExternalCommand().getProvider());
    assertEquals("contact ll name=\"Lily\" avatar=\"assets/phone/lily.png\"", externals.get(0).getExternalCommand().getPayload());
    assertEquals("message mc_lily ll \"You awake?\" time=08:14", externals.get(1).getExternalCommand().getPayload());
    assertEquals("chat mc_lily", externals.get(2).getExternalCommand().getPayload());
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
  public void parsesVisualizerCommandIntoUiInteropPayload() throws Exception {
    String script = """
      @label start
      [visualizer on]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);
    VnNode ext = scen.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.EXTERNAL
            && "ui".equals(n.getExternalCommand().getProvider()))
        .findFirst().orElseThrow();
    assertEquals("visualizer on", ext.getExternalCommand().getPayload());
  }

  @Test
  public void parsesVisualizerCommandWithoutArgAsToggle() throws Exception {
    String script = """
      @label start
      [visualizer]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);
    VnNode ext = scen.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.EXTERNAL
            && "ui".equals(n.getExternalCommand().getProvider()))
        .findFirst().orElseThrow();
    assertEquals("visualizer toggle", ext.getExternalCommand().getPayload());
  }

  @Test
  public void parsesModeCommandIntoModeInteropPayload() throws Exception {
    String script = """
      @label start
      [mode dialogue standard]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);
    VnNode ext = scen.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.EXTERNAL
            && "mode".equals(n.getExternalCommand().getProvider()))
        .findFirst().orElseThrow();
    assertEquals("dialogue standard", ext.getExternalCommand().getPayload());
  }

  @Test
  public void parsesInlineTimelineShowcaseScript() throws Exception {
    String script = """
      @scenario runtime_showcase
      @character narrator "Narrator"
      @character hero "Hero"
      @background classroom game/images/backgrounds/classroom_day.png

      @label start
      [bg classroom]
      Narrator: Timeline parser smoke test.

      timeline {
        move "hero" {
          x: 620
          y: 400
          dur: 420
          easing: ease_in_out_cubic
        }
        rotate "hero" {
          angle: -8
          dur: 420
          easing: ease_in_out_sine
        }
      }
      [wait 450]
      [end]
      """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);

    assertNotNull(scen);
    assertEquals("runtime_showcase", scen.getId());
    assertTrue(scen.getNodes().stream().anyMatch(n ->
        n.getType() == VnNodeType.EXTERNAL
            && "jes_timeline_inline".equals(n.getExternalCommand().getProvider())));
  }

  @Test
  public void parsesVisualizerCommandWithBarsOption() throws Exception {
    String script = """
      @label start
      [visualizer on bars=48]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);
    VnNode ext = scen.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.EXTERNAL
            && "ui".equals(n.getExternalCommand().getProvider()))
        .findFirst().orElseThrow();
    assertEquals("visualizer on bars=48", ext.getExternalCommand().getPayload());
  }

  @Test
  public void parsesVisualizerConfigCommandWithStyleOptions() throws Exception {
    String script = """
      @label start
      [visualizer set bars=32 color=#7de2ff glow=off style=minimal z=-15]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);
    VnNode ext = scen.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.EXTERNAL
            && "ui".equals(n.getExternalCommand().getProvider()))
        .findFirst().orElseThrow();
    assertEquals("visualizer set bars=32 color=#7de2ff glow=off style=minimal z=-15", ext.getExternalCommand().getPayload());
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

  @Test
  public void parsesShowCommandWithLayerOrder() throws Exception {
    String script = """
      @label start
      [show hero center neutral 25]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scenario;
    try (var in = new ByteArrayInputStream(script.getBytes(StandardCharsets.UTF_8))) {
      scenario = parser.parse(in);
    }

    VnNode show = scenario.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.SHOW)
        .findFirst()
        .orElseThrow();
    assertEquals(Integer.valueOf(25), show.getShowLayerOrder());
  }

  @Test
  public void moveCommandSupportsNamedOptions() throws Exception {
    String script = """
      @label start
      [move hero pos=right expr=smile ease=easeInOut dur=500]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scenario;
    try (var in = new ByteArrayInputStream(script.getBytes(StandardCharsets.UTF_8))) {
      scenario = parser.parse(in);
    }

    VnNode move = scenario.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.MOVE)
        .findFirst()
        .orElseThrow();
    assertEquals(CharacterPosition.RIGHT, move.getShowPosition());
    assertEquals("smile", move.getShowExpression());
    assertEquals(com.jvn.core.animation.Easing.Type.EASE_IN_OUT_QUAD, move.getMoveEasingType());
    assertEquals(500, move.getMoveDurationMs());
  }

  @Test
  public void transitionCommandSupportsNamedOptions() throws Exception {
    String script = """
      @label start
      [transition type=crossfade dur=700 bg=classroom]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scenario = parser.parseFromString(script);
    VnNode transition = scenario.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.TRANSITION)
        .findFirst()
        .orElseThrow();

    assertEquals(VnTransition.TransitionType.CROSSFADE, transition.getTransition().getType());
    assertEquals(700, transition.getTransition().getDurationMs());
    assertEquals("classroom", transition.getTransition().getTargetBackgroundId());
  }

  @Test
  public void waitCommandSupportsNamedOptions() throws Exception {
    String script = """
      @label start
      [wait ms=450]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scenario = parser.parseFromString(script);
    VnNode wait = scenario.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.WAIT)
        .findFirst()
        .orElseThrow();

    assertEquals(450, wait.getWaitMs());
  }

  @Test
  public void stageCommandSupportsNamedOptions() throws Exception {
    String script = """
      @label start
      [stage preset=dramatic_evening]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scenario = parser.parseFromString(script);
    VnNode stage = scenario.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.EXTERNAL
            && "stage".equals(n.getExternalCommand().getProvider()))
        .findFirst()
        .orElseThrow();

    assertEquals("dramatic_evening", stage.getExternalCommand().getPayload());
  }

  @Test
  public void settingsCommandsSupportNamedOptions() throws Exception {
    String script = """
      @label start
      [volume channel=bgm level=0.65]
      [textspeed value=42]
      [autodelay ms=1750]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scenario = parser.parseFromString(script);
    List<String> payloads = scenario.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.EXTERNAL
            && "settings".equals(n.getExternalCommand().getProvider()))
        .map(n -> n.getExternalCommand().getPayload())
        .toList();

    assertTrue(payloads.contains("volume bgm 0.65"));
    assertTrue(payloads.contains("textspeed 42"));
    assertTrue(payloads.contains("autodelay 1750"));
  }

  @Test
  public void particlesCommandSupportsNamedOptions() throws Exception {
    String script = """
      @label start
      [particles preset=rain intensity=0.75 layer=120]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scenario = parser.parseFromString(script);
    VnNode particles = scenario.getNodes().stream()
        .filter(n -> n.getParticleCommand() != null)
        .findFirst()
        .orElseThrow();

    assertEquals(VnParticleCommand.Preset.RAIN, particles.getParticleCommand().getPreset());
    assertEquals(0.75f, particles.getParticleCommand().getIntensity(), 0.0001f);
    assertEquals(120, particles.getParticleCommand().getLayer());
    assertFalse(particles.getParticleCommand().isStop());
  }

  @Test
  public void particlesCommandSupportsFxShapingOptionsAndAliases() throws Exception {
    String script = """
      @label start
      [pfx snow intensity=0.6 layer=90 opacity=0.35 speed=1.25 wind=-18 duration=2500 tint=#88aaff]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scenario = parser.parseFromString(script);
    VnParticleCommand command = scenario.getNodes().stream()
        .filter(n -> n.getParticleCommand() != null)
        .findFirst()
        .orElseThrow()
        .getParticleCommand();

    assertEquals(VnParticleCommand.Preset.SNOW, command.getPreset());
    assertEquals(0.6f, command.getIntensity(), 0.0001f);
    assertEquals(90, command.getLayer());
    assertEquals(0.35, command.getOpacityScale(), 0.0001);
    assertEquals(1.25, command.getSpeedScale(), 0.0001);
    assertEquals(-18.0, command.getWindX(), 0.0001);
    assertEquals(2500L, command.getDurationMs());
    assertEquals(0x0088AAFF, command.getTintArgb());
  }

  @Test
  public void weatherCommandSupportsNamedOptionsAndStopAlias() throws Exception {
    String script = """
      @label start
      [weather type=stop]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scenario = parser.parseFromString(script);
    VnNode particles = scenario.getNodes().stream()
        .filter(n -> n.getParticleCommand() != null)
        .findFirst()
        .orElseThrow();

    assertEquals(VnParticleCommand.Preset.NONE, particles.getParticleCommand().getPreset());
    assertTrue(particles.getParticleCommand().isStop());
  }

  @Test
  public void rejectsShowCommandWithUnknownPosition() {
    String script = """
      @label start
      [show hero middle neutral]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    IOException ex = assertThrows(IOException.class, () -> parser.parseFromString(script));
    assertTrue(ex.getMessage().contains("Unknown character position: middle"));
  }

  @Test
  public void rejectsTransitionWithNegativeDuration() {
    String script = """
      @label start
      [transition fade -10]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    IOException ex = assertThrows(IOException.class, () -> parser.parseFromString(script));
    assertTrue(ex.getMessage().contains("[transition] duration must be >= 0"));
  }

  @Test
  public void rejectsInlineTimelineWithoutOpeningBrace() {
    String script = """
      @label start
      timeline
      [show hero center neutral]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    IOException ex = assertThrows(IOException.class, () -> parser.parseFromString(script));
    assertTrue(ex.getMessage().contains("Expected '{' after timeline"));
  }

  @Test
  public void preservesInlineTimelineLineIndentation() throws Exception {
    String script = """
      @label start
      timeline {
          actor hero x 10
      }
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scenario = parser.parseFromString(script);
    VnNode inline = scenario.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.EXTERNAL
            && "jes_timeline_inline".equals(n.getExternalCommand().getProvider()))
        .findFirst()
        .orElseThrow();
    assertTrue(inline.getExternalCommand().getPayload().contains("    actor hero x 10"));
  }

  // ── Audio DSL tests ───────────────────────────────────────────────────

  @Test
  public void parsesBgmBasic() throws Exception {
    String script = """
      @label start
      [bgm theme_main]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);
    VnNode audio = scen.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.AUDIO).findFirst().orElseThrow();
    assertEquals("theme_main", audio.getAudioCommand().getTrackId());
    assertTrue(audio.getAudioCommand().isLoop());
  }

  @Test
  public void parsesBgmQuotedTrackPathWithSpaces() throws Exception {
    String script = """
      @label start
      [bgm "assets/demo/audio/03 - Definitely Our Town.mp3"]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);
    VnNode audio = scen.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.AUDIO).findFirst().orElseThrow();
    assertEquals("assets/demo/audio/03 - Definitely Our Town.mp3", audio.getAudioCommand().getTrackId());
    assertTrue(audio.getAudioCommand().isLoop());
  }

  @Test
  public void parsesBgmLoopFalse() throws Exception {
    String script = """
      @label start
      [bgm theme_main loop=false]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);
    VnNode audio = scen.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.AUDIO).findFirst().orElseThrow();
    assertEquals("theme_main", audio.getAudioCommand().getTrackId());
    assertFalse(audio.getAudioCommand().isLoop());
  }

  @Test
  public void parsesBgmWithVolume() throws Exception {
    String script = """
      @label start
      [bgm theme_main vol=0.6]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);
    // volume emits an EXTERNAL node after the AUDIO node
    VnNode audio = scen.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.AUDIO).findFirst().orElseThrow();
    assertEquals("theme_main", audio.getAudioCommand().getTrackId());
    assertTrue(audio.getAudioCommand().isLoop());

    VnNode ext = scen.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.EXTERNAL
            && n.getExternalCommand().getProvider().equals("settings"))
        .findFirst().orElseThrow();
    assertTrue(ext.getExternalCommand().getPayload().startsWith("volume bgm "));
  }

  @Test
  public void parsesBgmLoopAndVolumeCombined() throws Exception {
    String script = """
      @label start
      [bgm theme_main loop=off vol=0.8]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);
    VnNode audio = scen.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.AUDIO).findFirst().orElseThrow();
    assertFalse(audio.getAudioCommand().isLoop());

    VnNode ext = scen.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.EXTERNAL
            && n.getExternalCommand().getProvider().equals("settings"))
        .findFirst().orElseThrow();
    assertTrue(ext.getExternalCommand().getPayload().contains("volume bgm"));
  }

  @Test
  public void parsesBgmShorthandLoopFalse() throws Exception {
    String script = """
      @label start
      [bgm theme_main false]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);
    VnNode audio = scen.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.AUDIO).findFirst().orElseThrow();
    assertFalse(audio.getAudioCommand().isLoop());
  }

  @Test
  public void rejectsBgmUnknownOption() {
    String script = """
      @label start
      [bgm theme_main foo=bar]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    IOException ex = assertThrows(IOException.class, () -> parser.parseFromString(script));
    assertTrue(ex.getMessage().contains("[bgm] unknown option"));
  }

  @Test
  public void rejectsBgmInvalidVolume() {
    String script = """
      @label start
      [bgm theme_main vol=1.5]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    IOException ex = assertThrows(IOException.class, () -> parser.parseFromString(script));
    assertTrue(ex.getMessage().contains("volume must be between 0 and 1"));
  }

  // ── bgm_seek tests ───────────────────────────────────────────────────

  @Test
  public void parsesBgmSeekValid() throws Exception {
    String script = """
      @label start
      [bgm_seek 3.5]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);
    VnNode ext = scen.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.EXTERNAL
            && n.getExternalCommand().getProvider().equals("audio"))
        .findFirst().orElseThrow();
    assertEquals("seek 3.5", ext.getExternalCommand().getPayload());
  }

  @Test
  public void rejectsBgmSeekNonNumeric() {
    String script = """
      @label start
      [bgm_seek abc]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    IOException ex = assertThrows(IOException.class, () -> parser.parseFromString(script));
    assertTrue(ex.getMessage().contains("[bgm_seek] expects a numeric"));
  }

  @Test
  public void rejectsBgmSeekNegative() {
    String script = """
      @label start
      [bgm_seek -5]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    IOException ex = assertThrows(IOException.class, () -> parser.parseFromString(script));
    assertTrue(ex.getMessage().contains("[bgm_seek] seconds must be >= 0"));
  }

  // ── bgm_crossfade tests ──────────────────────────────────────────────

  @Test
  public void parsesBgmCrossfadeValid() throws Exception {
    String script = """
      @label start
      [bgm_crossfade new_track 2000]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);
    VnNode ext = scen.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.EXTERNAL
            && n.getExternalCommand().getProvider().equals("audio"))
        .findFirst().orElseThrow();
    assertEquals("crossfade new_track 2000", ext.getExternalCommand().getPayload());
  }

  @Test
  public void parsesBgmCrossfadeWithLoop() throws Exception {
    String script = """
      @label start
      [bgm_crossfade new_track 2000 loop=false]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);
    VnNode ext = scen.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.EXTERNAL
            && n.getExternalCommand().getProvider().equals("audio"))
        .findFirst().orElseThrow();
    assertEquals("crossfade new_track 2000 false", ext.getExternalCommand().getPayload());
  }

  @Test
  public void parsesBgmCrossfadeWithNamedOptions() throws Exception {
    String script = """
      @label start
      [bgm_crossfade track=new_track dur=2000 loop=false]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);
    VnNode ext = scen.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.EXTERNAL
            && n.getExternalCommand().getProvider().equals("audio"))
        .findFirst().orElseThrow();
    assertEquals("crossfade new_track 2000 false", ext.getExternalCommand().getPayload());
  }

  @Test
  public void parsesBgmCrossfadeQuotedTrackPathWithSpaces() throws Exception {
    String script = """
      @label start
      [bgm_crossfade "assets/demo/audio/03 - Definitely Our Town.mp3" 1200 loop=true]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);
    VnNode ext = scen.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.EXTERNAL
            && n.getExternalCommand().getProvider().equals("audio"))
        .findFirst().orElseThrow();
    assertEquals("crossfade \"assets/demo/audio/03 - Definitely Our Town.mp3\" 1200 true", ext.getExternalCommand().getPayload());
  }

  @Test
  public void parsesSfxQuotedTrackPathWithSpaces() throws Exception {
    String script = """
      @label start
      [sfx "assets/demo/audio/03 - Definitely Our Town.mp3"]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);
    VnNode audio = scen.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.AUDIO).findFirst().orElseThrow();
    assertEquals("assets/demo/audio/03 - Definitely Our Town.mp3", audio.getAudioCommand().getTrackId());
  }

  @Test
  public void parsesSfxWithNamedTrackOption() throws Exception {
    String script = """
      @label start
      [sfx track=ui/click.ogg]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);
    VnNode audio = scen.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.AUDIO).findFirst().orElseThrow();
    assertEquals("ui/click.ogg", audio.getAudioCommand().getTrackId());
  }

  @Test
  public void rejectsBgmCrossfadeMissingDuration() {
    String script = """
      @label start
      [bgm_crossfade new_track]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    IOException ex = assertThrows(IOException.class, () -> parser.parseFromString(script));
    assertTrue(ex.getMessage().contains("[bgm_crossfade] expects"));
  }

  @Test
  public void rejectsBgmCrossfadeNonNumericDuration() {
    String script = """
      @label start
      [bgm_crossfade new_track abc]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    IOException ex = assertThrows(IOException.class, () -> parser.parseFromString(script));
    assertTrue(ex.getMessage().contains("[bgm_crossfade] duration must be an integer"));
  }

  @Test
  public void rejectsBgmCrossfadeTooManyArgs() {
    String script = """
      @label start
      [bgm_crossfade new_track 2000 true extra]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    IOException ex = assertThrows(IOException.class, () -> parser.parseFromString(script));
    assertTrue(ex.getMessage().contains("[bgm_crossfade] too many arguments"));
  }

  @Test
  public void rejectsBgmCrossfadeNegativeDuration() {
    String script = """
      @label start
      [bgm_crossfade new_track -500]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    IOException ex = assertThrows(IOException.class, () -> parser.parseFromString(script));
    assertTrue(ex.getMessage().contains("[bgm_crossfade] duration must be >= 0"));
  }

  @Test
  public void parsesChannelStopCommands() throws Exception {
    String script = """
      @label start
      [sfx_stop]
      [voice_stop]
      [audio_stop_all]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);
    var audioPayloads = scen.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.EXTERNAL)
        .map(n -> n.getExternalCommand().getProvider() + ":" + n.getExternalCommand().getPayload())
        .toList();
    assertTrue(audioPayloads.contains("audio:sfx_stop"));
    assertTrue(audioPayloads.contains("audio:voice_stop"));
    assertTrue(audioPayloads.contains("audio:stop_all"));
  }

  @Test
  public void attachesVoiceCommandToFollowingDialogueLine() throws Exception {
    String script = """
      @label start
      [voice voice/alice_001.ogg]
      Alice: Hello there.
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scenario = parser.parseFromString(script);

    assertEquals(2, scenario.getNodes().size());
    VnNode dialogue = scenario.getNodes().get(0);
    assertEquals(VnNodeType.DIALOGUE, dialogue.getType());
    assertEquals("voice/alice_001.ogg", dialogue.getDialogue().getVoiceTrackId());
  }

  @Test
  public void attachesNamedVoiceTrackToFollowingDialogueLine() throws Exception {
    String script = """
      @label start
      [voice track=voice/alice_001.ogg]
      Alice: Hello there.
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scenario = parser.parseFromString(script);

    assertEquals(2, scenario.getNodes().size());
    VnNode dialogue = scenario.getNodes().get(0);
    assertEquals(VnNodeType.DIALOGUE, dialogue.getType());
    assertEquals("voice/alice_001.ogg", dialogue.getDialogue().getVoiceTrackId());
  }

  @Test
  public void keepsVoiceCommandStandaloneWhenNotFollowedByDialogue() throws Exception {
    String script = """
      @label start
      [voice voice/sting.ogg]
      [show hero center neutral]
      [end]
    """;

    VnScriptParser parser = new VnScriptParser();
    VnScenario scenario = parser.parseFromString(script);

    assertEquals(VnNodeType.AUDIO, scenario.getNodes().get(0).getType());
    assertEquals("voice/sting.ogg", scenario.getNodes().get(0).getAudioCommand().getTrackId());
    assertEquals(VnNodeType.SHOW, scenario.getNodes().get(1).getType());
  }

  @Test
  public void parsesAudioPauseResumeAllCommands() throws Exception {
    String script = """
      @label start
      [audio_pause_all]
      [audio_resume_all]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);
    var payloads = scen.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.EXTERNAL
            && "audio".equals(n.getExternalCommand().getProvider()))
        .map(n -> n.getExternalCommand().getPayload())
        .toList();
    assertTrue(payloads.contains("pause_all"));
    assertTrue(payloads.contains("resume_all"));
  }

  @Test
  public void parsesGenericAudioCommand() throws Exception {
    String script = """
      @label start
      [audio crossfade theme2 1500 true]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);
    VnNode ext = scen.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.EXTERNAL
            && "audio".equals(n.getExternalCommand().getProvider()))
        .findFirst().orElseThrow();
    assertEquals("crossfade theme2 1500 true", ext.getExternalCommand().getPayload());
  }

  @Test
  public void rejectsRemovedSynthesizerCommand() {
    String script = """
      @label start
      [synthesizer off]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    IOException ex = assertThrows(IOException.class, () -> parser.parseFromString(script));
    assertTrue(ex.getMessage().contains("Unknown command [synthesizer]"));
  }

  @Test
  public void parsesMoveCommandBasic() throws Exception {
    String script = """
      @label start
      [show hero center neutral]
      [move hero right]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);
    VnNode move = scen.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.MOVE)
        .findFirst().orElseThrow();
    assertEquals("hero", move.getCharacterToShow());
    assertEquals(CharacterPosition.RIGHT, move.getShowPosition());
    assertEquals(0, move.getMoveDurationMs());
    assertNull(move.getMoveEasingType());
  }

  @Test
  public void parsesMoveCommandWithEasing() throws Exception {
    String script = """
      @label start
      [show hero center neutral]
      [move hero far_left ease_out_bounce]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);
    VnNode move = scen.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.MOVE)
        .findFirst().orElseThrow();
    assertEquals("hero", move.getCharacterToShow());
    assertEquals(CharacterPosition.FAR_LEFT, move.getShowPosition());
    assertEquals(com.jvn.core.animation.Easing.Type.EASE_OUT_BOUNCE, move.getMoveEasingType());
  }

  @Test
  public void parsesMoveCommandWithExpressionEasingDuration() throws Exception {
    String script = """
      @label start
      [show hero center neutral]
      [move hero right smile ease_in_out_elastic 600]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);
    VnNode move = scen.getNodes().stream()
        .filter(n -> n.getType() == VnNodeType.MOVE)
        .findFirst().orElseThrow();
    assertEquals("hero", move.getCharacterToShow());
    assertEquals(CharacterPosition.RIGHT, move.getShowPosition());
    assertEquals("smile", move.getShowExpression());
    assertEquals(com.jvn.core.animation.Easing.Type.EASE_IN_OUT_ELASTIC, move.getMoveEasingType());
    assertEquals(600, move.getMoveDurationMs());
  }

  @Test
  public void moveCommandMovesCharacterWithGlobalPosition() throws Exception {
    String script = """
      @label start
      [char hero global on]
      [show hero center neutral]
      [move hero right smile ease_out_bounce]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    VnScenario scenario = parser.parseFromString(script);

    VnScene scene = new VnScene(scenario);
    scene.setInterop(new DefaultVnInterop());
    scene.onEnter();

    assertTrue(scene.getState().getVisibleCharacters().containsKey(CharacterPosition.RIGHT));
    assertEquals("hero", scene.getState().getVisibleCharacters().get(CharacterPosition.RIGHT).getCharacterId());

    scene.update(400);
    assertEquals("smile", scene.getState().getVisibleCharacters().get(CharacterPosition.RIGHT).getExpression());
  }

  @Test
  public void charInteropMoveSupportsEasing() throws Exception {
    String script = """
      @label start
      [char hero global on]
      [show hero center neutral]
      [char hero move right smile ease_out_back 500]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    VnScenario scenario = parser.parseFromString(script);

    VnScene scene = new VnScene(scenario);
    scene.setInterop(new DefaultVnInterop());
    scene.onEnter();

    assertTrue(scene.getState().getVisibleCharacters().containsKey(CharacterPosition.RIGHT));
    assertEquals("hero", scene.getState().getVisibleCharacters().get(CharacterPosition.RIGHT).getCharacterId());

    scene.update(600);
    assertEquals("smile", scene.getState().getVisibleCharacters().get(CharacterPosition.RIGHT).getExpression());
  }

  // ═══════════════════════════════════════════════════════════════════
  // Custom Position Tests
  // ═══════════════════════════════════════════════════════════════════

  @Test
  public void characterPositionPredefinedLookup() {
    assertEquals(CharacterPosition.LEFT, CharacterPosition.predefined("left"));
    assertEquals(CharacterPosition.CENTER, CharacterPosition.predefined("C"));
    assertEquals(CharacterPosition.FAR_RIGHT, CharacterPosition.predefined("FR"));
    assertNull(CharacterPosition.predefined("balcony"));
    assertNull(CharacterPosition.predefined(null));
  }

  @Test
  public void characterPositionCustomEquality() {
    CharacterPosition a = CharacterPosition.named("balcony", 0.3, 0.5);
    CharacterPosition b = CharacterPosition.named("balcony", 0.9, 0.1);
    assertEquals(a, b, "Named positions with the same name should be equal");
    assertEquals(a.hashCode(), b.hashCode());

    CharacterPosition c = CharacterPosition.at(0.3, 0.5);
    CharacterPosition d = CharacterPosition.at(0.3, 0.5);
    assertEquals(c, d, "Inline at positions with same coords should be equal");

    CharacterPosition e = CharacterPosition.at(0.7, 0.2);
    assertFalse(c.equals(e), "Different inline at positions should not be equal");
  }

  @Test
  public void characterPositionCustomRendering() {
    CharacterPosition custom = CharacterPosition.at(0.5, 0.8);
    assertTrue(custom.isCustom());
    assertTrue(custom.hasCustomY());
    assertEquals(0.5, custom.getXFraction(), 1e-6);
    assertEquals(0.8, custom.getYFraction(), 1e-6);

    // computeScreenX: custom centres sprite on xFraction
    double screenX = custom.computeScreenX(1920, 200);
    assertEquals(1920 * 0.5 - 100, screenX, 1e-6);

    // computeScreenY: custom uses yFraction
    double screenY = custom.computeScreenY(1080, 600, 0.85);
    assertEquals(1080 * 0.8 - 600, screenY, 1e-6);
  }

  @Test
  public void parsesPositionDirective() throws Exception {
    String script = """
      @scenario test_positions
      @character hero "Hero"
      @charimg hero neutral game/images/hero_neutral.png
      @position balcony 0.3 0.6
      @position rooftop 0.5

      [show hero balcony]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    VnScenario scenario = parser.parseFromString(script);
    VnNode showNode = scenario.getNodes().get(0);
    assertEquals(VnNodeType.SHOW, showNode.getType());
    CharacterPosition pos = showNode.getShowPosition();
    assertTrue(pos.isCustom());
    assertEquals("balcony", pos.getName());
    assertEquals(0.3, pos.getXFraction(), 1e-6);
    assertEquals(0.6, pos.getYFraction(), 1e-6);
  }

  @Test
  public void parsesPositionDirectiveXOnly() throws Exception {
    String script = """
      @scenario test_positions
      @character hero "Hero"
      @charimg hero neutral game/images/hero_neutral.png
      @position stage_left 0.15

      [show hero stage_left]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    VnScenario scenario = parser.parseFromString(script);
    VnNode showNode = scenario.getNodes().get(0);
    CharacterPosition pos = showNode.getShowPosition();
    assertTrue(pos.isCustom());
    assertEquals("stage_left", pos.getName());
    assertEquals(0.15, pos.getXFraction(), 1e-6);
    assertFalse(pos.hasCustomY());
  }

  @Test
  public void parsesShowWithInlineAt() throws Exception {
    String script = """
      @scenario test_at
      @character hero "Hero"
      @charimg hero neutral game/images/hero_neutral.png

      [show hero at 0.3,0.5]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    VnScenario scenario = parser.parseFromString(script);
    VnNode showNode = scenario.getNodes().get(0);
    assertEquals(VnNodeType.SHOW, showNode.getType());
    CharacterPosition pos = showNode.getShowPosition();
    assertTrue(pos.isCustom());
    assertEquals(0.3, pos.getXFraction(), 1e-6);
    assertEquals(0.5, pos.getYFraction(), 1e-6);
    assertEquals("neutral", showNode.getShowExpression());
  }

  @Test
  public void parsesShowWithInlineAtAndExpression() throws Exception {
    String script = """
      @scenario test_at_expr
      @character hero "Hero"
      @charimg hero smile game/images/hero_smile.png

      [show hero at 0.7,0.2 smile]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    VnScenario scenario = parser.parseFromString(script);
    VnNode showNode = scenario.getNodes().get(0);
    CharacterPosition pos = showNode.getShowPosition();
    assertTrue(pos.isCustom());
    assertEquals(0.7, pos.getXFraction(), 1e-6);
    assertEquals(0.2, pos.getYFraction(), 1e-6);
    assertEquals("smile", showNode.getShowExpression());
  }

  @Test
  public void parsesShowWithInlineAtAndLayerZ() throws Exception {
    String script = """
      @scenario test_at_z
      @character hero "Hero"
      @charimg hero neutral game/images/hero_neutral.png

      [show hero at 0.3,0.5,10]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    VnScenario scenario = parser.parseFromString(script);
    VnNode showNode = scenario.getNodes().get(0);
    CharacterPosition pos = showNode.getShowPosition();
    assertTrue(pos.isCustom());
    assertEquals(0.3, pos.getXFraction(), 1e-6);
    assertEquals(0.5, pos.getYFraction(), 1e-6);
    assertNotNull(showNode.getShowLayerOrder());
    assertEquals(10, showNode.getShowLayerOrder().intValue());
  }

  @Test
  public void parsesMoveWithInlineAt() throws Exception {
    String script = """
      @scenario test_move_at
      @character hero "Hero"
      @charimg hero neutral game/images/hero_neutral.png

      [show hero center]
      [move hero at 0.8,0.3 ease_out_bounce 500]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    VnScenario scenario = parser.parseFromString(script);

    VnNode moveNode = scenario.getNodes().get(1);
    assertEquals(VnNodeType.MOVE, moveNode.getType());
    CharacterPosition pos = moveNode.getShowPosition();
    assertTrue(pos.isCustom());
    assertEquals(0.8, pos.getXFraction(), 1e-6);
    assertEquals(0.3, pos.getYFraction(), 1e-6);
    assertEquals(com.jvn.core.animation.Easing.Type.EASE_OUT_BOUNCE, moveNode.getMoveEasingType());
    assertEquals(500, moveNode.getMoveDurationMs());
  }

  @Test
  public void parsesMoveWithNamedCustomPosition() throws Exception {
    String script = """
      @scenario test_move_named
      @character hero "Hero"
      @charimg hero neutral game/images/hero_neutral.png
      @position balcony 0.3 0.6

      [show hero center]
      [move hero balcony ease_out_quad]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    VnScenario scenario = parser.parseFromString(script);

    VnNode moveNode = scenario.getNodes().get(1);
    assertEquals(VnNodeType.MOVE, moveNode.getType());
    CharacterPosition pos = moveNode.getShowPosition();
    assertTrue(pos.isCustom());
    assertEquals("balcony", pos.getName());
    assertEquals(0.3, pos.getXFraction(), 1e-6);
    assertEquals(0.6, pos.getYFraction(), 1e-6);
  }

  @Test
  public void positionDirectiveRejectsConflictWithPredefined() {
    String script = """
      @scenario test_conflict
      @position center 0.3 0.5
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    assertThrows(IOException.class, () -> parser.parseFromString(script));
  }

  @Test
  public void showAtCustomPositionCreatesCorrectSlot() throws Exception {
    String script = """
      @scenario test_custom_slot
      @character hero "Hero"
      @charimg hero neutral game/images/hero_neutral.png

      [show hero at 0.3,0.5]
      [end]
    """;
    VnScriptParser parser = new VnScriptParser();
    VnScenario scenario = parser.parseFromString(script);

    VnScene scene = new VnScene(scenario);
    scene.setInterop(new DefaultVnInterop());
    scene.onEnter();

    // Find the character in the visible characters map
    CharacterPosition expectedPos = CharacterPosition.at(0.3, 0.5);
    assertTrue(scene.getState().getVisibleCharacters().containsKey(expectedPos),
        "Visible characters should contain the custom at position");
    assertEquals("hero", scene.getState().getVisibleCharacters().get(expectedPos).getCharacterId());
  }

  @Test
  public void customPositionMoveDeltaCalculation() {
    CharacterPosition from = CharacterPosition.at(0.2, 0.5);
    CharacterPosition to = CharacterPosition.at(0.8, 0.5);
    double delta = to.moveDeltaFrom(from);
    // (0.2 - 0.8) * 1100 = -660
    assertEquals(-660.0, delta, 1e-6);

    // Predefined to custom
    double delta2 = to.moveDeltaFrom(CharacterPosition.CENTER);
    // (0.5 - 0.8) * 1100 = -330
    assertEquals(-330.0, delta2, 1e-6);
  }
}
