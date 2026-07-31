package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectIconPreferencesTest {
  @TempDir Path temporaryDirectory;

  @Test
  void readsTheCompleteHubManagedProfile() throws Exception {
    Path file = temporaryDirectory.resolve("project-icons.properties");
    Files.writeString(file, """
        icons.source=theme
        icons.theme=Tango
        icons.size=21
        icons.folderVariants=false
        icons.fileTypeVariants=true
        icons.inheritTheme=false
        icons.bundledFallback=false
        icons.smoothScaling=false
        """);

    ProjectIconPreferences.Options options = ProjectIconPreferences.load(file);

    assertEquals(ProjectIconPreferences.Source.THEME, options.source());
    assertEquals("Tango", options.theme());
    assertEquals(21, options.size());
    assertFalse(options.folderVariants());
    assertTrue(options.fileTypeVariants());
    assertFalse(options.inheritTheme());
    assertFalse(options.bundledFallback());
    assertFalse(options.smoothScaling());
  }

  @Test
  void outOfRangeSizeIsClamped() throws Exception {
    Path file = temporaryDirectory.resolve("project-icons.properties");
    Files.writeString(file, "icons.size=2\n");

    assertEquals(12, ProjectIconPreferences.load(file).size());
  }

  @Test
  void recognizesJvnDefaultsAndLegacyBundledAliases() throws Exception {
    Path file = temporaryDirectory.resolve("project-icons.properties");
    Files.writeString(file, "icons.source=jvn-defaults\n");
    assertEquals(ProjectIconPreferences.Source.BUNDLED, ProjectIconPreferences.load(file).source());

    Files.writeString(file, "icons.source=material\n");
    assertEquals(ProjectIconPreferences.Source.BUNDLED, ProjectIconPreferences.load(file).source());
  }
}
