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
 * Loads and manages gallery/CG entries from a {@code gallery.properties} file.
 *
 * <h2>File format</h2>
 * <pre>
 * # gallery.properties
 * entry.ids=sunset_kiss,classroom_fight,ending_a
 *
 * entry.sunset_kiss.image=assets/cg/sunset_kiss.png
 * entry.sunset_kiss.category=Chapter 3
 * entry.sunset_kiss.order=1
 *
 * entry.classroom_fight.image=assets/cg/classroom_fight.png
 * entry.classroom_fight.category=Chapter 1
 * entry.classroom_fight.order=0
 *
 * entry.ending_a.image=assets/cg/ending_a.png
 * entry.ending_a.category=Endings
 * entry.ending_a.order=0
 * </pre>
 */
public final class GalleryRegistry {
  private static final String DEFAULT_FILE = "config/gallery/gallery.properties";

  private final List<GalleryEntry> entries;
  private final Map<String, List<GalleryEntry>> byCategory;

  private GalleryRegistry(List<GalleryEntry> entries) {
    this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
    Map<String, List<GalleryEntry>> map = new LinkedHashMap<>();
    for (GalleryEntry e : this.entries) {
      map.computeIfAbsent(e.category(), k -> new ArrayList<>()).add(e);
    }
    this.byCategory = Collections.unmodifiableMap(map);
  }

  public List<GalleryEntry> entries() { return entries; }
  public Map<String, List<GalleryEntry>> byCategory() { return byCategory; }
  public boolean isEmpty() { return entries.isEmpty(); }

  public boolean isUnlocked(GalleryEntry entry, VnPersistentStore store) {
    if (entry == null || store == null) return false;
    Object val = store.get(entry.unlockFlag());
    if (val instanceof Boolean b) return b;
    if (val instanceof String s) return "true".equalsIgnoreCase(s.trim());
    return val != null;
  }

  public void unlock(GalleryEntry entry, VnPersistentStore store) {
    if (entry == null || store == null) return;
    store.put(entry.unlockFlag(), Boolean.TRUE);
  }

  public int unlockedCount(VnPersistentStore store) {
    int count = 0;
    for (GalleryEntry e : entries) {
      if (isUnlocked(e, store)) count++;
    }
    return count;
  }

  /** Find a gallery entry whose image path matches a given background asset path. */
  public GalleryEntry findByImagePath(String imagePath) {
    if (imagePath == null || imagePath.isBlank()) return null;
    String normalized = imagePath.trim().replace('\\', '/');
    for (GalleryEntry e : entries) {
      if (normalized.equals(e.imagePath().replace('\\', '/'))) return e;
    }
    return null;
  }

  // --- Loading ---

  public static GalleryRegistry load() {
    return load(DEFAULT_FILE);
  }

  public static GalleryRegistry load(String path) {
    Path file = Path.of(path);
    if (Files.exists(file)) {
      try (InputStream is = Files.newInputStream(file)) {
        return parse(is);
      } catch (IOException e) {
        return new GalleryRegistry(List.of());
      }
    }
    // Try classpath
    try (InputStream is = GalleryRegistry.class.getClassLoader().getResourceAsStream(path)) {
      if (is != null) return parse(is);
    } catch (IOException ignored) {
            // reason: I/O failure on best-effort save/load; in-memory state remains valid
        }
    return new GalleryRegistry(List.of());
  }

  public static GalleryRegistry parse(InputStream is) throws IOException {
    Map<String, String> props = new LinkedHashMap<>();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        line = line.trim();
        if (line.isEmpty() || line.startsWith("#")) continue;
        int eq = line.indexOf('=');
        if (eq <= 0) continue;
        String key = line.substring(0, eq).trim();
        String val = line.substring(eq + 1).trim();
        if (!key.isEmpty()) props.put(key, val);
      }
    }
    return parseProperties(props);
  }

  public static GalleryRegistry parseProperties(Map<String, String> props) {
    String idsRaw = props.getOrDefault("entry.ids", "");
    String[] ids = idsRaw.isEmpty() ? new String[0] : idsRaw.split("[,;\\s]+");

    List<GalleryEntry> entries = new ArrayList<>();
    for (String id : ids) {
      id = id.trim();
      if (id.isEmpty()) continue;
      String prefix = "entry." + id + ".";
      String image = props.getOrDefault(prefix + "image", "");
      if (image.isBlank()) continue;
      String category = props.getOrDefault(prefix + "category", "Default");
      int order = parseIntSafe(props.getOrDefault(prefix + "order", "0"), 0);
      String unlockFlag = props.getOrDefault(prefix + "unlockFlag", null);
      entries.add(new GalleryEntry(id, image, category, order, unlockFlag));
    }
    entries.sort(Comparator.comparingInt(GalleryEntry::order).thenComparing(GalleryEntry::id));
    return new GalleryRegistry(entries);
  }

  private static int parseIntSafe(String s, int fallback) {
    if (s == null || s.isBlank()) return fallback;
    try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return fallback; }
  }
}
