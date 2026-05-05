package com.jvn.core.vn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manages dialogue history/backlog for review
 */
public class VnHistory {
  private final List<HistoryEntry> entries = new ArrayList<>();
  private final int maxEntries;

  public VnHistory() {
    this(200); // Default to 200 entries
  }

  public VnHistory(int maxEntries) {
    this.maxEntries = maxEntries;
  }

  public void addEntry(String speaker, String text) {
    entries.add(new HistoryEntry(speaker, text, System.currentTimeMillis()));
    
    // Trim old entries if we exceed max
    while (entries.size() > maxEntries) {
      entries.remove(0);
    }
  }

  public List<HistoryEntry> getEntries() {
    return Collections.unmodifiableList(entries);
  }

  public void clear() {
    entries.clear();
  }

  public int size() {
    return entries.size();
  }

  public static class HistoryEntry {
    private final String speaker;
    private final String text;
    private final long timestamp;

    public HistoryEntry(String speaker, String text, long timestamp) {
      this.speaker = speaker;
      this.text = text;
      this.timestamp = timestamp;
    }

    public String getSpeaker() { return speaker; }
    public String getText() { return text; }
    public long getTimestamp() { return timestamp; }
  }
}
