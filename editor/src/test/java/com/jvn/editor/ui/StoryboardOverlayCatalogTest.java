package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StoryboardOverlayCatalogTest {
  @TempDir
  Path tempDir;

  @Test
  void scanUsesManualFolderWhenProvided() throws Exception {
    Path manualFolder = Files.createDirectories(tempDir.resolve("boards"));
    Files.write(manualFolder.resolve("frame_001.png"), new byte[] {0});

    StoryboardOverlayCatalog.ScanResult result =
        StoryboardOverlayCatalog.scan(tempDir, "boards");

    assertEquals(1, result.frames().size());
    assertTrue(result.sourceLabel().contains("boards"));
    assertTrue(result.statusMessage().contains("Loaded 1 storyboard frame"));
  }

  @Test
  void scanAutoDetectsStoryboardFoldersBeforeFallbackProjectImages() throws Exception {
    Path storyboardFolder = Files.createDirectories(tempDir.resolve("assets/storyboards"));
    Files.write(storyboardFolder.resolve("shot_010.png"), new byte[] {0});
    Path charactersFolder = Files.createDirectories(tempDir.resolve("assets/characters"));
    Files.write(charactersFolder.resolve("lavender.png"), new byte[] {0});

    StoryboardOverlayCatalog.ScanResult result =
        StoryboardOverlayCatalog.scan(tempDir, "");

    assertEquals(1, result.frames().size());
    assertTrue(result.sourceLabel().toLowerCase().contains("auto"));
    assertTrue(result.frames().get(0).endsWith("shot_010.png"));
  }

  @Test
  void scanFallsBackToProjectImagesWhenStoryboardFolderIsMissing() throws Exception {
    Path referencesFolder = Files.createDirectories(tempDir.resolve("assets/reference"));
    Files.write(referencesFolder.resolve("shot_board.jpg"), new byte[] {0});
    Path charactersFolder = Files.createDirectories(tempDir.resolve("assets/characters"));
    Files.write(charactersFolder.resolve("lavender.png"), new byte[] {0});

    StoryboardOverlayCatalog.ScanResult result =
        StoryboardOverlayCatalog.scan(tempDir, "");

    assertEquals(2, result.frames().size());
    assertTrue(result.statusMessage().contains("Showing project images"));
    assertTrue(result.frames().get(0).endsWith("shot_board.jpg"));
  }
}
