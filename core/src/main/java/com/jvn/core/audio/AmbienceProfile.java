package com.jvn.core.audio;

public record AmbienceProfile(float detail, float motion, float spread, float accent, boolean loop) {
  public static final float DEFAULT_DETAIL = 0.5f;
  public static final float DEFAULT_MOTION = 0.5f;
  public static final float DEFAULT_SPREAD = 0.5f;
  public static final float DEFAULT_ACCENT = 0.5f;

  public AmbienceProfile {
    detail = clamp01(detail);
    motion = clamp01(motion);
    spread = clamp01(spread);
    accent = clamp01(accent);
  }

  public static AmbienceProfile defaults(boolean loop) {
    return new AmbienceProfile(
        DEFAULT_DETAIL,
        DEFAULT_MOTION,
        DEFAULT_SPREAD,
        DEFAULT_ACCENT,
        loop);
  }

  public static AmbienceProfile defaults() {
    return defaults(true);
  }

  private static float clamp01(float value) {
    if (value < 0f) return 0f;
    if (value > 1f) return 1f;
    return value;
  }
}
