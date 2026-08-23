package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvn.fx.testkit.FxToolkit;
import com.jvn.fx.testkit.FxToolkitExtension;
import java.net.URL;
import java.util.List;
import java.util.concurrent.Callable;
import javafx.css.PseudoClass;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.MenuButton;
import javafx.scene.control.SplitMenuButton;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FxToolkitExtension.class)
class SidebarToolButtonStyleFxTest {
  private static final PseudoClass HOVER = PseudoClass.getPseudoClass("hover");

  @Test
  void everySidebarButtonFamilyUsesTheSharedAeroStatesInBothThemes() throws Exception {
    for (String stylesheet : List.of("editor-light.css", "editor.css")) {
      runFx(() -> {
        Button button = new Button("Run");
        ToggleButton toggle = new ToggleButton("Selected");
        MenuButton menu = new MenuButton("Options");
        SplitMenuButton split = new SplitMenuButton();
        split.setText("More");

        VBox root = new VBox(button, toggle, menu, split);
        root.getStyleClass().add("sidebar-tool-root");
        Scene scene = new Scene(root, 420, 180);
        URL css = SidebarToolButtonStyleFxTest.class.getResource("/com/jvn/editor/" + stylesheet);
        assertTrue(css != null, stylesheet + " should be available");
        scene.getStylesheets().add(css.toExternalForm());
        root.applyCss();
        root.layout();

        for (ButtonBase control : List.of(button, toggle, menu, split)) {
          assertTrue(hasTransparentRestingSurface(control), control.getClass().getSimpleName());
          control.pseudoClassStateChanged(HOVER, true);
          root.applyCss();
          assertTrue(hasVisibleGlassSurface(control), control.getClass().getSimpleName());
          control.pseudoClassStateChanged(HOVER, false);
        }

        toggle.setSelected(true);
        root.applyCss();
        assertTrue(hasVisibleGlassSurface(toggle), "selected toggles should stay illuminated");

        button.setDisable(true);
        root.applyCss();
        assertEquals(0.42, button.getOpacity(), 0.001);
        return null;
      });
    }
  }

  private static boolean hasTransparentRestingSurface(ButtonBase button) {
    if (button.getBackground() == null || button.getBackground().getFills().isEmpty()) return true;
    return button.getBackground().getFills().stream().allMatch(fill ->
        fill.getFill() instanceof Color color && color.getOpacity() == 0.0);
  }

  private static boolean hasVisibleGlassSurface(ButtonBase button) {
    assertFalse(button.getBackground() == null || button.getBackground().getFills().isEmpty());
    return button.getBackground().getFills().stream().anyMatch(fill ->
        !(fill.getFill() instanceof Color color) || color.getOpacity() > 0.0);
  }

  private static <T> T runFx(Callable<T> callable) throws Exception {
    return FxToolkit.runFx(callable);
  }
}
