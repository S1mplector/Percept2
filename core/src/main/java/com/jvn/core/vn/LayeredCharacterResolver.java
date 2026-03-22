package com.jvn.core.vn;

import java.util.ArrayList;
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

  private LayeredCharacterResolver() {
  }

  public record CharacterRef(String characterId, String localId) {}

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
    if (layersByCharacter == null || rawRef == null || rawRef.isBlank()) {
      return null;
    }
    CharacterRef ref = parseReference(rawRef, defaultCharacterId);
    if (ref.characterId() == null || ref.characterId().isBlank() || ref.localId() == null || ref.localId().isBlank()) {
      return null;
    }
    Map<String, String> layerMap = layersByCharacter.get(ref.characterId());
    if (layerMap == null || layerMap.isEmpty()) {
      return null;
    }
    for (String candidate : candidateLayerIds(ref.localId())) {
      String path = layerMap.get(candidate);
      if (path != null && !path.isBlank()) {
        return path.trim();
      }
    }
    return null;
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
}
