package com.jvn.core.animation;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Immutable easing descriptor that carries an easing type and any optional
 * parameter payload required to evaluate it.
 */
public final class EasingSpec {
  private static final Pattern FUNCTION_PATTERN =
      Pattern.compile("^([a-zA-Z_][\\w-]*)\\s*\\((.*)\\)$");

  private final Easing.Type type;
  private final double[] parameters;

  private EasingSpec(Easing.Type type, double[] parameters) {
    this.type = type != null ? type : Easing.Type.LINEAR;
    this.parameters = parameters != null ? Arrays.copyOf(parameters, parameters.length) : null;
  }

  public static EasingSpec of(Easing.Type type) {
    return new EasingSpec(type, Easing.coerceParameters(type, null));
  }

  public static EasingSpec of(Easing.Type type, double[] parameters) {
    return new EasingSpec(type, Easing.coerceParameters(type, parameters));
  }

  public static EasingSpec cubicBezier(double cx1, double cy1, double cx2, double cy2) {
    return of(Easing.Type.CUSTOM, new double[]{cx1, cy1, cx2, cy2});
  }

  public static EasingSpec curve(double... points) {
    return of(Easing.Type.CURVE, points);
  }

  public static EasingSpec spring(double stiffness, double damping, double mass, double velocity) {
    return of(Easing.Type.SPRING, new double[]{stiffness, damping, mass, velocity});
  }

  public static EasingSpec dampedSpring(
      double frequency,
      double dampingRatio,
      double response,
      double velocity
  ) {
    return of(Easing.Type.DAMPED_SPRING,
        new double[]{frequency, dampingRatio, response, velocity});
  }

  public static EasingSpec tryParse(String raw) {
    if (raw == null || raw.isBlank()) return of(Easing.Type.LINEAR);

    String value = raw.trim();
    Matcher matcher = FUNCTION_PATTERN.matcher(value);
    if (matcher.matches()) {
      String function = matcher.group(1).trim().toLowerCase(Locale.ROOT).replace('-', '_');
      double[] args = parseArguments(matcher.group(2));
      if (args == null) return null;
      return switch (function) {
        case "cubic_bezier" -> args.length == 4
            ? cubicBezier(args[0], args[1], args[2], args[3])
            : null;
        case "curve" -> args.length >= 2 && args.length % 2 == 0
            ? curve(args)
            : null;
        case "spring" -> of(Easing.Type.SPRING, args);
        case "damped_spring" -> of(Easing.Type.DAMPED_SPRING, args);
        default -> null;
      };
    }

    String normalized = normalizeTypeToken(value);
    try {
      return of(Easing.Type.valueOf(normalized));
    } catch (Exception ignored) {
      return null;
    }
  }

  public static EasingSpec parseOrDefault(String raw) {
    EasingSpec parsed = tryParse(raw);
    return parsed != null ? parsed : of(Easing.Type.LINEAR);
  }

  public Easing.Type getType() {
    return type;
  }

  public boolean hasParameters() {
    return parameters != null && parameters.length > 0;
  }

  public double[] getParameters() {
    return hasParameters() ? Arrays.copyOf(parameters, parameters.length) : null;
  }

  public String toDslString() {
    return Easing.formatSpec(this);
  }

  @Override
  public String toString() {
    return toDslString();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof EasingSpec other)) return false;
    return type == other.type && Arrays.equals(parameters, other.parameters);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, Arrays.hashCode(parameters));
  }

  private static String normalizeTypeToken(String token) {
    String normalized = camelToSnake(token == null ? "" : token.trim())
        .toUpperCase(Locale.ROOT)
        .replace('-', '_');
    if ("EASE_IN".equals(normalized)) return "EASE_IN_QUAD";
    if ("EASE_OUT".equals(normalized)) return "EASE_OUT_QUAD";
    if ("EASE_IN_OUT".equals(normalized)) return "EASE_IN_OUT_QUAD";
    return normalized;
  }

  private static String camelToSnake(String token) {
    if (token == null || token.isBlank()) return "";
    StringBuilder out = new StringBuilder(token.length() + 8);
    for (int i = 0; i < token.length(); i++) {
      char c = token.charAt(i);
      if (i > 0 && Character.isUpperCase(c)) {
        char prev = token.charAt(i - 1);
        if (Character.isLowerCase(prev) || Character.isDigit(prev)) out.append('_');
      }
      out.append(c);
    }
    return out.toString();
  }

  private static double[] parseArguments(String raw) {
    if (raw == null) return new double[0];
    String trimmed = raw.trim();
    if (trimmed.isEmpty()) return new double[0];

    String[] parts = trimmed.split(",");
    double[] values = new double[parts.length];
    for (int i = 0; i < parts.length; i++) {
      try {
        values[i] = Double.parseDouble(parts[i].trim());
      } catch (Exception ignored) {
        return null;
      }
    }
    return values;
  }
}
