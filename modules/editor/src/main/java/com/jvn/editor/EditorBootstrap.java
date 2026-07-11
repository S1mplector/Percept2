package com.jvn.editor;

import javafx.application.Application;

/** Plain Java main class for direct dev launches outside Gradle's JavaExec task. */
public final class EditorBootstrap {
  private EditorBootstrap() {}

  public static void main(String[] args) {
    Application.launch(EditorApp.class, args);
  }
}
