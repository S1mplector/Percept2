package com.jvn.core.vn;

import com.jvn.core.vn.ui.VnUiLayoutLoader;
import com.jvn.core.vn.ui.VnUiActionButtonSpec;
import com.jvn.core.vn.ui.VnUiLayoutSpec;
import com.jvn.core.vn.ui.VnUiStyleSpec;
import org.junit.jupiter.api.Test;

import java.util.Properties;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VnUiLayoutLoaderTest {

  @Test
  void parsesDialogueLayoutOverrides() {
    Properties p = new Properties();
    p.setProperty("textBoxY", "0.68");
    p.setProperty("textBoxHeight", "0.30");
    p.setProperty("nameBoxYOffset", "-52");
    p.setProperty("choiceYStart", "0.2");
    p.setProperty("choiceWidthFactor", "0.72");

    VnUiLayoutSpec spec = VnUiLayoutLoader.parse(p, VnUiLayoutSpec.defaults());

    assertEquals(0.68, spec.textBoxY(), 1e-6);
    assertEquals(0.30, spec.textBoxHeight(), 1e-6);
    assertEquals(-52.0, spec.nameBoxYOffset(), 1e-6);
    assertEquals(0.2, spec.choiceYStart(), 1e-6);
    assertEquals(0.72, spec.choiceWidthFactor(), 1e-6);
  }

  @Test
  void serializesKnownKeys() {
    VnUiLayoutSpec defaults = VnUiLayoutSpec.defaults();
    Properties p = VnUiLayoutLoader.toProperties(defaults);

    assertTrue(p.containsKey("textBoxX"));
    assertTrue(p.containsKey("textBoxY"));
    assertTrue(p.containsKey("dialogueTextHorizontalPadding"));
    assertTrue(p.containsKey("choiceYStart"));
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
    p.setProperty("choiceBorderWidth", "3");
    p.setProperty("choiceTextBaselineOffset", "7");
    p.setProperty("characterHeightFactor", "1.2");
    p.setProperty("characterBaselineY", "0.95");

    VnUiStyleSpec style = VnUiLayoutLoader.parseStyle(p, VnUiStyleSpec.defaults());

    assertEquals("assets/ui/textbox.png", style.textBoxAssetPath());
    assertEquals("0,0;1,0;1,1;0,1", style.textBoxBoundsPoints());
    assertEquals("assets/ui/choice.png", style.choiceButtonAssetPath());
    assertEquals("0,0;1,0;0.9,1;0.1,1", style.choiceButtonBoundsPoints());
    assertEquals("#4466aa", style.choiceHoverColor());
    assertEquals(3.0, style.choiceBorderWidth(), 1e-6);
    assertEquals(7.0, style.choiceTextBaselineOffset(), 1e-6);
    assertEquals(1.2, style.characterHeightFactor(), 1e-6);
    assertEquals(0.95, style.characterBaselineY(), 1e-6);
  }

  @Test
  void serializesCharacterFramingStyleKeys() {
    VnUiStyleSpec style = new VnUiStyleSpec(
        null, null, null, null,
        null, null, null, null, null, null,
        null, null, null, null,
        null, null, null, null, null,
        null, null, null, null,
        null, null, null, null,
        null, null, null, null,
        10.0, 2.0, 5.0,
        null, null,
        1.15, 0.9
    );

    Properties p = VnUiLayoutLoader.toStyleProperties(style);

    assertEquals("1.15", p.getProperty("characterHeightFactor"));
    assertEquals("0.9", p.getProperty("characterBaselineY"));
  }

  @Test
  void serializesStyleBoundsPointKeys() {
    VnUiStyleSpec style = new VnUiStyleSpec(
        null, null, null, "0,0;1,0;1,1;0,1",
        null, null, null, null, null, "0,0;1,0;0.85,1;0.15,1",
        null, null, null, "0.05,0.05;0.95,0.05;0.95,0.95;0.05,0.95",
        null, null, null, null, "0,0;1,0;0.9,1;0.1,1",
        null, null, null, null,
        null, null, null, null,
        null, null, null, null,
        10.0, 2.0, 5.0,
        null, null,
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

    VnUiLayoutLoader.LoadResult result = VnUiLayoutLoader.parseWithDiagnostics(
        p,
        VnUiLayoutSpec.defaults(),
        VnUiStyleSpec.defaults()
    );

    assertTrue(result.diagnostics().stream().anyMatch(d -> d.contains("textBoxY")));
    assertTrue(result.diagnostics().stream().anyMatch(d -> d.contains("choiceCornerRadius")));
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
        0.24
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
}
