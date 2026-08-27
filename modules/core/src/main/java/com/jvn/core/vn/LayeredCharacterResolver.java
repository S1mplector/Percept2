package com.jvn.core.vn;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shared helpers for resolving layered character references such as
 * {@code $eyes_happy}, {@code $eyes=happy}, or {@code $shared.eyes=happy}.
 */
public final class LayeredCharacterResolver {
  private static final char[] GROUP_VARIANT_SEPARATORS = {'_', '-', '/', '.', ':'};
  private static final Set<String> ANATOMICAL_TOKENS = Set.of(
      "body", "neck", "head", "face", "mouth", "eye", "eyes", "brow", "brows",
      "hair", "arm", "arms", "hand", "hands", "leg", "legs", "foot", "feet",
      "torso", "chest", "hip", "hips", "shoulder", "shoulders");
  private static final Set<String> LANE_MODIFIERS = Set.of(
      "front", "behind", "back", "rear", "left", "right", "upper", "lower");

  private LayeredCharacterResolver() {
  }

  public record CharacterRef(String characterId, String localId) {}
  public record LayerMatch(String characterId, String layerId, String path) {}
  public record LayerChange(String fromLayerId, String toLayerId) {}
  public record ExpressionLayerDiff(
      List<String> unchangedLayerIds,
      List<LayerChange> changedPairs,
      List<String> addedLayerIds,
      List<String> removedLayerIds) {}

  /**
   * Diffs the declared layer IDs of two expressions so unchanged layers can stay stable
   * during a crossfade while only added/removed/changed layers transition.
   */
  public static ExpressionLayerDiff diffExpressionLayers(List<String> fromLayerIds, List<String> toLayerIds) {
    List<String> from = fromLayerIds == null ? List.of() : fromLayerIds;
    List<String> to = toLayerIds == null ? List.of() : toLayerIds;
    Set<String> toSet = new LinkedHashSet<>(to);

    List<String> unchanged = new ArrayList<>();
    List<String> removedCandidates = new ArrayList<>();
    for (String layerId : from) {
      if (toSet.contains(layerId)) {
        unchanged.add(layerId);
      } else {
        removedCandidates.add(layerId);
      }
    }

    Set<String> fromSet = new LinkedHashSet<>(from);
    List<String> addedCandidates = new ArrayList<>();
    for (String layerId : to) {
      if (!fromSet.contains(layerId)) {
        addedCandidates.add(layerId);
      }
    }

    List<LayerChange> changedPairs = new ArrayList<>();
    List<String> removed = new ArrayList<>();
    List<String> remainingAdded = new ArrayList<>(addedCandidates);
    for (String removedId : removedCandidates) {
      String replacement = inferReplacementLayerId(removedId, remainingAdded);
      if (replacement != null) {
        changedPairs.add(new LayerChange(removedId, replacement));
        remainingAdded.remove(replacement);
      } else {
        removed.add(removedId);
      }
    }

    return new ExpressionLayerDiff(unchanged, changedPairs, remainingAdded, removed);
  }

  public static CharacterRef parseReference(String rawRef, String defaultCharacterId) {
    String ref = rawRef == null ? "" : rawRef.trim();
    String characterId = defaultCharacterId == null ? "" : defaultCharacterId.trim();
    String localId = ref;

    int eq = ref.indexOf('=');
    int sep = characterSeparatorIndex(ref, eq);
    if (sep > 0) {
      characterId = ref.substring(0, sep).trim();
      localId = ref.substring(sep + 1).trim();
    }

    return new CharacterRef(characterId, localId);
  }

  public static String resolveLayerPath(Map<String, ? extends Map<String, String>> layersByCharacter,
                                        String defaultCharacterId,
                                        String rawRef) {
    List<LayerMatch> matches = resolveLayerMatches(layersByCharacter, defaultCharacterId, rawRef);
    return matches.isEmpty() ? null : matches.get(0).path();
  }

  /**
   * Resolve an exact layer reference or a glob such as {@code body_*}. Globs
   * are useful in {@code @chargroup} declarations so a semantic animation
   * group can cover many sprite variants without repeating every identifier.
   */
  public static List<LayerMatch> resolveLayerMatches(
      Map<String, ? extends Map<String, String>> layersByCharacter,
      String defaultCharacterId,
      String rawRef
  ) {
    if (layersByCharacter == null || rawRef == null || rawRef.isBlank()) return List.of();
    CharacterRef ref = parseReference(rawRef, defaultCharacterId);
    if (ref.characterId() == null || ref.characterId().isBlank()
        || ref.localId() == null || ref.localId().isBlank()) {
      return List.of();
    }
    Map<String, String> layerMap = layersByCharacter.get(ref.characterId());
    if (layerMap == null || layerMap.isEmpty()) return List.of();

    String localId = ref.localId();
    if (!containsGlob(localId)) {
      for (String candidate : candidateLayerIds(localId)) {
        String path = layerMap.get(candidate);
        if (path != null && !path.isBlank()) {
          return List.of(new LayerMatch(ref.characterId(), candidate, path.trim()));
        }
      }
      return List.of();
    }

    return layerMap.entrySet().stream()
        .filter(entry -> entry.getKey() != null && globMatches(localId, entry.getKey()))
        .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
        .sorted(Map.Entry.comparingByKey())
        .map(entry -> new LayerMatch(ref.characterId(), entry.getKey(), entry.getValue().trim()))
        .toList();
  }

  public static boolean containsGlob(String value) {
    return value != null && (value.indexOf('*') >= 0 || value.indexOf('?') >= 0);
  }

  /**
   * Joins backslash-continued layered declarations while retaining the same
   * number of physical lines, so editor and parser diagnostics keep accurate
   * source locations.
   */
  public static String collapseLayerDirectiveContinuations(String source) {
    if (source == null || source.isEmpty()) return source == null ? "" : source;
    String[] lines = source.split("\r\n|\r|\n", -1);
    for (int start = 0; start < lines.length; start++) {
      String trimmed = lines[start] == null ? "" : lines[start].trim();
      if (!startsLayerDirective(trimmed) || !hasTrailingContinuation(trimmed)) continue;
      StringBuilder logical = new StringBuilder(lines[start]);
      int cursor = start;
      while (hasTrailingContinuation(logical.toString().trim()) && cursor + 1 < lines.length) {
        String withoutSlash = removeTrailingContinuation(logical.toString());
        logical.setLength(0);
        logical.append(withoutSlash).append(' ').append(lines[++cursor].trim());
        lines[cursor] = "";
      }
      lines[start] = logical.toString();
      start = cursor;
    }
    return String.join("\n", lines);
  }

  private static boolean startsLayerDirective(String trimmed) {
    if (trimmed == null) return false;
    return trimmed.regionMatches(true, 0, "@chargroup", 0, "@chargroup".length())
        || trimmed.regionMatches(true, 0, "@charpreset", 0, "@charpreset".length());
  }

  private static boolean hasTrailingContinuation(String value) {
    if (value == null) return false;
    int index = value.length() - 1;
    while (index >= 0 && Character.isWhitespace(value.charAt(index))) index--;
    return index >= 0 && value.charAt(index) == '\\';
  }

  private static String removeTrailingContinuation(String value) {
    if (value == null || value.isEmpty()) return "";
    int index = value.length() - 1;
    while (index >= 0 && Character.isWhitespace(value.charAt(index))) index--;
    if (index >= 0 && value.charAt(index) == '\\') {
      return value.substring(0, index).stripTrailing();
    }
    return value;
  }

  static boolean globMatches(String pattern, String value) {
    if (pattern == null || value == null) return false;
    int patternIndex = 0;
    int valueIndex = 0;
    int starIndex = -1;
    int starValueIndex = -1;
    while (valueIndex < value.length()) {
      if (patternIndex < pattern.length()
          && (pattern.charAt(patternIndex) == '?' || pattern.charAt(patternIndex) == value.charAt(valueIndex))) {
        patternIndex++;
        valueIndex++;
      } else if (patternIndex < pattern.length() && pattern.charAt(patternIndex) == '*') {
        starIndex = patternIndex++;
        starValueIndex = valueIndex;
      } else if (starIndex >= 0) {
        patternIndex = starIndex + 1;
        valueIndex = ++starValueIndex;
      } else {
        return false;
      }
    }
    while (patternIndex < pattern.length() && pattern.charAt(patternIndex) == '*') patternIndex++;
    return patternIndex == pattern.length();
  }

  public static List<String> candidateLayerIds(String rawLayerId) {
    String layerId = rawLayerId == null ? "" : rawLayerId.trim();
    if (layerId.isBlank()) {
      return List.of();
    }

    Set<String> candidates = new LinkedHashSet<>();
    candidates.add(layerId);

    GroupVariant groupVariant = parseGroupVariant(layerId);
    if (groupVariant != null) {
      candidates.add(groupVariant.groupId() + "=" + groupVariant.variantId());
      for (char separator : GROUP_VARIANT_SEPARATORS) {
        candidates.add(groupVariant.groupId() + separator + groupVariant.variantId());
      }
    }

    return new ArrayList<>(candidates);
  }

  /**
   * Infer which previously animated declared layer is the replacement lane for
   * a newly visible layer. Returns {@code null} when no safe, unique match exists.
   * Explicit {@code @chargroup} metadata remains authoritative; this is the
   * convention-based fallback for projects with many one-off sprite variants.
   */
  public static String inferReplacementLayerId(
      String activeLayerId,
      Collection<String> animatedLayerIds
  ) {
    if (activeLayerId == null || activeLayerId.isBlank()
        || animatedLayerIds == null || animatedLayerIds.isEmpty()) {
      return null;
    }
    String active = activeLayerId.trim();
    String activeLane = replacementLane(active);
    if (activeLane.isBlank()) return null;

    List<ReplacementCandidate> candidates = new ArrayList<>();
    for (String rawCandidate : animatedLayerIds) {
      if (rawCandidate == null || rawCandidate.isBlank()) continue;
      String candidate = rawCandidate.trim();
      if (active.equals(candidate) || !activeLane.equals(replacementLane(candidate))) continue;
      candidates.add(new ReplacementCandidate(candidate, replacementScore(active, candidate)));
    }
    if (candidates.isEmpty()) return null;
    candidates.sort(Comparator
        .comparingInt(ReplacementCandidate::score).reversed()
        .thenComparing(ReplacementCandidate::layerId));
    ReplacementCandidate best = candidates.get(0);
    if (candidates.size() > 1 && candidates.get(1).score() == best.score()) return null;
    return best.layerId();
  }

  public static String replacementLane(String rawLayerId) {
    List<String> tokens = layerTokens(rawLayerId);
    if (tokens.isEmpty()) return "";
    for (int i = 0; i < tokens.size(); i++) {
      String token = tokens.get(i);
      if (!ANATOMICAL_TOKENS.contains(token)) continue;
      if (i + 1 < tokens.size() && LANE_MODIFIERS.contains(tokens.get(i + 1))) {
        return token + "_" + tokens.get(i + 1);
      }
      return token;
    }
    return tokens.get(0);
  }

  private static int replacementScore(String left, String right) {
    List<String> a = layerTokens(left);
    List<String> b = layerTokens(right);
    int prefix = 0;
    while (prefix < a.size() && prefix < b.size() && a.get(prefix).equals(b.get(prefix))) prefix++;
    Set<String> shared = new LinkedHashSet<>(a);
    shared.retainAll(b);
    return prefix * 100 + shared.size();
  }

  private static List<String> layerTokens(String rawLayerId) {
    String normalized = rawLayerId == null ? "" : rawLayerId.trim().toLowerCase(java.util.Locale.ROOT);
    if (normalized.isBlank()) return List.of();
    List<String> tokens = new ArrayList<>();
    for (String token : normalized.split("[^a-z0-9]+")) {
      if (!token.isBlank()) tokens.add(token);
    }
    return tokens;
  }

  private static int characterSeparatorIndex(String ref, int eqIndex) {
    if (ref == null || ref.isBlank()) {
      return -1;
    }
    int dot = ref.indexOf('.');
    int colon = ref.indexOf(':');
    int sep = -1;
    if (eqIndex >= 0) {
      if (dot > 0 && dot < eqIndex) sep = dot;
      if (colon > 0 && colon < eqIndex) sep = sep < 0 ? colon : Math.min(sep, colon);
      return sep;
    }
    if (dot > 0) sep = dot;
    if (colon > 0) sep = sep < 0 ? colon : Math.min(sep, colon);
    return sep;
  }

  private static GroupVariant parseGroupVariant(String rawLayerId) {
    String layerId = rawLayerId == null ? "" : rawLayerId.trim();
    if (layerId.isBlank()) {
      return null;
    }

    int eq = layerId.indexOf('=');
    if (eq > 0 && eq < layerId.length() - 1) {
      String group = layerId.substring(0, eq).trim();
      String variant = layerId.substring(eq + 1).trim();
      if (!group.isBlank() && !variant.isBlank()) {
        return new GroupVariant(group, variant);
      }
    }

    int sep = firstGroupVariantSeparator(layerId);
    if (sep > 0 && sep < layerId.length() - 1) {
      String group = layerId.substring(0, sep).trim();
      String variant = layerId.substring(sep + 1).trim();
      if (!group.isBlank() && !variant.isBlank()) {
        return new GroupVariant(group, variant);
      }
    }

    return null;
  }

  private static int firstGroupVariantSeparator(String layerId) {
    int best = -1;
    for (char separator : GROUP_VARIANT_SEPARATORS) {
      int idx = layerId.indexOf(separator);
      if (idx > 0 && idx < layerId.length() - 1) {
        best = best < 0 ? idx : Math.min(best, idx);
      }
    }
    return best;
  }

  private record GroupVariant(String groupId, String variantId) {}
  private record ReplacementCandidate(String layerId, int score) {}
}
