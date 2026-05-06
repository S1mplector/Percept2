package com.jvn.editor.ui;

import javafx.geometry.Pos;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

/** Clean, background-free metallic JVN wordmark shared by editor chrome, launcher, and splashes. */
public final class MetallicJvnLogo extends StackPane {
  public MetallicJvnLogo(double width, double height) {
    double safeWidth = Math.max(72.0, width);
    double safeHeight = Math.max(36.0, height);
    double fontSize = Math.max(30.0, safeHeight * 0.76);

    Text wordmark = new Text("JVN");
    wordmark.setFont(Font.font("System", FontWeight.BLACK, fontSize));
    wordmark.setSmooth(true);
    wordmark.setFill(new LinearGradient(
        0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
        new Stop(0.0, Color.web("#ffffff")),
        new Stop(0.14, Color.web("#cfd8df")),
        new Stop(0.27, Color.web("#7c8791")),
        new Stop(0.42, Color.web("#f9fbfc")),
        new Stop(0.58, Color.web("#9da8b2")),
        new Stop(0.76, Color.web("#eef3f7")),
        new Stop(1.0, Color.web("#59636d"))));
    wordmark.setStroke(null);
    wordmark.setStrokeWidth(0);
    DropShadow shadow = new DropShadow();
    shadow.setRadius(Math.max(4.0, fontSize * 0.14));
    shadow.setOffsetY(Math.max(2.0, fontSize * 0.05));
    shadow.setColor(Color.web("#000000", 0.48));
    wordmark.setEffect(shadow);

    getChildren().add(wordmark);
    setAlignment(Pos.CENTER);
    setMinSize(safeWidth, safeHeight);
    setPrefSize(safeWidth, safeHeight);
    setMaxSize(safeWidth, safeHeight);
    setStyle("-fx-background-color: transparent;");
  }
}
