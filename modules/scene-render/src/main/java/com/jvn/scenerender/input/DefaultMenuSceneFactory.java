package com.jvn.scenerender.input;

import com.jvn.core.engine.Engine;
import com.jvn.core.scene.Scene;
import com.jvn.core.vn.VnScene;

/**
 * Real {@link MenuSceneFactory} implementation — constructs the actual
 * {@code SaveMenuScene}/{@code LoadMenuScene}/{@code SettingsScene}/
 * {@code MainMenuScene}/{@code PauseMenuScene} instances, with the exact
 * construction logic previously inlined in {@link SceneInputRouter}. Used by
 * every platform that can actually run the JDK save/settings persistence
 * machinery (currently: desktop/{@code FxLauncher}).
 */
public class DefaultMenuSceneFactory implements MenuSceneFactory {

  @Override
  public Scene createSaveMenuScene(Engine engine, VnScene vnScene) {
    return new com.jvn.core.menu.SaveMenuScene(
        engine, new com.jvn.core.vn.save.VnSaveManager(), vnScene);
  }

  @Override
  public Scene createSaveMenuScene(Engine engine, VnScene vnScene, String defaultScriptName) {
    return new com.jvn.core.menu.SaveMenuScene(
        engine, new com.jvn.core.vn.save.VnSaveManager(), vnScene, defaultScriptName);
  }

  @Override
  public Scene createLoadMenuScene(Engine engine, VnScene vnScene, String defaultScriptName) {
    return new com.jvn.core.menu.LoadMenuScene(
        engine,
        new com.jvn.core.vn.save.VnSaveManager(),
        defaultScriptName,
        vnScene.getState().getSettings(),
        vnScene.getAudioFacade(),
        vnScene.getPersistenceBackend()
    );
  }

  @Override
  public Scene createSettingsScene(Engine engine, VnScene vnScene, String defaultScriptName, String menuId) {
    com.jvn.core.input.ActionBindingProfile profile =
        com.jvn.core.input.ActionBindingProfile.deserialize(
            vnScene.getState().getSettings().getInputProfileSerialized());
    return new com.jvn.core.menu.SettingsScene(
        engine,
        new com.jvn.core.vn.save.VnSaveManager(),
        defaultScriptName,
        vnScene.getState().getSettings(),
        vnScene.getAudioFacade(),
        vnScene.getPersistenceBackend(),
        profile,
        menuId
    );
  }

  @Override
  public Scene createMainMenuScene(Engine engine, VnScene vnScene, String defaultScriptName) {
    return new com.jvn.core.menu.MainMenuScene(
        engine,
        vnScene.getState().getSettings(),
        new com.jvn.core.vn.save.VnSaveManager(),
        defaultScriptName,
        vnScene.getAudioFacade(),
        vnScene.getPersistenceBackend()
    );
  }

  @Override
  public Scene createMainMenuScene(Engine engine, VnScene vnScene, String defaultScriptName, String menuId) {
    return new com.jvn.core.menu.MainMenuScene(
        engine,
        vnScene.getState().getSettings(),
        new com.jvn.core.vn.save.VnSaveManager(),
        defaultScriptName,
        vnScene.getAudioFacade(),
        vnScene.getPersistenceBackend(),
        menuId
    );
  }

  @Override
  public Scene createPauseMenuScene(Engine engine, VnScene vnScene, String defaultScriptName) {
    return new com.jvn.core.menu.PauseMenuScene(
        engine, vnScene,
        new com.jvn.core.vn.save.VnSaveManager(),
        defaultScriptName,
        vnScene.getAudioFacade()
    );
  }

  @Override
  public Scene createHistoryMenuScene(Engine engine, VnScene vnScene) {
    return new com.jvn.core.menu.HistoryMenuScene(engine, vnScene);
  }

  @Override
  public boolean quickSave(VnScene vnScene) {
    return vnScene.quickSave();
  }

  @Override
  public boolean quickLoad(VnScene vnScene) {
    return vnScene.quickLoad();
  }
}
