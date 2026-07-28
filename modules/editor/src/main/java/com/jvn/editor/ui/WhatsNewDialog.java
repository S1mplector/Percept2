package com.jvn.editor.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/** Builds the themed, scrollable release summary shown after an editor update. */
public final class WhatsNewDialog {
  private WhatsNewDialog() {
  }

  public static void show(Window owner, WhatsNewCatalog.Release release) {
    if (release == null) return;

    VBox content = new VBox(12);
    content.getStyleClass().add("whats-new-content");

    HBox hero = new HBox(14);
    hero.setAlignment(Pos.CENTER_LEFT);
    hero.getStyleClass().add("whats-new-hero");

    Region icon = AeroIcon.of(AeroIcon.Kind.WHATS_NEW, 48);
    VBox heading = new VBox(3);
    HBox badges = new HBox(7);
    badges.setAlignment(Pos.CENTER_LEFT);
    Label newBadge = new Label("NEW RELEASE");
    newBadge.getStyleClass().add("whats-new-badge");
    Label versionBadge = new Label(release.versionLabel());
    versionBadge.getStyleClass().add("whats-new-version");
    badges.getChildren().addAll(newBadge, versionBadge);

    Label summary = new Label(release.summary());
    summary.getStyleClass().add("whats-new-summary");
    summary.setWrapText(true);
    summary.setMaxWidth(420);
    heading.getChildren().addAll(badges, summary);
    HBox.setHgrow(heading, Priority.ALWAYS);
    hero.getChildren().addAll(icon, heading);
    content.getChildren().add(hero);

    for (WhatsNewCatalog.Section section : release.sections()) {
      content.getChildren().add(sectionCard(section));
    }

    Label footer = new Label("Reopen this summary any time from Help > What's New.");
    footer.getStyleClass().add("whats-new-footer");
    footer.setWrapText(true);
    content.getChildren().add(footer);

    ScrollPane scroll = new ScrollPane(content);
    scroll.getStyleClass().add("whats-new-scroll");
    scroll.setFitToWidth(true);
    scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
    scroll.setPrefViewportWidth(510);
    scroll.setPrefViewportHeight(440);
    scroll.setMaxHeight(480);

    EditorDialogs.show(
        owner,
        "What's New in " + release.versionLabel(),
        release.curated()
            ? "Here are the highlights included in this version."
            : "This version has changed since your last JVN session.",
        scroll,
        EditorDialogs.ActionSpec.accent("close", "Start Creating", () -> {}));
  }

  private static VBox sectionCard(WhatsNewCatalog.Section section) {
    VBox card = new VBox(7);
    card.getStyleClass().add("whats-new-section");
    card.setPadding(new Insets(12));

    Label title = new Label(section.title());
    title.getStyleClass().add("whats-new-section-title");

    Label summary = new Label(section.summary());
    summary.getStyleClass().add("whats-new-section-summary");
    summary.setWrapText(true);

    card.getChildren().addAll(title, summary);
    for (String change : section.changes()) {
      HBox row = new HBox(8);
      row.setAlignment(Pos.TOP_LEFT);
      row.getStyleClass().add("whats-new-change-row");

      Label marker = new Label("✓");
      marker.getStyleClass().add("whats-new-change-marker");
      marker.setMinWidth(18);

      Label text = new Label(change);
      text.getStyleClass().add("whats-new-change-text");
      text.setWrapText(true);
      text.setMaxWidth(Double.MAX_VALUE);
      HBox.setHgrow(text, Priority.ALWAYS);
      row.getChildren().addAll(marker, text);
      card.getChildren().add(row);
    }
    return card;
  }
}
