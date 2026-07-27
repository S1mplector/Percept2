package com.jvn.editor.runtime;

import java.util.List;

/**
 * Gradle launch policy used by the runtime console.
 *
 * <p>The fast defaults mirror the source launcher's Gradle fallback: keep a warm daemon, reuse
 * build and configuration caches, allow parallel work, and cap workers so launching a game does
 * not starve the editor UI.
 */
public record RuntimeGradleOptions(
    boolean reuseDaemon,
    boolean buildCache,
    boolean configurationCache,
    boolean parallelExecution,
    boolean sharedDependencyCache,
    int maxWorkers) {

  public static final int AUTOMATIC_WORKERS = 0;
  public static final int DEFAULT_MAX_WORKERS = 2;

  public RuntimeGradleOptions {
    maxWorkers = normalizeWorkers(maxWorkers);
  }

  public static RuntimeGradleOptions fastDefaults() {
    return new RuntimeGradleOptions(true, true, true, true, true, DEFAULT_MAX_WORKERS);
  }

  public static RuntimeGradleOptions compatibilityDefaults() {
    return new RuntimeGradleOptions(false, true, false, false, false, AUTOMATIC_WORKERS);
  }

  public RuntimeGradleOptions withReuseDaemon(boolean value) {
    return new RuntimeGradleOptions(
        value, buildCache, configurationCache, parallelExecution, sharedDependencyCache, maxWorkers);
  }

  public RuntimeGradleOptions withBuildCache(boolean value) {
    return new RuntimeGradleOptions(
        reuseDaemon, value, configurationCache, parallelExecution, sharedDependencyCache, maxWorkers);
  }

  public RuntimeGradleOptions withConfigurationCache(boolean value) {
    return new RuntimeGradleOptions(
        reuseDaemon, buildCache, value, parallelExecution, sharedDependencyCache, maxWorkers);
  }

  public RuntimeGradleOptions withParallelExecution(boolean value) {
    return new RuntimeGradleOptions(
        reuseDaemon, buildCache, configurationCache, value, sharedDependencyCache, maxWorkers);
  }

  public RuntimeGradleOptions withSharedDependencyCache(boolean value) {
    return new RuntimeGradleOptions(
        reuseDaemon, buildCache, configurationCache, parallelExecution, value, maxWorkers);
  }

  public RuntimeGradleOptions withMaxWorkers(int value) {
    return new RuntimeGradleOptions(
        reuseDaemon, buildCache, configurationCache, parallelExecution, sharedDependencyCache, value);
  }

  public List<String> performanceArguments() {
    java.util.ArrayList<String> args = new java.util.ArrayList<>();
    args.add(reuseDaemon ? "--daemon" : "--no-daemon");
    args.add(buildCache ? "--build-cache" : "--no-build-cache");
    args.add(configurationCache ? "--configuration-cache" : "--no-configuration-cache");
    args.add(parallelExecution ? "--parallel" : "--no-parallel");
    if (maxWorkers > AUTOMATIC_WORKERS) {
      args.add("--max-workers=" + maxWorkers);
    }
    return List.copyOf(args);
  }

  public boolean isFastPreset() {
    return equals(fastDefaults());
  }

  public boolean isCompatibilityPreset() {
    return equals(compatibilityDefaults());
  }

  public String shortLabel() {
    if (isFastPreset()) return "Fast";
    if (isCompatibilityPreset()) return "Compatible";
    return "Custom";
  }

  public String summary() {
    String workers = maxWorkers == AUTOMATIC_WORKERS ? "automatic workers" : maxWorkers + " workers";
    return (reuseDaemon ? "warm daemon" : "single-use daemon")
        + ", "
        + (buildCache ? "build cache" : "no build cache")
        + ", "
        + (configurationCache ? "configuration cache" : "no configuration cache")
        + ", "
        + (parallelExecution ? "parallel" : "serial")
        + ", "
        + workers
        + ", "
        + (sharedDependencyCache ? "shared dependencies" : "project dependencies");
  }

  private static int normalizeWorkers(int value) {
    if (value <= AUTOMATIC_WORKERS) return AUTOMATIC_WORKERS;
    return Math.min(value, 32);
  }
}
