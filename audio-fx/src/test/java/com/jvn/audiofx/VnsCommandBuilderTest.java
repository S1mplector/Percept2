package com.jvn.audiofx;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VnsCommandBuilderTest {

  @Test
  void defaultAmbienceProducesMinimalCommand() {
    SynthPreviewSettings s = new SynthPreviewSettings();
    String cmd = VnsCommandBuilder.buildOnCommand(s);
    assertTrue(cmd.startsWith("[synthesizer on mode:\"wind\""), cmd);
    assertTrue(cmd.endsWith("]"), cmd);
    // defaults should be omitted
    assertFalse(cmd.contains("intensity:"), "default intensity should be omitted: " + cmd);
    assertFalse(cmd.contains("volume:"), "default volume should be omitted: " + cmd);
    assertFalse(cmd.contains("detail:"), "default detail should be omitted: " + cmd);
    assertFalse(cmd.contains("loop:"), "default loop should be omitted: " + cmd);
  }

  @Test
  void nonDefaultAmbienceParamsAppear() {
    SynthPreviewSettings s = new SynthPreviewSettings();
    s.setIntensity(0.80f);
    s.setVolume(0.30f);
    s.setDetail(0.90f);
    s.setMotion(0.10f);
    s.setSpread(0.75f);
    s.setAccent(0.20f);
    s.setLoop(false);
    String cmd = VnsCommandBuilder.buildOnCommand(s);
    assertTrue(cmd.contains("intensity:0.80"), cmd);
    assertTrue(cmd.contains("volume:0.30"), cmd);
    assertTrue(cmd.contains("detail:0.90"), cmd);
    assertTrue(cmd.contains("motion:0.10"), cmd);
    assertTrue(cmd.contains("spread:0.75"), cmd);
    assertTrue(cmd.contains("accent:0.20"), cmd);
    assertTrue(cmd.contains("loop:false"), cmd);
  }

  @Test
  void chiptuneCommandIncludesType() {
    SynthPreviewSettings s = new SynthPreviewSettings();
    s.setType(SynthPreviewSettings.SynthType.CHIPTUNE);
    s.setCueId("confirm");
    String cmd = VnsCommandBuilder.buildOnCommand(s);
    assertTrue(cmd.contains("type:chiptune"), cmd);
    assertTrue(cmd.contains("cue:\"confirm\""), cmd);
    assertFalse(cmd.contains("mode:"), "chiptune should not have mode: " + cmd);
    assertFalse(cmd.contains("detail:"), "chiptune should not have detail: " + cmd);
    assertFalse(cmd.contains("spread:"), "chiptune should not have spread: " + cmd);
  }

  @Test
  void chiptuneNonDefaultParamsAppear() {
    SynthPreviewSettings s = new SynthPreviewSettings();
    s.setType(SynthPreviewSettings.SynthType.CHIPTUNE);
    s.setCueId("error");
    s.setIntensity(0.50f);
    s.setVolume(0.90f);
    s.setLoop(true);
    String cmd = VnsCommandBuilder.buildOnCommand(s);
    assertTrue(cmd.contains("intensity:0.50"), cmd);
    assertTrue(cmd.contains("volume:0.90"), cmd);
    assertTrue(cmd.contains("loop:true"), cmd);
  }

  @Test
  void verboseCommandAlwaysIncludesAllParams() {
    SynthPreviewSettings s = new SynthPreviewSettings();
    String cmd = VnsCommandBuilder.buildVerboseCommand(s);
    assertTrue(cmd.contains("mode:\"wind\""), cmd);
    assertTrue(cmd.contains("intensity:"), cmd);
    assertTrue(cmd.contains("volume:"), cmd);
    assertTrue(cmd.contains("detail:"), cmd);
    assertTrue(cmd.contains("motion:"), cmd);
    assertTrue(cmd.contains("spread:"), cmd);
    assertTrue(cmd.contains("accent:"), cmd);
    assertTrue(cmd.contains("loop:"), cmd);
  }

  @Test
  void offCommandIncludesTypeQualifier() {
    assertEquals("[synthesizer off type:ambience]",
        VnsCommandBuilder.buildOffCommand(SynthPreviewSettings.SynthType.AMBIENCE));
    assertEquals("[synthesizer off type:chiptune]",
        VnsCommandBuilder.buildOffCommand(SynthPreviewSettings.SynthType.CHIPTUNE));
    assertEquals("[synthesizer off]",
        VnsCommandBuilder.buildOffCommand(null));
  }

  @Test
  void nullSettingsProducesBasicCommand() {
    assertEquals("[synthesizer on]", VnsCommandBuilder.buildOnCommand(null));
    assertEquals("[synthesizer on]", VnsCommandBuilder.buildVerboseCommand(null));
  }

  @Test
  void presetChangeReflectsInCommand() {
    SynthPreviewSettings s = new SynthPreviewSettings();
    s.setPreset("thunder");
    String cmd = VnsCommandBuilder.buildOnCommand(s);
    assertTrue(cmd.contains("mode:\"thunder\""), cmd);
  }

  @Test
  void roundTripSettingsProduceDeterministicOutput() {
    SynthPreviewSettings s = new SynthPreviewSettings();
    s.setPreset("fireplace");
    s.setIntensity(0.72f);
    s.setDetail(0.88f);
    s.setAccent(0.33f);
    String first = VnsCommandBuilder.buildOnCommand(s);
    String second = VnsCommandBuilder.buildOnCommand(s);
    assertEquals(first, second, "Same settings should produce identical command strings");
  }
}
