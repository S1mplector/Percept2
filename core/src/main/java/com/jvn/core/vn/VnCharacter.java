package com.jvn.core.vn;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a character in the visual novel with multiple expressions
 */
public class VnCharacter {
  private final String id;
  private final String displayName;
  private final Map<String, String> expressions; // expression name -> image path

  private VnCharacter(Builder builder) {
    this.id = builder.id;
    this.displayName = builder.displayName;
    this.expressions = new HashMap<>(builder.expressions);
  }

  public String getId() { return id; }
  public String getDisplayName() { return displayName; }
  public String getExpressionPath(String expression) {
    return expressions.getOrDefault(expression, expressions.get("neutral"));
  }
  public boolean hasExpression(String expression) {
    return expressions.containsKey(expression);
  }

  public static Builder builder(String id) { return new Builder(id); }

  public static class Builder {
    private final String id;
    private String displayName;
    private final Map<String, String> expressions = new HashMap<>();

    private Builder(String id) {
      this.id = id;
      this.displayName = id;
    }

    public Builder displayName(String name) { this.displayName = name; return this; }
    public Builder addExpression(String name, String imagePath) {
      expressions.put(name, imagePath);
      return this;
    }
    public VnCharacter build() { return new VnCharacter(this); }
  }
}
