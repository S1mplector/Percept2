package com.jvn.core.vn.ui;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class VnCursorConfigLoaderTest {

  @Test
  void loadsCursorFromDefaultSettingsPath() throws Exception {
    Path project = Files.createTempDirectory("jvn-cursor-default");
    Path settings = project.resolve("config/settings/vn.settings");
    Files.createDirectories(settings.getParent());
    Files.writeString(settings, """
        cursorAsset=assets/ui/cursor/cursor.png
        cursorHotspotX=0
        cursorHotspotY=0
        """);

    VnCursorConfigLoader.LoadResult result = VnCursorConfigLoader.loadFromProjectRootWithDiagnostics(project.toFile());
    VnCursorConfigLoader.VnCursorConfig config = result.config();

    assertNotNull(config);
    assertEquals("assets/ui/cursor/cursor.png", config.assetPath());
    assertEquals(0.0, config.hotspotX(), 0.0001);
    assertEquals(0.0, config.hotspotY(), 0.0001);
    assertTrue(result.diagnostics().isEmpty());
  }

  @Test
  void settingsFileInManifestOverridesDefaultPath() throws Exception {
    Path project = Files.createTempDirectory("jvn-cursor-manifest");
    Files.writeString(project.resolve("jvn.project"), "settingsFile=config/custom/runtime.settings\n");
    Path settings = project.resolve("config/custom/runtime.settings");
    Files.createDirectories(settings.getParent());
    Files.writeString(settings, """
        cursor.asset=assets/import/asset_pack_-_0.0.6/cursor.png
        cursor.hotspotX=4
        cursor.hotspotY=6
        """);

    VnCursorConfigLoader.LoadResult result = VnCursorConfigLoader.loadFromProjectRootWithDiagnostics(project.toFile());
    VnCursorConfigLoader.VnCursorConfig config = result.config();

    assertNotNull(config);
    assertEquals("assets/import/asset_pack_-_0.0.6/cursor.png", config.assetPath());
    assertEquals(4.0, config.hotspotX(), 0.0001);
    assertEquals(6.0, config.hotspotY(), 0.0001);
  }

  @Test
  void invalidHotspotFallsBackToZeroWithDiagnostic() throws Exception {
    Path project = Files.createTempDirectory("jvn-cursor-invalid");
    Path settings = project.resolve("config/settings/vn.settings");
    Files.createDirectories(settings.getParent());
    Files.writeString(settings, """
        cursorAsset=assets/ui/cursor/cursor.png
        cursorHotspotX=not_a_number
        cursorHotspotY=2
        """);

    VnCursorConfigLoader.LoadResult result = VnCursorConfigLoader.loadFromProjectRootWithDiagnostics(project.toFile());
    VnCursorConfigLoader.VnCursorConfig config = result.config();

    assertNotNull(config);
    assertEquals(0.0, config.hotspotX(), 0.0001);
    assertEquals(2.0, config.hotspotY(), 0.0001);
    assertTrue(result.diagnostics().stream().anyMatch(d -> d.contains("cursorHotspotX")));
  }

  @Test
  void returnsNullWhenCursorAssetMissing() throws Exception {
    Path project = Files.createTempDirectory("jvn-cursor-missing");
    Path settings = project.resolve("config/settings/vn.settings");
    Files.createDirectories(settings.getParent());
    Files.writeString(settings, "textSpeed=35\n");

    VnCursorConfigLoader.LoadResult result = VnCursorConfigLoader.loadFromProjectRootWithDiagnostics(project.toFile());
    assertNull(result.config());
  }
}
