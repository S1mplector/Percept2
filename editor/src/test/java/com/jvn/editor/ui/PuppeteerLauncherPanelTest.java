package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PuppeteerLauncherPanelTest {

  @Test
  void resolveSnapshotExpandsCharpresetLayerReferences() {
    String script = """
        @character lavender "Lavender"
        @charlayer lavender base assets/demo/characters/lavender/base/lavender_test_sprite_base.png
        @charlayer lavender eyes_neutral assets/demo/characters/lavender/eyes/lavender_test_sprite_eyes_neutral.png
        @charlayer lavender mouth_smile assets/demo/characters/lavender/mouth/lavender_test_sprite_mouth_smile.png
        @charpreset lavender talking $base | $eyes_neutral | $mouth_smile

        @label start
        [show lavender center talking]
        """;

    PuppeteerLauncherPanel.SceneSnapshot snapshot = PuppeteerLauncherPanel.resolveSnapshot(script, 999);

    assertEquals(
        "assets/demo/characters/lavender/base/lavender_test_sprite_base.png"
            + " | assets/demo/characters/lavender/eyes/lavender_test_sprite_eyes_neutral.png"
            + " | assets/demo/characters/lavender/mouth/lavender_test_sprite_mouth_smile.png",
        snapshot.resolveCharacterPath("lavender", "talking"));
  }

  @Test
  void resolveSnapshotSupportsShowAtSyntaxAndInlineComments() {
    String script = """
        @label intro
        [show hero at right happy]   # this should parse with explicit 'at'
        [show guide smile left]       # expression before position is accepted
        [show npc center]             # no expression defaults to neutral
        """;

    PuppeteerLauncherPanel.SceneSnapshot snapshot = PuppeteerLauncherPanel.resolveSnapshot(script, 999);

    assertEquals(3, snapshot.characters.size());
    assertEquals("hero", snapshot.characters.get(0).characterId);
    assertEquals("right", snapshot.characters.get(0).position);
    assertEquals("happy", snapshot.characters.get(0).expression);

    assertEquals("guide", snapshot.characters.get(1).characterId);
    assertEquals("left", snapshot.characters.get(1).position);
    assertEquals("smile", snapshot.characters.get(1).expression);

    assertEquals("npc", snapshot.characters.get(2).characterId);
    assertEquals("center", snapshot.characters.get(2).position);
    assertEquals("neutral", snapshot.characters.get(2).expression);
  }

  @Test
  void resolveActiveLabelStartLineTracksNearestLabelBeforeCursor() {
    String script = """
        @label intro
        Narrator: intro line

        @label scene2
        [show hero center]
        Hero: scene2 line
        """;

    assertEquals(0, PuppeteerLauncherPanel.resolveActiveLabelStartLine(script, 0));
    assertEquals(3, PuppeteerLauncherPanel.resolveActiveLabelStartLine(script, 5));
  }

  @Test
  void snapshotMappingDiagnosticsHelpersReportMappedAndUnmappedAssets() {
    String script = """
        @background park assets/backgrounds/park.png
        @charimg hero happy assets/hero/happy.png
        [bg park]
        [show hero center happy]
        [show stranger center neutral]
        """;

    PuppeteerLauncherPanel.SceneSnapshot snapshot = PuppeteerLauncherPanel.resolveSnapshot(script, 999);

    assertTrue(snapshot.hasBackgroundPathMapping());
    assertTrue(snapshot.hasCharacterPathMapping("hero", "happy"));
    assertTrue(!snapshot.hasCharacterPathMapping("stranger", "neutral"));
  }
}
