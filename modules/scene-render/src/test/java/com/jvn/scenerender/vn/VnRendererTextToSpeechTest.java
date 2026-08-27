package com.jvn.scenerender.vn;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.jvn.core.accessibility.TextToSpeechService;
import com.jvn.scenerender.testkit.RecordingBlitter2D;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class VnRendererTextToSpeechTest {

  @Test
  void setTextToSpeechServiceAcceptsARealImplementationWithoutThrowing() {
    VnRenderer renderer = new VnRenderer(new RecordingBlitter2D());
    RecordingTts tts = new RecordingTts();

    assertDoesNotThrow(() -> renderer.setTextToSpeechService(tts));
  }

  @Test
  void setTextToSpeechServiceNullCoercesToNoopRatherThanThrowing() {
    VnRenderer renderer = new VnRenderer(new RecordingBlitter2D());

    assertDoesNotThrow(() -> renderer.setTextToSpeechService(null));
  }

  private static final class RecordingTts implements TextToSpeechService {
    @Override
    public void speak(String text, Locale locale) {}

    @Override
    public void stop() {}

    @Override
    public boolean isAvailable() {
      return true;
    }
  }
}
