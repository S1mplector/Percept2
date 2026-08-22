package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvn.fx.testkit.FxToolkit;
import com.jvn.fx.testkit.FxToolkitExtension;
import java.util.List;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FxToolkitExtension.class)
class VnsTimelineOutlineFxTest {
  @Test
  void computeTimelineOutlineEntriesListsBlocksInDocumentOrder() throws Exception {
    runFx(() -> {
      VnsCodeEditor editor = new VnsCodeEditor();
      editor.setText(
          "@label start\n"
              + "timeline {\n"
              + "  move \"hero\" {\n"
              + "    x: 100\n"
              + "    dur: 200\n"
              + "  }\n"
              + "}\n"
              + "\n"
              + "[say \"hero\" \"Hi\"]\n"
              + "\n"
              + "timeline {\n"
              + "  move \"hero\" {\n"
              + "    x: 0\n"
              + "    dur: 100\n"
              + "  }\n"
              + "}\n");

      List<VnsCodeEditor.TimelineOutlineEntry> entries = editor.computeTimelineOutlineEntries();
      assertEquals(2, entries.size());
      assertEquals(2, entries.get(0).oneBasedStartLine());
      assertTrue(entries.get(0).oneBasedEndLine() > entries.get(0).oneBasedStartLine());
      assertTrue(entries.get(1).oneBasedStartLine() > entries.get(0).oneBasedEndLine());
      return null;
    });
  }

  @Test
  void computeTimelineOutlineEntriesReturnsEmptyForScriptWithoutTimelines() throws Exception {
    runFx(() -> {
      VnsCodeEditor editor = new VnsCodeEditor();
      editor.setText("@label start\n[say \"hero\" \"Hi\"]\n");

      assertTrue(editor.computeTimelineOutlineEntries().isEmpty());
      return null;
    });
  }

  @Test
  void formatTimelineOutlineDurationFormatsMillisecondsAndSeconds() {
    assertEquals("200 ms", VnsCodeEditor.formatTimelineOutlineDuration(
        "timeline {\n  move \"hero\" {\n    x: 100\n    dur: 200\n  }\n  wait 200\n}\n"));
    assertEquals(
        "1.50 s",
        VnsCodeEditor.formatTimelineOutlineDuration(
            "timeline {\n  move \"hero\" {\n    x: 100\n    dur: 1500\n  }\n  wait 1500\n}\n"));
  }

  @Test
  void formatTimelineOutlineDurationReturnsNullForUnparsableBlock() {
    assertNull(VnsCodeEditor.formatTimelineOutlineDuration(""));
    assertNull(VnsCodeEditor.formatTimelineOutlineDuration(null));
  }

  @Test
  void outlineViewClearsToPlaceholderWithNoActiveFile() throws Exception {
    runFx(() -> {
      VnsTimelineOutlineView view = new VnsTimelineOutlineView();
      view.clear();
      assertNotNull(view.lookup(".vns-timeline-outline-list"));
      return null;
    });
  }

  private static <T> T runFx(Callable<T> callable) throws Exception {
    return FxToolkit.runFx(callable);
  }
}
