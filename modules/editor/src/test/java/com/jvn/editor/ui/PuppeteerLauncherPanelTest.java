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
  void snapshotCarriesPersistentCharacterScale() {
    String source = """
        @scenario scale_preview
        @character emi "Emi" scale=1.2 color=#F28C8C
        @charimg emi neutral assets/characters/emi.png
        @label start
        [show emi center neutral]
        """;

    PuppeteerLauncherPanel.SceneSnapshot snapshot =
        PuppeteerLauncherPanel.resolveSnapshot(source, 4);

    assertEquals(1, snapshot.characters.size());
    assertEquals(1.2, snapshot.characters.get(0).scale, 1e-9);
  }

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
  void resolveSnapshotSupportsCharacterLayerGroups() {
    String source = """
        @label start
        @include /definitions/characters.vns
        [show john center neutral]
        """;
    String definitions = """
        @charlayer john body_default assets/john/body.png
        @charlayer john head_base assets/john/head.png
        @charlayer john eyes_neutral assets/john/eyes.png
        @charlayer john mouth_smile assets/john/mouth.png
        @chargroup john head pivot=0.5,0.28 $head_base | $eyes_neutral | $mouth_smile
        @charpreset john neutral $body_default | $head
        """;

    PuppeteerLauncherPanel.SceneSnapshot snapshot = PuppeteerLauncherPanel.resolveSnapshot(
        source,
        99,
        "/tmp/project/scripts/story/prologue.vns",
        (sourceName, includePath) -> new PuppeteerLauncherPanel.ResolvedInclude(
            "/tmp/project/scripts/definitions/characters.vns",
            definitions));

    assertEquals(
        "assets/john/body.png | assets/john/head.png | assets/john/eyes.png | assets/john/mouth.png",
        snapshot.resolveCharacterPath("john", "neutral"));
    PuppeteerLauncherPanel.CharacterLayerGroupEntry head = snapshot.resolveCharacterLayerGroup("john", "head");
    assertEquals(List.of("head_base", "eyes_neutral", "mouth_smile"), head.layerIds);
    assertTrue(head.hasPivot);
    assertEquals(
        List.of("john_neutral_head", "john_head"),
        PuppeteerLauncherPanel.equivalentSnapshotLayerGroupEntityNames("john", "neutral", "head"));
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
	  void resolveSnapshotKeepsLastInlineTimelineAfterCursorPassesBlock() {
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

    PuppeteerLauncherPanel.SceneSnapshot snapshot = PuppeteerLauncherPanel.resolveSnapshot(source, 8);

    assertTrue(snapshot.hasInlineTimeline());
    assertEquals(2, snapshot.inlineTimelineStartLine);
    assertEquals("intro_inline_3", snapshot.preferredTimelineName());
	    assertTrue(snapshot.inlineTimelineBody.contains("move \"lavender\""));
	  }

	  @Test
	  void resolveSnapshotCapturesAllInlineTimelinesBeforeCursorForSceneReplay() {
	    String source = """
	        @label intro
	        [show john center neutral]
	        timeline {
	          move "john_neutral_body_default" {
	            x: 542
	            dur: 500
	          }
	        }
	        [show lily center neutral]
	        timeline {
	          move "lily_neutral_body_default" {
	            x: 227
	            dur: 500
	          }
	        }
	        [character lily expression talking]
	        """;

	    PuppeteerLauncherPanel.SceneSnapshot snapshot = PuppeteerLauncherPanel.resolveSnapshot(source, 15);

	    assertEquals(2, snapshot.inlineTimelineHistory.size());
	    assertEquals(2, snapshot.inlineTimelineHistory.get(0).startLine());
	    assertEquals(9, snapshot.inlineTimelineHistory.get(1).startLine());
	    assertTrue(snapshot.inlineTimelineBody.contains("move \"lily_neutral_body_default\""));
	  }

	  @Test
	  void resolveSnapshotTracksBracketCharacterExpressionChanges() {
	    String source = """
	        @label intro
	        [show john center neutral]
	        [character john expression talking]
	        [show lily center neutral]
	        [character lily expr happy]
	        """;

	    PuppeteerLauncherPanel.SceneSnapshot snapshot = PuppeteerLauncherPanel.resolveSnapshot(source, 4);

	    assertEquals(2, snapshot.characters.size());
	    assertEquals("talking", snapshot.characters.stream()
	        .filter(ch -> "john".equals(ch.characterId))
	        .findFirst()
	        .orElseThrow()
	        .expression);
	    assertEquals("happy", snapshot.characters.stream()
	        .filter(ch -> "lily".equals(ch.characterId))
	        .findFirst()
	        .orElseThrow()
	        .expression);
	  }

	  @Test
	  void equivalentSnapshotLayerNamesBridgeSharedLayersAcrossExpressions() {
	    String source = """
	        @label intro
	        @include /definitions/characters.vns
	        [show john center talking]
	        """;
	    String definitions = """
	        @charlayer john body_default assets/john/body.png
	        @charlayer john arm_front_default assets/john/arm.png
	        @charlayer john mouth_default assets/john/mouth_default.png
	        @charlayer john mouth_murmuring assets/john/mouth_murmuring.png
	        @charpreset john neutral $body_default | $arm_front_default | $mouth_default
	        @charpreset john talking $body_default | $arm_front_default | $mouth_murmuring
	        """;

	    PuppeteerLauncherPanel.SceneSnapshot snapshot = PuppeteerLauncherPanel.resolveSnapshot(
	        source,
	        2,
	        "/tmp/project/scripts/story/prologue.vns",
	        (sourceName, includePath) -> new PuppeteerLauncherPanel.ResolvedInclude(
	            "/tmp/project/scripts/definitions/characters.vns",
	            definitions));
	    PuppeteerLauncherPanel.CharacterEntry john = snapshot.characters.get(0);

	    List<String> armNames = PuppeteerLauncherPanel.equivalentSnapshotLayerEntityNames(
	        snapshot,
	        john,
	        "arm_front_default");
	    List<String> mouthNames = PuppeteerLauncherPanel.equivalentSnapshotLayerEntityNames(
	        snapshot,
	        john,
	        "mouth_murmuring");

	    assertTrue(armNames.contains("john_talking_arm_front_default"));
	    assertTrue(armNames.contains("john_arm_front_default"));
	    assertTrue(armNames.contains("john_neutral_arm_front_default"));
	    assertEquals(List.of("john_talking_mouth_murmuring", "john_mouth_murmuring"), mouthNames);
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
        null,
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
        null,
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
