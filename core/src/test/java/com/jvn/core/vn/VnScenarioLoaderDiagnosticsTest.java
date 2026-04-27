package com.jvn.core.vn;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.assets.FilesystemAssetManager;
import com.jvn.core.vn.script.VnScriptParser;

/**
 * Verifies that {@link VnScenarioLoader#load(String)} surfaces diagnostic detail
 * (the underlying cause + the candidate paths it tried) when no script can be opened,
 * rather than the bare "Script not found" message.
 */
class VnScenarioLoaderDiagnosticsTest {

  @TempDir
  Path tempProjectRoot;

  @Test
  void missingScriptIncludesUnderlyingCauseAndTriedCandidates() {
    VnScenarioLoader loader = new VnScenarioLoader(
        new AssetCatalog(new FilesystemAssetManager(tempProjectRoot)),
        new VnScriptParser(),
        "game/scripts/");

    IOException ex = assertThrows(IOException.class, () -> loader.load("story/does_not_exist.vns"));

    String msg = ex.getMessage();
    assertNotNull(msg);
    assertTrue(msg.contains("does_not_exist.vns"), "message should reference the script name: " + msg);
    assertTrue(msg.contains("Tried:"), "message should list the candidate paths tried: " + msg);
    // The original IO cause should be chained (NoSuchFileException from the filesystem).
    assertNotNull(ex.getCause(), "the underlying cause must be preserved");
    assertNotEquals(ex.getMessage(), ex.getCause().getMessage(),
        "wrapper message should add context beyond the bare cause");
  }

  @Test
  void blankScriptNameProducesScriptNotFoundError() {
    VnScenarioLoader loader = new VnScenarioLoader(
        new AssetCatalog(new FilesystemAssetManager(tempProjectRoot)),
        new VnScriptParser(),
        "game/scripts/");
    // Empty name produces an empty candidate list; load() should still throw IOException
    // (no underlying cause, no "Tried" since nothing was attempted).
    IOException ex = assertThrows(IOException.class, () -> loader.load(""));
    assertNotNull(ex.getMessage());
  }
}
