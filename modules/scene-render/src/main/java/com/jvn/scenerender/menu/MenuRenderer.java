package com.jvn.scenerender.menu;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;

import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.localization.Localization;
import com.jvn.core.menu.HistoryMenuScene;
import com.jvn.core.menu.LoadMenuScene;
import com.jvn.core.menu.MainMenuScene;
import com.jvn.core.menu.PauseMenuScene;
import com.jvn.core.menu.SaveMenuScene;
import com.jvn.core.menu.SettingsScene;
import com.jvn.core.menu.config.MenuItemSpec;
import com.jvn.core.menu.config.MenuLayoutSpec;
import com.jvn.core.menu.config.MenuStyleSpec;
import com.jvn.core.menu.gallery.GalleryScene;
import com.jvn.core.menu.gallery.MusicRoomScene;
import com.jvn.core.scene2d.Blitter2D;

/**
 * Facade over the menu-rendering collaborators: paints backgrounds/headers/hints
 * ({@link MenuBackgroundRenderer}), the menu-item list ({@link MenuListRenderer}), save/load slot
 * previews and page controls ({@link SaveLoadSlotRenderer}), settings sliders/toggles
 * ({@link SettingsSliderRenderer}), and the gallery/music-room screens
 * ({@link GalleryMusicRoomRenderer}). Every {@code render*}/{@code get*Hover*}/hit-test method
 * here is a thin delegation to the appropriate collaborator.
 */
public class MenuRenderer {
  private static final Logger log = LoggerFactory.getLogger(MenuRenderer.class);

  private final Blitter2D blitter;
  private MenuTheme theme;
  private final AssetCatalog assetCatalog = new AssetCatalog();

  private final MenuBackgroundRenderer background;
  private final MenuListRenderer list;
  private final SaveLoadSlotRenderer saveLoad;
  private final SettingsSliderRenderer settingsSlider;
  private final GalleryMusicRoomRenderer galleryMusicRoom;

  public enum LoadControlType {
    NONE,
    CYCLE_LEFT,
    CYCLE_RIGHT,
    TOGGLE_FAVORITES_ONLY,
    TOGGLE_SLOT_FAVORITE,
    SET_PAGE
  }

  public record LoadControlHit(LoadControlType type, int saveIndex, double pageProgress01) {
    public static LoadControlHit none() { return new LoadControlHit(LoadControlType.NONE, -1, 0.0); }
    public boolean handled() { return type != null && type != LoadControlType.NONE; }
  }

  public MenuRenderer(Blitter2D blitter) {
    this(blitter, MenuTheme.defaults());
  }

  public MenuRenderer(Blitter2D blitter, MenuTheme theme) {
    this.blitter = blitter;
    this.theme = (theme == null ? MenuTheme.defaults() : theme);
    this.background = new MenuBackgroundRenderer(blitter, assetCatalog);
    this.background.theme = this.theme;
    this.list = new MenuListRenderer(blitter, background);
    this.saveLoad = new SaveLoadSlotRenderer(blitter, background);
    this.settingsSlider = new SettingsSliderRenderer(blitter, background);
    this.galleryMusicRoom = new GalleryMusicRoomRenderer(blitter, background);
  }

  public void setTheme(MenuTheme t) {
    this.theme = (t == null ? MenuTheme.defaults() : t);
    this.background.theme = this.theme;
  }

  public MenuTheme getTheme() { return theme; }

  public void setProjectRoot(File root) {
    background.setProjectRoot(root);
  }

  public void setUiFontScale(double scale) {
    if (!Double.isFinite(scale)) scale = 1.0;
    background.activeUiFontScale = Math.max(0.75, Math.min(2.0, scale));
  }

  public void renderMainMenu(MainMenuScene scene, double w, double h) {
    MenuLayoutSpec layout = scene != null ? scene.getMenuLayout() : null;
    MenuStyleSpec screenStyle = scene != null ? scene.getDefaultMenuStyle() : null;
    String screenBg = scene != null && scene.getMenuScreen() != null ? scene.getMenuScreen().backgroundAsset() : null;
    background.drawScreenBackground(w, h, screenStyle, true, screenBg);

    // Draw logo if configured, otherwise draw text title
    if (theme.getLogoImagePath() != null) {
      background.drawLogo(theme.getLogoImagePath(), w, h);
    } else {
      String title = scene != null ? scene.getDisplayTitle() : null;
      String subtitle = scene != null ? scene.getDisplaySubtitle() : null;
      if (title == null) title = theme.getTitleText();
      if (title == null) title = Localization.t("app.title");
      double titleY = (layout != null && layout.titleY() != null)
          ? background.resolve(layout.titleY(), h)
          : background.resolve(theme.getTitleY(), h);
      background.drawHeader(title, subtitle, w, titleY, screenStyle, layout);
    }

    String[] items;
    if (scene != null && scene.getItemCount() > 0) {
      items = scene.getDisplayItems();
    } else {
      items = new String[] {
        (theme.getLabelNewGame() != null ? theme.getLabelNewGame() : Localization.t("menu.new_game")),
        (theme.getLabelLoad() != null ? theme.getLabelLoad() : Localization.t("menu.load")),
        (theme.getLabelSettings() != null ? theme.getLabelSettings() : Localization.t("menu.settings")),
        (theme.getLabelQuit() != null ? theme.getLabelQuit() : Localization.t("menu.quit"))
      };
    }

    boolean[] enabled = new boolean[items.length];
    MenuStyleSpec[] styles = new MenuStyleSpec[items.length];
    MenuItemSpec[] specs = new MenuItemSpec[items.length];
    for (int i = 0; i < items.length; i++) {
      enabled[i] = scene == null || scene.isItemEnabled(i);
      styles[i] = scene != null ? scene.getStyleForIndex(i) : null;
      specs[i] = scene != null ? scene.getMenuItemSpec(i) : null;
    }

    list.drawMenuList(items, scene != null ? scene.getSelected() : 0, enabled, styles, specs, layout, 0, w, h);

    String hints = scene != null ? scene.getDisplayHints() : null;
    if (hints == null) hints = theme.getMainHintsText();
    if (hints == null) {
      hints = Localization.t("common.select") + ": Enter    " + Localization.t("common.back") + ": Esc";
    }
    double bottomMargin = layout != null ? layout.hintsBottomMargin() : 20.0;
    background.drawHints(hints, w, h, bottomMargin, screenStyle, layout);
  }

  public void renderPauseMenu(PauseMenuScene scene, double w, double h) {
    MenuLayoutSpec layout = scene != null ? scene.getMenuLayout() : null;
    MenuStyleSpec screenStyle = scene != null ? scene.getDefaultMenuStyle() : null;
    background.drawGameplayMenuBackdrop(w, h);

    String title = scene != null ? scene.getDisplayTitle() : "Paused";
    String subtitle = scene != null ? scene.getDisplaySubtitle() : null;
    double titleY = (layout != null && layout.titleY() != null)
        ? background.resolve(layout.titleY(), h)
        : background.resolve(theme.getTitleY(), h);
    background.drawHeader(title, subtitle, w, titleY, screenStyle, layout);

    String[] items = scene != null ? scene.getDisplayItems() : new String[]{"Resume"};
    boolean[] enabled = new boolean[items.length];
    MenuStyleSpec[] styles = new MenuStyleSpec[items.length];
    MenuItemSpec[] specs = new MenuItemSpec[items.length];
    for (int i = 0; i < items.length; i++) {
      enabled[i] = scene == null || scene.isItemEnabled(i);
      styles[i] = scene != null ? scene.getStyleForIndex(i) : null;
      specs[i] = scene != null ? scene.getMenuItemSpec(i) : null;
    }

    list.drawMenuList(items, scene != null ? scene.getSelected() : 0, enabled, styles, specs, layout, 0, w, h);

    String hints = scene != null ? scene.getDisplayHints() : null;
    if (hints == null) hints = "Esc: Resume";
    double bottomMargin = layout != null ? layout.hintsBottomMargin() : 20.0;
    background.drawHints(hints, w, h, bottomMargin, screenStyle, layout);
  }

  public void renderSaveMenu(SaveMenuScene scene, double w, double h) {
    if (scene == null) return;
    MenuLayoutSpec layout = scene.getMenuLayout();
    MenuStyleSpec screenStyle = scene.getDefaultMenuStyle();
    String screenBg = scene.getMenuScreen() != null ? scene.getMenuScreen().backgroundAsset() : null;
    if (scene.getCurrentVnScene() != null) background.drawGameplayMenuBackdrop(w, h);
    else background.drawScreenBackground(w, h, screenStyle, false, screenBg);
    String title = scene.getDisplayTitle();
    String subtitle = scene.getDisplaySubtitle();
    double titleY = (layout != null && layout.titleY() != null) ? background.resolve(layout.titleY(), h) : 60.0;
    background.drawHeader(title, subtitle, w, titleY, screenStyle, layout);
    List<String> saves = scene.getSaves();
    String[] items = new String[(saves.size() + 1)];
    items[0] = scene.getNewSlotLabel();
    for (int i = 0; i < saves.size(); i++) items[i + 1] = saves.get(i);
    boolean[] enabled = new boolean[items.length];
    MenuStyleSpec[] styles = new MenuStyleSpec[items.length];
    MenuItemSpec[] specs = new MenuItemSpec[items.length];
    for (int i = 0; i < items.length; i++) {
      enabled[i] = true;
      styles[i] = scene.getStyleForIndex(i);
      specs[i] = scene.getMenuItemSpec(i);
    }
    boolean showSidePreview = saveLoad.shouldShowSaveSidePreview(scene, specs);
    double listAreaWidth = showSidePreview ? w * 0.6 : w;
    list.drawMenuList(items, scene.getSelected(), enabled, styles, specs, layout, 0, listAreaWidth, h, true);
    saveLoad.drawInlineSaveSlotPreviews(scene, layout, specs, 0, listAreaWidth, h);

    if (showSidePreview) {
      // Preview: prefer thumbnail when selecting existing; when selecting new, try current background
      if (scene.isNewItemSelected()) {
        String path = scene.getCurrentBackgroundPreviewPath();
        if (path != null) saveLoad.drawPreviewResource(path, w, h); else saveLoad.drawPreviewPlaceholder(w, h);
      } else {
        File f = new File(scene.getSaveDirectory(), scene.getSelectedName() + ".png");
        if (f.exists()) saveLoad.drawPreviewFile(f, w, h); else saveLoad.drawPreviewPlaceholder(w, h);
        saveLoad.drawPreviewMetadata(null, scene.getSelectedTimestamp(), null, w, h);
      }
      String rpg = scene.getCurrentRpgSummary();
      if (rpg != null && !rpg.isBlank()) {
        blitter.setFill(0.827, 0.827, 0.827, 1.0); // Color.LIGHTGRAY
        MenuTheme.FontSpec hintFont = theme.getHintFontSpec();
        blitter.setFont(hintFont.family(), hintFont.size(), hintFont.bold());
        blitter.drawText(rpg, 20, h - 50, hintFont.size(), hintFont.bold());
      }
    }
    String hints = scene.getDisplayHints();
    if (hints == null) {
      hints = Localization.t("common.select") + ": Enter    " + Localization.t("common.back") + ": Esc    "
          + Localization.t("save.delete") + ": Delete    " + Localization.t("save.rename") + ": R";
    }
    background.drawHints(hints, w, h, layout != null ? layout.hintsBottomMargin() : 20.0, screenStyle, layout);
  }

  public void renderLoadMenu(LoadMenuScene scene, double w, double h) {
    if (scene == null) return;
    MenuLayoutSpec layout = scene.getMenuLayout();
    MenuStyleSpec screenStyle = scene.getDefaultMenuStyle();
    String screenBg = scene.getMenuScreen() != null ? scene.getMenuScreen().backgroundAsset() : null;
    if (scene.getGameplayVnScene() != null) background.drawGameplayMenuBackdrop(w, h);
    else background.drawScreenBackground(w, h, screenStyle, false, screenBg);
    String title = scene.getDisplayTitle();
    String subtitle = scene.getDisplaySubtitle();
    double titleY = (layout != null && layout.titleY() != null) ? background.resolve(layout.titleY(), h) : 60.0;
    background.drawHeader(title, subtitle, w, titleY, screenStyle, layout);
    List<String> saves = scene.getSaves();
    int visibleStartIndex = 0;
    int visibleDrawCount = 0;
    MenuItemSpec[] visibleSpecs = null;
    double listAreaWidth = saveLoad.resolveLoadListAreaWidth(scene, null, w);
    if (saves.isEmpty()) {
      MenuItemSpec template = scene.getMenuItemSpec(0);
      int emptySlots = background.parseItemExtraInt(template, "emptySlotCount", scene.getPageSize());
      int configuredVisible = background.parseItemExtraInt(template, "visibleSlotCount", 0);
      int drawSlots = Math.max(emptySlots, configuredVisible);
      if (drawSlots > 0) {
        String[] items = new String[drawSlots];
        boolean[] enabled = new boolean[drawSlots];
        MenuStyleSpec[] styles = new MenuStyleSpec[drawSlots];
        MenuItemSpec[] specs = new MenuItemSpec[drawSlots];
        for (int i = 0; i < drawSlots; i++) {
          items[i] = "";
          enabled[i] = false;
          styles[i] = scene.getStyleForIndex(i);
          MenuItemSpec candidate = scene.getMenuItemSpec(i);
          specs[i] = candidate != null ? candidate : template;
        }
        listAreaWidth = saveLoad.resolveLoadListAreaWidth(scene, specs, w);
        list.drawMenuList(items, -1, enabled, styles, specs, layout, 0, listAreaWidth, h, true);
        visibleDrawCount = drawSlots;
        visibleSpecs = specs;
      } else {
        background.drawCenteredText(Localization.t("load.no_saves"), w, h / 2, theme.getItemFontSpec(), MenuTheme.ColorSpec.rgb255(128, 128, 128));
      }
    } else {
      String[] items = saves.toArray(new String[0]);
      int startIndex = scene.getVisibleStartIndex();
      int dataVisibleCount = scene.getVisibleCount();
      dataVisibleCount = Math.max(0, Math.min(dataVisibleCount, items.length - startIndex));
      if (dataVisibleCount <= 0) {
        background.drawCenteredText(Localization.t("load.no_saves"), w, h / 2, theme.getItemFontSpec(), MenuTheme.ColorSpec.rgb255(128, 128, 128));
      } else {
        MenuItemSpec template = scene.getMenuItemSpec(startIndex);
        int configuredVisible = background.parseItemExtraInt(template, "visibleSlotCount", 0);
        boolean fillVisibleSlots = background.parseItemExtraBoolean(template, "fillVisibleSlots", configuredVisible > 0);
        int drawSlots = fillVisibleSlots
            ? Math.max(dataVisibleCount, configuredVisible > 0 ? configuredVisible : scene.getPageSize())
            : dataVisibleCount;
        drawSlots = Math.max(dataVisibleCount, drawSlots);
        String[] visibleItems = new String[drawSlots];
        boolean[] enabled = new boolean[drawSlots];
        MenuStyleSpec[] styles = new MenuStyleSpec[drawSlots];
        MenuItemSpec[] specs = new MenuItemSpec[drawSlots];
        for (int i = 0; i < drawSlots; i++) {
          int globalIndex = startIndex + i;
          boolean hasSave = globalIndex >= 0 && globalIndex < items.length;
          visibleItems[i] = hasSave ? items[globalIndex] : "";
          enabled[i] = hasSave;
          styles[i] = scene.getStyleForIndex(globalIndex);
          MenuItemSpec candidate = scene.getMenuItemSpec(globalIndex);
          specs[i] = candidate != null ? candidate : template;
        }
        boolean showSidePreview = saveLoad.shouldShowLoadSidePreview(scene, specs);
        listAreaWidth = showSidePreview ? w * 0.6 : w;
        int selectedGlobal = scene.getSelected();
        int localSelected = (selectedGlobal >= startIndex && selectedGlobal < startIndex + drawSlots)
            ? (selectedGlobal - startIndex)
            : -1;
        list.drawMenuList(visibleItems, localSelected, enabled, styles, specs, layout, 0, listAreaWidth, h, true);
        saveLoad.drawInlineLoadSlotPreviews(scene, layout, specs, startIndex, drawSlots, 0, listAreaWidth, h);
        visibleStartIndex = startIndex;
        visibleDrawCount = drawSlots;
        visibleSpecs = specs;
        if (showSidePreview) {
          File thumb = saveLoad.getThumbnailFile(scene);
          if (thumb != null) {
            saveLoad.drawPreviewFile(thumb, w, h);
          } else {
            String previewPath = scene.getSelectedPreviewImagePath();
            if (previewPath != null) {
              saveLoad.drawPreviewResource(previewPath, w, h);
            } else {
              saveLoad.drawPreviewPlaceholder(w, h);
            }
          }
          saveLoad.drawPreviewMetadata(
            scene.getSelectedScenarioId(),
            scene.getSelectedTimestamp(),
            scene.getSelectedNodeIndex(),
            w, h
          );
          String rpg = scene.getSelectedRpgSummary();
          if (rpg != null && !rpg.isBlank()) {
            blitter.setFill(0.827, 0.827, 0.827, 1.0); // Color.LIGHTGRAY
            MenuTheme.FontSpec hintFont = theme.getHintFontSpec();
            blitter.setFont(hintFont.family(), hintFont.size(), hintFont.bold());
            blitter.drawText(rpg, 20, h - 50, hintFont.size(), hintFont.bold());
          }
        }
      }
    }
    saveLoad.drawLoadMenuControls(scene, layout, visibleSpecs, visibleStartIndex, visibleDrawCount, w, h, listAreaWidth);
    String hints = scene.getDisplayHints();
    if (hints == null) {
      hints = Localization.t("common.select") + ": Enter    " + Localization.t("common.back") + ": Esc    "
          + Localization.t("load.delete") + ": Delete    " + Localization.t("load.rename") + ": R";
    }
    background.drawHints(hints, w, h, layout != null ? layout.hintsBottomMargin() : 20.0, screenStyle, layout);
  }

  public void renderHistoryMenu(HistoryMenuScene scene, double w, double h) {
    MenuLayoutSpec layout = scene != null ? scene.getMenuLayout() : null;
    MenuStyleSpec screenStyle = scene != null ? scene.getDefaultMenuStyle() : null;
    MenuStyleSpec entryStyle = scene != null ? scene.getEntryStyle() : screenStyle;
    String screenBg = scene != null && scene.getMenuScreen() != null ? scene.getMenuScreen().backgroundAsset() : null;
    background.drawGameplayMenuBackdrop(w, h);

    String title = scene != null ? scene.getDisplayTitle() : Localization.t("history.title");
    String subtitle = scene != null ? scene.getDisplaySubtitle() : null;
    double titleY = (layout != null && layout.titleY() != null) ? background.resolve(layout.titleY(), h) : background.resolve(0.1, h);
    background.drawHeader(title, subtitle, w, titleY, screenStyle, layout);

    MenuTheme.FontSpec titleFont = background.resolveTitleFontSpec(screenStyle);
    MenuTheme.FontSpec subtitleFont = background.resolveSubtitleFontSpec(screenStyle);
    MenuTheme.FontSpec entryFont = background.resolveItemFontSpec(entryStyle, null);
    double lineHeight = layout != null && layout.lineHeight() > 0 ? layout.lineHeight() : 34.0;
    MenuBackgroundRenderer.Rect content = background.resolveHistoryContentRect(layout, w, h, titleFont, subtitleFont, subtitle);

    blitter.setFill(8.0 / 255.0, 12.0 / 255.0, 20.0 / 255.0, 0.74);
    background.fillRoundRect(content.x() - 12, content.y() - 10, content.w() + 24, content.h() + 20, 14, 14);
    blitter.setStroke(215.0 / 255.0, 225.0 / 255.0, 245.0 / 255.0, 0.18);
    blitter.setStrokeWidth(1.2);
    background.strokeRoundRect(content.x() - 12, content.y() - 10, content.w() + 24, content.h() + 20, 14, 14);

    List<com.jvn.core.vn.VnHistory.HistoryEntry> entries = scene != null ? scene.getEntries() : List.of();
    int linesPerPage = scene != null ? scene.linesPerPage(h) : Math.max(1, (int) Math.floor(content.h() / lineHeight));
    int total = entries.size();
    int maxOffset = Math.max(0, total - linesPerPage);
    int effectiveOffset = Math.min(Math.max(0, scene != null ? scene.getScrollOffset() : 0), maxOffset);
    int startIdx = Math.max(0, total - 1 - effectiveOffset);

    blitter.setFont(entryFont.family(), entryFont.size(), entryFont.bold());
    MenuTheme.ColorSpec entryColor = background.resolveItemColorSpec(entryStyle, false, true);
    MenuTheme.ColorSpec emptyColor = background.parseColorRgba(
        entryStyle != null ? entryStyle.itemDisabledColor() : null,
        MenuTheme.ColorSpec.rgb255(160, 170, 190, 0.9));
    int drawn = 0;
    for (int i = startIdx; i >= 0 && drawn < linesPerPage; i--) {
      com.jvn.core.vn.VnHistory.HistoryEntry entry = entries.get(i);
      String speakerPrefix = entry.getSpeaker() != null && !entry.getSpeaker().isBlank() ? entry.getSpeaker() + ": " : "";
      String line = speakerPrefix + entry.getText();
      String truncated = background.truncateToWidth(line, Math.max(40, content.w() - 18), entryFont);
      double rowY = content.y() + drawn * lineHeight;
      if (drawn % 2 == 0) {
        blitter.setFill(1.0, 1.0, 1.0, 0.055);
      } else {
        blitter.setFill(1.0, 1.0, 1.0, 0.03);
      }
      background.fillRoundRect(content.x() - 4, rowY + 2, content.w() + 8, Math.max(18, lineHeight - 6), 8, 8);
      String shadowRaw = entryStyle != null ? entryStyle.itemShadowColor() : null;
      if (shadowRaw != null && !shadowRaw.isBlank()) {
        MenuTheme.ColorSpec shadow = background.parseColorRgba(shadowRaw, null);
        if (shadow != null) {
          double sx = entryStyle.itemShadowOffsetX() != null ? entryStyle.itemShadowOffsetX() : 1.0;
          double sy = entryStyle.itemShadowOffsetY() != null ? entryStyle.itemShadowOffsetY() : 1.0;
          blitter.setFill(shadow.r(), shadow.g(), shadow.b(), shadow.a());
          blitter.drawText(truncated, content.x() + sx, rowY + lineHeight * 0.72 + sy, entryFont.size(), entryFont.bold());
        }
      }
      blitter.setFill(entryColor.r(), entryColor.g(), entryColor.b(), entryColor.a());
      blitter.drawText(truncated, content.x(), rowY + lineHeight * 0.72, entryFont.size(), entryFont.bold());
      drawn++;
    }

    if (total == 0) {
      blitter.setFill(emptyColor.r(), emptyColor.g(), emptyColor.b(), emptyColor.a());
      blitter.setFont(entryFont.family(), entryFont.size(), entryFont.bold());
      blitter.drawText(Localization.t("history.empty"), content.x(), content.y() + lineHeight * 0.72, entryFont.size(), entryFont.bold());
    }

    if (maxOffset > 0) {
      double trackX = content.x() + content.w() + 10;
      double trackY = content.y();
      double trackW = 6;
      double trackH = content.h();
      blitter.setFill(1.0, 1.0, 1.0, 0.12);
      background.fillRoundRect(trackX, trackY, trackW, trackH, 6, 6);
      double thumbFrac = Math.max(0.08, Math.min(1.0, (double) linesPerPage / (double) total));
      double thumbH = trackH * thumbFrac;
      double posFrac = maxOffset == 0 ? 0.0 : (double) effectiveOffset / (double) maxOffset;
      double thumbY = trackY + (trackH - thumbH) * posFrac;
      MenuTheme.ColorSpec thumbColor = background.parseColorRgba(
          entryStyle != null ? entryStyle.itemSelectedColor() : null,
          MenuTheme.ColorSpec.rgb255(150, 200, 255, 0.8));
      blitter.setFill(thumbColor.r(), thumbColor.g(), thumbColor.b(), thumbColor.a());
      background.fillRoundRect(trackX, thumbY, trackW, thumbH, 4, 4);
    }

    if (total > 0) {
      int totalPages = maxOffset == 0 ? 1 : (maxOffset / linesPerPage) + 1;
      int currentPage = maxOffset == 0 ? 1 : (effectiveOffset / linesPerPage) + 1;
      String pageText = "Page " + currentPage + " / " + totalPages;
      MenuTheme.FontSpec hintFont = background.resolveHintFontSpec(screenStyle);
      blitter.setFont(hintFont.family(), hintFont.size(), hintFont.bold());
      MenuTheme.ColorSpec hintColor = background.parseColorRgba(screenStyle != null ? screenStyle.hintsColor() : null, theme.getHintColor());
      blitter.setFill(hintColor.r(), hintColor.g(), hintColor.b(), hintColor.a());
      double pageTextW = blitter.measureTextWidth(pageText, hintFont.size(), hintFont.bold());
      blitter.drawText(pageText, content.x() + content.w() - pageTextW, content.y() - 14, hintFont.size(), hintFont.bold());
    }

    String hints = scene != null ? scene.getDisplayHints() : Localization.t("history.hint");
    background.drawHints(hints, w, h, layout != null ? layout.hintsBottomMargin() : 18.0, screenStyle, layout);
  }

  public void renderSettings(SettingsScene scene, double w, double h) {
    if (scene == null) return;
    setUiFontScale(scene.getUiFontScale());
    MenuLayoutSpec layout = scene.getMenuLayout();
    MenuStyleSpec screenStyle = scene.getDefaultMenuStyle();
    String screenBg = scene.getMenuScreen() != null ? scene.getMenuScreen().backgroundAsset() : null;
    if (scene.getGameplayVnScene() != null) background.drawGameplayMenuBackdrop(w, h);
    else background.drawScreenBackground(w, h, screenStyle, false, screenBg);
    String title = scene.getDisplayTitle();
    String subtitle = scene.getDisplaySubtitle();
    double titleY = (layout != null && layout.titleY() != null) ? background.resolve(layout.titleY(), h) : 60.0;
    background.drawHeader(title, subtitle, w, titleY, screenStyle, layout);

    String[] items = scene.getDisplayItems();
    boolean[] enabled = new boolean[items.length];
    MenuStyleSpec[] styles = new MenuStyleSpec[items.length];
    MenuItemSpec[] specs = new MenuItemSpec[items.length];
    for (int i = 0; i < items.length; i++) {
      enabled[i] = scene.isItemEnabled(i);
      styles[i] = scene.getStyleForIndex(i);
      specs[i] = scene.getMenuItemSpec(i);
    }
    list.drawMenuList(items, scene.getSelected(), enabled, styles, specs, layout, 0, w, h);

    for (int i = 0; i < items.length; i++) {
      boolean hasSlider = scene.hasSliderAt(i);
      if (!hasSlider) continue;
      double value = scene.sliderValue01At(i);
      MenuItemSpec item = specs[i];
      double[] geo = settingsSlider.sliderGeometry(i, items.length, item, specs, layout, w, h);
      settingsSlider.drawSlider(geo[0], geo[1], geo[2], value, i == scene.getSelected(), item);
      MenuBackgroundRenderer.Rect resetRect = settingsSlider.resolveSettingsSliderResetRect(item, i == scene.getSelected(), geo[0], geo[1], geo[2], w, h);
      settingsSlider.drawSettingsSliderReset(item, i == scene.getSelected(), resetRect);
    }
    for (int i = 0; i < items.length; i++) {
      if (!scene.hasToggleAt(i)) continue;
      MenuItemSpec item = specs[i];
      MenuBackgroundRenderer.Rect toggleRect = settingsSlider.resolveSettingsToggleRect(item, i, items.length, specs, layout, w, h);
      settingsSlider.drawSettingsToggle(item, scene.toggleValueAt(i), i == scene.getSelected(), toggleRect);
    }
    String hints = scene.getDisplayHints();
    if (hints == null) {
      hints = "Up/Down, Left/Right, Enter • " + Localization.t("common.back") + ": Esc";
    }
    background.drawHints(hints, w, h, layout != null ? layout.hintsBottomMargin() : 20.0, screenStyle, layout);
  }

  public void clearImageCache() {
    background.clearImageCache();
  }

  public void clearTextMeasureCache() {
    // No-op: Blitter2D's default measureTextMetrics does not maintain a separate cache.
  }

  private boolean disposed = false;

  public void dispose() {
    if (disposed) return;
    disposed = true;
    background.clearImageCache();
  }

  public int getHoverIndexForList(int count, double w, double h, double mouseX, double mouseY) {
    if (count <= 0) return -1;
    return background.hoverIndex(count, null, null, 0, w, h, mouseX, mouseY);
  }

  public int getHoverIndexForMainMenu(MainMenuScene scene, double w, double h, double mouseX, double mouseY) {
    if (scene == null) return -1;
    MenuItemSpec[] specs = new MenuItemSpec[scene.getItemCount()];
    for (int i = 0; i < specs.length; i++) specs[i] = scene.getMenuItemSpec(i);
    return background.hoverIndex(scene.getItemCount(), scene.getMenuLayout(), specs, 0, w, h, mouseX, mouseY);
  }

  public int getHoverIndexForPauseMenu(PauseMenuScene scene, double w, double h, double mouseX, double mouseY) {
    if (scene == null) return -1;
    MenuItemSpec[] specs = new MenuItemSpec[scene.getItemCount()];
    for (int i = 0; i < specs.length; i++) specs[i] = scene.getMenuItemSpec(i);
    return background.hoverIndex(scene.getItemCount(), scene.getMenuLayout(), specs, 0, w, h, mouseX, mouseY);
  }

  public int getHoverIndexForLoadMenu(LoadMenuScene scene, double w, double h, double mouseX, double mouseY) {
    if (scene == null) return -1;
    int total = scene.getItemCount();
    if (total <= 0) return -1;
    MenuLayoutSpec layout = scene.getMenuLayout();
    int startIndex = scene.getVisibleStartIndex();
    int drawCount = scene.getVisibleCount();
    drawCount = Math.max(0, Math.min(drawCount, total - startIndex));
    if (drawCount <= 0) return -1;
    MenuItemSpec[] specs = new MenuItemSpec[drawCount];
    for (int i = 0; i < drawCount; i++) specs[i] = scene.getMenuItemSpec(startIndex + i);
    double listAreaWidth = saveLoad.resolveLoadListAreaWidth(scene, specs, w);
    int local = background.hoverIndex(drawCount, layout, specs, 0, listAreaWidth, h, mouseX, mouseY);
    return local < 0 ? -1 : (startIndex + local);
  }

  public LoadControlHit getLoadControlHit(LoadMenuScene scene, double w, double h, double mouseX, double mouseY) {
    if (scene == null) return LoadControlHit.none();
    MenuItemSpec template = saveLoad.resolveLoadTemplateItem(scene, null);
    if (!saveLoad.areLoadControlsVisible(template)) return LoadControlHit.none();

    // Per-slot favorite icon hit (visible rows only).
    int total = scene.getItemCount();
    if (total > 0) {
      MenuLayoutSpec layout = scene.getMenuLayout();
      int startIndex = scene.getVisibleStartIndex();
      int drawCount = scene.getVisibleCount();
      drawCount = Math.max(0, Math.min(drawCount, total - startIndex));
      if (drawCount > 0) {
        MenuItemSpec[] specs = new MenuItemSpec[drawCount];
        for (int i = 0; i < drawCount; i++) specs[i] = scene.getMenuItemSpec(startIndex + i);
        double listAreaWidth = saveLoad.resolveLoadListAreaWidth(scene, specs, w);
        for (int i = 0; i < drawCount; i++) {
          MenuBackgroundRenderer.Rect itemRect = background.resolveItemRect(i, drawCount, specs[i], specs, layout, 0, listAreaWidth, h);
          MenuBackgroundRenderer.Rect iconRect = saveLoad.resolveLoadSlotFavoriteIconRect(template, itemRect);
          if (iconRect.contains(mouseX, mouseY)) {
            return new LoadControlHit(LoadControlType.TOGGLE_SLOT_FAVORITE, startIndex + i, 0.0);
          }
        }
      }
    }

    MenuBackgroundRenderer.Rect leftRect = saveLoad.resolveLoadCycleLeftRect(template, w, h);
    if (leftRect.contains(mouseX, mouseY)) {
      return new LoadControlHit(LoadControlType.CYCLE_LEFT, -1, 0.0);
    }
    MenuBackgroundRenderer.Rect rightRect = saveLoad.resolveLoadCycleRightRect(template, w, h);
    if (rightRect.contains(mouseX, mouseY)) {
      return new LoadControlHit(LoadControlType.CYCLE_RIGHT, -1, 0.0);
    }
    MenuBackgroundRenderer.Rect favoritesRect = saveLoad.resolveLoadFavoritesButtonRect(template, w, h);
    if (favoritesRect.contains(mouseX, mouseY)) {
      return new LoadControlHit(LoadControlType.TOGGLE_FAVORITES_ONLY, -1, 0.0);
    }
    MenuBackgroundRenderer.Rect trackRect = saveLoad.resolveLoadPageTrackRect(template, w, h);
    MenuBackgroundRenderer.Rect selectorRect = saveLoad.resolveLoadPageSelectorRect(template, trackRect, scene.getPageProgress01());
    if (trackRect.contains(mouseX, mouseY) || selectorRect.contains(mouseX, mouseY)) {
      double t = trackRect.w() <= 1 ? 0.0 : (mouseX - trackRect.x()) / trackRect.w();
      return new LoadControlHit(LoadControlType.SET_PAGE, -1, background.clamp01(t));
    }
    return LoadControlHit.none();
  }

  public int getHoverIndexForSaveMenu(SaveMenuScene scene, double w, double h, double mouseX, double mouseY) {
    if (scene == null) return -1;
    MenuItemSpec[] specs = new MenuItemSpec[scene.getItemCount()];
    for (int i = 0; i < specs.length; i++) specs[i] = scene.getMenuItemSpec(i);
    double listAreaWidth = saveLoad.shouldShowSaveSidePreview(scene, specs) ? w * 0.6 : w;
    return background.hoverIndex(scene.getItemCount(), scene.getMenuLayout(), specs, 0, listAreaWidth, h, mouseX, mouseY);
  }

  public int getHoverIndexForSettings(SettingsScene scene, double w, double h, double mouseX, double mouseY) {
    if (scene == null) return -1;
    MenuItemSpec[] specs = new MenuItemSpec[scene.itemCount()];
    for (int i = 0; i < specs.length; i++) specs[i] = scene.getMenuItemSpec(i);
    return background.hoverIndex(scene.itemCount(), scene.getMenuLayout(), specs, 0, w, h, mouseX, mouseY);
  }

  public double computeSettingsSliderValue01(SettingsScene scene, int itemIndex, double canvasW, double canvasH, double mouseX) {
    if (scene == null || itemIndex < 0) return 0;
    int count = scene.itemCount();
    if (itemIndex >= count) return 0;
    MenuItemSpec item = scene.getMenuItemSpec(itemIndex);
    MenuLayoutSpec layout = scene.getMenuLayout();
    MenuItemSpec[] itemSpecs = new MenuItemSpec[count];
    for (int i = 0; i < count; i++) itemSpecs[i] = scene.getMenuItemSpec(i);
    double[] geo = settingsSlider.sliderGeometry(itemIndex, count, item, itemSpecs, layout, canvasW, canvasH);
    double sliderX = geo[0];
    double sliderW = geo[2];
    double v = (mouseX - sliderX) / sliderW;
    if (v < 0) v = 0;
    if (v > 1) v = 1;
    return v;
  }

  public boolean isSettingsSliderResetHit(
      SettingsScene scene,
      int itemIndex,
      double canvasW,
      double canvasH,
      double mouseX,
      double mouseY
  ) {
    if (scene == null || itemIndex < 0) return false;
    int count = scene.itemCount();
    if (itemIndex >= count || !scene.hasSliderAt(itemIndex)) return false;
    MenuItemSpec item = scene.getMenuItemSpec(itemIndex);
    MenuLayoutSpec layout = scene.getMenuLayout();
    MenuItemSpec[] itemSpecs = new MenuItemSpec[count];
    for (int i = 0; i < count; i++) itemSpecs[i] = scene.getMenuItemSpec(i);
    double[] geo = settingsSlider.sliderGeometry(itemIndex, count, item, itemSpecs, layout, canvasW, canvasH);
    MenuBackgroundRenderer.Rect resetRect = settingsSlider.resolveSettingsSliderResetRect(item, true, geo[0], geo[1], geo[2], canvasW, canvasH);
    return resetRect != null && resetRect.contains(mouseX, mouseY);
  }

  public void renderGallery(GalleryScene scene, double w, double h) {
    galleryMusicRoom.renderGallery(scene, w, h);
  }

  public void renderMusicRoom(MusicRoomScene scene, double w, double h) {
    galleryMusicRoom.renderMusicRoom(scene, w, h);
  }
}
