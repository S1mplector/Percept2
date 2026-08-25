package com.jvn.scenerender.menu;

import java.util.ArrayList;
import java.util.List;

import com.jvn.core.menu.config.MenuItemSpec;
import com.jvn.core.menu.config.MenuLayoutSpec;
import com.jvn.core.menu.config.MenuStyleSpec;
import com.jvn.core.scene2d.Blitter2D;

/** Renders the vertical/paged menu-item list shared by main/pause/save/load/settings screens. */
final class MenuListRenderer {
  private final Blitter2D blitter;
  private final MenuBackgroundRenderer background;

  MenuListRenderer(Blitter2D blitter, MenuBackgroundRenderer background) {
    this.blitter = blitter;
    this.background = background;
  }

  void drawMenuList(String[] items, int selected, double w, double h) {
    drawMenuList(items, selected, null, null, null, null, 0, w, h, false);
  }

  void drawMenuList(
      String[] items,
      int selected,
      boolean[] enabled,
      MenuStyleSpec[] styles,
      MenuItemSpec[] itemSpecs,
      MenuLayoutSpec layout,
      double areaX,
      double areaWidth,
      double h
  ) {
    drawMenuList(items, selected, enabled, styles, itemSpecs, layout, areaX, areaWidth, h, false);
  }

  void drawMenuList(
      String[] items,
      int selected,
      boolean[] enabled,
      MenuStyleSpec[] styles,
      MenuItemSpec[] itemSpecs,
      MenuLayoutSpec layout,
      double areaX,
      double areaWidth,
      double h,
      boolean reserveInlineSlotPreviewSpace
  ) {
    String align = layout != null ? layout.textAlign() : "center";
    double textPadXDefault = 18;
    double textPadYDefault = 0;
    for (int i = 0; i < items.length; i++) {
      MenuStyleSpec style = styles != null && i < styles.length ? styles[i] : null;
      MenuItemSpec item = itemSpecs != null && i < itemSpecs.length ? itemSpecs[i] : null;
      boolean isEnabled = enabled == null || i >= enabled.length || enabled[i];
      boolean sel = i == selected;
      boolean sectionItem = background.isSectionItem(item);
      boolean bodyItem = background.isBodyTextItem(item);
      boolean noteItem = background.isNoteTextItem(item);
      String label = (sectionItem || bodyItem || noteItem)
          ? (items[i] == null ? "" : items[i])
          : withPrefix(items[i], style, sel, isEnabled);
      MenuTheme.ColorSpec color = background.resolveItemColorSpec(style, sel, isEnabled);
      MenuTheme.FontSpec font = background.resolveItemFontSpec(style, item);
      MenuBackgroundRenderer.Rect rect = background.resolveItemRect(i, items.length, item, itemSpecs, layout, areaX, areaWidth, h);
      boolean inlinePreviewEnabled = reserveInlineSlotPreviewSpace && isInlineSlotPreviewEnabled(item, true);
      MenuBackgroundRenderer.Rect inlinePreviewRect = inlinePreviewEnabled ? resolveInlineSlotPreviewRect(item, rect) : null;
      double reservedRightSpace = inlinePreviewRect != null
          ? Math.max(0, rect.x() + rect.w() - inlinePreviewRect.x() + 8)
          : 0;

      if (!sectionItem && !bodyItem && !noteItem) {
        String backgroundAsset = resolveButtonAssetPath(item, style, sel, isEnabled);
        if (background.imageDimensions(backgroundAsset).isPresent()) {
          blitter.drawImage(backgroundAsset, rect.x(), rect.y(), rect.w(), rect.h());
        } else {
          MenuTheme.ColorSpec bgFill = !isEnabled
              ? MenuTheme.ColorSpec.rgb255(80, 80, 90, 0.45)
              : (sel ? MenuTheme.ColorSpec.rgb255(90, 120, 180, 0.5) : MenuTheme.ColorSpec.rgb255(32, 36, 46, 0.55));
          blitter.setFill(bgFill.r(), bgFill.g(), bgFill.b(), bgFill.a());
          background.fillRoundRect(rect.x(), rect.y(), rect.w(), rect.h(), 10, 10);
          MenuTheme.ColorSpec borderColor = sel
              ? MenuTheme.ColorSpec.rgb255(170, 210, 255, 0.9)
              : MenuTheme.ColorSpec.rgb255(110, 130, 160, 0.55);
          blitter.setStroke(borderColor.r(), borderColor.g(), borderColor.b(), borderColor.a());
          blitter.setStrokeWidth(sel ? 2.0 : 1.1);
          background.strokeRoundRect(rect.x(), rect.y(), rect.w(), rect.h(), 10, 10);
        }
      } else {
        if (sectionItem) {
          double dividerY = rect.y() + rect.h() * 0.62;
          MenuTheme.ColorSpec dividerColor = MenuTheme.ColorSpec.rgb255(160, 176, 210, 0.28);
          blitter.setStroke(dividerColor.r(), dividerColor.g(), dividerColor.b(), dividerColor.a());
          blitter.setStrokeWidth(1.0);
          blitter.drawLine(rect.x(), dividerY, rect.x() + rect.w(), dividerY);
        } else if (noteItem) {
          MenuTheme.ColorSpec noteFill = MenuTheme.ColorSpec.rgb255(24, 31, 42, sel && isEnabled ? 0.86 : 0.72);
          blitter.setFill(noteFill.r(), noteFill.g(), noteFill.b(), noteFill.a());
          background.fillRoundRect(rect.x(), rect.y(), rect.w(), rect.h(), 10, 10);
          MenuTheme.ColorSpec noteBorder = MenuTheme.ColorSpec.rgb255(118, 138, 172, 0.38);
          blitter.setStroke(noteBorder.r(), noteBorder.g(), noteBorder.b(), noteBorder.a());
          blitter.setStrokeWidth(1.0);
          background.strokeRoundRect(rect.x(), rect.y(), rect.w(), rect.h(), 10, 10);
        }
      }

      String iconPath = item != null ? item.iconPath() : null;
      double iconSize = 0;
      if (background.imageDimensions(iconPath).isPresent()) {
        iconSize = background.clamp(rect.h() * 0.56, 12, 36);
        double iconX = rect.x() + 10;
        double iconY = rect.y() + (rect.h() - iconSize) / 2.0;
        blitter.setGlobalAlpha(isEnabled ? 0.98 : 0.55);
        blitter.drawImage(iconPath, iconX, iconY, iconSize, iconSize);
        blitter.setGlobalAlpha(1.0);
      }

      if (bodyItem || noteItem) {
        drawWrappedItemText(label, rect, item, style, layout, font, color, iconSize, reservedRightSpace);
        continue;
      }

      blitter.setFont(font.family(), font.size(), font.bold());
      double tw = blitter.measureTextWidth(label, font.size(), font.bold());
      String itemAlign = resolveItemTextAlign(item, align);
      Double textXRaw = background.parseExtraDouble(item, "textX");
      Double textBaselineRaw = MenuBackgroundRenderer.firstNonNull(background.parseExtraDouble(item, "textBaselineY"), background.parseExtraDouble(item, "textY"));
      double textPadX = resolveItemTextPaddingX(item, style, textPadXDefault);
      double textPadY = resolveItemTextPaddingY(item, style, textPadYDefault);
      double leftInset = rect.x() + Math.max(0, textPadX) + (iconSize > 0 ? iconSize + 8 : 0);
      double rightInset = rect.x() + Math.max(0, rect.w() - textPadX - reservedRightSpace);
      double x = textXRaw != null
          ? rect.x() + background.resolveLocalCoordinate(textXRaw, rect.w())
          : switch (itemAlign) {
            case "left" -> leftInset;
            case "right" -> rightInset - tw;
            default -> leftInset + Math.max(0, (rightInset - leftInset - tw) / 2.0);
          };
      if (sectionItem && textXRaw == null) {
        x = rect.x();
      }
      double baseline = textBaselineRaw != null
          ? rect.y() + background.resolveLocalCoordinate(textBaselineRaw, rect.h())
          : rect.y() + rect.h() * 0.55 + textPadY;
      drawItemText(label, x, baseline, style, font, color);
    }
  }

  private boolean isInlineSlotPreviewEnabled(MenuItemSpec itemSpec, boolean defaultIfMissingSpec) {
    if (itemSpec == null) return defaultIfMissingSpec;
    return itemSpec.slotPreviewEnabled();
  }

  MenuBackgroundRenderer.Rect resolveInlineSlotPreviewRect(MenuItemSpec itemSpec, MenuBackgroundRenderer.Rect itemRect) {
    if (itemRect == null) return new MenuBackgroundRenderer.Rect(0, 0, 1, 1);
    if (itemSpec != null
        && itemSpec.slotPreviewX() != null
        && itemSpec.slotPreviewY() != null
        && itemSpec.slotPreviewWidth() != null
        && itemSpec.slotPreviewHeight() != null) {
      double x = itemRect.x() + background.resolveCoordinate(itemSpec.slotPreviewX(), itemRect.w());
      double y = itemRect.y() + background.resolveCoordinate(itemSpec.slotPreviewY(), itemRect.h());
      double w = background.resolveSize(itemSpec.slotPreviewWidth(), itemRect.w());
      double h = background.resolveSize(itemSpec.slotPreviewHeight(), itemRect.h());
      w = background.clamp(w, 8, Math.max(8, itemRect.w()));
      h = background.clamp(h, 8, Math.max(8, itemRect.h()));
      x = background.clamp(x, itemRect.x(), itemRect.x() + Math.max(0, itemRect.w() - w));
      y = background.clamp(y, itemRect.y(), itemRect.y() + Math.max(0, itemRect.h() - h));
      return new MenuBackgroundRenderer.Rect(x, y, w, h);
    }

    double margin = 6;
    double h = background.clamp(itemRect.h() - margin * 2, 14, Math.max(14, itemRect.h() - margin * 2));
    double w = background.clamp(Math.min(itemRect.w() * 0.34, h * 1.6), 24, Math.max(24, itemRect.w() - margin * 2));
    double x = itemRect.x() + itemRect.w() - w - margin;
    double y = itemRect.y() + (itemRect.h() - h) / 2.0;
    return new MenuBackgroundRenderer.Rect(x, y, w, h);
  }

  private String resolveButtonAssetPath(MenuItemSpec item, MenuStyleSpec style, boolean selected, boolean enabled) {
    String path = null;
    if (!enabled) {
      path = background.firstNonBlank(
          item != null ? item.buttonDisabledAssetPath() : null,
          item != null ? item.buttonAssetPath() : null,
          style != null ? style.buttonDisabledAssetPath() : null,
          style != null ? style.buttonAssetPath() : null
      );
    } else if (selected) {
      path = background.firstNonBlank(
          item != null ? item.buttonSelectedAssetPath() : null,
          style != null ? style.buttonSelectedAssetPath() : null,
          style != null ? style.buttonHoverAssetPath() : null,
          item != null ? item.buttonAssetPath() : null,
          style != null ? style.buttonAssetPath() : null
      );
    } else {
      path = background.firstNonBlank(
          item != null ? item.buttonAssetPath() : null,
          style != null ? style.buttonAssetPath() : null
      );
    }
    return path;
  }

  private String resolveItemTextAlign(MenuItemSpec itemSpec, String defaultAlign) {
    if (itemSpec != null && itemSpec.extras() != null) {
      String raw = background.firstNonBlank(itemSpec.extras().get("textAlign"), itemSpec.extras().get("align"));
      if (raw != null) {
        String normalized = raw.trim().toLowerCase();
        if ("left".equals(normalized) || "center".equals(normalized) || "right".equals(normalized)) {
          return normalized;
        }
      }
    }
    return defaultAlign == null ? "center" : defaultAlign.toLowerCase();
  }

  private double resolveItemTextPaddingX(MenuItemSpec itemSpec, MenuStyleSpec style, double defaultValue) {
    Double parsed = MenuBackgroundRenderer.firstNonNull(background.parseExtraDouble(itemSpec, "textPaddingX"), background.parseExtraDouble(itemSpec, "textPadX"));
    if (parsed != null) return parsed;
    return style != null && style.buttonTextPaddingX() != null ? style.buttonTextPaddingX() : defaultValue;
  }

  private double resolveItemTextPaddingY(MenuItemSpec itemSpec, MenuStyleSpec style, double defaultValue) {
    Double parsed = MenuBackgroundRenderer.firstNonNull(background.parseExtraDouble(itemSpec, "textPaddingY"), background.parseExtraDouble(itemSpec, "textPadY"));
    if (parsed != null) return parsed;
    return style != null && style.buttonTextPaddingY() != null ? style.buttonTextPaddingY() : defaultValue;
  }

  private double resolveBodyPaddingX(MenuItemSpec itemSpec, MenuStyleSpec style) {
    Double parsed = background.parseExtraDouble(itemSpec, "bodyPaddingX");
    if (parsed != null) return Math.max(0.0, parsed);
    return style != null && style.buttonTextPaddingX() != null ? Math.max(0.0, style.buttonTextPaddingX()) : 18.0;
  }

  private double resolveBodyPaddingY(MenuItemSpec itemSpec, MenuStyleSpec style) {
    Double parsed = background.parseExtraDouble(itemSpec, "bodyPaddingY");
    if (parsed != null) return Math.max(0.0, parsed);
    return style != null && style.buttonTextPaddingY() != null ? Math.max(0.0, style.buttonTextPaddingY()) : 10.0;
  }

  private double resolveBodyLineHeight(MenuItemSpec itemSpec, double fontSize) {
    Double parsed = background.parseExtraDouble(itemSpec, "bodyLineHeight");
    if (parsed != null && parsed > 0) return parsed;
    return Math.max(fontSize * 1.35, fontSize + 6.0);
  }

  private String resolveBodyAlign(MenuItemSpec itemSpec, MenuLayoutSpec layout) {
    if (itemSpec != null && itemSpec.extras() != null) {
      String raw = background.firstNonBlank(itemSpec.extras().get("bodyAlign"), itemSpec.extras().get("textAlign"));
      if (raw != null) {
        String normalized = raw.trim().toLowerCase();
        if ("left".equals(normalized) || "center".equals(normalized) || "right".equals(normalized)) {
          return normalized;
        }
      }
    }
    return layout != null ? layout.textAlign() : "left";
  }

  private void drawWrappedItemText(
      String label,
      MenuBackgroundRenderer.Rect rect,
      MenuItemSpec item,
      MenuStyleSpec style,
      MenuLayoutSpec layout,
      MenuTheme.FontSpec font,
      MenuTheme.ColorSpec color,
      double iconSize,
      double reservedRightSpace
  ) {
    blitter.setFont(font.family(), font.size(), font.bold());
    double padX = resolveBodyPaddingX(item, style);
    double padY = resolveBodyPaddingY(item, style);
    double leftInset = rect.x() + padX + (iconSize > 0 ? iconSize + 8 : 0);
    double rightInset = rect.x() + Math.max(0, rect.w() - padX - reservedRightSpace);
    double maxWidth = Math.max(16.0, rightInset - leftInset);
    double lineHeight = resolveBodyLineHeight(item, font.size());
    List<String> lines = wrapTextToWidth(label, maxWidth, font);
    double baseline = rect.y() + padY + font.size();
    double maxBaseline = rect.y() + rect.h() - padY;
    String align = resolveBodyAlign(item, layout);
    for (String line : lines) {
      if (baseline > maxBaseline) break;
      double x = switch (align) {
        case "center" -> leftInset + Math.max(0, (maxWidth - blitter.measureTextWidth(line, font.size(), font.bold())) / 2.0);
        case "right" -> rightInset - blitter.measureTextWidth(line, font.size(), font.bold());
        default -> leftInset;
      };
      drawItemText(line, x, baseline, style, font, color);
      baseline += lineHeight;
    }
  }

  void drawItemText(String label, double x, double baseline, MenuStyleSpec style, MenuTheme.FontSpec font, MenuTheme.ColorSpec color) {
    Double itemOp = style != null ? style.itemOpacity() : null;
    if (itemOp != null && itemOp < 0.999) blitter.setGlobalAlpha(itemOp);

    String shadowRaw = style != null ? style.itemShadowColor() : null;
    if (shadowRaw != null && !shadowRaw.isBlank()) {
      MenuTheme.ColorSpec shadow = background.parseColorRgba(shadowRaw, null);
      if (shadow != null) {
        double sx = style.itemShadowOffsetX() != null ? style.itemShadowOffsetX() : 1.5;
        double sy = style.itemShadowOffsetY() != null ? style.itemShadowOffsetY() : 1.5;
        blitter.setFill(shadow.r(), shadow.g(), shadow.b(), shadow.a());
        blitter.setFont(font.family(), font.size(), font.bold());
        blitter.drawText(label, x + sx, baseline + sy, font.size(), font.bold());
      }
    }

    blitter.setFill(color.r(), color.g(), color.b(), color.a());
    blitter.setFont(font.family(), font.size(), font.bold());
    blitter.drawText(label, x, baseline, font.size(), font.bold());
    if (itemOp != null && itemOp < 0.999) blitter.setGlobalAlpha(1.0);
  }

  private String withPrefix(String label, MenuStyleSpec style, boolean selected, boolean enabled) {
    String base = label == null ? "" : label;
    String prefix;
    if (!enabled) {
      prefix = background.firstNonBlank(style != null ? style.itemDisabledPrefix() : null,
          style != null ? style.itemPrefix() : null,
          background.theme.getItemPrefix());
    } else if (selected) {
      prefix = background.firstNonBlank(style != null ? style.itemSelectedPrefix() : null, background.theme.getItemSelectedPrefix());
    } else {
      prefix = background.firstNonBlank(style != null ? style.itemPrefix() : null, background.theme.getItemPrefix());
    }
    return (prefix == null ? "" : prefix) + base;
  }

  private List<String> wrapTextToWidth(String text, double maxWidth, MenuTheme.FontSpec font) {
    List<String> lines = new ArrayList<>();
    if (text == null || text.isBlank()) {
      lines.add("");
      return lines;
    }
    String[] paragraphs = text.replace("\r", "").split("\n", -1);
    for (int p = 0; p < paragraphs.length; p++) {
      String paragraph = paragraphs[p].trim();
      if (paragraph.isEmpty()) {
        lines.add("");
        continue;
      }
      String[] words = paragraph.split("\\s+");
      String current = "";
      for (String word : words) {
        String candidate = current.isEmpty() ? word : current + " " + word;
        if (blitter.measureTextWidth(candidate, font.size(), font.bold()) <= maxWidth) {
          current = candidate;
          continue;
        }
        if (!current.isEmpty()) {
          lines.add(current);
          current = "";
        }
        if (blitter.measureTextWidth(word, font.size(), font.bold()) <= maxWidth) {
          current = word;
        } else {
          lines.addAll(breakLongWord(word, maxWidth, font));
        }
      }
      if (!current.isEmpty()) lines.add(current);
      if (p < paragraphs.length - 1) lines.add("");
    }
    return lines.isEmpty() ? List.of("") : lines;
  }

  private List<String> breakLongWord(String word, double maxWidth, MenuTheme.FontSpec font) {
    List<String> pieces = new ArrayList<>();
    if (word == null || word.isEmpty()) return pieces;
    int start = 0;
    while (start < word.length()) {
      int end = start + 1;
      while (end <= word.length() && blitter.measureTextWidth(word.substring(start, end), font.size(), font.bold()) <= maxWidth) {
        end++;
      }
      int safeEnd = Math.max(start + 1, end - 1);
      pieces.add(word.substring(start, safeEnd));
      start = safeEnd;
    }
    return pieces;
  }
}
