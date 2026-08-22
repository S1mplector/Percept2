package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvn.fx.testkit.FxToolkit;
import com.jvn.fx.testkit.FxToolkitExtension;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import javax.imageio.ImageIO;
import javafx.scene.Scene;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(FxToolkitExtension.class)
class GameBuildPublisherViewFxTest {
  @TempDir Path tempDir;

  @Test
  void validationActionsStayCompactAndWrapInNarrowLayouts() throws Exception {
    runFx(() -> {
      GameBuildPublisherView view = createView();
      Scene scene = style(new Scene(view, 760, 900));
      scene.getRoot().applyCss();
      scene.getRoot().layout();

      assertTrue(view.preflightButtonForTest().getGraphic() == null);
      assertTrue(view.dependencyScanButtonForTest().getGraphic() == null);
      assertTrue(view.preflightButtonForTest().minHeight(-1) <= 40);
      assertTrue(view.dependencyScanButtonForTest().minHeight(-1) <= 40);
      assertTrue(view.preflightButtonForTest().minWidth(-1) < 220);
      assertTrue(view.dependencyScanButtonForTest().minWidth(-1) < 220);

      view.validationActionsRowForTest().resize(220, 120);
      view.validationActionsRowForTest().layout();
      assertTrue(view.validationActionsRowForTest().prefHeight(220) > 50);
      return null;
    });
  }

  @Test
  void nativeModeControlsGuidanceVisibilityAndPlatformChecklist() throws Exception {
    runFx(() -> {
      GameBuildPublisherView view = createView();
      style(new Scene(view, 800, 900));

      view.selectPortableModeForTest();
      assertFalse(view.nativeReleaseBoxForTest().isManaged());
      assertFalse(view.nativeReleaseBoxForTest().isVisible());

      view.selectNativeModeForTest();
      assertTrue(view.nativeReleaseBoxForTest().isManaged());
      assertTrue(view.nativeReleaseBoxForTest().isVisible());
      assertTrue(view.nativeReleaseSummaryForTest().getText().contains("1."));
      assertTrue(view.nativeReleaseSummaryForTest().getText().contains("2."));
      assertTrue(view.nativeReleaseActionsRowForTest().getChildren().size() >= 5);
      return null;
    });
  }

  @Test
  void selectingPngGeneratesAndSelectsPlatformIcon() throws Exception {
    Path source = transparentPng(tempDir.resolve("game-icon.png"));
    runFx(() -> {
      GameBuildPublisherView view = createView();
      style(new Scene(view, 800, 900));
      view.installGameIcon(source.toFile());

      String configured = view.gameIconFieldForTest().getText();
      assertTrue(configured.startsWith("packaging/icon."));
      assertTrue(tempDir.resolve("game/" + configured).toFile().isFile());
      assertTrue(tempDir.resolve("game/packaging/icon-source.png").toFile().isFile());
      return null;
    });
  }

  private GameBuildPublisherView createView() throws Exception {
    Path workspace = Files.createDirectories(tempDir.resolve("workspace"));
    Path project = Files.createDirectories(tempDir.resolve("game"));
    Files.createDirectories(project.resolve("scripts/story"));
    Files.writeString(project.resolve("scripts/story/prologue.vns"), "@scene prologue\n");
    Files.writeString(project.resolve("jvn.project"), """
        name=UI Test Game
        version=1.0.0
        type=vn
        entryVns=scripts/story/prologue.vns
        runtime.ui=fx
        """);
    return new GameBuildPublisherView(workspace.toFile(), project.toFile(), request -> {});
  }

  private static Scene style(Scene scene) {
    var css = GameBuildPublisherView.class.getResource("/com/jvn/editor/editor.css");
    if (css != null) scene.getStylesheets().add(css.toExternalForm());
    return scene;
  }

  private static Path transparentPng(Path path) throws Exception {
    BufferedImage image = new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB);
    image.setRGB(256, 256, 0xffffaa00);
    ImageIO.write(image, "png", path.toFile());
    return path;
  }

  private static <T> T runFx(Callable<T> callable) throws Exception {
    return FxToolkit.runFx(callable);
  }
}
