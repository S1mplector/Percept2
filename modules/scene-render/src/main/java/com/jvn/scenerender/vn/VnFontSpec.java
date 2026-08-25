package com.jvn.scenerender.vn;

/**
 * Plain, platform-agnostic font descriptor for {@code VnRenderer} and its collaborators — the
 * same shape as {@code com.jvn.scenerender.menu.MenuTheme.FontSpec}, replacing the raw
 * {@code javafx.scene.text.Font} fields the pre-retrofit class held directly.
 */
public record VnFontSpec(String family, double size, boolean bold) {
  public VnFontSpec withSize(double newSize) {
    return new VnFontSpec(family, newSize, bold);
  }
}
