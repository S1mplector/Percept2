package com.jvn.core.accessibility;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class TextToSpeechServiceTest {

  @Test
  void noopIsUnavailable() {
    TextToSpeechService svc = new NoopTextToSpeechService();
    assertFalse(svc.isAvailable());
  }

  @Test
  void noopSpeakDoesNotThrow() {
    TextToSpeechService svc = new NoopTextToSpeechService();
    assertDoesNotThrow(() -> svc.speak("Hello world", Locale.ENGLISH));
    assertDoesNotThrow(() -> svc.speak(null, Locale.ENGLISH));
    assertDoesNotThrow(() -> svc.speak("", null));
  }

  @Test
  void noopStopDoesNotThrow() {
    TextToSpeechService svc = new NoopTextToSpeechService();
    assertDoesNotThrow(svc::stop);
  }
}
