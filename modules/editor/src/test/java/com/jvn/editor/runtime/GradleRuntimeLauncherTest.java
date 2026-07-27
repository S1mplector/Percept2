package com.jvn.editor.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GradleRuntimeLauncherTest {
  @TempDir Path tempDirectory;

  @Test
  void commandPlacesPerformanceFlagsBeforeTaskAndPreservesTaskArguments() throws Exception {
    Path wrapper = Files.createFile(tempDirectory.resolve("gradlew"));
    RuntimeGradleOptions options =
        RuntimeGradleOptions.fastDefaults().withSharedDependencyCache(false);

    List<String> command =
        GradleRuntimeLauncher.command(
            tempDirectory.toFile(),
            wrapper.toFile(),
            ":runtime:run",
            new String[] {"--args=--project demo", "-x", "test"},
            options);

    assertEquals(wrapper.toAbsolutePath().toString(), command.get(0));
    assertTrue(command.contains("--daemon"));
    assertTrue(command.contains("--build-cache"));
    assertTrue(command.contains("--configuration-cache"));
    assertTrue(command.contains("--parallel"));
    assertTrue(command.contains("--max-workers=2"));
    assertTrue(command.indexOf("--configuration-cache") < command.indexOf(":runtime:run"));
    assertEquals("--args=--project demo", command.get(command.indexOf(":runtime:run") + 1));
    assertEquals(
        tempDirectory.resolve(".jvn-gradle-user-home").toAbsolutePath(),
        Path.of(command.get(command.indexOf("--gradle-user-home") + 1)));
  }

  @Test
  void compatibilityCommandDisablesOptimizationsAndFallsBackToGradleCli() {
    List<String> command =
        GradleRuntimeLauncher.command(
            tempDirectory.toFile(),
            new File(tempDirectory.toFile(), "missing-gradlew"),
            "run",
            null,
            RuntimeGradleOptions.compatibilityDefaults());

    assertEquals("gradle", command.get(0));
    assertTrue(command.contains("--no-daemon"));
    assertTrue(command.contains("--no-configuration-cache"));
    assertTrue(command.contains("--no-parallel"));
    assertFalse(command.stream().anyMatch(value -> value.startsWith("--max-workers=")));
  }

  @Test
  void fastLaunchUsesTheSharedGradleHome() {
    List<String> command =
        GradleRuntimeLauncher.command(
            tempDirectory.toFile(),
            null,
            ":runtime:classes",
            new String[0],
            RuntimeGradleOptions.fastDefaults());

    assertEquals(
        RuntimeGradleOptionsStore.sharedGradleHome().toAbsolutePath(),
        Path.of(command.get(command.indexOf("--gradle-user-home") + 1)));
  }
}
