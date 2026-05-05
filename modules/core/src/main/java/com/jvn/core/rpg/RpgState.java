package com.jvn.core.rpg;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Aggregate RPG state for saves.
 */
public class RpgState implements Serializable {
  private final Map<String, RpgStats> actors = new HashMap<>();
  private final Map<String, Inventory> inventories = new HashMap<>();
  private final Map<String, Equipment> equipment = new HashMap<>();
  private final QuestLog questLog = new QuestLog();

  public Map<String, RpgStats> getActors() { return actors; }
  public Map<String, Inventory> getInventories() { return inventories; }
  public Map<String, Equipment> getEquipment() { return equipment; }
  public QuestLog getQuestLog() { return questLog; }

  public RpgStats actor(String id) { return actors.computeIfAbsent(id, k -> new RpgStats()); }
  public Inventory inventory(String id) { return inventories.computeIfAbsent(id, k -> new Inventory()); }
  public Equipment equip(String id) { return equipment.computeIfAbsent(id, k -> new Equipment()); }
}
