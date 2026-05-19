package com.jvn.core.vn;

import java.util.HashMap;
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
  private final Map<String, String> expressions; // expression name -> image path
  private final Map<String, String> layerPaths; // @charlayer id -> image path
  private final Map<String, List<String>> expressionLayerIds; // expression name -> ordered @charlayer ids

  private VnCharacter(Builder builder) {
    this.id = builder.id;
    this.displayName = builder.displayName;
    this.expressions = new HashMap<>(builder.expressions);
    this.layerPaths = new HashMap<>(builder.layerPaths);
    this.expressionLayerIds = new HashMap<>(builder.expressionLayerIds);
  }

  public String getId() { return id; }
  public String getDisplayName() { return displayName; }
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

  public static Builder builder(String id) { return new Builder(id); }

  public static class Builder {
    private final String id;
    private String displayName;
    private final Map<String, String> expressions = new HashMap<>();
    private final Map<String, String> layerPaths = new HashMap<>();
    private final Map<String, List<String>> expressionLayerIds = new HashMap<>();

    private Builder(String id) {
      this.id = id;
      this.displayName = id;
    }

    public Builder displayName(String name) { this.displayName = name; return this; }
    public String getDisplayName() { return displayName; }
    public boolean hasExpression(String name) { return expressions.containsKey(name); }
    public String getExpressionPath(String name) { return expressions.get(name); }
    public String getLayerPath(String layerId) { return layerPaths.get(layerId); }
    public Set<String> getLayerIds() { return Set.copyOf(layerPaths.keySet()); }
    public List<String> getExpressionLayerIds(String name) {
      List<String> ids = expressionLayerIds.get(name);
      return ids == null ? List.of() : ids;
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
    public Builder addLayer(String layerId, String path) {
      if (layerId != null && !layerId.isBlank() && path != null && !path.isBlank()) {
        layerPaths.put(layerId, path);
      }
      return this;
    }
    public VnCharacter build() { return new VnCharacter(this); }
  }
}
