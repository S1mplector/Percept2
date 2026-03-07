package com.jvn.audiofx;

/**
 * Deterministic ambience DSP kernel used by procedural ambience providers.
 * This is intentionally pure/math-centric so it can be tested without audio I/O.
 */
public final class FxAmbienceDsp {
  private FxAmbienceDsp() {
  }

  public enum Preset {
    WIND,
    RAIN,
    OCEAN;

    public static Preset fromToken(String raw) {
      String t = raw == null ? "" : raw.trim().toLowerCase();
      if (t.isEmpty()) return WIND;
      return switch (t) {
        case "rain", "drizzle", "storm", "downpour" -> RAIN;
        case "ocean", "sea", "waves", "shore" -> OCEAN;
        default -> WIND;
      };
    }
  }

  public static final class State {
    private static final long DEFAULT_SEED = 0x9E3779B97F4A7C15L;

    long seed;
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

    public State(long seed) {
      this.seed = normalizeSeed(seed);
      reset();
    }

    public void reseed(long seed) {
      this.seed = normalizeSeed(seed);
      reset();
    }

    public void reset() {
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

    public double elapsedSeconds() {
      return time;
    }

    private static long normalizeSeed(long seed) {
      if (seed == 0L) return DEFAULT_SEED;
      return seed;
    }
  }

  public static double synthSample(State st, double dt, Preset preset, float intensity) {
    if (st == null) return 0.0;
    double i = clamp01(intensity);
    double mono = switch (preset == null ? Preset.WIND : preset) {
      case WIND -> wind(st, dt, i);
      case RAIN -> rain(st, dt, i);
      case OCEAN -> ocean(st, dt, i);
    };
    st.time += Math.max(0.0, dt);
    return clamp11(mono);
  }

  private static double wind(State st, double dt, double i) {
    double n1 = noise(st);
    double n2 = noise(st);
    st.gustPhase += dt * (0.035 + 0.065 * i);
    double gust = 0.5 + 0.5 * Math.sin(st.gustPhase * Math.PI * 2.0)
        + 0.25 * Math.sin(st.gustPhase * Math.PI * 3.4 + 1.3);
    gust = clamp01(gust);

    double bodyCut = 120.0 + 600.0 * gust + 420.0 * i;
    st.windBodyLp = onePoleLp(st.windBodyLp, n1, bodyCut, dt);
    double whistleCut = 900.0 + 1600.0 * gust + 800.0 * i;
    st.windWhistleLp = onePoleLp(st.windWhistleLp, n2, whistleCut, dt);
    st.windWhistleHpLp = onePoleLp(st.windWhistleHpLp, st.windWhistleLp, 650.0 + 220.0 * i, dt);
    double whistleHp = st.windWhistleLp - st.windWhistleHpLp;

    return 0.72 * st.windBodyLp + 0.28 * whistleHp * (0.4 + 0.6 * gust);
  }

  private static double rain(State st, double dt, double i) {
    double n = noise(st);
    st.rainBedLp = onePoleLp(st.rainBedLp, n, 4800.0 + 3200.0 * i, dt);
    st.rainBedHpLp = onePoleLp(st.rainBedHpLp, st.rainBedLp, 1300.0 + 400.0 * i, dt);
    double rainHp = st.rainBedLp - st.rainBedHpLp;

    double chance = 0.001 + 0.020 * i;
    if (rand01(st) < chance) {
      st.dropAmp += 0.08 + rand01(st) * 0.24;
      st.dropTone = 1700.0 + rand01(st) * 3800.0;
    }
    st.dropPhase += dt * st.dropTone;
    st.dropAmp *= 0.988;
    double drop = Math.sin(st.dropPhase * Math.PI * 2.0) * st.dropAmp;

    return 0.68 * rainHp + 0.55 * drop;
  }

  private static double ocean(State st, double dt, double i) {
    double n1 = noise(st);
    double n2 = noise(st);
    st.swellPhase += dt * (0.045 + 0.030 * i);
    double swell = 0.5 + 0.5 * Math.sin(st.swellPhase * Math.PI * 2.0);

    st.oceanLow = onePoleLp(st.oceanLow, n1, 120.0 + 200.0 * i, dt);
    st.oceanMidLp = onePoleLp(st.oceanMidLp, n2, 1400.0 + 2200.0 * swell, dt);
    st.oceanMidHpLp = onePoleLp(st.oceanMidHpLp, st.oceanMidLp, 420.0 + 260.0 * i, dt);
    double oceanHp = st.oceanMidLp - st.oceanMidHpLp;

    double foam = oceanHp * (0.22 + 0.78 * swell);
    return 0.74 * st.oceanLow + 0.52 * foam;
  }

  private static double onePoleLp(double state, double x, double cutoffHz, double dt) {
    double c = Math.max(10.0, cutoffHz);
    double rc = 1.0 / (2.0 * Math.PI * c);
    double alpha = dt / (rc + dt);
    return state + alpha * (x - state);
  }

  private static double noise(State st) {
    long seed = st.seed;
    seed ^= (seed << 13);
    seed ^= (seed >>> 7);
    seed ^= (seed << 17);
    if (seed == 0L) seed = 0x9E3779B97F4A7C15L;
    st.seed = seed;
    long bits = seed & ((1L << 53) - 1);
    return (bits / (double) ((1L << 53) - 1)) * 2.0 - 1.0;
  }

  private static double rand01(State st) {
    return (noise(st) + 1.0) * 0.5;
  }

  private static double clamp11(double v) {
    if (v < -1.0) return -1.0;
    if (v > 1.0) return 1.0;
    return v;
  }

  private static double clamp01(double v) {
    if (v < 0.0) return 0.0;
    if (v > 1.0) return 1.0;
    return v;
  }
}

