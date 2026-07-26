package com.jvn.web;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.jvn.core.config.ApplicationConfig;

class WebApplicationConfigParserTest {

  @Test
  void blankConfigurationUsesWebDefaults() {
    ApplicationConfig config = WebLauncher.parseConfig("  ");

    assertAll(
        () -> assertEquals("JVN Game", config.title()),
        () -> assertEquals(1280, config.width()),
        () -> assertEquals(720, config.height()),
        () -> assertEquals(0L, config.fixedUpdateMs()),
        () -> assertEquals(5, config.fixedUpdateMaxSteps()),
        () -> assertEquals(1.0, config.timeScale()));
  }

  @Test
  void parsesEverySupportedField() {
    ApplicationConfig config = WebLauncher.parseConfig("""
        {
          "title": "Browser Story",
          "width": 1920,
          "height": 1080,
          "fixedUpdateMs": 16,
          "fixedUpdateMaxSteps": 8,
          "timeScale": 0.75
        }
        """);

    assertAll(
        () -> assertEquals("Browser Story", config.title()),
        () -> assertEquals(1920, config.width()),
        () -> assertEquals(1080, config.height()),
        () -> assertEquals(16L, config.fixedUpdateMs()),
        () -> assertEquals(8, config.fixedUpdateMaxSteps()),
        () -> assertEquals(0.75, config.timeScale()));
  }

  @Test
  void omittedFieldsKeepDefaults() {
    ApplicationConfig config = WebLauncher.parseConfig("{\"width\": 1024}");

    assertAll(
        () -> assertEquals("JVN Game", config.title()),
        () -> assertEquals(1024, config.width()),
        () -> assertEquals(720, config.height()),
        () -> assertEquals(1.0, config.timeScale()));
  }

  @Test
  void ignoresWellFormedUnknownFieldsForForwardCompatibility() {
    ApplicationConfig config = WebLauncher.parseConfig("""
        {
          "title": "JVN \\u2605",
          "future": {
            "flags": [true, false, null],
            "ratio": 1.25e2
          }
        }
        """);

    assertEquals("JVN ★", config.title());
  }

  @Test
  void acceptsWholeExponentForIntegerField() {
    ApplicationConfig config = WebLauncher.parseConfig("{\"width\": 1.28e3}");

    assertEquals(1280, config.width());
  }

  @Test
  void rejectsMalformedJsonWithPosition() {
    IllegalArgumentException error = assertThrows(
        IllegalArgumentException.class,
        () -> WebLauncher.parseConfig("{\"width\": 1280"));

    assertTrue(String.valueOf(error.getMessage()).contains("position"));
  }

  @Test
  void rejectsNonObjectRootAndTrailingContent() {
    assertThrows(IllegalArgumentException.class, () -> WebLauncher.parseConfig("[]"));
    assertThrows(
        IllegalArgumentException.class,
        () -> WebLauncher.parseConfig("{\"width\": 1280} false"));
  }

  @Test
  void rejectsNumbersOutsideSupportedRange() {
    assertThrows(
        IllegalArgumentException.class,
        () -> WebLauncher.parseConfig("{\"future\": 1e9999}"));
    assertThrows(
        IllegalArgumentException.class,
        () -> WebLauncher.parseConfig("{\"fixedUpdateMs\": 9.007199254740992e15}"));
  }

  @Test
  void rejectsDuplicateFields() {
    IllegalArgumentException error = assertThrows(
        IllegalArgumentException.class,
        () -> WebLauncher.parseConfig("{\"width\": 800, \"width\": 1024}"));

    assertTrue(String.valueOf(error.getMessage()).contains("duplicate object key 'width'"));
  }

  @Test
  void rejectsInvalidKnownFieldTypes() {
    assertThrows(
        IllegalArgumentException.class,
        () -> WebLauncher.parseConfig("{\"title\": 7}"));
    assertThrows(
        IllegalArgumentException.class,
        () -> WebLauncher.parseConfig("{\"width\": \"1280\"}"));
    assertThrows(
        IllegalArgumentException.class,
        () -> WebLauncher.parseConfig("{\"height\": 720.5}"));
    assertThrows(
        IllegalArgumentException.class,
        () -> WebLauncher.parseConfig("{\"timeScale\": false}"));
  }

  @Test
  void rejectsKnownFieldValuesOutsideRuntimeBounds() {
    assertThrows(
        IllegalArgumentException.class,
        () -> WebLauncher.parseConfig("{\"width\": 0}"));
    assertThrows(
        IllegalArgumentException.class,
        () -> WebLauncher.parseConfig("{\"fixedUpdateMs\": -1}"));
    assertThrows(
        IllegalArgumentException.class,
        () -> WebLauncher.parseConfig("{\"fixedUpdateMaxSteps\": 0}"));
    assertThrows(
        IllegalArgumentException.class,
        () -> WebLauncher.parseConfig("{\"timeScale\": 10.01}"));
  }
}
