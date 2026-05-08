package com.jvn.editor;

import com.jvn.editor.ui.EditorTheme;
import com.jvn.editor.ui.HelpCenterView;
import java.awt.Desktop;
import java.io.File;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

/** Standalone pop-out Help Center used by the Engine Hub and docs tooling. */
public final class HelpCenterApp extends Application {
  private static final String HELP_WORKSPACE_ROOT_PROPERTY = "jvn.help.workspaceRoot";
  private static final String HELP_PROJECT_ROOT_PROPERTY = "jvn.help.projectRoot";
  private static final String REPO_ROOT_PROPERTY = "jvn.repoRoot";

  public static void main(String[] args) {
    launch(args);
  }

  @Override
  public void start(Stage stage) {
    File workspaceRoot = resolveDirectory(
        HELP_WORKSPACE_ROOT_PROPERTY,
        REPO_ROOT_PROPERTY,
        "user.dir");
    File projectRoot = resolveDirectory(HELP_PROJECT_ROOT_PROPERTY);

    HelpCenterView view = new HelpCenterView();
    view.setWorkspaceRoot(workspaceRoot);
    view.setProjectRoot(projectRoot);
    view.setOnOpenDoc(HelpCenterApp::openDocument);

    Scene scene = new Scene(new BorderPane(view), 700, 650);
    EditorTheme.apply(scene);
    stage.setTitle("Help Center");
    stage.setScene(scene);
    stage.setMinWidth(640);
    stage.setMinHeight(520);
    stage.show();
  }

  private static File resolveDirectory(String... propertyNames) {
    if (propertyNames == null) return null;
    for (String propertyName : propertyNames) {
      if (propertyName == null || propertyName.isBlank()) continue;
      String raw = System.getProperty(propertyName);
      if (raw == null || raw.isBlank()) continue;
      File dir = new File(raw).getAbsoluteFile();
      if (dir.isDirectory()) return dir;
    }
    return null;
  }

  private static void openDocument(File file) {
    if (file == null || !file.isFile()) return;
    if (!Desktop.isDesktopSupported()) return;
    try {
      Desktop.getDesktop().open(file);
    } catch (Exception ignored) {
      // The preview remains usable even if the host OS cannot open Markdown files.
    }
  }
}
