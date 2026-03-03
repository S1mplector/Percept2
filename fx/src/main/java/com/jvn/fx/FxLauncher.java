package com.jvn.fx;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

import javax.imageio.ImageIO;

import com.jvn.core.demo.Example2DScene;
import com.jvn.core.engine.Engine;
import com.jvn.core.graphics.Camera2D;
import com.jvn.core.graphics.ViewportScaler2D;
import com.jvn.core.input.ActionMap;
import com.jvn.core.input.InputActions;
import com.jvn.core.input.InputCode;
import com.jvn.core.menu.LoadMenuScene;
import com.jvn.core.menu.MainMenuScene;
import com.jvn.core.menu.PauseMenuScene;
import com.jvn.core.menu.SaveMenuScene;
import com.jvn.core.menu.SettingsScene;
import com.jvn.core.scene2d.Scene2D;
import com.jvn.core.scene2d.Scene2DBase;
import com.jvn.core.vn.VnScene;
import com.jvn.fx.menu.MenuRenderer;
import com.jvn.fx.menu.MenuTheme;
import com.jvn.fx.render.FxSceneRendererRegistry;
import com.jvn.fx.scene2d.FxBlitter2D;
import com.jvn.fx.vn.VnRenderer;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

public class FxLauncher extends Application {
  private static Engine engine;
  private AnimationTimer timer;
  private Canvas canvas;
  private GraphicsContext gc;
  private VnRenderer vnRenderer;
  private MenuRenderer menuRenderer;
  private FxBlitter2D blitter2D;
  private FxSceneRendererRegistry rendererRegistry;
  private ActionMap actionMap;
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
    applyLinuxDefaultWindowState(primaryStage);
    primaryStage.show();

    // Initialize graphics context and resize canvas with scene
    this.gc = this.canvas.getGraphicsContext2D();
    this.vnRenderer = new VnRenderer(gc);
    this.menuRenderer = new MenuRenderer(gc, MenuTheme.fromAssets());
    this.blitter2D = new FxBlitter2D(gc);
    this.rendererRegistry = createRendererRegistry();
    this.actionMap = loadActionBindings();
    canvas.widthProperty().bind(root.widthProperty());
    canvas.heightProperty().bind(root.heightProperty());

    // Input handling
    scene.setOnKeyPressed(e -> {
      // Intercept when VN save slot overlay is open
      com.jvn.core.scene.Scene cur = engine != null ? engine.scenes().peek() : null;
      if (cur instanceof VnScene vn && vn.getState().isSaveSlotOverlayShown()) {
        handleSaveSlotOverlayInput(vn, e);
        e.consume();
        return;
      }
      
      // Intercept when VN history overlay is open
      if (cur instanceof VnScene vn && vn.getState().isHistoryOverlayShown()) {
        int pageLines = vnRenderer != null ? vnRenderer.getHistoryLinesPerPage(canvas.getHeight()) : 5;
        int step = e.isShiftDown() ? 5 : 1;
        if (e.getCode() == KeyCode.ESCAPE || e.getCode() == KeyCode.SPACE || e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.B) {
          // Close overlay on Esc/Space/Enter/B
          vn.getState().setHistoryOverlayShown(false);
          e.consume();
          return;
        } else if (e.getCode() == KeyCode.UP) {
          vn.getState().scrollHistoryByLines(step);
          e.consume();
          return;
        } else if (e.getCode() == KeyCode.DOWN) {
          vn.getState().scrollHistoryByLines(-step);
          e.consume();
          return;
        } else if (e.getCode() == KeyCode.PAGE_UP) {
          vn.getState().scrollHistoryByLines(pageLines);
          e.consume();
          return;
        } else if (e.getCode() == KeyCode.PAGE_DOWN) {
          vn.getState().scrollHistoryByLines(-pageLines);
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
          engine.scenes().push(new SettingsScene(
              engine,
              new com.jvn.core.vn.save.VnSaveManager(),
              "demo.vns",
              vn.getState().getSettings(),
              vn.getAudioFacade()
          ));
        }
      } else if (e.getCode() == KeyCode.ESCAPE) {
        // ESC = Pop menu if in menu, otherwise open pause menu from VN scene
        if (!handleMenuBack()) handleOpenPauseMenu();
      } else if (e.getCode() == KeyCode.UP) {
        handleMenuMove(-1);
      } else if (e.getCode() == KeyCode.DOWN) {
        handleMenuMove(1);
      } else if (e.getCode() == KeyCode.LEFT) {
        handleSettingsAdjust(-1);
      } else if (e.getCode() == KeyCode.RIGHT) {
        handleSettingsAdjust(1);
      } else if (e.getCode() == KeyCode.F5) {
        // F5 = Show save slot overlay
        handleShowSaveSlotOverlay(true);
      } else if (e.getCode() == KeyCode.F9) {
        // F9 = Show load slot overlay
        handleShowSaveSlotOverlay(false);
      } else if (e.getCode() == KeyCode.F6) {
        // F6 = Save menu (in-game)
        com.jvn.core.scene.Scene currentScene = engine.scenes().peek();
        if (currentScene instanceof VnScene vn) {
          engine.scenes().push(new SaveMenuScene(engine, new com.jvn.core.vn.save.VnSaveManager(), vn));
        }
      } else if (e.getCode() == KeyCode.F10) {
        // F10 = Launch 2D demo scene (developer shortcut)
        if (engine != null) engine.scenes().push(new Example2DScene());
      } else if (e.getCode() == KeyCode.F11) {
        // F11 = Launch Billiards scene (developer shortcut via reflection)
        try {
          Class<?> cls = Class.forName("com.jvn.billiards.scene.BilliardsScene2D");
          Object obj = cls.getDeclaredConstructor().newInstance();
          if (obj instanceof com.jvn.core.scene2d.Scene2D s2d && engine != null) {
            engine.scenes().push(s2d);
          }
        } catch (Throwable ignored) {}
      } else if (e.getCode() == KeyCode.DELETE) {
        handleMenuDelete();
      } else if (e.getCode() == KeyCode.R) {
        handleMenuRename();
      }

      // Feed to engine input system
      if (engine != null && engine.input() != null) {
        InputCode code = InputCode.key(e.getCode().getName());
        engine.input().keyDown(code);
        if (actionMap != null) {
          if (actionMap.matches(InputActions.ADVANCE, code)) { if (!handleMenuEnter()) handleAdvance(); }
          if (actionMap.matches(InputActions.MENU_CONFIRM, code)) { if (!handleMenuEnter()) handleAdvance(); }
          if (actionMap.matches(InputActions.SKIP_TOGGLE, code)) handleToggleSkip();
          if (actionMap.matches(InputActions.AUTO_TOGGLE, code)) handleToggleAutoPlay();
          if (actionMap.matches(InputActions.HIDE_UI, code)) handleToggleUI();
          if (actionMap.matches(InputActions.HISTORY, code)) handleToggleHistory();
          if (actionMap.matches(InputActions.SETTINGS, code)) {
            com.jvn.core.scene.Scene currentScene = engine.scenes().peek();
            if (currentScene instanceof VnScene vn) {
              engine.scenes().push(new SettingsScene(
                  engine,
                  new com.jvn.core.vn.save.VnSaveManager(),
                  "demo.vns",
                  vn.getState().getSettings(),
                  vn.getAudioFacade()
              ));
            }
          }
          if (actionMap.matches(InputActions.QUICK_SAVE, code)) handleQuickSave();
          if (actionMap.matches(InputActions.QUICK_LOAD, code)) handleQuickLoad();
          if (actionMap.matches(InputActions.ROLLBACK, code)) handleRollback();
          if (actionMap.matches(InputActions.ROLLFORWARD, code)) handleRollforward();
          if (actionMap.matches(InputActions.SAVE_MENU, code)) {
            com.jvn.core.scene.Scene currentScene = engine.scenes().peek();
            if (currentScene instanceof VnScene vn) {
              engine.scenes().push(new SaveMenuScene(engine, new com.jvn.core.vn.save.VnSaveManager(), vn));
            }
          }
          if (actionMap.matches(InputActions.MENU_BACK, code)) handleMenuBack();
          if (actionMap.matches(InputActions.MENU_UP, code)) handleMenuMove(-1);
          if (actionMap.matches(InputActions.MENU_DOWN, code)) handleMenuMove(1);
          if (actionMap.matches(InputActions.MENU_LEFT, code)) handleSettingsAdjust(-1);
          if (actionMap.matches(InputActions.MENU_RIGHT, code)) handleSettingsAdjust(1);
          if (actionMap.matches(InputActions.MENU_DELETE, code)) handleMenuDelete();
          if (actionMap.matches(InputActions.MENU_RENAME, code)) handleMenuRename();
        }
      }
    });

    

    scene.setOnKeyReleased(e -> {
      // Feed to engine input system
      if (engine != null && engine.input() != null) {
        engine.input().keyUp(com.jvn.core.input.InputCode.key(e.getCode().getName()));
      }
    });

    canvas.setOnMouseMoved(e -> {
      mouseX = e.getX();
      mouseY = e.getY();
      if (engine != null && engine.input() != null) engine.input().setMousePosition(mouseX, mouseY);
      // Hover selection for menus
      if (engine != null) {
        com.jvn.core.scene.Scene currentScene = engine.scenes().peek();
        if (currentScene instanceof PauseMenuScene pause) {
          int idx = menuRenderer.getHoverIndexForPauseMenu(pause, canvas.getWidth(), canvas.getHeight(), mouseX, mouseY);
          if (idx >= 0) pause.setSelected(idx);
        } else if (currentScene instanceof MainMenuScene main) {
          int idx = menuRenderer.getHoverIndexForMainMenu(main, canvas.getWidth(), canvas.getHeight(), mouseX, mouseY);
          if (idx >= 0) main.setSelected(idx);
        } else if (currentScene instanceof LoadMenuScene load) {
          int idx = menuRenderer.getHoverIndexForLoadMenu(load, canvas.getWidth(), canvas.getHeight(), mouseX, mouseY);
          if (idx >= 0) {
            load.setSelected(idx);
          }
        } else if (currentScene instanceof SettingsScene settings) {
          int idx = menuRenderer.getHoverIndexForSettings(settings, canvas.getWidth(), canvas.getHeight(), mouseX, mouseY);
          if (idx >= 0) settings.setSelected(idx);
        } else if (currentScene instanceof SaveMenuScene save) {
          int idx = menuRenderer.getHoverIndexForSaveMenu(save, canvas.getWidth(), canvas.getHeight(), mouseX, mouseY);
          if (idx >= 0) save.setSelected(idx);
        }
      }
    });

    canvas.setOnMousePressed(e -> {
      if (engine != null && engine.input() != null) {
        int btn = e.getButton() == MouseButton.PRIMARY ? 1 : (e.getButton() == MouseButton.MIDDLE ? 2 : 3);
        engine.input().mouseDown(btn);
      }
    });

    canvas.setOnMouseReleased(e -> {
      if (engine != null && engine.input() != null) {
        int btn = e.getButton() == MouseButton.PRIMARY ? 1 : (e.getButton() == MouseButton.MIDDLE ? 2 : 3);
        engine.input().mouseUp(btn);
      }
    });

    canvas.setOnMouseClicked(e -> {
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
      } else if (e.getButton() == MouseButton.SECONDARY) {
        // Right-click = open pause menu or pop menu
        if (!handleMenuBack()) handleOpenPauseMenu();
      }
    });

    
    canvas.setOnScroll(e -> {
      if (engine != null && engine.input() != null) {
        engine.input().addScrollDeltaY(e.getDeltaY());
      }
      if (engine != null) {
        com.jvn.core.scene.Scene currentScene = engine.scenes().peek();
        if (currentScene instanceof VnScene vn && vn.getState().isHistoryOverlayShown()) {
          double dy = e.getDeltaY();
          int step = e.isShiftDown() ? 6 : 2;
          if (dy > 0) vn.getState().scrollHistoryByLines(step); else if (dy < 0) vn.getState().scrollHistoryByLines(-step);
        }
      }
    });

    canvas.setOnMouseDragged(e -> {
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

          com.jvn.core.scene.Scene currentScene = engine != null ? engine.scenes().peek() : null;
          boolean rendered = rendererRegistry != null
              && rendererRegistry.render(currentScene, new com.jvn.fx.render.FxSceneRendererRegistry.RenderContext(gc, blitter2D, w, h, mouseX, mouseY));
          if (!rendered) {
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

  private FxSceneRendererRegistry createRendererRegistry() {
    FxSceneRendererRegistry reg = new FxSceneRendererRegistry();
    reg.register(VnScene.class, (vn, ctx) -> {
      vnRenderer.setAudioFacade(vn.getAudioFacade());
      vnRenderer.render(vn.getState(), vn.getScenario(), ctx.width(), ctx.height(), mouseX, mouseY);
    });

    reg.register(PauseMenuScene.class, (pause, ctx) -> {
      // Render the underlying VN scene first, then the pause overlay
      VnScene vn = pause.getVnScene();
      if (vn != null) {
        vnRenderer.setAudioFacade(vn.getAudioFacade());
        vnRenderer.render(vn.getState(), vn.getScenario(), ctx.width(), ctx.height(), mouseX, mouseY);
      }
      menuRenderer.renderPauseMenu(pause, ctx.width(), ctx.height());
    });
    reg.register(MainMenuScene.class, (scene, ctx) -> menuRenderer.renderMainMenu(scene, ctx.width(), ctx.height()));
    reg.register(LoadMenuScene.class, (scene, ctx) -> menuRenderer.renderLoadMenu(scene, ctx.width(), ctx.height()));
    reg.register(SettingsScene.class, (scene, ctx) -> menuRenderer.renderSettings(scene, ctx.width(), ctx.height()));
    reg.register(SaveMenuScene.class, (scene, ctx) -> menuRenderer.renderSaveMenu(scene, ctx.width(), ctx.height()));

    reg.register(Scene2D.class, (scene2D, ctx) -> {
      double w = ctx.width();
      double h = ctx.height();
      FxBlitter2D b = ctx.blitter();
      b.setViewport(w, h);
      b.clear(0, 0, 0, 1);
      if (scene2D instanceof Scene2DBase s2db) {
        if (engine != null) s2db.setInput(engine.input());
        if (s2db.getCamera() == null) s2db.setCamera(new Camera2D());
      }
      double targetW = (engine != null && engine.getConfig() != null) ? engine.getConfig().width() : w;
      double targetH = (engine != null && engine.getConfig() != null) ? engine.getConfig().height() : h;
      var vp = ViewportScaler2D.fit(targetW, targetH, w, h);
      b.push();
      b.translate(vp.offsetX(), vp.offsetY());
      b.scale(vp.scale(), vp.scale());
      scene2D.render(b, vp.targetWidth(), vp.targetHeight());
      b.pop();
    });
    return reg;
  }

  private ActionMap loadActionBindings() {
    try {
      return FxLauncherBindings.load();
    } catch (Exception e) {
      ActionMap map = new ActionMap(new com.jvn.core.input.Input());
      map.loadProfile(InputActions.defaultProfile());
      return map;
    }
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

  private void handleRollback() {
    if (engine == null) return;
    com.jvn.core.scene.Scene currentScene = engine.scenes().peek();
    if (currentScene instanceof VnScene vn) {
      boolean success = vn.rollback();
      if (success) {
        vn.getState().showHudMessage("Rolled back", 800);
      }
    }
  }

  private void handleRollforward() {
    if (engine == null) return;
    com.jvn.core.scene.Scene currentScene = engine.scenes().peek();
    if (currentScene instanceof VnScene vn) {
      boolean success = vn.rollforward();
      if (success) {
        vn.getState().showHudMessage("Rolled forward", 800);
      }
    }
  }

  private void handleShowSaveSlotOverlay(boolean isSaveMode) {
    if (engine == null) return;
    com.jvn.core.scene.Scene currentScene = engine.scenes().peek();
    if (currentScene instanceof VnScene vn) {
      vn.getState().showSaveSlotOverlay(isSaveMode);
    }
  }

  private void handleSaveSlotOverlayInput(VnScene vn, javafx.scene.input.KeyEvent e) {
    var state = vn.getState();
    KeyCode code = e.getCode();
    
    if (code == KeyCode.ESCAPE) {
      state.hideSaveSlotOverlay();
    } else if (code == KeyCode.ENTER || code == KeyCode.SPACE) {
      // Confirm selection
      int slot = state.getSaveSlotSelected();
      boolean isSaveMode = state.isSaveSlotOverlaySaveMode();
      state.hideSaveSlotOverlay();
      
      if (isSaveMode) {
        performSlotSave(vn, slot);
      } else {
        performSlotLoad(vn, slot);
      }
    } else if (code == KeyCode.UP) {
      // Move up (subtract 2 to go to previous row)
      state.moveSaveSlotSelection(-2);
    } else if (code == KeyCode.DOWN) {
      // Move down (add 2 to go to next row)
      state.moveSaveSlotSelection(2);
    } else if (code == KeyCode.LEFT) {
      state.moveSaveSlotSelection(-1);
    } else if (code == KeyCode.RIGHT) {
      state.moveSaveSlotSelection(1);
    } else if (code.isDigitKey()) {
      // Direct slot selection with number keys
      String name = code.getName();
      try {
        int digit = Integer.parseInt(name);
        state.setSaveSlotSelected(digit);
      } catch (NumberFormatException ignored) {}
    }
  }

  // Centralized save slot service
  private final com.jvn.core.vn.save.VnSaveSlotService saveSlotService = new com.jvn.core.vn.save.VnSaveSlotService();

  private void performSlotSave(VnScene vn, int slot) {
    String slotName = saveSlotService.getSlotName(slot);
    var slotInfo = saveSlotService.getSlotInfo(slot);
    try {
      var saveManager = new com.jvn.core.vn.save.VnSaveManager();
      saveManager.save(vn.getState(), slotName);
      try { writeSaveThumbnail(vn, slotName); } catch (Exception ignored) {}
      vn.getState().showHudMessage("Saved to " + slotInfo.displayName(), 1500);
    } catch (Exception e) {
      vn.getState().showHudMessage("Save failed", 1500);
    }
  }

  private void performSlotLoad(VnScene vn, int slot) {
    String slotName = saveSlotService.getSlotName(slot);
    var slotInfo = saveSlotService.getSlotInfo(slot);
    if (!slotInfo.hasData()) {
      vn.getState().showHudMessage(slotInfo.displayName() + " is empty", 1500);
      return;
    }
    try {
      var saveManager = new com.jvn.core.vn.save.VnSaveManager();
      var saveData = saveManager.load(slotName);
      if (saveData.getScenarioId().equals(vn.getScenario().getId())) {
        saveManager.applyToState(saveData, vn.getState());
        vn.getState().showHudMessage("Loaded from " + slotInfo.displayName(), 1500);
      } else {
        vn.getState().showHudMessage("Save is for different scenario", 1500);
      }
    } catch (Exception e) {
      vn.getState().showHudMessage("Load failed: " + e.getMessage(), 1500);
    }
  }

  private void writeSaveThumbnail(VnScene vnScene, String slotName) {
    try {
      String dir = System.getProperty("user.home") + "/.jvn/saves";
      Path d = Paths.get(dir);
      Files.createDirectories(d);
      File out = d.resolve(slotName + ".png").toFile();
      var img = canvas.snapshot(null, null);
      ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", out);
    } catch (Exception ignored) {}
  }

  private void handleMouseClick(double x, double y) {
    if (engine == null) return;
    com.jvn.core.scene.Scene currentScene = engine.scenes().peek();
    if (currentScene instanceof VnScene) {
      VnScene vnScene = (VnScene) currentScene;

      com.jvn.core.vn.ui.VnUiActionButtonSpec textBoxButton =
          vnRenderer.getHoveredTextBoxButton(vnScene.getState(), canvas.getWidth(), canvas.getHeight(), x, y);
      if (textBoxButton != null && executeTextBoxButtonAction(vnScene, textBoxButton)) {
        return;
      }
      
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
      vnScene.advanceFromClick();
    } else if (currentScene instanceof PauseMenuScene pause) {
      int idx = menuRenderer.getHoverIndexForPauseMenu(pause, canvas.getWidth(), canvas.getHeight(), x, y);
      if (idx >= 0) {
        pause.setSelected(idx);
        pause.activateSelected();
      }
    } else if (currentScene instanceof MainMenuScene main) {
      int idx = menuRenderer.getHoverIndexForMainMenu(main, canvas.getWidth(), canvas.getHeight(), x, y);
      if (idx >= 0) {
        main.setSelected(idx);
        main.activateSelected();
      }
    } else if (currentScene instanceof LoadMenuScene load) {
      int idx = menuRenderer.getHoverIndexForLoadMenu(load, canvas.getWidth(), canvas.getHeight(), x, y);
      if (idx >= 0) {
        load.setSelected(idx);
        load.activateSelected();
      }
    } else if (currentScene instanceof SettingsScene settings) {
      int idx = menuRenderer.getHoverIndexForSettings(settings, canvas.getWidth(), canvas.getHeight(), x, y);
      if (idx >= 0) {
        settings.setSelected(idx);
        if (!settings.hasSliderAt(idx)) {
          settings.toggleCurrent();
          if (settings.consumeCloseRequested()) engine.scenes().pop();
        } else {
          double val = menuRenderer.computeSettingsSliderValue01(settings, idx, canvas.getWidth(), canvas.getHeight(), x);
          settings.setValueByIndex(idx, val);
        }
      }
    } else if (currentScene instanceof SaveMenuScene save) {
      int idx = menuRenderer.getHoverIndexForSaveMenu(save, canvas.getWidth(), canvas.getHeight(), x, y);
      if (idx >= 0) {
        save.setSelected(idx);
        handleMenuEnter();
      }
    }
  }

  private boolean executeTextBoxButtonAction(VnScene vnScene, com.jvn.core.vn.ui.VnUiActionButtonSpec button) {
    if (vnScene == null || button == null || !button.enabled()) return false;
    String action = normalizeButtonAction(button.action());
    String target = button.target() == null ? "" : button.target().trim();
    var state = vnScene.getState();

    switch (action) {
      case "advance" -> {
        vnScene.advanceFromClick();
        return true;
      }
      case "quick_save", "save_quick" -> {
        handleQuickSave();
        return true;
      }
      case "quick_load", "load_quick" -> {
        handleQuickLoad();
        return true;
      }
      case "save_slots", "open_save_slots" -> {
        state.showSaveSlotOverlay(true);
        return true;
      }
      case "load_slots", "open_load_slots" -> {
        state.showSaveSlotOverlay(false);
        return true;
      }
      case "toggle_history", "history" -> {
        boolean wasShown = state.isHistoryOverlayShown();
        state.toggleHistoryOverlay();
        if (!wasShown) state.clearHistoryScroll();
        return true;
      }
      case "toggle_skip", "skip" -> {
        vnScene.toggleSkipMode();
        return true;
      }
      case "toggle_auto", "auto" -> {
        vnScene.toggleAutoPlayMode();
        return true;
      }
      case "toggle_ui", "ui" -> {
        state.toggleUiHidden();
        return true;
      }
      case "save_menu", "open_save_menu", "menu_save" -> {
        engine.scenes().push(new SaveMenuScene(engine, new com.jvn.core.vn.save.VnSaveManager(), vnScene));
        return true;
      }
      case "load_menu", "open_load_menu", "menu_load" -> {
        String script = target.isBlank() ? "demo.vns" : target;
        engine.scenes().push(new LoadMenuScene(
            engine,
            new com.jvn.core.vn.save.VnSaveManager(),
            script,
            vnScene.getState().getSettings(),
            vnScene.getAudioFacade()
        ));
        return true;
      }
      case "settings_menu", "open_settings_menu", "menu_settings" -> {
        engine.scenes().push(new SettingsScene(
            engine,
            new com.jvn.core.vn.save.VnSaveManager(),
            "demo.vns",
            vnScene.getState().getSettings(),
            vnScene.getAudioFacade()
        ));
        return true;
      }
      case "main_menu", "open_main_menu", "menu_main" -> {
        engine.scenes().push(new MainMenuScene(
            engine,
            vnScene.getState().getSettings(),
            new com.jvn.core.vn.save.VnSaveManager(),
            "demo.vns",
            vnScene.getAudioFacade()
        ));
        return true;
      }
      case "open_menu", "menu_open" -> {
        if (target.isBlank()) {
          vnScene.getState().showHudMessage("Button target missing", 1200);
          return true;
        }
        engine.scenes().push(new MainMenuScene(
            engine,
            vnScene.getState().getSettings(),
            new com.jvn.core.vn.save.VnSaveManager(),
            "demo.vns",
            vnScene.getAudioFacade(),
            target
        ));
        return true;
      }
      case "noop", "none" -> {
        return true;
      }
      default -> {
        vnScene.getState().showHudMessage("Unknown button action: " + action, 1200);
        return true;
      }
    }
  }

  private static String normalizeButtonAction(String raw) {
    if (raw == null || raw.isBlank()) return "noop";
    return raw.trim().toLowerCase(java.util.Locale.ROOT).replace('-', '_').replace(' ', '_');
  }

  private void handleMouseDrag(double x, double y) {
    if (engine == null) return;
    com.jvn.core.scene.Scene currentScene = engine.scenes().peek();
    if (currentScene instanceof SettingsScene settings) {
      int idx = menuRenderer.getHoverIndexForSettings(settings, canvas.getWidth(), canvas.getHeight(), x, y);
      if (idx >= 0 && settings.hasSliderAt(idx)) {
        settings.setSelected(idx);
        double val = menuRenderer.computeSettingsSliderValue01(settings, idx, canvas.getWidth(), canvas.getHeight(), x);
        settings.setValueByIndex(idx, val);
      }
    }
  }

  private boolean handleMenuEnter() {
    if (engine == null) return false;
    com.jvn.core.scene.Scene currentScene = engine.scenes().peek();
    if (currentScene instanceof PauseMenuScene pause) {
      pause.activateSelected();
      return true;
    } else if (currentScene instanceof MainMenuScene main) {
      main.activateSelected();
      return true;
    } else if (currentScene instanceof LoadMenuScene load) {
      load.activateSelected();
      return true;
    } else if (currentScene instanceof SettingsScene settings) {
      settings.toggleCurrent();
      if (settings.consumeCloseRequested()) engine.scenes().pop();
      return true;
    } else if (currentScene instanceof SaveMenuScene save) {
      if (save.activateSelectedWithoutPrompt()) {
        return true;
      }
      if (save.isNewItemSelected()) {
        save.saveNew(save.generateSaveName());
      } else {
        save.saveOverwriteSelected();
      }
      return true;
    }
    return false;
  }

  private boolean handleMenuBack() {
    if (engine == null) return false;
    com.jvn.core.scene.Scene currentScene = engine.scenes().peek();
    if (currentScene instanceof PauseMenuScene || currentScene instanceof LoadMenuScene
        || currentScene instanceof SettingsScene || currentScene instanceof SaveMenuScene) {
      engine.scenes().pop();
      return true;
    }
    return false;
  }

  private void handleOpenPauseMenu() {
    if (engine == null) return;
    com.jvn.core.scene.Scene currentScene = engine.scenes().peek();
    if (currentScene instanceof VnScene vn) {
      engine.scenes().push(new PauseMenuScene(
          engine, vn,
          new com.jvn.core.vn.save.VnSaveManager(),
          "demo.vns",
          vn.getAudioFacade()
      ));
    }
  }

  private void handleMenuMove(int delta) {
    if (engine == null) return;
    com.jvn.core.scene.Scene currentScene = engine.scenes().peek();
    if (currentScene instanceof PauseMenuScene pause) {
      pause.moveSelection(delta);
    } else if (currentScene instanceof MainMenuScene main) {
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

  private static void applyLinuxDefaultWindowState(Stage stage) {
    if (stage == null || !isLinux()) return;
    stage.setIconified(false);
    stage.setMaximized(true);
    Platform.runLater(() -> {
      stage.setIconified(false);
      stage.setMaximized(true);
    });
  }

  private static boolean isLinux() {
    return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux");
  }
}
