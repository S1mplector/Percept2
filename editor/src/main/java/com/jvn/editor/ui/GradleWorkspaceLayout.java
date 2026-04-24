package com.jvn.editor.ui;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

final class GradleWorkspaceLayout {
  private static final String BUILD_DIR_PROPERTY = "jvnBuildDir";
  private static final String DEFAULT_BUILD_DIR = "build";

  private GradleWorkspaceLayout() {}

  static Path buildDir(Path workspaceRoot) {
    if (workspaceRoot == null) return Path.of(DEFAULT_BUILD_DIR);
    Path normalizedRoot = workspaceRoot.toAbsolutePath().normalize();
    String raw = readGradleProperty(normalizedRoot, BUILD_DIR_PROPERTY);
    if (raw == null || raw.isBlank()) {
      return normalizedRoot.resolve(DEFAULT_BUILD_DIR).normalize();
    }
    try {
      Path candidate = Path.of(raw.trim());
      return (candidate.isAbsolute() ? candidate : normalizedRoot.resolve(candidate)).normalize();
    } catch (Exception ignore) {
      return normalizedRoot.resolve(DEFAULT_BUILD_DIR).normalize();
    }
  }

  private static String readGradleProperty(Path workspaceRoot, String key) {
    if (workspaceRoot == null || key == null || key.isBlank()) return null;
    Path propsPath = workspaceRoot.resolve("gradle.properties");
    if (!Files.isRegularFile(propsPath)) return null;
    Properties props = new Properties();
    try (InputStream in = Files.newInputStream(propsPath)) {
      props.load(in);
      return props.getProperty(key);
    } catch (Exception ignore) {
      return null;
    }
  }
}
