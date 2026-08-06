package com.jvn.core.vn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jvn.core.vn.stage.VnStagePreset;

/**
 * Represents a complete visual novel scenario with script nodes and branches
 */
public class VnScenario {
  private final String id;
  private final List<VnNode> nodes;
  private final Map<String, Integer> labels; // label -> node index
  private final Map<String, VnCharacter> characters;
  private final Map<String, VnBackground> backgrounds;
  private final Map<String, VnStagePreset> stagePresets;
  private final Map<String, VnGroup> groups;

  private VnScenario(Builder builder) {
    this.id = builder.id;
    this.nodes = new ArrayList<>(builder.nodes);
    this.labels = new HashMap<>(builder.labels);
    this.characters = new HashMap<>(builder.characters);
    this.backgrounds = new HashMap<>(builder.backgrounds);
    this.stagePresets = new HashMap<>(builder.stagePresets);
    this.groups = new HashMap<>(builder.groups);
  }

  public String getId() { return id; }
  public List<VnNode> getNodes() { return nodes; }
  public VnNode getNode(int index) {
    return index >= 0 && index < nodes.size() ? nodes.get(index) : null;
  }
  public Integer getLabelIndex(String label) { return labels.get(label); }
  public String findLabelAtOrBefore(int nodeIndex) {
    String best = null;
    int bestIndex = Integer.MIN_VALUE;
    for (Map.Entry<String, Integer> entry : labels.entrySet()) {
      Integer index = entry.getValue();
      if (index == null || index > nodeIndex || index < bestIndex) continue;
      bestIndex = index;
      best = entry.getKey();
    }
    return best;
  }
  public VnCharacter getCharacter(String id) { return characters.get(id); }
  public VnBackground getBackground(String id) { return backgrounds.get(id); }
  public VnStagePreset getStagePreset(String id) { return stagePresets.get(id); }
  public Map<String, VnStagePreset> getStagePresets() { return stagePresets; }
  public VnGroup getGroup(String id) { return groups.get(id); }
  public Map<String, VnGroup> getGroups() { return groups; }

  public static Builder builder(String id) { return new Builder(id); }

  public static class Builder {
    private final String id;
    private final List<VnNode> nodes = new ArrayList<>();
    private final Map<String, Integer> labels = new HashMap<>();
    private final Map<String, VnCharacter> characters = new HashMap<>();
    private final Map<String, VnBackground> backgrounds = new HashMap<>();
    private final Map<String, VnStagePreset> stagePresets = new HashMap<>();
    private final Map<String, VnGroup> groups = new HashMap<>();
    private int sourceLine;

    private Builder(String id) { this.id = id; }

    public Builder addNode(VnNode node) {
      if (node != null && node.getSourceLine() <= 0 && sourceLine > 0) {
        node = node.withSourceLine(sourceLine);
      }
      nodes.add(node);
      return this;
    }

    public Builder sourceLine(int line) {
      sourceLine = Math.max(0, line);
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

    public Builder addStagePreset(VnStagePreset stagePreset) {
      if (stagePreset != null && !stagePreset.getId().isBlank()) {
        stagePresets.put(stagePreset.getId(), stagePreset);
      }
      return this;
    }

    public Builder addGroup(VnGroup group) {
      if (group != null && !group.id().isBlank()) {
        groups.put(group.id(), group);
      }
      return this;
    }

    public VnScenario build() { return new VnScenario(this); }
  }
}
