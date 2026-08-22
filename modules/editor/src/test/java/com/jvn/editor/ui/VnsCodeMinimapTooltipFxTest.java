package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import org.fxmisc.richtext.CodeArea;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class VnsCodeMinimapTooltipFxTest {
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
  void tooltipShowsLabelNameAndLine() throws Exception {
    Assumptions.assumeTrue(toolkitAvailable, "JavaFX toolkit is unavailable in this environment");
    runFx(() -> {
      VnsCodeMinimap minimap = newMinimap("@label start\n[say \"hero\" \"Hi\"]\n");
      String tooltip = minimap.tooltipTextForLine(0);
      assertTrue(tooltip.contains("Line 1"));
      assertTrue(tooltip.contains("@label start"));
      return null;
    });
  }

  @Test
  void tooltipShowsSpeakerForDialogueLine() throws Exception {
    Assumptions.assumeTrue(toolkitAvailable, "JavaFX toolkit is unavailable in this environment");
    runFx(() -> {
      VnsCodeMinimap minimap = newMinimap("hero: Hello there\n");
      String tooltip = minimap.tooltipTextForLine(0);
      assertTrue(tooltip.contains("dialogue: hero"));
      return null;
    });
  }

  @Test
  void tooltipShowsNearbySpeakerForPlainTextLine() throws Exception {
    Assumptions.assumeTrue(toolkitAvailable, "JavaFX toolkit is unavailable in this environment");
    runFx(() -> {
      VnsCodeMinimap minimap = newMinimap(
          "hero: Hello there\n"
              + "This continues without a new speaker tag\n");
      String tooltip = minimap.tooltipTextForLine(1);
      assertTrue(tooltip.contains("dialogue near: hero"));
      return null;
    });
  }

  @Test
  void tooltipShowsTimelineLineRangeDurationTrackAndActionCounts() throws Exception {
    Assumptions.assumeTrue(toolkitAvailable, "JavaFX toolkit is unavailable in this environment");
    runFx(() -> {
      String script =
          "@label start\n"
              + "timeline {\n"
              + "  move \"hero\" {\n"
              + "    x: 100\n"
              + "    dur: 200\n"
              + "  }\n"
              + "}\n";
      VnsCodeMinimap minimap = new VnsCodeMinimap(new CodeArea());
      minimap.setSnapshot(script, List.of(), List.of(),
          List.of(new VnsCodeMinimap.TimelineBlock(1, 6, false)));

      String tooltip = minimap.tooltipTextForLine(3);
      assertTrue(tooltip.contains("timeline lines 2-7"));
      assertTrue(tooltip.contains("200 ms"));
      assertTrue(tooltip.contains("1 track"));
      assertTrue(tooltip.contains("1 action"));
      return null;
    });
  }

  @Test
  void tooltipMarksFoldedTimelineBlocks() throws Exception {
    Assumptions.assumeTrue(toolkitAvailable, "JavaFX toolkit is unavailable in this environment");
    runFx(() -> {
      String script = "timeline {\n  move \"hero\" {\n    x: 0\n    dur: 50\n  }\n}\n";
      VnsCodeMinimap minimap = new VnsCodeMinimap(new CodeArea());
      minimap.setSnapshot(script, List.of(), List.of(),
          List.of(new VnsCodeMinimap.TimelineBlock(0, 5, true)));

      assertTrue(minimap.tooltipTextForLine(2).contains("folded"));
      return null;
    });
  }

  private static VnsCodeMinimap newMinimap(String text) {
    VnsCodeMinimap minimap = new VnsCodeMinimap(new CodeArea());
    minimap.setSnapshot(text, List.of(), List.of(), List.of());
    return minimap;
  }

  private static <T> T runFx(Callable<T> callable) throws Exception {
    FutureTask<T> task = new FutureTask<>(callable);
    Platform.runLater(task);
    return task.get(30, TimeUnit.SECONDS);
  }
}
