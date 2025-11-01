package com.jvn.fx;

import com.jvn.core.engine.Engine;
// Note: Avoid importing com.jvn.core.scene.Scene to prevent name clash with javafx.scene.Scene
import com.jvn.core.vn.VnScene;
import com.jvn.core.menu.MainMenuScene;
import com.jvn.core.menu.LoadMenuScene;
import com.jvn.core.menu.SettingsScene;
import com.jvn.core.menu.SaveMenuScene;
import com.jvn.fx.vn.VnRenderer;
import com.jvn.fx.menu.MenuRenderer;
import com.jvn.core.scene2d.Scene2D;
import com.jvn.fx.scene2d.FxBlitter2D;
import com.jvn.core.scene2d.Scene2DBase;
import com.jvn.core.graphics.Camera2D;
import com.jvn.core.demo.Example2DScene;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
// no direct import of javafx.scene.Scene to avoid name clash; use fully qualified name
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.embed.swing.SwingFXUtils;
import javax.imageio.ImageIO;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FxLauncher extends Application {
  private static Engine engine;
  private AnimationTimer timer;
  private Canvas canvas;
  private GraphicsContext gc;
  private VnRenderer vnRenderer;
  private MenuRenderer menuRenderer;
  private FxBlitter2D blitter2D;
  private double mouseX = 0;
  private double mouseY = 0;

  public static void launch(Engine eng) {
    engine = eng;
    Application.launch();
  }

  private void handleToggleHistory() {
    if (engine == null) return;
    com.jvn.core.scene.Scene currentScene = engine.scenes().peek();
    if (currentScene instanceof VnScene) {
      VnScene vnScene = (VnScene) currentScene;
      boolean wasShown = vnScene.getState().isHistoryOverlayShown();
      vnScene.getState().toggleHistoryOverlay();
      if (!wasShown) vnScene.getState().clearHistoryScroll();
    }
  }

  private void handleCloseHistory() {
    if (engine == null) return;
    com.jvn.core.scene.Scene currentScene = engine.scenes().peek();
    if (currentScene instanceof VnScene) {
      VnScene vnScene = (VnScene) currentScene;
      if (vnScene.getState().isHistoryOverlayShown()) {
        vnScene.getState().setHistoryOverlayShown(false);
      }
    }
  }

  @Override
  public void start(Stage primaryStage) {
    String title = engine != null && engine.getConfig() != null ? engine.getConfig().title() : "JVN";
    int width = engine != null && engine.getConfig() != null ? engine.getConfig().width() : 960;
    int height = engine != null && engine.getConfig() != null ? engine.getConfig().height() : 540;
    primaryStage.setTitle(title);

    StackPane root = new StackPane();
    this.canvas = new Canvas(width, height);
    root.getChildren().add(this.canvas);
    javafx.scene.Scene scene = new javafx.scene.Scene(root, width, height);
    primaryStage.setScene(scene);
    primaryStage.show();

    // Initialize graphics context and resize canvas with scene
    this.gc = this.canvas.getGraphicsContext2D();
    this.vnRenderer = new VnRenderer(gc);
    this.menuRenderer = new MenuRenderer(gc);
    this.blitter2D = new FxBlitter2D(gc);
    scene.widthProperty().addListener((obs, ov, nv) -> this.canvas.setWidth(nv.doubleValue()));
    scene.heightProperty().addListener((obs, ov, nv) -> this.canvas.setHeight(nv.doubleValue()));

    // Input handling
    scene.setOnKeyPressed(e -> {
      // Intercept when VN history overlay is open
      com.jvn.core.scene.Scene cur = engine != null ? engine.scenes().peek() : null;
      if (cur instanceof VnScene vn && vn.getState().isHistoryOverlayShown()) {
        if (e.getCode() == KeyCode.ESCAPE || e.getCode() == KeyCode.SPACE || e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.B) {
          // Close overlay on Esc/Space/Enter/B
          vn.getState().setHistoryOverlayShown(false);
          e.consume();
          return;
        } else if (e.getCode() == KeyCode.UP) {
          vn.getState().scrollHistoryByLines(1);
          e.consume();
          return;
        } else if (e.getCode() == KeyCode.DOWN) {
          vn.getState().scrollHistoryByLines(-1);
          e.consume();
          return;
        } else if (e.getCode() == KeyCode.PAGE_UP) {
          vn.getState().scrollHistoryByLines(5);
          e.consume();
          return;
        } else if (e.getCode() == KeyCode.PAGE_DOWN) {
          vn.getState().scrollHistoryByLines(-5);
          e.consume();
          return;
        }
        // Ignore other keys while overlay is open
        return;
      }

      if (e.getCode() == KeyCode.SPACE || e.getCode() == KeyCode.ENTER) {
        if (!handleMenuEnter()) handleAdvance();
      } else if (e.getCode() == KeyCode.CONTROL || e.getCode() == KeyCode.COMMAND) {
        // Ctrl/Cmd = Skip mode toggle
        handleToggleSkip();
      } else if (e.getCode() == KeyCode.A) {
        // A = Auto-play toggle
        handleToggleAutoPlay();
      } else if (e.getCode() == KeyCode.H) {
        // H = Hide UI
        handleToggleUI();
      } else if (e.getCode() == KeyCode.B) {
        // B = Toggle history/backlog overlay
        handleToggleHistory();
      } else if (e.getCode() == KeyCode.S) {
        // S = Settings (in-game)
        com.jvn.core.scene.Scene currentScene = engine.scenes().peek();
        if (currentScene instanceof VnScene vn) {
          engine.scenes().push(new SettingsScene(vn.getState().getSettings(), vn.getAudioFacade()));
        }
      } else if (e.getCode() == KeyCode.ESCAPE) {
        // ESC = Close history overlay if open
        if (!handleMenuBack()) handleCloseHistory();
      } else if (e.getCode() == KeyCode.UP) {
        handleMenuMove(-1);
      } else if (e.getCode() == KeyCode.DOWN) {
        handleMenuMove(1);
      } else if (e.getCode() == KeyCode.LEFT) {
        handleSettingsAdjust(-1);
      } else if (e.getCode() == KeyCode.RIGHT) {
        handleSettingsAdjust(1);
      } else if (e.getCode() == KeyCode.F5) {
        // F5 = Quick save
        handleQuickSave();
      } else if (e.getCode() == KeyCode.F9) {
        // F9 = Quick load
        handleQuickLoad();
      } else if (e.getCode() == KeyCode.F6) {
        // F6 = Save menu (in-game)
        com.jvn.core.scene.Scene currentScene = engine.scenes().peek();
        if (currentScene instanceof VnScene vn) {
          engine.scenes().push(new SaveMenuScene(engine, new com.jvn.core.vn.save.VnSaveManager(), vn));
        }
      } else if (e.getCode() == KeyCode.F10) {
        // F10 = Launch 2D demo scene (developer shortcut)
        if (engine != null) engine.scenes().push(new Example2DScene());
      } else if (e.getCode() == KeyCode.DELETE) {
        handleMenuDelete();
      } else if (e.getCode() == KeyCode.R) {
        handleMenuRename();
      }

      // Feed to engine input system
      if (engine != null && engine.input() != null) {
        engine.input().keyDown(e.getCode().getName());
      }
    });

    // (scroll handler consolidated below with history overlay support)

    scene.setOnKeyReleased(e -> {
      // Feed to engine input system
      if (engine != null && engine.input() != null) {
        engine.input().keyUp(e.getCode().getName());
      }
    });

    scene.setOnMouseMoved(e -> {
      mouseX = e.getX();
      mouseY = e.getY();
      if (engine != null && engine.input() != null) engine.input().setMousePosition(mouseX, mouseY);
      // Hover selection for menus
      if (engine != null) {
        com.jvn.core.scene.Scene currentScene = engine.scenes().peek();
        if (currentScene instanceof MainMenuScene main) {
          int idx = menuRenderer.getHoverIndexForList(4, canvas.getWidth(), canvas.getHeight(), mouseX, mouseY);
          if (idx >= 0) main.setSelected(idx);
        } else if (currentScene instanceof LoadMenuScene load) {
          int idx = menuRenderer.getHoverIndexForList(load.getSaves().size(), canvas.getWidth() * 0.6, canvas.getHeight(), mouseX, mouseY);
          if (idx >= 0) {
            // ensure selection moves to hovered
            // We don't have setSelected; adjust by computing delta
            int current = load.getSelected();
            int delta = idx - current;
            if (delta != 0) load.moveSelection(delta);
          }
        } else if (currentScene instanceof SettingsScene settings) {
          int idx = menuRenderer.getHoverIndexForList(settings.itemCount(), canvas.getWidth(), canvas.getHeight(), mouseX, mouseY);
          if (idx >= 0) settings.setSelected(idx);
        } else if (currentScene instanceof SaveMenuScene save) {
          int idx = menuRenderer.getHoverIndexForList(save.getEntriesCount(), canvas.getWidth() * 0.6, canvas.getHeight(), mouseX, mouseY);
          if (idx >= 0) save.setSelected(idx);
        }
      }
    });

    scene.setOnMousePressed(e -> {
      if (engine != null && engine.input() != null) {
        int btn = e.getButton() == MouseButton.PRIMARY ? 1 : (e.getButton() == MouseButton.MIDDLE ? 2 : 3);
        engine.input().mouseDown(btn);
      }
    });

    scene.setOnMouseReleased(e -> {
      if (engine != null && engine.input() != null) {
        int btn = e.getButton() == MouseButton.PRIMARY ? 1 : (e.getButton() == MouseButton.MIDDLE ? 2 : 3);
        engine.input().mouseUp(btn);
      }
    });

    scene.setOnMouseClicked(e -> {
      if (e.getButton() == MouseButton.PRIMARY) {
        // If history overlay open, close it instead of interacting
        if (engine != null) {
          com.jvn.core.scene.Scene currentScene = engine.scenes().peek();
          if (currentScene instanceof VnScene vn && vn.getState().isHistoryOverlayShown()) {
            vn.getState().setHistoryOverlayShown(false);
            return;
          }
        }
        handleMouseClick(e.getX(), e.getY());
      }
    });

    // Mouse wheel scroll for history overlay
    scene.setOnScroll(e -> {
      // Always feed scroll to engine input
      if (engine != null && engine.input() != null) {
        engine.input().addScrollDeltaY(e.getDeltaY());
      }
      // Additionally, if VN history overlay is open, map scroll to history scroll
      if (engine != null) {
        com.jvn.core.scene.Scene currentScene = engine.scenes().peek();
        if (currentScene instanceof VnScene vn && vn.getState().isHistoryOverlayShown()) {
          double dy = e.getDeltaY();
          if (dy > 0) vn.getState().scrollHistoryByLines(2); else if (dy < 0) vn.getState().scrollHistoryByLines(-2);
        }
      }
    });

    scene.setOnMouseDragged(e -> {
      if (e.isPrimaryButtonDown()) {
        handleMouseDrag(e.getX(), e.getY());
      }
    });

    timer = new AnimationTimer() {
      private long lastNs = -1L;

      @Override
      public void handle(long now) {
        if (lastNs < 0) { lastNs = now; return; }
        long deltaMs = (now - lastNs) / 1_000_000L;
        lastNs = now;
        if (engine != null) engine.update(deltaMs);

        // Render
        if (gc != null && canvas != null) {
          double w = canvas.getWidth();
          double h = canvas.getHeight();

          // Check if current scene is a VN scene
          com.jvn.core.scene.Scene currentScene = engine != null ? engine.scenes().peek() : null;
          if (currentScene instanceof VnScene vnScene) {
            vnRenderer.render(vnScene.getState(), vnScene.getScenario(), w, h, mouseX, mouseY);
          } else if (currentScene instanceof Scene2D scene2D) {
            blitter2D.setViewport(w, h);
            if (currentScene instanceof Scene2DBase s2db) {
              if (engine != null) s2db.setInput(engine.input());
              if (s2db.getCamera() == null) s2db.setCamera(new Camera2D());
            }
            scene2D.render(blitter2D, w, h);
          } else if (currentScene instanceof MainMenuScene main) {
            menuRenderer.renderMainMenu(main, w, h);
          } else if (currentScene instanceof LoadMenuScene load) {
            menuRenderer.renderLoadMenu(load, w, h);
          } else if (currentScene instanceof SettingsScene settings) {
            menuRenderer.renderSettings(settings, w, h);
          } else if (currentScene instanceof SaveMenuScene save) {
            menuRenderer.renderSaveMenu(save, w, h);
          } else {
            // Default render: clear and draw title text
            gc.setFill(Color.BLACK);
            gc.fillRect(0, 0, w, h);
            gc.setFill(Color.WHITE);
            gc.fillText("JVN - Java Visual Novel", 20, 30);
            gc.fillText("No scene loaded. Push a Scene to the engine's scene manager.", 20, 60);
          }
        }
      }
    };
    timer.start();
  }

  private void handleAdvance() {
    if (engine == null) return;
    com.jvn.core.scene.Scene currentScene = engine.scenes().peek();
    if (currentScene instanceof VnScene) {
      ((VnScene) currentScene).advance();
    }
  }

  private void handleToggleSkip() {
    if (engine == null) return;
    com.jvn.core.scene.Scene currentScene = engine.scenes().peek();
    if (currentScene instanceof VnScene) {
      ((VnScene) currentScene).toggleSkipMode();
    }
  }

  private void handleToggleAutoPlay() {
    if (engine == null) return;
    com.jvn.core.scene.Scene currentScene = engine.scenes().peek();
    if (currentScene instanceof VnScene) {
      ((VnScene) currentScene).toggleAutoPlayMode();
    }
  }

  private void handleToggleUI() {
    if (engine == null) return;
    com.jvn.core.scene.Scene currentScene = engine.scenes().peek();
    if (currentScene instanceof VnScene) {
      ((VnScene) currentScene).getState().toggleUiHidden();
    }
  }

  private void handleQuickSave() {
    if (engine == null) return;
    com.jvn.core.scene.Scene currentScene = engine.scenes().peek();
    if (currentScene instanceof VnScene) {
      VnScene vn = (VnScene) currentScene;
      boolean success = vn.quickSave();
      if (success) {
        try { writeQuickSaveThumbnail(vn); } catch (Exception ignored) {}
      }
      // HUD toast
      vn.getState().showHudMessage(success ? "Quick saved" : "Quick save failed", 1500);
    }
  }

  private void handleQuickLoad() {
    if (engine == null) return;
    com.jvn.core.scene.Scene currentScene = engine.scenes().peek();
    if (currentScene instanceof VnScene) {
      VnScene vn = (VnScene) currentScene;
      boolean success = vn.quickLoad();
      vn.getState().showHudMessage(success ? "Quick loaded" : "Quick load failed", 1500);
    }
  }

  private void handleMouseClick(double x, double y) {
    if (engine == null) return;
    com.jvn.core.scene.Scene currentScene = engine.scenes().peek();
    if (currentScene instanceof VnScene) {
      VnScene vnScene = (VnScene) currentScene;
      
      // Check if clicking on a choice
      if (vnScene.getState().getCurrentNode() != null && 
          vnScene.getState().getCurrentNode().getType() == com.jvn.core.vn.VnNodeType.CHOICE) {
        int choiceIndex = vnRenderer.getHoveredChoiceIndex(
          vnScene.getState().getCurrentNode().getChoices(),
          canvas.getWidth(), canvas.getHeight(), x, y
        );
        if (choiceIndex >= 0) {
          vnScene.selectChoice(choiceIndex);
          return;
        }
      }
      
      // Otherwise treat as advance
      vnScene.advance();
    } else if (currentScene instanceof MainMenuScene main) {
      int idx = menuRenderer.getHoverIndexForList(4, canvas.getWidth(), canvas.getHeight(), x, y);
      if (idx >= 0) {
        main.setSelected(idx);
        main.activateSelected();
      }
    } else if (currentScene instanceof LoadMenuScene load) {
      int idx = menuRenderer.getHoverIndexForList(load.getSaves().size(), canvas.getWidth() * 0.6, canvas.getHeight(), x, y);
      if (idx >= 0) {
        int current = load.getSelected();
        int delta = idx - current;
        if (delta != 0) load.moveSelection(delta);
        load.loadSelected();
      }
    } else if (currentScene instanceof SettingsScene settings) {
      int idx = menuRenderer.getHoverIndexForList(settings.itemCount(), canvas.getWidth(), canvas.getHeight(), x, y);
      if (idx >= 0) {
        settings.setSelected(idx);
        if (idx == settings.itemCount() - 1) {
          settings.toggleCurrent();
        } else {
          double val = computeSliderValue01(x);
          settings.setValueByIndex(idx, val);
        }
      }
    }
  }

  private void handleMouseDrag(double x, double y) {
    if (engine == null) return;
    com.jvn.core.scene.Scene currentScene = engine.scenes().peek();
    if (currentScene instanceof SettingsScene settings) {
      int idx = menuRenderer.getHoverIndexForList(settings.itemCount(), canvas.getWidth(), canvas.getHeight(), x, y);
      if (idx >= 0 && idx < settings.itemCount() - 1) {
        settings.setSelected(idx);
        double val = computeSliderValue01(x);
        settings.setValueByIndex(idx, val);
      }
    }
  }

  private boolean handleMenuEnter() {
    if (engine == null) return false;
    com.jvn.core.scene.Scene currentScene = engine.scenes().peek();
    if (currentScene instanceof MainMenuScene main) {
      main.activateSelected();
      return true;
    } else if (currentScene instanceof LoadMenuScene load) {
      load.loadSelected();
      return true;
    } else if (currentScene instanceof SettingsScene settings) {
      settings.toggleCurrent();
      return true;
    } else if (currentScene instanceof SaveMenuScene save) {
      if (save.isNewItemSelected()) {
        TextInputDialog dlg = new TextInputDialog("");
        dlg.setTitle("New Save");
        dlg.setHeaderText(null);
        dlg.setContentText("Save name:");
        var result = dlg.showAndWait();
        result.ifPresent(name -> {
          String trimmed = name.trim();
          if (!trimmed.isEmpty()) save.saveNew(trimmed);
        });
      } else {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Overwrite save '" + save.getSelectedName() + "'?", ButtonType.YES, ButtonType.NO);
        alert.setHeaderText(null);
        alert.setTitle("Confirm Overwrite");
        var result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {
          save.saveOverwriteSelected();
        }
      }
      return true;
    }
    return false;
  }

  private boolean handleMenuBack() {
    if (engine == null) return false;
    com.jvn.core.scene.Scene currentScene = engine.scenes().peek();
    if (currentScene instanceof LoadMenuScene || currentScene instanceof SettingsScene || currentScene instanceof SaveMenuScene) {
      engine.scenes().pop();
      return true;
    }
    return false;
  }

  private void handleMenuMove(int delta) {
    if (engine == null) return;
    com.jvn.core.scene.Scene currentScene = engine.scenes().peek();
    if (currentScene instanceof MainMenuScene main) {
      main.moveSelection(delta);
    } else if (currentScene instanceof LoadMenuScene load) {
      load.moveSelection(delta);
    } else if (currentScene instanceof SettingsScene settings) {
      settings.moveSelection(delta);
    } else if (currentScene instanceof SaveMenuScene save) {
      save.moveSelection(delta);
    }
  }

  private void handleSettingsAdjust(int delta) {
    if (engine == null) return;
    com.jvn.core.scene.Scene currentScene = engine.scenes().peek();
    if (currentScene instanceof SettingsScene settings) {
      settings.adjustCurrent(delta);
    }
  }

  private double computeSliderValue01(double mouseX) {
    double w = canvas.getWidth();
    double sliderW = w * 0.4;
    double sliderX = (w - sliderW) / 2;
    double v = (mouseX - sliderX) / sliderW;
    if (v < 0) v = 0;
    if (v > 1) v = 1;
    return v;
  }

  private void writeQuickSaveThumbnail(VnScene vnScene) {
    try {
      var qsm = vnScene.getQuickSaveManager();
      if (qsm == null) return;
      String dir = qsm.getSaveDirectory();
      String name = qsm.getQuickSaveSlotName();
      if (dir == null || name == null || name.isBlank()) return;
      Path d = Paths.get(dir);
      Files.createDirectories(d);
      File out = d.resolve(name + ".png").toFile();
      var img = canvas.snapshot(null, null);
      ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", out);
    } catch (Exception ignored) {
    }
  }

  private void handleMenuDelete() {
    if (engine == null) return;
    com.jvn.core.scene.Scene currentScene = engine.scenes().peek();
    if (currentScene instanceof LoadMenuScene load) {
      String sel = load.getSelectedName();
      if (sel == null) return;
      Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Delete save '" + sel + "'?", ButtonType.YES, ButtonType.NO);
      alert.setHeaderText(null);
      alert.setTitle("Confirm Delete");
      var result = alert.showAndWait();
      if (result.isPresent() && result.get() == ButtonType.YES) {
        load.deleteSelected();
      }
    } else if (currentScene instanceof SaveMenuScene save) {
      String sel = save.getSelectedName();
      if (sel == null) return;
      Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Delete save '" + sel + "'?", ButtonType.YES, ButtonType.NO);
      alert.setHeaderText(null);
      alert.setTitle("Confirm Delete");
      var result = alert.showAndWait();
      if (result.isPresent() && result.get() == ButtonType.YES) {
        save.deleteSelected();
      }
    }
  }

  private void handleMenuRename() {
    if (engine == null) return;
    com.jvn.core.scene.Scene currentScene = engine.scenes().peek();
    if (currentScene instanceof LoadMenuScene load) {
      String sel = load.getSelectedName();
      if (sel == null) return;
      TextInputDialog dlg = new TextInputDialog(sel);
      dlg.setTitle("Rename Save");
      dlg.setHeaderText(null);
      dlg.setContentText("New name:");
      var result = dlg.showAndWait();
      if (result.isPresent()) {
        String newName = result.get().trim();
        if (!newName.isEmpty()) {
          load.renameSelected(newName);
        }
      }
    } else if (currentScene instanceof SaveMenuScene save) {
      String sel = save.getSelectedName();
      if (sel == null) return;
      TextInputDialog dlg = new TextInputDialog(sel);
      dlg.setTitle("Rename Save");
      dlg.setHeaderText(null);
      dlg.setContentText("New name:");
      var result = dlg.showAndWait();
      if (result.isPresent()) {
        String newName = result.get().trim();
        if (!newName.isEmpty()) {
          save.renameSelected(newName);
        }
      }
    }
  }

  @Override
  public void stop() {
    if (timer != null) timer.stop();
    if (engine != null && engine.isStarted()) {
      engine.stop();
    }
  }
}
