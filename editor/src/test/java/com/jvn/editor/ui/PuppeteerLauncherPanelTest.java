package com.jvn.editor.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
