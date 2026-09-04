package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvn.fx.testkit.FxToolkit;
import com.jvn.fx.testkit.FxToolkitExtension;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
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
class SidebarToolIconTest {
  private static final int SHEET_WIDTH = 1260;
  private static final int SHEET_HEIGHT = 850;
  private static final Set<SidebarToolIcon.Kind> BESPOKE_WINDOWS_7_ROLES = EnumSet.of(
      SidebarToolIcon.Kind.PREVIOUS,
      SidebarToolIcon.Kind.NEXT,
      SidebarToolIcon.Kind.REMOVE,
      SidebarToolIcon.Kind.UP,
      SidebarToolIcon.Kind.DOWN,
      SidebarToolIcon.Kind.SORT,
      SidebarToolIcon.Kind.CLOSE,
      SidebarToolIcon.Kind.UNDO,
      SidebarToolIcon.Kind.REDO,
      SidebarToolIcon.Kind.LIST,
      SidebarToolIcon.Kind.SEARCH,
      SidebarToolIcon.Kind.GRID,
      SidebarToolIcon.Kind.PALETTE,
      SidebarToolIcon.Kind.DOWNLOAD,
      SidebarToolIcon.Kind.SAVE,
      SidebarToolIcon.Kind.EXPAND,
      SidebarToolIcon.Kind.LINK,
      SidebarToolIcon.Kind.POLYGON,
      SidebarToolIcon.Kind.FREEHAND,
      SidebarToolIcon.Kind.SHOW,
      SidebarToolIcon.Kind.HIDE,
      SidebarToolIcon.Kind.DELETE,
      SidebarToolIcon.Kind.TIMELINE,
      SidebarToolIcon.Kind.AUTO,
      SidebarToolIcon.Kind.LABEL,
      SidebarToolIcon.Kind.MEMORY,
      SidebarToolIcon.Kind.OPEN,
      SidebarToolIcon.Kind.SPEECH,
      SidebarToolIcon.Kind.APPLY,
      SidebarToolIcon.Kind.HOME,
      SidebarToolIcon.Kind.COPY,
      SidebarToolIcon.Kind.SELECT,
      SidebarToolIcon.Kind.PERSON,
      SidebarToolIcon.Kind.IMAGE,
      SidebarToolIcon.Kind.EDIT,
      SidebarToolIcon.Kind.SPARKLES,
      SidebarToolIcon.Kind.MOVIE,
      SidebarToolIcon.Kind.LOCATE,
      SidebarToolIcon.Kind.DOCK);

  @Test
  void everyUtilityRoleRendersVisibleDimensionalArtwork() throws Exception {
    for (SidebarToolIcon.Kind kind : SidebarToolIcon.Kind.values()) {
      WritableImage image = FxToolkit.runFx(() -> {
        SidebarToolIcon icon = SidebarToolIcon.of(kind);
        StackPane root = new StackPane(icon);
        new Scene(root, 32, 32);
        root.applyCss();
        root.layout();
        assertEquals(kind, icon.kind());
        assertEquals(20.0, icon.iconSize());
        return root.snapshot(null, new WritableImage(32, 32));
      });
      assertTrue(nonTransparentPixels(image) > 35, kind + " should render visible artwork");
    }
  }

  @Test
  void utilityButtonsUseTransparentAeroHoverTreatment() throws Exception {
    for (SidebarToolIcon.Kind kind : SidebarToolIcon.Kind.values()) {
      Button button = FxToolkit.runFx(() -> {
        Button result = new Button("", SidebarToolIcon.of(kind));
        StackPane root = new StackPane(result);
        new Scene(root, 38, 38);
        root.applyCss();
        root.layout();
        return result;
      });
      assertTrue(button.getStyleClass().contains("aero-icon-button"));
    }
  }

  @Test
  void requestedProfessionalRolesUseBespokeWindows7Artwork() throws Exception {
    FxToolkit.runFx(() -> {
      for (SidebarToolIcon.Kind kind : BESPOKE_WINDOWS_7_ROLES) {
        SidebarToolIcon icon = SidebarToolIcon.of(kind);
        assertTrue(icon.getChildren().getFirst().getStyleClass()
            .contains("jvn-sidebar-tool-bespoke-artwork"), kind + " must not fall back to CssIcon");
      }
      return null;
    });
  }

  @Test
  void rendersLabeledSidebarUtilityContactSheetForReview() throws Exception {
    WritableImage image = FxToolkit.runFx(SidebarToolIconTest::renderSheet);
    assertEquals(SHEET_WIDTH, image.getWidth());
    assertEquals(SHEET_HEIGHT, image.getHeight());
    assertTrue(nonTransparentPixels(image) > SHEET_WIDTH * SHEET_HEIGHT * 0.95);

    String requestedOutput = System.getenv("JVN_SIDEBAR_ICON_CONTACT_SHEET");
    if (requestedOutput != null && !requestedOutput.isBlank()) {
      Path output = Path.of(requestedOutput).toAbsolutePath().normalize();
      Files.createDirectories(output.getParent());
      ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", output.toFile());
      assertTrue(Files.size(output) > 10_000, "Contact sheet should contain rendered artwork");
    }
  }

  private static WritableImage renderSheet() {
    Label eyebrow = new Label("JVN EDITOR • WINDOWS 7 AERO ARTWORK");
    eyebrow.setStyle("-fx-text-fill: #d68a3d; -fx-font-size: 12px; -fx-font-weight: 800;");
    Label title = new Label("Sidebar Utility Icons");
    title.setStyle("-fx-text-fill: #f4f7fb; -fx-font-size: 27px; -fx-font-weight: 800;");
    Label subtitle = new Label(
        SidebarToolIcon.Kind.values().length
            + " semantic roles • glass shell objects • chrome hardware • transparent glow");
    subtitle.setStyle("-fx-text-fill: #8997a6; -fx-font-size: 12px;");
    VBox heading = new VBox(3, eyebrow, title, subtitle);

    GridPane grid = new GridPane();
    grid.setHgap(9);
    grid.setVgap(9);
    for (int index = 0; index < SidebarToolIcon.Kind.values().length; index++) {
      SidebarToolIcon.Kind kind = SidebarToolIcon.Kind.values()[index];
      SidebarToolIcon icon = SidebarToolIcon.of(kind, nullSafeAccent(kind), 34);
      StackPane iconWell = new StackPane(icon);
      iconWell.setMinSize(52, 48);
      iconWell.setPrefSize(52, 48);
      iconWell.setStyle(
          "-fx-background-color: linear-gradient(to bottom, #252d36, #151a20);"
              + "-fx-background-radius: 9; -fx-border-color: #465565; -fx-border-radius: 9;");

      Label name = new Label(displayName(kind));
      name.setMaxWidth(92);
      name.setWrapText(true);
      name.setStyle("-fx-text-fill: #e8edf3; -fx-font-size: 11px; -fx-font-weight: 700;");
      HBox cell = new HBox(9, iconWell, name);
      cell.setAlignment(Pos.CENTER_LEFT);
      cell.setPadding(new Insets(8));
      cell.setMinSize(166, 86);
      cell.setPrefSize(166, 86);
      cell.setStyle(
          "-fx-background-color: linear-gradient(to bottom, #1b2128, #11161b);"
              + "-fx-background-radius: 10; -fx-border-color: #34414e; -fx-border-radius: 10;");
      grid.add(cell, index % 7, index / 7);
    }

    VBox root = new VBox(18, heading, grid);
    root.setPadding(new Insets(28));
    root.setStyle(
        "-fx-background-color: linear-gradient(to bottom right, #0e141a, #080c10);"
            + "-fx-font-family: 'Segoe UI', sans-serif;");
    root.setMinSize(SHEET_WIDTH, SHEET_HEIGHT);
    root.setPrefSize(SHEET_WIDTH, SHEET_HEIGHT);
    root.setMaxSize(SHEET_WIDTH, SHEET_HEIGHT);
    new Scene(root, SHEET_WIDTH, SHEET_HEIGHT, Color.web("#080c10"));
    root.applyCss();
    root.layout();
    return root.snapshot(null, new WritableImage(SHEET_WIDTH, SHEET_HEIGHT));
  }

  private static String nullSafeAccent(SidebarToolIcon.Kind kind) {
    return switch (kind) {
      case ADD, APPLY, HOME -> "#62d56e";
      case REMOVE, CLOSE, DELETE, HIDE -> "#e16b78";
      case OPEN, PALETTE, WARNING, SPARKLES -> "#e7ad55";
      case UNDO, FREEHAND, POLYGON -> "#ae91e8";
      case REDO, EDIT -> "#58bce8";
      case SORT, AUTO -> "#efa15b";
      default -> "#69c6eb";
    };
  }

  private static String displayName(SidebarToolIcon.Kind kind) {
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
    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        if (image.getPixelReader().getArgb(x, y) >>> 24 != 0) count++;
      }
    }
    return count;
  }
}
