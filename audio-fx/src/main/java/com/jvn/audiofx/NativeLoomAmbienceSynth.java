package com.jvn.audiofx;

import com.jvn.audiofx.spi.AmbienceSynthProvider;
import com.jvn.core.audio.AmbienceProfile;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

public final class NativeLoomAmbienceSynth implements AmbienceSynthProvider {
  private static final int SAMPLE_RATE = 44_100;
  private static final int CHANNELS = 2;
  private static final int BYTES_PER_SAMPLE = 2;
  private static final int FRAME_BYTES = CHANNELS * BYTES_PER_SAMPLE;
  private static final int BUFFER_FRAMES = 1024;

  private final Object lock = new Object();

  private volatile boolean running;
  private volatile String preset = "wind";
  private volatile float intensity = 0.65f;
  private volatile float volume = 0.45f;
  private volatile AmbienceProfile profile = AmbienceProfile.defaults(true);

  private AudioFxNativeBridge.AmbienceRenderer renderer;
  private SourceDataLine line;
  private Thread worker;

  @Override
  public String id() {
    return "native-loom";
  }

  @Override
  public void play(String presetName, float newIntensity, float newVolume, boolean newLoop) {
    play(presetName, newIntensity, newVolume, AmbienceProfile.defaults(newLoop));
  }

  @Override
  public void play(String presetName, float newIntensity, float newVolume, AmbienceProfile newProfile) {
    synchronized (lock) {
      preset = (presetName == null || presetName.isBlank()) ? "wind" : presetName;
      intensity = clamp01(newIntensity);
      volume = clamp01(newVolume);
      profile = newProfile == null ? AmbienceProfile.defaults(true) : newProfile;
      if (renderer == null) {
        renderer = AudioFxNativeBridge.createAmbienceRenderer(SAMPLE_RATE);
      }
      renderer.configure(preset, intensity, volume, profile);
      if (running) return;
      running = true;
      worker = new Thread(this::runLoop, "audiofx-native-loom");
      worker.setDaemon(true);
      worker.start();
    }
  }

  @Override
  public void stop() {
    synchronized (lock) {
      running = false;
      if (renderer != null) renderer.stop();
      closeLine();
      closeRenderer();
    }
  }

  @Override
  public void setVolume(float newVolume) {
    synchronized (lock) {
      volume = clamp01(newVolume);
      if (renderer != null) {
        renderer.setVolume(volume);
      }
    }
  }

  private void runLoop() {
    byte[] pcm = new byte[BUFFER_FRAMES * FRAME_BYTES];
    try {
      AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, CHANNELS, true, false);
      DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
      line = (SourceDataLine) AudioSystem.getLine(info);
      line.open(format, pcm.length * 4);
      line.start();

      while (running) {
        AudioFxNativeBridge.AmbienceRenderer active;
        int written;
        boolean finished;
        synchronized (lock) {
          active = renderer;
          if (active == null) break;
          written = active.render(pcm, BUFFER_FRAMES);
          finished = !profile.loop() && active.isFinished();
        }
        if (active == null) break;
        if (written <= 0) break;
        if (line != null) {
          line.write(pcm, 0, written);
        }
        if (finished) {
          break;
        }
      }
    } catch (Exception ignored) {
    } finally {
      running = false;
      closeLine();
      closeRenderer();
    }
  }

  private void closeLine() {
    if (line == null) return;
    try {
      line.stop();
    } catch (Exception ignored) {
    }
    try {
      line.flush();
    } catch (Exception ignored) {
    }
    try {
      line.close();
    } catch (Exception ignored) {
    }
    line = null;
  }

  private void closeRenderer() {
    if (renderer == null) return;
    try {
      renderer.close();
    } catch (Exception ignored) {
    }
    renderer = null;
  }

  private static float clamp01(float value) {
    if (value < 0f) return 0f;
    if (value > 1f) return 1f;
    return value;
  }
}
