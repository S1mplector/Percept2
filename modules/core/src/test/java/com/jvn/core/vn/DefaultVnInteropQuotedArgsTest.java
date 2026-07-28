package com.jvn.core.vn;

import java.nio.file.Files;
import java.nio.file.Path;

import com.jvn.core.animation.TimelineRegistry;
import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.assets.ClasspathAssetManager;
import com.jvn.core.assets.FilesystemAssetManager;
import com.jvn.core.audio.AudioFacade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultVnInteropQuotedArgsTest {
  @Test
  void loadsExternalJesTimelineFromProjectScripts(@TempDir Path projectRoot) throws Exception {
    Path timelines = Files.createDirectories(projectRoot.resolve("scripts/timelines"));
    Files.writeString(timelines.resolve("my_animation.jes"), """
        timeline {
          wait 25
        }
        """);
    AssetCatalog.setDefaultManager(new FilesystemAssetManager(projectRoot));
    TimelineRegistry.clear();

    try {
      VnScenario scenario = new VnScenarioBuilder("timeline_load")
        .label("start")
        .end()
        .build();
      VnScene scene = new VnScene(scenario);
      DefaultVnInterop interop = new DefaultVnInterop();

      interop.handle(new VnExternalCommand("jes_timeline", "my_animation"), scene);

      assertTrue(TimelineRegistry.has("my_animation"));
      assertEquals("jes_timeline: no scene accessor", scene.getState().getHudMessage());
    } finally {
      TimelineRegistry.clear();
      AssetCatalog.setDefaultManager(new ClasspathAssetManager());
    }
  }

  @Test
  void inlineTimelineLayerMoveMarksVisibleCharacterDisplacement() {
    VnScenario scenario = new VnScenarioBuilder("timeline_displacement")
      .addCharacterWithExpressions("john", "John", "body.png")
      .label("start")
      .end()
      .build();
    VnScene scene = new VnScene(scenario);
    scene.getState().showCharacter(CharacterPosition.CENTER, "john", "neutral");
    DefaultVnInterop interop = new DefaultVnInterop();
    interop.setSceneAccessor(new VnCharacterSceneAccessor());

    interop.handle(new VnExternalCommand("jes_timeline_inline", """
        timeline {
          move "john_neutral_body_default" {
            x: 542
            y: 0
            dur: 100
          }
        }
        """), scene);

    VnState.TimelineDisplacement displacement = scene.getState().getTimelineDisplacement("john");
    assertNotNull(displacement);
    assertTrue(displacement.hasX());
    assertEquals(542.0, displacement.getX(), 0.0001);
  }

  @Test
  void characterExpressionCommandAcceptsTransitionDuration() {
    VnScenario scenario = new VnScenarioBuilder("expression_transition")
      .addCharacterWithExpressions("lily", "Lily", "neutral.png", "talking.png")
      .label("start")
      .end()
      .build();
    VnScene scene = new VnScene(scenario);
    scene.getState().showCharacter(CharacterPosition.CENTER, "lily", "neutral");
    DefaultVnInterop interop = new DefaultVnInterop();

    interop.handle(new VnExternalCommand("char", "lily expression talking dur=240"), scene);

    assertEquals("talking", scene.getState().getCharacterExpression("lily"));
    VnState.ExpressionTransition transition = scene.getState().getExpressionTransition("lily");
    assertNotNull(transition);
    assertEquals("neutral", transition.getFromExpression());
    assertEquals("talking", transition.getToExpression());
  }

  @Test
  void characterExpressionCommandSupportsInstantAndDefaultSwaps() {
    VnScenario scenario = new VnScenarioBuilder("expression_swap_modes")
      .addCharacterWithExpressions("lily", "Lily", "neutral.png", "talking.png")
      .label("start")
      .end()
      .build();
    VnScene scene = new VnScene(scenario);
    scene.getState().showCharacter(CharacterPosition.CENTER, "lily", "neutral");
    DefaultVnInterop interop = new DefaultVnInterop();

    interop.handle(new VnExternalCommand("char", "lily expression talking dur=0"), scene);
    assertEquals("talking", scene.getState().getCharacterExpression("lily"));
    assertNull(scene.getState().getExpressionTransition("lily"));

    interop.handle(new VnExternalCommand("char", "lily expression neutral"), scene);
    assertEquals("neutral", scene.getState().getCharacterExpression("lily"));
    assertNotNull(scene.getState().getExpressionTransition("lily"));
  }

  @Test
  void characterMoveAppliesExpressionDurationIndependently() {
    VnScenario scenario = new VnScenarioBuilder("independent_expression_duration")
      .addCharacterWithExpressions("john", "John", "neutral.png", "talking.png")
      .label("start")
      .end()
      .build();
    VnScene scene = new VnScene(scenario);
    scene.getState().showCharacter(CharacterPosition.CENTER, "john", "neutral");
    scene.getState().setCharacterGlobalPositionEnabled("john", true);
    DefaultVnInterop interop = new DefaultVnInterop();

    interop.handle(
        new VnExternalCommand(
            "char",
            "john move right expression=talking dur=400 exprDur=120"),
        scene);

    assertEquals("neutral", scene.getState().getCharacterExpression("john"));
    scene.getState().updateCharacterAnimations(400);
    assertEquals("talking", scene.getState().getCharacterExpression("john"));
    assertNotNull(scene.getState().getExpressionTransition("john"));
    scene.getState().updateCharacterAnimations(120);
    assertNull(scene.getState().getExpressionTransition("john"));
  }

  @Test
  void characterMoveAcceptsQuotedExpressionWithInstantDuration() {
    VnScenario scenario = new VnScenarioBuilder("quoted_expression_duration")
      .addCharacterWithExpressions("john", "John", "neutral.png")
      .label("start")
      .end()
      .build();
    VnScene scene = new VnScene(scenario);
    scene.getState().showCharacter(CharacterPosition.CENTER, "john", "neutral");
    scene.getState().setCharacterGlobalPositionEnabled("john", true);
    DefaultVnInterop interop = new DefaultVnInterop();

    interop.handle(
        new VnExternalCommand(
            "char",
            "john move right expression=\"soft smile\" dur=400 exprDur=0"),
        scene);

    scene.getState().updateCharacterAnimations(400);
    assertEquals("soft smile", scene.getState().getCharacterExpression("john"));
    assertNull(scene.getState().getExpressionTransition("john"));
  }

  @Test
  void laterInlineTimelineMoveReplacesEarlierCharacterDisplacement() {
    VnScenario scenario = new VnScenarioBuilder("timeline_displacement_latest")
      .addCharacterWithExpressions("john", "John", "body.png")
      .label("start")
      .end()
      .build();
    VnScene scene = new VnScene(scenario);
    scene.getState().showCharacter(CharacterPosition.CENTER, "john", "neutral");
    DefaultVnInterop interop = new DefaultVnInterop();
    interop.setSceneAccessor(new VnCharacterSceneAccessor());

    interop.handle(new VnExternalCommand("jes_timeline_inline", """
        timeline {
          move "john_neutral_body_default" {
            x: 542
            dur: 100
          }
        }
        """), scene);

    interop.handle(new VnExternalCommand("jes_timeline_inline", """
        timeline {
          move "john_neutral_body_default" {
            x: -532
            dur: 100
          }
        }
        """), scene);

    VnState.TimelineDisplacement displacement = scene.getState().getTimelineDisplacement("john");
    assertNotNull(displacement);
    assertTrue(displacement.hasX());
    assertEquals(-532.0, displacement.getX(), 0.0001);
  }

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

  @Test
  void eyeFocusInteropStoresPointAndCharacterTargets() {
    VnScenario scenario = new VnScenarioBuilder("eye_focus")
        .label("start")
        .dialogue("Narrator", "Start")
        .end()
        .build();
    VnScene scene = new VnScene(scenario);
    DefaultVnInterop interop = new DefaultVnInterop();

    interop.handle(new VnExternalCommand("eye_focus", "john at=100,200 dur=220 strength=0.8 deadZone=0.2"), scene);
    VnState.EyeFocusRequest point = scene.getState().getEyeFocusRequest("john");
    assertNotNull(point);
    assertTrue(point.hasPointTarget());
    assertEquals(100.0, point.targetX(), 0.001);
    assertEquals(200.0, point.targetY(), 0.001);
    assertEquals(220L, point.durationMs());
    assertEquals(0.8, point.strength(), 0.001);
    assertEquals(0.2, point.deadZone(), 0.001);

    interop.handle(new VnExternalCommand("eye_focus", "john target=lily expression=smile"), scene);
    VnState.EyeFocusRequest character = scene.getState().getEyeFocusRequest("john");
    assertNotNull(character);
    assertTrue(character.hasCharacterTarget());
    assertEquals("lily", character.targetCharacterId());
    assertEquals("smile", character.expression());

    interop.handle(new VnExternalCommand("eye_focus", "john clear"), scene);
    assertNull(scene.getState().getEyeFocusRequest("john"));
  }

  @Test
  void characterExpressionInteropPreservesDetachedTimelinePlacement() {
    VnScene scene = new VnScene(new VnScenarioBuilder("char_expression").end().build());
    DefaultVnInterop interop = new DefaultVnInterop();

    scene.getState().showCharacter(CharacterPosition.CENTER, "john", "neutral");
    scene.getState().recordTimelineDisplacement("john", 542.0, 0.0, true, false);
    scene.getState().showCharacter(CharacterPosition.CENTER, "lily", "neutral");

    interop.handle(new VnExternalCommand("char", "john expression talking"), scene);

    VnState.CharacterSlot lily = scene.getState().getVisibleCharacters().get(CharacterPosition.CENTER);
    assertNotNull(lily);
    assertEquals("lily", lily.getCharacterId());
    assertEquals("talking", scene.getState().getCharacterExpression("john"));
    VnState.DetachedCharacterSlot john = scene.getState().getDetachedCharacter("john");
    assertNotNull(john);
    assertEquals("talking", john.getSlot().getExpression());
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
