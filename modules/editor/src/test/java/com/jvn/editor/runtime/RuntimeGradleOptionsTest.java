package com.jvn.editor.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class RuntimeGradleOptionsTest {
  @Test
  void fastPresetMatchesOptimizedLauncherPolicy() {
    RuntimeGradleOptions options = RuntimeGradleOptions.fastDefaults();

    assertTrue(options.reuseDaemon());
    assertTrue(options.buildCache());
    assertTrue(options.configurationCache());
    assertTrue(options.parallelExecution());
    assertTrue(options.sharedDependencyCache());
    assertEquals(2, options.maxWorkers());
    assertEquals(
        List.of(
            "--daemon",
            "--build-cache",
            "--configuration-cache",
            "--parallel",
            "--max-workers=2"),
        options.performanceArguments());
    assertEquals("Fast", options.shortLabel());
  }

  @Test
  void compatibilityPresetAvoidsStatefulGradleFeatures() {
    RuntimeGradleOptions options = RuntimeGradleOptions.compatibilityDefaults();

    assertFalse(options.reuseDaemon());
    assertTrue(options.buildCache());
    assertFalse(options.configurationCache());
    assertFalse(options.parallelExecution());
    assertFalse(options.sharedDependencyCache());
    assertEquals(
        List.of("--no-daemon", "--build-cache", "--no-configuration-cache", "--no-parallel"),
        options.performanceArguments());
    assertEquals("Compatible", options.shortLabel());
  }

  @Test
  void individualChangesProduceCustomPresetWithoutMutatingOriginal() {
    RuntimeGradleOptions fast = RuntimeGradleOptions.fastDefaults();
    RuntimeGradleOptions changed = fast.withMaxWorkers(4).withConfigurationCache(false);

    assertEquals(2, fast.maxWorkers());
    assertTrue(fast.configurationCache());
    assertEquals(4, changed.maxWorkers());
    assertFalse(changed.configurationCache());
    assertEquals("Custom", changed.shortLabel());
  }
}
