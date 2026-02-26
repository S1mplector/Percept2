package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
