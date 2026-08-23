package com.jvn.editor.ui;

import javafx.scene.CacheHint;
import javafx.scene.Group;
import javafx.scene.control.ButtonBase;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.InnerShadow;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

/** Compact Windows 7-style vector artwork for the New Panel action buttons. */
public final class PanelChooserActionIcon extends Pane {
  public enum Kind {
    ADD_HERE,
    POP_OUT
  }

  private static final double SIZE = 20.0;
  private final Kind kind;
  private final Color glow;

  private PanelChooserActionIcon(Kind requestedKind) {
    kind = requestedKind == null ? Kind.ADD_HERE : requestedKind;
    setMinSize(SIZE, SIZE);
    setPrefSize(SIZE, SIZE);
    setMaxSize(SIZE, SIZE);
    setMouseTransparent(true);
    setCache(true);
    setCacheHint(CacheHint.SPEED);
    getStyleClass().addAll("jvn-fx-icon", "panel-chooser-action-icon");
    glow = kind == Kind.ADD_HERE ? Color.web("#69dc55") : Color.web("#62c7f2");

    Group artwork = switch (kind) {
      case ADD_HERE -> addHereArtwork();
      case POP_OUT -> popOutArtwork();
    };
    artwork.setEffect(new DropShadow(2.2, 0, 1.0, Color.rgb(0, 0, 0, 0.82)));
    getChildren().setAll(artwork);
    parentProperty().addListener((obs, oldParent, newParent) -> {
      if (newParent instanceof ButtonBase button) installButtonTreatment(button);
    });
  }

  public static PanelChooserActionIcon of(Kind kind) {
    return new PanelChooserActionIcon(kind);
  }

  public Kind kind() {
    return kind;
  }

  private void installButtonTreatment(ButtonBase button) {
    if (!button.getStyleClass().contains("aero-icon-button")) {
      button.getStyleClass().add("aero-icon-button");
    }
    button.hoverProperty().addListener((obs, wasHovered, hovered) -> updateGlow(button));
    button.pressedProperty().addListener((obs, wasPressed, pressed) -> updateGlow(button));
    updateGlow(button);
  }

  private void updateGlow(ButtonBase button) {
    if (button.isPressed()) {
      setScaleX(0.94);
      setScaleY(0.94);
      setEffect(new DropShadow(3.2, glow.deriveColor(0, 0.86, 0.82, 0.78)));
    } else if (button.isHover()) {
      setScaleX(1.06);
      setScaleY(1.06);
      setEffect(new DropShadow(7.0, glow));
    } else {
      setScaleX(1.0);
      setScaleY(1.0);
      setEffect(null);
    }
  }

  private static Group addHereArtwork() {
    Group orb = glassOrb("#d8ffb7", "#7edb47", "#35a91d", "#176d12", "#e9ffd8");

    SVGPath plusShadow = filled("M8.2 4.9 H11.8 V8.2 H15.1 V11.8 H11.8 V15.1 H8.2 V11.8 H4.9 V8.2 H8.2 Z",
        Color.rgb(16, 64, 13, 0.72));
    plusShadow.setTranslateY(0.65);
    SVGPath plus = filled("M8.35 4.65 H11.65 V8.35 H15.35 V11.65 H11.65 V15.35 H8.35 V11.65 H4.65 V8.35 H8.35 Z",
        new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
            new Stop(0, Color.WHITE),
            new Stop(0.52, Color.web("#f7fff1")),
            new Stop(1, Color.web("#bfd8b2"))));
    plus.setStroke(Color.rgb(32, 91, 25, 0.85));
    plus.setStrokeWidth(0.52);

    orb.getChildren().addAll(plusShadow, plus);
    return orb;
  }

  private static Group popOutArtwork() {
    Group orb = glassOrb("#f7fdff", "#94d9f4", "#3c7fae", "#173b5a", "#e8f8ff");
    SVGPath arrowShadow = stroked(
        "M6.1 13.9 H5.4 V8.2 H10.8 M9.4 5.5 H14.5 V10.6 M14.2 5.8 L8.2 11.8",
        Color.rgb(0, 23, 42, 0.82), 2.85);
    arrowShadow.setTranslateY(0.55);
    SVGPath arrow = stroked(
        "M6.1 13.6 H5.4 V8.2 H10.6 M9.4 5.5 H14.5 V10.6 M14.2 5.8 L8.2 11.8",
        Color.web("#ffffff"), 1.72);
    orb.getChildren().addAll(arrowShadow, arrow);
    return orb;
  }

  private static Group glassOrb(
      String highlight,
      String light,
      String mid,
      String dark,
      String edge) {
    Circle rim = new Circle(10, 10, 8.35, new LinearGradient(
        0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
        new Stop(0, Color.WHITE),
        new Stop(0.42, Color.web("#dfe8ed")),
        new Stop(1, Color.web("#788894"))));
    rim.setStroke(Color.web("#35434c"));
    rim.setStrokeWidth(0.8);

    Circle glass = new Circle(10, 10, 6.95, new RadialGradient(
        0, 0, 7.4, 6.7, 8.5, false, CycleMethod.NO_CYCLE,
        new Stop(0, Color.web(highlight)),
        new Stop(0.32, Color.web(light)),
        new Stop(0.7, Color.web(mid)),
        new Stop(1, Color.web(dark))));
    glass.setStroke(Color.web(edge));
    glass.setStrokeWidth(0.72);
    glass.setEffect(new InnerShadow(1.25, 0, 0.65, Color.rgb(5, 28, 45, 0.7)));

    SVGPath shine = stroked(
        "M4.8 8.1 C6.3 3.9 12.1 2.4 15.4 5.7",
        Color.rgb(255, 255, 255, 0.72),
        0.78);
    return new Group(rim, glass, shine);
  }

  private static SVGPath filled(String content, javafx.scene.paint.Paint fill) {
    SVGPath path = path(content);
    path.setFill(fill);
    return path;
  }

  private static SVGPath stroked(String content, javafx.scene.paint.Paint stroke, double width) {
    SVGPath path = path(content);
    path.setFill(Color.TRANSPARENT);
    path.setStroke(stroke);
    path.setStrokeWidth(width);
    return path;
  }

  private static SVGPath path(String content) {
    SVGPath path = new SVGPath();
    path.setContent(content);
    path.setStrokeLineCap(StrokeLineCap.ROUND);
    path.setStrokeLineJoin(StrokeLineJoin.ROUND);
    return path;
  }
}
