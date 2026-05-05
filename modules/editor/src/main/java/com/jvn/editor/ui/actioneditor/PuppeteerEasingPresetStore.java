package com.jvn.editor.ui.actioneditor;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jvn.core.animation.EasingSpec;

final class PuppeteerEasingPresetStore {
  static final String CONFIG_PATH = "config/puppeteer/easing-presets.properties";

  private static final Pattern PRESET_KEY =
      Pattern.compile("^preset\\.(\\d+)\\.(id|name|spec)$");
  private static final DecimalFormat INDEX_FORMAT =
      new DecimalFormat("000", DecimalFormatSymbols.getInstance(Locale.ROOT));

  record Preset(String id, String name, EasingSpec spec) {
    Preset {
      id = sanitizeId(id);
      name = normalizeName(name);
      spec = spec != null ? spec : EasingSpec.parseOrDefault("linear");
      if (id.isBlank()) id = sanitizeId(name);
      if (id.isBlank()) throw new IllegalArgumentException("Preset id cannot be blank");
      if (name.isBlank()) throw new IllegalArgumentException("Preset name cannot be blank");
    }

    @Override
    public String toString() {
      return name;
    }
  }

  private PuppeteerEasingPresetStore() {}

  static Path resolveProjectFile(File projectRoot) {
    return projectRoot == null ? null : projectRoot.toPath().resolve(CONFIG_PATH);
  }

  static List<Preset> load(File projectRoot) {
    if (projectRoot == null) return List.of();
    return load(resolveProjectFile(projectRoot));
  }

  static List<Preset> load(Path file) {
    if (file == null || !Files.isRegularFile(file)) return List.of();
    Properties properties = new Properties();
    try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
      properties.load(reader);
    } catch (IOException ignored) {
      return List.of();
    }

    Map<Integer, String> ids = new LinkedHashMap<>();
    Map<Integer, String> names = new LinkedHashMap<>();
    Map<Integer, String> specs = new LinkedHashMap<>();
    for (String key : properties.stringPropertyNames()) {
      Matcher matcher = PRESET_KEY.matcher(key);
      if (!matcher.matches()) continue;
      int index = Integer.parseInt(matcher.group(1));
      String field = matcher.group(2);
      String value = properties.getProperty(key, "").trim();
      switch (field) {
        case "id" -> ids.put(index, value);
        case "name" -> names.put(index, value);
        case "spec" -> specs.put(index, value);
        default -> {
        }
      }
    }

    List<Integer> indexes = new ArrayList<>(names.keySet());
    indexes.sort(Comparator.naturalOrder());
    List<Preset> presets = new ArrayList<>();
    for (Integer index : indexes) {
      String name = names.get(index);
      String specValue = specs.get(index);
      if (name == null || name.isBlank() || specValue == null || specValue.isBlank()) continue;
      EasingSpec spec = EasingSpec.tryParse(specValue);
      if (spec == null) continue;
      String id = ids.get(index);
      if (id == null || id.isBlank()) {
        id = uniqueId(name, presets, null);
      }
      presets.add(new Preset(id, name, spec));
    }
    return List.copyOf(presets);
  }

  static void save(File projectRoot, Collection<Preset> presets) throws IOException {
    if (projectRoot == null) throw new IOException("Project root is missing");
    save(resolveProjectFile(projectRoot), presets);
  }

  static void save(Path file, Collection<Preset> presets) throws IOException {
    Objects.requireNonNull(file, "file");
    List<Preset> ordered = presets == null ? List.of() : List.copyOf(presets);
    Path parent = file.getParent();
    if (parent != null) Files.createDirectories(parent);
    try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
      writer.write("# Puppeteer easing presets");
      writer.newLine();
      writer.write("# Saved by the Puppeteer keyframe editor");
      writer.newLine();
      writer.newLine();
      int index = 1;
      for (Preset preset : ordered) {
        String prefix = "preset." + INDEX_FORMAT.format(index++);
        writer.write(prefix + ".id=" + escapeProperty(preset.id()));
        writer.newLine();
        writer.write(prefix + ".name=" + escapeProperty(preset.name()));
        writer.newLine();
        writer.write(prefix + ".spec=" + escapeProperty(preset.spec().toDslString()));
        writer.newLine();
        writer.newLine();
      }
    }
  }

  static String normalizeName(String raw) {
    return raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
  }

  static String uniqueId(String preferredName, Collection<Preset> presets, String reservedId) {
    String base = sanitizeId(preferredName);
    if (base.isBlank()) base = "preset";
    String candidate = base;
    int suffix = 2;
    while (idExists(candidate, presets, reservedId)) {
      candidate = base + "_" + suffix++;
    }
    return candidate;
  }

  private static boolean idExists(String candidate, Collection<Preset> presets, String reservedId) {
    if (candidate == null || candidate.isBlank()) return true;
    for (Preset preset : presets) {
      if (preset == null) continue;
      if (candidate.equals(preset.id()) && !candidate.equals(reservedId)) return true;
    }
    return false;
  }

  private static String sanitizeId(String raw) {
    if (raw == null) return "";
    String normalized = raw.trim().toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9]+", "_")
        .replaceAll("_+", "_")
        .replaceAll("^_+|_+$", "");
    return normalized;
  }

  private static String escapeProperty(String value) {
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < value.length(); i++) {
      char ch = value.charAt(i);
      switch (ch) {
        case '\\' -> out.append("\\\\");
        case '\t' -> out.append("\\t");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\f' -> out.append("\\f");
        case '=' -> out.append("\\=");
        case ':' -> out.append("\\:");
        case '#' -> out.append("\\#");
        case '!' -> out.append("\\!");
        default -> {
          if ((i == 0 || i == value.length() - 1) && ch == ' ') out.append("\\ ");
          else out.append(ch);
        }
      }
    }
    return out.toString();
  }
}
