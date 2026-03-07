package com.jvn.audiofx;

/**
 * Builds valid VNS {@code [synthesizer ...]} command strings from
 * {@link SynthPreviewSettings}. Pure logic, no UI dependencies.
 */
public final class VnsCommandBuilder {
  private VnsCommandBuilder() {}

  /**
   * Build a complete {@code [synthesizer on ...]} command from the given settings.
   * Only emits parameters that differ from their defaults to keep commands concise.
   */
  public static String buildOnCommand(SynthPreviewSettings s) {
    if (s == null) return "[synthesizer on]";

    StringBuilder sb = new StringBuilder("[synthesizer on");

    if (s.type() == SynthPreviewSettings.SynthType.CHIPTUNE) {
      sb.append(" type:chiptune");
      appendQuoted(sb, "cue", s.cueId());
      appendFloat(sb, "intensity", s.intensity(), 0.85f);
      appendFloat(sb, "volume", s.volume(), 0.70f);
      appendBool(sb, "loop", s.loop(), false);
    } else {
      appendQuoted(sb, "mode", s.preset());
      appendFloat(sb, "intensity", s.intensity(), 0.65f);
      appendFloat(sb, "volume", s.volume(), 0.45f);
      appendFloat(sb, "detail", s.detail(), 0.50f);
      appendFloat(sb, "motion", s.motion(), 0.50f);
      appendFloat(sb, "spread", s.spread(), 0.50f);
      appendFloat(sb, "accent", s.accent(), 0.50f);
      appendBool(sb, "loop", s.loop(), true);
    }

    sb.append(']');
    return sb.toString();
  }

  /**
   * Build a verbose command that always includes all parameters regardless of defaults.
   * Useful for documentation or explicit authoring.
   */
  public static String buildVerboseCommand(SynthPreviewSettings s) {
    if (s == null) return "[synthesizer on]";

    StringBuilder sb = new StringBuilder("[synthesizer on");

    if (s.type() == SynthPreviewSettings.SynthType.CHIPTUNE) {
      sb.append(" type:chiptune");
      appendQuoted(sb, "cue", s.cueId());
      appendFloatAlways(sb, "intensity", s.intensity());
      appendFloatAlways(sb, "volume", s.volume());
      appendBoolAlways(sb, "loop", s.loop());
    } else {
      appendQuoted(sb, "mode", s.preset());
      appendFloatAlways(sb, "intensity", s.intensity());
      appendFloatAlways(sb, "volume", s.volume());
      appendFloatAlways(sb, "detail", s.detail());
      appendFloatAlways(sb, "motion", s.motion());
      appendFloatAlways(sb, "spread", s.spread());
      appendFloatAlways(sb, "accent", s.accent());
      appendBoolAlways(sb, "loop", s.loop());
    }

    sb.append(']');
    return sb.toString();
  }

  /** Build a {@code [synthesizer off]} command with optional type qualifier. */
  public static String buildOffCommand(SynthPreviewSettings.SynthType type) {
    if (type == null) return "[synthesizer off]";
    return switch (type) {
      case CHIPTUNE -> "[synthesizer off type:chiptune]";
      case AMBIENCE -> "[synthesizer off type:ambience]";
    };
  }

  // --- Internal helpers ---

  private static void appendQuoted(StringBuilder sb, String key, String value) {
    sb.append(' ').append(key).append(":\"").append(value == null ? "" : value).append('"');
  }

  private static void appendFloat(StringBuilder sb, String key, float value, float defaultValue) {
    if (Math.abs(value - defaultValue) > 0.005f) {
      appendFloatAlways(sb, key, value);
    }
  }

  private static void appendFloatAlways(StringBuilder sb, String key, float value) {
    sb.append(' ').append(key).append(':').append(String.format("%.2f", value));
  }

  private static void appendBool(StringBuilder sb, String key, boolean value, boolean defaultValue) {
    if (value != defaultValue) {
      appendBoolAlways(sb, key, value);
    }
  }

  private static void appendBoolAlways(StringBuilder sb, String key, boolean value) {
    sb.append(' ').append(key).append(':').append(value);
  }
}
