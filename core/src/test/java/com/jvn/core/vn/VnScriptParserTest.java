package com.jvn.core.vn;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
  public void parsesRuntimeDemoScriptWithHeavyInlineTimelineShowcase() throws Exception {
    Path demoScriptPath = Path.of("..", "runtime", "src", "main", "resources", "game", "scripts", "demo.vns");
    String script = Files.readString(demoScriptPath, StandardCharsets.UTF_8);

    VnScriptParser parser = new VnScriptParser();
    VnScenario scen = parser.parseFromString(script);

    assertNotNull(scen);
    assertEquals("runtime_demo_prologue", scen.getId());
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
}
