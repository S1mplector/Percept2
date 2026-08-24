package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.assets.ClasspathAssetManager;
import com.jvn.core.assets.FilesystemAssetManager;
import com.jvn.core.assets.OverlayAssetManager;
import com.jvn.core.vn.DefaultVnInterop;
import com.jvn.core.vn.VnCharacterSceneAccessor;
import com.jvn.core.vn.VnNode;
import com.jvn.core.vn.VnNodeType;
import com.jvn.core.vn.VnScenario;
import com.jvn.core.vn.VnScenarioLoader;
import com.jvn.core.vn.VnScene;
import com.jvn.core.vn.text.TextParser;
import com.jvn.fx.testkit.FxToolkit;
import com.jvn.fx.testkit.FxToolkitExtension;
import com.jvn.fx.vn.VnRenderer;
import java.io.File;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Menu;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Property-driven offscreen VN review renderer.
 *
 * <p>The test is skipped during ordinary builds. Set {@code JVN_REVIEW_PROJECT_ROOT},
 * {@code JVN_REVIEW_OUTPUT}, and {@code JVN_REVIEW_TARGET} to render a full-resolution frame at
 * the first dialogue containing the requested text. {@code JVN_REVIEW_SCRIPT},
 * {@code JVN_REVIEW_WIDTH}, and {@code JVN_REVIEW_HEIGHT} are optional.</p>
 */
@ExtendWith(FxToolkitExtension.class)
class HeadlessVnReviewRenderTest {
  @Test
  void rendersRequestedDialogueWithRuleOfThirds() throws Exception {
    String projectSetting = setting("review.projectRoot", "JVN_REVIEW_PROJECT_ROOT", "");
    String outputSetting = setting("review.output", "JVN_REVIEW_OUTPUT", "");
    String target = setting("review.target", "JVN_REVIEW_TARGET", "");
    assumeTrue(!projectSetting.isBlank() && !outputSetting.isBlank() && !target.isBlank(),
        "Offscreen review render was not requested");

    Path projectRoot = Path.of(projectSetting).toAbsolutePath();
    File output = Path.of(outputSetting).toAbsolutePath().toFile();
    String script = setting("review.script", "JVN_REVIEW_SCRIPT", "scripts/story/chapter_1_demo.vns");
    int width = positiveInt(setting("review.width", "JVN_REVIEW_WIDTH", "1920"), 1920);
    int height = positiveInt(setting("review.height", "JVN_REVIEW_HEIGHT", "1080"), 1080);

    AssetCatalog.setDefaultManager(new OverlayAssetManager(
        new FilesystemAssetManager(projectRoot), new ClasspathAssetManager()));
    System.setProperty("jvn.assets.root", projectRoot.toString());
    System.setProperty("jvn.render.width", Integer.toString(width));
    System.setProperty("jvn.render.height", Integer.toString(height));

    VnScenario scenario = new VnScenarioLoader().load(script);
    VnScene vnScene = new VnScene(scenario);
    VnCharacterSceneAccessor timelineAccessor = new VnCharacterSceneAccessor();
    DefaultVnInterop interop = new DefaultVnInterop();
    interop.setSceneAccessor(timelineAccessor);
    vnScene.setInterop(interop);
    vnScene.onEnter();
    advanceToTarget(vnScene, target);

    VnNode targetNode = vnScene.getState().getCurrentNode();
    assertNotNull(targetNode);
    assertTrue(targetNode.getDialogue().getText().contains(target));
    vnScene.getState().updateCharacterAnimations(10_000);
    vnScene.getState().setTextRevealProgress(
        TextParser.plainLength(targetNode.getDialogue().getText()));

    VnRenderer[] rendererHolder = new VnRenderer[1];
    Canvas canvas = FxToolkit.runFx(() -> {
      Canvas result = new Canvas(width, height);
      VnRenderer renderer = new VnRenderer(result.getGraphicsContext2D());
      renderer.setProjectRoot(projectRoot.toFile());
      renderer.setTimelineAccessor(timelineAccessor);
      renderer.render(vnScene.getState(), scenario, width, height);
      rendererHolder[0] = renderer;
      return result;
    });

    // Backgrounds load asynchronously; layered character composition itself is blocking.
    Thread.sleep(1_000);

    WritableImage image = FxToolkit.runFx(() -> {
      rendererHolder[0].render(vnScene.getState(), scenario, width, height);

      Menu guideMenu = CompositionGuideOverlay.createMenu();
      boolean[] previousGuideState = new boolean[guideMenu.getItems().size()];
      for (int i = 0; i < guideMenu.getItems().size(); i++) {
        CheckMenuItem item = (CheckMenuItem) guideMenu.getItems().get(i);
        previousGuideState[i] = item.isSelected();
        item.setSelected(i == 0);
      }
      try {
        CompositionGuideOverlay guides = new CompositionGuideOverlay();
        guides.setVirtualResolution(width, height);
        StackPane root = new StackPane(canvas, guides);
        root.setPrefSize(width, height);
        new Scene(root, width, height);
        root.applyCss();
        root.layout();
        return root.snapshot(null, new WritableImage(width, height));
      } finally {
        for (int i = 0; i < guideMenu.getItems().size(); i++) {
          ((CheckMenuItem) guideMenu.getItems().get(i)).setSelected(previousGuideState[i]);
        }
      }
    });

    File parent = output.getParentFile();
    if (parent != null) parent.mkdirs();
    assertTrue(ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", output));
  }

  private static void advanceToTarget(VnScene scene, String target) {
    for (int attempts = 0; attempts < 10_000; attempts++) {
      VnNode node = scene.getState().getCurrentNode();
      if (node != null && node.getType() == VnNodeType.DIALOGUE
          && node.getDialogue() != null && node.getDialogue().getText().contains(target)) {
        return;
      }
      if (node != null && node.getType() == VnNodeType.DIALOGUE) scene.advance();
      else scene.update(16);
    }
    throw new AssertionError("Could not reach requested dialogue: " + target);
  }

  private static String setting(String property, String environment, String fallback) {
    String value = System.getProperty(property);
    if (value == null || value.isBlank()) value = System.getenv(environment);
    return value == null || value.isBlank() ? fallback : value;
  }

  private static int positiveInt(String value, int fallback) {
    try {
      int parsed = Integer.parseInt(value);
      return parsed > 0 ? parsed : fallback;
    } catch (Exception ignored) {
      return fallback;
    }
  }
}
