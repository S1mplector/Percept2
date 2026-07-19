package com.jvn.core.animation;

import com.jvn.plugin.api.animation.AnimationEasing;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Immutable descriptor for either a built-in or contributed easing curve. */
public final class EasingSpec {
  private static final Pattern FUNCTION_PATTERN =
      Pattern.compile("^([a-zA-Z_][\\w.-]*)\\s*\\((.*)\\)$");
  private static final Pattern EXTENSION_ID =
      Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_-]*(?:\\.[a-zA-Z_][a-zA-Z0-9_-]*)+$");

  private final Easing.Type type;
  private final double[] parameters;
  private final String extensionId;
  private final Map<String, Double> namedParameters;

  private EasingSpec(Easing.Type type, double[] parameters, String extensionId,
                     Map<String, Double> namedParameters) {
    this.type = type != null ? type : Easing.Type.LINEAR;
    this.parameters = parameters != null ? Arrays.copyOf(parameters, parameters.length) : null;
    this.extensionId = extensionId;
    this.namedParameters = namedParameters == null ? Map.of()
        : Collections.unmodifiableMap(new LinkedHashMap<>(namedParameters));
  }

  public static EasingSpec of(Easing.Type type) {
    return new EasingSpec(type, Easing.coerceParameters(type, null), null, null);
  }

  public static EasingSpec of(Easing.Type type, double[] parameters) {
    return new EasingSpec(type, Easing.coerceParameters(type, parameters), null, null);
  }

  public static EasingSpec extension(String id, Map<String, Double> parameters) {
    String normalized = EasingExtensions.normalizeId(id);
    AnimationEasing easing = EasingExtensions.find(normalized)
        .orElseThrow(() -> new IllegalArgumentException("Unknown easing extension: " + id));
    Map<String, Double> resolved = EasingExtensions.resolveParameters(easing, normalizeNames(parameters));
    return new EasingSpec(Easing.Type.LINEAR, null, normalized, resolved);
  }

  public static EasingSpec cubicBezier(double cx1, double cy1, double cx2, double cy2) {
    return of(Easing.Type.CUSTOM, new double[]{cx1, cy1, cx2, cy2});
  }
  public static EasingSpec curve(double... points) { return of(Easing.Type.CURVE, points); }
  public static EasingSpec spring(double stiffness, double damping, double mass, double velocity) {
    return of(Easing.Type.SPRING, new double[]{stiffness, damping, mass, velocity});
  }
  public static EasingSpec dampedSpring(double frequency, double dampingRatio, double response, double velocity) {
    return of(Easing.Type.DAMPED_SPRING, new double[]{frequency, dampingRatio, response, velocity});
  }

  public static EasingSpec tryParse(String raw) {
    if (raw == null || raw.isBlank()) return of(Easing.Type.LINEAR);
    String value = stripQuotes(raw.trim());
    Matcher matcher = FUNCTION_PATTERN.matcher(value);
    if (matcher.matches()) {
      String function = matcher.group(1).trim();
      String builtIn = function.toLowerCase(Locale.ROOT).replace('-', '_');
      if (EXTENSION_ID.matcher(function).matches()) {
        try { return extension(function, parseNamedArguments(matcher.group(2))); }
        catch (IllegalArgumentException ignored) { return null; }
      }
      double[] args = parseArguments(matcher.group(2));
      if (args == null) return null;
      return switch (builtIn) {
        case "cubic_bezier" -> args.length == 4 ? cubicBezier(args[0], args[1], args[2], args[3]) : null;
        case "curve" -> args.length >= 2 && args.length % 2 == 0 ? curve(args) : null;
        case "spring" -> of(Easing.Type.SPRING, args);
        case "damped_spring" -> of(Easing.Type.DAMPED_SPRING, args);
        default -> null;
      };
    }
    if (EXTENSION_ID.matcher(value).matches()) {
      try { return extension(value, Map.of()); }
      catch (IllegalArgumentException ignored) { return null; }
    }
    try { return of(Easing.Type.valueOf(normalizeTypeToken(value))); }
    catch (Exception ignored) { return null; }
  }

  public static EasingSpec parseOrDefault(String raw) {
    EasingSpec parsed = tryParse(raw);
    return parsed != null ? parsed : of(Easing.Type.LINEAR);
  }

  public Easing.Type getType() { return type; }
  public boolean isExtension() { return extensionId != null; }
  public String getExtensionId() { return extensionId; }
  public Map<String, Double> getNamedParameters() { return namedParameters; }
  public boolean hasParameters() { return isExtension() ? !namedParameters.isEmpty() : parameters != null && parameters.length > 0; }
  public double[] getParameters() { return parameters != null ? Arrays.copyOf(parameters, parameters.length) : null; }
  public String toDslString() { return Easing.formatSpec(this); }
  @Override public String toString() { return toDslString(); }

  @Override public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof EasingSpec other)) return false;
    return type == other.type && Arrays.equals(parameters, other.parameters)
        && Objects.equals(extensionId, other.extensionId) && namedParameters.equals(other.namedParameters);
  }
  @Override public int hashCode() { return Objects.hash(type, Arrays.hashCode(parameters), extensionId, namedParameters); }

  private static String normalizeTypeToken(String token) {
    String normalized = camelToSnake(token == null ? "" : token.trim()).toUpperCase(Locale.ROOT).replace('-', '_');
    if ("EASE_IN".equals(normalized)) return "EASE_IN_QUAD";
    if ("EASE_OUT".equals(normalized)) return "EASE_OUT_QUAD";
    if ("EASE_IN_OUT".equals(normalized)) return "EASE_IN_OUT_QUAD";
    return normalized;
  }

  private static String camelToSnake(String token) {
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
    if (raw == null || raw.trim().isEmpty()) return new double[0];
    String[] parts = raw.split(",");
    double[] values = new double[parts.length];
    for (int i = 0; i < parts.length; i++) {
      try { values[i] = Double.parseDouble(parts[i].trim()); }
      catch (Exception ignored) { return null; }
    }
    return values;
  }

  private static Map<String, Double> parseNamedArguments(String raw) {
    if (raw == null || raw.isBlank()) return Map.of();
    LinkedHashMap<String, Double> values = new LinkedHashMap<>();
    for (String part : raw.split(",")) {
      String[] pair = part.trim().split("\\s*[:=]\\s*", 2);
      if (pair.length != 2 || pair[0].isBlank()) throw new IllegalArgumentException("Expected name: value");
      String name = pair[0].trim().toLowerCase(Locale.ROOT).replace('-', '_');
      if (values.containsKey(name)) throw new IllegalArgumentException("Duplicate easing parameter: " + name);
      values.put(name, Double.parseDouble(pair[1].trim()));
    }
    return values;
  }

  private static Map<String, Double> normalizeNames(Map<String, Double> parameters) {
    if (parameters == null || parameters.isEmpty()) return Map.of();
    LinkedHashMap<String, Double> normalized = new LinkedHashMap<>();
    parameters.forEach((name, value) -> normalized.put(name.toLowerCase(Locale.ROOT).replace('-', '_'), value));
    return normalized;
  }

  private static String stripQuotes(String value) {
    if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
        || (value.startsWith("'") && value.endsWith("'")))) return value.substring(1, value.length() - 1);
    return value;
  }
}
