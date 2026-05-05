package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GradleWorkspaceLayoutTest {

  @TempDir
  Path tempDir;

  @Test
  void defaultsToWorkspaceBuildDirectory() {
    assertEquals(
        tempDir.resolve("build").normalize(),
        GradleWorkspaceLayout.buildDir(tempDir));
  }

  @Test
  void resolvesRelativeBuildDirectoryFromGradleProperties() throws Exception {
    Files.writeString(tempDir.resolve("gradle.properties"), "jvnBuildDir=.jvn-build/out\n");

    assertEquals(
        tempDir.resolve(".jvn-build/out").normalize(),
        GradleWorkspaceLayout.buildDir(tempDir));
  }

  @Test
  void resolvesAbsoluteBuildDirectoryFromGradleProperties() throws Exception {
    Path externalBuildDir = Files.createDirectories(tempDir.resolve("external-build")).toAbsolutePath().normalize();
    Files.writeString(tempDir.resolve("gradle.properties"), "jvnBuildDir=" + externalBuildDir + "\n");

    assertEquals(externalBuildDir, GradleWorkspaceLayout.buildDir(tempDir));
  }
}
