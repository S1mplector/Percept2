package com.jvn.hub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JvmLaunchSettingsTest {

  @Test
  void producesExactUsefulJdk21Arguments(@TempDir Path temporaryDirectory) {
    Path dumpDirectory = temporaryDirectory.resolve("heap dumps");
    JvmLaunchSettings settings = new JvmLaunchSettings(
        512, 4096, JvmLaunchSettings.Collector.ZGC, true, true, true,
        "-XX:MaxGCPauseMillis=75 '-Dexample=a value'");

    assertEquals(List.of(
        "-Xms512m",
        "-Xmx4096m",
        "-XX:+UseZGC",
        "-XX:+HeapDumpOnOutOfMemoryError",
        "-XX:HeapDumpPath=" + dumpDirectory.toAbsolutePath(),
        "-XX:+ExitOnOutOfMemoryError",
        "-XX:+UseStringDeduplication",
        "-XX:MaxGCPauseMillis=75",
        "-Dexample=a value"), settings.jvmArguments(dumpDirectory));
  }

  @Test
  void persistsSettingsAndOneArgumentPerLine(@TempDir Path temporaryDirectory) throws Exception {
    Path settingsFile = temporaryDirectory.resolve("jvm.properties");
    Path argumentsFile = temporaryDirectory.resolve("jvm.args");
    JvmLaunchSettings original = new JvmLaunchSettings(
        256, 2048, JvmLaunchSettings.Collector.G1, true, false, true, "-Dfile.encoding=UTF-8");

    original.save(settingsFile);
    JvmLaunchSettings loaded = JvmLaunchSettings.load(settingsFile);
    loaded.writeArguments(argumentsFile, temporaryDirectory.resolve("dumps with spaces"));

    assertEquals(256, loaded.initialHeapMb());
    assertEquals(2048, loaded.maxHeapMb());
    assertEquals(JvmLaunchSettings.Collector.G1, loaded.collector());
    assertEquals(loaded.jvmArguments(temporaryDirectory.resolve("dumps with spaces")),
        Files.readAllLines(argumentsFile));
  }

  @Test
  void rejectsContradictoryOrDuplicatedMemoryControls() {
    JvmLaunchSettings reversed = new JvmLaunchSettings(
        4096, 1024, JvmLaunchSettings.Collector.G1, true, true, false, "");
    JvmLaunchSettings duplicate = new JvmLaunchSettings(
        0, 2048, JvmLaunchSettings.Collector.DEFAULT, true, true, false, "-Xmx8g");
    JvmLaunchSettings unsupportedDedup = new JvmLaunchSettings(
        0, 0, JvmLaunchSettings.Collector.SERIAL, true, true, true, "");
    JvmLaunchSettings alternateHeapFlag = new JvmLaunchSettings(
        0, 2048, JvmLaunchSettings.Collector.DEFAULT, false, false, false,
        "-XX:MaxHeapSize=8g");
    JvmLaunchSettings alternateCollector = new JvmLaunchSettings(
        0, 0, JvmLaunchSettings.Collector.DEFAULT, false, false, false,
        "-XX:+UseEpsilonGC");

    assertTrue(reversed.validationError().isPresent());
    assertTrue(duplicate.validationError().isPresent());
    assertTrue(unsupportedDedup.validationError().isPresent());
    assertTrue(alternateHeapFlag.validationError().isPresent());
    assertTrue(alternateCollector.validationError().isPresent());
  }

  @Test
  void rejectsMalformedAdvancedArguments() {
    JvmLaunchSettings unmatchedQuote = new JvmLaunchSettings(
        0, 0, JvmLaunchSettings.Collector.DEFAULT, false, false, false,
        "-Dexample='unfinished");
    JvmLaunchSettings lineBreak = new JvmLaunchSettings(
        0, 0, JvmLaunchSettings.Collector.DEFAULT, false, false, false,
        "-Dfirst=true\n-Dsecond=true");

    assertTrue(unmatchedQuote.validationError().isPresent());
    assertTrue(lineBreak.validationError().isPresent());
  }

  @Test
  void defaultsPreserveJdkLaunchBehavior() {
    JvmLaunchSettings defaults = JvmLaunchSettings.defaults();

    assertEquals(0, defaults.initialHeapMb());
    assertEquals(0, defaults.maxHeapMb());
    assertEquals(JvmLaunchSettings.Collector.DEFAULT, defaults.collector());
    assertFalse(defaults.heapDumpOnOutOfMemory());
    assertFalse(defaults.exitOnOutOfMemory());
    assertFalse(defaults.stringDeduplication());
  }

  @Test
  void recognizesDirectSettingsLaunchFlag() {
    assertTrue(JvnHub.hasArgument(
        new String[] {"--project-root=/tmp/jvn", " --JVM-MEMORY-SETTINGS "},
        "--jvm-memory-settings"));
    assertFalse(JvnHub.hasArgument(new String[] {"--safe-mode"}, "--jvm-memory-settings"));
  }
}
