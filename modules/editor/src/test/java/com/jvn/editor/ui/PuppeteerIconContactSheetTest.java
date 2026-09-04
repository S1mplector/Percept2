package com.jvn.editor.ui;

import com.jvn.fx.testkit.FxToolkit;
import com.jvn.fx.testkit.FxToolkitExtension;
import java.nio.file.Path;
import java.util.Locale;
import javax.imageio.ImageIO;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FxToolkitExtension.class)
class PuppeteerIconContactSheetTest {
  @Test
  void render() throws Exception {
    String requested = System.getenv("JVN_PUPPETEER_ICON_CONTACT_SHEET");
    if (requested == null || requested.isBlank()) return;
    WritableImage image = FxToolkit.runFx(() -> {
      GridPane grid = new GridPane();
      grid.setHgap(8);
      grid.setVgap(8);
      PuppeteerAeroIcon.Kind[] kinds = PuppeteerAeroIcon.Kind.values();
      for (int i = 0; i < kinds.length; i++) {
        PuppeteerAeroIcon.Kind kind = kinds[i];
        StackPane well = new StackPane(PuppeteerAeroIcon.of(kind, 28));
        well.setMinSize(52, 48);
        well.setStyle("-fx-background-color:#202a33;-fx-background-radius:8;-fx-border-color:#4b5d6d;-fx-border-radius:8;");
        Label label = new Label(title(kind.name()));
        label.setWrapText(true);
        label.setMaxWidth(130);
        label.setStyle("-fx-text-fill:#edf3f8;-fx-font-size:10px;-fx-font-weight:700;");
        VBox cell = new VBox(3, well, label);
        cell.setAlignment(Pos.TOP_CENTER);
        cell.setPadding(new Insets(6));
        cell.setMinSize(160, 82);
        cell.setStyle("-fx-background-color:#12191f;-fx-background-radius:9;-fx-border-color:#33414d;-fx-border-radius:9;");
        grid.add(cell, i % 7, i / 7);
      }
      Label heading = new Label("Puppeteer • Windows 7 Semantic Shells");
      heading.setStyle("-fx-text-fill:#f5f8fb;-fx-font-size:26px;-fx-font-weight:800;");
      VBox root = new VBox(16, heading, grid);
      root.setPadding(new Insets(26));
      root.setStyle("-fx-background-color:#090e13;-fx-font-family:'Segoe UI';");
      root.setMinSize(1240, 730);
      new Scene(root, 1240, 730, Color.web("#090e13"));
      root.applyCss();
      root.layout();
      return root.snapshot(null, new WritableImage(1240, 730));
    });
    ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", Path.of(requested).toFile());
  }

  private static String title(String value) {
    StringBuilder result = new StringBuilder();
    for (String word : value.toLowerCase(Locale.ROOT).split("_")) {
      if (!result.isEmpty()) result.append(' ');
      result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
    }
    return result.toString();
  }
}
