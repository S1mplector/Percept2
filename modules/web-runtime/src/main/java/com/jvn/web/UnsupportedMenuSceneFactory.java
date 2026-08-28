package com.jvn.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jvn.core.engine.Engine;
import com.jvn.core.scene.Scene;
import com.jvn.core.vn.VnScene;
import com.jvn.scenerender.input.MenuSceneFactory;

/**
 * Browser-target {@link MenuSceneFactory}. Its method bodies deliberately
 * never reference {@code com.jvn.core.menu.SaveMenuScene}/{@code
 * LoadMenuScene}/{@code SettingsScene}/{@code MainMenuScene}/{@code
 * PauseMenuScene}/{@code HistoryMenuScene}'s constructors, nor
 * {@code VnScene.quickSave()}/{@code quickLoad()} — those types/methods
 * transitively use JDK APIs ({@code ObjectInputStream}/{@code
 * ObjectOutputStream}, {@code StringJoiner}, {@code
 * BufferedReader.transferTo}) or unsupported reflection-adjacent APIs
 * ({@code ClassLoader.getResources}, via {@code HistoryMenuScene}'s
 * {@code MenuProfileLoader.loadWithDiagnostics()}) that TeaVM's classlib
 * does not support. Keeping those calls out of this factory's method
 * bodies keeps them unreachable from TeaVM's whole-program static analysis
 * when compiling {@code :web-runtime:generateJavaScript}, since {@code
 * SceneInputRouter} only calls through the {@code MenuSceneFactory}
 * interface and TeaVM only needs to consider the concrete implementation
 * actually wired into this module ({@code WebLauncher}).
 *
 * <p>Save/settings/main-menu/pause-menu/history UI and quick-save/quick-load
 * are out of scope for the current browser fixture (see {@code WebMain}'s
 * {@code dispatchAction} javadoc for the same exclusion applied to keyboard
 * actions); every scene-construction method here simply logs and returns
 * {@code null} rather than throwing, so an unexpected click on a button
 * wired to one of these actions degrades gracefully (the null return means
 * {@code SceneInputRouter} pushes nothing) instead of crashing the whole
 * page. {@code quickSave}/{@code quickLoad} likewise log and return
 * {@code false}, matching {@code VnScene.quickSave()}/{@code quickLoad()}'s
 * own "false means failed, show a HUD message" contract.</p>
 */
public final class UnsupportedMenuSceneFactory implements MenuSceneFactory {
  private static final Logger log = LoggerFactory.getLogger(UnsupportedMenuSceneFactory.class);

  @Override
  public Scene createSaveMenuScene(Engine engine, VnScene vnScene) {
    log.warn("Save menu is not supported in the browser build; ignoring request to open it.");
    return null;
  }

  @Override
  public Scene createSaveMenuScene(Engine engine, VnScene vnScene, String defaultScriptName) {
    log.warn("Save menu is not supported in the browser build; ignoring request to open it.");
    return null;
  }

  @Override
  public Scene createLoadMenuScene(Engine engine, VnScene vnScene, String defaultScriptName) {
    log.warn("Load menu is not supported in the browser build; ignoring request to open it.");
    return null;
  }

  @Override
  public Scene createSettingsScene(Engine engine, VnScene vnScene, String defaultScriptName, String menuId) {
    log.warn("Settings menu is not supported in the browser build; ignoring request to open it.");
    return null;
  }

  @Override
  public Scene createMainMenuScene(Engine engine, VnScene vnScene, String defaultScriptName) {
    log.warn("Main menu is not supported in the browser build; ignoring request to open it.");
    return null;
  }

  @Override
  public Scene createMainMenuScene(Engine engine, VnScene vnScene, String defaultScriptName, String menuId) {
    log.warn("Main menu is not supported in the browser build; ignoring request to open it.");
    return null;
  }

  @Override
  public Scene createPauseMenuScene(Engine engine, VnScene vnScene, String defaultScriptName) {
    log.warn("Pause menu is not supported in the browser build; ignoring request to open it.");
    return null;
  }

  @Override
  public Scene createHistoryMenuScene(Engine engine, VnScene vnScene) {
    log.warn("History menu is not supported in the browser build; ignoring request to open it.");
    return null;
  }

  @Override
  public boolean quickSave(VnScene vnScene) {
    log.warn("Quick save is not supported in the browser build; ignoring request.");
    return false;
  }

  @Override
  public boolean quickLoad(VnScene vnScene) {
    log.warn("Quick load is not supported in the browser build; ignoring request.");
    return false;
  }
}
