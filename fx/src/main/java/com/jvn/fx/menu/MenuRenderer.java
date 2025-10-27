package com.jvn.fx.menu;

import com.jvn.core.localization.Localization;
import com.jvn.core.menu.LoadMenuScene;
import com.jvn.core.menu.MainMenuScene;
import com.jvn.core.menu.SettingsScene;
import com.jvn.core.vn.VnSettings;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.image.Image;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.List;

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

  public void renderLoadMenu(LoadMenuScene scene, double w, double h) {
    clear(w, h);
    drawTitle(Localization.t("load.title"), w, 60);
    List<String> saves = scene.getSaves();
    if (saves.isEmpty()) {
      drawCenteredText(Localization.t("load.no_saves"), w, h/2, itemFont, Color.GRAY);
    } else {
      // Left: list
      drawMenuList(saves.toArray(new String[0]), scene.getSelected(), w * 0.6, h);
      // Right: preview
      String previewPath = getSelectedPreviewImagePath(scene);
      if (previewPath != null) {
        drawPreview(previewPath, w, h);
      } else {
        drawPreviewPlaceholder(w, h);
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
      Localization.t("settings.skip_unread") + ": " + (s.isSkipUnreadText() ? "ON" : "OFF")
    };

    drawMenuList(items, scene.getSelected(), w, h);
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
    double width = w; // for main/settings; load passes 0.6*w if needed externally
    double yStart = h * 0.35;
    double lineH = 40;
    double totalH = count * lineH;
    // Compute by vertical slot
    double relY = mouseY - yStart + (lineH/2);
    if (relY < 0) return -1;
    int idx = (int) Math.floor(relY / lineH);
    if (idx < 0 || idx >= count) return -1;
    return idx;
  }

  private String getSelectedPreviewImagePath(LoadMenuScene scene) {
    try {
      // Expose via scene method in future; for now derive here
      String name = scene.getSelectedName();
      if (name == null) return null;
      // Use scene helper if present; else return null
      // We call scene.getSelectedPreviewImagePath() if available; fall back to null
      try {
        java.lang.reflect.Method m = scene.getClass().getMethod("getSelectedPreviewImagePath");
        Object o = m.invoke(scene);
        return (String) o;
      } catch (Exception ignored) { }
      return null;
    } catch (Exception ignored) {
      return null;
    }
  }

  private void drawPreview(String path, double w, double h) {
    try {
      var url = getClass().getClassLoader().getResource(path);
      if (url == null) { drawPreviewPlaceholder(w, h); return; }
      Image img = new Image(url.toExternalForm());
      double panelX = w * 0.65;
      double panelY = h * 0.25;
      double panelW = w * 0.3;
      double panelH = h * 0.5;
      gc.setFill(Color.rgb(255,255,255,0.1));
      gc.fillRoundRect(panelX - 8, panelY - 8, panelW + 16, panelH + 16, 12, 12);
      // Fit image into panel keeping aspect
      double scale = Math.min(panelW / img.getWidth(), panelH / img.getHeight());
      double iw = img.getWidth() * scale;
      double ih = img.getHeight() * scale;
      double ix = panelX + (panelW - iw) / 2;
      double iy = panelY + (panelH - ih) / 2;
      gc.drawImage(img, ix, iy, iw, ih);
    } catch (Exception e) {
      drawPreviewPlaceholder(w, h);
    }
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
}
