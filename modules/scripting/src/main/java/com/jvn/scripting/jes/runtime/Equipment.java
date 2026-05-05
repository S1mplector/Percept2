package com.jvn.scripting.jes.runtime;

import java.util.HashMap;
import java.util.Map;

public class Equipment {
  private final Map<String,String> slots = new HashMap<>();

  public Map<String,String> getSlots() { return slots; }

  public String get(String slot) {
    if (slot == null) return null;
    return slots.get(slot);
  }

  public void set(String slot, String itemId) {
    if (slot == null || slot.isBlank()) return;
    if (itemId == null || itemId.isBlank()) {
      slots.remove(slot);
    } else {
      slots.put(slot, itemId);
    }
  }
}
