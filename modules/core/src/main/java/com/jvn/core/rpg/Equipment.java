package com.jvn.core.rpg;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class Equipment implements Serializable {
  private final Map<String, String> slots = new HashMap<>();

  public Map<String, String> getSlots() { return slots; }

  public void set(String slot, String itemId) {
    if (slot == null || slot.isBlank()) return;
    if (itemId == null || itemId.isBlank()) slots.remove(slot);
    else slots.put(slot, itemId);
  }

  public String get(String slot) { return slots.get(slot); }
}
