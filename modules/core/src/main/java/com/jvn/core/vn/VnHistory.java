package com.jvn.core.vn;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.List;

/**
 * Manages dialogue history/backlog for review
 */
public class VnHistory {
  private final ArrayDeque<HistoryEntry> entries = new ArrayDeque<>();
  private final int maxEntries;

  public VnHistory() {
    this(200); // Default to 200 entries
  }

  public VnHistory(int maxEntries) {
    this.maxEntries = maxEntries;
  }

  public void addEntry(String speaker, String text) {
    addEntry(speaker, text, null);
  }

  public void addEntry(String speaker, String text, String speakerColor) {
    entries.addLast(new HistoryEntry(speaker, text, speakerColor, System.currentTimeMillis()));
    
    // Trim old entries if we exceed max (O(1) with ArrayDeque)
    while (entries.size() > maxEntries) {
      entries.removeFirst();
    }
  }

  public List<HistoryEntry> getEntries() {
    return List.copyOf(entries);
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
    private final String speakerColor;
    private final long timestamp;

    public HistoryEntry(String speaker, String text, long timestamp) {
      this(speaker, text, null, timestamp);
    }

    public HistoryEntry(String speaker, String text, String speakerColor, long timestamp) {
      this.speaker = speaker;
      this.text = text;
      this.speakerColor = speakerColor;
      this.timestamp = timestamp;
    }

    public String getSpeaker() { return speaker; }
    public String getText() { return text; }
    public String getSpeakerColor() { return speakerColor; }
    public long getTimestamp() { return timestamp; }
  }
}
