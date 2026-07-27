package com.jvn.editor.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Persists the launch controls shown directly in the runtime window. */
public final class RuntimeGradleOptionsStore {
  static final String KEY_REUSE_DAEMON = "reuseDaemon";
  static final String KEY_BUILD_CACHE = "buildCache";
  static final String KEY_CONFIGURATION_CACHE = "configurationCache";
  static final String KEY_PARALLEL_EXECUTION = "parallelExecution";
  static final String KEY_SHARED_DEPENDENCY_CACHE = "sharedDependencyCache";
  static final String KEY_MAX_WORKERS = "maxWorkers";

  private final Path optionsFile;

  public RuntimeGradleOptionsStore() {
    this(defaultOptionsPath());
  }

  public RuntimeGradleOptionsStore(Path optionsFile) {
    this.optionsFile = optionsFile;
  }

  public RuntimeGradleOptions load() {
    Properties properties = new Properties();
    if (optionsFile != null && Files.isRegularFile(optionsFile)) {
      try (InputStream input = Files.newInputStream(optionsFile)) {
        properties.load(input);
      } catch (IOException | IllegalArgumentException ignored) {
        return RuntimeGradleOptions.fastDefaults();
      }
    }
    RuntimeGradleOptions defaults = RuntimeGradleOptions.fastDefaults();
    return new RuntimeGradleOptions(
        booleanValue(properties, KEY_REUSE_DAEMON, defaults.reuseDaemon()),
        booleanValue(properties, KEY_BUILD_CACHE, defaults.buildCache()),
        booleanValue(properties, KEY_CONFIGURATION_CACHE, defaults.configurationCache()),
        booleanValue(properties, KEY_PARALLEL_EXECUTION, defaults.parallelExecution()),
        booleanValue(properties, KEY_SHARED_DEPENDENCY_CACHE, defaults.sharedDependencyCache()),
        intValue(properties, KEY_MAX_WORKERS, defaults.maxWorkers()));
  }

  public void save(RuntimeGradleOptions options) throws IOException {
    if (optionsFile == null || options == null) return;
    Path parent = optionsFile.getParent();
    if (parent != null) Files.createDirectories(parent);
    Properties properties = new Properties();
    properties.setProperty(KEY_REUSE_DAEMON, Boolean.toString(options.reuseDaemon()));
    properties.setProperty(KEY_BUILD_CACHE, Boolean.toString(options.buildCache()));
    properties.setProperty(KEY_CONFIGURATION_CACHE, Boolean.toString(options.configurationCache()));
    properties.setProperty(KEY_PARALLEL_EXECUTION, Boolean.toString(options.parallelExecution()));
    properties.setProperty(
        KEY_SHARED_DEPENDENCY_CACHE, Boolean.toString(options.sharedDependencyCache()));
    properties.setProperty(KEY_MAX_WORKERS, Integer.toString(options.maxWorkers()));
    try (OutputStream output = Files.newOutputStream(optionsFile)) {
      properties.store(output, "JVN Runtime Gradle Launch Options");
    }
  }

  public Path optionsFile() {
    return optionsFile;
  }

  public static Path sharedGradleHome() {
    String configuredHome = System.getenv("GRADLE_USER_HOME");
    if (configuredHome != null && !configuredHome.isBlank()) {
      return Path.of(configuredHome.trim()).toAbsolutePath().normalize();
    }
    return Path.of(System.getProperty("user.home", "."), ".gradle");
  }

  static Path defaultOptionsPath() {
    return Path.of(
        System.getProperty("user.home", "."),
        ".jvn-editor",
        "runtime-gradle.properties");
  }

  private static boolean booleanValue(
      Properties properties, String key, boolean fallback) {
    String raw = properties.getProperty(key);
    if (raw == null || raw.isBlank()) return fallback;
    String normalized = raw.trim().toLowerCase(java.util.Locale.ROOT);
    if ("true".equals(normalized)) return true;
    if ("false".equals(normalized)) return false;
    return fallback;
  }

  private static int intValue(Properties properties, String key, int fallback) {
    String raw = properties.getProperty(key);
    if (raw == null || raw.isBlank()) return fallback;
    try {
      return Integer.parseInt(raw.trim());
    } catch (NumberFormatException ignored) {
      return fallback;
    }
  }
}
