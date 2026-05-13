package com.jvn.core.accessibility;

import java.util.Locale;

/**
 * No-op TTS implementation used when no platform service is available.
 */
public final class NoopTextToSpeechService implements TextToSpeechService {

  @Override
  public void speak(String text, Locale locale) {
    // intentionally no-op
  }

  @Override
  public void stop() {
    // intentionally no-op
  }

  @Override
  public boolean isAvailable() {
    return false;
  }
}
