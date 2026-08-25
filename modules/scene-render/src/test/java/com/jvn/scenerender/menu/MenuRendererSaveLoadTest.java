package com.jvn.scenerender.menu;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;

import org.junit.jupiter.api.Test;

import com.jvn.core.menu.SaveMenuScene;
import com.jvn.core.vn.save.VnSaveManager;
import com.jvn.scenerender.testkit.RecordingBlitter2D;

class MenuRendererSaveLoadTest {

  @Test
  void rendersNewSlotLabelForEmptySaveMenu() throws Exception {
    RecordingBlitter2D blitter = new RecordingBlitter2D();
    MenuRenderer renderer = new MenuRenderer(blitter, MenuTheme.defaults());
    VnSaveManager saveManager = new VnSaveManager(Files.createTempDirectory("jvn-save-menu-test").toString());
    SaveMenuScene scene = new SaveMenuScene(null, saveManager, null);

    renderer.renderSaveMenu(scene, 1280.0, 720.0);

    // Item 0 ("New Save...") is selected by default (SaveMenuScene.getSelected() == 0), and
    // drawMenuList's withPrefix() prepends MenuTheme's default itemSelectedPrefix ("> ") to the
    // currently-selected item's label before it reaches drawText — pre-existing, unmodified
    // behavior (see Task 5's identical adaptation for renderPauseMenu). So the drawn text is
    // "> New Save..." rather than a bare match; assert containment instead of equality.
    assertTrue(blitter.calls().stream()
        .anyMatch(c -> c.method().equals("drawText")
            && c.args().get(0) instanceof String s
            && s.contains(scene.getNewSlotLabel())));
  }
}
