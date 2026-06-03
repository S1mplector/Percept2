package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StoryboardOverlayCatalogTest {

  @TempDir
  Path tempDir;

  @Test
  void scanAcceptsQuotedManualFolderOverride() throws Exception {
    Path projectRoot = Files.createDirectory(tempDir.resolve("game-project"));
    Path storyboardDir = Files.createDirectories(projectRoot.resolve("storyboard"));
    Path frame = storyboardDir.resolve("frame-01.png");
    Files.writeString(frame, "png");

    StoryboardOverlayCatalog.ScanResult result =
        StoryboardOverlayCatalog.scan(projectRoot, "'" + storyboardDir.toString() + "'");

    assertEquals("Manual folder: storyboard", result.sourceLabel());
    assertEquals(1, result.frames().size());
    assertEquals(frame.toAbsolutePath().normalize(), result.frames().get(0));
  }

  @Test
  void scanIncludesDocumentedBitmapAndGifStoryboardFormats() throws Exception {
    Path projectRoot = Files.createDirectory(tempDir.resolve("format-project"));
    Path storyboardDir = Files.createDirectories(projectRoot.resolve("storyboard"));
    Path bmp = storyboardDir.resolve("frame-01.bmp");
    Path gif = storyboardDir.resolve("frame-02.gif");
    Files.writeString(bmp, "bmp");
    Files.writeString(gif, "gif");

    StoryboardOverlayCatalog.ScanResult result = StoryboardOverlayCatalog.scan(projectRoot, "");

    assertEquals(2, result.frames().size());
    assertEquals(bmp.toAbsolutePath().normalize(), result.frames().get(0));
    assertEquals(gif.toAbsolutePath().normalize(), result.frames().get(1));
  }
}
