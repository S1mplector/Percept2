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
import com.jvn.fx.menu.MenuTheme;
import com.jvn.core.scene2d.Scene2D;
import com.jvn.fx.scene2d.FxBlitter2D;
import com.jvn.core.scene2d.Scene2DBase;
import com.jvn.fx.render.FxSceneRendererRegistry;
import com.jvn.core.graphics.Camera2D;
import com.jvn.core.graphics.ViewportScaler2D;
import com.jvn.core.demo.Example2DScene;
import com.jvn.core.input.ActionBindingProfile;
import com.jvn.core.input.ActionBindingProfileStore;
import com.jvn.core.input.ActionMap;
import com.jvn.core.input.InputActions;
import com.jvn.core.input.InputCode;
import com.jvn.core.vn.VnSettingsStore;
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
    primaryStage.show();

    // Initialize graphics context and resize canvas with scene
    this.gc = this.canvas.getGraphicsContext2D();
    this.vnRenderer = new VnRenderer(gc);
    this.menuRenderer = new MenuRenderer(gc, MenuTheme.fromAssets());
    this.blitter2D = new FxBlitter2D(gc);
    this.rendererRegistry = createRendererRegistry();
    this.actionMap = loadActionBindings();
    scene.widthProperty().addListener((obs, ov, nv) -> this.canvas.setWidth(nv.doubleValue()));
    scene.heightProperty().addListener((obs, ov, nv) -> this.canvas.setHeight(nv.doubleValue()));

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
              engine.scenes().push(new SettingsScene(vn.getState().getSettings(), vn.getAudioFacade()));
            }
          }
          if (actionMap.matches(InputActions.QUICK_SAVE, code)) handleQuickSave();
          if (actionMap.matches(InputActions.QUICK_LOAD, code)) handleQuickLoad();
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

    scene.setOnMouseMoved(e -> {
      mouseX = e.getX();
      mouseY = e.getY();
      if (engine != null && engine.input() != null) engine.input().setMousePosition(mouseX, mouseY);
      // Hover selection for menus
      if (engine != null) {
        com.jvn.core.scene.Scene currentScene = engine.scenes().peek();
        if (currentScene instanceof MainMenuScene main) {
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

    
    scene.setOnScroll(e -> {
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
    reg.register(VnScene.class, (vn, ctx) ->
        vnRenderer.render(vn.getState(), vn.getScenario(), ctx.width(), ctx.height(), mouseX, mouseY));

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

  private void performSlotSave(VnScene vn, int slot) {
    String slotName = slot == 0 ? "_quicksave" : ("slot_" + slot);
    try {
      var saveManager = new com.jvn.core.vn.save.VnSaveManager();
      saveManager.save(vn.getState(), slotName);
      try { writeSaveThumbnail(vn, slotName); } catch (Exception ignored) {}
      vn.getState().showHudMessage("Saved to " + (slot == 0 ? "Quick Save" : "Slot " + slot), 1500);
    } catch (Exception e) {
      vn.getState().showHudMessage("Save failed", 1500);
    }
  }

  private void performSlotLoad(VnScene vn, int slot) {
    String slotName = slot == 0 ? "_quicksave" : ("slot_" + slot);
    try {
      var saveManager = new com.jvn.core.vn.save.VnSaveManager();
      var saveData = saveManager.load(slotName);
      if (saveData.getScenarioId().equals(vn.getScenario().getId())) {
        saveManager.applyToState(saveData, vn.getState());
        vn.getState().showHudMessage("Loaded from " + (slot == 0 ? "Quick Save" : "Slot " + slot), 1500);
      } else {
        vn.getState().showHudMessage("Save is for different scenario", 1500);
      }
    } catch (Exception e) {
      vn.getState().showHudMessage(slot == 0 ? "No quick save found" : "Slot " + slot + " is empty", 1500);
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
          double val = computeSliderValue01(x);
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

  private void handleMouseDrag(double x, double y) {
    if (engine == null) return;
    com.jvn.core.scene.Scene currentScene = engine.scenes().peek();
    if (currentScene instanceof SettingsScene settings) {
      int idx = menuRenderer.getHoverIndexForSettings(settings, canvas.getWidth(), canvas.getHeight(), x, y);
      if (idx >= 0 && settings.hasSliderAt(idx)) {
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
    double sliderW = w * 0.45;
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
