package com.jvn.editor.ui;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jvn.core.vn.LayeredCharacterResolver;

final class LayeredCharacterProjectCatalog {
  private static final Map<String, String> GROUP_TOKEN_ALIASES = Map.ofEntries(
      Map.entry("eye", "eyes"),
      Map.entry("eyes", "eyes"),
      Map.entry("mouth", "mouth"),
      Map.entry("lip", "mouth"),
      Map.entry("lips", "mouth"),
      Map.entry("brow", "brow"),
      Map.entry("eyebrow", "brow"),
      Map.entry("eyebrows", "brow"),
      Map.entry("base", "base"),
      Map.entry("body", "body"),
      Map.entry("hair", "hair"),
      Map.entry("face", "face"),
      Map.entry("faces", "face"),
      Map.entry("snoot", "snoot"),
      Map.entry("snoots", "snoot"),
      Map.entry("outfit", "outfit"),
      Map.entry("clothes", "outfit"),
      Map.entry("accessory", "accessory"),
      Map.entry("accessories", "accessory"),
      Map.entry("acc", "accessory"),
      Map.entry("add", "add"),
      Map.entry("addition", "add"),
      Map.entry("additions", "add")
  );
  private static final Pattern CHARLAYER_PATTERN =
      Pattern.compile("^\\s*@charlayer\\s+(\\S+)\\s+(\\S+)\\s+(.+)$", Pattern.CASE_INSENSITIVE);
  private static final Pattern CHARPRESET_PATTERN =
      Pattern.compile("^\\s*@charpreset\\s+(\\S+)\\s+(\\S+)\\s+(.+)$", Pattern.CASE_INSENSITIVE);

  private LayeredCharacterProjectCatalog() {}

  static Catalog load(File projectRoot) {
    if (projectRoot == null || !projectRoot.isDirectory()) return Catalog.empty();
    Path scriptsRoot = ScriptEditorWorkspaceModel.resolveScriptsRoot(projectRoot);
    if (scriptsRoot == null || !Files.isDirectory(scriptsRoot)) return Catalog.empty();

    Map<String, String> sources = new LinkedHashMap<>();
    try (var stream = Files.walk(scriptsRoot, 16)) {
      List<Path> files = stream
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".vns"))
          .sorted(Comparator.comparing(path -> path.toString().toLowerCase(Locale.ROOT)))
          .toList();
      for (Path file : files) {
        String sourceName = scriptsRoot.relativize(file).toString().replace('\\', '/');
        sources.put(sourceName, Files.readString(file, StandardCharsets.UTF_8));
      }
    } catch (IOException ex) {
      return Catalog.empty();
    }
    return parseSources(sources);
  }

  static Catalog parseSources(Map<String, String> sources) {
    if (sources == null || sources.isEmpty()) return Catalog.empty();

    Map<String, LinkedHashMap<String, String>> layersByCharacter = new LinkedHashMap<>();
    Map<String, LinkedHashMap<String, String>> rawPresetsByCharacter = new LinkedHashMap<>();

    List<Map.Entry<String, String>> orderedSources = new ArrayList<>(sources.entrySet());
    orderedSources.sort(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER));
    for (Map.Entry<String, String> entry : orderedSources) {
      parseSource(entry.getValue(), layersByCharacter, rawPresetsByCharacter);
    }

    if (layersByCharacter.isEmpty()) return Catalog.empty();

    Map<String, LinkedHashMap<String, List<ResolvedLayer>>> resolvedPresetsByCharacter = new LinkedHashMap<>();
    Map<String, Set<String>> splitLayerIdsByCharacter = new LinkedHashMap<>();
    for (Map.Entry<String, LinkedHashMap<String, String>> entry : rawPresetsByCharacter.entrySet()) {
      String characterId = entry.getKey();
      LinkedHashMap<String, List<ResolvedLayer>> resolvedPresets = new LinkedHashMap<>();
      Map<String, List<ResolvedLayer>> memo = new LinkedHashMap<>();
      for (String presetId : entry.getValue().keySet()) {
        List<ResolvedLayer> resolved = resolvePreset(
            layersByCharacter,
            rawPresetsByCharacter,
            characterId,
            presetId,
            memo,
            new ArrayDeque<>());
        if (!resolved.isEmpty()) {
          resolvedPresets.put(presetId, List.copyOf(resolved));
        }
      }
      if (!resolvedPresets.isEmpty()) {
        resolvedPresetsByCharacter.put(characterId, resolvedPresets);
        splitLayerIdsByCharacter.put(characterId, findSplitLayerIds(characterId, resolvedPresets));
      }
    }

    Map<String, MutableDeclaredSet> setsById = new LinkedHashMap<>();
    for (Map.Entry<String, LinkedHashMap<String, String>> entry : layersByCharacter.entrySet()) {
      String characterId = entry.getKey();
      LinkedHashMap<String, String> layers = entry.getValue();
      if (layers == null || layers.isEmpty()) continue;

      String setId = chooseSetId(characterId, layers);
      if (setId.isBlank()) continue;
      MutableDeclaredSet set = uniqueSet(setsById, setId, characterId);

      Set<String> splitLayerIds = splitLayerIdsByCharacter.getOrDefault(characterId, Set.of());
      Map<String, String> finalGroupByLayerId = new LinkedHashMap<>();
      int order = 0;
      for (Map.Entry<String, String> layerEntry : layers.entrySet()) {
        String layerId = layerEntry.getKey() == null ? "" : layerEntry.getKey().trim();
        String relativePath = normalizeRelativePath(layerEntry.getValue());
        if (layerId.isBlank() || relativePath.isBlank()) continue;

        GroupLabel raw = inferGroupLabel(layerId, relativePath);
        String finalGroup = splitLayerIds.contains(layerId) ? sanitizeId(layerId) : raw.groupId();
        if (finalGroup.isBlank()) {
          finalGroup = raw.groupId().isBlank() ? sanitizeId(layerId) : raw.groupId();
        }
        if (finalGroup.isBlank()) finalGroup = "layer";

        String label = raw.label();
        if (label.isBlank()) {
          label = sanitizeLabel(layerId);
        }
        if (label.isBlank()) label = "default";

        finalGroupByLayerId.put(layerId, finalGroup);
        set.addOption(new DeclaredOption(layerId, finalGroup, label, relativePath, order++));
      }

      LinkedHashMap<String, List<ResolvedLayer>> resolvedPresets =
          resolvedPresetsByCharacter.getOrDefault(characterId, new LinkedHashMap<>());
      for (Map.Entry<String, List<ResolvedLayer>> presetEntry : resolvedPresets.entrySet()) {
        LinkedHashMap<String, String> selectionsByGroup = new LinkedHashMap<>();
        for (ResolvedLayer resolvedLayer : presetEntry.getValue()) {
          if (!Objects.equals(characterId, resolvedLayer.characterId())) continue;
          String layerId = resolvedLayer.layerId();
          if (layerId == null || layerId.isBlank()) continue;
          String groupId = finalGroupByLayerId.get(layerId);
          if (groupId == null || groupId.isBlank()) continue;
          String relativePath = normalizeRelativePath(resolvedLayer.relativePath());
          if (relativePath.isBlank()) continue;
          selectionsByGroup.put(groupId, relativePath);
        }
        if (!selectionsByGroup.isEmpty()) {
          set.addPreset(new DeclaredPreset(presetEntry.getKey(), selectionsByGroup));
        }
      }
    }

    if (setsById.isEmpty()) return Catalog.empty();
    LinkedHashMap<String, DeclaredSet> immutableSets = new LinkedHashMap<>();
    for (MutableDeclaredSet set : setsById.values()) {
      immutableSets.put(set.setId, set.freeze());
    }
    return new Catalog(immutableSets);
  }

  static GroupLabel inferGroupLabel(String layerId, String relativePath) {
    String normalizedLayerId = sanitizeId(layerId);
    if (!normalizedLayerId.isBlank()) {
      int eq = normalizedLayerId.indexOf('=');
      if (eq > 0 && eq < normalizedLayerId.length() - 1) {
        String group = normalizedLayerId.substring(0, eq);
        String label = sanitizeLabel(normalizedLayerId.substring(eq + 1));
        return new GroupLabel(group, label);
      }

      String[] tokens = normalizedLayerId.split("_+");
      if (tokens.length == 1) {
        return new GroupLabel(tokens[0], sanitizeLabel(tokens[0]));
      }
      if ("arm".equals(tokens[0]) && tokens.length >= 3) {
        return new GroupLabel(
            "arm_" + tokens[1],
            sanitizeLabel(String.join("_", java.util.Arrays.copyOfRange(tokens, 2, tokens.length))));
      }
      if ("body".equals(tokens[0]) && tokens.length >= 3 && tokens[1].contains("arm")) {
        return new GroupLabel(
            "body_arms",
            sanitizeLabel(String.join("_", java.util.Arrays.copyOfRange(tokens, 2, tokens.length))));
      }
      return new GroupLabel(
          tokens[0],
          sanitizeLabel(String.join("_", java.util.Arrays.copyOfRange(tokens, 1, tokens.length))));
    }

    String group = inferGroupFromSetSubfolder(relativePath);
    if (group.isBlank() && relativePath != null) {
      group = sanitizeId(takeLastPathToken(parentPath(relativePath)));
    }
    String label = "";
    if (relativePath != null && !relativePath.isBlank()) {
      String base = takeLastPathToken(relativePath);
      int dot = base.lastIndexOf('.');
      if (dot > 0) base = base.substring(0, dot);
      label = inferLabelFromFilenameForGroup(base, group);
    }
    return new GroupLabel(group, label);
  }

  private static void parseSource(String source,
                                  Map<String, LinkedHashMap<String, String>> layersByCharacter,
                                  Map<String, LinkedHashMap<String, String>> rawPresetsByCharacter) {
    if (source == null || source.isBlank()) return;
    String[] lines = source.split("\\R");
    for (String line : lines) {
      if (line == null) continue;

      Matcher layerMatcher = CHARLAYER_PATTERN.matcher(line);
      if (layerMatcher.matches()) {
        String characterId = layerMatcher.group(1).trim();
        String layerId = layerMatcher.group(2).trim();
        String path = stripQuotes(layerMatcher.group(3).trim());
        if (characterId.isBlank() || layerId.isBlank() || path.isBlank()) continue;
        layersByCharacter.computeIfAbsent(characterId, key -> new LinkedHashMap<>()).put(layerId, path);
        continue;
      }

      Matcher presetMatcher = CHARPRESET_PATTERN.matcher(line);
      if (presetMatcher.matches()) {
        String characterId = presetMatcher.group(1).trim();
        String presetId = presetMatcher.group(2).trim();
        String spec = presetMatcher.group(3).trim();
        if (characterId.isBlank() || presetId.isBlank() || spec.isBlank()) continue;
        rawPresetsByCharacter.computeIfAbsent(characterId, key -> new LinkedHashMap<>()).put(presetId, spec);
      }
    }
  }

  private static List<ResolvedLayer> resolvePreset(Map<String, LinkedHashMap<String, String>> layersByCharacter,
                                                   Map<String, LinkedHashMap<String, String>> rawPresetsByCharacter,
                                                   String characterId,
                                                   String presetId,
                                                   Map<String, List<ResolvedLayer>> memo,
                                                   Deque<String> stack) {
    String key = characterId + "/" + presetId;
    List<ResolvedLayer> cached = memo.get(key);
    if (cached != null) return cached;
    if (stack.contains(key)) return List.of();

    Map<String, String> presetMap = rawPresetsByCharacter.get(characterId);
    if (presetMap == null) return List.of();
    String spec = presetMap.get(presetId);
    if (spec == null || spec.isBlank()) return List.of();

    stack.push(key);
    List<ResolvedLayer> resolved = new ArrayList<>();
    try {
      for (String token : spec.split("\\|")) {
        if (token == null) continue;
        String part = token.trim();
        if (part.isEmpty()) continue;
        if (part.startsWith("$")) {
          ResolvedLayer layer = resolveLayerReference(layersByCharacter, characterId, part.substring(1).trim());
          if (layer != null) resolved.add(layer);
        } else if (part.startsWith("@")) {
          LayeredCharacterResolver.CharacterRef ref =
              LayeredCharacterResolver.parseReference(part.substring(1).trim(), characterId);
          if (ref.characterId() == null || ref.characterId().isBlank()
              || ref.localId() == null || ref.localId().isBlank()) {
            continue;
          }
          resolved.addAll(resolvePreset(
              layersByCharacter,
              rawPresetsByCharacter,
              ref.characterId(),
              ref.localId(),
              memo,
              stack));
        } else {
          String path = normalizeRelativePath(stripQuotes(part));
          if (!path.isBlank()) {
            resolved.add(new ResolvedLayer(characterId, "", path));
          }
        }
      }
    } finally {
      stack.pop();
    }

    List<ResolvedLayer> immutable = List.copyOf(resolved);
    memo.put(key, immutable);
    return immutable;
  }

  private static ResolvedLayer resolveLayerReference(Map<String, ? extends Map<String, String>> layersByCharacter,
                                                     String defaultCharacterId,
                                                     String rawRef) {
    if (layersByCharacter == null || rawRef == null || rawRef.isBlank()) return null;
    LayeredCharacterResolver.CharacterRef ref = LayeredCharacterResolver.parseReference(rawRef, defaultCharacterId);
    if (ref.characterId() == null || ref.characterId().isBlank()
        || ref.localId() == null || ref.localId().isBlank()) {
      return null;
    }
    Map<String, String> layerMap = layersByCharacter.get(ref.characterId());
    if (layerMap == null || layerMap.isEmpty()) return null;
    for (String candidate : LayeredCharacterResolver.candidateLayerIds(ref.localId())) {
      String path = layerMap.get(candidate);
      if (path != null && !path.isBlank()) {
        return new ResolvedLayer(ref.characterId(), candidate, normalizeRelativePath(path));
      }
    }
    return null;
  }

  private static Set<String> findSplitLayerIds(String characterId,
                                               Map<String, List<ResolvedLayer>> resolvedPresets) {
    Set<String> out = new LinkedHashSet<>();
    if (resolvedPresets == null || resolvedPresets.isEmpty()) return out;

    Map<String, LinkedHashSet<String>> layersByGroup = new LinkedHashMap<>();
    Map<String, Map<String, Set<String>>> cooccurrenceByGroup = new LinkedHashMap<>();

    for (List<ResolvedLayer> presetLayers : resolvedPresets.values()) {
      if (presetLayers == null || presetLayers.isEmpty()) continue;
      Map<String, List<String>> selectedLayerIdsByGroup = new LinkedHashMap<>();
      for (ResolvedLayer layer : presetLayers) {
        if (!Objects.equals(characterId, layer.characterId())) continue;
        if (layer.layerId() == null || layer.layerId().isBlank()) continue;
        GroupLabel inferred = inferGroupLabel(layer.layerId(), layer.relativePath());
        if (inferred.groupId().isBlank()) continue;
        layersByGroup.computeIfAbsent(inferred.groupId(), key -> new LinkedHashSet<>()).add(layer.layerId());
        selectedLayerIdsByGroup.computeIfAbsent(inferred.groupId(), key -> new ArrayList<>()).add(layer.layerId());
      }
      for (Map.Entry<String, List<String>> entry : selectedLayerIdsByGroup.entrySet()) {
        List<String> selected = entry.getValue();
        if (selected.size() < 2) continue;
        Map<String, Set<String>> cooccurs = cooccurrenceByGroup.computeIfAbsent(entry.getKey(), key -> new LinkedHashMap<>());
        for (String layerId : selected) {
          Set<String> others = cooccurs.computeIfAbsent(layerId, key -> new LinkedHashSet<>());
          for (String other : selected) {
            if (!Objects.equals(layerId, other)) {
              others.add(other);
            }
          }
        }
      }
    }

    for (Map.Entry<String, LinkedHashSet<String>> entry : layersByGroup.entrySet()) {
      List<String> groupLayers = new ArrayList<>(entry.getValue());
      Map<String, Set<String>> cooccurs = cooccurrenceByGroup.getOrDefault(entry.getKey(), Map.of());
      boolean additivePair = groupLayers.size() == 2
          && cooccurs.getOrDefault(groupLayers.get(0), Set.of()).contains(groupLayers.get(1))
          && cooccurs.getOrDefault(groupLayers.get(1), Set.of()).contains(groupLayers.get(0));

      for (String layerId : groupLayers) {
        Set<String> siblings = cooccurs.getOrDefault(layerId, Set.of());
        if (siblings.isEmpty()) continue;
        GroupLabel inferred = inferGroupLabel(layerId, "");
        String label = sanitizeId(inferred.label());
        boolean looksFoundational = "base".equals(label)
            || "default".equals(label)
            || "neutral".equals(label);
        if (siblings.size() > 1 || looksFoundational || additivePair) {
          out.add(layerId);
        }
      }
    }

    return out;
  }

  private static String chooseSetId(String characterId, Map<String, String> layers) {
    LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
    for (String path : layers.values()) {
      String normalizedPath = normalizeRelativePath(path);
      if (normalizedPath.isBlank()) continue;
      String setId = deriveSetIdFromRelative(normalizedPath);
      if (setId.isBlank() || "(root)".equals(setId)) continue;
      counts.put(setId, counts.getOrDefault(setId, 0) + 1);
    }

    String best = "";
    int bestCount = -1;
    for (Map.Entry<String, Integer> entry : counts.entrySet()) {
      if (entry.getValue() > bestCount) {
        best = entry.getKey();
        bestCount = entry.getValue();
      }
    }
    if (!best.isBlank()) return best;
    return "characters/" + sanitizeId(characterId);
  }

  private static MutableDeclaredSet uniqueSet(Map<String, MutableDeclaredSet> setsById, String setId, String characterId) {
    MutableDeclaredSet existing = setsById.get(setId);
    if (existing == null) {
      MutableDeclaredSet created = new MutableDeclaredSet(setId, characterId);
      setsById.put(setId, created);
      return created;
    }
    if (Objects.equals(existing.characterId, characterId)) {
      return existing;
    }

    String seed = setId + " (" + characterId + ")";
    String unique = seed;
    int counter = 2;
    while (setsById.containsKey(unique)) {
      unique = seed + " " + counter;
      counter++;
    }
    MutableDeclaredSet created = new MutableDeclaredSet(unique, characterId);
    setsById.put(unique, created);
    return created;
  }

  private static String normalizeRelativePath(String rawPath) {
    if (rawPath == null) return "";
    String normalized = rawPath.trim().replace('\\', '/');
    while (normalized.startsWith("./")) {
      normalized = normalized.substring(2);
    }
    return normalized;
  }

  private static String stripQuotes(String value) {
    if (value == null) return "";
    String trimmed = value.trim();
    if (trimmed.length() >= 2) {
      char first = trimmed.charAt(0);
      char last = trimmed.charAt(trimmed.length() - 1);
      if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
        return trimmed.substring(1, trimmed.length() - 1);
      }
    }
    return trimmed;
  }

  private static String sanitizeId(String raw) {
    if (raw == null) return "";
    String s = raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_");
    s = s.replaceAll("^_+", "").replaceAll("_+$", "");
    return s;
  }

  private static String sanitizeLabel(String raw) {
    if (raw == null) return "";
    String s = raw.trim().replaceAll("[\\s]+", "_");
    s = s.replaceAll("[^a-zA-Z0-9_]+", "_");
    s = s.replaceAll("^_+", "").replaceAll("_+$", "");
    return s;
  }

  private static String parentPath(String path) {
    if (path == null || path.isBlank()) return "";
    int slash = path.lastIndexOf('/');
    return slash <= 0 ? "" : path.substring(0, slash);
  }

  private static String takeLastPathToken(String path) {
    if (path == null || path.isBlank()) return "";
    int slash = path.lastIndexOf('/');
    return slash < 0 ? path : path.substring(slash + 1);
  }

  private static String deriveSetIdFromRelative(String relative) {
    String path = relative == null ? "" : relative.replace('\\', '/');
    String parent = parentPath(path);
    if (parent.isBlank()) return "(root)";

    String[] parts = parent.split("/");
    int characterIndex = findPathSegmentIndex(parts, "characters");
    if (characterIndex >= 0 && characterIndex + 1 < parts.length) {
      return String.join("/", java.util.Arrays.copyOfRange(parts, 0, characterIndex + 2));
    }
    if (parts.length >= 3 && "assets".equals(parts[0]) && "characters".equals(parts[1])) {
      return "assets/characters/" + parts[2];
    }
    if (parts.length >= 2 && "assets".equals(parts[0])) {
      return "assets/" + parts[1];
    }
    if (parts.length >= 2) {
      return parts[0] + "/" + parts[1];
    }
    return parent;
  }

  private static int findPathSegmentIndex(String[] parts, String segment) {
    if (parts == null || segment == null || segment.isBlank()) return -1;
    for (int i = 0; i < parts.length; i++) {
      if (segment.equalsIgnoreCase(parts[i])) return i;
    }
    return -1;
  }

  private static String inferGroupFromSetSubfolder(String relativePath) {
    if (relativePath == null || relativePath.isBlank()) return "";
    String normalized = relativePath.replace('\\', '/');
    String parent = parentPath(normalized);
    if (parent.isBlank()) return "";
    String setId = deriveSetIdFromRelative(normalized);
    if (setId == null || setId.isBlank() || "(root)".equals(setId)) return "";
    String prefix = setId + "/";
    if (!parent.startsWith(prefix)) return "";
    String remainder = parent.substring(prefix.length());
    if (remainder.isBlank()) return "";
    String[] rawSegments = remainder.split("/");
    List<String> segments = new ArrayList<>();
    for (String segment : rawSegments) {
      String normalizedSegment = sanitizeId(segment);
      if (!normalizedSegment.isBlank()) segments.add(normalizedSegment);
    }
    if (segments.isEmpty()) return "";
    if (segments.size() == 1) {
      String only = segments.get(0);
      if (only.startsWith("arm")) return only.replaceFirst("_[0-9]+$", "");
      return only;
    }

    String first = segments.get(0);
    String second = segments.get(1);
    if (first.startsWith("arm")) return first;

    if ("body".equals(first)) {
      if (second.startsWith("tail")) return second;
      if (second.contains("arm")) return "body_arms";
      return first;
    }

    if ("head".equals(first)) {
      if (("normal".equals(second) || "tilted".equals(second)) && segments.size() >= 3) {
        return segments.get(2);
      }
      return second;
    }

    return first;
  }

  private static String inferLabelFromFilenameForGroup(String baseName, String group) {
    String[] tokens = splitTokens(baseName);
    if (tokens.length == 0) return "";
    String normalizedGroup = sanitizeId(group);
    int match = -1;
    for (int i = 0; i < tokens.length; i++) {
      String tokenGroup = normalizeGroupToken(tokens[i]);
      if (!tokenGroup.isBlank() && tokenGroup.equals(normalizedGroup)) {
        match = i;
      }
    }
    if (match >= 0 && match + 1 < tokens.length) {
      return sanitizeLabel(String.join("_", java.util.Arrays.copyOfRange(tokens, match + 1, tokens.length)));
    }
    if (match >= 0) {
      return sanitizeLabel(tokens[match]);
    }
    return sanitizeLabel(tokens[tokens.length - 1]);
  }

  private static String[] splitTokens(String raw) {
    if (raw == null || raw.isBlank()) return new String[0];
    return raw.split("[\\s._-]+");
  }

  private static String normalizeGroupToken(String rawToken) {
    String token = sanitizeId(rawToken);
    if (token.isBlank()) return "";
    return GROUP_TOKEN_ALIASES.getOrDefault(token, "");
  }

  record Catalog(Map<String, DeclaredSet> setsById) {
    static Catalog empty() {
      return new Catalog(Map.of());
    }
  }

  record DeclaredSet(String setId,
                     String characterId,
                     List<String> groupOrder,
                     Map<String, List<DeclaredOption>> groups,
                     Map<String, DeclaredPreset> presets) {
    String defaultPresetName() {
      if (presets == null || presets.isEmpty()) return "";
      for (String key : List.of("neutral", "default", "idle", "normal")) {
        for (String presetName : presets.keySet()) {
          if (key.equalsIgnoreCase(presetName)) return presetName;
        }
      }
      return presets.keySet().iterator().next();
    }
  }

  record DeclaredOption(String layerId, String groupId, String label, String relativePath, int order) {}

  record DeclaredPreset(String name, Map<String, String> selectionsByGroup) {}

  record GroupLabel(String groupId, String label) {}

  private record ResolvedLayer(String characterId, String layerId, String relativePath) {}

  private static final class MutableDeclaredSet {
    final String setId;
    final String characterId;
    final LinkedHashMap<String, List<DeclaredOption>> groups = new LinkedHashMap<>();
    final List<String> groupOrder = new ArrayList<>();
    final LinkedHashMap<String, DeclaredPreset> presets = new LinkedHashMap<>();

    private MutableDeclaredSet(String setId, String characterId) {
      this.setId = setId;
      this.characterId = characterId;
    }

    private void addOption(DeclaredOption option) {
      if (option == null) return;
      groups.computeIfAbsent(option.groupId(), key -> {
        groupOrder.add(option.groupId());
        return new ArrayList<>();
      }).add(option);
    }

    private void addPreset(DeclaredPreset preset) {
      if (preset == null || preset.name() == null || preset.name().isBlank()) return;
      presets.put(preset.name(), preset);
    }

    private DeclaredSet freeze() {
      LinkedHashMap<String, List<DeclaredOption>> frozenGroups = new LinkedHashMap<>();
      for (Map.Entry<String, List<DeclaredOption>> entry : groups.entrySet()) {
        frozenGroups.put(entry.getKey(), List.copyOf(entry.getValue()));
      }
      LinkedHashMap<String, DeclaredPreset> frozenPresets = new LinkedHashMap<>(presets);
      return new DeclaredSet(setId, characterId, List.copyOf(groupOrder), frozenGroups, frozenPresets);
    }
  }
}
