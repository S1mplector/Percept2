package com.jvn.core.vn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VnSettingsStoreAccessibilityTest {

  @TempDir
  Path tempDir;

  @Test
  void persistsAccessibilityPreferences() {
    Path settingsFile = tempDir.resolve("settings.properties");
    VnSettingsStore store = new VnSettingsStore(settingsFile.toString());
    VnSettings settings = new VnSettings();
    settings.setAccessibilityTheme("opendyslexic");
    settings.setTextToSpeechEnabled(true);
    settings.setUiFontScale(1.5);
    settings.setAutoFitResolution(true);

    store.save(settings);
    VnSettings loaded = store.load();

    assertEquals("opendyslexic", loaded.getAccessibilityTheme());
    assertTrue(loaded.isTextToSpeechEnabled());
    assertEquals(1.5, loaded.getUiFontScale(), 0.0001);
    assertTrue(loaded.isAutoFitResolution());
  }
}
