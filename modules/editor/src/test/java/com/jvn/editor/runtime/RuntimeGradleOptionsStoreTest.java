package com.jvn.editor.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeGradleOptionsStoreTest {
  @TempDir Path tempDirectory;

  @Test
  void missingSettingsUseFastDefaults() {
    RuntimeGradleOptionsStore store =
        new RuntimeGradleOptionsStore(tempDirectory.resolve("runtime-gradle.properties"));

    assertEquals(RuntimeGradleOptions.fastDefaults(), store.load());
  }

  @Test
  void selectedRuntimeOptionsRoundTrip() throws Exception {
    Path optionsFile = tempDirectory.resolve("nested/runtime-gradle.properties");
    RuntimeGradleOptionsStore store = new RuntimeGradleOptionsStore(optionsFile);
    RuntimeGradleOptions expected =
        RuntimeGradleOptions.fastDefaults()
            .withReuseDaemon(false)
            .withSharedDependencyCache(false)
            .withMaxWorkers(8);

    store.save(expected);

    assertEquals(expected, store.load());
    assertEquals(optionsFile, store.optionsFile());
  }

  @Test
  void malformedValuesFallBackIndividually() throws Exception {
    Path optionsFile = tempDirectory.resolve("runtime-gradle.properties");
    Files.writeString(
        optionsFile,
        "reuseDaemon=maybe\n"
            + "buildCache=false\n"
            + "configurationCache=true\n"
            + "maxWorkers=not-a-number\n");

    RuntimeGradleOptions loaded = new RuntimeGradleOptionsStore(optionsFile).load();

    assertEquals(true, loaded.reuseDaemon());
    assertEquals(false, loaded.buildCache());
    assertEquals(true, loaded.configurationCache());
    assertEquals(RuntimeGradleOptions.DEFAULT_MAX_WORKERS, loaded.maxWorkers());
  }
}
