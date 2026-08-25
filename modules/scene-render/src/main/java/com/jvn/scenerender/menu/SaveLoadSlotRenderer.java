package com.jvn.scenerender.menu;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.jvn.core.localization.Localization;
import com.jvn.core.menu.LoadMenuScene;
import com.jvn.core.menu.SaveMenuScene;
import com.jvn.core.menu.config.MenuItemSpec;
import com.jvn.core.menu.config.MenuLayoutSpec;
import com.jvn.core.scene2d.Blitter2D;

/**
 * Renders inline save/load slot preview thumbnails, the side (large) preview panel, preview
 * metadata text, and the load-menu page-control widgets (cycle-left/right, favorites toggle,
 * page track/selector, per-slot favorite icon).
 */
final class SaveLoadSlotRenderer {
  private static final String LOAD_CYCLE_LEFT_ACTIVE_ASSET = "assets/ui/load/controls/page_turn_left_active.png";
  private static final String LOAD_CYCLE_LEFT_INACTIVE_ASSET = "assets/ui/load/controls/page_turn_left_inactive.png";
  private static final String LOAD_CYCLE_RIGHT_ACTIVE_ASSET = "assets/ui/load/controls/page_turn_right_active.png";
  private static final String LOAD_CYCLE_RIGHT_INACTIVE_ASSET = "assets/ui/load/controls/page_turn_right_inactive.png";
  private static final String LOAD_PAGE_TRACK_ASSET = "assets/ui/load/controls/page_track.png";
  private static final String LOAD_PAGE_SELECTOR_ASSET = "assets/ui/load/controls/page_selector.png";
  private static final String LOAD_FAVORITES_BUTTON_ACTIVE_ASSET = "assets/ui/load/controls/favorites_button_active.png";
  private static final String LOAD_FAVORITES_BUTTON_INACTIVE_ASSET = "assets/ui/load/controls/favorites_button_inactive.png";
  private static final String LOAD_SLOT_FAVORITE_ICON_ASSET = "assets/ui/load/controls/slot_favorite_icon.png";

  private final Blitter2D blitter;
  private final MenuBackgroundRenderer background;

  SaveLoadSlotRenderer(Blitter2D blitter, MenuBackgroundRenderer background) {
    this.blitter = blitter;
    this.background = background;
  }

  // --- Side-preview visibility ---

  boolean shouldShowLoadSidePreview(LoadMenuScene scene, MenuItemSpec[] specs) {
    if (scene == null) return true;
    MenuItemSpec selectedSpec = scene.getSelected() >= 0 ? scene.getMenuItemSpec(scene.getSelected()) : null;
    List<MenuItemSpec> candidates = collectSidePreviewCandidates(specs, scene.getMenuItemSpec(0), selectedSpec);
    return resolveSidePreviewPreference(candidates);
  }

  boolean shouldShowSaveSidePreview(SaveMenuScene scene, MenuItemSpec[] specs) {
    if (scene == null) return true;
    List<MenuItemSpec> candidates = collectSidePreviewCandidates(specs, scene.getMenuItemSpec(0), scene.getMenuItemSpec(scene.getSelected()));
    return resolveSidePreviewPreference(candidates);
  }

  double resolveLoadListAreaWidth(LoadMenuScene scene, MenuItemSpec[] specs, double viewportWidth) {
    return shouldShowLoadSidePreview(scene, specs) ? viewportWidth * 0.6 : viewportWidth;
  }

  private List<MenuItemSpec> collectSidePreviewCandidates(MenuItemSpec[] specs, MenuItemSpec... fallbacks) {
    List<MenuItemSpec> out = new ArrayList<>();
    if (specs != null) {
      for (MenuItemSpec spec : specs) {
        if (spec != null) out.add(spec);
      }
    }
    if (out.isEmpty() && fallbacks != null) {
      for (MenuItemSpec fallback : fallbacks) {
        if (fallback != null) out.add(fallback);
      }
    }
    return out;
  }

  private boolean resolveSidePreviewPreference(List<MenuItemSpec> candidates) {
    if (candidates == null || candidates.isEmpty()) return true;

    boolean sawExplicitPreference = false;
    for (MenuItemSpec item : candidates) {
      Boolean show = background.parseItemExtraBooleanNullable(item, "showSidePreview");
      if (show != null) {
        sawExplicitPreference = true;
        if (!show) return false;
      }
      Boolean alias = background.parseItemExtraBooleanNullable(item, "sidePreview");
      if (alias != null) {
        sawExplicitPreference = true;
        if (!alias) return false;
      }
    }
    if (sawExplicitPreference) return true;

    // Explicit absolute bounds are typically authored against full-width card layouts.
    // When side preview defaults on, cards are clamped into a narrower list area and can overlap.
    for (MenuItemSpec item : candidates) {
      if (hasExplicitBounds(item)) return false;
    }
    return true;
  }

  private boolean hasExplicitBounds(MenuItemSpec itemSpec) {
    if (itemSpec == null) return false;
    return itemSpec.boundsX() != null
        && itemSpec.boundsY() != null
        && itemSpec.boundsWidth() != null
        && itemSpec.boundsHeight() != null;
  }

  File getThumbnailFile(LoadMenuScene scene) {
    String dir = scene.getSaveDirectory();
    String name = scene.getSelectedName();
    if (dir == null || name == null) return null;
    File f = new File(dir, name + ".png");
    return f.exists() ? f : null;
  }

  // --- Inline slot previews ---

  void drawInlineSaveSlotPreviews(
      SaveMenuScene scene,
      MenuLayoutSpec layout,
      MenuItemSpec[] specs,
      double areaX,
      double areaWidth,
      double h
  ) {
    if (scene == null) return;
    List<String> saves = scene.getSaves();
    int count = scene.getItemCount();
    for (int i = 0; i < count; i++) {
      MenuItemSpec spec = specs != null && i < specs.length ? specs[i] : scene.getMenuItemSpec(i);
      if (!isInlineSlotPreviewEnabled(spec, true)) continue;
      MenuBackgroundRenderer.Rect itemRect = background.resolveItemRect(i, count, spec, specs, layout, areaX, areaWidth, h);
      String previewPath = null;
      if (i == 0) {
        previewPath = scene.getCurrentBackgroundPreviewPath();
      } else {
        int saveIndex = i - 1;
        if (saveIndex >= 0 && saveIndex < saves.size()) {
          File thumb = new File(scene.getSaveDirectory(), saves.get(saveIndex) + ".png");
          if (thumb.exists()) previewPath = thumb.getAbsolutePath();
        }
      }
      drawInlineSlotPreview(itemRect, spec, previewPath, i == scene.getSelected(), i == 0 ? Localization.t("save.new") : Localization.t("load.no_preview"));
    }
  }

  void drawInlineLoadSlotPreviews(
      LoadMenuScene scene,
      MenuLayoutSpec layout,
      MenuItemSpec[] specs,
      int startIndex,
      int visibleCount,
      double areaX,
      double areaWidth,
      double h
  ) {
    if (scene == null) return;
    List<String> saves = scene.getSaves();
    int count = Math.max(0, Math.min(visibleCount, saves.size() - Math.max(0, startIndex)));
    for (int i = 0; i < count; i++) {
      int globalIndex = startIndex + i;
      MenuItemSpec spec = specs != null && i < specs.length ? specs[i] : scene.getMenuItemSpec(globalIndex);
      if (!isInlineSlotPreviewEnabled(spec, true)) continue;
      MenuBackgroundRenderer.Rect itemRect = background.resolveItemRect(i, count, spec, specs, layout, areaX, areaWidth, h);
      String previewPath = null;
      if (globalIndex >= 0 && globalIndex < saves.size()) {
        File thumb = new File(scene.getSaveDirectory(), saves.get(globalIndex) + ".png");
        if (thumb.exists()) previewPath = thumb.getAbsolutePath();
      }
      drawInlineSlotPreview(itemRect, spec, previewPath, globalIndex == scene.getSelected(), Localization.t("load.no_preview"));
    }
  }

  private boolean isInlineSlotPreviewEnabled(MenuItemSpec itemSpec, boolean defaultIfMissingSpec) {
    if (itemSpec == null) return defaultIfMissingSpec;
    return itemSpec.slotPreviewEnabled();
  }

  private MenuBackgroundRenderer.Rect resolveInlineSlotPreviewRect(MenuItemSpec itemSpec, MenuBackgroundRenderer.Rect itemRect) {
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

  private void drawInlineSlotPreview(MenuBackgroundRenderer.Rect itemRect, MenuItemSpec itemSpec, String previewPath, boolean selected, String fallbackText) {
    if (itemRect == null) return;
    MenuBackgroundRenderer.Rect previewRect = resolveInlineSlotPreviewRect(itemSpec, itemRect);
    if (previewRect.w() <= 1 || previewRect.h() <= 1) return;
    String fitMode = resolveSlotPreviewFitMode(itemSpec);
    boolean containFit = "contain".equals(fitMode) || "fit".equals(fitMode);

    MenuTheme.ColorSpec backdrop = MenuTheme.ColorSpec.rgb255(6, 9, 14, 0.95);
    blitter.setFill(backdrop.r(), backdrop.g(), backdrop.b(), backdrop.a());
    background.fillRoundRect(previewRect.x(), previewRect.y(), previewRect.w(), previewRect.h(), 7, 7);

    double[] previewDims = previewPath != null ? background.imageDimensions(previewPath).orElse(null) : null;
    if (previewDims != null) {
      if (containFit) {
        drawImageContain(previewPath, previewDims, previewRect);
      } else {
        drawImageCover(previewPath, previewDims, previewRect);
      }
    } else {
      String placeholderPath = itemSpec != null ? itemSpec.slotPreviewPlaceholderAssetPath() : null;
      double[] placeholderDims = placeholderPath != null ? background.imageDimensions(placeholderPath).orElse(null) : null;
      if (placeholderDims != null) {
        if (containFit) {
          drawImageContain(placeholderPath, placeholderDims, previewRect);
        } else {
          drawImageCover(placeholderPath, placeholderDims, previewRect);
        }
      } else {
        MenuTheme.ColorSpec emptyFill = MenuTheme.ColorSpec.rgb255(32, 36, 48, 0.95);
        blitter.setFill(emptyFill.r(), emptyFill.g(), emptyFill.b(), emptyFill.a());
        background.fillRoundRect(previewRect.x(), previewRect.y(), previewRect.w(), previewRect.h(), 7, 7);
        MenuTheme.ColorSpec textColor = MenuTheme.ColorSpec.rgb255(205, 212, 225, 0.85);
        blitter.setFill(textColor.r(), textColor.g(), textColor.b(), textColor.a());
        MenuTheme.FontSpec hintFont = background.theme.getHintFontSpec();
        blitter.setFont(hintFont.family(), hintFont.size(), hintFont.bold());
        String txt = background.firstNonBlank(
            background.extra(itemSpec, "slotPreviewFallbackText"),
            background.extra(itemSpec, "previewFallbackText"),
            fallbackText
        );
        if (txt == null || txt.isBlank()) txt = Localization.t("load.no_preview");
        double tw = blitter.measureTextWidth(txt, hintFont.size(), hintFont.bold());
        double tx = previewRect.x() + Math.max(6, (previewRect.w() - tw) / 2.0);
        double ty = previewRect.y() + previewRect.h() * 0.56;
        blitter.drawText(txt, tx, ty, hintFont.size(), hintFont.bold());
      }
    }

    String framePath = itemSpec != null ? itemSpec.slotPreviewFrameAssetPath() : null;
    boolean hasFrame = framePath != null && background.imageDimensions(framePath).isPresent();
    if (hasFrame) {
      blitter.drawImage(framePath, previewRect.x(), previewRect.y(), previewRect.w(), previewRect.h());
    } else {
      MenuTheme.ColorSpec frameStroke = selected
          ? MenuTheme.ColorSpec.rgb255(170, 220, 255, 0.95)
          : MenuTheme.ColorSpec.rgb255(150, 170, 205, 0.7);
      blitter.setStroke(frameStroke.r(), frameStroke.g(), frameStroke.b(), frameStroke.a());
      blitter.setStrokeWidth(selected ? 1.8 : 1.1);
      background.strokeRoundRect(previewRect.x(), previewRect.y(), previewRect.w(), previewRect.h(), 7, 7);
    }
  }

  private String resolveSlotPreviewFitMode(MenuItemSpec itemSpec) {
    if (itemSpec == null || itemSpec.extras() == null) return "cover";
    String raw = background.firstNonBlank(itemSpec.extras().get("slotPreviewFit"), itemSpec.extras().get("previewFit"));
    if (raw == null || raw.isBlank()) return "cover";
    String normalized = raw.trim().toLowerCase();
    if ("contain".equals(normalized) || "fit".equals(normalized)) return "contain";
    return "cover";
  }

  private void drawImageCover(String imagePath, double[] imageDims, MenuBackgroundRenderer.Rect target) {
    if (imagePath == null || imageDims == null || target == null) return;
    double iw = imageDims[0];
    double ih = imageDims[1];
    if (iw <= 0 || ih <= 0) return;

    double targetRatio = target.w() / target.h();
    double imageRatio = iw / ih;
    double sx = 0;
    double sy = 0;
    double sw = iw;
    double sh = ih;
    if (imageRatio > targetRatio) {
      sw = ih * targetRatio;
      sx = (iw - sw) / 2.0;
    } else {
      sh = iw / targetRatio;
      sy = (ih - sh) / 2.0;
    }
    blitter.drawImageRegion(imagePath, sx, sy, sw, sh, target.x(), target.y(), target.w(), target.h());
  }

  private void drawImageContain(String imagePath, double[] imageDims, MenuBackgroundRenderer.Rect target) {
    if (imagePath == null || imageDims == null || target == null) return;
    double iw = imageDims[0];
    double ih = imageDims[1];
    if (iw <= 0 || ih <= 0) return;
    double scale = Math.min(target.w() / iw, target.h() / ih);
    double dw = iw * scale;
    double dh = ih * scale;
    double dx = target.x() + (target.w() - dw) / 2.0;
    double dy = target.y() + (target.h() - dh) / 2.0;
    blitter.drawImage(imagePath, dx, dy, dw, dh);
  }

  // --- Large side preview panel ---

  void drawPreviewResource(String path, double w, double h) {
    try {
      double[] dims = background.imageDimensions(path).orElse(null);
      if (dims == null) { drawPreviewPlaceholder(w, h); return; }
      drawPreviewImage(path, dims, w, h);
    } catch (Exception e) {
      drawPreviewPlaceholder(w, h);
    }
  }

  void drawPreviewFile(File file, double w, double h) {
    try {
      String absolutePath = file.getAbsolutePath();
      double[] dims = background.imageDimensions(absolutePath).orElse(null);
      if (dims == null) { drawPreviewPlaceholder(w, h); return; }
      drawPreviewImage(absolutePath, dims, w, h);
    } catch (Exception e) {
      drawPreviewPlaceholder(w, h);
    }
  }

  private void drawPreviewImage(String imagePath, double[] imageDims, double w, double h) {
    double panelX = w * 0.65;
    double panelY = h * 0.25;
    double panelW = w * 0.3;
    double panelH = h * 0.5;
    MenuTheme.ColorSpec panelFill = MenuTheme.ColorSpec.rgb255(255, 255, 255, 0.1);
    blitter.setFill(panelFill.r(), panelFill.g(), panelFill.b(), panelFill.a());
    background.fillRoundRect(panelX - 8, panelY - 8, panelW + 16, panelH + 16, 12, 12);
    double scale = Math.min(panelW / imageDims[0], panelH / imageDims[1]);
    double iw = imageDims[0] * scale;
    double ih = imageDims[1] * scale;
    double ix = panelX + (panelW - iw) / 2;
    double iy = panelY + (panelH - ih) / 2;
    blitter.drawImage(imagePath, ix, iy, iw, ih);
  }

  void drawPreviewPlaceholder(double w, double h) {
    double panelX = w * 0.65;
    double panelY = h * 0.25;
    double panelW = w * 0.3;
    double panelH = h * 0.5;
    MenuTheme.ColorSpec panelFill = MenuTheme.ColorSpec.rgb255(255, 255, 255, 0.1);
    blitter.setFill(panelFill.r(), panelFill.g(), panelFill.b(), panelFill.a());
    background.fillRoundRect(panelX - 8, panelY - 8, panelW + 16, panelH + 16, 12, 12);
    MenuTheme.ColorSpec grayFill = MenuTheme.ColorSpec.rgb255(128, 128, 128);
    blitter.setFill(grayFill.r(), grayFill.g(), grayFill.b(), grayFill.a());
    MenuTheme.FontSpec itemFont = background.theme.getItemFontSpec();
    blitter.setFont(itemFont.family(), itemFont.size(), itemFont.bold());
    background.drawCenteredText(Localization.t("load.no_preview"), panelX + panelW / 2, panelY + panelH / 2, itemFont, grayFill);
  }

  void drawPreviewMetadata(String scenarioId, Long timestampMs, Integer nodeIndex, double w, double h) {
    double panelX = w * 0.65;
    double panelY = h * 0.25;
    double panelH = h * 0.5;
    double textY = panelY + panelH + 20;
    blitter.setFill(0.827, 0.827, 0.827, 1.0); // Color.LIGHTGRAY
    MenuTheme.FontSpec hintFont = background.theme.getHintFontSpec();
    blitter.setFont(hintFont.family(), hintFont.size(), hintFont.bold());
    String ts = timestampMs != null ? formatTimestamp(timestampMs) : "";
    String line1 = (ts.isEmpty() ? "" : ts);
    String line2 = (scenarioId != null ? (Localization.t("meta.scenario") + ": " + scenarioId) : "");
    String line3 = (nodeIndex != null ? (Localization.t("meta.node") + ": " + nodeIndex) : "");
    double x = panelX;
    if (!line1.isEmpty()) blitter.drawText(line1, x, textY, hintFont.size(), hintFont.bold());
    if (!line2.isEmpty()) blitter.drawText(line2, x, textY + 18, hintFont.size(), hintFont.bold());
    if (!line3.isEmpty()) blitter.drawText(line3, x, textY + 36, hintFont.size(), hintFont.bold());
  }

  private String formatTimestamp(long millis) {
    try {
      java.time.Instant inst = java.time.Instant.ofEpochMilli(millis);
      java.time.ZonedDateTime z = java.time.ZonedDateTime.ofInstant(inst, java.time.ZoneId.systemDefault());
      return z.toLocalDate().toString() + " " + z.toLocalTime().withNano(0).toString();
    } catch (Exception e) { return Long.toString(millis); }
  }

  // --- Load-menu page controls ---

  boolean areLoadControlsVisible(MenuItemSpec template) {
    return background.parseItemExtraBoolean(template, "controlsVisible", true)
        && background.parseItemExtraBoolean(template, "showControls", true);
  }

  MenuItemSpec resolveLoadTemplateItem(LoadMenuScene scene, MenuItemSpec[] visibleSpecs) {
    if (visibleSpecs != null && visibleSpecs.length > 0 && visibleSpecs[0] != null) return visibleSpecs[0];
    if (scene == null) return null;
    MenuItemSpec first = scene.getMenuItemSpec(0);
    if (first != null) return first;
    return scene.getMenuItemSpec(scene.getSelected());
  }

  void drawLoadMenuControls(
      LoadMenuScene scene,
      MenuLayoutSpec layout,
      MenuItemSpec[] visibleSpecs,
      int visibleStartIndex,
      int visibleDrawCount,
      double w,
      double h,
      double listAreaWidth
  ) {
    if (scene == null) return;
    MenuItemSpec template = resolveLoadTemplateItem(scene, visibleSpecs);
    if (!areLoadControlsVisible(template)) return;

    int pageCount = scene.getPageCount();
    int currentPage = scene.getCurrentPageIndex();
    boolean canPageLeft = currentPage > 0;
    boolean canPageRight = currentPage < Math.max(0, pageCount - 1);

    MenuBackgroundRenderer.Rect leftRect = resolveLoadCycleLeftRect(template, w, h);
    MenuBackgroundRenderer.Rect rightRect = resolveLoadCycleRightRect(template, w, h);
    MenuBackgroundRenderer.Rect trackRect = resolveLoadPageTrackRect(template, w, h);
    MenuBackgroundRenderer.Rect selectorRect = resolveLoadPageSelectorRect(template, trackRect, scene.getPageProgress01());
    MenuBackgroundRenderer.Rect favoritesRect = resolveLoadFavoritesButtonRect(template, w, h);

    drawLoadControlImage(resolveLoadCycleLeftAsset(template, canPageLeft), leftRect);
    drawLoadControlImage(resolveLoadCycleRightAsset(template, canPageRight), rightRect);
    drawLoadControlImage(resolveLoadPageTrackAsset(template), trackRect);
    drawLoadControlImage(resolveLoadPageSelectorAsset(template), selectorRect);
    drawLoadControlImage(resolveLoadFavoritesButtonAsset(template, scene.isFavoritesOnly()), favoritesRect);

    String slotFavoriteAsset = resolveLoadSlotFavoriteIconAsset(template);
    if (background.imageDimensions(slotFavoriteAsset).isEmpty()) return;
    if (visibleDrawCount <= 0) return;

    MenuItemSpec[] specs = visibleSpecs;
    if (specs == null || specs.length < visibleDrawCount) {
      specs = new MenuItemSpec[visibleDrawCount];
      for (int i = 0; i < visibleDrawCount; i++) {
        specs[i] = scene.getMenuItemSpec(visibleStartIndex + i);
      }
    }
    for (int i = 0; i < visibleDrawCount; i++) {
      int globalIndex = visibleStartIndex + i;
      if (globalIndex < 0 || globalIndex >= scene.getItemCount()) continue;
      MenuBackgroundRenderer.Rect itemRect = background.resolveItemRect(i, visibleDrawCount, specs[i], specs, layout, 0, listAreaWidth, h);
      MenuBackgroundRenderer.Rect iconRect = resolveLoadSlotFavoriteIconRect(template, itemRect);
      blitter.setGlobalAlpha(scene.isFavoriteAt(globalIndex) ? 1.0 : 0.28);
      blitter.drawImage(slotFavoriteAsset, iconRect.x(), iconRect.y(), iconRect.w(), iconRect.h());
      blitter.setGlobalAlpha(1.0);
    }
  }

  private void drawLoadControlImage(String assetPath, MenuBackgroundRenderer.Rect target) {
    if (target == null || target.w() <= 0 || target.h() <= 0) return;
    if (background.imageDimensions(assetPath).isEmpty()) return;
    blitter.drawImage(assetPath, target.x(), target.y(), target.w(), target.h());
  }

  MenuBackgroundRenderer.Rect resolveLoadCycleLeftRect(MenuItemSpec itemSpec, double w, double h) {
    return resolveLoadControlRect(itemSpec, "cycleLeft", 0.084375, 0.48148, 0.01979, 0.04630, w, h);
  }

  MenuBackgroundRenderer.Rect resolveLoadCycleRightRect(MenuItemSpec itemSpec, double w, double h) {
    return resolveLoadControlRect(itemSpec, "cycleRight", 0.702083, 0.48148, 0.01979, 0.04630, w, h);
  }

  MenuBackgroundRenderer.Rect resolveLoadPageTrackRect(MenuItemSpec itemSpec, double w, double h) {
    return resolveLoadControlRect(itemSpec, "pageTrack", 0.23906, 0.74444, 0.28854, 0.06019, w, h);
  }

  MenuBackgroundRenderer.Rect resolveLoadFavoritesButtonRect(MenuItemSpec itemSpec, double w, double h) {
    return resolveLoadControlRect(itemSpec, "favoritesButton", 0.51094, 0.74537, 0.04635, 0.05556, w, h);
  }

  MenuBackgroundRenderer.Rect resolveLoadPageSelectorRect(MenuItemSpec itemSpec, MenuBackgroundRenderer.Rect trackRect, double progress01) {
    if (trackRect == null) return new MenuBackgroundRenderer.Rect(0, 0, 1, 1);
    Double selectorWRaw = background.parseExtraDouble(itemSpec, "pageSelectorWidth");
    Double selectorHRaw = background.parseExtraDouble(itemSpec, "pageSelectorHeight");
    double selectorW = selectorWRaw == null
        ? trackRect.w() * 0.0903
        : (selectorWRaw <= 1.0 ? trackRect.w() * selectorWRaw : selectorWRaw);
    double selectorH = selectorHRaw == null
        ? trackRect.h() * 0.8769
        : (selectorHRaw <= 1.0 ? trackRect.h() * selectorHRaw : selectorHRaw);
    selectorW = background.clamp(selectorW, 10.0, Math.max(10.0, trackRect.w()));
    selectorH = background.clamp(selectorH, 10.0, Math.max(10.0, trackRect.h() * 1.4));
    double t = background.clamp01(progress01);
    double x = trackRect.x() + t * Math.max(0.0, trackRect.w() - selectorW);
    double y = trackRect.y() + (trackRect.h() - selectorH) / 2.0;
    return new MenuBackgroundRenderer.Rect(x, y, selectorW, selectorH);
  }

  MenuBackgroundRenderer.Rect resolveLoadSlotFavoriteIconRect(MenuItemSpec itemSpec, MenuBackgroundRenderer.Rect itemRect) {
    if (itemRect == null) return new MenuBackgroundRenderer.Rect(0, 0, 1, 1);
    Double xVal = background.parseExtraDouble(itemSpec, "slotFavoriteX");
    Double yVal = background.parseExtraDouble(itemSpec, "slotFavoriteY");
    Double wVal = background.parseExtraDouble(itemSpec, "slotFavoriteWidth");
    Double hVal = background.parseExtraDouble(itemSpec, "slotFavoriteHeight");
    double x = itemRect.x() + background.resolveCoordinate(xVal != null ? xVal : 0.0335, itemRect.w());
    double y = itemRect.y() + background.resolveCoordinate(yVal != null ? yVal : 0.002, itemRect.h());
    double w = background.resolveSize(wVal != null ? wVal : 0.0928, itemRect.w());
    double h = background.resolveSize(hVal != null ? hVal : 0.1455, itemRect.h());
    w = background.clamp(w, 8, Math.max(8, itemRect.w()));
    h = background.clamp(h, 8, Math.max(8, itemRect.h()));
    x = background.clamp(x, itemRect.x(), itemRect.x() + Math.max(0.0, itemRect.w() - w));
    y = background.clamp(y, itemRect.y(), itemRect.y() + Math.max(0.0, itemRect.h() - h));
    return new MenuBackgroundRenderer.Rect(x, y, w, h);
  }

  private MenuBackgroundRenderer.Rect resolveLoadControlRect(
      MenuItemSpec itemSpec,
      String keyPrefix,
      double defaultX,
      double defaultY,
      double defaultW,
      double defaultH,
      double viewportW,
      double viewportH
  ) {
    Double xVal = background.parseExtraDouble(itemSpec, keyPrefix + "X");
    Double yVal = background.parseExtraDouble(itemSpec, keyPrefix + "Y");
    Double wVal = background.parseExtraDouble(itemSpec, keyPrefix + "Width");
    Double hVal = background.parseExtraDouble(itemSpec, keyPrefix + "Height");
    double x = background.resolveCoordinate(xVal != null ? xVal : defaultX, viewportW);
    double y = background.resolveCoordinate(yVal != null ? yVal : defaultY, viewportH);
    double w = background.resolveSize(wVal != null ? wVal : defaultW, viewportW);
    double h = background.resolveSize(hVal != null ? hVal : defaultH, viewportH);
    w = background.clamp(w, 8, Math.max(8, viewportW));
    h = background.clamp(h, 8, Math.max(8, viewportH));
    x = background.clamp(x, 0, Math.max(0, viewportW - w));
    y = background.clamp(y, 0, Math.max(0, viewportH - h));
    return new MenuBackgroundRenderer.Rect(x, y, w, h);
  }

  private String resolveLoadCycleLeftAsset(MenuItemSpec itemSpec, boolean active) {
    return active
        ? background.firstNonBlank(background.extra(itemSpec, "cycleLeftActiveAsset"), LOAD_CYCLE_LEFT_ACTIVE_ASSET)
        : background.firstNonBlank(background.extra(itemSpec, "cycleLeftInactiveAsset"), LOAD_CYCLE_LEFT_INACTIVE_ASSET);
  }

  private String resolveLoadCycleRightAsset(MenuItemSpec itemSpec, boolean active) {
    return active
        ? background.firstNonBlank(background.extra(itemSpec, "cycleRightActiveAsset"), LOAD_CYCLE_RIGHT_ACTIVE_ASSET)
        : background.firstNonBlank(background.extra(itemSpec, "cycleRightInactiveAsset"), LOAD_CYCLE_RIGHT_INACTIVE_ASSET);
  }

  private String resolveLoadPageTrackAsset(MenuItemSpec itemSpec) {
    return background.firstNonBlank(background.extra(itemSpec, "pageTrackAsset"), LOAD_PAGE_TRACK_ASSET);
  }

  private String resolveLoadPageSelectorAsset(MenuItemSpec itemSpec) {
    return background.firstNonBlank(background.extra(itemSpec, "pageSelectorAsset"), LOAD_PAGE_SELECTOR_ASSET);
  }

  private String resolveLoadFavoritesButtonAsset(MenuItemSpec itemSpec, boolean active) {
    return active
        ? background.firstNonBlank(background.extra(itemSpec, "favoritesButtonActiveAsset"), LOAD_FAVORITES_BUTTON_ACTIVE_ASSET)
        : background.firstNonBlank(background.extra(itemSpec, "favoritesButtonInactiveAsset"), LOAD_FAVORITES_BUTTON_INACTIVE_ASSET);
  }

  private String resolveLoadSlotFavoriteIconAsset(MenuItemSpec itemSpec) {
    return background.firstNonBlank(background.extra(itemSpec, "slotFavoriteAsset"), LOAD_SLOT_FAVORITE_ICON_ASSET);
  }
}
