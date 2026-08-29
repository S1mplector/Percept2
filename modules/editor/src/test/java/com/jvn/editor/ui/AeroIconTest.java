package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvn.fx.testkit.FxToolkit;
import com.jvn.fx.testkit.FxToolkitExtension;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import javax.imageio.ImageIO;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FxToolkitExtension.class)
class AeroIconTest {
  private static final int PANEL_SHEET_WIDTH = 1080;
  private static final int PANEL_SHEET_HEIGHT = 620;
  private static final Set<AeroIcon.Kind> SIDEBAR_PANEL_ROLES = EnumSet.of(
      AeroIcon.Kind.PROJECT,
      AeroIcon.Kind.TRASHMAN,
      AeroIcon.Kind.STORY_MAP,
      AeroIcon.Kind.INSPECTOR,
      AeroIcon.Kind.DIAGNOSTICS,
      AeroIcon.Kind.LABEL_FLOW,
      AeroIcon.Kind.TIMELINE_OUTLINE,
      AeroIcon.Kind.ASSETS,
      AeroIcon.Kind.LAYOUT,
      AeroIcon.Kind.STORYBOARD,
      AeroIcon.Kind.LAYERS,
      AeroIcon.Kind.IMAGE_ATTRIBUTES,
      AeroIcon.Kind.LIGHTING,
      AeroIcon.Kind.VERSION_CONTROL,
      AeroIcon.Kind.PUPPETEER,
      AeroIcon.Kind.SCRIPT_EDITOR,
      AeroIcon.Kind.SETTINGS);

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

  @Test
  void sidebarPanelsUseBespokeWindows7ObjectsInsteadOfSymbolicFallbacks() throws Exception {
    onFxThread(() -> {
      for (AeroIcon.Kind kind : SIDEBAR_PANEL_ROLES) {
        AeroIcon icon = AeroIcon.of(kind, 22);
        assertTrue(icon.getChildren().getFirst().getStyleClass()
            .contains("jvn-sidebar-panel-bespoke-artwork"), kind + " must use panel artwork");
      }
      return null;
    });
  }

  @Test
  void sidebarPanelObjectsHaveDistinctReadableSilhouettes() throws Exception {
    Set<Long> artworkHashes = new HashSet<>();
    for (AeroIcon.Kind kind : SIDEBAR_PANEL_ROLES) {
      WritableImage image = onFxThread(() -> snapshot(kind, 30, 40));
      artworkHashes.add(pixelHash(image));
    }
    assertEquals(SIDEBAR_PANEL_ROLES.size(), artworkHashes.size(),
        "Every sidebar panel needs its own physical object metaphor");
  }

  @Test
  void rendersSidebarPanelContactSheetForReview() throws Exception {
    WritableImage image = onFxThread(AeroIconTest::renderSidebarPanelSheet);
    assertEquals(PANEL_SHEET_WIDTH, image.getWidth());
    assertEquals(PANEL_SHEET_HEIGHT, image.getHeight());

    String requestedOutput = System.getenv("JVN_SIDEBAR_PANEL_ICON_CONTACT_SHEET");
    if (requestedOutput != null && !requestedOutput.isBlank()) {
      Path output = Path.of(requestedOutput).toAbsolutePath().normalize();
      Files.createDirectories(output.getParent());
      ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", output.toFile());
      assertTrue(Files.size(output) > 10_000, "Contact sheet should contain rendered artwork");
    }
  }

  private static WritableImage snapshot(AeroIcon.Kind kind, double iconSize, int canvasSize) {
    AeroIcon icon = AeroIcon.of(kind, iconSize);
    StackPane root = new StackPane(icon);
    new Scene(root, canvasSize, canvasSize);
    root.applyCss();
    root.layout();
    return root.snapshot(null, new WritableImage(canvasSize, canvasSize));
  }

  private static WritableImage renderSidebarPanelSheet() {
    Label eyebrow = new Label("JVN EDITOR • WINDOWS 7 AERO ARTWORK");
    eyebrow.setStyle("-fx-text-fill: #d68a3d; -fx-font-size: 12px; -fx-font-weight: 800;");
    Label title = new Label("Sidebar Panel Icons");
    title.setStyle("-fx-text-fill: #f4f7fb; -fx-font-size: 27px; -fx-font-weight: 800;");
    Label subtitle = new Label(
        "17 distinct shell objects • shown at 22 px in-app size and 36 px review size");
    subtitle.setStyle("-fx-text-fill: #8997a6; -fx-font-size: 12px;");
    VBox heading = new VBox(3, eyebrow, title, subtitle);

    GridPane grid = new GridPane();
    grid.setHgap(10);
    grid.setVgap(10);
    int index = 0;
    for (AeroIcon.Kind kind : SIDEBAR_PANEL_ROLES) {
      StackPane smallWell = iconWell(AeroIcon.of(kind, 22), 42, 48);
      StackPane largeWell = iconWell(AeroIcon.of(kind, 36), 54, 48);
      HBox previews = new HBox(5, smallWell, largeWell);
      previews.setAlignment(Pos.CENTER_LEFT);

      Label name = new Label(displayName(kind));
      name.setMaxWidth(84);
      name.setWrapText(true);
      name.setStyle("-fx-text-fill: #e8edf3; -fx-font-size: 11px; -fx-font-weight: 700;");
      HBox cell = new HBox(8, previews, name);
      cell.setAlignment(Pos.CENTER_LEFT);
      cell.setPadding(new Insets(7));
      cell.setMinSize(196, 92);
      cell.setPrefSize(196, 92);
      cell.setStyle(
          "-fx-background-color: linear-gradient(to bottom, #1b2128, #11161b);"
              + "-fx-background-radius: 10; -fx-border-color: #34414e; -fx-border-radius: 10;");
      grid.add(cell, index % 5, index / 5);
      index++;
    }

    VBox root = new VBox(18, heading, grid);
    root.setPadding(new Insets(28));
    root.setStyle(
        "-fx-background-color: linear-gradient(to bottom right, #0e141a, #080c10);"
            + "-fx-font-family: 'Segoe UI', sans-serif;");
    root.setMinSize(PANEL_SHEET_WIDTH, PANEL_SHEET_HEIGHT);
    root.setPrefSize(PANEL_SHEET_WIDTH, PANEL_SHEET_HEIGHT);
    root.setMaxSize(PANEL_SHEET_WIDTH, PANEL_SHEET_HEIGHT);
    new Scene(root, PANEL_SHEET_WIDTH, PANEL_SHEET_HEIGHT, Color.web("#080c10"));
    root.applyCss();
    root.layout();
    return root.snapshot(null, new WritableImage(PANEL_SHEET_WIDTH, PANEL_SHEET_HEIGHT));
  }

  private static StackPane iconWell(AeroIcon icon, double width, double height) {
    StackPane well = new StackPane(icon);
    well.setMinSize(width, height);
    well.setPrefSize(width, height);
    well.setStyle(
        "-fx-background-color: linear-gradient(to bottom, #252d36, #151a20);"
            + "-fx-background-radius: 8; -fx-border-color: #465565; -fx-border-radius: 8;");
    return well;
  }

  private static String displayName(AeroIcon.Kind kind) {
    String[] words = kind.name().toLowerCase(Locale.ROOT).split("_");
    StringBuilder result = new StringBuilder();
    for (String word : words) {
      if (!result.isEmpty()) result.append(' ');
      result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
    }
    return result.toString();
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
