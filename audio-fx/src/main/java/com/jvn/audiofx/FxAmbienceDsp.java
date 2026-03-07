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
    double windMacro;
    double windMicro;
    double windRumbleLp;
    double windBodyLp;
    double windBodyHpRef;
    double windHissLp;
    double windHissHpRef;
    double windResNoise;
    double windWhistlePhase;
    double windWhistleEnv;

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
      windMacro = 0.0;
      windMicro = 0.0;
      windRumbleLp = 0.0;
      windBodyLp = 0.0;
      windBodyHpRef = 0.0;
      windHissLp = 0.0;
      windHissHpRef = 0.0;
      windResNoise = 0.0;
      windWhistlePhase = 0.0;
      windWhistleEnv = 0.0;
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
    double nA = noise(st);
    double nB = noise(st);
    double nC = noise(st);
    double nD = noise(st);

    // Two turbulence layers:
    // - macro controls broad gust envelope shifts
    // - micro adds flutter and short-term modulation
    st.windMacro = onePoleLp(st.windMacro, nA, 0.12 + 0.28 * i, dt);
    st.windMicro = onePoleLp(st.windMicro, nB, 1.8 + 2.8 * i, dt);

    st.gustPhase += dt * (0.028 + 0.062 * i);
    double periodic = 0.5 + 0.5 * Math.sin(st.gustPhase * Math.PI * 2.0
        + 0.45 * Math.sin(st.gustPhase * Math.PI * 2.0 * 0.43 + 1.1));

    // "wind speed" proxy used for spectral shaping. Clamped to [0..1].
    double speed = 0.22 + 0.62 * i
        + 0.34 * st.windMacro
        + 0.18 * st.windMicro
        + 0.20 * (periodic - 0.5);
    speed = clamp01(speed);
    double pressure = speed * speed;

    // Low pressure/structure rumble (colored very low band).
    double rumbleCut = 34.0 + 145.0 * pressure;
    st.windRumbleLp = onePoleLp(st.windRumbleLp, nC, rumbleCut, dt);

    // Body of wind (broad moving bandpass).
    double bodyHighCut = 420.0 + 1650.0 * pressure + 420.0 * i;
    double bodyLowCut = 88.0 + 240.0 * pressure;
    st.windBodyLp = onePoleLp(st.windBodyLp, nC, bodyHighCut, dt);
    st.windBodyHpRef = onePoleLp(st.windBodyHpRef, st.windBodyLp, bodyLowCut, dt);
    double body = st.windBodyLp - st.windBodyHpRef;

    // Air hiss layer (higher band that grows with speed).
    double hissHighCut = 5200.0 + 6400.0 * pressure;
    double hissLowCut = 1200.0 + 1800.0 * pressure;
    st.windHissLp = onePoleLp(st.windHissLp, nD, hissHighCut, dt);
    st.windHissHpRef = onePoleLp(st.windHissHpRef, st.windHissLp, hissLowCut, dt);
    double hiss = st.windHissLp - st.windHissHpRef;

    // Resonance/whistle component for wind over edges and openings.
    st.windResNoise = onePoleLp(st.windResNoise, noise(st), 3.0 + 5.0 * i, dt);
    double whistleBase = 520.0 + 1650.0 * pressure + 420.0 * i;
    double whistleFreq = clamp(whistleBase * (1.0 + 0.07 * st.windResNoise), 180.0, 4200.0);
    st.windWhistlePhase += dt * whistleFreq;
    st.windWhistlePhase -= Math.floor(st.windWhistlePhase);
    double whistleCarrier = Math.sin(Math.PI * 2.0 * st.windWhistlePhase)
        + 0.34 * Math.sin(Math.PI * 2.0 * st.windWhistlePhase * 2.03 + 0.7);
    double whistleTarget = smoothstep(0.42, 0.92, speed) * (0.20 + 0.80 * pressure);
    st.windWhistleEnv = onePoleLp(st.windWhistleEnv, whistleTarget, 8.0 + 12.0 * i, dt);
    double whistle = whistleCarrier * st.windWhistleEnv;

    double shaped = (0.30 - 0.10 * i) * st.windRumbleLp
        + 0.58 * body
        + (0.16 + 0.44 * pressure) * hiss
        + 0.32 * whistle;

    // Mild saturation behaves closer to real-world pressure compression than hard clipping.
    double level = 0.38 + 0.95 * pressure;
    return Math.tanh(shaped * level * 1.55);
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

  private static double clamp(double v, double lo, double hi) {
    if (v < lo) return lo;
    if (v > hi) return hi;
    return v;
  }

  private static double smoothstep(double edge0, double edge1, double x) {
    if (edge1 <= edge0) return x >= edge1 ? 1.0 : 0.0;
    double t = clamp((x - edge0) / (edge1 - edge0), 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
  }
}
