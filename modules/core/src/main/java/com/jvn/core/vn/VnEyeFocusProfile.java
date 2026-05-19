package com.jvn.core.vn;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Character-specific mapping from keypad gaze positions to pupil/eye layers.
 */
public final class VnEyeFocusProfile {
  private static final Pattern AUTO_LAYER_PATTERN = Pattern.compile(
      "(?i)(?:^|[_-])(eyes?|pupils?)[_-]?(0?[1-9])(?:$|[_-])");

  private final String characterId;
  private final String expression;
  private final String sourceAnchor;
  private final double sourceX;
  private final double sourceY;
  private final double deadZone;
  private final double maxNudgePx;
  private final double strength;
  private final Map<Integer, String> layerIds;

  public VnEyeFocusProfile(
      String characterId,
      String expression,
      String sourceAnchor,
      double sourceX,
      double sourceY,
      double deadZone,
      double maxNudgePx,
      double strength,
      Map<Integer, String> layerIds
  ) {
    this.characterId = clean(characterId);
    this.expression = clean(expression).isBlank() ? "neutral" : clean(expression);
    this.sourceAnchor = clean(sourceAnchor);
    this.sourceX = finite(sourceX, 0.5);
    this.sourceY = finite(sourceY, 0.26);
    this.deadZone = finite(deadZone, 0.12);
    this.maxNudgePx = finite(maxNudgePx, 3.0);
    this.strength = finite(strength, 1.0);
    Map<Integer, String> copy = new LinkedHashMap<>();
    if (layerIds != null) {
      for (Map.Entry<Integer, String> entry : layerIds.entrySet()) {
        Integer index = entry.getKey();
        String layer = clean(entry.getValue());
        if (index == null || index < 1 || index > 9 || layer.isBlank()) continue;
        copy.put(index, layer);
      }
    }
    this.layerIds = Collections.unmodifiableMap(copy);
  }

  public String characterId() { return characterId; }
  public String expression() { return expression; }
  public String sourceAnchor() { return sourceAnchor; }
  public double sourceX() { return sourceX; }
  public double sourceY() { return sourceY; }
  public double deadZone() { return deadZone; }
  public double maxNudgePx() { return maxNudgePx; }
  public double strength() { return strength; }
  public Map<Integer, String> layerIds() { return layerIds; }
  public String layerIdFor(int keypadIndex) { return layerIds.get(keypadIndex); }
  public boolean complete() { return layerIds.size() >= 9; }

  public String key() {
    return key(characterId, expression);
  }

  public static String key(String characterId, String expression) {
    String c = clean(characterId);
    String e = clean(expression);
    if (e.isBlank()) e = "neutral";
    return c + "/" + e;
  }

  public static Optional<VnEyeFocusProfile> autoDetect(VnCharacter character, String expression) {
    if (character == null) return Optional.empty();
    String expr = clean(expression).isBlank() ? "neutral" : clean(expression);
    Map<Integer, String> layers = new LinkedHashMap<>();
    for (String layerId : character.getLayerIds()) {
      int index = detectKeypadIndex(layerId);
      if (index >= 1 && index <= 9) {
        layers.putIfAbsent(index, layerId);
      }
    }
    if (layers.isEmpty()) return Optional.empty();
    return Optional.of(new VnEyeFocusProfile(
        character.getId(),
        expr,
        "eyes",
        0.5,
        0.26,
        0.12,
        3.0,
        1.0,
        layers));
  }

  public static int detectKeypadIndex(String layerId) {
    String value = clean(layerId);
    if (value.isBlank()) return -1;
    Matcher matcher = AUTO_LAYER_PATTERN.matcher(value);
    if (!matcher.find()) return -1;
    try {
      return Integer.parseInt(matcher.group(2));
    } catch (NumberFormatException ex) {
      return -1;
    }
  }

  private static String clean(String raw) {
    return raw == null ? "" : raw.trim();
  }

  private static double finite(double value, double fallback) {
    return Double.isFinite(value) ? value : fallback;
  }

  @Override
  public String toString() {
    return String.format(Locale.ROOT, "VnEyeFocusProfile[%s, layers=%d]", key(), layerIds.size());
  }
}
