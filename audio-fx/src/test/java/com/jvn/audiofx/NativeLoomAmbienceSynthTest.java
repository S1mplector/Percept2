package com.jvn.audiofx;

import com.jvn.core.audio.AmbienceProfile;
import java.util.concurrent.TimeUnit;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class NativeLoomAmbienceSynthTest {

  @Test
  @Timeout(value = 15, unit = TimeUnit.SECONDS)
  void rapidPlayStopCyclesDoNotHangOrCrash() throws Exception {
    Assumptions.assumeTrue(AudioFxNativeBridge.isAvailable(), AudioFxNativeBridge.diagnostics());
    Assumptions.assumeTrue(defaultOutputLineAvailable(), "No SourceDataLine available for ambience test");

    NativeLoomAmbienceSynth synth = new NativeLoomAmbienceSynth();
    AmbienceProfile profile = new AmbienceProfile(0.62f, 0.58f, 0.48f, 0.66f, true);
    try {
      for (int i = 0; i < 4; i++) {
        synth.play("ocean", 0.82f, 0.22f, profile);
        Thread.sleep(120L);
        synth.stop();
      }
      synth.stop();
    } finally {
      synth.stop();
    }
  }

  @Test
  @Timeout(value = 15, unit = TimeUnit.SECONDS)
  void replayAfterStopStartsANewSessionCleanly() throws Exception {
    Assumptions.assumeTrue(AudioFxNativeBridge.isAvailable(), AudioFxNativeBridge.diagnostics());
    Assumptions.assumeTrue(defaultOutputLineAvailable(), "No SourceDataLine available for ambience test");

    NativeLoomAmbienceSynth synth = new NativeLoomAmbienceSynth();
    try {
      synth.play("wind", 0.70f, 0.18f, new AmbienceProfile(0.44f, 0.71f, 0.35f, 0.55f, true));
      Thread.sleep(100L);
      synth.stop();

      synth.play("thunder", 0.86f, 0.18f, new AmbienceProfile(0.68f, 0.63f, 0.42f, 0.91f, true));
      Thread.sleep(100L);
      synth.stop();
    } finally {
      synth.stop();
    }
  }

  private static boolean defaultOutputLineAvailable() {
    AudioFormat format = new AudioFormat(44_100, 16, 2, true, false);
    DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
    return AudioSystem.isLineSupported(info);
  }
}
