package com.jvn.core.rpg;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Quest implements Serializable {
  private String id;
  private String title;
  private final List<QuestStage> stages = new ArrayList<>();
  private int currentStage = 0;
  private boolean completed;

  public Quest() {}
  public Quest(String id, String title) { this.id = id; this.title = title; }

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getTitle() { return title; }
  public void setTitle(String title) { this.title = title; }

  public List<QuestStage> getStages() { return stages; }
  public void addStage(QuestStage stage) { if (stage != null) stages.add(stage); }

  public QuestStage getCurrentStage() {
    if (currentStage < 0 || currentStage >= stages.size()) return null;
    return stages.get(currentStage);
  }

  public void advance() {
    if (currentStage < stages.size()) currentStage++;
    if (currentStage >= stages.size()) completed = true;
  }

  public boolean isCompleted() { return completed; }
  public void setCompleted(boolean completed) { this.completed = completed; }
}
