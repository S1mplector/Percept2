package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import javafx.stage.Window;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import com.jvn.fx.testkit.FxToolkit;
import com.jvn.fx.testkit.FxToolkitExtension;
import com.jvn.editor.vcs.GitVcsService;

@ExtendWith(FxToolkitExtension.class)
class VersionControlViewPreflightFxTest {
  @TempDir Path tempDir;

  private File repoRoot;
  private final GitVcsService vcs = new GitVcsService();

  @BeforeEach
  void setUp() throws Exception {
    Assumptions.assumeTrue(vcs.isGitAvailable(), "git must be on PATH to run this test");
    repoRoot = tempDir.toFile();
    vcs.bootstrapRepository(repoRoot, false, null);
    run("git", "config", "user.email", "test@example.com");
    run("git", "config", "user.name", "Test User");
    Files.writeString(repoRoot.toPath().resolve("file.txt"), "hello", StandardCharsets.UTF_8);
    vcs.commitAll(repoRoot, "initial commit");
    Files.writeString(repoRoot.toPath().resolve("changed.txt"), "new", StandardCharsets.UTF_8);
  }

  @Test
  void clickingSaveSnapshotShowsPreflightSummaryBeforeCommitting() throws Exception {
    // View must be attached to a real showing Stage for owner-window lookup in EditorDialogs.
    Stage[] stageHolder = new Stage[1];
    VersionControlView view = runFx(() -> {
      VersionControlView v = new VersionControlView();
      v.setProjectRoot(repoRoot);
      Stage stage = new Stage();
      stage.setScene(new Scene(v, 980, 900));
      stage.show();
      stageHolder[0] = stage;
      return v;
    });
    Stage mainStage = stageHolder[0];

    try {
      waitForStatusLoaded(view);

      Button saveSnapshotButton = findButtonByText(view, "Save Snapshot");
      assertNotNull(saveSnapshotButton, "Save Snapshot button should exist");

      Platform.runLater(saveSnapshotButton::fire);

      Stage dialog = waitForDialogWithTitle("Save Snapshot?");
      assertNotNull(dialog, "Preflight dialog should appear before committing");

      try {
        String summaryText = runFx(() -> collectLabelText(dialog));
        assertTrue(summaryText.contains("Branch: master") || summaryText.contains("Branch: main"),
            "Summary should show current branch: " + summaryText);
        assertTrue(summaryText.contains("Changes: 1 file"), "Summary should show changed file count: " + summaryText);

        GitVcsService.RepositoryStatus statusBeforeConfirm = vcs.getRepositoryStatus(repoRoot);
        assertEquals(1, statusBeforeConfirm.entries().size(), "Commit must not happen before the user confirms");
      } finally {
        runFx(() -> {
          dialog.close();
          return null;
        });
      }
    } finally {
      runFx(() -> {
        mainStage.close();
        return null;
      });
    }
  }

  private void waitForStatusLoaded(VersionControlView view) throws Exception {
    for (int i = 0; i < 40; i++) {
      Boolean loaded = runFx(() -> findButtonByText(view, "Save Snapshot") != null
          && !findButtonByText(view, "Save Snapshot").isDisabled());
      if (Boolean.TRUE.equals(loaded)) return;
      Thread.sleep(250);
    }
  }

  private Stage waitForDialogWithTitle(String titleSubstring) throws Exception {
    for (int i = 0; i < 40; i++) {
      Stage found = runFx(() -> {
        for (Window w : Window.getWindows()) {
          if (w instanceof Stage s && s.getTitle() != null && s.getTitle().contains(titleSubstring)) {
            return s;
          }
        }
        return null;
      });
      if (found != null) return found;
      Thread.sleep(250);
    }
    return null;
  }

  private static Button findButtonByText(javafx.scene.Parent root, String text) {
    for (javafx.scene.Node node : root.lookupAll(".button")) {
      if (node instanceof Button button && text.equals(button.getText())) {
        return button;
      }
    }
    return null;
  }

  private static String collectLabelText(Stage dialog) {
    StringBuilder sb = new StringBuilder();
    for (javafx.scene.Node node : dialog.getScene().getRoot().lookupAll(".label")) {
      if (node instanceof Label label && label.getText() != null) {
        sb.append(label.getText()).append('\n');
      }
    }
    return sb.toString();
  }

  private void run(String... command) throws Exception {
    ProcessBuilder pb = new ProcessBuilder(command);
    pb.directory(repoRoot);
    pb.redirectErrorStream(true);
    Process p = pb.start();
    p.getInputStream().readAllBytes();
    p.waitFor();
  }

  private static <T> T runFx(Callable<T> callable) throws Exception {
    return FxToolkit.runFx(callable);
  }
}
