package com.jvn.core.vn;

import com.jvn.core.audio.AudioFacade;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultVnInteropQuotedArgsTest {
  @Test
  void supportsQuotedArgsInVarCondAndJavaInterop() {
    VnScenario scenario = new VnScenarioBuilder("quoted_interop")
      .label("start")
      .dialogue("Narrator", "Start")
      .label("ok")
      .dialogue("Narrator", "OK")
      .end()
      .build();
    VnScene scene = new VnScene(scenario);
    DefaultVnInterop interop = new DefaultVnInterop();

    interop.handle(new VnExternalCommand("var", "set title \"Final Battle\""), scene);
    assertEquals("Final Battle", scene.getState().getVariable("title"));

    interop.handle(new VnExternalCommand("var", "set subtitle Final Battle"), scene);
    assertEquals("Final Battle", scene.getState().getVariable("subtitle"));

    VnInteropResult cond = interop.handle(
      new VnExternalCommand("cond", "if title == \"Final Battle\" goto ok"),
      scene
    );
    assertFalse(cond.shouldAdvance());
    assertEquals(scenario.getLabelIndex("ok"), scene.getState().getCurrentNodeIndex());

    scene.getState().setCurrentNodeIndex(0);
    interop.handle(new VnExternalCommand("var", "set hp 12"), scene);
    VnInteropResult complexCond = interop.handle(
      new VnExternalCommand("cond", "if (title == \"Final Battle\" && hp >= 10) goto ok"),
      scene
    );
    assertFalse(complexCond.shouldAdvance());
    assertEquals(scenario.getLabelIndex("ok"), scene.getState().getCurrentNodeIndex());

    String payload = Methods.class.getName() + "#join \"hello world\" \"boss fight\"";
    interop.handle(new VnExternalCommand("java", payload), scene);
    assertEquals("java: hello world|boss fight", scene.getState().getHudMessage());
  }

  @Test
  void blocksJavaInteropOutsideAllowedClassPrefixes() {
    VnScenario scenario = new VnScenarioBuilder("quoted_interop")
      .label("start")
      .dialogue("Narrator", "Start")
      .end()
      .build();
    VnScene scene = new VnScene(scenario);
    DefaultVnInterop interop = new DefaultVnInterop();

    interop.handle(new VnExternalCommand("java", "java.lang.Math#max 1 2"), scene);

    assertEquals("java: class not allowed", scene.getState().getHudMessage());
  }

  @Test
  void togglesVisualizerFlagViaUiInterop() {
    VnScenario scenario = new VnScenarioBuilder("ui_visualizer")
      .label("start")
      .dialogue("Narrator", "Start")
      .end()
      .build();
    VnScene scene = new VnScene(scenario);
    DefaultVnInterop interop = new DefaultVnInterop();

    interop.handle(new VnExternalCommand("ui", "visualizer on"), scene);
    assertEquals(Boolean.TRUE, scene.getState().getVariable("ui.audioVisualizer"));

    interop.handle(new VnExternalCommand("ui", "visualizer off"), scene);
    assertEquals(Boolean.FALSE, scene.getState().getVariable("ui.audioVisualizer"));

    interop.handle(new VnExternalCommand("ui", "visualizer toggle"), scene);
    Object value = scene.getState().getVariable("ui.audioVisualizer");
    assertTrue(value instanceof Boolean);
    assertTrue((Boolean) value);

    interop.handle(new VnExternalCommand("ui", "visualizer on bars=48"), scene);
    assertEquals(Boolean.TRUE, scene.getState().getVariable("ui.audioVisualizer"));
    assertEquals(48, scene.getState().getVariable("ui.audioVisualizerBars"));

    interop.handle(new VnExternalCommand("ui", "visualizer off bars 24"), scene);
    assertEquals(Boolean.FALSE, scene.getState().getVariable("ui.audioVisualizer"));
    assertEquals(24, scene.getState().getVariable("ui.audioVisualizerBars"));
  }

  @Test
  void appliesSynthesizerAudioInteropCommands() {
    VnScenario scenario = new VnScenarioBuilder("audio_synth")
        .label("start")
        .dialogue("Narrator", "Start")
        .end()
        .build();
    VnScene scene = new VnScene(scenario);
    FakeAudio audio = new FakeAudio();
    scene.setAudioFacade(audio);
    DefaultVnInterop interop = new DefaultVnInterop();

    interop.handle(new VnExternalCommand(
        "audio",
        "synth on type=ambience mode=rain intensity=0.81 volume=0.42 loop=true"), scene);
    assertEquals("rain", audio.lastAmbiencePreset);
    assertEquals(0.81f, audio.lastAmbienceIntensity);
    assertEquals(true, audio.lastAmbienceLoop);
    assertEquals(0.42f, audio.lastAmbienceVolume);

    interop.handle(new VnExternalCommand(
        "audio",
        "synth on type=chiptune cue=\"confirm tone\" intensity=0.56 volume=0.73 loop=false"), scene);
    assertEquals("confirm tone", audio.lastChiptuneCue);
    assertEquals(0.56f, audio.lastChiptuneIntensity);
    assertEquals(false, audio.lastChiptuneLoop);
    assertEquals(0.73f, audio.lastChiptuneVolume);

    interop.handle(new VnExternalCommand("audio", "synth off type=all"), scene);
    assertEquals(1, audio.stopAmbienceCount);
    assertEquals(1, audio.stopChiptuneCount);
  }

  public static class Methods {
    public static String join(String a, String b) {
      return a + "|" + b;
    }
  }

  private static final class FakeAudio implements AudioFacade {
    private String lastAmbiencePreset;
    private float lastAmbienceIntensity;
    private boolean lastAmbienceLoop;
    private float lastAmbienceVolume = -1f;

    private String lastChiptuneCue;
    private float lastChiptuneIntensity;
    private boolean lastChiptuneLoop;
    private float lastChiptuneVolume = -1f;
    private int stopAmbienceCount;
    private int stopChiptuneCount;

    @Override
    public void playBgm(String trackId, boolean loop) {
    }

    @Override
    public void stopBgm() {
    }

    @Override
    public void playSfx(String sfxId) {
    }

    @Override
    public void playAmbience(String preset, float intensity, boolean loop) {
      lastAmbiencePreset = preset;
      lastAmbienceIntensity = intensity;
      lastAmbienceLoop = loop;
    }

    @Override
    public void stopAmbience() {
      stopAmbienceCount++;
    }

    @Override
    public void setAmbienceVolume(float volume) {
      lastAmbienceVolume = volume;
    }

    @Override
    public void playChiptune(String cueId, float intensity, boolean loop) {
      lastChiptuneCue = cueId;
      lastChiptuneIntensity = intensity;
      lastChiptuneLoop = loop;
    }

    @Override
    public void stopChiptune() {
      stopChiptuneCount++;
    }

    @Override
    public void setChiptuneVolume(float volume) {
      lastChiptuneVolume = volume;
    }
  }
}
