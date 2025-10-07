package com.jvn.core.vn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a complete visual novel scenario with script nodes and branches
 */
public class VnScenario {
  private final String id;
  private final List<VnNode> nodes;
  private final Map<String, Integer> labels; // label -> node index
  private final Map<String, VnCharacter> characters;
  private final Map<String, VnBackground> backgrounds;

  private VnScenario(Builder builder) {
    this.id = builder.id;
    this.nodes = new ArrayList<>(builder.nodes);
    this.labels = new HashMap<>(builder.labels);
    this.characters = new HashMap<>(builder.characters);
    this.backgrounds = new HashMap<>(builder.backgrounds);
  }

  public String getId() { return id; }
  public List<VnNode> getNodes() { return nodes; }
  public VnNode getNode(int index) {
    return index >= 0 && index < nodes.size() ? nodes.get(index) : null;
  }
  public Integer getLabelIndex(String label) { return labels.get(label); }
  public VnCharacter getCharacter(String id) { return characters.get(id); }
  public VnBackground getBackground(String id) { return backgrounds.get(id); }

  public static Builder builder(String id) { return new Builder(id); }

  public static class Builder {
    private final String id;
    private final List<VnNode> nodes = new ArrayList<>();
    private final Map<String, Integer> labels = new HashMap<>();
    private final Map<String, VnCharacter> characters = new HashMap<>();
    private final Map<String, VnBackground> backgrounds = new HashMap<>();

    private Builder(String id) { this.id = id; }

    public Builder addNode(VnNode node) {
      nodes.add(node);
      return this;
    }

    public Builder addLabel(String label) {
      labels.put(label, nodes.size());
      return this;
    }

    public Builder addCharacter(VnCharacter character) {
      characters.put(character.getId(), character);
      return this;
    }

    public Builder addBackground(VnBackground background) {
      backgrounds.put(background.getId(), background);
      return this;
    }

    public VnScenario build() { return new VnScenario(this); }
  }
}
