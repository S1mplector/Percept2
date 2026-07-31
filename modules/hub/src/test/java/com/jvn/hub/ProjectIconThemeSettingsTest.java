package com.jvn.hub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectIconThemeSettingsTest {
  @TempDir Path temporaryDirectory;

  @Test
  void missingProfileUsesDesktopThemeWithSafeFallbacks() {
    ProjectIconThemeSettings.Options options = ProjectIconThemeSettings.load(
        temporaryDirectory.resolve("missing.properties"));

    assertEquals(ProjectIconThemeSettings.Source.DESKTOP, options.source());
    assertEquals(18, options.size());
    assertTrue(options.folderVariants());
    assertTrue(options.fileTypeVariants());
    assertTrue(options.inheritTheme());
    assertTrue(options.bundledFallback());
    assertTrue(options.smoothScaling());
  }

  @Test
  void completeProfileRoundTripsForTheEditor() throws Exception {
    Path file = temporaryDirectory.resolve("nested/project-icons.properties");
    ProjectIconThemeSettings.Options requested = new ProjectIconThemeSettings.Options(
        ProjectIconThemeSettings.Source.THEME,
        "Tango",
        22,
        false,
        true,
        false,
        false,
        false);

    ProjectIconThemeSettings.save(file, requested);

    assertEquals(requested, ProjectIconThemeSettings.load(file));
    String stored = Files.readString(file);
    assertTrue(stored.contains("icons.source=theme"));
    assertTrue(stored.contains("icons.theme=Tango"));
    assertTrue(stored.contains("icons.size=22"));
  }

  @Test
  void malformedValuesAreNormalized() throws Exception {
    Path file = temporaryDirectory.resolve("project-icons.properties");
    Files.writeString(file, """
        icons.source=material
        icons.size=400
        icons.folderVariants=no
        icons.fileTypeVariants=invalid
        """);

    ProjectIconThemeSettings.Options options = ProjectIconThemeSettings.load(file);

    assertEquals(ProjectIconThemeSettings.Source.BUNDLED, options.source());
    assertEquals(ProjectIconThemeSettings.MAX_ICON_SIZE, options.size());
    assertFalse(options.folderVariants());
    assertTrue(options.fileTypeVariants());
  }

  @Test
  void gtkThemeSettingSupportsQuotedDesktopValues() {
    assertEquals("Mint-X-Grey", ProjectIconThemeSettings.parseThemeSetting(
        "[Settings]\ngtk-icon-theme-name='Mint-X-Grey'\n"));
    assertFalse(ProjectIconThemeSettings.detectedDesktopTheme().isBlank());
  }
}
