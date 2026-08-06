package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvn.core.audio.AudioFacade;
import com.jvn.core.vn.VnAudioCommand;
import com.jvn.core.vn.VnNode;
import com.jvn.core.vn.VnNodeType;
import com.jvn.core.vn.VnScenario;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileEditorTabVnsLaunchTest {
  private static boolean toolkitAvailable;

  @BeforeAll
  static void startToolkit() throws Exception {
    if (System.getProperty("os.name", "").toLowerCase().contains("linux")
        && System.getenv().getOrDefault("DISPLAY", "").isBlank()) {
      return;
    }
    CountDownLatch ready = new CountDownLatch(1);
    try {
      Platform.startup(ready::countDown);
      toolkitAvailable = ready.await(5, TimeUnit.SECONDS);
    } catch (IllegalStateException alreadyStarted) {
      toolkitAvailable = true;
    } catch (RuntimeException unavailable) {
      toolkitAvailable = false;
    }
  }

  @Test
  void runningVnsFromTheStripMakesTheDetachedPreviewVisible(@TempDir Path tempDir)
      throws Exception {
    Assumptions.assumeTrue(toolkitAvailable, "JavaFX toolkit is unavailable in this environment");
    Path script = tempDir.resolve("launch_test.vns");
    Files.writeString(
        script,
        """
        @scenario launch_test
        @character narrator ""
        @label start
        narrator: The preview is running.
        """);

    onFxThread(() -> {
      FileEditorTab tab = new FileEditorTab(script.toFile());
      try {
        tab.runFromLabel(null);
        assertTrue(tab.isDetachedPreviewVisible());
      } finally {
        tab.dispose();
      }
      return null;
    });
  }

  @Test
  void vnsStripExposesWorkingReviewControls(@TempDir Path tempDir) throws Exception {
    Assumptions.assumeTrue(toolkitAvailable, "JavaFX toolkit is unavailable in this environment");
    Path script = tempDir.resolve("review_controls.vns");
    Files.writeString(script, String.join("\n",
        "@scenario review_controls",
        "@character narrator \"\"",
        "@character panel_01_wendi \"\"",
        "@character panel_mags \"\"",
        "@background bg_mags bg.png",
        "",
        "",
        "@label start",
        "",
        "[bg bg_mags]",
        "",
        "",
        "[show panel_01_wendi center neutral]",
        "[show panel_mags center neutral]",
        "narrator:1714",
        "[show panel_01_wendi center neutral]",
        "[show panel_mags center neutral]",
        "narrator:1716"));

    onFxThread(() -> {
      FileEditorTab tab = new FileEditorTab(script.toFile());
      AtomicBoolean diagnosticsOpened = new AtomicBoolean();
      tab.setOnOpenDiagnostics(() -> diagnosticsOpened.set(true));
      new Scene(tab, 1400, 180);
      tab.applyCss();
      tab.layout();
      tab.navigateToLine(18);
      try {
        Set<javafx.scene.Node> controls = tab.lookupAll(".vns-tools-aero-button");
        Node stripNode = tab.lookup(".vns-tools-strip");
        assertTrue(stripNode instanceof HBox);
        assertTrue(stripNode.getBoundsInLocal().getHeight() <= 44.0,
            "The VNS command strip should remain compact");
        assertTrue(tab.lookupAll(".script-editor-workspace-title").isEmpty(),
            "The compact VNS strip should not reserve space for a title or logo");
        ButtonBase diagnostics = controls.stream()
            .filter(ButtonBase.class::isInstance)
            .map(ButtonBase.class::cast)
            .filter(button -> "Open VNS diagnostics".equals(button.getAccessibleText()))
            .findFirst()
            .orElseThrow();
        ToggleButton wordWrap = controls.stream()
            .filter(ToggleButton.class::isInstance)
            .map(ToggleButton.class::cast)
            .filter(button -> "Toggle VNS word wrap".equals(button.getAccessibleText()))
            .findFirst()
            .orElseThrow();
        ButtonBase runFromCursor = controls.stream()
            .filter(ButtonBase.class::isInstance)
            .map(ButtonBase.class::cast)
            .filter(button -> "Run from cursor".equals(button.getAccessibleText()))
            .findFirst()
            .orElseThrow();
        assertTrue(controls.stream()
            .filter(ButtonBase.class::isInstance)
            .map(ButtonBase.class::cast)
            .anyMatch(button -> "Run from current label".equals(button.getAccessibleText())));
        assertTrue(controls.stream()
            .filter(ButtonBase.class::isInstance)
            .map(ButtonBase.class::cast)
            .anyMatch(button -> "Compare VNS script with saved version".equals(button.getAccessibleText())));

        diagnostics.fire();
        wordWrap.fire();
        runFromCursor.fire();
        assertTrue(diagnosticsOpened.get());
        assertTrue(wordWrap.isSelected());
        assertTrue(tab.isDetachedPreviewVisible());
        assertEquals(18, tab.getVnsPreviewSourceLine());
      } finally {
        tab.dispose();
      }
      return null;
    });
  }

  @Test
  void loadingVnsKeepsAudioSilentUntilPreviewIsVisible() throws Exception {
    Assumptions.assumeTrue(toolkitAvailable, "JavaFX toolkit is unavailable in this environment");

    onFxThread(() -> {
      RecordingAudio audio = new RecordingAudio();
      VnPreviewView preview = new VnPreviewView(audio);
      VnScenario scenario = VnScenario.builder("silent_editor")
          .addNode(VnNode.builder(VnNodeType.AUDIO)
              .audioCommand(VnAudioCommand.builder(VnAudioCommand.AudioCommandType.PLAY_BGM)
                  .trackId("theme.ogg")
                  .loop(true)
                  .build())
              .build())
          .build();

      try {
        preview.setScenario(scenario);

        assertFalse(preview.isPlaybackActive());
        assertEquals(0, audio.bgmPlayCount);

        preview.setPlaybackActive(true);

        assertTrue(preview.isPlaybackActive());
        assertEquals(1, audio.bgmPlayCount);
        assertEquals("theme.ogg", audio.lastBgm);

        preview.setPlaybackActive(false);

        assertFalse(preview.isPlaybackActive());
        assertTrue(audio.stopCount > 0);
      } finally {
        preview.dispose();
      }
      return null;
    });
  }

  private static final class RecordingAudio implements AudioFacade {
    private int bgmPlayCount;
    private int stopCount;
    private String lastBgm = "";

    @Override
    public void playBgm(String trackId, boolean loop) {
      bgmPlayCount++;
      lastBgm = trackId;
    }

    @Override
    public void stopBgm() {
      stopCount++;
    }

    @Override
    public void playSfx(String soundId) {
      // Historical sound effects must not replay when the preview becomes visible.
    }
  }

  private static <T> T onFxThread(java.util.concurrent.Callable<T> work) throws Exception {
    CountDownLatch done = new CountDownLatch(1);
    AtomicReference<T> result = new AtomicReference<>();
    AtomicReference<Throwable> failure = new AtomicReference<>();
    Platform.runLater(() -> {
      try {
        result.set(work.call());
      } catch (Throwable error) {
        failure.set(error);
      } finally {
        done.countDown();
      }
    });
    assertTrue(done.await(15, TimeUnit.SECONDS), "JavaFX work timed out");
    if (failure.get() != null) throw new AssertionError(failure.get());
    return result.get();
  }
}
