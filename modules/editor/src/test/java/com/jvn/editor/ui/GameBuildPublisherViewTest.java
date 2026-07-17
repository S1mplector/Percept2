package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
    Files.writeString(tempDir.resolve("new.zip.sha256"), "abc  new.zip\n");
    oldArtifact.toFile().setLastModified(1_000L);
    newArtifact.toFile().setLastModified(2_000L);

    List<GameBuildPublisherView.ArtifactSummary> artifacts =
        GameBuildPublisherView.summarizeArtifacts(tempDir.toFile());

    assertEquals(2, artifacts.size());
    assertEquals("new.zip", artifacts.get(0).name());
    assertTrue(artifacts.get(0).checksumAvailable());
    assertEquals("old.zip", artifacts.get(1).name());
  }

  @Test
  void artifactInventoryUsesCompactSizeLabels() {
    String text = GameBuildPublisherView.formatArtifactInventory(List.of(
        new GameBuildPublisherView.ArtifactSummary("game.zip", 1536L, 2_000L)));

    assertTrue(text.contains("game.zip"));
    assertTrue(text.contains("1.5 KB"));
  }

  @Test
  void artifactInventoryShowsChecksumMarkerWhenAvailable() {
    String text = GameBuildPublisherView.formatArtifactInventory(List.of(
        new GameBuildPublisherView.ArtifactSummary("game.zip", 1536L, 2_000L, true)));

    assertTrue(text.contains("sha256"));
  }

  @Test
  void validationSummaryShowsEveryBlockingIssue() {
    String text = GameBuildPublisherView.formatValidationMessages(
        "Blocked",
        List.of("Missing jvn.project", "Output folder is read-only"));

    assertEquals("Blocked:\n• Missing jvn.project\n• Output folder is read-only", text);
  }

  @Test
  void desktopShippingOffersOnlyBackedTargets() {
    List<String> targets = GameBuildPublisherView.supportedDesktopTargetTokens();

    assertTrue(targets.containsAll(List.of("windows-x64", "linux-x64", "macos-x64", "macos-aarch64", "all")));
    assertFalse(targets.contains("linux-aarch64"));
  }

  @Test
  void releaseConfigTemplateStartsSafeAndCrossPlatform() {
    String config = GameBuildPublisherView.defaultReleaseConfigText("Lantern House");

    assertTrue(config.contains("defaultProfile=release"));
    assertTrue(config.contains("profile.release.description=Lantern House built with JVN."));
    assertTrue(config.contains("profile.release.mac.sign=false"));
    assertTrue(config.contains("profile.release.win.sign=false"));
    assertTrue(config.contains("profile.release.publish.command.1"));
  }
}
