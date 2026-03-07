package com.jvn.audiofx;

import com.jvn.audiofx.spi.ChipSynthProvider;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

public final class NativeBeezChipSynth implements ChipSynthProvider {
  private static final int SAMPLE_RATE = 44_100;
  private static final int CHANNELS = 2;
  private static final int BYTES_PER_SAMPLE = 2;
  private static final int FRAME_BYTES = CHANNELS * BYTES_PER_SAMPLE;
  private static final int BUFFER_FRAMES = 1024;

  private final Object lock = new Object();

  private volatile boolean running;
  private volatile String cueId = "blip";
  private volatile float intensity = 0.85f;
  private volatile float volume = 0.7f;
  private volatile boolean loop;

  private AudioFxNativeBridge.BeezRenderer renderer;
  private SourceDataLine line;
  private Thread worker;

  @Override
  public String id() {
    return "native-beez";
  }

  @Override
  public void play(String newCueId, float newIntensity, float newVolume, boolean newLoop) {
    synchronized (lock) {
      cueId = (newCueId == null || newCueId.isBlank()) ? "blip" : newCueId;
      intensity = clamp01(newIntensity);
      volume = clamp01(newVolume);
      loop = newLoop;
      if (renderer == null) {
        renderer = AudioFxNativeBridge.createBeezRenderer(SAMPLE_RATE);
      }
      renderer.configure(cueId, intensity, volume, loop);
      if (running) return;
      running = true;
      worker = new Thread(this::runLoop, "audiofx-native-beez");
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
        AudioFxNativeBridge.BeezRenderer active;
        int written;
        boolean finished;
        synchronized (lock) {
          active = renderer;
          if (active == null) break;
          written = active.render(pcm, BUFFER_FRAMES);
          finished = !loop && active.isFinished();
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
