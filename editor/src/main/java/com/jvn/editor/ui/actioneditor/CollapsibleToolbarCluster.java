package com.jvn.editor.ui.actioneditor;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

public class CollapsibleToolbarCluster extends VBox {
    private static final Duration TOGGLE_DURATION = Duration.millis(180);
    private static final Interpolator SIZE_INTERPOLATOR = Interpolator.SPLINE(0.2, 0.0, 0.2, 1.0);
    private static final Interpolator OPACITY_INTERPOLATOR = Interpolator.EASE_BOTH;
    private static final String STYLE_HEADER_COLLAPSED =
        "-fx-background-color: #15181d; -fx-border-color: #2d3542; -fx-border-radius: 8; " +
        "-fx-background-radius: 8; -fx-padding: 0 10; -fx-cursor: hand;";
    private static final String STYLE_HEADER_EXPANDED =
        "-fx-background-color: #182130; -fx-border-color: #415a80; -fx-border-radius: 8; " +
        "-fx-background-radius: 8; -fx-padding: 0 10; -fx-cursor: hand;";
    private static final String STYLE_CONTENT =
        "-fx-background-color: #101318; -fx-border-color: #253247; -fx-border-radius: 8; " +
        "-fx-background-radius: 8;";

    private final String clusterKey;
    private final Button headerButton;
    private final Label indicatorLabel;
    private final Label stateLabel;
    private final StackPane contentWrapper;
    private final Rectangle contentClip = new Rectangle();

    private boolean expanded;
    private Timeline activeAnimation;

    public CollapsibleToolbarCluster(String clusterKey, String title, Node content) {
        this.clusterKey = sanitize(clusterKey);
        setSpacing(4);
        setFillWidth(false);
        setAlignment(Pos.TOP_LEFT);
        setId("toolbar-cluster-" + this.clusterKey);

        indicatorLabel = new Label(">");
        indicatorLabel.setStyle("-fx-text-fill: #b7c4d8; -fx-font-size: 10px; -fx-font-weight: bold;");
        indicatorLabel.setMouseTransparent(true);

        Label titleLabel = new Label(normalizeTitle(title));
        titleLabel.setStyle("-fx-text-fill: #e6e6e6; -fx-font-size: 11px; -fx-font-weight: bold;");
        titleLabel.setMouseTransparent(true);

        stateLabel = new Label("show");
        stateLabel.setStyle("-fx-text-fill: #7f8da3; -fx-font-size: 9px;");
        stateLabel.setMouseTransparent(true);

        HBox headerGraphic = new HBox(6, indicatorLabel, titleLabel, stateLabel);
        headerGraphic.setAlignment(Pos.CENTER_LEFT);

        headerButton = new Button();
        headerButton.setGraphic(headerGraphic);
        headerButton.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        headerButton.setGraphicTextGap(0);
        headerButton.setMinHeight(28);
        headerButton.setPrefHeight(28);
        headerButton.setFocusTraversable(false);
        headerButton.setOnAction(e -> setExpanded(!expanded));

        contentWrapper = new StackPane(content);
        contentWrapper.setId(getId() + "-content");
        contentWrapper.setPadding(new Insets(8, 10, 8, 10));
        contentWrapper.setStyle(STYLE_CONTENT);
        contentWrapper.setClip(contentClip);
        contentClip.widthProperty().bind(contentWrapper.widthProperty());
        contentClip.heightProperty().bind(contentWrapper.heightProperty());

        getChildren().addAll(headerButton, contentWrapper);
        applyExpandedState(false, false);
    }

    public String getClusterKey() {
        return clusterKey;
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(boolean expanded) {
        if (this.expanded == expanded && activeAnimation == null) {
            return;
        }
        applyExpandedState(expanded, true);
    }

    private void applyExpandedState(boolean expanded, boolean animate) {
        this.expanded = expanded;
        stateLabel.setText(expanded ? "hide" : "show");
        headerButton.setStyle(expanded ? STYLE_HEADER_EXPANDED : STYLE_HEADER_COLLAPSED);
        if (!animate) {
            finishExpandedState(expanded);
            indicatorLabel.setRotate(expanded ? 90.0 : 0.0);
            return;
        }
        animateExpandedState(expanded);
    }

    private void animateExpandedState(boolean expanding) {
        if (activeAnimation != null) {
            activeAnimation.stop();
            activeAnimation = null;
        }

        double startHeight = currentHeight();
        double targetHeight = expanding ? measureExpandedHeight() : 0.0;
        double startOpacity = contentWrapper.isVisible() ? contentWrapper.getOpacity() : 0.0;
        double targetOpacity = expanding ? 1.0 : 0.0;
        double startRotation = indicatorLabel.getRotate();
        double targetRotation = expanding ? 90.0 : 0.0;

        contentWrapper.setManaged(true);
        contentWrapper.setVisible(true);
        contentWrapper.setMinHeight(0.0);
        contentWrapper.setPrefHeight(startHeight);
        contentWrapper.setMaxHeight(startHeight);
        contentWrapper.setOpacity(startOpacity);

        activeAnimation = new Timeline(
            new KeyFrame(Duration.ZERO,
                new KeyValue(contentWrapper.prefHeightProperty(), startHeight),
                new KeyValue(contentWrapper.maxHeightProperty(), startHeight),
                new KeyValue(contentWrapper.opacityProperty(), startOpacity),
                new KeyValue(indicatorLabel.rotateProperty(), startRotation)
            ),
            new KeyFrame(TOGGLE_DURATION,
                new KeyValue(contentWrapper.prefHeightProperty(), targetHeight, SIZE_INTERPOLATOR),
                new KeyValue(contentWrapper.maxHeightProperty(), targetHeight, SIZE_INTERPOLATOR),
                new KeyValue(contentWrapper.opacityProperty(), targetOpacity, OPACITY_INTERPOLATOR),
                new KeyValue(indicatorLabel.rotateProperty(), targetRotation, SIZE_INTERPOLATOR)
            )
        );
        activeAnimation.setOnFinished(event -> {
            activeAnimation = null;
            finishExpandedState(expanding);
            indicatorLabel.setRotate(targetRotation);
        });
        activeAnimation.play();
    }

    private void finishExpandedState(boolean expanded) {
        contentWrapper.setMinHeight(0.0);
        if (expanded) {
            contentWrapper.setManaged(true);
            contentWrapper.setVisible(true);
            contentWrapper.setOpacity(1.0);
            contentWrapper.setPrefHeight(Region.USE_COMPUTED_SIZE);
            contentWrapper.setMaxHeight(Region.USE_COMPUTED_SIZE);
        } else {
            contentWrapper.setOpacity(0.0);
            contentWrapper.setPrefHeight(0.0);
            contentWrapper.setMaxHeight(0.0);
            contentWrapper.setVisible(false);
            contentWrapper.setManaged(false);
        }
        requestLayout();
    }

    private double currentHeight() {
        if (!contentWrapper.isVisible()) {
            return 0.0;
        }
        double current = Math.max(contentWrapper.getHeight(), contentWrapper.getPrefHeight());
        if (!Double.isFinite(current) || current < 0.0) {
            return 0.0;
        }
        if (current == Region.USE_COMPUTED_SIZE) {
            return measureExpandedHeight();
        }
        return current;
    }

    private double measureExpandedHeight() {
        double previousPref = contentWrapper.getPrefHeight();
        double previousMax = contentWrapper.getMaxHeight();
        boolean wasManaged = contentWrapper.isManaged();
        boolean wasVisible = contentWrapper.isVisible();

        contentWrapper.setManaged(true);
        contentWrapper.setVisible(true);
        contentWrapper.setPrefHeight(Region.USE_COMPUTED_SIZE);
        contentWrapper.setMaxHeight(Region.USE_COMPUTED_SIZE);
        applyCss();
        layout();

        double measured = Math.max(contentWrapper.prefHeight(-1), contentWrapper.minHeight(-1));
        if (!Double.isFinite(measured) || measured < 0.0) {
            measured = 0.0;
        }

        contentWrapper.setPrefHeight(previousPref);
        contentWrapper.setMaxHeight(previousMax);
        contentWrapper.setManaged(wasManaged);
        contentWrapper.setVisible(wasVisible);
        return measured;
    }

    private static String normalizeTitle(String title) {
        if (title == null || title.isBlank()) {
            return "Cluster";
        }
        return title.trim();
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "cluster";
        }
        String normalized = value.trim().toLowerCase();
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')) {
                out.append(ch);
            } else if (out.isEmpty() || out.charAt(out.length() - 1) != '-') {
                out.append('-');
            }
        }
        while (!out.isEmpty() && out.charAt(out.length() - 1) == '-') {
            out.deleteCharAt(out.length() - 1);
        }
        return out.isEmpty() ? "cluster" : out.toString();
    }
}
