package com.jvn.editor.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PuppeteerLauncherPanelTest {

  @Test
  void resolveSnapshotClampsLineToLastSourceLine() {
    String source = """
        @label start
        [background school]
        [show alice center neutral]
        """;

    PuppeteerLauncherPanel.SceneSnapshot snapshot = PuppeteerLauncherPanel.resolveSnapshot(source, 999);
    assertEquals(source.split("\n", -1).length - 1, snapshot.atLine);
  }

  @Test
  void resolveSnapshotClampsNegativeLineToZero() {
    String source = """
        @label start
        [background school]
        """;

    PuppeteerLauncherPanel.SceneSnapshot snapshot = PuppeteerLauncherPanel.resolveSnapshot(source, -12);
    assertEquals(0, snapshot.atLine);
    assertEquals("start", snapshot.currentLabel);
  }

  @Test
  void resolveSnapshotFollowsIncludedCharacterDefinitions() {
    String source = """
        @label start
        @include /definitions/characters.vns
        [show lavender center talking]
        """;
    String definitions = """
        @charlayer lavender base assets/demo/characters/lavender/base.png
        @charlayer lavender eyes_half_closed assets/demo/characters/lavender/eyes_half_closed.png
        @charlayer lavender mouth_smile assets/demo/characters/lavender/mouth_smile.png
        @charpreset lavender talking $base | $eyes_half_closed | $mouth_smile
        """;

    PuppeteerLauncherPanel.SceneSnapshot snapshot = PuppeteerLauncherPanel.resolveSnapshot(
        source,
        99,
        "/tmp/project/scripts/story/prologue.vns",
        (sourceName, includePath) -> new PuppeteerLauncherPanel.ResolvedInclude(
            "/tmp/project/scripts/definitions/characters.vns",
            definitions));

    assertTrue(snapshot.hasCharacterPathMapping("lavender", "talking"));
    assertEquals(
        "assets/demo/characters/lavender/base.png | assets/demo/characters/lavender/eyes_half_closed.png | assets/demo/characters/lavender/mouth_smile.png",
        snapshot.resolveCharacterPath("lavender", "talking"));
  }

  @Test
  void resolveSnapshotSupportsGroupedLayerRefsAndNestedPresets() {
    String source = """
        @label start
        @include /definitions/characters.vns
        [show lavender center talking]
        """;
    String definitions = """
        @charlayer lavender base assets/demo/characters/lavender/base.png
        @charlayer lavender body_school assets/demo/characters/lavender/body_school.png
        @charlayer lavender eyes_half_closed assets/demo/characters/lavender/eyes_half_closed.png
        @charlayer lavender mouth_smile assets/demo/characters/lavender/mouth_smile.png
        @charpreset lavender neutral $base | $body=school | $eyes=half_closed
        @charpreset lavender talking @neutral | $mouth=smile
        """;

    PuppeteerLauncherPanel.SceneSnapshot snapshot = PuppeteerLauncherPanel.resolveSnapshot(
        source,
        99,
        "/tmp/project/scripts/story/prologue.vns",
        (sourceName, includePath) -> new PuppeteerLauncherPanel.ResolvedInclude(
            "/tmp/project/scripts/definitions/characters.vns",
            definitions));

    assertTrue(snapshot.hasCharacterPathMapping("lavender", "talking"));
    assertEquals(
        "assets/demo/characters/lavender/base.png | assets/demo/characters/lavender/body_school.png | assets/demo/characters/lavender/eyes_half_closed.png | assets/demo/characters/lavender/mouth_smile.png",
        snapshot.resolveCharacterPath("lavender", "talking"));
  }

  @Test
  void resolveSnapshotCapturesReferencedTimelineName() {
    String source = """
        @label intro
        @external jes_timeline hero_entrance
        [show lavender center neutral]
        """;

    PuppeteerLauncherPanel.SceneSnapshot snapshot = PuppeteerLauncherPanel.resolveSnapshot(source, 2);

    assertEquals("hero_entrance", snapshot.referencedTimelineName);
    assertEquals(1, snapshot.referencedTimelineLine);
    assertEquals("hero_entrance", snapshot.preferredTimelineName());
  }

  @Test
  void resolveSnapshotCapturesActiveStagePreset() {
    String source = """
        @stagepreset sunset_park config/stage/sunset_park.stagepreset
        @label intro
        [background field_day]
        [stage preset=sunset_park]
        [show lavender center neutral]
        """;

    PuppeteerLauncherPanel.SceneSnapshot snapshot = PuppeteerLauncherPanel.resolveSnapshot(
        source,
        4,
        "/tmp/project/scripts/story/intro.vns",
        null);

    assertEquals("sunset_park", snapshot.activeStagePresetId);
    assertEquals(3, snapshot.activeStageLine);
    assertTrue(snapshot.hasStagePresetPathMapping());
    assertEquals(
        "/tmp/project/scripts/story/config/stage/sunset_park.stagepreset",
        snapshot.resolveStagePresetPath(null));
  }

  @Test
  void resolveSnapshotCapturesInlineTimelineAtCursor() {
    String source = """
        @label intro
        [show lavender center neutral]
        timeline {
          move "lavender" {
            x: 420
            dur: 500
          }
        }
        narrator: done
        """;

    PuppeteerLauncherPanel.SceneSnapshot snapshot = PuppeteerLauncherPanel.resolveSnapshot(source, 4);

    assertTrue(snapshot.hasInlineTimeline());
    assertEquals(2, snapshot.inlineTimelineStartLine);
    assertEquals("intro_inline_3", snapshot.preferredTimelineName());
    assertTrue(snapshot.inlineTimelineBody.contains("move \"lavender\""));
  }

  @Test
  void resolveSceneStartLineUsesLatestBackgroundInActiveLabel() {
    String source = """
        @label intro
        [show lavender left neutral]
        [background corridor]
        [show finley right neutral]
        """;

    assertEquals(2, PuppeteerLauncherPanel.resolveSceneStartLine(source, 3));
  }

  @Test
  void discoverRegisteredAnimationsBuildsSortedCards(@TempDir Path tempDir) throws Exception {
    Path timelinesDir = Files.createDirectories(tempDir.resolve("scripts/timelines"));
    Files.writeString(timelinesDir.resolve("zeta_idle.jes"), """
        timeline {
          move "hero" {
            x: 100
            dur: 200
          }
        }
        """);
    Path clusterDir = Files.createDirectories(timelinesDir.resolve("chapter_one"));
    Files.writeString(clusterDir.resolve("alpha_zoom.jes"), """
        timeline {
          cameraZoom {
            zoom: 1.2
            dur: 250
          }
        }
        """);

    List<PuppeteerLauncherPanel.RegisteredAnimation> animations =
        PuppeteerLauncherPanel.discoverRegisteredAnimations(tempDir.toFile());

    assertEquals(2, animations.size());
    assertEquals("alpha_zoom", animations.get(0).name());
    assertEquals("zeta_idle", animations.get(1).name());
    assertEquals("chapter_one/alpha_zoom.jes", animations.get(0).relativePath());
    assertEquals("chapter_one", animations.get(0).clusterName());
    assertTrue(animations.get(0).importable());
    assertTrue(animations.get(0).statsText().contains("track(s)"));
    assertTrue(animations.get(0).previewText().contains("cameraZoom"));
  }

  @Test
  void discoverRegisteredAnimationsRetainsBrokenTimelinesAsWarningCards(@TempDir Path tempDir) throws Exception {
    Path timelinesDir = Files.createDirectories(tempDir.resolve("scripts/timelines"));
    Path brokenFile = timelinesDir.resolve("broken_pose.jes");
    Files.writeString(brokenFile, "timeline {\n}\n");
    Assumptions.assumeTrue(
        Files.getFileAttributeView(brokenFile, PosixFileAttributeView.class) != null,
        "Posix permissions are required for this test");
    Files.setPosixFilePermissions(brokenFile, Set.of());

    List<PuppeteerLauncherPanel.RegisteredAnimation> animations =
        PuppeteerLauncherPanel.discoverRegisteredAnimations(tempDir.toFile());

    assertEquals(1, animations.size());
    assertEquals("broken_pose", animations.get(0).name());
    assertFalse(animations.get(0).importable());
    assertTrue(animations.get(0).statsText() != null && !animations.get(0).statsText().isBlank());
    assertTrue(animations.get(0).warningMessage() != null && !animations.get(0).warningMessage().isBlank());
    Files.setPosixFilePermissions(brokenFile, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
  }

  @Test
  void extractTimelinePreviewSkipsCommentsAndWrapperLines() {
    String code = """
        // Usage in VNS: @external jes_timeline demo

        timeline {
          move "hero" {
            x: 100
            dur: 200
          }
        }
        """;

    assertEquals(
        """
        move "hero" {
        x: 100 …\
        """,
        PuppeteerLauncherPanel.extractTimelinePreview(code));
  }

  @Test
  void describeScenePreviewIncludesVisibleEntities() {
    PuppeteerLauncherPanel.SceneSnapshot snapshot = new PuppeteerLauncherPanel.SceneSnapshot(
        "intro",
        "field_day",
        3,
        List.of(new PuppeteerLauncherPanel.CharacterEntry("lavender", "center", "talking", 4)),
        4,
        java.util.Map.of(),
        java.util.Map.of(),
        null,
        -1,
        null,
        -1,
        null);

    assertEquals(
        """
        Scene • label intro • bg field_day
        Entities • lavender @ center [talking]\
        """,
        PuppeteerLauncherPanel.describeScenePreview(snapshot));
  }

  @Test
  void resolveRegisteredAnimationPreviewFallsBackToSceneContextWhenTimelineIsEmpty() {
    PuppeteerLauncherPanel.SceneSnapshot snapshot = new PuppeteerLauncherPanel.SceneSnapshot(
        "intro",
        "field_day",
        3,
        List.of(new PuppeteerLauncherPanel.CharacterEntry("lavender", "center", "neutral", 4)),
        4,
        java.util.Map.of(),
        java.util.Map.of(),
        null,
        -1,
        null,
        -1,
        null);
    PuppeteerLauncherPanel.RegisteredAnimation animation = new PuppeteerLauncherPanel.RegisteredAnimation(
        "idle_pose",
        "idle_pose.jes",
        "Root",
        Path.of("idle_pose.jes").toFile(),
        "Timeline block is empty.",
        "0 track(s) • 0 keyframe(s) • 0.1s",
        0,
        0,
        0,
        0,
        true,
        "");

    assertEquals(
        """
        No authored timeline actions yet.
        Launch uses the scene preview above.\
        """,
        PuppeteerLauncherPanel.resolveRegisteredAnimationPreviewText(animation, snapshot));
  }
}
