package com.jvn.editor.ui.actioneditor;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class CollapsibleToolbarCluster extends VBox {
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

    private boolean expanded;

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

        getChildren().addAll(headerButton, contentWrapper);
        setExpanded(false);
    }

    public String getClusterKey() {
        return clusterKey;
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
        indicatorLabel.setText(expanded ? "v" : ">");
        stateLabel.setText(expanded ? "hide" : "show");
        headerButton.setStyle(expanded ? STYLE_HEADER_EXPANDED : STYLE_HEADER_COLLAPSED);
        contentWrapper.setManaged(expanded);
        contentWrapper.setVisible(expanded);
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
