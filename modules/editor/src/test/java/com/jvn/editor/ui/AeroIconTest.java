package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvn.fx.testkit.FxToolkit;
import com.jvn.fx.testkit.FxToolkitExtension;
import java.util.HashSet;
import java.util.Set;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FxToolkitExtension.class)
class AeroIconTest {
  @Test
  void everySemanticKindRendersVisibleGlassAndGlyphPixels() throws Exception {
    for (AeroIcon.Kind kind : AeroIcon.Kind.values()) {
      WritableImage image = onFxThread(() -> {
        AeroIcon icon = AeroIcon.of(kind, 24);
        StackPane root = new StackPane(icon);
        new Scene(root, 32, 32);
        root.applyCss();
        root.layout();
        return root.snapshot(null, new WritableImage(32, 32));
      });
      assertTrue(nonTransparentPixels(image) > 80, kind + " should render visible artwork");
    }
  }

  @Test
  void supportedSizeRangeIsClampedWithoutChangingSemanticKind() throws Exception {
    AeroIcon small = onFxThread(() -> AeroIcon.of(AeroIcon.Kind.SETTINGS, 2));
    AeroIcon large = onFxThread(() -> AeroIcon.of(AeroIcon.Kind.SETTINGS, 200));

    assertEquals(14, small.iconSize());
    assertEquals(48, large.iconSize());
    assertEquals(AeroIcon.Kind.SETTINGS, small.kind());
  }

  @Test
  void projectActionFactoriesKeepWelcomeAndExplorerArtworkAligned() throws Exception {
    AeroIcon run = onFxThread(() -> AeroIcon.runProject(22));
    AeroIcon build = onFxThread(() -> AeroIcon.buildProject(22));

    assertEquals(AeroIcon.Kind.RUN, run.kind());
    assertEquals(AeroIcon.Kind.BUILD, build.kind());
    assertEquals(22, run.iconSize());
    assertEquals(22, build.iconSize());
  }

  @Test
  void vnsCommandsUseTheSameTransparentAeroButtonTreatment() throws Exception {
    for (AeroIcon.Kind kind : new AeroIcon.Kind[] {
        AeroIcon.Kind.VNS_RUN_LABEL,
        AeroIcon.Kind.VNS_RUN_CURSOR,
        AeroIcon.Kind.VNS_RUN_ENTRY,
        AeroIcon.Kind.VNS_SYMBOLS,
        AeroIcon.Kind.VNS_SNIPPET,
        AeroIcon.Kind.VNS_FIND,
        AeroIcon.Kind.VNS_COMMANDS,
        AeroIcon.Kind.VNS_WORD_WRAP,
        AeroIcon.Kind.VNS_DIFF,
        AeroIcon.Kind.VNS_DIAGNOSTICS,
        AeroIcon.Kind.VNS_PREVIEW
    }) {
      Button button = onFxThread(() -> {
        Button result = new Button();
        result.setGraphic(AeroIcon.of(kind, 30));
        StackPane root = new StackPane(result);
        new Scene(root, 48, 48);
        root.applyCss();
        root.layout();
        return result;
      });
      assertTrue(button.getStyleClass().contains("aero-icon-button"));
    }
  }

  @Test
  void vnsCommandsHaveDistinctPrimarySilhouettes() throws Exception {
    Set<Long> artworkHashes = new HashSet<>();
    for (AeroIcon.Kind kind : new AeroIcon.Kind[] {
        AeroIcon.Kind.VNS_RUN_LABEL,
        AeroIcon.Kind.VNS_RUN_CURSOR,
        AeroIcon.Kind.VNS_RUN_ENTRY,
        AeroIcon.Kind.VNS_SYMBOLS,
        AeroIcon.Kind.VNS_SNIPPET,
        AeroIcon.Kind.VNS_FIND,
        AeroIcon.Kind.VNS_COMMANDS,
        AeroIcon.Kind.VNS_WORD_WRAP,
        AeroIcon.Kind.VNS_DIFF,
        AeroIcon.Kind.VNS_DIAGNOSTICS,
        AeroIcon.Kind.VNS_PREVIEW
    }) {
      WritableImage image = onFxThread(() -> {
        AeroIcon icon = AeroIcon.of(kind, 30);
        StackPane root = new StackPane(icon);
        new Scene(root, 38, 38);
        root.applyCss();
        root.layout();
        return root.snapshot(null, new WritableImage(38, 38));
      });
      artworkHashes.add(pixelHash(image));
    }
    assertEquals(11, artworkHashes.size(), "Every VNS command needs a distinct readable silhouette");
  }

  @Test
  void semanticArtworkUsesRenderCachingForPanelMovement() throws Exception {
    AeroIcon icon = onFxThread(() -> AeroIcon.of(AeroIcon.Kind.PROJECT, 28));

    assertTrue(icon.isCache());
    assertTrue(icon.getChildren().getFirst().isCache());
  }

  private static int nonTransparentPixels(WritableImage image) {
    int count = 0;
    for (int y = 0; y < (int) image.getHeight(); y++) {
      for (int x = 0; x < (int) image.getWidth(); x++) {
        if (image.getPixelReader().getArgb(x, y) >>> 24 != 0) count++;
      }
    }
    return count;
  }

  private static long pixelHash(WritableImage image) {
    long hash = 0xcbf29ce484222325L;
    for (int y = 0; y < (int) image.getHeight(); y++) {
      for (int x = 0; x < (int) image.getWidth(); x++) {
        hash ^= image.getPixelReader().getArgb(x, y);
        hash *= 0x100000001b3L;
      }
    }
    return hash;
  }

  private static <T> T onFxThread(java.util.concurrent.Callable<T> work) throws Exception {
    return FxToolkit.runFx(work);
  }
}
