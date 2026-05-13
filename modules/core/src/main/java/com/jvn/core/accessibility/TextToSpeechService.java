package com.jvn.core.accessibility;

import java.util.Locale;

/**
 * SPI for platform text-to-speech synthesis.
 *
 * <p>Implementations are discovered via {@link java.util.ServiceLoader}.
 * If no implementation is available, {@link NoopTextToSpeechService} is used.</p>
 */
public interface TextToSpeechService {

  /**
   * Speak the given text using the platform TTS engine.
   *
   * @param text   the text to synthesize; null or blank is silently ignored
   * @param locale the target locale for voice selection
   */
  void speak(String text, Locale locale);

  /**
   * Stop any currently active speech immediately.
   */
  void stop();

  /**
   * @return true if a TTS engine is available on this platform
   */
  boolean isAvailable();
}
