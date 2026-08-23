package com.jvn.editor.ui.actioneditor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvn.fx.testkit.FxToolkit;
import com.jvn.fx.testkit.FxToolkitExtension;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FxToolkitExtension.class)
class EntitySelectorFxTest {
  @Test
  void supportsMultipleSelectedEntities() throws Exception {
    List<String> selected = runFx(() -> {
      AnimationProject project = new AnimationProject();
      project.getOrCreateTrack("body");
      project.getOrCreateTrack("eyes");
      project.getOrCreateTrack("mouth");

      EntitySelector selector = new EntitySelector();
      selector.refresh(project);
      // Mirrors Puppeteer's timeline acknowledgement after a picker selection.
      selector.setOnSelectionChanged((name, group) -> {
        if (group) selector.selectGroup(name);
        else selector.selectEntity(name);
      });
      selector.selectEntities(List.of("body", "eyes", "mouth"));
      return selector.getSelectedEntityNames();
    });

    assertEquals(List.of("body", "eyes", "mouth"), selected);
  }

  private static <T> T runFx(java.util.concurrent.Callable<T> callable) throws Exception {
    T result = FxToolkit.runFx(callable);
    assertTrue(result != null);
    return result;
  }
}
