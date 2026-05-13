package com.jvn.core.menu.gallery;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.jvn.core.vn.VnPersistentStore;

/**
 * Loads and manages music-room track entries from {@code music-room.properties}.
 *
 * <h2>File format</h2>
 * <pre>
 * track.ids=main_theme,battle,credits
 *
 * track.main_theme.audio=assets/audio/bgm/main_theme.ogg
 * track.main_theme.title=Main Theme
 * track.main_theme.artist=Composer Name
 * track.main_theme.category=BGM
 * track.main_theme.order=0
 * </pre>
 */
public final class MusicRoomRegistry {
  private static final String DEFAULT_FILE = "config/gallery/music-room.properties";

  private final List<MusicRoomEntry> entries;
  private final Map<String, List<MusicRoomEntry>> byCategory;

  private MusicRoomRegistry(List<MusicRoomEntry> entries) {
    this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
    Map<String, List<MusicRoomEntry>> map = new LinkedHashMap<>();
    for (MusicRoomEntry e : this.entries) {
      map.computeIfAbsent(e.category(), k -> new ArrayList<>()).add(e);
    }
    this.byCategory = Collections.unmodifiableMap(map);
  }

  public List<MusicRoomEntry> entries() { return entries; }
  public Map<String, List<MusicRoomEntry>> byCategory() { return byCategory; }
  public boolean isEmpty() { return entries.isEmpty(); }

  public boolean isUnlocked(MusicRoomEntry entry, VnPersistentStore store) {
    if (entry == null || store == null) return false;
    Object val = store.get(entry.unlockFlag());
    if (val instanceof Boolean b) return b;
    if (val instanceof String s) return "true".equalsIgnoreCase(s.trim());
    return val != null;
  }

  public void unlock(MusicRoomEntry entry, VnPersistentStore store) {
    if (entry == null || store == null) return;
    store.put(entry.unlockFlag(), Boolean.TRUE);
  }

  public MusicRoomEntry findByAudioPath(String audioPath) {
    if (audioPath == null || audioPath.isBlank()) return null;
    String normalized = audioPath.trim().replace('\\', '/');
    for (MusicRoomEntry e : entries) {
      if (normalized.equals(e.audioPath().replace('\\', '/'))) return e;
    }
    return null;
  }

  // --- Loading ---

  public static MusicRoomRegistry load() {
    return load(DEFAULT_FILE);
  }

  public static MusicRoomRegistry load(String path) {
    Path file = Path.of(path);
    if (Files.exists(file)) {
      try (InputStream is = Files.newInputStream(file)) {
        return parse(is);
      } catch (IOException e) {
        return new MusicRoomRegistry(List.of());
      }
    }
    try (InputStream is = MusicRoomRegistry.class.getClassLoader().getResourceAsStream(path)) {
      if (is != null) return parse(is);
    } catch (IOException ignored) {
            // reason: I/O failure on best-effort save/load; in-memory state remains valid
        }
    return new MusicRoomRegistry(List.of());
  }

  public static MusicRoomRegistry parse(InputStream is) throws IOException {
    Map<String, String> props = new LinkedHashMap<>();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        line = line.trim();
        if (line.isEmpty() || line.startsWith("#")) continue;
        int eq = line.indexOf('=');
        if (eq <= 0) continue;
        props.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
      }
    }
    return parseProperties(props);
  }

  public static MusicRoomRegistry parseProperties(Map<String, String> props) {
    String idsRaw = props.getOrDefault("track.ids", "");
    String[] ids = idsRaw.isEmpty() ? new String[0] : idsRaw.split("[,;\\s]+");

    List<MusicRoomEntry> entries = new ArrayList<>();
    for (String id : ids) {
      id = id.trim();
      if (id.isEmpty()) continue;
      String prefix = "track." + id + ".";
      String audio = props.getOrDefault(prefix + "audio", "");
      if (audio.isBlank()) continue;
      String title = props.getOrDefault(prefix + "title", id);
      String artist = props.getOrDefault(prefix + "artist", "");
      String category = props.getOrDefault(prefix + "category", "BGM");
      int order = parseIntSafe(props.getOrDefault(prefix + "order", "0"), 0);
      String unlockFlag = props.getOrDefault(prefix + "unlockFlag", null);
      entries.add(new MusicRoomEntry(id, audio, title, artist, category, order, unlockFlag));
    }
    entries.sort(Comparator.comparingInt(MusicRoomEntry::order).thenComparing(MusicRoomEntry::id));
    return new MusicRoomRegistry(entries);
  }

  private static int parseIntSafe(String s, int fallback) {
    if (s == null || s.isBlank()) return fallback;
    try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return fallback; }
  }
}
