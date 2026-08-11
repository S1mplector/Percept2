package com.jvn.core.vn;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Represents a character in the visual novel with multiple expressions
 */
public class VnCharacter {
  private final String id;
  private final String displayName;
  private final String nameColor;
  private final double scale;
  private final Map<String, String> expressions; // expression name -> image path
  private final Map<String, String> layerPaths; // @charlayer id -> image path
  private final Map<String, List<String>> expressionLayerIds; // expression name -> ordered @charlayer ids
  private final Map<String, LayerGroup> layerGroups; // @chargroup id -> layer group metadata

  private VnCharacter(Builder builder) {
    this.id = builder.id;
    this.displayName = builder.displayName;
    this.nameColor = builder.nameColor;
    this.scale = builder.scale;
    this.expressions = new HashMap<>(builder.expressions);
    this.layerPaths = new HashMap<>(builder.layerPaths);
    this.expressionLayerIds = new HashMap<>(builder.expressionLayerIds);
    this.layerGroups = new LinkedHashMap<>(builder.layerGroups);
  }

  public String getId() { return id; }
  public String getDisplayName() { return displayName; }
  /** Optional authored speaker/name-box color in {@code #RRGGBB[AA]} form. */
  public String getNameColor() { return nameColor; }
  /** Persistent authored sprite scale applied on top of the global character height factor. */
  public double getScale() { return scale; }
  public String getExpressionPath(String expression) {
    return expressions.getOrDefault(expression, expressions.get("neutral"));
  }
  public boolean hasExpression(String expression) {
    return expressions.containsKey(expression);
  }
  public String getLayerPath(String layerId) {
    return layerPaths.get(layerId);
  }
  public Map<String, String> getLayerPaths() {
    return Map.copyOf(layerPaths);
  }
  public Set<String> getLayerIds() {
    LinkedHashSet<String> ids = new LinkedHashSet<>();
    for (List<String> expressionIds : expressionLayerIds.values()) {
      ids.addAll(expressionIds);
    }
    ids.addAll(layerPaths.keySet());
    return Set.copyOf(ids);
  }
  public List<String> getExpressionLayerIds(String expression) {
    List<String> ids = expressionLayerIds.get(expression);
    if ((ids == null || ids.isEmpty()) && !"neutral".equals(expression)) {
      ids = expressionLayerIds.get("neutral");
    }
    return ids == null ? List.of() : ids;
  }
  public Map<String, List<String>> getExpressionLayerIdsByName() {
    return Map.copyOf(expressionLayerIds);
  }
  public LayerGroup getLayerGroup(String groupId) {
    if (groupId == null || groupId.isBlank()) return null;
    return layerGroups.get(groupId.trim());
  }
  public Map<String, LayerGroup> getLayerGroups() {
    return Map.copyOf(layerGroups);
  }
  public List<LayerGroup> getLayerGroupChainForLayer(String layerId) {
    if (layerGroups.isEmpty() || layerId == null || layerId.isBlank()) return List.of();
    LayerGroup deepest = null;
    int deepestDepth = -1;
    for (LayerGroup group : layerGroups.values()) {
      if (group == null || !group.containsLayerId(layerId)) continue;
      int depth = layerGroupDepth(group.id(), new LinkedHashSet<>());
      if (depth > deepestDepth) {
        deepest = group;
        deepestDepth = depth;
      }
    }
    if (deepest == null) return List.of();

    LinkedHashSet<String> seen = new LinkedHashSet<>();
    List<LayerGroup> chain = new java.util.ArrayList<>();
    LayerGroup current = deepest;
    while (current != null && seen.add(current.id())) {
      chain.add(0, current);
      String parent = current.parentId();
      current = parent == null || parent.isBlank() ? null : layerGroups.get(parent);
    }
    return List.copyOf(chain);
  }

  private int layerGroupDepth(String groupId, Set<String> seen) {
    if (groupId == null || groupId.isBlank() || !seen.add(groupId)) return 0;
    LayerGroup group = layerGroups.get(groupId);
    if (group == null || group.parentId().isBlank()) return 0;
    return 1 + layerGroupDepth(group.parentId(), seen);
  }

  private static boolean equivalentLayerId(String left, String right) {
    if (left == null || right == null) return false;
    String a = left.trim();
    String b = right.trim();
    if (a.equals(b)) return true;
    return LayeredCharacterResolver.candidateLayerIds(a).contains(b)
        || LayeredCharacterResolver.candidateLayerIds(b).contains(a);
  }

  public record LayerGroup(
      String id,
      String parentId,
      List<String> layerIds,
      double pivotX,
      double pivotY,
      boolean hasPivot
  ) {
    public LayerGroup {
      id = id == null ? "" : id.trim();
      parentId = parentId == null ? "" : parentId.trim();
      layerIds = layerIds == null ? List.of() : List.copyOf(layerIds);
      if (!Double.isFinite(pivotX)) pivotX = 0.5;
      if (!Double.isFinite(pivotY)) pivotY = 1.0;
    }

    public boolean containsLayerId(String layerId) {
      if (layerId == null || layerId.isBlank() || layerIds.isEmpty()) return false;
      for (String member : layerIds) {
        if (equivalentLayerId(layerId, member)) return true;
      }
      return false;
    }
  }

  public static Builder builder(String id) { return new Builder(id); }

  public static class Builder {
    private final String id;
    private String displayName;
    private String nameColor;
    private double scale;
    private final Map<String, String> expressions = new HashMap<>();
    private final Map<String, String> layerPaths = new HashMap<>();
    private final Map<String, List<String>> expressionLayerIds = new HashMap<>();
    private final Map<String, LayerGroup> layerGroups = new LinkedHashMap<>();

    private Builder(String id) {
      this.id = id;
      this.displayName = id;
      this.nameColor = null;
      this.scale = 1.0;
    }

    public Builder displayName(String name) { this.displayName = name; return this; }
    public Builder nameColor(String color) {
      this.nameColor = color == null || color.isBlank() ? null : color.trim();
      return this;
    }
    public Builder scale(double scale) {
      if (!Double.isFinite(scale) || scale < 0.1 || scale > 3.0) {
        throw new IllegalArgumentException("Character scale must be between 0.1 and 3.0");
      }
      this.scale = scale;
      return this;
    }
    public String getDisplayName() { return displayName; }
    public String getNameColor() { return nameColor; }
    public double getScale() { return scale; }
    public boolean hasExpression(String name) { return expressions.containsKey(name); }
    public String getExpressionPath(String name) { return expressions.get(name); }
    public String getLayerPath(String layerId) { return layerPaths.get(layerId); }
    public Set<String> getLayerIds() { return Set.copyOf(layerPaths.keySet()); }
    public List<String> getExpressionLayerIds(String name) {
      List<String> ids = expressionLayerIds.get(name);
      return ids == null ? List.of() : ids;
    }
    public LayerGroup getLayerGroup(String groupId) {
      if (groupId == null || groupId.isBlank()) return null;
      return layerGroups.get(groupId.trim());
    }
    public Map<String, LayerGroup> getLayerGroups() {
      return Map.copyOf(layerGroups);
    }
    public Builder addExpression(String name, String imagePath) {
      expressions.put(name, imagePath);
      expressionLayerIds.remove(name);
      return this;
    }
    public Builder addExpression(String name, String imagePath, List<String> layerIds) {
      expressions.put(name, imagePath);
      if (layerIds == null || layerIds.isEmpty()) {
        expressionLayerIds.remove(name);
      } else {
        expressionLayerIds.put(name, List.copyOf(layerIds));
      }
      return this;
    }
    public Builder addLayerGroup(String groupId, String parentId, List<String> layerIds) {
      return addLayerGroup(groupId, parentId, layerIds, 0.5, 1.0, false);
    }
    public Builder addLayerGroup(String groupId,
                                 String parentId,
                                 List<String> layerIds,
                                 double pivotX,
                                 double pivotY,
                                 boolean hasPivot) {
      if (groupId != null && !groupId.isBlank() && layerIds != null && !layerIds.isEmpty()) {
        layerGroups.put(groupId.trim(), new LayerGroup(groupId, parentId, layerIds, pivotX, pivotY, hasPivot));
      }
      return this;
    }
    public Builder addLayer(String layerId, String path) {
      if (layerId != null && !layerId.isBlank() && path != null && !path.isBlank()) {
        layerPaths.put(layerId, path);
      }
      return this;
    }
    public VnCharacter build() { return new VnCharacter(this); }
  }
}
