package com.jvn.core.vn;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.jvn.core.vn.ui.VnUiActionButtonSpec;
import com.jvn.core.vn.ui.VnUiLayoutLoader;
import com.jvn.core.vn.ui.VnUiLayoutSpec;
import com.jvn.core.vn.ui.VnUiStyleSpec;

class VnUiLayoutLoaderTest {

  @Test
  void parsesDialogueLayoutOverrides() {
    Properties p = new Properties();
    p.setProperty("textBoxY", "0.68");
    p.setProperty("textBoxHeight", "0.30");
    p.setProperty("nameBoxYOffset", "-52");
    p.setProperty("nameTextTopPadding", "6");
    p.setProperty("nameTextBottomPadding", "4");
    p.setProperty("nameTextYAlign", "0.5");
    p.setProperty("choiceYStart", "0.2");
    p.setProperty("choiceWidthFactor", "0.72");
    p.setProperty("choiceTextTopPadding", "8");
    p.setProperty("choiceTextBottomPadding", "6");
    p.setProperty("choiceTextYAlign", "1.0");
    p.setProperty("nvlSpeakerWidth", "192");
    p.setProperty("bubbleWidthFactor", "0.34");

    VnUiLayoutSpec spec = VnUiLayoutLoader.parse(p, VnUiLayoutSpec.defaults());

    assertEquals(0.68, spec.textBoxY(), 1e-6);
    assertEquals(0.30, spec.textBoxHeight(), 1e-6);
    assertEquals(-52.0, spec.nameBoxYOffset(), 1e-6);
    assertEquals(6.0, spec.nameTextTopPadding(), 1e-6);
    assertEquals(4.0, spec.nameTextBottomPadding(), 1e-6);
    assertEquals(0.5, spec.nameTextYAlign(), 1e-6);
    assertEquals(0.2, spec.choiceYStart(), 1e-6);
    assertEquals(0.72, spec.choiceWidthFactor(), 1e-6);
    assertEquals(8.0, spec.choiceTextTopPadding(), 1e-6);
    assertEquals(6.0, spec.choiceTextBottomPadding(), 1e-6);
    assertEquals(1.0, spec.choiceTextYAlign(), 1e-6);
    assertEquals(192.0, spec.nvlSpeakerWidth(), 1e-6);
    assertEquals(0.34, spec.bubbleWidthFactor(), 1e-6);
  }

  @Test
  void serializesKnownKeys() {
    VnUiLayoutSpec defaults = VnUiLayoutSpec.defaults();
    Properties p = VnUiLayoutLoader.toProperties(defaults);

    assertTrue(p.containsKey("textBoxX"));
    assertTrue(p.containsKey("textBoxY"));
    assertTrue(p.containsKey("dialogueTextHorizontalPadding"));
    assertTrue(p.containsKey("nameTextYAlign"));
    assertTrue(p.containsKey("choiceYStart"));
    assertTrue(p.containsKey("choiceTextYAlign"));
    assertTrue(p.containsKey("nvlX"));
    assertTrue(p.containsKey("bubbleTailSize"));
    assertEquals("0.75", p.getProperty("textBoxY"));
  }

  @Test
  void parsesStyleOverrides() {
    Properties p = new Properties();
    p.setProperty("textBoxAsset", "assets/ui/textbox.png");
    p.setProperty("textBoxBoundsPoints", "0,0;1,0;1,1;0,1");
    p.setProperty("choiceButtonAsset", "assets/ui/choice.png");
    p.setProperty("choiceButtonBoundsPoints", "0,0;1,0;0.9,1;0.1,1");
    p.setProperty("choiceHoverColor", "#4466aa");
    p.setProperty("nameTextXAlign", "0.5");
    p.setProperty("dialogueTextXAlign", "1.0");
    p.setProperty("choiceBorderWidth", "3");
    p.setProperty("choiceTextBaselineOffset", "7");
    p.setProperty("choiceTextXAlign", "0.5");
    p.setProperty("characterHeightFactor", "1.2");
    p.setProperty("characterBaselineY", "0.95");
    p.setProperty("nvlPanelColor", "#08111acc");
    p.setProperty("bubbleColor", "#203040");
    p.setProperty("bubbleCornerRadius", "24");

    VnUiStyleSpec style = VnUiLayoutLoader.parseStyle(p, VnUiStyleSpec.defaults());

    assertEquals("assets/ui/textbox.png", style.textBoxAssetPath());
    assertEquals("0,0;1,0;1,1;0,1", style.textBoxBoundsPoints());
    assertEquals("assets/ui/choice.png", style.choiceButtonAssetPath());
    assertEquals("0,0;1,0;0.9,1;0.1,1", style.choiceButtonBoundsPoints());
    assertEquals("#4466aa", style.choiceHoverColor());
    assertEquals(0.5, style.nameTextXAlign(), 1e-6);
    assertEquals(1.0, style.dialogueTextXAlign(), 1e-6);
    assertEquals(3.0, style.choiceBorderWidth(), 1e-6);
    assertEquals(7.0, style.choiceTextBaselineOffset(), 1e-6);
    assertEquals(0.5, style.choiceTextXAlign(), 1e-6);
    assertEquals(1.2, style.characterHeightFactor(), 1e-6);
    assertEquals(0.95, style.characterBaselineY(), 1e-6);
    assertEquals("#08111acc", style.nvlPanelColor());
    assertEquals("#203040", style.bubbleColor());
    assertEquals(24.0, style.bubbleCornerRadius(), 1e-6);
  }

  @Test
  void serializesTextAlignmentStyleKeys() {
    Properties raw = new Properties();
    raw.setProperty("nameTextXAlign", "0.5");
    raw.setProperty("dialogueTextXAlign", "1.0");
    raw.setProperty("choiceTextXAlign", "0.25");

    VnUiStyleSpec style = VnUiLayoutLoader.parseStyle(raw, VnUiStyleSpec.defaults());
    Properties serialized = VnUiLayoutLoader.toStyleProperties(style);

    assertEquals("0.5", serialized.getProperty("nameTextXAlign"));
    assertEquals("1", serialized.getProperty("dialogueTextXAlign"));
    assertEquals("0.25", serialized.getProperty("choiceTextXAlign"));
  }

  @Test
  void serializesCharacterFramingStyleKeys() {
    VnUiStyleSpec style = new VnUiStyleSpec(
        null, null, null, null,
        null, null, null, null, null, null, null,
        null, null,
        null, null, null, null, null, null,
        null, null, null, null, null,
        null, null, null, null,
        null, null, null, null,
        null, null, null, null,
        10.0, 2.0, 5.0, null,
        null, null, null,
        1.15, 0.9
    );

    Properties p = VnUiLayoutLoader.toStyleProperties(style);

    assertEquals("1.15", p.getProperty("characterHeightFactor"));
    assertEquals("0.9", p.getProperty("characterBaselineY"));
  }

  @Test
  void supportsRenpyStyleGuiParityFieldsForDialogueChoiceAndNvl() {
    Properties p = new Properties();
    p.setProperty("textBoxY", "0.69");
    p.setProperty("textBoxHeight", "0.28");
    p.setProperty("nameBoxXOffset", "48");
    p.setProperty("nameBoxYOffset", "-56");
    p.setProperty("nameBoxWidth", "260");
    p.setProperty("nameTextXAlign", "0.5");
    p.setProperty("dialogueTextHorizontalPadding", "34");
    p.setProperty("dialogueTextRightPadding", "28");
    p.setProperty("dialogueTextXAlign", "0.25");
    p.setProperty("choiceWidthFactor", "0.78");
    p.setProperty("choiceHeight", "62");
    p.setProperty("choiceGap", "14");
    p.setProperty("choiceTextXAlign", "0.5");
    p.setProperty("choiceButtonSelectedAsset", "assets/ui/choice_selected.png");
    p.setProperty("choiceButtonDisabledAsset", "assets/ui/choice_disabled.png");
    p.setProperty("nvlWidth", "0.82");
    p.setProperty("nvlHeight", "0.76");
    p.setProperty("nvlPanelAsset", "assets/ui/nvl_panel.png");

    VnUiLayoutSpec layout = VnUiLayoutLoader.parse(p, VnUiLayoutSpec.defaults());
    VnUiStyleSpec style = VnUiLayoutLoader.parseStyle(p, VnUiStyleSpec.defaults());
    Properties serialized = VnUiLayoutLoader.toProperties(layout, style, List.of());

    assertEquals(0.69, layout.textBoxY(), 1e-6);
    assertEquals(0.28, layout.textBoxHeight(), 1e-6);
    assertEquals(48.0, layout.nameBoxXOffset(), 1e-6);
    assertEquals(-56.0, layout.nameBoxYOffset(), 1e-6);
    assertEquals(260.0, layout.nameBoxWidth(), 1e-6);
    assertEquals(0.5, style.nameTextXAlign(), 1e-6);
    assertEquals(34.0, layout.dialogueTextHorizontalPadding(), 1e-6);
    assertEquals(28.0, layout.dialogueTextRightPadding(), 1e-6);
    assertEquals(0.25, style.dialogueTextXAlign(), 1e-6);
    assertEquals(0.78, layout.choiceWidthFactor(), 1e-6);
    assertEquals(62.0, layout.choiceHeight(), 1e-6);
    assertEquals(14.0, layout.choiceGap(), 1e-6);
    assertEquals(0.5, style.choiceTextXAlign(), 1e-6);
    assertEquals("assets/ui/choice_selected.png", style.choiceButtonSelectedAssetPath());
    assertEquals("assets/ui/choice_disabled.png", style.choiceButtonDisabledAssetPath());
    assertEquals(0.82, layout.nvlWidth(), 1e-6);
    assertEquals(0.76, layout.nvlHeight(), 1e-6);
    assertEquals("assets/ui/nvl_panel.png", style.nvlPanelAssetPath());
    assertEquals("assets/ui/choice_selected.png", serialized.getProperty("choiceButtonSelectedAsset"));
    assertEquals("assets/ui/choice_disabled.png", serialized.getProperty("choiceButtonDisabledAsset"));
    assertEquals("assets/ui/nvl_panel.png", serialized.getProperty("nvlPanelAsset"));
  }

  @Test
  void serializesNvlAndBubbleKeys() {
    VnUiLayoutSpec layout = new VnUiLayoutSpec(
        0.0, 0.75, 1.0, 0.25, 20.0,
        20.0, -40.0, 200.0, 40.0,
        10.0, 25.0, 20.0, 40.0,
        20.0, 10.0, 0.5, -1.0, 0.6,
        50.0, 10.0, 20.0, false,
        0.06, 0.08, 0.88, 0.74, 28.0, 180.0, 16.0, 7,
        0.31, 104.0, 20.0, 32.0, 22.0
    );
    VnUiStyleSpec style = new VnUiStyleSpec(
        null, null, null, null,
        null, null, null, null, null, null, null,
        null, null,
        null, null, null, null, null, null,
        null, null, null, null, null,
        null, null, null, null,
        null, null, null, null,
        null, null, null, null,
        10.0, 2.0, 5.0, null,
        null, null, null,
        null, null,
        null, "#08111acc", 0.9, "#ffd88a", "#e8edf6",
        null, "#203040", 0.95, "#90a0c0", "#ffd78a", "#f1f5ff", 18.0, 2.5
    );

    Properties p = VnUiLayoutLoader.toProperties(layout, style, List.of());

    assertEquals("-1", p.getProperty("nameTextYAlign"));
    assertEquals("-1", p.getProperty("choiceTextYAlign"));
    assertEquals("0.06", p.getProperty("nvlX"));
    assertEquals("7", p.getProperty("nvlMaxEntries"));
    assertEquals("0.31", p.getProperty("bubbleWidthFactor"));
    assertEquals("#08111acc", p.getProperty("nvlPanelColor"));
    assertEquals("#203040", p.getProperty("bubbleColor"));
    assertEquals("18", p.getProperty("bubbleCornerRadius"));
  }

  @Test
  void serializesVerticalAlignmentLayoutKeys() {
    VnUiLayoutSpec layout = new VnUiLayoutSpec(
        0.0, 0.75, 1.0, 0.25, 20.0,
        20.0, -40.0, 200.0, 40.0,
        10.0, 25.0,
        6.0, 4.0, 0.5,
        20.0, 40.0, 20.0, 10.0,
        0.5, -1.0, 0.6, 50.0, 10.0, 20.0,
        8.0, 6.0, 1.0,
        false,
        0.08, 0.10, 0.84, 0.72, 24.0, 160.0, 18.0, 6,
        0.28, 92.0, 18.0, 26.0, 18.0
    );

    Properties p = VnUiLayoutLoader.toProperties(layout);

    assertEquals("6", p.getProperty("nameTextTopPadding"));
    assertEquals("4", p.getProperty("nameTextBottomPadding"));
    assertEquals("0.5", p.getProperty("nameTextYAlign"));
    assertEquals("8", p.getProperty("choiceTextTopPadding"));
    assertEquals("6", p.getProperty("choiceTextBottomPadding"));
    assertEquals("1", p.getProperty("choiceTextYAlign"));
  }

  @Test
  void serializesStyleBoundsPointKeys() {
    VnUiStyleSpec style = new VnUiStyleSpec(
        null, null, null, "0,0;1,0;1,1;0,1",
        null, null, null, null, null, null, null,
        "0,0;1,0;0.85,1;0.15,1", null,
        null, null, null, null, null,
        "0.05,0.05;0.95,0.05;0.95,0.95;0.05,0.95",
        null, null, null, null, "0,0;1,0;0.9,1;0.1,1",
        null, null, null, null,
        null, null, null, null,
        null, null, null, null,
        10.0, 2.0, 5.0, null,
        null, null, null,
        null, null
    );

    Properties p = VnUiLayoutLoader.toStyleProperties(style);

    assertEquals("0,0;1,0;1,1;0,1", p.getProperty("textBoxBoundsPoints"));
    assertEquals("0,0;1,0;0.85,1;0.15,1", p.getProperty("nameBoxBoundsPoints"));
    assertEquals("0.05,0.05;0.95,0.05;0.95,0.95;0.05,0.95", p.getProperty("dialogueTextBoundsPoints"));
    assertEquals("0,0;1,0;0.9,1;0.1,1", p.getProperty("choiceButtonBoundsPoints"));
  }

  @Test
  void reportsDiagnosticsForInvalidNumbers() {
    Properties p = new Properties();
    p.setProperty("textBoxY", "oops");
    p.setProperty("choiceCornerRadius", "not-a-number");
    p.setProperty("dialogueTextXAlign", "5");

    VnUiLayoutLoader.LoadResult result = VnUiLayoutLoader.parseWithDiagnostics(
        p,
        VnUiLayoutSpec.defaults(),
        VnUiStyleSpec.defaults()
    );

    assertTrue(result.diagnostics().stream().anyMatch(d -> d.contains("textBoxY")));
    assertTrue(result.diagnostics().stream().anyMatch(d -> d.contains("choiceCornerRadius")));
    assertTrue(result.diagnostics().stream().anyMatch(d -> d.contains("dialogueTextXAlign")));
  }

  @Test
  void parsesTextboxButtonSpecs() {
    Properties p = new Properties();
    p.setProperty("textBoxButton.ids", "save,load");
    p.setProperty("textBoxButton.save.label", "Save");
    p.setProperty("textBoxButton.save.action", "save_menu");
    p.setProperty("textBoxButton.save.x", "0.72");
    p.setProperty("textBoxButton.save.y", "0.06");
    p.setProperty("textBoxButton.save.width", "0.12");
    p.setProperty("textBoxButton.save.height", "0.24");
    p.setProperty("textBoxButton.save.space", "viewport");
    p.setProperty("textBoxButton.save.boundsPoints", "0,0;1,0;0.8,1;0.2,1");
    p.setProperty("textBoxButton.load.label", "Load");
    p.setProperty("textBoxButton.load.action", "load_menu");

    VnUiLayoutLoader.LoadResult result = VnUiLayoutLoader.parseWithDiagnostics(
        p,
        VnUiLayoutSpec.defaults(),
        VnUiStyleSpec.defaults()
    );

    assertEquals(2, result.textBoxButtons().size());
    VnUiActionButtonSpec save = result.textBoxButtons().get(0);
    assertEquals("save", save.id());
    assertEquals("Save", save.label());
    assertEquals("save_menu", save.action());
    assertEquals("0,0;1,0;0.8,1;0.2,1", save.boundsPoints());
    assertEquals(0.72, save.x(), 1e-6);
    assertEquals(0.24, save.height(), 1e-6);
    assertEquals("viewport", save.coordinateSpace());
    assertTrue(save.viewportSpace());
  }

  @Test
  void serializesTextboxButtonSpecs() {
    VnUiActionButtonSpec save = new VnUiActionButtonSpec(
        "save",
        "Save",
        "save_menu",
        null,
        true,
        "assets/ui/save.png",
        "assets/ui/save_hover.png",
        "assets/ui/save_disabled.png",
        "0,0;1,0;1,1;0,1",
        0.74,
        0.08,
        0.1,
        0.24,
        "viewport"
    );

    Properties p = VnUiLayoutLoader.toProperties(
        VnUiLayoutSpec.defaults(),
        VnUiStyleSpec.defaults(),
        List.of(save)
    );

    assertEquals("save", p.getProperty("textBoxButton.ids"));
    assertEquals("save_menu", p.getProperty("textBoxButton.save.action"));
    assertEquals("assets/ui/save.png", p.getProperty("textBoxButton.save.asset"));
    assertEquals("0,0;1,0;1,1;0,1", p.getProperty("textBoxButton.save.boundsPoints"));
    assertEquals("0.74", p.getProperty("textBoxButton.save.x"));
    assertEquals("viewport", p.getProperty("textBoxButton.save.space"));
  }

  @Test
  void emitsDiagnosticsForUnknownKeysAdjustmentsAndButtonIssues() {
    Properties p = new Properties();
    p.setProperty("textBoxX", "-2.0");
    p.setProperty("choiceWidthFactor", "2.0");
    p.setProperty("textBoxButon.ids", "typo");
    p.setProperty("textBoxButton.ids", "save,save");
    p.setProperty("textBoxButton.save.action", "open_menu");
    p.setProperty("textBoxButton.save.boundsPoints", "0,0;1,0");

    VnUiLayoutLoader.LoadResult result = VnUiLayoutLoader.parseWithDiagnostics(
        p,
        VnUiLayoutSpec.defaults(),
        VnUiStyleSpec.defaults()
    );

    assertEquals(1, result.textBoxButtons().size());
    assertTrue(result.diagnostics().stream().anyMatch(d -> d.contains("Unknown dialogue layout key 'textBoxButon.ids'")));
    assertTrue(result.diagnostics().stream().anyMatch(d -> d.contains("Value for 'textBoxX' was adjusted")));
    assertTrue(result.diagnostics().stream().anyMatch(d -> d.contains("Duplicate textbox button id 'save'")));
    assertTrue(result.diagnostics().stream().anyMatch(d -> d.contains("open_menu without target")));
    assertTrue(result.diagnostics().stream().anyMatch(d -> d.contains("bounds points")));
  }

  @Test
  void missingDialogueLayoutDoesNotInventTextBoxButtons() throws Exception {
    Path root = Files.createTempDirectory("jvn-dialogue-defaults-");

    VnUiLayoutLoader.LoadResult result = VnUiLayoutLoader.loadFromProjectRootWithDiagnostics(root.toFile());

    assertTrue(result.textBoxButtons().isEmpty());
  }
}
