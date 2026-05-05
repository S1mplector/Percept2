package com.jvn.scripting.jes.runtime;

import java.util.HashMap;
import java.util.Map;

public class Inventory {
  private int slots;
  private final Map<String,Integer> itemCounts = new HashMap<>();

  public int getSlots() { return slots; }

  public void setSlots(int slots) { this.slots = slots; }

  public Map<String,Integer> getItemCounts() { return itemCounts; }

  public int getCount(String itemId) {
    if (itemId == null) return 0;
    Integer c = itemCounts.get(itemId);
    return c == null ? 0 : c;
  }

  public void add(String itemId, int count) {
    if (itemId == null || itemId.isBlank()) return;
    if (count <= 0) return;
    int existing = getCount(itemId);
    int total = existing + count;
    itemCounts.put(itemId, total);
  }

  public boolean addBounded(String itemId, int count, int maxPerItem) {
    if (itemId == null || itemId.isBlank()) return false;
    if (count <= 0) return false;
    boolean hasItem = itemCounts.containsKey(itemId);
    if (slots > 0 && !hasItem && itemCounts.size() >= slots) return false;
    int existing = getCount(itemId);
    long totalLong = (long) existing + (long) count;
    int total = (int) totalLong;
    if (maxPerItem > 0 && total > maxPerItem) {
      total = maxPerItem;
    }
    if (total <= 0 || total == existing) return false;
    itemCounts.put(itemId, total);
    return true;
  }

  public boolean remove(String itemId, int count) {
    if (itemId == null || itemId.isBlank()) return false;
    if (count <= 0) return false;
    int existing = getCount(itemId);
    if (existing < count) return false;
    int remaining = existing - count;
    if (remaining <= 0) itemCounts.remove(itemId);
    else itemCounts.put(itemId, remaining);
    return true;
  }
}
