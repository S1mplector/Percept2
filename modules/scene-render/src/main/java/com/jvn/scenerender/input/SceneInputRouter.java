package com.jvn.scenerender.input;

import com.jvn.core.engine.Engine;
import com.jvn.core.menu.HistoryMenuScene;
import com.jvn.core.menu.LoadMenuScene;
import com.jvn.core.menu.MainMenuScene;
import com.jvn.core.menu.PauseMenuScene;
import com.jvn.core.menu.SaveMenuScene;
import com.jvn.core.menu.SettingsScene;
import com.jvn.core.scene.Scene;
import com.jvn.core.vn.VnNodeType;
import com.jvn.core.vn.VnScene;
import com.jvn.core.vn.ui.VnOverlayButtonSpec;
import com.jvn.core.vn.ui.VnUiActionButtonSpec;
import com.jvn.scenerender.menu.MenuRenderer;
import com.jvn.scenerender.vn.VnRenderer;

/**
 * Platform-agnostic click/hover/keyboard-action dispatch shared by the
 * FX and web launchers, extracted from FxLauncher's handleMouseClick,
 * its mouse-hover block, and its keyboard-action handler methods.
 */
public final class SceneInputRouter {
  private final MenuRenderer menuRenderer;
  private final VnRenderer vnRenderer;
  private final MenuSceneFactory menuSceneFactory;

  public SceneInputRouter(MenuRenderer menuRenderer, VnRenderer vnRenderer, MenuSceneFactory menuSceneFactory) {
    this.menuRenderer = menuRenderer;
    this.vnRenderer = vnRenderer;
    this.menuSceneFactory = menuSceneFactory;
  }

  /**
   * Click dispatch — full port of FxLauncher.handleMouseClick's scene-type
   * switch, minus the two error-overlay button actions that are
   * platform-specific (hot-reload-from-disk, JavaFX Clipboard copy).
   *
   * @return -1 if no error overlay is active or the click did not land on
   *         an overlay button, else the clicked button index (0/1/2). The
   *         caller decides what a non-negative result means: FxLauncher
   *         reacts via its existing handleRuntimeErrorButton; WebLauncher
   *         only acts on button 0 (clear the error).
   */
  public int handleClick(Scene currentScene, Engine engine, double renderWidth, double renderHeight, double x, double y) {
    if (currentScene instanceof VnScene vnScene) {
      if (vnScene.hasActiveError()) {
        return vnRenderer.renderErrorOverlay(vnScene.getActiveError(), renderWidth, renderHeight, x, y);
      }

      VnOverlayButtonSpec overlayButton =
          vnRenderer.getHoveredOverlayButton(vnScene.getState(), renderWidth, renderHeight, x, y);
      if (overlayButton != null && executeOverlayButtonAction(engine, vnScene, overlayButton)) {
        return -1;
      }
      if (vnScene.getState().hasModalOverlayScreen()) {
        if (vnScene.getState().getTopOverlayScreen() != null
            && vnScene.getState().getTopOverlayScreen().isDismissOnAdvance()) {
          vnScene.getState().dismissTopOverlayScreen();
        }
        return -1;
      }

      VnUiActionButtonSpec textBoxButton =
          vnRenderer.getHoveredTextBoxButton(vnScene.getState(), renderWidth, renderHeight, x, y);
      if (textBoxButton != null && executeTextBoxButtonAction(engine, vnScene, textBoxButton)) {
        return -1;
      }

      if (vnScene.getState().getCurrentNode() != null
          && vnScene.getState().getCurrentNode().getType() == VnNodeType.CHOICE) {
        int choiceIndex = vnRenderer.getHoveredChoiceIndex(
            vnScene.getState().getCurrentNode().getChoices(), renderWidth, renderHeight, x, y);
        if (choiceIndex >= 0) {
          vnScene.selectChoice(choiceIndex);
          return -1;
        }
      }

      vnScene.advanceFromClick();
      return -1;
    } else if (currentScene instanceof PauseMenuScene pause) {
      int idx = menuRenderer.getHoverIndexForPauseMenu(pause, renderWidth, renderHeight, x, y);
      if (idx >= 0) {
        pause.setSelected(idx);
        pause.activateSelected();
      }
    } else if (currentScene instanceof MainMenuScene main) {
      int idx = menuRenderer.getHoverIndexForMainMenu(main, renderWidth, renderHeight, x, y);
      if (idx >= 0) {
        main.setSelected(idx);
        main.activateSelected();
      }
    } else if (currentScene instanceof HistoryMenuScene history) {
      history.close();
    } else if (currentScene instanceof LoadMenuScene load) {
      MenuRenderer.LoadControlHit controlHit =
          menuRenderer.getLoadControlHit(load, renderWidth, renderHeight, x, y);
      if (controlHit != null && controlHit.handled()) {
        switch (controlHit.type()) {
          case CYCLE_LEFT -> load.movePage(-1);
          case CYCLE_RIGHT -> load.movePage(1);
          case TOGGLE_FAVORITES_ONLY -> load.toggleFavoritesOnly();
          case TOGGLE_SLOT_FAVORITE -> load.toggleFavoriteAt(controlHit.saveIndex());
          case SET_PAGE -> load.setPageFromProgress01(controlHit.pageProgress01());
          default -> { }
        }
        return -1;
      }
      int idx = menuRenderer.getHoverIndexForLoadMenu(load, renderWidth, renderHeight, x, y);
      if (idx >= 0) {
        load.setSelected(idx);
        load.activateSelected();
      }
    } else if (currentScene instanceof SettingsScene settings) {
      int idx = menuRenderer.getHoverIndexForSettings(settings, renderWidth, renderHeight, x, y);
      if (idx >= 0) {
        settings.setSelected(idx);
        if (!settings.hasSliderAt(idx)) {
          settings.toggleCurrent();
          if (settings.consumeCloseRequested()) engine.scenes().pop();
        } else {
          if (menuRenderer.isSettingsSliderResetHit(settings, idx, renderWidth, renderHeight, x, y)) {
            settings.resetValueByIndex(idx);
          } else {
            double val = menuRenderer.computeSettingsSliderValue01(settings, idx, renderWidth, renderHeight, x);
            settings.setValueByIndex(idx, val);
          }
        }
      }
    } else if (currentScene instanceof SaveMenuScene save) {
      int idx = menuRenderer.getHoverIndexForSaveMenu(save, renderWidth, renderHeight, x, y);
      if (idx >= 0) {
        save.setSelected(idx);
        menuEnter(engine);
      }
    }
    return -1;
  }

  /**
   * Hover-highlight dispatch across menu scene types. Port of the
   * menu-highlighting half of FxLauncher's canvas.setOnMouseMoved lambda —
   * the mouse-position-tracking half (engine.input().setMousePosition(...))
   * stays a platform-launcher responsibility since it operates on Input,
   * not scene state. FxLauncher's hover lambda never highlights VnScene
   * choices/textbox buttons/overlay buttons on hover, only these five menu
   * scene types, so there is no VnScene branch here.
   */
  public void handleHover(Scene currentScene, double renderWidth, double renderHeight, double x, double y) {
    if (currentScene instanceof PauseMenuScene pause) {
      int idx = menuRenderer.getHoverIndexForPauseMenu(pause, renderWidth, renderHeight, x, y);
      if (idx >= 0) pause.setSelected(idx);
    } else if (currentScene instanceof MainMenuScene main) {
      int idx = menuRenderer.getHoverIndexForMainMenu(main, renderWidth, renderHeight, x, y);
      main.setSelected(idx);
    } else if (currentScene instanceof LoadMenuScene load) {
      int idx = menuRenderer.getHoverIndexForLoadMenu(load, renderWidth, renderHeight, x, y);
      if (idx >= 0) load.setSelected(idx);
    } else if (currentScene instanceof SettingsScene settings) {
      int idx = menuRenderer.getHoverIndexForSettings(settings, renderWidth, renderHeight, x, y);
      if (idx >= 0) settings.setSelected(idx);
    } else if (currentScene instanceof SaveMenuScene save) {
      int idx = menuRenderer.getHoverIndexForSaveMenu(save, renderWidth, renderHeight, x, y);
      if (idx >= 0) save.setSelected(idx);
    }
  }

  /**
   * Advance the current VnScene by one step. Port of FxLauncher.handleAdvance.
   */
  public void advance(Engine engine) {
    if (engine == null) return;
    Scene currentScene = engine.scenes().peek();
    if (currentScene instanceof VnScene vn) vn.advance();
  }

  /**
   * Toggle skip mode on the current VnScene. Port of FxLauncher.handleToggleSkip.
   */
  public void toggleSkip(Engine engine) {
    if (engine == null) return;
    Scene currentScene = engine.scenes().peek();
    if (currentScene instanceof VnScene vn) vn.toggleSkipMode();
  }

  /**
   * Toggle auto-play mode on the current VnScene. Port of FxLauncher.handleToggleAutoPlay.
   */
  public void toggleAutoPlay(Engine engine) {
    if (engine == null) return;
    Scene currentScene = engine.scenes().peek();
    if (currentScene instanceof VnScene vn) vn.toggleAutoPlayMode();
  }

  /**
   * Toggle UI-hidden state on the current VnScene. Port of FxLauncher.handleToggleUI.
   */
  public void toggleUI(Engine engine) {
    if (engine == null) return;
    Scene currentScene = engine.scenes().peek();
    if (currentScene instanceof VnScene vn) vn.getState().toggleUiHidden();
  }

  /**
   * Keyboard/gamepad "confirm" action dispatch across menu scene types.
   * Port of FxLauncher.handleMenuEnter, minus the save-slot thumbnail
   * write (JavaFX Canvas.snapshot()-only, no browser equivalent).
   */
  public boolean menuEnter(Engine engine) {
    if (engine == null) return false;
    Scene currentScene = engine.scenes().peek();
    if (currentScene instanceof PauseMenuScene pause) {
      pause.activateSelected();
      return true;
    } else if (currentScene instanceof MainMenuScene main) {
      main.activateSelected();
      return true;
    } else if (currentScene instanceof HistoryMenuScene history) {
      history.close();
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
      // FxLauncher's original also writes a save-slot thumbnail here
      // (writeSaveThumbnail/captureVnThumbnail, JavaFX Canvas.snapshot()-only,
      // no browser equivalent). menuEnter itself stays platform-agnostic;
      // platforms that can produce a thumbnail hook in via
      // MenuSceneFactory.afterSaveSlotWritten (default no-op).
      VnScene vnScene = save.getCurrentVnScene();
      String slotName;
      if (save.isNewItemSelected()) {
        slotName = save.saveNew(save.generateSaveName());
      } else {
        slotName = save.saveOverwriteSelected();
      }
      if (slotName != null) {
        menuSceneFactory.afterSaveSlotWritten(engine, vnScene, slotName);
      }
      return true;
    }
    return false;
  }

  /**
   * Keyboard/gamepad "back" action dispatch across menu scene types.
   * Port of FxLauncher.handleMenuBack.
   */
  public boolean menuBack(Engine engine) {
    if (engine == null) return false;
    Scene currentScene = engine.scenes().peek();
    if (currentScene instanceof PauseMenuScene || currentScene instanceof LoadMenuScene
        || currentScene instanceof SettingsScene || currentScene instanceof SaveMenuScene
        || currentScene instanceof HistoryMenuScene) {
      engine.scenes().pop();
      return true;
    }
    if (currentScene instanceof MainMenuScene main) {
      String activeMenuId = main.getMenuId();
      String rootMenuId = main.getMenuProfile() != null ? main.getMenuProfile().defaultScreenId() : "main";
      if (activeMenuId != null && rootMenuId != null && !activeMenuId.equalsIgnoreCase(rootMenuId)) {
        engine.scenes().pop();
        return true;
      }
    }
    return false;
  }

  /**
   * Open the pause menu over the current VnScene. Port of
   * FxLauncher.handleOpenPauseMenu, minus the resolveConfiguredPauseMenuId
   * check (desktop system-property + jvn.project-manifest lookup, out of
   * scope for the browser fixture) — always pushes the plain PauseMenuScene.
   */
  public void openPauseMenu(Engine engine) {
    if (engine == null) return;
    Scene currentScene = engine.scenes().peek();
    if (currentScene instanceof VnScene vn) {
      String fallbackScript = resolveDefaultScriptForMenus(vn);
      Scene pauseScene = menuSceneFactory.createPauseMenuScene(engine, vn, fallbackScript);
      if (pauseScene != null) engine.scenes().push(pauseScene);
    }
  }

  /**
   * Keyboard/gamepad selection-move dispatch across menu scene types.
   * Port of FxLauncher.handleMenuMove.
   */
  public void menuMove(Engine engine, int delta) {
    if (engine == null) return;
    Scene currentScene = engine.scenes().peek();
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

  /**
   * Keyboard/gamepad value-adjust dispatch (settings sliders, load-menu
   * paging). Port of FxLauncher.handleSettingsAdjust.
   */
  public void settingsAdjust(Engine engine, int delta) {
    if (engine == null) return;
    Scene currentScene = engine.scenes().peek();
    if (currentScene instanceof SettingsScene settings) {
      settings.adjustCurrent(delta);
    } else if (currentScene instanceof LoadMenuScene load) {
      load.movePage(delta);
    }
  }

  /**
   * Quick-save the current VnScene. Port of FxLauncher.handleQuickSave,
   * minus the writeQuickSaveThumbnail side effect (JavaFX Canvas.snapshot()
   * -only, no browser equivalent).
   */
  public void quickSave(Engine engine) {
    if (engine == null) return;
    Scene currentScene = engine.scenes().peek();
    if (currentScene instanceof VnScene vn) {
      boolean success = menuSceneFactory.quickSave(vn);
      vn.getState().showHudMessage(success ? "Quick saved" : "Quick save failed", 1500);
    }
  }

  /**
   * Quick-load into the current VnScene. Port of FxLauncher.handleQuickLoad.
   */
  public void quickLoad(Engine engine) {
    if (engine == null) return;
    Scene currentScene = engine.scenes().peek();
    if (currentScene instanceof VnScene vn) {
      boolean success = menuSceneFactory.quickLoad(vn);
      vn.getState().showHudMessage(success ? "Quick loaded" : "Quick load failed", 1500);
    }
  }

  /**
   * Roll back the current VnScene by one step. Port of FxLauncher.handleRollback.
   */
  public void rollback(Engine engine) {
    if (engine == null) return;
    Scene currentScene = engine.scenes().peek();
    if (currentScene instanceof VnScene vn) {
      if (vn.rollback()) vn.getState().showHudMessage("Rolled back", 800);
    }
  }

  /**
   * Roll forward the current VnScene by one step. Port of FxLauncher.handleRollforward.
   */
  public void rollforward(Engine engine) {
    if (engine == null) return;
    Scene currentScene = engine.scenes().peek();
    if (currentScene instanceof VnScene vn) {
      if (vn.rollforward()) vn.getState().showHudMessage("Rolled forward", 800);
    }
  }

  /**
   * Open the save menu over the current VnScene. Port of FxLauncher.handleOpenSaveMenu.
   */
  public void openSaveMenu(Engine engine) {
    if (engine == null) return;
    Scene currentScene = engine.scenes().peek();
    if (currentScene instanceof VnScene vn) {
      Scene saveScene = menuSceneFactory.createSaveMenuScene(engine, vn);
      if (saveScene != null) engine.scenes().push(saveScene);
    }
  }

  /**
   * Open the load menu over the current VnScene. Port of FxLauncher.handleOpenLoadMenu.
   */
  public void openLoadMenu(Engine engine) {
    if (engine == null) return;
    Scene currentScene = engine.scenes().peek();
    if (currentScene instanceof VnScene vn) {
      String fallbackScript = resolveDefaultScriptForMenus(vn);
      Scene loadScene = menuSceneFactory.createLoadMenuScene(engine, vn, fallbackScript);
      if (loadScene != null) engine.scenes().push(loadScene);
    }
  }

  /**
   * Toggle the history-scrollback menu over the current VnScene. Port of
   * FxLauncher.handleToggleHistory.
   */
  public void toggleHistory(Engine engine) {
    if (engine == null) return;
    Scene currentScene = engine.scenes().peek();
    if (currentScene instanceof HistoryMenuScene) {
      engine.scenes().pop();
      return;
    }
    if (currentScene instanceof VnScene vnScene) {
      vnScene.getState().clearHistoryScroll();
      Scene historyScene = menuSceneFactory.createHistoryMenuScene(engine, vnScene);
      if (historyScene != null) engine.scenes().push(historyScene);
    }
  }

  /**
   * Test-only entry point into the private executeUiAction dispatch, so
   * tests can target individual action branches (e.g. "rollback") without
   * needing to reproduce click-hit-test geometry for a real button. Not
   * part of the public production API surface.
   */
  boolean executeUiActionForTest(VnScene vnScene, String action, String target, String overlayScreenId, boolean clickAdvanceUsesReveal) {
    return executeUiAction(null, vnScene, action, target, overlayScreenId, clickAdvanceUsesReveal);
  }

  /**
   * Test-only entry point into the private executeUiAction dispatch with an
   * Engine threaded through, for actions (e.g. "toggle_history") whose
   * behavior depends on inspecting/mutating the engine's scene stack.
   */
  boolean executeUiActionForTest(Engine engine, VnScene vnScene, String action, String target, String overlayScreenId, boolean clickAdvanceUsesReveal) {
    return executeUiAction(engine, vnScene, action, target, overlayScreenId, clickAdvanceUsesReveal);
  }

  private boolean executeTextBoxButtonAction(Engine engine, VnScene vnScene, VnUiActionButtonSpec button) {
    if (vnScene == null || button == null || !button.enabled()) return false;
    String target = button.target() == null ? "" : button.target().trim();
    String action = normalizeButtonAction(button.action());

    String rawAction = button.action();
    if (rawAction != null) {
      String trimmed = rawAction.trim();
      int colon = trimmed.indexOf(':');
      if (colon > 0 && colon < trimmed.length() - 1) {
        action = normalizeButtonAction(trimmed.substring(0, colon));
        if (target.isBlank()) {
          String inlineTarget = trimmed.substring(colon + 1).trim();
          if (!inlineTarget.isBlank()) {
            target = inlineTarget;
          }
        }
      }
    }
    return executeUiAction(engine, vnScene, action, target, null, true);
  }

  private boolean executeOverlayButtonAction(Engine engine, VnScene vnScene, VnOverlayButtonSpec button) {
    if (vnScene == null || button == null || !button.enabled()) return false;
    String target = button.target() == null ? "" : button.target().trim();
    String action = normalizeButtonAction(button.action());
    return executeUiAction(engine, vnScene, action, target, button.screenId(), false);
  }

  private boolean executeUiAction(
      Engine engine,
      VnScene vnScene,
      String action,
      String target,
      String overlayScreenId,
      boolean clickAdvanceUsesReveal
  ) {
    if (vnScene == null) return false;
    var state = vnScene.getState();

    switch (action) {
      case "advance" -> {
        if (clickAdvanceUsesReveal) vnScene.advanceFromClick();
        else vnScene.advance();
        return true;
      }
      case "rollback", "back" -> {
        vnScene.rollback();
        return true;
      }
      case "quick_save", "save_quick" -> {
        if (menuSceneFactory.quickSave(vnScene)) {
          state.showHudMessage("Quick saved", 1500);
        } else {
          state.showHudMessage("Quick save failed", 1500);
        }
        return true;
      }
      case "quick_load", "load_quick" -> {
        boolean success = menuSceneFactory.quickLoad(vnScene);
        state.showHudMessage(success ? "Quick loaded" : "Quick load failed", 1500);
        return true;
      }
      case "save_slots", "open_save_slots" -> {
        if (engine != null) {
          Scene saveScene = menuSceneFactory.createSaveMenuScene(engine, vnScene);
          if (saveScene != null) engine.scenes().push(saveScene);
        }
        return true;
      }
      case "load_slots", "open_load_slots" -> {
        if (engine != null) {
          String fallbackScript = resolveDefaultScriptForMenus(vnScene);
          Scene loadScene = menuSceneFactory.createLoadMenuScene(engine, vnScene, fallbackScript);
          if (loadScene != null) engine.scenes().push(loadScene);
        }
        return true;
      }
      case "toggle_history", "history" -> {
        if (engine != null) {
          if (engine.scenes().peek() instanceof com.jvn.core.menu.HistoryMenuScene) {
            engine.scenes().pop();
          } else {
            vnScene.getState().clearHistoryScroll();
            Scene historyScene = menuSceneFactory.createHistoryMenuScene(engine, vnScene);
            if (historyScene != null) engine.scenes().push(historyScene);
          }
        }
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
      case "hide", "close", "dismiss" -> {
        if (target == null || target.isBlank()) state.hideOverlayScreen(overlayScreenId);
        else state.hideOverlayScreen(target);
        return true;
      }
      case "return" -> {
        state.returnOverlayScreen(overlayScreenId, target);
        return true;
      }
      case "goto" -> {
        if (target == null || target.isBlank()) return true;
        state.hideOverlayScreen(overlayScreenId);
        vnScene.jumpToLabel(target);
        return true;
      }
      case "set", "flag", "unflag", "clear", "inc", "dec" -> {
        if (vnScene.getInterop() != null) {
          vnScene.getInterop().handle(new com.jvn.core.vn.VnExternalCommand("var", action + " " + target), vnScene);
        }
        return true;
      }
      case "persistent" -> {
        if (vnScene.getInterop() != null) {
          vnScene.getInterop().handle(new com.jvn.core.vn.VnExternalCommand("persistent", target), vnScene);
        }
        return true;
      }
      case "screen" -> {
        if (vnScene.getInterop() != null) {
          vnScene.getInterop().handle(new com.jvn.core.vn.VnExternalCommand("screen", target), vnScene);
        }
        return true;
      }
      case "save_menu", "open_save_menu", "menu_save" -> {
        if (engine != null) {
          Scene saveScene = menuSceneFactory.createSaveMenuScene(engine, vnScene);
          if (saveScene != null) engine.scenes().push(saveScene);
        }
        return true;
      }
      case "load_menu", "open_load_menu", "menu_load" -> {
        if (engine != null) {
          String fallbackScript = resolveDefaultScriptForMenus(vnScene);
          String script = target == null || target.isBlank() ? fallbackScript : target;
          Scene loadScene = menuSceneFactory.createLoadMenuScene(engine, vnScene, script);
          if (loadScene != null) engine.scenes().push(loadScene);
        }
        return true;
      }
      case "settings_menu", "open_settings_menu", "menu_settings" -> {
        if (engine != null) {
          String fallbackScript = resolveDefaultScriptForMenus(vnScene);
          String targetMenu = normalizeMenuId(target, "settings");
          Scene settingsScene = menuSceneFactory.createSettingsScene(engine, vnScene, fallbackScript, targetMenu);
          if (settingsScene != null) engine.scenes().push(settingsScene);
        }
        return true;
      }
      case "main_menu", "open_main_menu", "menu_main" -> {
        if (engine != null) {
          String fallbackScript = resolveDefaultScriptForMenus(vnScene);
          Scene mainScene = menuSceneFactory.createMainMenuScene(engine, vnScene, fallbackScript);
          if (mainScene != null) engine.scenes().push(mainScene);
        }
        return true;
      }
      case "open_menu", "menu_open" -> {
        if (target == null || target.isBlank()) {
          vnScene.getState().showHudMessage("Button target missing", 1200);
          return true;
        }
        if (engine != null) {
          String fallbackScript = resolveDefaultScriptForMenus(vnScene);
          Scene mainScene = menuSceneFactory.createMainMenuScene(engine, vnScene, fallbackScript, target);
          if (mainScene != null) engine.scenes().push(mainScene);
        }
        return true;
      }
      case "quit", "quit_game", "close_game", "exit" -> {
        if (engine != null) {
          String fallbackScript = resolveDefaultScriptForMenus(vnScene);
          String menuTarget = target == null || target.isBlank() ? "confirm_exit" : target;
          Scene mainScene = menuSceneFactory.createMainMenuScene(engine, vnScene, fallbackScript, menuTarget);
          if (mainScene != null) engine.scenes().push(mainScene);
        }
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
    return com.jvn.core.vn.ui.VnUiActionButtonActions.normalize(raw);
  }

  private static String resolveDefaultScriptForMenus(VnScene vnScene) {
    if (vnScene != null && vnScene.getState() != null) {
      String sourceScript = com.jvn.core.vn.VnEntryScriptResolver.normalizeScriptKey(vnScene.getState().getSourceScriptName());
      if (sourceScript != null) return sourceScript;
    }
    String resolved = com.jvn.core.vn.VnEntryScriptResolver.resolveEntryScript(null, null);
    if (resolved != null) return resolved;
    return "story/prologue.vns";
  }

  private static String normalizeMenuId(String raw, String fallback) {
    if (raw == null) return fallback;
    String value = raw.trim();
    return value.isEmpty() ? fallback : value;
  }
}
