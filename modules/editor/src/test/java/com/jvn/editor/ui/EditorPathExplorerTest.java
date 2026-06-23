package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EditorPathExplorerTest {
  @Test
  void fileTargetsOpenTheirParentAndRemainSelected(@TempDir Path tempDir) throws Exception {
    Path file = Files.writeString(tempDir.resolve("runtime.log"), "hello");

    assertEquals(tempDir.toFile().getAbsoluteFile(), EditorPathExplorer.initialDirectory(file.toFile()));
    assertEquals(file.toFile().getAbsoluteFile(), EditorPathExplorer.initialSelection(file.toFile()));
  }

  @Test
  void directoryTargetsOpenTheDirectoryWithoutASelection(@TempDir Path tempDir) {
    assertEquals(tempDir.toFile().getAbsoluteFile(), EditorPathExplorer.initialDirectory(tempDir.toFile()));
    assertNull(EditorPathExplorer.initialSelection(tempDir.toFile()));
  }

  @Test
  void missingTargetsRevealTheirNearestExistingParent(@TempDir Path tempDir) {
    assertNull(EditorPathExplorer.initialDirectory(null));
    assertNull(EditorPathExplorer.initialSelection(null));
    assertEquals(tempDir.toFile().getAbsoluteFile(),
        EditorPathExplorer.initialDirectory(tempDir.resolve("missing.txt").toFile()));
    assertNull(EditorPathExplorer.initialSelection(tempDir.resolve("missing.txt").toFile()));
  }
}
