package com.jvn.audiofx;

import com.jvn.audiofx.spi.ChipSynthProvider;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class BeezChipSynth implements ChipSynthProvider {
  private static final int SAMPLE_RATE = 44_100;
  private static final int CHANNELS = 1;
  private static final int BYTES_PER_SAMPLE = 2;

  private static final Map<String, Sequence> PRESETS = Map.of(
      "blip", new Sequence(new double[]{880.0}, new int[]{120}),
      "confirm", new Sequence(new double[]{660.0, 880.0, 1320.0}, new int[]{100, 100, 140}),
      "error", new Sequence(new double[]{320.0, 240.0}, new int[]{180, 240}),
      "pickup", new Sequence(new double[]{440.0, 660.0, 880.0, 1320.0}, new int[]{90, 90, 90, 140})
  );

  private final Object lock = new Object();
  private volatile boolean running;
  private volatile float volume = 0.7f;
  private volatile float intensity = 0.85f;
  private volatile boolean loop;
  private volatile Sequence sequence = PRESETS.get("blip");
  private Thread worker;
  private SourceDataLine line;

  @Override
  public String id() { return "beez"; }

  @Override
  public void play(String cueId, float newIntensity, float newVolume, boolean newLoop) {
    synchronized (lock) {
      sequence = resolveSequence(cueId);
      intensity = clamp01(newIntensity);
      volume = clamp01(newVolume);
      loop = newLoop;
      if (running) return;
      running = true;
      worker = new Thread(this::run, "audiofx-beez-chip");
      worker.setDaemon(true);
      worker.start();
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
  public void setVolume(float v) {
    volume = clamp01(v);
  }

  private void run() {
    try {
      AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, CHANNELS, true, false);
      DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
      line = (SourceDataLine) AudioSystem.getLine(info);
      line.open(format, SAMPLE_RATE / 2);
      line.start();

      do {
        for (int i = 0; i < sequence.frequencies.length && running; i++) {
          renderTone(sequence.frequencies[i], sequence.durationMs[i], intensity, volume);
          renderTone(0.0, 18, intensity, volume);
        }
      } while (running && loop);
    } catch (Exception ignored) {
    } finally {
      closeLine();
      running = false;
    }
  }

  private void renderTone(double frequencyHz, int durationMs, float shapeIntensity, float gain) {
    int samples = Math.max(1, (int) ((durationMs / 1000.0) * SAMPLE_RATE));
    byte[] pcm = new byte[samples * BYTES_PER_SAMPLE];
    double phase = 0.0;
    double phaseStep = frequencyHz <= 0 ? 0 : frequencyHz / SAMPLE_RATE;
    double duty = 0.40 + 0.25 * shapeIntensity;

    for (int i = 0; i < samples; i++) {
      double envAttack = Math.min(1.0, i / (SAMPLE_RATE * 0.004));
      double envRelease = Math.min(1.0, (samples - i) / (SAMPLE_RATE * 0.018));
      double env = Math.min(envAttack, envRelease);
      double sample;
      if (frequencyHz <= 0.0) {
        sample = 0.0;
      } else {
        sample = ((phase % 1.0) < duty ? 1.0 : -1.0) * env;
        phase += phaseStep;
      }
      sample *= gain * 0.55;
      short s = (short) Math.round(clamp11(sample) * Short.MAX_VALUE);
      int bi = i * BYTES_PER_SAMPLE;
      pcm[bi] = (byte) (s & 0xFF);
      pcm[bi + 1] = (byte) ((s >> 8) & 0xFF);
    }

    if (line != null) {
      line.write(pcm, 0, pcm.length);
    }
  }

  private Sequence resolveSequence(String cueId) {
    String key = cueId == null ? "" : cueId.trim().toLowerCase(Locale.ROOT);
    if (key.isEmpty()) return PRESETS.get("blip");
    Sequence preset = PRESETS.get(key);
    if (preset != null) return preset;

    String[] parts = key.split(",");
    if (parts.length >= 2) {
      double[] freqs = new double[parts.length];
      int[] durs = new int[parts.length];
      for (int i = 0; i < parts.length; i++) {
        try {
          freqs[i] = Double.parseDouble(parts[i].trim());
        } catch (Exception ignored) {
          freqs[i] = 440.0;
        }
        durs[i] = 110;
      }
      return new Sequence(freqs, durs);
    }
    return PRESETS.get("blip");
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

  private record Sequence(double[] frequencies, int[] durationMs) {}
}
