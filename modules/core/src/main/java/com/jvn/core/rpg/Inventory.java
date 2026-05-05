package com.jvn.core.rpg;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class Inventory implements Serializable {
  private int slots;
  private final Map<String, Integer> itemCounts = new HashMap<>();

  public int getSlots() { return slots; }
  public void setSlots(int slots) { this.slots = Math.max(0, slots); }

  public Map<String, Integer> getItemCounts() { return Collections.unmodifiableMap(itemCounts); }

  public int getCount(String itemId) {
    if (itemId == null) return 0;
    return itemCounts.getOrDefault(itemId, 0);
  }

  public boolean add(String itemId, int count) {
    if (itemId == null || itemId.isBlank() || count <= 0) return false;
    boolean hasItem = itemCounts.containsKey(itemId);
    if (slots > 0 && !hasItem && itemCounts.size() >= slots) return false;
    itemCounts.put(itemId, getCount(itemId) + count);
    return true;
  }

  public boolean remove(String itemId, int count) {
    if (itemId == null || itemId.isBlank() || count <= 0) return false;
    int existing = getCount(itemId);
    if (existing < count) return false;
    int remaining = existing - count;
    if (remaining <= 0) itemCounts.remove(itemId);
    else itemCounts.put(itemId, remaining);
    return true;
  }
}
