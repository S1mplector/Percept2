package com.jvn.audiofx;

import com.jvn.audiofx.spi.AmbienceSynthProvider;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

/**
 * Procedural ambience renderer for wind/rain/ocean style sound beds.
 * Shared base for provider aliases (fx/loom/beez ambience).
 */
public class FxAmbienceSynth implements AmbienceSynthProvider {
  private static final int SAMPLE_RATE = 44_100;
  private static final int CHANNELS = 2;
  private static final int BYTES_PER_SAMPLE = 2;
  private static final int FRAME_BYTES = CHANNELS * BYTES_PER_SAMPLE;
  private static final int BUFFER_FRAMES = 1024;
  private static final long AUTO_STOP_SECONDS = 15L;

  private final Object lock = new Object();
  private final String providerId;
  private final String threadName;

  private volatile boolean running;
  private volatile boolean loop = true;
  private volatile FxAmbienceDsp.Preset preset = FxAmbienceDsp.Preset.WIND;
  private volatile float intensity = 0.65f;
  private volatile float volume = 0.45f;

  private final FxAmbienceDsp.State dspState =
      new FxAmbienceDsp.State(System.nanoTime() ^ 0x9E3779B97F4A7C15L);

  private SourceDataLine line;
  private Thread renderThread;

  public FxAmbienceSynth() {
    this("fx", "audiofx-fx-ambience");
  }

  protected FxAmbienceSynth(String providerId, String threadName) {
    this.providerId = providerId;
    this.threadName = threadName;
  }

  @Override
  public String id() {
    return providerId;
  }

  @Override
  public void play(String presetName, float newIntensity, float newVolume, boolean newLoop) {
    synchronized (lock) {
      preset = FxAmbienceDsp.Preset.fromToken(presetName);
      intensity = clamp01(newIntensity);
      volume = clamp01(newVolume);
      loop = newLoop;
      if (running) return;
      running = true;
      dspState.reseed(System.nanoTime() ^ 0x9E3779B97F4A7C15L);
      renderThread = new Thread(this::runRenderLoop, threadName);
      renderThread.setDaemon(true);
      renderThread.start();
    }
  }

  @Override
  public void stop() {
    synchronized (lock) {
      running = false;
      closeLine();
    }
  }

  @Override
  public void setVolume(float newVolume) {
    volume = clamp01(newVolume);
  }

  private void runRenderLoop() {
    byte[] pcm = new byte[BUFFER_FRAMES * FRAME_BYTES];
    try {
      AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, CHANNELS, true, false);
      DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
      line = (SourceDataLine) AudioSystem.getLine(info);
      line.open(format, BUFFER_FRAMES * FRAME_BYTES * 4);
      line.start();

      double dt = 1.0 / SAMPLE_RATE;
      while (running) {
        for (int i = 0; i < BUFFER_FRAMES; i++) {
          double mono = FxAmbienceDsp.synthSample(dspState, dt, preset, intensity);
          double gain = volume * (0.20 + 0.80 * intensity);
          mono *= gain;
          double pan = 0.18 * Math.sin(dspState.elapsedSeconds() * 0.22 * Math.PI * 2.0);
          double left = clamp11(mono * (1.0 - pan));
          double right = clamp11(mono * (1.0 + pan));
          short ls = (short) Math.round(left * Short.MAX_VALUE);
          short rs = (short) Math.round(right * Short.MAX_VALUE);
          int bi = i * FRAME_BYTES;
          pcm[bi] = (byte) (ls & 0xFF);
          pcm[bi + 1] = (byte) ((ls >> 8) & 0xFF);
          pcm[bi + 2] = (byte) (rs & 0xFF);
          pcm[bi + 3] = (byte) ((rs >> 8) & 0xFF);
        }

        if (line != null) {
          line.write(pcm, 0, pcm.length);
        }
        if (!loop && dspState.elapsedSeconds() > AUTO_STOP_SECONDS) {
          running = false;
        }
      }
    } catch (Exception ignored) {
    } finally {
      closeLine();
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

  private static float clamp01(float v) {
    if (v < 0f) return 0f;
    if (v > 1f) return 1f;
    return v;
  }

  private static double clamp11(double v) {
    if (v < -1.0) return -1.0;
    if (v > 1.0) return 1.0;
    return v;
  }
}

