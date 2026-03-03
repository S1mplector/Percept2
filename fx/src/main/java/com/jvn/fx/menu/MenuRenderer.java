package com.jvn.fx.menu;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.assets.AssetType;
import com.jvn.core.localization.Localization;
import com.jvn.core.menu.LoadMenuScene;
import com.jvn.core.menu.MainMenuScene;
import com.jvn.core.menu.PauseMenuScene;
import com.jvn.core.menu.SaveMenuScene;
import com.jvn.core.menu.SettingsScene;
import com.jvn.core.menu.config.MenuItemSpec;
import com.jvn.core.menu.config.MenuLayoutSpec;
import com.jvn.core.menu.config.MenuStyleSpec;
import com.jvn.core.ui.BoundsPointCodec;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

public class MenuRenderer {
  private final GraphicsContext gc;
  private MenuTheme theme;
  private final java.util.Map<String, Image> imageCache = new java.util.HashMap<>();

  public MenuRenderer(GraphicsContext gc) { this.gc = gc; this.theme = MenuTheme.defaults(); }
  public MenuRenderer(GraphicsContext gc, MenuTheme theme) { this.gc = gc; this.theme = (theme == null ? MenuTheme.defaults() : theme); }
  public void setTheme(MenuTheme t) { this.theme = (t == null ? MenuTheme.defaults() : t); }
  public MenuTheme getTheme() { return theme; }

  public void renderMainMenu(MainMenuScene scene, double w, double h) {
    MenuLayoutSpec layout = scene != null ? scene.getMenuLayout() : null;
    MenuStyleSpec screenStyle = scene != null ? scene.getDefaultMenuStyle() : null;
    drawScreenBackground(w, h, screenStyle, true);

    // Draw logo if configured, otherwise draw text title
    if (theme.getLogoImagePath() != null) {
      drawLogo(theme.getLogoImagePath(), w, h);
    } else {
      String title = scene != null ? scene.getDisplayTitle() : null;
      if (title == null || title.isBlank()) title = theme.getTitleText();
      if (title == null || title.isBlank()) title = Localization.t("app.title");
      double titleY = (layout != null && layout.titleY() != null)
          ? resolve(layout.titleY(), h)
          : resolve(theme.getTitleY(), h);
      drawTitle(title, w, titleY, screenStyle);
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

    drawMenuList(items, scene != null ? scene.getSelected() : 0, enabled, styles, specs, layout, 0, w, h);

    String hints = scene != null ? scene.getDisplayHints() : null;
    if (hints == null || hints.isBlank()) hints = theme.getMainHintsText();
    if (hints == null || hints.isBlank()) {
      hints = Localization.t("common.select") + ": Enter    " + Localization.t("common.back") + ": Esc";
    }
    double bottomMargin = layout != null ? layout.hintsBottomMargin() : 20.0;
    drawHints(hints, w, h, bottomMargin, screenStyle);
  }

  public void renderPauseMenu(PauseMenuScene scene, double w, double h) {
    MenuLayoutSpec layout = scene != null ? scene.getMenuLayout() : null;
    MenuStyleSpec screenStyle = scene != null ? scene.getDefaultMenuStyle() : null;
    // Semi-transparent dark overlay instead of full background
    gc.setFill(Color.rgb(6, 10, 20, 0.72));
    gc.fillRect(0, 0, w, h);

    String title = scene != null ? scene.getDisplayTitle() : "Paused";
    double titleY = (layout != null && layout.titleY() != null)
        ? resolve(layout.titleY(), h)
        : resolve(theme.getTitleY(), h);
    drawTitle(title, w, titleY, screenStyle);

    String[] items = scene != null ? scene.getDisplayItems() : new String[]{"Resume"};
    boolean[] enabled = new boolean[items.length];
    MenuStyleSpec[] styles = new MenuStyleSpec[items.length];
    MenuItemSpec[] specs = new MenuItemSpec[items.length];
    for (int i = 0; i < items.length; i++) {
      enabled[i] = scene == null || scene.isItemEnabled(i);
      styles[i] = scene != null ? scene.getStyleForIndex(i) : null;
      specs[i] = scene != null ? scene.getMenuItemSpec(i) : null;
    }

    drawMenuList(items, scene != null ? scene.getSelected() : 0, enabled, styles, specs, layout, 0, w, h);

    String hints = scene != null ? scene.getDisplayHints() : null;
    if (hints == null || hints.isBlank()) hints = "Esc: Resume";
    double bottomMargin = layout != null ? layout.hintsBottomMargin() : 20.0;
    drawHints(hints, w, h, bottomMargin, screenStyle);
  }

  public void renderSaveMenu(SaveMenuScene scene, double w, double h) {
    MenuLayoutSpec layout = scene != null ? scene.getMenuLayout() : null;
    MenuStyleSpec screenStyle = scene != null ? scene.getDefaultMenuStyle() : null;
    drawScreenBackground(w, h, screenStyle, false);
    String title = scene != null ? scene.getDisplayTitle() : Localization.t("save.title");
    double titleY = (layout != null && layout.titleY() != null) ? resolve(layout.titleY(), h) : 60.0;
    drawTitle(title, w, titleY, screenStyle);
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
    drawMenuList(items, scene.getSelected(), enabled, styles, specs, layout, 0, w * 0.6, h, true);
    drawInlineSaveSlotPreviews(scene, layout, specs, 0, w * 0.6, h);

    // Preview: prefer thumbnail when selecting existing; when selecting new, try current background
    if (scene.isNewItemSelected()) {
      String path = scene.getCurrentBackgroundPreviewPath();
      if (path != null) drawPreviewResource(path, w, h); else drawPreviewPlaceholder(w, h);
    } else {
      File f = new File(scene.getSaveDirectory(), scene.getSelectedName() + ".png");
      if (f.exists()) drawPreviewFile(f, w, h); else drawPreviewPlaceholder(w, h);
      drawPreviewMetadata(null, scene.getSelectedTimestamp(), null, w, h);
    }
    String rpg = scene.getCurrentRpgSummary();
    if (rpg != null && !rpg.isBlank()) {
      gc.setFill(Color.LIGHTGRAY);
      gc.setFont(theme.getHintFont());
      gc.fillText(rpg, 20, h - 50);
    }
    String hints = scene != null ? scene.getDisplayHints() : null;
    if (hints == null || hints.isBlank()) {
      hints = Localization.t("common.select") + ": Enter    " + Localization.t("common.back") + ": Esc    "
          + Localization.t("save.delete") + ": Delete    " + Localization.t("save.rename") + ": R";
    }
    drawHints(hints, w, h, layout != null ? layout.hintsBottomMargin() : 20.0, screenStyle);
  }

  public void renderLoadMenu(LoadMenuScene scene, double w, double h) {
    MenuLayoutSpec layout = scene != null ? scene.getMenuLayout() : null;
    MenuStyleSpec screenStyle = scene != null ? scene.getDefaultMenuStyle() : null;
    drawScreenBackground(w, h, screenStyle, false);
    String title = scene != null ? scene.getDisplayTitle() : Localization.t("load.title");
    double titleY = (layout != null && layout.titleY() != null) ? resolve(layout.titleY(), h) : 60.0;
    drawTitle(title, w, titleY, screenStyle);
    List<String> saves = scene.getSaves();
    if (saves.isEmpty()) {
      drawCenteredText(Localization.t("load.no_saves"), w, h/2, theme.getItemFont(), Color.GRAY);
    } else {
      String[] items = saves.toArray(new String[0]);
      boolean[] enabled = new boolean[items.length];
      MenuStyleSpec[] styles = new MenuStyleSpec[items.length];
      MenuItemSpec[] specs = new MenuItemSpec[items.length];
      for (int i = 0; i < items.length; i++) {
        enabled[i] = true;
        styles[i] = scene.getStyleForIndex(i);
        specs[i] = scene.getMenuItemSpec(i);
      }
      drawMenuList(items, scene.getSelected(), enabled, styles, specs, layout, 0, w * 0.6, h, true);
      drawInlineLoadSlotPreviews(scene, layout, specs, 0, w * 0.6, h);
      File thumb = getThumbnailFile(scene);
      if (thumb != null) {
        drawPreviewFile(thumb, w, h);
      } else {
        String previewPath = scene.getSelectedPreviewImagePath();
        if (previewPath != null) {
          drawPreviewResource(previewPath, w, h);
        } else {
          drawPreviewPlaceholder(w, h);
        }
      }
      drawPreviewMetadata(
        scene.getSelectedScenarioId(),
        scene.getSelectedTimestamp(),
        scene.getSelectedNodeIndex(),
        w, h
      );
      String rpg = scene.getSelectedRpgSummary();
      if (rpg != null && !rpg.isBlank()) {
        gc.setFill(Color.LIGHTGRAY);
        gc.setFont(theme.getHintFont());
        gc.fillText(rpg, 20, h - 50);
      }
    }
    String hints = scene != null ? scene.getDisplayHints() : null;
    if (hints == null || hints.isBlank()) {
      hints = Localization.t("common.select") + ": Enter    " + Localization.t("common.back") + ": Esc    "
          + Localization.t("load.delete") + ": Delete    " + Localization.t("load.rename") + ": R";
    }
    drawHints(hints, w, h, layout != null ? layout.hintsBottomMargin() : 20.0, screenStyle);
  }

  public void renderSettings(SettingsScene scene, double w, double h) {
    MenuLayoutSpec layout = scene != null ? scene.getMenuLayout() : null;
    MenuStyleSpec screenStyle = scene != null ? scene.getDefaultMenuStyle() : null;
    drawScreenBackground(w, h, screenStyle, false);
    String title = scene != null ? scene.getDisplayTitle() : Localization.t("settings.title");
    double titleY = (layout != null && layout.titleY() != null) ? resolve(layout.titleY(), h) : 60.0;
    drawTitle(title, w, titleY, screenStyle);

    String[] items = scene.getDisplayItems();
    boolean[] enabled = new boolean[items.length];
    MenuStyleSpec[] styles = new MenuStyleSpec[items.length];
    MenuItemSpec[] specs = new MenuItemSpec[items.length];
    for (int i = 0; i < items.length; i++) {
      enabled[i] = scene.isItemEnabled(i);
      styles[i] = scene.getStyleForIndex(i);
      specs[i] = scene.getMenuItemSpec(i);
    }
    drawMenuList(items, scene.getSelected(), enabled, styles, specs, layout, 0, w, h);

    for (int i = 0; i < items.length; i++) {
      boolean hasSlider = scene.hasSliderAt(i);
      if (!hasSlider) continue;
      double value = scene.sliderValue01At(i);
      MenuItemSpec item = specs[i];
      Rect rowRect = resolveItemRect(i, items.length, item, layout, 0, w, h);
      double padX = Math.max(20, rowRect.w() * 0.16);
      double sliderX = rowRect.x() + padX;
      double sliderW = Math.max(140, rowRect.w() - (padX * 2));
      double sliderY = rowRect.y() + (rowRect.h() - 8.0) / 2.0;
      drawSlider(sliderX, sliderY, sliderW, value, i == scene.getSelected());
    }
    String hints = scene != null ? scene.getDisplayHints() : null;
    if (hints == null || hints.isBlank()) {
      hints = "Up/Down, Left/Right, Enter • " + Localization.t("common.back") + ": Esc";
    }
    drawHints(hints, w, h, layout != null ? layout.hintsBottomMargin() : 20.0, screenStyle);
  }

  private void clear(double w, double h) {
    gc.setFill(theme.getBackgroundColor());
    gc.fillRect(0, 0, w, h);
  }

  private void drawScreenBackground(double w, double h, MenuStyleSpec style, boolean allowThemeImageFallback) {
    String styleAsset = style != null ? style.backgroundAssetPath() : null;
    if (styleAsset != null && !styleAsset.isBlank()) {
      Image styleImage = loadImage(styleAsset);
      if (styleImage != null) {
        double alpha = style != null && style.backgroundOpacity() != null ? clamp01(style.backgroundOpacity()) : 1.0;
        double prevAlpha = gc.getGlobalAlpha();
        gc.setGlobalAlpha(alpha);
        gc.drawImage(styleImage, 0, 0, w, h);
        gc.setGlobalAlpha(prevAlpha);
        return;
      }
    }

    String styleColorRaw = style != null ? style.backgroundColor() : null;
    if (styleColorRaw != null && !styleColorRaw.isBlank()) {
      Color color = parseColor(styleColorRaw, theme.getBackgroundColor());
      double opacity = style != null && style.backgroundOpacity() != null ? clamp01(style.backgroundOpacity()) : color.getOpacity();
      gc.setFill(Color.color(color.getRed(), color.getGreen(), color.getBlue(), opacity));
      gc.fillRect(0, 0, w, h);
      return;
    }

    if (allowThemeImageFallback && theme.getBackgroundImagePath() != null) {
      drawBackgroundImage(theme.getBackgroundImagePath(), w, h);
      return;
    }
    clear(w, h);
  }

  private void drawBackgroundImage(String path, double w, double h) {
    Image img = loadImage(path);
    if (img != null) {
      // Draw scaled to fill
      gc.drawImage(img, 0, 0, w, h);
    } else {
      clear(w, h);
    }
  }

  private void drawLogo(String path, double w, double h) {
    Image img = loadImage(path);
    if (img == null) return;
    
    double scale = theme.getLogoScale();
    double logoW = img.getWidth() * scale;
    double logoH = img.getHeight() * scale;
    
    // Calculate position (logoX/logoY are fractions)
    double x = w * theme.getLogoX() - logoW / 2;
    double y = h * theme.getLogoY();
    
    // Draw shadow if enabled
    if (theme.isLogoShadow()) {
      gc.setGlobalAlpha(0.4);
      gc.drawImage(img, x + 4, y + 4, logoW, logoH);
      gc.setGlobalAlpha(1.0);
    }
    
    gc.drawImage(img, x, y, logoW, logoH);
  }

  private Image loadImage(String path) {
    if (path == null || path.isBlank()) return null;
    Image cached = imageCache.get(path);
    if (cached != null) return cached;

    List<String> candidates = new ArrayList<>();
    candidates.add(path);
    candidates.addAll(buildFallbackImageCandidates(path));

    for (String candidate : candidates) {
      Image img = loadImageDirect(candidate);
      if (img != null) {
        imageCache.put(path, img);
        imageCache.put(candidate, img);
        return img;
      }
    }
    return null;
  }

  private Image loadImageDirect(String path) {
    if (path == null || path.isBlank()) return null;
    try {
      // Resolve via active asset manager first (supports --assets and project roots).
      try {
        AssetCatalog catalog = new AssetCatalog();
        var resolved = catalog.url(AssetType.IMAGE, path);
        if (resolved != null) {
          Image img = new Image(resolved.toExternalForm());
          if (!img.isError() && img.getWidth() > 0 && img.getHeight() > 0) return img;
        }
      } catch (Exception ignored) {
      }

      // Try classpath first
      var url = getClass().getClassLoader().getResource(path);
      if (url != null) {
        Image img = new Image(url.toExternalForm());
        if (!img.isError() && img.getWidth() > 0 && img.getHeight() > 0) return img;
      }
      // Then filesystem
      File f = new File(path);
      if (f.exists()) {
        Image img = new Image(f.toURI().toString());
        if (!img.isError() && img.getWidth() > 0 && img.getHeight() > 0) return img;
      }
    } catch (Exception e) {
      System.err.println("Failed to load menu image: " + path);
    }
    return null;
  }

  private List<String> buildFallbackImageCandidates(String originalPath) {
    List<String> out = new ArrayList<>();
    String lower = originalPath.toLowerCase();
    int dot = originalPath.lastIndexOf('.');
    String base = dot > 0 ? originalPath.substring(0, dot) : originalPath;
    if (dot > 0) {
      out.add(base + ".png");
      out.add(base + ".jpg");
      out.add(base + ".jpeg");
      out.add(base + ".webp");
      out.add(base + ".bmp");
      out.add(base + ".gif");
    }

    // Generic sibling fallbacks for old projects that may not have the same filename set.
    int slash = originalPath.lastIndexOf('/');
    if (slash > 0) {
      String parent = originalPath.substring(0, slash + 1);
      out.add(parent + "field.jpg");
      out.add(parent + "field.png");
    }
    return out;
  }

  public void clearImageCache() {
    imageCache.clear();
  }

  private void drawTitle(String text, double w, double y) {
    drawTitle(text, w, y, null);
  }

  private void drawTitle(String text, double w, double y, MenuStyleSpec style) {
    if (text == null || text.isBlank()) text = "JVN";
    Color titleColor = parseColor(style != null ? style.titleColor() : null, theme.getTitleColor());
    Font titleFont = resolveTitleFont(style);
    gc.setFill(titleColor);
    gc.setFont(titleFont);
    gc.fillText(text, (w - measure(text, titleFont)) / 2, y);
  }

  private void drawMenuList(String[] items, int selected, double w, double h) {
    drawMenuList(items, selected, null, null, null, null, 0, w, h, false);
  }

  private void drawMenuList(
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

  private void drawMenuList(
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
      String label = withPrefix(items[i], style, sel, isEnabled);
      Color color = resolveItemColor(style, sel, isEnabled);
      Font font = resolveItemFont(style);
      Rect rect = resolveItemRect(i, items.length, item, layout, areaX, areaWidth, h);
      boolean inlinePreviewEnabled = reserveInlineSlotPreviewSpace && isInlineSlotPreviewEnabled(item, true);
      Rect inlinePreviewRect = inlinePreviewEnabled ? resolveInlineSlotPreviewRect(item, rect) : null;
      double reservedRightSpace = inlinePreviewRect != null
          ? Math.max(0, rect.x() + rect.w() - inlinePreviewRect.x() + 8)
          : 0;

      String backgroundAsset = resolveButtonAssetPath(item, style, sel, isEnabled);
      Image buttonBg = loadImage(backgroundAsset);
      if (buttonBg != null) {
        gc.drawImage(buttonBg, rect.x(), rect.y(), rect.w(), rect.h());
      } else {
        Color bgFill = !isEnabled
            ? Color.rgb(80, 80, 90, 0.45)
            : (sel ? Color.rgb(90, 120, 180, 0.5) : Color.rgb(32, 36, 46, 0.55));
        gc.setFill(bgFill);
        gc.fillRoundRect(rect.x(), rect.y(), rect.w(), rect.h(), 10, 10);
        gc.setStroke(sel ? Color.rgb(170, 210, 255, 0.9) : Color.rgb(110, 130, 160, 0.55));
        gc.setLineWidth(sel ? 2.0 : 1.1);
        gc.strokeRoundRect(rect.x(), rect.y(), rect.w(), rect.h(), 10, 10);
      }

      Image iconImage = item != null ? loadImage(item.iconPath()) : null;
      double iconSize = 0;
      if (iconImage != null) {
        iconSize = clamp(rect.h() * 0.56, 12, 36);
        double iconX = rect.x() + 10;
        double iconY = rect.y() + (rect.h() - iconSize) / 2.0;
        double previousAlpha = gc.getGlobalAlpha();
        gc.setGlobalAlpha(isEnabled ? 0.98 : 0.55);
        gc.drawImage(iconImage, iconX, iconY, iconSize, iconSize);
        gc.setGlobalAlpha(previousAlpha);
      }

      gc.setFill(color);
      gc.setFont(font);
      double tw = measure(label, font);
      double textPadX = style != null && style.buttonTextPaddingX() != null ? style.buttonTextPaddingX() : textPadXDefault;
      double textPadY = style != null && style.buttonTextPaddingY() != null ? style.buttonTextPaddingY() : textPadYDefault;
      double leftInset = rect.x() + Math.max(0, textPadX) + (iconSize > 0 ? iconSize + 8 : 0);
      double rightInset = rect.x() + Math.max(0, rect.w() - textPadX - reservedRightSpace);
      double x = switch (align == null ? "center" : align.toLowerCase()) {
        case "left" -> leftInset;
        case "right" -> rightInset - tw;
        default -> leftInset + Math.max(0, (rightInset - leftInset - tw) / 2.0);
      };
      double baseline = rect.y() + rect.h() * 0.55 + textPadY;
      gc.fillText(label, x, baseline);
    }
  }

  private void drawHints(String text, double w, double h) {
    drawHints(text, w, h, 20.0);
  }

  private void drawHints(String text, double w, double h, double bottomMargin) {
    drawHints(text, w, h, bottomMargin, null);
  }

  private void drawHints(String text, double w, double h, double bottomMargin, MenuStyleSpec style) {
    Font hintFont = resolveHintFont(style);
    Color hintColor = parseColor(style != null ? style.hintsColor() : null, theme.getHintColor());
    gc.setFill(hintColor);
    gc.setFont(hintFont);
    gc.fillText(text, (w - measure(text, hintFont)) / 2, h - Math.max(0, bottomMargin));
  }

  private void drawCenteredText(String text, double w, double y, Font font, Color color) {
    gc.setFill(color);
    gc.setFont(font);
    gc.fillText(text, (w - measure(text, font)) / 2, y);
  }

  private double measure(String s, Font f) {
    javafx.scene.text.Text t = new javafx.scene.text.Text(s);
    t.setFont(f);
    return t.getLayoutBounds().getWidth();
  }

  public int getHoverIndexForList(int count, double w, double h, double mouseX, double mouseY) {
    if (count <= 0) return -1;
    return hoverIndex(count, null, null, 0, w, h, mouseX, mouseY);
  }

  public int getHoverIndexForMainMenu(MainMenuScene scene, double w, double h, double mouseX, double mouseY) {
    if (scene == null) return -1;
    MenuItemSpec[] specs = new MenuItemSpec[scene.getItemCount()];
    for (int i = 0; i < specs.length; i++) specs[i] = scene.getMenuItemSpec(i);
    return hoverIndex(scene.getItemCount(), scene.getMenuLayout(), specs, 0, w, h, mouseX, mouseY);
  }

  public int getHoverIndexForPauseMenu(PauseMenuScene scene, double w, double h, double mouseX, double mouseY) {
    if (scene == null) return -1;
    MenuItemSpec[] specs = new MenuItemSpec[scene.getItemCount()];
    for (int i = 0; i < specs.length; i++) specs[i] = scene.getMenuItemSpec(i);
    return hoverIndex(scene.getItemCount(), scene.getMenuLayout(), specs, 0, w, h, mouseX, mouseY);
  }

  public int getHoverIndexForLoadMenu(LoadMenuScene scene, double w, double h, double mouseX, double mouseY) {
    if (scene == null) return -1;
    MenuItemSpec[] specs = new MenuItemSpec[scene.getItemCount()];
    for (int i = 0; i < specs.length; i++) specs[i] = scene.getMenuItemSpec(i);
    return hoverIndex(scene.getItemCount(), scene.getMenuLayout(), specs, 0, w * 0.6, h, mouseX, mouseY);
  }

  public int getHoverIndexForSaveMenu(SaveMenuScene scene, double w, double h, double mouseX, double mouseY) {
    if (scene == null) return -1;
    MenuItemSpec[] specs = new MenuItemSpec[scene.getItemCount()];
    for (int i = 0; i < specs.length; i++) specs[i] = scene.getMenuItemSpec(i);
    return hoverIndex(scene.getItemCount(), scene.getMenuLayout(), specs, 0, w * 0.6, h, mouseX, mouseY);
  }

  public int getHoverIndexForSettings(SettingsScene scene, double w, double h, double mouseX, double mouseY) {
    if (scene == null) return -1;
    MenuItemSpec[] specs = new MenuItemSpec[scene.itemCount()];
    for (int i = 0; i < specs.length; i++) specs[i] = scene.getMenuItemSpec(i);
    return hoverIndex(scene.itemCount(), scene.getMenuLayout(), specs, 0, w, h, mouseX, mouseY);
  }

  private int hoverIndex(
      int count,
      MenuLayoutSpec layout,
      MenuItemSpec[] itemSpecs,
      double areaX,
      double areaWidth,
      double h,
      double mouseX,
      double mouseY
  ) {
    if (count <= 0) return -1;
    for (int i = 0; i < count; i++) {
      MenuItemSpec item = itemSpecs != null && i < itemSpecs.length ? itemSpecs[i] : null;
      Rect rect = resolveItemRect(i, count, item, layout, areaX, areaWidth, h);
      if (itemContainsPoint(item, rect, mouseX, mouseY)) return i;
    }
    return -1;
  }

  private boolean itemContainsPoint(MenuItemSpec itemSpec, Rect rect, double mouseX, double mouseY) {
    if (rect == null) return false;
    if (itemSpec != null && itemSpec.extras() != null) {
      String raw = itemSpec.extras().get("boundsPoints");
      if (raw != null && !raw.isBlank()) {
        List<BoundsPointCodec.Point> points = BoundsPointCodec.parse(raw);
        if (points.size() >= 3) {
          if (BoundsPointCodec.containsInRect(points, rect.x(), rect.y(), rect.w(), rect.h(), mouseX, mouseY)) {
            return true;
          }
          return false;
        }
      }
    }
    return rect.contains(mouseX, mouseY);
  }

  private Rect resolveItemRect(
      int index,
      int count,
      MenuItemSpec itemSpec,
      MenuLayoutSpec layout,
      double areaX,
      double areaWidth,
      double h
  ) {
    double yStart = layout != null ? resolve(layout.listYStart(), h) : resolve(theme.getListYStart(), h);
    double lineH = layout != null ? layout.lineHeight() : theme.getLineHeight();
    if (lineH <= 0) lineH = theme.getLineHeight();
    double widthFactor = layout != null ? clamp(layout.listWidthFactor(), 0.1, 1.0) : 1.0;
    double listW = areaWidth * widthFactor;
    String align = layout != null ? layout.textAlign() : "center";
    double listX = switch (align == null ? "center" : align.toLowerCase()) {
      case "left" -> areaX;
      case "right" -> areaX + areaWidth - listW;
      default -> areaX + (areaWidth - listW) / 2.0;
    };

    if (itemSpec != null && itemSpec.boundsX() != null && itemSpec.boundsY() != null
        && itemSpec.boundsWidth() != null && itemSpec.boundsHeight() != null) {
      double x = resolveCoordinate(itemSpec.boundsX(), areaWidth) + areaX;
      double y = resolveCoordinate(itemSpec.boundsY(), h);
      double w = resolveSize(itemSpec.boundsWidth(), areaWidth);
      double hh = resolveSize(itemSpec.boundsHeight(), h);
      w = clamp(w, 8, Math.max(8, areaWidth));
      hh = clamp(hh, 8, Math.max(8, h));
      x = clamp(x, areaX, areaX + Math.max(0, areaWidth - w));
      y = clamp(y, 0, Math.max(0, h - hh));
      return new Rect(x, y, w, hh);
    }

    double baseline = yStart + index * lineH;
    double itemH = Math.max(24, lineH * 0.92);
    double itemY = baseline - itemH * 0.76;
    return new Rect(listX, itemY, Math.max(1, listW), itemH);
  }

  private boolean isInlineSlotPreviewEnabled(MenuItemSpec itemSpec, boolean defaultIfMissingSpec) {
    if (itemSpec == null) return defaultIfMissingSpec;
    return itemSpec.slotPreviewEnabled();
  }

  private Rect resolveInlineSlotPreviewRect(MenuItemSpec itemSpec, Rect itemRect) {
    if (itemRect == null) return new Rect(0, 0, 1, 1);
    if (itemSpec != null
        && itemSpec.slotPreviewX() != null
        && itemSpec.slotPreviewY() != null
        && itemSpec.slotPreviewWidth() != null
        && itemSpec.slotPreviewHeight() != null) {
      double x = itemRect.x() + resolveCoordinate(itemSpec.slotPreviewX(), itemRect.w());
      double y = itemRect.y() + resolveCoordinate(itemSpec.slotPreviewY(), itemRect.h());
      double w = resolveSize(itemSpec.slotPreviewWidth(), itemRect.w());
      double h = resolveSize(itemSpec.slotPreviewHeight(), itemRect.h());
      w = clamp(w, 8, Math.max(8, itemRect.w()));
      h = clamp(h, 8, Math.max(8, itemRect.h()));
      x = clamp(x, itemRect.x(), itemRect.x() + Math.max(0, itemRect.w() - w));
      y = clamp(y, itemRect.y(), itemRect.y() + Math.max(0, itemRect.h() - h));
      return new Rect(x, y, w, h);
    }

    double margin = 6;
    double h = clamp(itemRect.h() - margin * 2, 14, Math.max(14, itemRect.h() - margin * 2));
    double w = clamp(Math.min(itemRect.w() * 0.34, h * 1.6), 24, Math.max(24, itemRect.w() - margin * 2));
    double x = itemRect.x() + itemRect.w() - w - margin;
    double y = itemRect.y() + (itemRect.h() - h) / 2.0;
    return new Rect(x, y, w, h);
  }

  private String resolveButtonAssetPath(MenuItemSpec item, MenuStyleSpec style, boolean selected, boolean enabled) {
    String path = null;
    if (!enabled) {
      path = firstNonBlank(
          item != null ? item.buttonDisabledAssetPath() : null,
          item != null ? item.buttonAssetPath() : null,
          style != null ? style.buttonDisabledAssetPath() : null,
          style != null ? style.buttonAssetPath() : null
      );
    } else if (selected) {
      path = firstNonBlank(
          item != null ? item.buttonSelectedAssetPath() : null,
          item != null ? item.buttonAssetPath() : null,
          style != null ? style.buttonSelectedAssetPath() : null,
          style != null ? style.buttonAssetPath() : null
      );
    } else {
      path = firstNonBlank(
          item != null ? item.buttonAssetPath() : null,
          style != null ? style.buttonAssetPath() : null
      );
    }
    return path;
  }

  private double resolveCoordinate(double value, double total) {
    return value <= 1.0 ? total * value : value;
  }

  private double resolveSize(double value, double total) {
    return value <= 1.0 ? total * Math.max(0, value) : value;
  }

  private File getThumbnailFile(LoadMenuScene scene) {
    String dir = scene.getSaveDirectory();
    String name = scene.getSelectedName();
    if (dir == null || name == null) return null;
    File f = new File(dir, name + ".png");
    return f.exists() ? f : null;
  }

  private void drawInlineSaveSlotPreviews(
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
      Rect itemRect = resolveItemRect(i, count, spec, layout, areaX, areaWidth, h);
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

  private void drawInlineLoadSlotPreviews(
      LoadMenuScene scene,
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
      Rect itemRect = resolveItemRect(i, count, spec, layout, areaX, areaWidth, h);
      String previewPath = null;
      if (i >= 0 && i < saves.size()) {
        File thumb = new File(scene.getSaveDirectory(), saves.get(i) + ".png");
        if (thumb.exists()) previewPath = thumb.getAbsolutePath();
      }
      drawInlineSlotPreview(itemRect, spec, previewPath, i == scene.getSelected(), Localization.t("load.no_preview"));
    }
  }

  private void drawInlineSlotPreview(Rect itemRect, MenuItemSpec itemSpec, String previewPath, boolean selected, String fallbackText) {
    if (itemRect == null) return;
    Rect previewRect = resolveInlineSlotPreviewRect(itemSpec, itemRect);
    if (previewRect.w() <= 1 || previewRect.h() <= 1) return;

    gc.setFill(Color.rgb(6, 9, 14, 0.95));
    gc.fillRoundRect(previewRect.x(), previewRect.y(), previewRect.w(), previewRect.h(), 7, 7);

    Image previewImage = loadImage(previewPath);
    if (previewImage != null && !previewImage.isError()) {
      drawImageCover(previewImage, previewRect);
    } else {
      Image placeholder = itemSpec != null ? loadImage(itemSpec.slotPreviewPlaceholderAssetPath()) : null;
      if (placeholder != null && !placeholder.isError()) {
        drawImageCover(placeholder, previewRect);
      } else {
        gc.setFill(Color.rgb(32, 36, 48, 0.95));
        gc.fillRoundRect(previewRect.x(), previewRect.y(), previewRect.w(), previewRect.h(), 7, 7);
        gc.setFill(Color.rgb(205, 212, 225, 0.85));
        gc.setFont(theme.getHintFont());
        String txt = fallbackText == null || fallbackText.isBlank() ? Localization.t("load.no_preview") : fallbackText;
        double tw = measure(txt, theme.getHintFont());
        double tx = previewRect.x() + Math.max(6, (previewRect.w() - tw) / 2.0);
        double ty = previewRect.y() + previewRect.h() * 0.56;
        gc.fillText(txt, tx, ty);
      }
    }

    Image frame = itemSpec != null ? loadImage(itemSpec.slotPreviewFrameAssetPath()) : null;
    if (frame != null && !frame.isError()) {
      gc.drawImage(frame, previewRect.x(), previewRect.y(), previewRect.w(), previewRect.h());
    } else {
      gc.setStroke(selected ? Color.rgb(170, 220, 255, 0.95) : Color.rgb(150, 170, 205, 0.7));
      gc.setLineWidth(selected ? 1.8 : 1.1);
      gc.strokeRoundRect(previewRect.x(), previewRect.y(), previewRect.w(), previewRect.h(), 7, 7);
    }
  }

  private void drawImageCover(Image image, Rect target) {
    if (image == null || target == null) return;
    double iw = image.getWidth();
    double ih = image.getHeight();
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
    gc.drawImage(image, sx, sy, sw, sh, target.x(), target.y(), target.w(), target.h());
  }

  private void drawPreviewResource(String path, double w, double h) {
    try {
      var url = getClass().getClassLoader().getResource(path);
      if (url == null) { drawPreviewPlaceholder(w, h); return; }
      Image img = new Image(url.toExternalForm());
      drawPreviewImage(img, w, h);
    } catch (Exception e) {
      drawPreviewPlaceholder(w, h);
    }
  }

  private void drawPreviewFile(File file, double w, double h) {
    try {
      Image img = new Image(file.toURI().toString());
      drawPreviewImage(img, w, h);
    } catch (Exception e) {
      drawPreviewPlaceholder(w, h);
    }
  }

  private void drawPreviewImage(Image img, double w, double h) {
    double panelX = w * 0.65;
    double panelY = h * 0.25;
    double panelW = w * 0.3;
    double panelH = h * 0.5;
    gc.setFill(Color.rgb(255,255,255,0.1));
    gc.fillRoundRect(panelX - 8, panelY - 8, panelW + 16, panelH + 16, 12, 12);
    double scale = Math.min(panelW / img.getWidth(), panelH / img.getHeight());
    double iw = img.getWidth() * scale;
    double ih = img.getHeight() * scale;
    double ix = panelX + (panelW - iw) / 2;
    double iy = panelY + (panelH - ih) / 2;
    gc.drawImage(img, ix, iy, iw, ih);
  }

  private void drawPreviewPlaceholder(double w, double h) {
    double panelX = w * 0.65;
    double panelY = h * 0.25;
    double panelW = w * 0.3;
    double panelH = h * 0.5;
    gc.setFill(Color.rgb(255,255,255,0.1));
    gc.fillRoundRect(panelX - 8, panelY - 8, panelW + 16, panelH + 16, 12, 12);
    gc.setFill(Color.GRAY);
    gc.setFont(theme.getItemFont());
    drawCenteredText(Localization.t("load.no_preview"), panelX + panelW/2, panelY + panelH/2, theme.getItemFont(), Color.GRAY);
  }

  private void drawPreviewMetadata(String scenarioId, Long timestampMs, Integer nodeIndex, double w, double h) {
    double panelX = w * 0.65;
    double panelY = h * 0.25;
    double panelH = h * 0.5;
    double textY = panelY + panelH + 20;
    gc.setFill(Color.LIGHTGRAY);
    gc.setFont(theme.getHintFont());
    String ts = timestampMs != null ? formatTimestamp(timestampMs) : "";
    String line1 = (ts.isEmpty() ? "" : ts);
    String line2 = (scenarioId != null ? (Localization.t("meta.scenario") + ": " + scenarioId) : "");
    String line3 = (nodeIndex != null ? (Localization.t("meta.node") + ": " + nodeIndex) : "");
    double x = panelX;
    if (!line1.isEmpty()) gc.fillText(line1, x, textY);
    if (!line2.isEmpty()) gc.fillText(line2, x, textY + 18);
    if (!line3.isEmpty()) gc.fillText(line3, x, textY + 36);
  }

  private String formatTimestamp(long millis) {
    try {
      java.time.Instant inst = java.time.Instant.ofEpochMilli(millis);
      java.time.ZonedDateTime z = java.time.ZonedDateTime.ofInstant(inst, java.time.ZoneId.systemDefault());
      return z.toLocalDate().toString() + " " + z.toLocalTime().withNano(0).toString();
    } catch (Exception e) { return Long.toString(millis); }
  }

  private void drawSlider(double x, double y, double w, double value01, boolean highlight) {
    double h = 8;
    gc.setFill(Color.rgb(255,255,255,0.15));
    gc.fillRoundRect(x, y, w, h, 6, 6);
    gc.setFill(highlight ? theme.getItemSelectedColor() : theme.getItemColor());
    double fill = Math.max(0, Math.min(1, value01));
    gc.fillRoundRect(x, y, w * fill, h, 6, 6);
    double knobX = x + w * fill - 6;
    gc.setFill(Color.WHITE);
    gc.fillOval(knobX, y - 4, 12, 12);
  }

  private double clamp01(double v) {
    return v < 0 ? 0 : (v > 1 ? 1 : v);
  }

  private double clamp(double v, double min, double max) {
    if (Double.isNaN(v) || Double.isInfinite(v)) return min;
    if (v < min) return min;
    if (v > max) return max;
    return v;
  }

  private String withPrefix(String label, MenuStyleSpec style, boolean selected, boolean enabled) {
    String base = label == null ? "" : label;
    String prefix;
    if (!enabled) {
      prefix = firstNonBlank(style != null ? style.itemDisabledPrefix() : null,
          style != null ? style.itemPrefix() : null,
          theme.getItemPrefix());
    } else if (selected) {
      prefix = firstNonBlank(style != null ? style.itemSelectedPrefix() : null, theme.getItemSelectedPrefix());
    } else {
      prefix = firstNonBlank(style != null ? style.itemPrefix() : null, theme.getItemPrefix());
    }
    return (prefix == null ? "" : prefix) + base;
  }

  private Color resolveItemColor(MenuStyleSpec style, boolean selected, boolean enabled) {
    if (!enabled) {
      return parseColor(
          style != null ? style.itemDisabledColor() : null,
          Color.rgb(160, 160, 160, 0.8)
      );
    }
    if (selected) {
      return parseColor(style != null ? style.itemSelectedColor() : null, theme.getItemSelectedColor());
    }
    return parseColor(style != null ? style.itemColor() : null, theme.getItemColor());
  }

  private Font resolveItemFont(MenuStyleSpec style) {
    if (style == null) return theme.getItemFont();
    String family = firstNonBlank(style.itemFontFamily(), theme.getItemFont().getFamily());
    double size = style.itemFontSize() != null ? style.itemFontSize() : theme.getItemFont().getSize();
    String weightRaw = style.itemFontWeight();
    if (weightRaw == null || weightRaw.isBlank()) {
      return Font.font(family, size);
    }
    FontWeight weight = parseFontWeight(weightRaw, FontWeight.NORMAL);
    return Font.font(family, weight, size);
  }

  private Font resolveTitleFont(MenuStyleSpec style) {
    if (style == null) return theme.getTitleFont();
    String family = firstNonBlank(style.titleFontFamily(), theme.getTitleFont().getFamily());
    double size = style.titleFontSize() != null ? style.titleFontSize() : theme.getTitleFont().getSize();
    FontWeight weight = parseFontWeight(style.titleFontWeight(), FontWeight.BOLD);
    return Font.font(family, weight, size);
  }

  private Font resolveHintFont(MenuStyleSpec style) {
    if (style == null) return theme.getHintFont();
    String family = firstNonBlank(style.hintsFontFamily(), theme.getHintFont().getFamily());
    double size = style.hintsFontSize() != null ? style.hintsFontSize() : theme.getHintFont().getSize();
    FontWeight weight = parseFontWeight(style.hintsFontWeight(), FontWeight.NORMAL);
    return Font.font(family, weight, size);
  }

  private FontWeight parseFontWeight(String raw, FontWeight def) {
    if (raw == null || raw.isBlank()) return def;
    try {
      return FontWeight.valueOf(raw.trim().toUpperCase());
    } catch (Exception ignored) {
      return def;
    }
  }

  private Color parseColor(String raw, Color def) {
    if (raw == null || raw.isBlank()) return def;
    String t = raw.trim();
    try {
      if (t.startsWith("#")) {
        String hex = t.substring(1);
        if (hex.length() == 6) {
          int r = Integer.parseInt(hex.substring(0, 2), 16);
          int g = Integer.parseInt(hex.substring(2, 4), 16);
          int b = Integer.parseInt(hex.substring(4, 6), 16);
          return Color.rgb(r, g, b);
        }
        if (hex.length() == 8) {
          int a = Integer.parseInt(hex.substring(0, 2), 16);
          int r = Integer.parseInt(hex.substring(2, 4), 16);
          int g = Integer.parseInt(hex.substring(4, 6), 16);
          int b = Integer.parseInt(hex.substring(6, 8), 16);
          return Color.rgb(r, g, b, a / 255.0);
        }
      } else if (t.toLowerCase().startsWith("rgb")) {
        int lp = t.indexOf('(');
        int rp = t.indexOf(')');
        if (lp >= 0 && rp > lp) {
          String[] parts = t.substring(lp + 1, rp).split(",");
          double r = Double.parseDouble(parts[0].trim());
          double g = Double.parseDouble(parts[1].trim());
          double b = Double.parseDouble(parts[2].trim());
          double a = parts.length >= 4 ? Double.parseDouble(parts[3].trim()) : 1.0;
          if (r > 1 || g > 1 || b > 1 || a > 1) {
            return Color.rgb((int) r, (int) g, (int) b, a > 1 ? (a / 255.0) : a);
          }
          return Color.color(r, g, b, a);
        }
      }
    } catch (Exception ignored) {
    }
    return def;
  }

  private String firstNonBlank(String... values) {
    if (values == null) return null;
    for (String v : values) {
      if (v != null && !v.isBlank()) return v;
    }
    return null;
  }

  private double resolve(double v, double total) {
    // if v <= 1, treat as fraction of total; otherwise pixels
    return v <= 1.0 ? (total * v) : v;
  }

  private record Rect(double x, double y, double w, double h) {
    boolean contains(double px, double py) {
      return px >= x && px <= x + w && py >= y && py <= y + h;
    }
  }
}
