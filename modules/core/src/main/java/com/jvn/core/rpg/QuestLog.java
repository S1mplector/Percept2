package com.jvn.core.rpg;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class QuestLog implements Serializable {
  private final Map<String, Quest> quests = new HashMap<>();

  public void add(Quest quest) {
    if (quest != null && quest.getId() != null) quests.put(quest.getId(), quest);
  }

  public Quest get(String id) { return quests.get(id); }

  public Collection<Quest> all() { return quests.values(); }
}
