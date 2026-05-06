package com.jvn.core.vn;

import com.jvn.core.audio.AudioFacade;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    assertNotNull(scene.getActiveError());
    assertEquals(VnErrorOverlay.ErrorType.INTEROP_ERROR, scene.getActiveError().getType());
  }

  @Test
  void javaInteropSelectsBestMatchingOverload() {
    VnScenario scenario = new VnScenarioBuilder("java_overload")
      .label("start")
      .dialogue("Narrator", "Start")
      .end()
      .build();
    VnScene scene = new VnScene(scenario);
    DefaultVnInterop interop = new DefaultVnInterop();

    interop.handle(new VnExternalCommand("java", Methods.class.getName() + "#overloaded 7"), scene);
    assertEquals("java: int:7", scene.getState().getHudMessage());

    interop.handle(new VnExternalCommand("java", Methods.class.getName() + "#overloaded 7.5"), scene);
    assertEquals("java: double:7.5", scene.getState().getHudMessage());
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
  void configuresVisualizerWithoutImplicitToggleAndCanResetOptions() {
    VnScenario scenario = new VnScenarioBuilder("ui_visualizer_config")
        .label("start")
        .dialogue("Narrator", "Start")
        .end()
        .build();
    VnScene scene = new VnScene(scenario);
    DefaultVnInterop interop = new DefaultVnInterop();

    interop.handle(new VnExternalCommand(
        "ui",
        "visualizer bars=32 color=#7de2ff accent=#ffffff alpha=0.5 glow=off style=minimal height=0.6 z=-15"), scene);

    assertNull(scene.getState().getVariable(VnAudioVisualizerConfig.VAR_ENABLED));
    assertEquals(32, scene.getState().getVariable(VnAudioVisualizerConfig.VAR_BARS));
    assertEquals("#7de2ff", scene.getState().getVariable(VnAudioVisualizerConfig.VAR_COLOR));
    assertEquals("#ffffff", scene.getState().getVariable(VnAudioVisualizerConfig.VAR_ACCENT));
    assertEquals(0.5, scene.getState().getVariable(VnAudioVisualizerConfig.VAR_ALPHA));
    assertEquals(Boolean.FALSE, scene.getState().getVariable(VnAudioVisualizerConfig.VAR_GLOW));
    assertEquals("minimal", scene.getState().getVariable(VnAudioVisualizerConfig.VAR_STYLE));
    assertEquals(0.6, scene.getState().getVariable(VnAudioVisualizerConfig.VAR_HEIGHT));
    assertEquals(-15, scene.getState().getVariable(VnAudioVisualizerConfig.VAR_Z));

    interop.handle(new VnExternalCommand("ui", "visualizer on"), scene);
    interop.handle(new VnExternalCommand("ui", "visualizer reset"), scene);

    assertEquals(Boolean.TRUE, scene.getState().getVariable(VnAudioVisualizerConfig.VAR_ENABLED));
    assertNull(scene.getState().getVariable(VnAudioVisualizerConfig.VAR_BARS));
    assertNull(scene.getState().getVariable(VnAudioVisualizerConfig.VAR_COLOR));
    assertNull(scene.getState().getVariable(VnAudioVisualizerConfig.VAR_ACCENT));
    assertNull(scene.getState().getVariable(VnAudioVisualizerConfig.VAR_ALPHA));
    assertNull(scene.getState().getVariable(VnAudioVisualizerConfig.VAR_GLOW));
    assertNull(scene.getState().getVariable(VnAudioVisualizerConfig.VAR_STYLE));
    assertNull(scene.getState().getVariable(VnAudioVisualizerConfig.VAR_HEIGHT));
    assertNull(scene.getState().getVariable(VnAudioVisualizerConfig.VAR_Z));
  }

  @Test
  void reportsVisualizerStatusAgainstSpectrumSupport() {
    VnScenario scenario = new VnScenarioBuilder("ui_visualizer_status")
        .label("start")
        .dialogue("Narrator", "Start")
        .end()
        .build();
    VnScene scene = new VnScene(scenario);
    FakeAudio audio = new FakeAudio();
    audio.supportsSpectrum = true;
    scene.setAudioFacade(audio);
    DefaultVnInterop interop = new DefaultVnInterop();

    interop.handle(new VnExternalCommand("ui", "visualizer on bars=16"), scene);
    interop.handle(new VnExternalCommand("ui", "visualizer status"), scene);
    assertEquals("Viz on 16 bars dynamic z=-100 waiting", scene.getState().getHudMessage());

    audio.latestSpectrum = new float[] {-12f, -18f, -22f};
    audio.latestSpectrumUpdatedAtNanos = System.nanoTime();
    interop.handle(new VnExternalCommand("ui", "visualizer status"), scene);
    assertEquals("Viz on 16 bars dynamic z=-100 live", scene.getState().getHudMessage());
  }

  @Test
  void supportsGenericAudioInteropChannelAliasesAndVolumeCommands() {
    VnScenario scenario = new VnScenarioBuilder("audio_aliases")
        .label("start")
        .dialogue("Narrator", "Start")
        .end()
        .build();
    VnScene scene = new VnScene(scenario);
    FakeAudio audio = new FakeAudio();
    scene.setAudioFacade(audio);
    DefaultVnInterop interop = new DefaultVnInterop();

    interop.handle(new VnExternalCommand("audio", "stop music"), scene);
    interop.handle(new VnExternalCommand("audio", "stop sound"), scene);
    interop.handle(new VnExternalCommand("audio", "stop voice"), scene);
    interop.handle(new VnExternalCommand("audio", "stop"), scene);
    interop.handle(new VnExternalCommand("audio", "pause all"), scene);
    interop.handle(new VnExternalCommand("audio", "resume all"), scene);
    interop.handle(new VnExternalCommand("audio", "volume music 0.45"), scene);
    interop.handle(new VnExternalCommand("audio", "volume voice=0.35"), scene);

    assertEquals(1, audio.stopBgmCount);
    assertEquals(1, audio.stopSfxCount);
    assertEquals(1, audio.stopVoiceCount);
    assertEquals(1, audio.stopAllCount);
    assertEquals(1, audio.pauseAllCount);
    assertEquals(1, audio.resumeAllCount);
    assertEquals(0.45f, audio.lastBgmVolume);
    assertEquals(0.35f, audio.lastVoiceVolume);
    assertEquals(0.45f, scene.getState().getSettings().getBgmVolume());
    assertEquals(0.35f, scene.getState().getSettings().getVoiceVolume());
  }

  @Test
  void supportsNamedGenericAudioCrossfadeOptions() {
    VnScenario scenario = new VnScenarioBuilder("audio_crossfade")
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
        "crossfade track=\"assets/audio/theme one.ogg\" loop=false dur=1500"), scene);

    assertEquals("assets/audio/theme one.ogg", audio.lastCrossfadeTrack);
    assertEquals(1500L, audio.lastCrossfadeDurationMs);
    assertFalse(audio.lastCrossfadeLoop);
  }

  void togglesDialoguePresentationModesAndBubblePreferences() {
    VnScenario scenario = new VnScenarioBuilder("dialogue_modes")
        .label("start")
        .dialogue("Narrator", "Start")
        .end()
        .build();
    VnScene scene = new VnScene(scenario);
    DefaultVnInterop interop = new DefaultVnInterop();

    interop.handle(new VnExternalCommand("mode", "dialogue nvl"), scene);
    assertEquals(DialoguePresentationMode.NVL, scene.getState().getDialoguePresentationMode());

    interop.handle(new VnExternalCommand("mode", "bubble on"), scene);
    assertEquals(DialoguePresentationMode.BUBBLE, scene.getState().getDialoguePresentationMode());

    interop.handle(new VnExternalCommand("char", "lavender bubble left"), scene);
    assertEquals(BubbleAnchor.LEFT, scene.getState().getBubbleAnchorPreference("lavender"));

    interop.handle(new VnExternalCommand("char", "lavender bubble_offset 12 -8"), scene);
    assertEquals(12.0, scene.getState().getBubbleOffsetXPreference("lavender"));
    assertEquals(-8.0, scene.getState().getBubbleOffsetYPreference("lavender"));

    interop.handle(new VnExternalCommand("char", "lavender bubble clear"), scene);
    assertEquals(BubbleAnchor.AUTO, scene.getState().getBubbleAnchorPreference("lavender"));

    interop.handle(new VnExternalCommand("mode", "nvl off"), scene);
    assertEquals(DialoguePresentationMode.STANDARD, scene.getState().getDialoguePresentationMode());
  }

  public static class Methods {
    public static String join(String a, String b) {
      return a + "|" + b;
    }

    public static String overloaded(String value) {
      return "string:" + value;
    }

    public static String overloaded(int value) {
      return "int:" + value;
    }

    public static String overloaded(double value) {
      return "double:" + value;
    }
  }

  private static final class FakeAudio implements AudioFacade {
    private int stopBgmCount;
    private int stopSfxCount;
    private int stopVoiceCount;
    private int stopAllCount;
    private int pauseAllCount;
    private int resumeAllCount;
    private float lastBgmVolume = -1f;
    private float lastSfxVolume = -1f;
    private float lastVoiceVolume = -1f;
    private String lastCrossfadeTrack;
    private long lastCrossfadeDurationMs = -1L;
    private boolean lastCrossfadeLoop;
    private boolean supportsSpectrum;
    private float[] latestSpectrum;
    private long latestSpectrumUpdatedAtNanos;

    @Override
    public void playBgm(String trackId, boolean loop) {
    }

    @Override
    public void stopBgm() {
      stopBgmCount++;
    }

    @Override
    public void playSfx(String sfxId) {
    }

    @Override
    public void stopSfx() {
      stopSfxCount++;
    }

    @Override
    public void stopVoice() {
      stopVoiceCount++;
    }

    @Override
    public void stopAllAudio() {
      stopAllCount++;
    }

    @Override
    public void pauseAllAudio() {
      pauseAllCount++;
    }

    @Override
    public void resumeAllAudio() {
      resumeAllCount++;
    }

    @Override
    public void setBgmVolume(float volume) {
      lastBgmVolume = volume;
    }

    @Override
    public void setSfxVolume(float volume) {
      lastSfxVolume = volume;
    }

    @Override
    public void setVoiceVolume(float volume) {
      lastVoiceVolume = volume;
    }

    @Override
    public void crossfadeBgm(String trackId, long ms, boolean loop) {
      lastCrossfadeTrack = trackId;
      lastCrossfadeDurationMs = ms;
      lastCrossfadeLoop = loop;
    }

    @Override
    public boolean supportsBgmSpectrum() {
      return supportsSpectrum;
    }

    @Override
    public float[] getBgmSpectrumMagnitudes() {
      return latestSpectrum == null ? null : latestSpectrum.clone();
    }

    @Override
    public long getBgmSpectrumUpdatedAtNanos() {
      return latestSpectrumUpdatedAtNanos;
    }
  }
}
