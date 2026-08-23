package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.ToggleButton;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VnsDiagnosticsViewFxTest {
  private static boolean toolkitAvailable;

  @BeforeAll
  static void startToolkit() {
    if (System.getProperty("os.name", "").toLowerCase().contains("linux")
        && System.getenv().getOrDefault("DISPLAY", "").isBlank()) {
      return;
    }
    try {
      CountDownLatch ready = new CountDownLatch(1);
      Platform.startup(ready::countDown);
      toolkitAvailable = ready.await(10, TimeUnit.SECONDS);
    } catch (IllegalStateException alreadyStarted) {
      toolkitAvailable = true;
    } catch (Exception unavailable) {
      toolkitAvailable = false;
    }
  }

  @Test
  void toolbarFiltersFindingsAndRescans(@TempDir Path tempDir) throws Exception {
    Assumptions.assumeTrue(toolkitAvailable, "JavaFX toolkit is unavailable in this environment");
    runFx(() -> {
      VnsDiagnosticsView view = new VnsDiagnosticsView();
      AtomicBoolean rescanned = new AtomicBoolean();
      view.setOnRefresh(() -> rescanned.set(true));
      view.setDiagnostics(
          tempDir.resolve("demo.vns").toFile(),
          "VNS",
          "[jump missing]\n[bgm assets/audio/missing.ogg]",
          "2 lines",
          List.of(
              VnsDiagnosticsView.Diagnostic.error("undefined_label", "Undefined label: missing", 6, 13, 0),
              VnsDiagnosticsView.Diagnostic.warning("missing_audio_asset", "Missing audio", 20, 30, 1)));

      Scene scene = new Scene(view, 720, 620);
      var stylesheet = VnsDiagnosticsView.class.getResource("/com/jvn/editor/editor-light.css");
      if (stylesheet != null) scene.getStylesheets().add(stylesheet.toExternalForm());
      view.applyCss();
      view.layout();

      assertTrue(view.lookup(".vns-diagnostics-health") != null);
      assertTrue(view.lookup(".vns-diagnostics-category-filter") != null);
      assertTrue(view.lookup(".vns-diagnostics-action-row") != null);

      Set<DiagnosticsToolbarIcon.Kind> toolbarKinds = new HashSet<>();
      java.util.concurrent.atomic.AtomicBoolean hasStandardRefresh =
          new java.util.concurrent.atomic.AtomicBoolean();
      view.lookupAll(".vns-diagnostics-action-button").stream()
          .filter(Button.class::isInstance)
          .map(Button.class::cast)
          .forEach(button -> {
            if (button.getGraphic() instanceof RefreshIcon) {
              hasStandardRefresh.set(true);
            } else {
              assertTrue(button.getGraphic() instanceof DiagnosticsToolbarIcon);
              toolbarKinds.add(((DiagnosticsToolbarIcon) button.getGraphic()).kind());
            }
          });
      assertTrue(hasStandardRefresh.get());
      assertEquals(Set.of(
          DiagnosticsToolbarIcon.Kind.OPEN,
          DiagnosticsToolbarIcon.Kind.PREVIOUS,
          DiagnosticsToolbarIcon.Kind.NEXT,
          DiagnosticsToolbarIcon.Kind.COPY_REPORT,
          DiagnosticsToolbarIcon.Kind.CLEAR_FILTER,
          DiagnosticsToolbarIcon.Kind.SORT_LINE), toolbarKinds);

      Button sort = view.lookupAll(".vns-diagnostics-action-button").stream()
          .filter(Button.class::isInstance)
          .map(Button.class::cast)
          .filter(button -> "By Line".equals(button.getText()))
          .findFirst()
          .orElseThrow();
      sort.fire();
      assertEquals(
          DiagnosticsToolbarIcon.Kind.SORT_SEVERITY,
          ((DiagnosticsToolbarIcon) sort.getGraphic()).kind());

      ToggleButton errors = view.lookupAll(".vns-diagnostics-chip").stream()
          .filter(ToggleButton.class::isInstance)
          .map(ToggleButton.class::cast)
          .filter(button -> button.getText().contains("Error"))
          .findFirst()
          .orElseThrow();
      errors.fire();

      @SuppressWarnings("unchecked")
      ListView<Object> findings = (ListView<Object>) view.lookup(".vns-diagnostics-list");
      assertEquals(1, findings.getItems().size());

      view.lookupAll(".button").stream()
          .filter(javafx.scene.control.Button.class::isInstance)
          .map(javafx.scene.control.Button.class::cast)
          .filter(button -> "Rescan".equals(button.getText()))
          .findFirst()
          .orElseThrow()
          .fire();
      assertTrue(rescanned.get());
      return null;
    });
  }

  private static <T> T runFx(Callable<T> callable) throws Exception {
    FutureTask<T> task = new FutureTask<>(callable);
    Platform.runLater(task);
    return task.get(30, TimeUnit.SECONDS);
  }
}
