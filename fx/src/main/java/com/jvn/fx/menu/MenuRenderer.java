package com.jvn.fx.menu;

import com.jvn.core.localization.Localization;
import com.jvn.core.menu.LoadMenuScene;
import com.jvn.core.menu.MainMenuScene;
import com.jvn.core.menu.SaveMenuScene;
import com.jvn.core.menu.SettingsScene;
import com.jvn.core.vn.VnSettings;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.image.Image;
import javafx.scene.text.Font;

import java.util.List;
import java.io.File;

public class MenuRenderer {
  private final GraphicsContext gc;
  private MenuTheme theme;
  private final java.util.Map<String, Image> imageCache = new java.util.HashMap<>();

  public MenuRenderer(GraphicsContext gc) { this.gc = gc; this.theme = MenuTheme.defaults(); }
  public MenuRenderer(GraphicsContext gc, MenuTheme theme) { this.gc = gc; this.theme = (theme == null ? MenuTheme.defaults() : theme); }
  public void setTheme(MenuTheme t) { this.theme = (t == null ? MenuTheme.defaults() : t); }
  public MenuTheme getTheme() { return theme; }

  public void renderMainMenu(MainMenuScene scene, double w, double h) {
    // Draw background image if configured
    if (theme.getBackgroundImagePath() != null) {
      drawBackgroundImage(theme.getBackgroundImagePath(), w, h);
    } else {
      clear(w, h);
    }

    // Draw logo if configured, otherwise draw text title
    if (theme.getLogoImagePath() != null) {
      drawLogo(theme.getLogoImagePath(), w, h);
    } else {
      String title = theme.getTitleText();
      if (title == null || title.isBlank()) title = Localization.t("app.title");
      double titleY = resolve(theme.getTitleY(), h);
      drawTitle(title, w, titleY);
    }

    String[] items = new String[] {
      (theme.getLabelNewGame() != null ? theme.getLabelNewGame() : Localization.t("menu.new_game")),
      (theme.getLabelLoad() != null ? theme.getLabelLoad() : Localization.t("menu.load")),
      (theme.getLabelSettings() != null ? theme.getLabelSettings() : Localization.t("menu.settings")),
      (theme.getLabelQuit() != null ? theme.getLabelQuit() : Localization.t("menu.quit"))
    };

    drawMenuList(items, scene.getSelected(), w, h);
    String hints = theme.getMainHintsText();
    if (hints == null || hints.isBlank()) {
      hints = Localization.t("common.select") + ": Enter    " + Localization.t("common.back") + ": Esc";
    }
    drawHints(hints, w, h);
  }

  public void renderSaveMenu(SaveMenuScene scene, double w, double h) {
    clear(w, h);
    drawTitle(Localization.t("save.title"), w, 60);
    List<String> saves = scene.getSaves();
    String[] items = new String[(saves.size() + 1)];
    items[0] = Localization.t("save.new");
    for (int i = 0; i < saves.size(); i++) items[i + 1] = saves.get(i);
    drawMenuList(items, scene.getSelected(), w * 0.6, h);

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
    drawHints(Localization.t("common.select") + ": Enter    " + Localization.t("common.back") + ": Esc    "
        + Localization.t("save.delete") + ": Delete    " + Localization.t("save.rename") + ": R",
        w, h);
  }

  public void renderLoadMenu(LoadMenuScene scene, double w, double h) {
    clear(w, h);
    drawTitle(Localization.t("load.title"), w, 60);
    List<String> saves = scene.getSaves();
    if (saves.isEmpty()) {
      drawCenteredText(Localization.t("load.no_saves"), w, h/2, theme.getItemFont(), Color.GRAY);
    } else {
      drawMenuList(saves.toArray(new String[0]), scene.getSelected(), w * 0.6, h);
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
    drawHints(Localization.t("common.select") + ": Enter    " + Localization.t("common.back") + ": Esc    "
        + Localization.t("load.delete") + ": Delete    " + Localization.t("load.rename") + ": R",
        w, h);
  }

  public void renderSettings(SettingsScene scene, double w, double h) {
    clear(w, h);
    drawTitle(Localization.t("settings.title"), w, 60);

    VnSettings s = scene.model();
    String[] items = new String[] {
      Localization.t("settings.text_speed") + ": " + s.getTextSpeed() + " ms",
      Localization.t("settings.bgm_volume") + ": " + toPct(s.getBgmVolume()),
      Localization.t("settings.sfx_volume") + ": " + toPct(s.getSfxVolume()),
      Localization.t("settings.voice_volume") + ": " + toPct(s.getVoiceVolume()),
      Localization.t("settings.auto_play_delay") + ": " + s.getAutoPlayDelay() + " ms",
      Localization.t("settings.skip_unread") + ": " + (s.isSkipUnreadText() ? "ON" : "OFF"),
      Localization.t("settings.skip_after_choices") + ": " + (s.isSkipAfterChoices() ? "ON" : "OFF"),
      "Physics: Fixed Step " + s.getPhysicsFixedStepMs() + " ms",
      "Physics: Max Substeps " + s.getPhysicsMaxSubSteps(),
      "Physics: Default Friction " + toPct((float) s.getPhysicsDefaultFriction()),
      "Input: Save/Load (" + s.getInputProfilePath() + ")" + (scene.getBindingStatus().isEmpty() ? "" : " • " + scene.getBindingStatus())
    };

    drawMenuList(items, scene.getSelected(), w, h);
    double yStart = h * 0.35;
    double lineH = 40;
    double sliderW = w * 0.45;
    double sliderX = (w - sliderW) / 2;

    double textSpeedMin = 10.0, textSpeedMax = 120.0;
    double autoDelayMin = 500.0, autoDelayMax = 5000.0;

    int sliderRow = 0;
    for (int i = 0; i < items.length; i++) {
      double value;
      boolean hasSlider = switch (i) {
        case 0 -> true;
        case 1 -> true;
        case 2 -> true;
        case 3 -> true;
        case 4 -> true;
        case 7 -> true;
        case 8 -> true;
        case 9 -> true;
        default -> false;
      };
      if (!hasSlider) continue;
      value = switch (i) {
        case 0 -> clamp01((s.getTextSpeed() - textSpeedMin) / (textSpeedMax - textSpeedMin));
        case 1 -> clamp01(s.getBgmVolume());
        case 2 -> clamp01(s.getSfxVolume());
        case 3 -> clamp01(s.getVoiceVolume());
        case 4 -> clamp01((s.getAutoPlayDelay() - autoDelayMin) / (autoDelayMax - autoDelayMin));
        case 7 -> clamp01(s.getPhysicsFixedStepMs() / 50.0);
        case 8 -> clamp01((s.getPhysicsMaxSubSteps() - 1) / 7.0);
        case 9 -> clamp01(s.getPhysicsDefaultFriction());
        default -> 0;
      };
      double y = yStart + sliderRow * lineH + 10;
      drawSlider(sliderX, y, sliderW, value, i == scene.getSelected());
      sliderRow++;
    }
    drawHints("Up/Down, Left/Right, Enter • " + Localization.t("common.back") + ": Esc", w, h);
  }

  private String toPct(float v) {
    int pct = Math.round(v * 100f);
    return pct + "%";
  }

  private void clear(double w, double h) {
    gc.setFill(theme.getBackgroundColor());
    gc.fillRect(0, 0, w, h);
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
    if (path == null) return null;
    return imageCache.computeIfAbsent(path, p -> {
      try {
        // Try classpath first
        var url = getClass().getClassLoader().getResource(p);
        if (url != null) return new Image(url.toExternalForm());
        // Try filesystem
        File f = new File(p);
        if (f.exists()) return new Image(f.toURI().toString());
      } catch (Exception e) {
        System.err.println("Failed to load menu image: " + p);
      }
      return null;
    });
  }

  public void clearImageCache() {
    imageCache.clear();
  }

  private void drawTitle(String text, double w, double y) {
    if (text == null || text.isBlank()) text = "JVN";
    gc.setFill(theme.getTitleColor());
    gc.setFont(theme.getTitleFont());
    gc.fillText(text, (w - measure(text, theme.getTitleFont())) / 2, y);
  }

  private void drawMenuList(String[] items, int selected, double w, double h) {
    double yStart = resolve(theme.getListYStart(), h);
    double lineH = theme.getLineHeight();
    for (int i = 0; i < items.length; i++) {
      boolean sel = i == selected;
      String label = (sel ? theme.getItemSelectedPrefix() : theme.getItemPrefix()) + items[i];
      gc.setFill(sel ? theme.getItemSelectedColor() : theme.getItemColor());
      gc.setFont(theme.getItemFont());
      gc.fillText(label, (w - measure(label, theme.getItemFont())) / 2, yStart + i * lineH);
    }
  }

  private void drawHints(String text, double w, double h) {
    gc.setFill(theme.getHintColor());
    gc.setFont(theme.getHintFont());
    gc.fillText(text, (w - measure(text, theme.getHintFont())) / 2, h - 20);
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
    double yStart = resolve(theme.getListYStart(), h);
    double lineH = theme.getLineHeight();
    // Compute by vertical slot
    double relY = mouseY - yStart;
    if (relY < 0) return -1;
    int idx = (int) Math.floor(relY / lineH);
    if (idx < 0 || idx >= count) return -1;
    return idx;
  }

  private File getThumbnailFile(LoadMenuScene scene) {
    String dir = scene.getSaveDirectory();
    String name = scene.getSelectedName();
    if (dir == null || name == null) return null;
    File f = new File(dir, name + ".png");
    return f.exists() ? f : null;
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

  private double resolve(double v, double total) {
    // if v <= 1, treat as fraction of total; otherwise pixels
    return v <= 1.0 ? (total * v) : v;
  }
}
