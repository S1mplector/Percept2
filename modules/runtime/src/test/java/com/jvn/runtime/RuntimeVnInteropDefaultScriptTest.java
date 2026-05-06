package com.jvn.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.jvn.core.config.ApplicationConfig;
import com.jvn.core.engine.Engine;
import com.jvn.core.menu.MainMenuScene;
import com.jvn.core.menu.config.MenuActionType;
import com.jvn.core.scene.Scene;
import com.jvn.core.vn.VnErrorOverlay;
import com.jvn.core.vn.VnExternalCommand;
import com.jvn.core.vn.VnScenarioBuilder;
import com.jvn.core.vn.VnScene;

class RuntimeVnInteropDefaultScriptTest {

  @Test
  void menuMainWithoutScriptUsesCurrentSceneSourceScript() {
    Engine engine = new Engine(ApplicationConfig.builder().build());
    RuntimeVnInterop interop = new RuntimeVnInterop(engine);
    String currentScript = "story/custom_route.vns";

    VnScene current = new VnScene(new VnScenarioBuilder("route").label("start").end().build());
    current.getState().setSourceScriptName(currentScript);

    interop.handle(new VnExternalCommand("menu", "main"), current);

    MainMenuScene mainMenu = assertInstanceOf(MainMenuScene.class, engine.scenes().peek());
    activateNewGame(mainMenu);

    Scene top = engine.scenes().peek();
    VnScene started = assertInstanceOf(VnScene.class, top);
    assertEquals(currentScript, started.getState().getSourceScriptName());
  }

  @Test
  void menuMainWithExplicitScriptUsesExplicitScript() {
    Engine engine = new Engine(ApplicationConfig.builder().build());
    RuntimeVnInterop interop = new RuntimeVnInterop(engine);
    String explicitScript = "story/explicit_start.vns";

    VnScene current = new VnScene(new VnScenarioBuilder("route").label("start").end().build());
    current.getState().setSourceScriptName("story/original.vns");

    interop.handle(new VnExternalCommand("menu", "main " + explicitScript), current);

    MainMenuScene mainMenu = assertInstanceOf(MainMenuScene.class, engine.scenes().peek());
    activateNewGame(mainMenu);

    Scene top = engine.scenes().peek();
    VnScene started = assertInstanceOf(VnScene.class, top);
    assertEquals(explicitScript, started.getState().getSourceScriptName());
  }

  @Test
  void missingVnsBridgeScriptShowsInteropError() {
    Engine engine = new Engine(ApplicationConfig.builder().build());
    RuntimeVnInterop interop = new RuntimeVnInterop(engine);
    VnScene current = new VnScene(new VnScenarioBuilder("route").label("start").end().build());

    interop.handle(new VnExternalCommand("vns", "replace story/does-not-exist.vns"), current);

    VnErrorOverlay error = current.getActiveError();
    assertNotNull(error);
    assertEquals(VnErrorOverlay.ErrorType.INTEROP_ERROR, error.getType());
    assertTrue(error.getMessage().contains("vns replace story/does-not-exist.vns failed"));
    assertTrue(current.getState().getHudMessage().contains("vns replace story/does-not-exist.vns failed"));
  }

  @Test
  void missingJesBridgeScriptShowsInteropError() {
    Engine engine = new Engine(ApplicationConfig.builder().build());
    RuntimeVnInterop interop = new RuntimeVnInterop(engine);
    VnScene current = new VnScene(new VnScenarioBuilder("route").label("start").end().build());

    interop.handle(new VnExternalCommand("jes", "push scripts/missing.jes"), current);

    VnErrorOverlay error = current.getActiveError();
    assertNotNull(error);
    assertEquals(VnErrorOverlay.ErrorType.INTEROP_ERROR, error.getType());
    assertTrue(error.getMessage().contains("jes push scripts/missing.jes failed"));
    assertTrue(current.getState().getHudMessage().contains("jes push scripts/missing.jes failed"));
  }

  private static void activateNewGame(MainMenuScene mainMenu) {
    int newGameIndex = -1;
    for (int i = 0; i < mainMenu.getItemCount(); i++) {
      var item = mainMenu.getMenuItemSpec(i);
      if (item == null || !item.enabled() || item.action() == null) continue;
      if (item.action().type() == MenuActionType.NEW_GAME) {
        newGameIndex = i;
        break;
      }
    }
    assertTrue(newGameIndex >= 0, "Main menu profile must expose an enabled NEW_GAME item");
    mainMenu.setSelected(newGameIndex);
    mainMenu.activateSelected();
  }
}
