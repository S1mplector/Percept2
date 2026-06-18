package com.jvn.hub;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

final class JvnFxHubLauncher {
  private static final AtomicReference<Path> PROJECT_ROOT = new AtomicReference<>();
  private static final AtomicBoolean LAUNCHED = new AtomicBoolean(false);

  private JvnFxHubLauncher() {
  }

  static void launch(String[] args, Path projectRoot) {
    PROJECT_ROOT.set(projectRoot);
    LAUNCHED.set(true);
    try {
      Application.launch(JvnFxHubApp.class, args == null ? new String[0] : args);
    } catch (RuntimeException | Error ex) {
      LAUNCHED.set(false);
      throw ex;
    }
  }

  static void openOrLaunch(Path projectRoot, Runnable onFailure) {
    PROJECT_ROOT.set(projectRoot);
    if (LAUNCHED.get()) {
      try {
        Platform.runLater(() -> openStage(onFailure));
      } catch (IllegalStateException ex) {
        reportFailure(ex, onFailure);
      }
      return;
    }
    Thread thread = new Thread(() -> {
      try {
        launch(new String[0], projectRoot);
      } catch (Throwable ex) {
        reportFailure(ex, onFailure);
      }
    }, "jvn-fx-hub-switch");
    thread.setDaemon(false);
    thread.start();
  }

  private static void openStage(Runnable onFailure) {
    try {
      new JvnFxHubApp().start(new Stage());
    } catch (Exception ex) {
      reportFailure(ex, onFailure);
    }
  }

  private static void reportFailure(Throwable ex, Runnable onFailure) {
    System.err.println("JVN JavaFX Hub failed to start: " + (ex == null ? "unknown error" : ex.getMessage()));
    if (onFailure != null) onFailure.run();
  }

  static Path projectRoot() {
    Path root = PROJECT_ROOT.get();
    return root == null ? Path.of(".").toAbsolutePath().normalize() : root;
  }
}
