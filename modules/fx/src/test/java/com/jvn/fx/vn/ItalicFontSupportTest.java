package com.jvn.fx.vn;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import org.junit.jupiter.api.Test;

class ItalicFontSupportTest {

  @Test
  void recognizesItalicAndObliqueFaceNames() {
    Font italic = Font.font("SansSerif", FontWeight.NORMAL, FontPosture.ITALIC, 18.0);
    assertTrue(ItalicFontSupport.hasNativeItalicPosture(italic));

    Font upright = Font.font("SansSerif", FontWeight.NORMAL, FontPosture.REGULAR, 18.0);
    assertFalse(ItalicFontSupport.hasNativeItalicPosture(upright));
  }

  @Test
  void usesSyntheticFallbackWhenResolvedFaceIsStillUpright() {
    Font base = Font.font("SansSerif", FontWeight.NORMAL, FontPosture.REGULAR, 18.0);

    ItalicFontSupport.ResolvedItalic resolved = ItalicFontSupport.resolve(base, base);

    assertTrue(resolved.synthetic());
    assertSame(base, resolved.font());
  }
}
