package com.jvn.editor;

import javafx.application.Application;

/** Plain Java main class for direct dev launches outside Gradle's JavaExec task. */
public final class JvnLauncherBootstrap {
  private JvnLauncherBootstrap() {}

  public static void main(String[] args) {
    Application.launch(JvnLauncherApp.class, args);
  }
}
