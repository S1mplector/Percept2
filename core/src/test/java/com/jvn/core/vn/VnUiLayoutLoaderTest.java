package com.jvn.core.vn;

import com.jvn.core.vn.ui.VnUiLayoutLoader;
import com.jvn.core.vn.ui.VnUiLayoutSpec;
import org.junit.jupiter.api.Test;

import java.util.Properties;

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
}
