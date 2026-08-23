package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvn.fx.testkit.FxToolkit;
import com.jvn.fx.testkit.FxToolkitExtension;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import javax.imageio.ImageIO;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FxToolkitExtension.class)
class VersionControlIconContactSheetTest {
  private static final int SHEET_WIDTH = 1060;
  private static final int SHEET_HEIGHT = 620;

  @Test
  void rendersLabeledVersionControlContactSheet() throws Exception {
    WritableImage image = FxToolkit.runFx(VersionControlIconContactSheetTest::renderSheet);
    assertEquals(SHEET_WIDTH, image.getWidth());
    assertEquals(SHEET_HEIGHT, image.getHeight());
    assertTrue(nonTransparentPixels(image) > SHEET_WIDTH * SHEET_HEIGHT * 0.95);

    String requestedOutput = System.getenv("JVN_VCS_ICON_CONTACT_SHEET");
    if (requestedOutput != null && !requestedOutput.isBlank()) {
      Path output = Path.of(requestedOutput).toAbsolutePath().normalize();
      Files.createDirectories(output.getParent());
      ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", output.toFile());
      assertTrue(Files.size(output) > 10_000, "Contact sheet should contain rendered artwork");
    }
  }

  private static WritableImage renderSheet() {
    List<IconEntry> entries = new ArrayList<>();
    entries.add(new IconEntry("Refresh", () -> RefreshIcon.of(36)));
    for (VersionControlIcon.Kind kind : VersionControlIcon.Kind.values()) {
      entries.add(new IconEntry(displayName(kind), () -> VersionControlIcon.of(kind, 36)));
    }

    Label eyebrow = new Label("JVN EDITOR • WINDOWS 7 AERO ARTWORK");
    eyebrow.setStyle("-fx-text-fill: #d68a3d; -fx-font-size: 12px; -fx-font-weight: 800;");
    Label title = new Label("Version Control Utility Icons");
    title.setStyle("-fx-text-fill: #f4f7fb; -fx-font-size: 27px; -fx-font-weight: 800;");
    Label subtitle = new Label(
        entries.size() + " production vector roles • glass highlights • chrome edges • semantic color");
    subtitle.setStyle("-fx-text-fill: #8997a6; -fx-font-size: 12px;");
    VBox heading = new VBox(3, eyebrow, title, subtitle);

    GridPane grid = new GridPane();
    grid.setHgap(12);
    grid.setVgap(12);
    grid.setAlignment(Pos.TOP_LEFT);
    for (int index = 0; index < entries.size(); index++) {
      IconEntry entry = entries.get(index);
      Region icon = entry.factory().get();
      StackPane iconWell = new StackPane(icon);
      iconWell.setMinSize(64, 58);
      iconWell.setPrefSize(64, 58);
      iconWell.setMaxSize(64, 58);
      iconWell.setStyle(
          "-fx-background-color: linear-gradient(to bottom, #252d36, #151a20);"
              + "-fx-background-radius: 9; -fx-border-color: #465565; -fx-border-radius: 9;"
              + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.55), 7, 0.2, 0, 2);");

      Label name = new Label(entry.label());
      name.setMaxWidth(112);
      name.setWrapText(true);
      name.setAlignment(Pos.CENTER_LEFT);
      name.setStyle("-fx-text-fill: #e8edf3; -fx-font-size: 12px; -fx-font-weight: 700;");
      VBox copy = new VBox(2, name);
      copy.setAlignment(Pos.CENTER_LEFT);
      StackPane.setAlignment(copy, Pos.CENTER_LEFT);

      javafx.scene.layout.HBox cell = new javafx.scene.layout.HBox(11, iconWell, copy);
      cell.setAlignment(Pos.CENTER_LEFT);
      cell.setPadding(new Insets(10));
      cell.setMinSize(190, 92);
      cell.setPrefSize(190, 92);
      cell.setMaxSize(190, 92);
      cell.setStyle(
          "-fx-background-color: linear-gradient(to bottom, #1b2128, #11161b);"
              + "-fx-background-radius: 11; -fx-border-color: #34414e; -fx-border-radius: 11;"
              + "-fx-border-width: 1;");
      grid.add(cell, index % 5, index / 5);
    }

    VBox root = new VBox(20, heading, grid);
    root.setPadding(new Insets(28));
    root.setStyle(
        "-fx-background-color: linear-gradient(to bottom right, #0e141a, #080c10);"
            + "-fx-font-family: 'Segoe UI', 'Inter', sans-serif;");
    root.setMinSize(SHEET_WIDTH, SHEET_HEIGHT);
    root.setPrefSize(SHEET_WIDTH, SHEET_HEIGHT);
    root.setMaxSize(SHEET_WIDTH, SHEET_HEIGHT);
    Scene scene = new Scene(root, SHEET_WIDTH, SHEET_HEIGHT, Color.web("#080c10"));
    root.applyCss();
    root.layout();
    return root.snapshot(null, new WritableImage(SHEET_WIDTH, SHEET_HEIGHT));
  }

  private static String displayName(VersionControlIcon.Kind kind) {
    String[] words = kind.name().toLowerCase(Locale.ROOT).split("_");
    StringBuilder label = new StringBuilder();
    for (String word : words) {
      if (!label.isEmpty()) label.append(' ');
      label.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
    }
    return label.toString();
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

  private record IconEntry(String label, Supplier<Region> factory) {}
}
