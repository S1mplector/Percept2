package com.jvn.core.rpg;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class Item implements Serializable {
  public enum Type { CONSUMABLE, EQUIPMENT, KEY }

  private String id;
  private String name;
  private String description;
  private Type type = Type.CONSUMABLE;
  private Map<String, Double> modifiers = new HashMap<>();

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public String getDescription() { return description; }
  public void setDescription(String description) { this.description = description; }
  public Type getType() { return type; }
  public void setType(Type type) { this.type = type == null ? Type.CONSUMABLE : type; }

  public Map<String, Double> getModifiers() { return modifiers; }
  public void setModifiers(Map<String, Double> modifiers) { this.modifiers = modifiers == null ? new HashMap<>() : modifiers; }
}
