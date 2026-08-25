package com.jvn.scenerender.menu;

import com.jvn.core.menu.config.MenuItemSpec;
import com.jvn.core.menu.config.MenuLayoutSpec;
import com.jvn.core.scene2d.Blitter2D;

/** Renders settings-screen sliders, their reset buttons, and boolean toggles. */
final class SettingsSliderRenderer {
  private final Blitter2D blitter;
  private final MenuBackgroundRenderer background;

  SettingsSliderRenderer(Blitter2D blitter, MenuBackgroundRenderer background) {
    this.blitter = blitter;
    this.background = background;
  }

  double[] sliderGeometry(int index, int count, MenuItemSpec item, MenuItemSpec[] itemSpecs, MenuLayoutSpec layout, double w, double h) {
    MenuBackgroundRenderer.Rect rowRect = background.resolveItemRect(index, count, item, itemSpecs, layout, 0, w, h);
    double padX = Math.max(20, rowRect.w() * 0.16);
    double defaultSliderX = rowRect.x() + padX;
    double defaultSliderW = Math.max(140, rowRect.w() - (padX * 2));
    double defaultSliderY = rowRect.y() + rowRect.h() * 0.7;

    Double sliderXRaw = background.parseExtraDouble(item, "sliderX");
    Double sliderYRaw = background.parseExtraDouble(item, "sliderY");
    Double sliderWRaw = background.parseExtraDouble(item, "sliderWidth");

    double sliderX = sliderXRaw != null ? background.resolveCoordinate(sliderXRaw, w) : defaultSliderX;
    double sliderY = sliderYRaw != null ? background.resolveCoordinate(sliderYRaw, h) : defaultSliderY;
    double sliderW = sliderWRaw != null ? background.resolveSize(sliderWRaw, w) : defaultSliderW;

    sliderW = background.clamp(sliderW, 12, Math.max(12, w));
    sliderX = background.clamp(sliderX, 0, Math.max(0, w - sliderW));
    sliderY = background.clamp(sliderY, 0, Math.max(0, h - 1));
    return new double[]{sliderX, sliderY, sliderW};
  }

  void drawSlider(double x, double y, double w, double value01, boolean highlight, MenuItemSpec item) {
    double fill = background.clamp01(value01);
    Double sliderTrackHeight = background.parseExtraDouble(item, "sliderTrackHeight");
    boolean showFill = background.parseItemExtraBoolean(item, "sliderShowFill", true);

    String trackAssetPath = background.firstNonBlank(
        background.extra(item, "sliderTrackAsset"),
        background.extra(item, "sliderBaseAsset")
    );
    String fillAssetPath = background.firstNonBlank(
        highlight ? background.extra(item, "sliderFillActiveAsset") : null,
        !highlight ? background.extra(item, "sliderFillInactiveAsset") : null,
        background.extra(item, "sliderFillAsset")
    );
    String knobAssetPath = background.firstNonBlank(
        highlight ? background.extra(item, "sliderKnobActiveAsset") : null,
        !highlight ? background.extra(item, "sliderKnobInactiveAsset") : null,
        background.extra(item, "sliderKnobAsset"),
        !highlight ? background.extra(item, "sliderKnobActiveAsset") : null
    );

    double[] trackDims = background.imageDimensions(trackAssetPath).orElse(null);
    double trackH = sliderTrackHeight != null
        ? Math.max(2.0, sliderTrackHeight)
        : (trackDims != null && trackDims[1] > 0 ? trackDims[1] : 8.0);

    if (trackDims != null) {
      blitter.drawImage(trackAssetPath, x, y, w, trackH);
    } else {
      blitter.setFill(1.0, 1.0, 1.0, 0.15);
      background.fillRoundRect(x, y, w, trackH, 6, 6);
    }

    if (showFill) {
      // Note: unlike the legacy JavaFX version (which clipped the fill image to the track's
      // filled fraction via beginPath/rect/clip), Blitter2D has no path-based clip primitive
      // (only setClipRect, a plain axis-aligned rect — which is exactly what's needed here since
      // the fill region is itself an axis-aligned rect). setClipRect + push/pop replaces the
      // save/beginPath/rect/clip/drawImage/restore sequence with equivalent visual behavior.
      if (background.imageDimensions(fillAssetPath).isPresent()) {
        blitter.push();
        blitter.setClipRect(x, y, w * fill, trackH);
        blitter.drawImage(fillAssetPath, x, y, w, trackH);
        blitter.pop();
      } else {
        MenuTheme.ColorSpec fillColor = highlight ? background.theme.getItemSelectedColor() : background.theme.getItemColor();
        blitter.setFill(fillColor.r(), fillColor.g(), fillColor.b(), fillColor.a());
        background.fillRoundRect(x, y, w * fill, trackH, 6, 6);
      }
    }

    double[] knobDims = background.imageDimensions(knobAssetPath).orElse(null);
    Double knobWidth = background.parseExtraDouble(item, "sliderKnobWidth");
    Double knobHeight = background.parseExtraDouble(item, "sliderKnobHeight");
    Double knobOffsetX = background.parseExtraDouble(item, "sliderKnobOffsetX");
    Double knobOffsetY = background.parseExtraDouble(item, "sliderKnobOffsetY");

    double knobW = knobWidth != null
        ? Math.max(2.0, knobWidth)
        : (knobDims != null && knobDims[0] > 0 ? knobDims[0] : 12.0);
    double knobH = knobHeight != null
        ? Math.max(2.0, knobHeight)
        : (knobDims != null && knobDims[1] > 0 ? knobDims[1] : 12.0);

    double knobX = x + w * fill - knobW * 0.5 + (knobOffsetX != null ? knobOffsetX : 0.0);
    double knobY = y + (trackH - knobH) * 0.5 + (knobOffsetY != null ? knobOffsetY : 0.0);
    if (knobDims != null) {
      blitter.drawImage(knobAssetPath, knobX, knobY, knobW, knobH);
    } else {
      // Approximates the legacy gc.fillOval knob as a filled circle; Blitter2D has no oval
      // primitive, but fillCircle covers the common (roughly square) knob case.
      blitter.setFill(1.0, 1.0, 1.0, 1.0);
      blitter.fillCircle(knobX + knobW / 2.0, knobY + knobH / 2.0, Math.min(knobW, knobH) / 2.0);
    }
  }

  void drawSettingsSliderReset(MenuItemSpec item, boolean highlight, MenuBackgroundRenderer.Rect rect) {
    if (item == null || rect == null) return;
    String resetAssetPath = background.firstNonBlank(
        highlight ? background.extra(item, "sliderResetActiveAsset") : null,
        !highlight ? background.extra(item, "sliderResetInactiveAsset") : null,
        background.extra(item, "sliderResetAsset"),
        !highlight ? background.extra(item, "sliderResetActiveAsset") : null
    );
    if (background.imageDimensions(resetAssetPath).isPresent()) {
      blitter.drawImage(resetAssetPath, rect.x(), rect.y(), rect.w(), rect.h());
      return;
    }
    MenuTheme.ColorSpec bg = highlight
        ? MenuTheme.ColorSpec.rgb255(229, 101, 94, 0.95)
        : MenuTheme.ColorSpec.rgb255(220, 212, 154, 0.95);
    blitter.setFill(bg.r(), bg.g(), bg.b(), bg.a());
    background.fillRoundRect(rect.x(), rect.y(), rect.w(), rect.h(), 4, 4);
    double fontSize = Math.max(10, Math.min(20, rect.h() * 0.58));
    blitter.setFill(18.0 / 255.0, 18.0 / 255.0, 18.0 / 255.0, 0.95);
    blitter.setFont("SansSerif", fontSize, true);
    blitter.drawText("R", rect.x() + rect.w() * 0.33, rect.y() + rect.h() * 0.72, fontSize, true);
  }

  MenuBackgroundRenderer.Rect resolveSettingsSliderResetRect(
      MenuItemSpec item,
      boolean highlight,
      double sliderX,
      double sliderY,
      double sliderW,
      double canvasW,
      double canvasH
  ) {
    if (item == null) return null;
    String resetAssetPath = background.firstNonBlank(
        highlight ? background.extra(item, "sliderResetActiveAsset") : null,
        !highlight ? background.extra(item, "sliderResetInactiveAsset") : null,
        background.extra(item, "sliderResetAsset"),
        !highlight ? background.extra(item, "sliderResetActiveAsset") : null
    );
    Double resetXRaw = background.parseExtraDouble(item, "sliderResetX");
    Double resetYRaw = background.parseExtraDouble(item, "sliderResetY");
    Double resetWidthRaw = background.parseExtraDouble(item, "sliderResetWidth");
    Double resetHeightRaw = background.parseExtraDouble(item, "sliderResetHeight");
    if ((resetAssetPath == null || resetAssetPath.isBlank())
        && resetWidthRaw == null && resetHeightRaw == null
        && resetXRaw == null && resetYRaw == null) {
      return null;
    }

    double[] resetDims = background.imageDimensions(resetAssetPath).orElse(null);
    double[] trackDims = background.imageDimensions(background.firstNonBlank(background.extra(item, "sliderTrackAsset"), background.extra(item, "sliderBaseAsset"))).orElse(null);
    Double sliderTrackHeight = background.parseExtraDouble(item, "sliderTrackHeight");
    double trackH = sliderTrackHeight != null
        ? Math.max(2.0, sliderTrackHeight)
        : (trackDims != null && trackDims[1] > 0 ? trackDims[1] : 8.0);

    double resetW = resetWidthRaw != null
        ? Math.max(2.0, background.resolveSize(resetWidthRaw, canvasW))
        : (resetDims != null && resetDims[0] > 0 ? resetDims[0] : 24.0);
    double resetH = resetHeightRaw != null
        ? Math.max(2.0, background.resolveSize(resetHeightRaw, canvasH))
        : (resetDims != null && resetDims[1] > 0 ? resetDims[1] : 24.0);

    double defaultX = sliderX - resetW - 8.0;
    double defaultY = sliderY + (trackH - resetH) * 0.5;
    double x = resetXRaw != null ? background.resolveCoordinate(resetXRaw, canvasW) : defaultX;
    double y = resetYRaw != null ? background.resolveCoordinate(resetYRaw, canvasH) : defaultY;
    x = background.clamp(x, 0, Math.max(0, canvasW - resetW));
    y = background.clamp(y, 0, Math.max(0, canvasH - resetH));
    return new MenuBackgroundRenderer.Rect(x, y, resetW, resetH);
  }

  void drawSettingsToggle(MenuItemSpec item, boolean enabled, boolean highlight, MenuBackgroundRenderer.Rect rect) {
    if (item == null || rect == null) return;
    String assetPath = background.firstNonBlank(
        enabled ? background.extra(item, "toggleCheckedAsset") : null,
        !enabled ? background.extra(item, "toggleUncheckedAsset") : null,
        enabled ? background.extra(item, "toggleUncheckedAsset") : null
    );
    if (background.imageDimensions(assetPath).isPresent()) {
      blitter.drawImage(assetPath, rect.x(), rect.y(), rect.w(), rect.h());
      return;
    }
    MenuTheme.ColorSpec bg = enabled
        ? MenuTheme.ColorSpec.rgb255(213, 108, 94, 0.95)
        : MenuTheme.ColorSpec.rgb255(232, 225, 178, 0.92);
    blitter.setFill(bg.r(), bg.g(), bg.b(), bg.a());
    blitter.fillRect(rect.x(), rect.y(), rect.w(), rect.h());
    MenuTheme.ColorSpec border = highlight
        ? MenuTheme.ColorSpec.rgb255(18, 18, 18, 0.95)
        : MenuTheme.ColorSpec.rgb255(38, 38, 38, 0.7);
    blitter.setStroke(border.r(), border.g(), border.b(), border.a());
    blitter.setStrokeWidth(highlight ? 2.2 : 1.2);
    blitter.strokeRect(rect.x(), rect.y(), rect.w(), rect.h());
    if (enabled) {
      blitter.setStroke(15.0 / 255.0, 15.0 / 255.0, 15.0 / 255.0, 0.95);
      blitter.setStrokeWidth(Math.max(2.0, rect.w() * 0.12));
      blitter.drawLine(rect.x() + rect.w() * 0.18, rect.y() + rect.h() * 0.56, rect.x() + rect.w() * 0.42, rect.y() + rect.h() * 0.82);
      blitter.drawLine(rect.x() + rect.w() * 0.42, rect.y() + rect.h() * 0.82, rect.x() + rect.w() * 0.84, rect.y() + rect.h() * 0.16);
    }
  }

  MenuBackgroundRenderer.Rect resolveSettingsToggleRect(
      MenuItemSpec item,
      int itemIndex,
      int count,
      MenuItemSpec[] itemSpecs,
      MenuLayoutSpec layout,
      double canvasW,
      double canvasH
  ) {
    if (item == null) return null;
    MenuBackgroundRenderer.Rect rowRect = background.resolveItemRect(itemIndex, count, item, itemSpecs, layout, 0, canvasW, canvasH);
    Double toggleXRaw = background.parseExtraDouble(item, "toggleX");
    Double toggleYRaw = background.parseExtraDouble(item, "toggleY");
    Double toggleWidthRaw = background.parseExtraDouble(item, "toggleWidth");
    Double toggleHeightRaw = background.parseExtraDouble(item, "toggleHeight");
    String checkedAsset = background.extra(item, "toggleCheckedAsset");
    String uncheckedAsset = background.extra(item, "toggleUncheckedAsset");
    if ((checkedAsset == null || checkedAsset.isBlank())
        && (uncheckedAsset == null || uncheckedAsset.isBlank())
        && toggleXRaw == null && toggleYRaw == null && toggleWidthRaw == null && toggleHeightRaw == null) {
      return null;
    }

    double[] toggleDims = background.imageDimensions(background.firstNonBlank(checkedAsset, uncheckedAsset)).orElse(null);
    double toggleW = toggleWidthRaw != null
        ? Math.max(2.0, background.resolveSize(toggleWidthRaw, canvasW))
        : (toggleDims != null && toggleDims[0] > 0 ? toggleDims[0] : 26.0);
    double toggleH = toggleHeightRaw != null
        ? Math.max(2.0, background.resolveSize(toggleHeightRaw, canvasH))
        : (toggleDims != null && toggleDims[1] > 0 ? toggleDims[1] : 26.0);
    double defaultX = rowRect.x() + rowRect.w() - toggleW - 8.0;
    double defaultY = rowRect.y() + Math.max(0.0, (rowRect.h() - toggleH) * 0.5);
    double x = toggleXRaw != null ? background.resolveCoordinate(toggleXRaw, canvasW) : defaultX;
    double y = toggleYRaw != null ? background.resolveCoordinate(toggleYRaw, canvasH) : defaultY;
    x = background.clamp(x, 0, Math.max(0, canvasW - toggleW));
    y = background.clamp(y, 0, Math.max(0, canvasH - toggleH));
    return new MenuBackgroundRenderer.Rect(x, y, toggleW, toggleH);
  }
}
