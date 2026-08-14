package com.jvn.fx.vn;

import java.util.Locale;

import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;

/** Resolves native italic faces and identifies fonts that need a synthetic slant. */
final class ItalicFontSupport {
  static final double SYNTHETIC_SHEAR = -0.22;

  private ItalicFontSupport() {}

  static ResolvedItalic resolve(Font baseFont, FontWeight weight) {
    Font italicFont = Font.font(
        baseFont.getFamily(),
        weight == null ? FontWeight.NORMAL : weight,
        FontPosture.ITALIC,
        baseFont.getSize()
    );
    return resolve(baseFont, italicFont);
  }

  static ResolvedItalic resolve(Font baseFont, Font italicFont) {
    if (hasNativeItalicPosture(italicFont)
        && italicFont.getFamily().equalsIgnoreCase(baseFont.getFamily())) {
      return new ResolvedItalic(italicFont, false);
    }
    return new ResolvedItalic(baseFont, true);
  }

  static boolean hasNativeItalicPosture(Font font) {
    if (font == null || font.getStyle() == null) return false;
    String style = font.getStyle().toLowerCase(Locale.ROOT);
    return style.contains("italic") || style.contains("oblique");
  }

  record ResolvedItalic(Font font, boolean synthetic) {}
}
