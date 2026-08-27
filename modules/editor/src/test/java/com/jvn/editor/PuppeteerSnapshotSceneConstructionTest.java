package com.jvn.editor;

import java.lang.reflect.Method;

import com.jvn.core.scene2d.Entity2D;
import com.jvn.editor.ui.PuppeteerLauncherPanel;
import com.jvn.scripting.jes.runtime.JesScene2D;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Verifies that resolved cursor state survives the final handoff into Puppeteer's JES scene. */
class PuppeteerSnapshotSceneConstructionTest {

  @Test
  void customPositionAndZOrderReachTheLaunchScene() throws Exception {
    String source = """
        @charimg heart_effect pulse assets/effects/heart_attack.png
        @position full_screen 0.5 1.0
        [show heart_effect full_screen pulse slot=heart z=100]
        """;
    PuppeteerLauncherPanel.SceneSnapshot snapshot = PuppeteerLauncherPanel.resolveSnapshot(source, 2);

    JesScene2D scene = buildScene(snapshot);
    Entity2D prop = scene.find("heart_effect");

    assertNotNull(prop);
    assertEquals(960.0, prop.getX(), 1e-9);
    assertEquals(1080.0, prop.getY(), 1e-9);
    assertEquals(100.0, prop.getZ(), 1e-9);
  }

  @Test
  void multiLayerCharImgCreatesEveryPuppeteerLayerTarget() throws Exception {
    String source = """
        @charimg hero battle assets/hero/body.png | assets/hero/eyes.png | assets/hero/mouth.png
        [show hero center battle]
        """;

    JesScene2D scene = buildScene(PuppeteerLauncherPanel.resolveSnapshot(source, 1));

    assertNotNull(scene.find("hero_body"));
    assertNotNull(scene.find("hero_eyes"));
    assertNotNull(scene.find("hero_mouth"));
  }

  private static JesScene2D buildScene(PuppeteerLauncherPanel.SceneSnapshot snapshot) throws Exception {
    EditorApp app = new EditorApp();
    Method method = EditorApp.class.getDeclaredMethod(
        "buildSceneFromSnapshot", PuppeteerLauncherPanel.SceneSnapshot.class);
    method.setAccessible(true);
    return (JesScene2D) method.invoke(app, snapshot);
  }
}
