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
    OCEAN,
    THUNDER,
    FIREPLACE,
    NIGHT_INSECTS;

    public static Preset fromToken(String raw) {
      String t = raw == null ? "" : raw.trim().toLowerCase();
      if (t.isEmpty()) return WIND;
      return switch (t) {
        case "rain", "drizzle", "storm", "downpour" -> RAIN;
        case "ocean", "sea", "waves", "shore" -> OCEAN;
        case "thunder", "lightning" -> THUNDER;
        case "fire", "fireplace", "hearth", "campfire" -> FIREPLACE;
        case "night_insects", "insects", "crickets", "cicada", "night" -> NIGHT_INSECTS;
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
    // Ocean enhanced
    double oceanUndertowLp;
    double oceanCrashEnv;
    double oceanSprayLp;

    double thunderRumbleLp;
    double thunderCrackEnv;
    // Thunder enhanced
    double thunderSubBassLp;
    double thunderBoltEnv;
    double thunderBoltTimer;
    double thunderBoltDecayRate = 0.9990;
    double thunderDropEnv;
    double thunderDropLp;
    double thunderRumblePhase;

    double fireCrackleLp;
    double fireCrackleEnv;
    double fireBaseLp;
    // Fireplace enhanced
    double fireSnapEnv;
    double fireSnapLp;
    double firePopEnv;
    double fireEmberPhase;
    double fireEmberLp;

    double chirpPhase;
    double chirpEnv;
    double insectBedLp;
    // Night insects enhanced
    double cricket2Phase;
    double cricket2Env;
    double cricket3Phase;
    double frogEnv;
    double frogLp;
    double cricket2Lp;

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
      oceanUndertowLp = 0.0;
      oceanCrashEnv = 0.0;
      oceanSprayLp = 0.0;
      thunderRumbleLp = 0.0;
      thunderCrackEnv = 0.0;
      thunderSubBassLp = 0.0;
      thunderBoltEnv = 0.0;
      thunderBoltTimer = 0.0;
      thunderBoltDecayRate = 0.9990;
      thunderDropEnv = 0.0;
      thunderDropLp = 0.0;
      thunderRumblePhase = 0.0;
      fireCrackleLp = 0.0;
      fireCrackleEnv = 0.0;
      fireBaseLp = 0.0;
      fireSnapEnv = 0.0;
      fireSnapLp = 0.0;
      firePopEnv = 0.0;
      fireEmberPhase = 0.0;
      fireEmberLp = 0.0;
      chirpPhase = 0.0;
      chirpEnv = 0.0;
      insectBedLp = 0.0;
      cricket2Phase = 0.0;
      cricket2Env = 0.0;
      cricket3Phase = 0.0;
      frogEnv = 0.0;
      frogLp = 0.0;
      cricket2Lp = 0.0;
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
      case THUNDER -> thunder(st, dt, i);
      case FIREPLACE -> fireplace(st, dt, i);
      case NIGHT_INSECTS -> nightInsects(st, dt, i);
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
    double n3 = noise(st);
    double n4 = noise(st);

    // Dual-period wave cycle — primary (8-14s) + secondary (4-7s)
    double primaryRate = 0.08 + i * 0.04;
    double secondaryRate = 0.18 + i * 0.06;
    st.swellPhase += dt;
    double primaryWave = Math.sin(st.swellPhase * primaryRate * Math.PI * 2.0);
    double secondaryWave = Math.sin(st.swellPhase * secondaryRate * Math.PI * 2.0);

    // Asymmetric blend — sharper crests, gentler troughs
    double rawCycle = 0.65 * primaryWave + 0.35 * secondaryWave;
    double shaped = rawCycle > 0.0
        ? Math.pow(rawCycle, 0.7)
        : -Math.pow(-rawCycle, 1.4) * 0.6;
    double waveCycle = shaped * 0.5 + 0.5;

    // 1) Undertow bass (20-80 Hz) — rumble between waves
    st.oceanUndertowLp = onePoleLp(st.oceanUndertowLp, n1, 55.0 + i * 25.0, dt);
    double undertow = st.oceanUndertowLp * (0.12 + i * 0.16) * (1.0 - waveCycle * 0.4);

    // 2) Swell body (100-300 Hz) — follows wave cycle
    st.oceanLow = onePoleLp(st.oceanLow, n1, 120.0 + 200.0 * i, dt);
    double swell = st.oceanLow * (0.14 + i * 0.22) * (0.25 + 0.75 * waveCycle);

    // 3) Wash mid (300-1200 Hz) — breaking wave body
    st.oceanMidLp = onePoleLp(st.oceanMidLp, n2, 1400.0 + 2200.0 * waveCycle, dt);
    st.oceanMidHpLp = onePoleLp(st.oceanMidHpLp, st.oceanMidLp, 420.0 + 260.0 * i, dt);
    double wash = (st.oceanMidLp - st.oceanMidHpLp)
        * (0.15 + i * 0.28) * Math.max(0.0, waveCycle - 0.2) / 0.8;

    // 4) Foam spray (2000+ Hz) — crests only
    double crest = Math.max(0.0, (waveCycle - 0.5) / 0.5);
    st.oceanSprayLp = onePoleLp(st.oceanSprayLp, n3, 4000.0, dt);
    double foam = n3 * crest * crest * (0.07 + i * 0.14);
    double spray = st.oceanSprayLp * crest * crest * crest * 0.05;

    // 5) Crash events — large wave impacts
    double crashChance = (0.0008 + i * 0.003) * Math.max(0.0, crest - 0.3);
    if (rand01(st) < crashChance && st.oceanCrashEnv < 0.06) {
      st.oceanCrashEnv = 0.5 + i * 0.4;
    }
    st.oceanCrashEnv *= 0.9975;
    double crash = n4 * st.oceanCrashEnv * (0.3 + i * 0.15);

    // 6) Distant continuous roar
    double roar = n2 * (0.05 + i * 0.07);

    return Math.tanh((undertow + swell + wash + foam + spray + crash + roar) * 0.78);
  }

  private static double thunder(State st, double dt, double i) {
    double n1 = noise(st);
    double n2 = noise(st);
    double n3 = noise(st);
    double n4 = noise(st);
    double n5 = noise(st);

    // 1) Deep sub-bass foundation (20-50 Hz)
    st.thunderSubBassLp = onePoleLp(st.thunderSubBassLp, n1, 45.0 + i * 20.0, dt);
    double subBass = st.thunderSubBassLp * (0.16 + i * 0.24);

    // 2) Rolling rumble (50-200 Hz)
    st.thunderRumblePhase += (0.025 + i * 0.04) * dt;
    if (st.thunderRumblePhase > 1.0) st.thunderRumblePhase -= 1.0;
    double rumbleMod = 0.75 + 0.25 * Math.sin(st.thunderRumblePhase * Math.PI * 2.0);
    st.thunderRumbleLp = onePoleLp(st.thunderRumbleLp, n1, 80.0 + 60.0 * i, dt);
    double rumble = st.thunderRumbleLp * (0.20 + i * 0.30) * rumbleMod;

    // 3) Lightning bolt events — multi-stage (crack → rolling decay)
    st.thunderBoltTimer += dt;
    double boltInterval = 7.0 - i * 4.0;
    if (st.thunderBoltTimer > Math.max(1.5, boltInterval) && st.thunderBoltEnv < 0.02) {
      st.thunderBoltEnv = 0.85 + i * 0.15;
      double r = rand01(st);
      st.thunderBoltDecayRate = 0.9980 + r * 0.0016;
      st.thunderBoltTimer = 0.0;
    }
    st.thunderBoltEnv *= st.thunderBoltDecayRate;

    // Bolt bright crack — only at high envelope (initial strike)
    double brightPhase = Math.max(0.0, (st.thunderBoltEnv - 0.35) / 0.65);
    double crack = n2 * brightPhase * brightPhase * (0.55 + i * 0.35);

    // Bolt rolling body — follows full envelope
    double bodyPhase = st.thunderBoltEnv * (1.0 - brightPhase * 0.4);
    double boltRumble = n3 * bodyPhase * (0.4 + i * 0.3);

    // 4) Rain bed — dense continuous rain
    double rain = n4 * (0.10 + i * 0.16);

    // Rain droplet events
    double dropChance = 0.005 + i * 0.010;
    if (rand01(st) < dropChance && st.thunderDropEnv < 0.08) {
      st.thunderDropEnv = 0.20 + i * 0.30;
    }
    st.thunderDropEnv *= 0.9945;
    st.thunderDropLp = onePoleLp(st.thunderDropLp, n4, 1800.0 + i * 1200.0, dt);
    double drop = st.thunderDropLp * st.thunderDropEnv * 0.25;

    // 5) Wind presence — storm winds
    double wind = n5 * (0.06 + i * 0.10);

    return Math.tanh((subBass + rumble + crack + boltRumble + rain + drop + wind) * 0.72);
  }

  private static double fireplace(State st, double dt, double i) {
    double n1 = noise(st);
    double n2 = noise(st);
    double n3 = noise(st);
    double n4 = noise(st);

    // Draft breathing — slow modulation simulating air currents
    double breathe = 0.78 + 0.22 * (0.5 + 0.5 * Math.sin(st.time * 0.6 * Math.PI * 2.0));

    // 1) Ember warmth (40-180 Hz) — oscillating warm glow
    st.fireEmberPhase += (0.08) * dt;
    if (st.fireEmberPhase > 1.0) st.fireEmberPhase -= 1.0;
    double emberGlow = 0.7 + 0.3 * Math.sin(st.fireEmberPhase * Math.PI * 2.0);
    st.fireEmberLp = onePoleLp(st.fireEmberLp, n1, 120.0 + i * 60.0, dt);
    double ember = st.fireEmberLp * (0.14 + i * 0.18) * emberGlow;

    // 2) Base warmth (120-300 Hz) — fire body
    st.fireBaseLp = onePoleLp(st.fireBaseLp, n1, 220.0 + 80.0 * i, dt);
    double base = st.fireBaseLp * (0.18 + i * 0.24);

    // 3) Large cracks — infrequent, loud, long decay
    double crackleChance = 0.002 + i * 0.005;
    if (rand01(st) < crackleChance && st.fireCrackleEnv < 0.06) {
      st.fireCrackleEnv = 0.40 + i * 0.40;
    }
    st.fireCrackleEnv *= 0.9972;
    st.fireCrackleLp = onePoleLp(st.fireCrackleLp, n2, 900.0 + 1200.0 * i, dt);
    double crackle = st.fireCrackleLp * st.fireCrackleEnv * 0.50;

    // 4) Medium pops — moderately frequent, mid energy
    double popChance = 0.006 + i * 0.010;
    if (rand01(st) < popChance && st.firePopEnv < 0.08) {
      st.firePopEnv = 0.22 + i * 0.25;
    }
    st.firePopEnv *= 0.9952;
    double pop = n3 * st.firePopEnv * (0.35 + i * 0.15);

    // 5) Small snaps — frequent, quiet, short
    double snapChance = 0.012 + i * 0.018;
    if (rand01(st) < snapChance && st.fireSnapEnv < 0.10) {
      st.fireSnapEnv = 0.15 + i * 0.18;
    }
    st.fireSnapEnv *= 0.992;
    st.fireSnapLp = onePoleLp(st.fireSnapLp, n3, 2500.0 + i * 1500.0, dt);
    double snap = st.fireSnapLp * st.fireSnapEnv * 0.20;

    // 6) Sizzle/hiss — high frequency continuous texture
    double hiss = n4 * (0.03 + i * 0.06);

    return Math.tanh((ember + base + crackle + pop + snap + hiss) * breathe * 0.76);
  }

  private static double nightInsects(State st, double dt, double i) {
    double n1 = noise(st);
    double n2 = noise(st);
    double n3 = noise(st);
    double n4 = noise(st);
    double n5 = noise(st);

    // Density chorus — insects synchronize and desynchronize
    double slowMod = 0.5 + 0.5 * Math.sin(st.time * 0.15 * Math.PI * 2.0);
    double chorusMod = 0.55 + 0.45 * slowMod;

    // 1) Night bed (50-300 Hz) — warm darkness
    st.insectBedLp = onePoleLp(st.insectBedLp, n1, 280.0 + 120.0 * i, dt);
    double bed = st.insectBedLp * (0.10 + i * 0.14) * (0.85 + slowMod * 0.15);

    // 2) Cricket species 1 (3000-5000 Hz) — burst chirp pattern
    st.chirpPhase += (3.0 + i * 4.5) * dt;
    if (st.chirpPhase > 1.0) st.chirpPhase -= 1.0;
    // Burst pattern: chirp-chirp-pause
    double burstPhase = (st.chirpPhase * 3.0) % 1.0;
    double chirpPulse = burstPhase < 0.6
        ? Math.max(0.0, Math.sin(burstPhase / 0.6 * Math.PI))
        : 0.0;
    double chirpGate = chirpPulse * chirpPulse;

    double chirp1Trigger = 0.003 + i * 0.006;
    if (rand01(st) < chirp1Trigger && st.chirpEnv < 0.1) {
      st.chirpEnv = 0.28 + i * 0.35;
    }
    st.chirpEnv *= 0.9960;
    double chirp1 = n2 * chirpGate * st.chirpEnv * (0.35 + i * 0.20) * chorusMod;

    // 3) Cricket species 2 (4500-7000 Hz) — different rhythm, higher pitch
    st.cricket2Phase += (4.5 + i * 3.0) * dt;
    if (st.cricket2Phase > 1.0) st.cricket2Phase -= 1.0;
    double chirp2Pulse = Math.max(0.0, Math.sin(st.cricket2Phase * Math.PI * 2.0));
    double chirp2Gate = chirp2Pulse * chirp2Pulse * chirp2Pulse;

    double chirp2Trigger = 0.002 + i * 0.005;
    if (rand01(st) < chirp2Trigger && st.cricket2Env < 0.1) {
      st.cricket2Env = 0.20 + i * 0.28;
    }
    st.cricket2Env *= 0.9955;
    st.cricket2Lp = onePoleLp(st.cricket2Lp, n3, 5200.0 + i * 1800.0, dt);
    double chirp2 = st.cricket2Lp * chirp2Gate * st.cricket2Env * (0.22 + i * 0.18) * chorusMod;

    // 4) Cicada drone (1500-3000 Hz) — sustained buzzing
    st.cricket3Phase += (6.0) * dt;
    if (st.cricket3Phase > 1.0) st.cricket3Phase -= 1.0;
    double cicadaMod = 0.5 + 0.5 * Math.sin(st.cricket3Phase * Math.PI * 2.0);
    double cicada = n4 * cicadaMod * (0.06 + i * 0.10) * Math.max(0.0, i - 0.25) / 0.75;

    // 5) Frog croaks (200-500 Hz) — occasional low-frequency bursts
    double frogChance = 0.0005 + i * 0.001;
    if (rand01(st) < frogChance && st.frogEnv < 0.05) {
      st.frogEnv = 0.30 + i * 0.25;
    }
    st.frogEnv *= 0.9985;
    st.frogLp = onePoleLp(st.frogLp, n5, 320.0 + i * 180.0, dt);
    double frog = st.frogLp * st.frogEnv * 0.15;

    // 6) Leaf/grass rustle — detail texture
    double rustle = n5 * (0.03 + i * 0.06);

    return Math.tanh((bed + chirp1 + chirp2 + cicada + frog + rustle) * 0.82);
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
