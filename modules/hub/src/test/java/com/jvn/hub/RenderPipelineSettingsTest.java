package com.jvn.hub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RenderPipelineSettingsTest {
  @TempDir Path temporaryDirectory;

  @Test
  void missingPreferenceDefaultsToAdaptiveSelection() {
    assertEquals(
        RenderPipelineSettings.Mode.AUTO,
        RenderPipelineSettings.load(temporaryDirectory.resolve("missing.properties")));
  }

  @Test
  void recognizesExistingGraphicsModeAliases() throws Exception {
    Path preferences = temporaryDirectory.resolve("editor-preferences.properties");
    Files.writeString(preferences, "graphics.mode=prefer-gpu\n");

    assertEquals(
        RenderPipelineSettings.Mode.HARDWARE,
        RenderPipelineSettings.load(preferences));
  }

  @Test
  void savingModePreservesUnrelatedEditorPreferences() throws Exception {
    Path preferences = temporaryDirectory.resolve("nested/editor-preferences.properties");
    Files.createDirectories(preferences.getParent());
    Files.writeString(preferences, "editor.theme=dark\neditorMaxFps=120\ngraphics.mode=auto\n");

    RenderPipelineSettings.save(preferences, RenderPipelineSettings.Mode.SOFTWARE);

    Properties stored = new Properties();
    try (var input = Files.newInputStream(preferences)) {
      stored.load(input);
    }
    assertEquals("dark", stored.getProperty("editor.theme"));
    assertEquals("120", stored.getProperty("editorMaxFps"));
    assertEquals("software", stored.getProperty("graphics.mode"));
  }

  @Test
  void managedLaunchesReceiveTheSelectedMode() {
    Map<String, String> environment = new HashMap<>();
    environment.put(RenderPipelineSettings.GRAPHICS_MODE_ENVIRONMENT, "stale-value");

    boolean applied = RenderPipelineSettings.applyLaunchEnvironment(
        environment,
        List.of("/engine/scripts/launch-app.sh", "editor"),
        RenderPipelineSettings.Mode.HARDWARE);

    assertTrue(applied);
    assertEquals(
        "hardware",
        environment.get(RenderPipelineSettings.GRAPHICS_MODE_ENVIRONMENT));
    assertTrue(RenderPipelineSettings.isManagedLaunchCommand(
        List.of("./gradlew", ":editor:run")));
    assertTrue(RenderPipelineSettings.isManagedLaunchCommand(
        List.of("gradlew.bat", ":editor:runLauncher")));
    assertTrue(RenderPipelineSettings.isManagedLaunchCommand(
        List.of("./gradlew", ":runtime:run")));
  }

  @Test
  void unrelatedHubTasksDoNotReceiveGraphicsOverrides() {
    Map<String, String> environment = new HashMap<>();

    boolean applied = RenderPipelineSettings.applyLaunchEnvironment(
        environment,
        List.of("./gradlew", "build"),
        RenderPipelineSettings.Mode.SOFTWARE);

    assertFalse(applied);
    assertFalse(environment.containsKey(RenderPipelineSettings.GRAPHICS_MODE_ENVIRONMENT));
  }

  @Test
  void reportsBackendOrderForEachPlatformProfile() {
    assertEquals(
        "Direct3D → OpenGL ES2 → software",
        RenderPipelineSettings.Mode.HARDWARE.backendOrder("Windows 11"));
    assertEquals(
        "OpenGL ES2 → software",
        RenderPipelineSettings.Mode.HARDWARE.backendOrder("Linux"));
    assertEquals(
        "Software renderer only",
        RenderPipelineSettings.Mode.SOFTWARE.backendOrder("macOS"));
  }
}
