package com.jvn.editor.ui;

import java.util.WeakHashMap;

import org.fxmisc.richtext.CodeArea;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.shape.Rectangle;

/** Keeps horizontally scrolled paragraph text out of the fixed line-number gutter. */
final class CodeEditorGutterGuard {
  private CodeEditorGutterGuard() {}

  static void install(CodeArea area) {
    WeakHashMap<Node, Rectangle> clips = new WeakHashMap<>();
    boolean[] queued = {false};
    Runnable[] refresh = new Runnable[1];
    Runnable schedule = () -> {
      if (queued[0]) return;
      queued[0] = true;
      Platform.runLater(() -> {
        queued[0] = false;
        refresh[0].run();
      });
    };
    refresh[0] = () -> {
      double scrollX = Math.max(0.0, area.getEstimatedScrollX());
      for (Node text : area.lookupAll(".paragraph-text")) {
        if (scrollX < 0.5) {
          Rectangle clip = clips.remove(text);
          if (clip != null && text.getClip() == clip) text.setClip(null);
          continue;
        }
        Rectangle clip = clips.computeIfAbsent(text, ignored -> new Rectangle());
        clip.setX(scrollX);
        clip.setY(-1.0);
        clip.setWidth(text.getLayoutBounds().getWidth() + scrollX + 1.0);
        clip.setHeight(text.getLayoutBounds().getHeight() + 2.0);
        if (text.getClip() != clip) text.setClip(clip);
      }
    };
    area.estimatedScrollXProperty().addListener((obs, oldValue, newValue) -> schedule.run());
    area.estimatedScrollYProperty().addListener((obs, oldValue, newValue) -> schedule.run());
    area.textProperty().addListener((obs, oldValue, newValue) -> schedule.run());
    area.layoutBoundsProperty().addListener((obs, oldValue, newValue) -> schedule.run());
    schedule.run();
  }
}
