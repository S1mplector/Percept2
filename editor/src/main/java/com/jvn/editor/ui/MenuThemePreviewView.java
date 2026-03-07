package com.jvn.editor.ui;

import com.jvn.core.menu.MainMenuScene;
import com.jvn.core.vn.VnSettings;
import com.jvn.core.vn.save.VnSaveManager;
import com.jvn.fx.menu.MenuRenderer;
import com.jvn.fx.menu.MenuTheme;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.StackPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;

import java.io.StringReader;
import java.util.Properties;

public class MenuThemePreviewView extends StackPane {
  private final Canvas canvas = new Canvas(1200, 740);
  private final GraphicsContext gc = canvas.getGraphicsContext2D();
  private final MenuRenderer renderer = new MenuRenderer(gc, MenuTheme.defaults());
  private final MainMenuScene previewScene = new MainMenuScene(null, new VnSettings(), new VnSaveManager(), "story/prologue.vns", null);

  public MenuThemePreviewView() {
    getChildren().add(canvas);
    // Interactivity for preview: keyboard and mouse
    canvas.setFocusTraversable(true);
    canvas.addEventHandler(MouseEvent.MOUSE_MOVED, e -> handleMouseMove(e));
    canvas.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> { handleMouseMove(e); canvas.requestFocus(); });
    canvas.addEventHandler(KeyEvent.KEY_PRESSED, e -> handleKey(e));
  }

  public void setThemeFromText(String text) {
    try {
      Properties p = new Properties();
      if (text != null && !text.isBlank()) {
        p.load(new StringReader(text));
      }
      MenuTheme t = MenuTheme.defaults();
      t.apply(p);
      this.renderer.setTheme(t);
    } catch (Exception ignored) {
      // keep defaults
    }
  }

  public void setSize(double w, double h) {
    double sw = sanitizeCanvasDimension(w);
    double sh = sanitizeCanvasDimension(h);
    if (Math.abs(canvas.getWidth() - sw) >= 0.5) canvas.setWidth(sw);
    if (Math.abs(canvas.getHeight() - sh) >= 0.5) canvas.setHeight(sh);
  }

  public void render(long deltaMs) {
    double w = canvas.getWidth();
    double h = canvas.getHeight();
    renderer.renderMainMenu(previewScene, w, h);
  }

  private void handleMouseMove(MouseEvent e) {
    double w = canvas.getWidth();
    double h = canvas.getHeight();
    int idx = renderer.getHoverIndexForList(4, w, h, e.getX(), e.getY());
    if (idx >= 0) previewScene.setSelected(idx);
  }

  private void handleKey(KeyEvent e) {
    KeyCode c = e.getCode();
    if (c == KeyCode.UP || c == KeyCode.W) {
      previewScene.moveSelection(-1);
      e.consume();
    } else if (c == KeyCode.DOWN || c == KeyCode.S) {
      previewScene.moveSelection(1);
      e.consume();
    }
  }

  private static double sanitizeCanvasDimension(double value) {
    if (!Double.isFinite(value)) return 1.0;
    return Math.max(1.0, Math.min(8192.0, value));
  }
}
