package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvn.fx.testkit.FxToolkit;
import com.jvn.fx.testkit.FxToolkitExtension;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(FxToolkitExtension.class)
class FileEditorTabTimelineFoldPersistenceTest {

  private static final String SCRIPT =
      "@label start\n"
          + "timeline {\n"
          + "  move \"hero\" {\n"
          + "    x: 100\n"
          + "    dur: 200\n"
          + "  }\n"
          + "}\n";

  @Test
  void reopeningAScriptRestoresItsFoldedTimelineBlocks(@TempDir Path projectRoot) throws Exception {
    Path scripts = Files.createDirectories(projectRoot.resolve("scripts"));
    Path script = scripts.resolve("intro.vns");
    Files.writeString(script, SCRIPT);

    FxToolkit.runFx(() -> {
      FileEditorTab tab = new FileEditorTab(script.toFile());
      try {
        tab.setProjectRoot(projectRoot.toFile());
        VnsCodeEditor editor = (VnsCodeEditor) tab.getEditorNode();
        editor.toggleTimelineFoldAtLineForTest(1);
        assertEquals(1, editor.exportFoldedTimelineBlocks().size());
      } finally {
        tab.dispose(); // saves fold state as the tab closes
      }
      return null;
    });

    FxToolkit.runFx(() -> {
      FileEditorTab reopened = new FileEditorTab(script.toFile());
      try {
        reopened.setProjectRoot(projectRoot.toFile());
        VnsCodeEditor editor = (VnsCodeEditor) reopened.getEditorNode();
        assertEquals(1, editor.exportFoldedTimelineBlocks().size());
      } finally {
        reopened.dispose();
      }
      return null;
    });
  }

  @Test
  void unrelatedScriptsDoNotInheritEachOthersFoldState(@TempDir Path projectRoot) throws Exception {
    Path scripts = Files.createDirectories(projectRoot.resolve("scripts"));
    Path scriptA = scripts.resolve("a.vns");
    Path scriptB = scripts.resolve("b.vns");
    Files.writeString(scriptA, SCRIPT);
    Files.writeString(scriptB, SCRIPT);

    FxToolkit.runFx(() -> {
      FileEditorTab tabA = new FileEditorTab(scriptA.toFile());
      try {
        tabA.setProjectRoot(projectRoot.toFile());
        ((VnsCodeEditor) tabA.getEditorNode()).toggleTimelineFoldAtLineForTest(1);
      } finally {
        tabA.dispose();
      }
      return null;
    });

    FxToolkit.runFx(() -> {
      FileEditorTab tabB = new FileEditorTab(scriptB.toFile());
      try {
        tabB.setProjectRoot(projectRoot.toFile());
        VnsCodeEditor editorB = (VnsCodeEditor) tabB.getEditorNode();
        assertTrue(editorB.exportFoldedTimelineBlocks().isEmpty());
      } finally {
        tabB.dispose();
      }
      return null;
    });
  }
}
