package com.jvn.scenerender.menu;

import java.util.List;

import com.jvn.core.localization.Localization;
import com.jvn.core.menu.gallery.GalleryEntry;
import com.jvn.core.menu.gallery.GalleryScene;
import com.jvn.core.menu.gallery.MusicRoomEntry;
import com.jvn.core.menu.gallery.MusicRoomScene;
import com.jvn.core.scene2d.Blitter2D;

/** Renders the unlockable-CG gallery screen and the music-room track-list screen. */
final class GalleryMusicRoomRenderer {
  private final Blitter2D blitter;
  private final MenuBackgroundRenderer background;

  GalleryMusicRoomRenderer(Blitter2D blitter, MenuBackgroundRenderer background) {
    this.blitter = blitter;
    this.background = background;
  }

  void renderGallery(GalleryScene scene, double w, double h) {
    blitter.setFill(20.0 / 255.0, 20.0 / 255.0, 30.0 / 255.0, 1.0);
    blitter.fillRect(0, 0, w, h);

    if (scene == null) return;

    MenuTheme.FontSpec titleFont = background.theme.getTitleFontSpec();
    MenuTheme.FontSpec catFont = background.theme.getItemFontSpec();
    MenuTheme.FontSpec smallFont = background.theme.getHintFontSpec();

    // Title
    blitter.setFont(titleFont.family(), titleFont.size(), titleFont.bold());
    blitter.setFill(1.0, 1.0, 1.0, 1.0);
    blitter.drawText(Localization.t("menu.gallery"), w * 0.04, h * 0.06, titleFont.size(), titleFont.bold());

    // Category tabs
    List<String> cats = scene.getCategories();
    double tabX = w * 0.04;
    blitter.setFont(catFont.family(), catFont.size(), catFont.bold());
    for (int i = 0; i < cats.size(); i++) {
      boolean active = i == scene.getCategoryIndex();
      MenuTheme.ColorSpec tabColor = active ? MenuTheme.ColorSpec.rgb255(240, 182, 115) : new MenuTheme.ColorSpec(0.6, 0.6, 0.6, 1.0);
      blitter.setFill(tabColor.r(), tabColor.g(), tabColor.b(), tabColor.a());
      blitter.drawText(cats.get(i), tabX, h * 0.12, catFont.size(), catFont.bold());
      tabX += catFont.size() * cats.get(i).length() * 0.65 + 24;
    }

    // Counter
    blitter.setFont(smallFont.family(), smallFont.size(), smallFont.bold());
    blitter.setFill(0.5, 0.5, 0.5, 1.0);
    blitter.drawText(scene.getUnlockedCount() + " / " + scene.getTotalCount(), w * 0.88, h * 0.06, smallFont.size(), smallFont.bold());

    if (scene.isViewingFullscreen()) {
      renderGalleryFullscreen(scene, w, h);
      return;
    }

    // Thumbnail grid
    List<GalleryEntry> entries = scene.getPageEntries();
    int cols = scene.getColumns();
    double gridX = w * 0.04;
    double gridY = h * 0.17;
    double cellW = (w * 0.92) / cols;
    double cellH = cellW * 0.6;
    double gap = 8;

    for (int i = 0; i < entries.size(); i++) {
      GalleryEntry entry = entries.get(i);
      int col = i % cols;
      int row = i / cols;
      double cx = gridX + col * (cellW + gap);
      double cy = gridY + row * (cellH + gap);
      boolean selected = i == scene.getSelectedIndex();
      boolean unlocked = scene.isUnlocked(entry);

      if (unlocked) {
        if (background.imageDimensions(entry.imagePath()).isPresent()) {
          blitter.drawImage(entry.imagePath(), cx, cy, cellW, cellH);
        } else {
          blitter.setFill(40.0 / 255.0, 40.0 / 255.0, 55.0 / 255.0, 1.0);
          blitter.fillRect(cx, cy, cellW, cellH);
        }
      } else {
        blitter.setFill(30.0 / 255.0, 30.0 / 255.0, 40.0 / 255.0, 1.0);
        blitter.fillRect(cx, cy, cellW, cellH);
        blitter.setFont(smallFont.family(), smallFont.size(), smallFont.bold());
        blitter.setFill(0.35, 0.35, 0.35, 1.0);
        blitter.drawText("???", cx + cellW / 2 - 10, cy + cellH / 2 + 5, smallFont.size(), smallFont.bold());
      }

      if (selected) {
        blitter.setStroke(240.0 / 255.0, 182.0 / 255.0, 115.0 / 255.0, 1.0);
        blitter.setStrokeWidth(2.5);
        blitter.strokeRect(cx - 1, cy - 1, cellW + 2, cellH + 2);
      }
    }

    // Page indicator
    int pageCount = scene.getPageCount();
    if (pageCount > 1) {
      blitter.setFont(smallFont.family(), smallFont.size(), smallFont.bold());
      blitter.setFill(0.5, 0.5, 0.5, 1.0);
      blitter.drawText("Page " + (scene.getPage() + 1) + " / " + pageCount, w * 0.46, h * 0.95, smallFont.size(), smallFont.bold());
    }
  }

  private void renderGalleryFullscreen(GalleryScene scene, double w, double h) {
    GalleryEntry entry = scene.getFullscreenEntry();
    if (entry == null) return;
    blitter.setFill(0.0, 0.0, 0.0, 1.0);
    blitter.fillRect(0, 0, w, h);
    double[] dims = background.imageDimensions(entry.imagePath()).orElse(null);
    if (dims == null) return;
    double iw = dims[0];
    double ih = dims[1];
    double scale = Math.min(w / iw, h / ih);
    double dw = iw * scale;
    double dh = ih * scale;
    blitter.drawImage(entry.imagePath(), (w - dw) / 2, (h - dh) / 2, dw, dh);
  }

  void renderMusicRoom(MusicRoomScene scene, double w, double h) {
    blitter.setFill(18.0 / 255.0, 18.0 / 255.0, 28.0 / 255.0, 1.0);
    blitter.fillRect(0, 0, w, h);

    if (scene == null) return;

    MenuTheme.FontSpec titleFont = background.theme.getTitleFontSpec();
    MenuTheme.FontSpec trackFont = background.theme.getItemFontSpec();
    MenuTheme.FontSpec artistFont = background.theme.getHintFontSpec();
    MenuTheme.FontSpec catFont = background.theme.getItemFontSpec();

    // Title
    blitter.setFont(titleFont.family(), titleFont.size(), titleFont.bold());
    blitter.setFill(1.0, 1.0, 1.0, 1.0);
    blitter.drawText(Localization.t("menu.music_room"), w * 0.04, h * 0.06, titleFont.size(), titleFont.bold());

    // Category tabs
    List<String> cats = scene.getCategories();
    double tabX = w * 0.04;
    blitter.setFont(catFont.family(), catFont.size(), catFont.bold());
    for (int i = 0; i < cats.size(); i++) {
      boolean active = i == scene.getCategoryIndex();
      MenuTheme.ColorSpec tabColor = active ? MenuTheme.ColorSpec.rgb255(126, 200, 227) : new MenuTheme.ColorSpec(0.6, 0.6, 0.6, 1.0);
      blitter.setFill(tabColor.r(), tabColor.g(), tabColor.b(), tabColor.a());
      blitter.drawText(cats.get(i), tabX, h * 0.12, catFont.size(), catFont.bold());
      tabX += catFont.size() * cats.get(i).length() * 0.65 + 24;
    }

    // Track list
    List<MusicRoomEntry> entries = scene.getCurrentEntries();
    double listX = w * 0.06;
    double listY = h * 0.18;
    double lineH = h * 0.06;

    for (int i = 0; i < entries.size(); i++) {
      MusicRoomEntry entry = entries.get(i);
      double y = listY + i * lineH;
      boolean selected = i == scene.getSelectedIndex();
      boolean unlocked = scene.isUnlocked(entry);
      boolean playing = entry.equals(scene.getNowPlaying()) && scene.isPlaying();

      if (selected) {
        blitter.setFill(1.0, 1.0, 1.0, 0.06);
        background.fillRoundRect(listX - 8, y - lineH * 0.65, w * 0.88, lineH, 6, 6);
      }

      if (!unlocked) {
        blitter.setFont(trackFont.family(), trackFont.size(), trackFont.bold());
        blitter.setFill(0.3, 0.3, 0.3, 1.0);
        blitter.drawText("??? - Locked", listX, y, trackFont.size(), trackFont.bold());
        continue;
      }

      // Now-playing indicator
      if (playing) {
        blitter.setFill(126.0 / 255.0, 200.0 / 255.0, 227.0 / 255.0, 1.0);
        blitter.drawText("♫", listX - 20, y, trackFont.size(), trackFont.bold());
      }

      blitter.setFont(trackFont.family(), trackFont.size(), trackFont.bold());
      MenuTheme.ColorSpec trackColor = selected ? MenuTheme.ColorSpec.rgb255(126, 200, 227) : new MenuTheme.ColorSpec(1.0, 1.0, 1.0, 1.0);
      blitter.setFill(trackColor.r(), trackColor.g(), trackColor.b(), trackColor.a());
      blitter.drawText(entry.title(), listX, y, trackFont.size(), trackFont.bold());

      if (!entry.artist().isBlank()) {
        blitter.setFont(artistFont.family(), artistFont.size(), artistFont.bold());
        blitter.setFill(0.5, 0.5, 0.5, 1.0);
        blitter.drawText(entry.artist(), listX + w * 0.5, y, artistFont.size(), artistFont.bold());
      }
    }
  }
}
