package com.jvn.editor.ui;

import javafx.scene.paint.Color;

/**
 * Shared color palette for external layout studio editors.
 * Keeps preview/renderer colors consistent across studio surfaces.
 */
final class LayoutStudioPalette {
  static final Color CANVAS_BACKGROUND = Color.web("#0f141c");
  static final Color CANVAS_BACKGROUND_ALT = Color.web("#10131a");
  static final Color GRID_LINE = Color.rgb(255, 255, 255, 0.06);

  static final Color TEXT_PRIMARY = Color.web("#ffffff");
  static final Color TEXT_SECONDARY = Color.web("#e2e6ee");
  static final Color TEXT_MUTED = Color.web("#cfd3db");
  static final Color TEXT_DISABLED = Color.web("#9599a4");
  static final Color TEXT_WARNING = Color.web("#f2b26f");
  static final Color TEXT_SUCCESS = Color.web("#8bcf98");
  static final Color ITEM_COLOR_DEFAULT = Color.web("#d3d3d3");
  static final Color ITEM_COLOR_SELECTED = Color.web("#ffff00");
  static final Color ITEM_COLOR_DISABLED = Color.web("#808080");

  static final Color PANEL_FILL = Color.rgb(50, 56, 74, 0.78);
  static final Color PANEL_FILL_SELECTED = Color.rgb(78, 102, 148, 0.8);
  static final Color PANEL_FILL_DISABLED = Color.rgb(58, 58, 66, 0.55);
  static final Color PANEL_FILL_SOFT = Color.rgb(34, 40, 52, 0.25);
  static final Color PANEL_BORDER = Color.rgb(126, 146, 188, 0.7);
  static final Color PANEL_BORDER_SELECTED = Color.rgb(188, 220, 255, 0.95);
  static final Color PANEL_BORDER_LIGHT = Color.rgb(160, 170, 210, 0.95);

  static final Color ACCENT_BLUE = Color.rgb(110, 170, 255, 0.92);
  static final Color ACCENT_BLUE_LIGHT = Color.rgb(180, 210, 255, 0.9);
  static final Color ACCENT_GOLD = Color.rgb(255, 198, 110, 0.9);
  static final Color ACCENT_GREEN = Color.rgb(84, 210, 136, 0.95);
  static final Color ACCENT_GREEN_DARK = Color.rgb(10, 30, 18, 0.9);

  static final Color TAG_BG = Color.rgb(12, 16, 25, 0.88);
  static final Color TAG_BORDER = Color.rgb(110, 140, 200, 0.8);
  static final Color TAG_TEXT = Color.rgb(225, 235, 255, 0.95);

  static final Color DIALOGUE_OVERLAY = Color.rgb(0, 0, 0, 0.78);
  static final Color DIALOGUE_ASSET_OVERLAY = Color.rgb(0, 0, 0, 0.30);
  static final Color DIALOGUE_NAME_FILL = Color.rgb(42, 47, 68, 0.95);

  private LayoutStudioPalette() {
  }
}
