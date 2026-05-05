package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GameBuildPublisherViewTest {

  @TempDir
  Path tempDir;

  @Test
  void buildCliCommandQuotesValuesThatNeedShellEscaping() {
    String command = GameBuildPublisherView.buildCliCommand(
        "assembleJvnGamePortableCurrent",
        List.of(
            "-PjvnGameProject=/tmp/My Game",
            "-PjvnGameName=Summer's End",
            "-PjvnPackageMode=portable"));

    assertEquals(
        "./jvnw gradle assembleJvnGamePortableCurrent '-PjvnGameProject=/tmp/My Game' '-PjvnGameName=Summer'\"'\"'s End' -PjvnPackageMode=portable",
        command);
  }

  @Test
  void summarizeArtifactsOrdersNewestFirst() throws Exception {
    Path oldArtifact = Files.writeString(tempDir.resolve("old.zip"), "old");
    Path newArtifact = Files.writeString(tempDir.resolve("new.zip"), "newer");
    oldArtifact.toFile().setLastModified(1_000L);
    newArtifact.toFile().setLastModified(2_000L);

    List<GameBuildPublisherView.ArtifactSummary> artifacts =
        GameBuildPublisherView.summarizeArtifacts(tempDir.toFile());

    assertEquals("new.zip", artifacts.get(0).name());
    assertEquals("old.zip", artifacts.get(1).name());
  }

  @Test
  void artifactInventoryUsesCompactSizeLabels() {
    String text = GameBuildPublisherView.formatArtifactInventory(List.of(
        new GameBuildPublisherView.ArtifactSummary("game.zip", 1536L, 2_000L)));

    assertTrue(text.contains("game.zip"));
    assertTrue(text.contains("1.5 KB"));
  }
}
