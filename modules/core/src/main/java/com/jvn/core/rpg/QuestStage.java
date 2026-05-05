package com.jvn.core.rpg;

import java.io.Serializable;

public class QuestStage implements Serializable {
  private String id;
  private String description;
  private boolean completed;

  public QuestStage() {}
  public QuestStage(String id, String description) { this.id = id; this.description = description; }

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getDescription() { return description; }
  public void setDescription(String description) { this.description = description; }
  public boolean isCompleted() { return completed; }
  public void setCompleted(boolean completed) { this.completed = completed; }
}
