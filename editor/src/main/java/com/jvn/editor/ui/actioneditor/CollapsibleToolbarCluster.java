package com.jvn.editor.ui.actioneditor;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
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
        "-fx-background-color: #171717; -fx-border-color: #373737; -fx-border-radius: 8; " +
        "-fx-background-radius: 8; -fx-padding: 0 10; -fx-cursor: hand;";
    private static final String STYLE_HEADER_EXPANDED =
        "-fx-background-color: #202020; -fx-border-color: #505050; -fx-border-radius: 8; " +
        "-fx-background-radius: 8; -fx-padding: 0 10; -fx-cursor: hand;";
    private static final String STYLE_HEADER_PINNED =
        "-fx-background-color: #242424; -fx-border-color: #6a6a6a; -fx-border-radius: 8; " +
        "-fx-background-radius: 8; -fx-padding: 0 10; -fx-cursor: hand;";
    private static final String STYLE_HEADER_COMPACT =
        "-fx-background-color: #151515; -fx-border-color: #353535; -fx-border-radius: 6; " +
        "-fx-background-radius: 6; -fx-padding: 0 5; -fx-cursor: default;";
    private static final String STYLE_PIN_OFF =
        "-fx-background-color: #161616; -fx-text-fill: #9c9c9c; -fx-border-color: #3b3b3b; " +
        "-fx-border-radius: 7; -fx-background-radius: 7; -fx-padding: 0 7; -fx-font-size: 9px; " +
        "-fx-font-weight: bold; -fx-cursor: hand;";
    private static final String STYLE_PIN_ON =
        "-fx-background-color: rgba(160, 160, 160, 0.16); -fx-text-fill: #d8d8d8; -fx-border-color: #7a7a7a; " +
        "-fx-border-radius: 7; -fx-background-radius: 7; -fx-padding: 0 7; -fx-font-size: 9px; " +
        "-fx-font-weight: bold; -fx-cursor: hand;";
    private static final String STYLE_CONTENT =
        "-fx-background-color: #121212; -fx-border-color: #2f2f2f; -fx-border-radius: 8; " +
        "-fx-background-radius: 8;";
    private static final String STYLE_CONTENT_COMPACT =
        "-fx-background-color: transparent; -fx-border-color: transparent; -fx-border-radius: 0; " +
        "-fx-background-radius: 0;";
    private static final String STYLE_CLUSTER_COMPACT =
        "-fx-background-color: linear-gradient(to bottom, #181818, #141414); " +
        "-fx-border-color: #2f2f2f; -fx-border-radius: 8; -fx-background-radius: 8; " +
        "-fx-padding: 4 6 5 6;";

    private final String clusterKey;
    private final Button headerButton;
    private final ToggleButton pinButton;
    private final Label pinIcon;
    private final Label indicatorLabel;
    private final Label titleLabel;
    private final Label stateLabel;
    private final HBox headerGraphic;
    private final HBox headerRow;
    private final StackPane contentWrapper;
    private final Rectangle contentClip = new Rectangle();
    private final ReadOnlyBooleanWrapper expanded = new ReadOnlyBooleanWrapper(false);
    private final BooleanProperty pinned = new SimpleBooleanProperty(false);

    private Timeline activeAnimation;
    private AnimatedToolbarPane.LayoutMode layoutMode = AnimatedToolbarPane.LayoutMode.DYNAMIC;

    public CollapsibleToolbarCluster(String clusterKey, String title, Node content) {
        this.clusterKey = sanitize(clusterKey);
        setSpacing(4);
        setFillWidth(false);
        setAlignment(Pos.TOP_LEFT);
        setId("toolbar-cluster-" + this.clusterKey);

        indicatorLabel = new Label(">");
        indicatorLabel.setStyle("-fx-text-fill: #c7c7c7; -fx-font-size: 10px; -fx-font-weight: bold;");
        indicatorLabel.setMouseTransparent(true);

        titleLabel = new Label(normalizeTitle(title));
        titleLabel.setStyle("-fx-text-fill: #e6e6e6; -fx-font-size: 11px; -fx-font-weight: bold;");
        titleLabel.setMouseTransparent(true);

        stateLabel = new Label("show");
        stateLabel.setStyle("-fx-text-fill: #9b9b9b; -fx-font-size: 9px;");
        stateLabel.setMouseTransparent(true);

        headerGraphic = new HBox(6, indicatorLabel, titleLabel, stateLabel);
        headerGraphic.setAlignment(Pos.CENTER_LEFT);

        headerButton = new Button();
        headerButton.setGraphic(headerGraphic);
        headerButton.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        headerButton.setGraphicTextGap(0);
        headerButton.setMinHeight(28);
        headerButton.setPrefHeight(28);
        headerButton.setMaxWidth(Double.MAX_VALUE);
        headerButton.setFocusTraversable(false);
        headerButton.setOnAction(e -> {
            if (layoutMode != AnimatedToolbarPane.LayoutMode.DYNAMIC) {
                return;
            }
            if (isPinned() && isExpanded()) {
                return;
            }
            setExpanded(!isExpanded());
        });

        pinIcon = new Label();
        pinIcon.getStyleClass().addAll("icon", "puppeteer-toolbar-icon", "icon-puppeteer-pin");
        pinIcon.setMouseTransparent(true);

        pinButton = new ToggleButton();
        pinButton.setText("");
        pinButton.setGraphic(pinIcon);
        pinButton.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        pinButton.setMinSize(24, 24);
        pinButton.setPrefSize(24, 24);
        pinButton.setMaxSize(24, 24);
        pinButton.setFocusTraversable(false);
        pinButton.setTooltip(new Tooltip("Pin cluster open"));
        pinButton.setOnAction(e -> setPinned(pinButton.isSelected()));

        headerRow = new HBox(6, headerButton, pinButton);
        headerRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(headerButton, Priority.ALWAYS);

        contentWrapper = new StackPane(content);
        contentWrapper.setId(getId() + "-content");
        contentWrapper.setPadding(new Insets(8, 10, 8, 10));
        contentWrapper.setStyle(STYLE_CONTENT);
        contentWrapper.setClip(contentClip);
        contentClip.widthProperty().bind(contentWrapper.widthProperty());
        contentClip.heightProperty().bind(contentWrapper.heightProperty());

        getChildren().addAll(headerRow, contentWrapper);
        applyExpandedState(false, false);
        refreshHeaderState();
    }

    public String getClusterKey() {
        return clusterKey;
    }

    public String getTitle() {
        return titleLabel.getText();
    }

    public boolean isExpanded() {
        return expanded.get();
    }

    public ReadOnlyBooleanProperty expandedProperty() {
        return expanded.getReadOnlyProperty();
    }

    public boolean isPinned() {
        return pinned.get();
    }

    public BooleanProperty pinnedProperty() {
        return pinned;
    }

    public AnimatedToolbarPane.LayoutMode getLayoutMode() {
        return layoutMode;
    }

    public void setLayoutMode(AnimatedToolbarPane.LayoutMode mode) {
        AnimatedToolbarPane.LayoutMode resolved = mode != null
            ? mode
            : AnimatedToolbarPane.LayoutMode.DYNAMIC;
        if (layoutMode == resolved) {
            return;
        }
        layoutMode = resolved;
        if (activeAnimation != null) {
            activeAnimation.stop();
            activeAnimation = null;
        }
        refreshHeaderState();
        finishExpandedState(expanded.get());
        indicatorLabel.setRotate(layoutMode == AnimatedToolbarPane.LayoutMode.COMPACT || expanded.get() ? 90.0 : 0.0);
    }

    public void setPinned(boolean pinned) {
        if (isPinned() == pinned) {
            return;
        }
        this.pinned.set(pinned);
        if (pinButton.isSelected() != pinned) {
            pinButton.setSelected(pinned);
        }
        if (pinned && !isExpanded()) {
            applyExpandedState(true, true);
            return;
        }
        refreshHeaderState();
        requestLayout();
    }

    public void setExpanded(boolean expanded) {
        if (!expanded && isPinned()) {
            return;
        }
        if (isExpanded() == expanded && activeAnimation == null) {
            return;
        }
        applyExpandedState(expanded, true);
    }

    private void applyExpandedState(boolean expanded, boolean animate) {
        this.expanded.set(expanded);
        refreshHeaderState();
        if (layoutMode == AnimatedToolbarPane.LayoutMode.COMPACT) {
            finishExpandedState(expanded);
            indicatorLabel.setRotate(90.0);
            return;
        }
        if (!animate) {
            finishExpandedState(expanded);
            indicatorLabel.setRotate(expanded ? 90.0 : 0.0);
            return;
        }
        animateExpandedState(expanded);
    }

    private void refreshHeaderState() {
        boolean compact = layoutMode == AnimatedToolbarPane.LayoutMode.COMPACT;
        stateLabel.setText(isPinned() ? "pinned" : (isExpanded() ? "hide" : "show"));
        if (compact) {
            setStyle(STYLE_CLUSTER_COMPACT);
            headerButton.setStyle(STYLE_HEADER_COMPACT + "-fx-background-color: transparent; -fx-border-color: transparent; -fx-padding: 0;");
        } else if (isPinned()) {
            setStyle("");
            headerButton.setStyle(STYLE_HEADER_PINNED);
        } else {
            setStyle("");
            headerButton.setStyle(isExpanded() ? STYLE_HEADER_EXPANDED : STYLE_HEADER_COLLAPSED);
        }
        pinButton.setStyle(isPinned() ? STYLE_PIN_ON : STYLE_PIN_OFF);
        pinIcon.setStyle(isPinned()
            ? "-fx-background-color: #e26c6c;"
            : "-fx-background-color: #bb5f5f;");
        indicatorLabel.setManaged(!compact);
        indicatorLabel.setVisible(!compact);
        stateLabel.setManaged(!compact);
        stateLabel.setVisible(!compact);
        pinButton.setManaged(!compact);
        pinButton.setVisible(!compact);
        headerButton.setMouseTransparent(compact);
        headerButton.setDisable(false);
        setSpacing(compact ? 1.0 : 4.0);
        headerGraphic.setSpacing(compact ? 0.0 : 6.0);
        headerRow.setSpacing(compact ? 0.0 : 6.0);
        titleLabel.setStyle(compact
            ? "-fx-text-fill: #9f9f9f; -fx-font-size: 9px; -fx-font-weight: bold; -fx-letter-spacing: 0.4px;"
            : "-fx-text-fill: #e6e6e6; -fx-font-size: 11px; -fx-font-weight: bold;");
        headerButton.setMinHeight(compact ? 12.0 : 28.0);
        headerButton.setPrefHeight(compact ? 12.0 : 28.0);
        contentWrapper.setPadding(compact ? new Insets(1, 0, 0, 0) : new Insets(8, 10, 8, 10));
        contentWrapper.setStyle(compact ? STYLE_CONTENT_COMPACT : STYLE_CONTENT);
        if (pinButton.isSelected() != isPinned()) {
            pinButton.setSelected(isPinned());
        }
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
        boolean showContent = layoutMode == AnimatedToolbarPane.LayoutMode.COMPACT || expanded;
        if (showContent) {
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
