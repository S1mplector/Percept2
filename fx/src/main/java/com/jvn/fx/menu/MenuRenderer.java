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
import javafx.scene.text.FontWeight;

import java.util.List;
import java.io.File;

public class MenuRenderer {
  private final GraphicsContext gc;
  private final Font titleFont = Font.font("Arial", FontWeight.BOLD, 32);
  private final Font itemFont = Font.font("Arial", FontWeight.NORMAL, 20);
  private final Font hintFont = Font.font("Arial", FontWeight.NORMAL, 14);

  public MenuRenderer(GraphicsContext gc) { this.gc = gc; }

  public void renderMainMenu(MainMenuScene scene, double w, double h) {
    clear(w, h);
    drawTitle(Localization.t("app.title"), w, 60);

    String[] items = new String[] {
      Localization.t("menu.new_game"),
      Localization.t("menu.load"),
      Localization.t("menu.settings"),
      Localization.t("menu.quit")
    };

    drawMenuList(items, scene.getSelected(), w, h);
    drawHints(Localization.t("common.select") + ": Enter    " + Localization.t("common.back") + ": Esc", w, h);
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
    drawHints(Localization.t("common.select") + ": Enter    " + Localization.t("common.back") + ": Esc    "
        + Localization.t("save.delete") + ": Delete    " + Localization.t("save.rename") + ": R",
        w, h);
  }

  public void renderLoadMenu(LoadMenuScene scene, double w, double h) {
    clear(w, h);
    drawTitle(Localization.t("load.title"), w, 60);
    List<String> saves = scene.getSaves();
    if (saves.isEmpty()) {
      drawCenteredText(Localization.t("load.no_saves"), w, h/2, itemFont, Color.GRAY);
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
      Localization.t("settings.skip_after_choices") + ": " + (s.isSkipAfterChoices() ? "ON" : "OFF")
    };

    drawMenuList(items, scene.getSelected(), w, h);
    double yStart = h * 0.35;
    double lineH = 40;
    double sliderW = w * 0.4;
    double sliderX = (w - sliderW) / 2;

    double textSpeedMin = 10.0, textSpeedMax = 120.0;
    double autoDelayMin = 500.0, autoDelayMax = 5000.0;
    double[] values = new double[] {
      clamp01((s.getTextSpeed() - textSpeedMin) / (textSpeedMax - textSpeedMin)),
      clamp01(s.getBgmVolume()),
      clamp01(s.getSfxVolume()),
      clamp01(s.getVoiceVolume()),
      clamp01((s.getAutoPlayDelay() - autoDelayMin) / (autoDelayMax - autoDelayMin))
    };

    for (int i = 0; i < values.length; i++) {
      double y = yStart + i * lineH + 10;
      drawSlider(sliderX, y, sliderW, values[i], i == scene.getSelected());
    }
    drawHints("Up/Down, Left/Right, Enter • " + Localization.t("common.back") + ": Esc", w, h);
  }

  private String toPct(float v) {
    int pct = Math.round(v * 100f);
    return pct + "%";
  }

  private void clear(double w, double h) {
    gc.setFill(Color.rgb(10, 12, 18));
    gc.fillRect(0, 0, w, h);
  }

  private void drawTitle(String text, double w, double y) {
    if (text == null || text.isBlank()) text = "JVN";
    gc.setFill(Color.WHITE);
    gc.setFont(titleFont);
    gc.fillText(text, (w - measure(text, titleFont)) / 2, y);
  }

  private void drawMenuList(String[] items, int selected, double w, double h) {
    double yStart = h * 0.35;
    double lineH = 40;
    for (int i = 0; i < items.length; i++) {
      boolean sel = i == selected;
      String label = (sel ? "> " : "  ") + items[i];
      gc.setFill(sel ? Color.YELLOW : Color.LIGHTGRAY);
      gc.setFont(itemFont);
      gc.fillText(label, (w - measure(label, itemFont)) / 2, yStart + i * lineH);
    }
  }

  private void drawHints(String text, double w, double h) {
    gc.setFill(Color.rgb(200,200,200,0.8));
    gc.setFont(hintFont);
    gc.fillText(text, (w - measure(text, hintFont)) / 2, h - 20);
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
    double yStart = h * 0.35;
    double lineH = 40;
    // Compute by vertical slot
    double relY = mouseY - yStart + (lineH/2);
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
    gc.setFont(itemFont);
    drawCenteredText(Localization.t("load.no_preview"), panelX + panelW/2, panelY + panelH/2, itemFont, Color.GRAY);
  }

  private void drawPreviewMetadata(String scenarioId, Long timestampMs, Integer nodeIndex, double w, double h) {
    double panelX = w * 0.65;
    double panelY = h * 0.25;
    double panelH = h * 0.5;
    double textY = panelY + panelH + 20;
    gc.setFill(Color.LIGHTGRAY);
    gc.setFont(hintFont);
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
    gc.setFill(highlight ? Color.YELLOW : Color.LIGHTGRAY);
    double fill = Math.max(0, Math.min(1, value01));
    gc.fillRoundRect(x, y, w * fill, h, 6, 6);
    double knobX = x + w * fill - 6;
    gc.setFill(Color.WHITE);
    gc.fillOval(knobX, y - 4, 12, 12);
  }

  private double clamp01(double v) {
    return v < 0 ? 0 : (v > 1 ? 1 : v);
  }
}
