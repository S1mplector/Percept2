package com.jvn.hub;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import javafx.application.Application;

final class JvnFxHubLauncher {
  private static final AtomicReference<Path> PROJECT_ROOT = new AtomicReference<>();

  private JvnFxHubLauncher() {
  }

  static void launch(String[] args, Path projectRoot) {
    PROJECT_ROOT.set(projectRoot);
    Application.launch(JvnFxHubApp.class, args == null ? new String[0] : args);
  }

  static Path projectRoot() {
    Path root = PROJECT_ROOT.get();
    return root == null ? Path.of(".").toAbsolutePath().normalize() : root;
  }
}
