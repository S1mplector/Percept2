package com.jvn.core.audio;

/**
 * Immutable configuration record for procedural ambience playback.
 *
 * <p>An {@code AmbienceProfile} describes four normalised [0, 1] parameters
 * that shape how an ambience preset is synthesised at runtime:</p>
 * <ul>
 *   <li><b>detail</b> — granularity / density of the soundscape.</li>
 *   <li><b>motion</b> — how much the soundscape evolves over time.</li>
 *   <li><b>spread</b> — stereo width / spatial distribution.</li>
 *   <li><b>accent</b> — emphasis on highlight transients.</li>
 * </ul>
 *
 * <p>All parameters are automatically clamped to [0, 1] in the compact
 * constructor. Use {@link #defaults()} for a balanced starting point.</p>
 *
 * @param detail granularity of the soundscape [0, 1]
 * @param motion temporal evolution rate [0, 1]
 * @param spread stereo width [0, 1]
 * @param accent transient emphasis [0, 1]
 * @param loop   whether the ambience should loop
 *
 * @see AudioFacade#playAmbience(String, float, AmbienceProfile)
 */
public record AmbienceProfile(float detail, float motion, float spread, float accent, boolean loop) {

  /** Default detail level (0.5). */
  public static final float DEFAULT_DETAIL = 0.5f;
  /** Default motion level (0.5). */
  public static final float DEFAULT_MOTION = 0.5f;
  /** Default spread level (0.5). */
  public static final float DEFAULT_SPREAD = 0.5f;
  /** Default accent level (0.5). */
  public static final float DEFAULT_ACCENT = 0.5f;

  /** Compact constructor — clamps all float parameters to [0, 1]. */
  public AmbienceProfile {
    detail = clamp01(detail);
    motion = clamp01(motion);
    spread = clamp01(spread);
    accent = clamp01(accent);
  }

  /**
   * Create a profile with all default parameter values.
   *
   * @param loop whether the ambience should loop
   * @return a balanced default profile
   */
  public static AmbienceProfile defaults(boolean loop) {
    return new AmbienceProfile(
        DEFAULT_DETAIL,
        DEFAULT_MOTION,
        DEFAULT_SPREAD,
        DEFAULT_ACCENT,
        loop);
  }

  /** Create a looping profile with all default parameter values. */
  public static AmbienceProfile defaults() {
    return defaults(true);
  }

  /** Clamp a float to [0, 1]. */
  private static float clamp01(float value) {
    if (value < 0f) return 0f;
    if (value > 1f) return 1f;
    return value;
  }
}
