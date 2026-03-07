package com.jvn.audiofx;

import com.jvn.audiofx.spi.AmbienceSynthProvider;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

public final class LoomAmbienceSynth implements AmbienceSynthProvider {
  private static final int SAMPLE_RATE = 44_100;
  private static final int CHANNELS = 2;
  private static final int BYTES_PER_SAMPLE = 2;
  private static final int FRAME_BYTES = CHANNELS * BYTES_PER_SAMPLE;
  private static final int BUFFER_FRAMES = 1024;

  private final Object lock = new Object();
  private volatile boolean running;
  private volatile boolean loop = true;
  private volatile AmbiencePreset preset = AmbiencePreset.WIND;
  private volatile float intensity = 0.65f;
  private volatile float volume = 0.45f;
  private SourceDataLine line;
  private Thread renderThread;
  private final DspState st = new DspState();

  @Override
  public String id() { return "loom"; }

  @Override
  public void play(String presetName, float newIntensity, float newVolume, boolean newLoop) {
    synchronized (lock) {
      preset = AmbiencePreset.fromToken(presetName);
      intensity = clamp01(newIntensity);
      volume = clamp01(newVolume);
      loop = newLoop;
      if (running) return;
      running = true;
      renderThread = new Thread(this::runRenderLoop, "audiofx-loom-ambience");
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
      st.reset();
      while (running) {
        for (int i = 0; i < BUFFER_FRAMES; i++) {
          double mono = synthSample(dt, preset, intensity);
          double gain = volume * (0.25 + 0.75 * intensity);
          mono *= gain;
          double pan = 0.18 * Math.sin(st.time * 0.22 * Math.PI * 2.0);
          double left = clamp11(mono * (1.0 - pan));
          double right = clamp11(mono * (1.0 + pan));
          short ls = (short) Math.round(left * Short.MAX_VALUE);
          short rs = (short) Math.round(right * Short.MAX_VALUE);
          int bi = i * FRAME_BYTES;
          pcm[bi] = (byte) (ls & 0xFF);
          pcm[bi + 1] = (byte) ((ls >> 8) & 0xFF);
          pcm[bi + 2] = (byte) (rs & 0xFF);
          pcm[bi + 3] = (byte) ((rs >> 8) & 0xFF);
          st.time += dt;
        }
        if (line != null) {
          line.write(pcm, 0, pcm.length);
        }
        if (!loop && st.time > 15.0) {
          running = false;
        }
      }
    } catch (Exception ignored) {
    } finally {
      closeLine();
    }
  }

  private double synthSample(double dt, AmbiencePreset p, float i) {
    return switch (p) {
      case WIND -> wind(dt, i);
      case RAIN -> rain(dt, i);
      case OCEAN -> ocean(dt, i);
    };
  }

  private double wind(double dt, float i) {
    double n1 = noise();
    double n2 = noise();
    st.gustPhase += dt * (0.035 + 0.065 * i);
    double gust = 0.5 + 0.5 * Math.sin(st.gustPhase * Math.PI * 2.0)
        + 0.25 * Math.sin(st.gustPhase * Math.PI * 3.4 + 1.3);
    gust = clamp01((float) gust);

    double bodyCut = 120.0 + 600.0 * gust + 420.0 * i;
    st.windBodyLp = onePoleLp(st.windBodyLp, n1, bodyCut, dt);
    double whistleCut = 900.0 + 1600.0 * gust + 800.0 * i;
    st.windWhistleLp = onePoleLp(st.windWhistleLp, n2, whistleCut, dt);
    st.windWhistleHpLp = onePoleLp(st.windWhistleHpLp, st.windWhistleLp, 650.0 + 220.0 * i, dt);
    double whistleHp = st.windWhistleLp - st.windWhistleHpLp;

    return 0.72 * st.windBodyLp + 0.28 * whistleHp * (0.4 + 0.6 * gust);
  }

  private double rain(double dt, float i) {
    double n = noise();
    st.rainBedLp = onePoleLp(st.rainBedLp, n, 4800.0 + 3200.0 * i, dt);
    st.rainBedHpLp = onePoleLp(st.rainBedHpLp, st.rainBedLp, 1300.0 + 400.0 * i, dt);
    double rainHp = st.rainBedLp - st.rainBedHpLp;

    double chance = 0.001 + 0.020 * i;
    if (rand01() < chance) {
      st.dropAmp += 0.08 + rand01() * 0.24;
      st.dropTone = 1700.0 + rand01() * 3800.0;
    }
    st.dropPhase += dt * st.dropTone;
    st.dropAmp *= 0.988;
    double drop = Math.sin(st.dropPhase * Math.PI * 2.0) * st.dropAmp;

    return 0.68 * rainHp + 0.55 * drop;
  }

  private double ocean(double dt, float i) {
    double n1 = noise();
    double n2 = noise();
    st.swellPhase += dt * (0.045 + 0.030 * i);
    double swell = 0.5 + 0.5 * Math.sin(st.swellPhase * Math.PI * 2.0);

    st.oceanLow = onePoleLp(st.oceanLow, n1, 120.0 + 200.0 * i, dt);
    st.oceanMidLp = onePoleLp(st.oceanMidLp, n2, 1400.0 + 2200.0 * swell, dt);
    st.oceanMidHpLp = onePoleLp(st.oceanMidHpLp, st.oceanMidLp, 420.0 + 260.0 * i, dt);
    double oceanHp = st.oceanMidLp - st.oceanMidHpLp;

    double foam = oceanHp * (0.22 + 0.78 * swell);
    return 0.74 * st.oceanLow + 0.52 * foam;
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

  private static double onePoleLp(double state, double x, double cutoffHz, double dt) {
    double c = Math.max(10.0, cutoffHz);
    double rc = 1.0 / (2.0 * Math.PI * c);
    double alpha = dt / (rc + dt);
    return state + alpha * (x - state);
  }

  private double noise() {
    st.seed ^= (st.seed << 13);
    st.seed ^= (st.seed >>> 7);
    st.seed ^= (st.seed << 17);
    long bits = st.seed & ((1L << 53) - 1);
    return (bits / (double) ((1L << 53) - 1)) * 2.0 - 1.0;
  }

  private double rand01() {
    return (noise() + 1.0) * 0.5;
  }

  private static double clamp11(double v) {
    if (v < -1.0) return -1.0;
    if (v > 1.0) return 1.0;
    return v;
  }

  private static float clamp01(float v) {
    if (v < 0f) return 0f;
    if (v > 1f) return 1f;
    return v;
  }

  private enum AmbiencePreset {
    WIND,
    RAIN,
    OCEAN;

    static AmbiencePreset fromToken(String raw) {
      String t = raw == null ? "" : raw.trim().toLowerCase();
      if (t.isEmpty()) return WIND;
      return switch (t) {
        case "rain", "drizzle", "storm", "downpour" -> RAIN;
        case "ocean", "sea", "waves", "shore" -> OCEAN;
        default -> WIND;
      };
    }
  }

  private static final class DspState {
    long seed = System.nanoTime() ^ 0x9E3779B97F4A7C15L;
    double time;

    double gustPhase;
    double windBodyLp;
    double windWhistleLp;
    double windWhistleHpLp;

    double rainBedLp;
    double rainBedHpLp;
    double dropAmp;
    double dropTone = 2800.0;
    double dropPhase;

    double swellPhase;
    double oceanLow;
    double oceanMidLp;
    double oceanMidHpLp;

    void reset() {
      time = 0.0;
      gustPhase = 0.0;
      windBodyLp = 0.0;
      windWhistleLp = 0.0;
      windWhistleHpLp = 0.0;
      rainBedLp = 0.0;
      rainBedHpLp = 0.0;
      dropAmp = 0.0;
      dropTone = 2800.0;
      dropPhase = 0.0;
      swellPhase = 0.0;
      oceanLow = 0.0;
      oceanMidLp = 0.0;
      oceanMidHpLp = 0.0;
    }
  }
}
