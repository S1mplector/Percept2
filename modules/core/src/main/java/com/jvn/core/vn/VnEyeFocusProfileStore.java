package com.jvn.core.vn;

import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.assets.AssetType;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Loads and saves Puppeteer/runtime eye-focus rig profiles.
 */
public final class VnEyeFocusProfileStore {
  public static final String CONFIG_PATH = "config/puppeteer/eye-focus.properties";
  public static final String ASSET_CONFIG_PATH = "puppeteer/eye-focus.properties";

  private VnEyeFocusProfileStore() {}

  public static List<VnEyeFocusProfile> load(File projectRoot) {
    if (projectRoot == null) return List.of();
    Path file = projectRoot.toPath().resolve(CONFIG_PATH);
    if (!Files.isRegularFile(file)) return List.of();
    Properties props = new Properties();
    try (InputStream in = Files.newInputStream(file)) {
      props.load(in);
      return parse(props);
    } catch (IOException ex) {
      return List.of();
    }
  }

  public static void save(File projectRoot, Iterable<VnEyeFocusProfile> profiles) {
    if (projectRoot == null || profiles == null) return;
    Path file = projectRoot.toPath().resolve(CONFIG_PATH);
    Properties props = write(profiles);
    try {
      Files.createDirectories(file.getParent());
      try (OutputStream out = Files.newOutputStream(file)) {
        props.store(out, "JVN Puppeteer eye focus profiles");
      }
    } catch (IOException ex) {
      // Persistence is best-effort; editor callers should remain usable if the file is locked.
    }
  }

  public static Map<String, VnEyeFocusProfile> loadFromAssets(AssetCatalog assets) {
    if (assets == null) return Map.of();
    List<String> candidates = List.of(
        ASSET_CONFIG_PATH,
        CONFIG_PATH
    );
    for (String candidate : candidates) {
      Properties props = loadAssetProperties(assets, AssetType.CONFIG, candidate);
      if (props == null) {
        props = loadAssetProperties(assets, AssetType.OTHER, candidate);
      }
      if (props == null) continue;
      return byKey(parse(props));
    }
    return Map.of();
  }

  public static Properties write(Iterable<VnEyeFocusProfile> profiles) {
    Properties props = new Properties();
    int index = 0;
    for (VnEyeFocusProfile profile : profiles) {
      if (profile == null || profile.characterId().isBlank()) continue;
      String prefix = "profile." + index + ".";
      props.setProperty(prefix + "character", profile.characterId());
      props.setProperty(prefix + "expression", profile.expression());
      if (!profile.sourceAnchor().isBlank()) {
        props.setProperty(prefix + "sourceAnchor", profile.sourceAnchor());
      }
      props.setProperty(prefix + "sourceX", Double.toString(profile.sourceX()));
      props.setProperty(prefix + "sourceY", Double.toString(profile.sourceY()));
      props.setProperty(prefix + "deadZone", Double.toString(profile.deadZone()));
      props.setProperty(prefix + "maxNudge", Double.toString(profile.maxNudgePx()));
      props.setProperty(prefix + "strength", Double.toString(profile.strength()));
      for (int keypad = 1; keypad <= 9; keypad++) {
        String layer = profile.layerIdFor(keypad);
        if (layer != null && !layer.isBlank()) {
          props.setProperty(prefix + "layer." + keypad, layer);
        }
      }
      index++;
    }
    props.setProperty("profile.count", Integer.toString(index));
    return props;
  }

  public static List<VnEyeFocusProfile> parse(Properties props) {
    if (props == null) return List.of();
    int count = parseInt(props.getProperty("profile.count"), -1);
    if (count < 0) {
      count = inferCount(props);
    }
    List<VnEyeFocusProfile> profiles = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      VnEyeFocusProfile profile = parseProfile(props, "profile." + i + ".");
      if (profile != null) {
        profiles.add(profile);
      }
    }
    return List.copyOf(profiles);
  }

  public static Map<String, VnEyeFocusProfile> byKey(Iterable<VnEyeFocusProfile> profiles) {
    Map<String, VnEyeFocusProfile> map = new LinkedHashMap<>();
    if (profiles == null) return Map.of();
    for (VnEyeFocusProfile profile : profiles) {
      if (profile != null && !profile.characterId().isBlank()) {
        map.put(profile.key(), profile);
      }
    }
    return Map.copyOf(map);
  }

  private static VnEyeFocusProfile parseProfile(Properties props, String prefix) {
    String character = clean(props.getProperty(prefix + "character"));
    if (character.isBlank()) return null;
    String expression = clean(props.getProperty(prefix + "expression"));
    String sourceAnchor = clean(props.getProperty(prefix + "sourceAnchor"));
    double sourceX = parseDouble(props.getProperty(prefix + "sourceX"), 0.5);
    double sourceY = parseDouble(props.getProperty(prefix + "sourceY"), 0.26);
    double deadZone = parseDouble(props.getProperty(prefix + "deadZone"), 0.12);
    double maxNudge = parseDouble(props.getProperty(prefix + "maxNudge"), 3.0);
    double strength = parseDouble(props.getProperty(prefix + "strength"), 1.0);
    Map<Integer, String> layers = new LinkedHashMap<>();
    for (int keypad = 1; keypad <= 9; keypad++) {
      String layer = clean(props.getProperty(prefix + "layer." + keypad));
      if (!layer.isBlank()) {
        layers.put(keypad, layer);
      }
    }
    return new VnEyeFocusProfile(
        character,
        expression.isBlank() ? "neutral" : expression,
        sourceAnchor,
        sourceX,
        sourceY,
        deadZone,
        maxNudge,
        strength,
        layers);
  }

  private static Properties loadAssetProperties(AssetCatalog assets, AssetType type, String path) {
    if (!assets.exists(type, path)) return null;
    Properties props = new Properties();
    try (InputStream in = assets.open(type, path)) {
      props.load(in);
      return props;
    } catch (IOException ex) {
      return null;
    }
  }

  private static int inferCount(Properties props) {
    int max = -1;
    for (String key : props.stringPropertyNames()) {
      if (!key.startsWith("profile.")) continue;
      int start = "profile.".length();
      int dot = key.indexOf('.', start);
      if (dot <= start) continue;
      max = Math.max(max, parseInt(key.substring(start, dot), -1));
    }
    return max + 1;
  }

  private static String clean(String raw) {
    return raw == null ? "" : raw.trim();
  }

  private static int parseInt(String raw, int fallback) {
    try {
      return Integer.parseInt(clean(raw));
    } catch (NumberFormatException ex) {
      return fallback;
    }
  }

  private static double parseDouble(String raw, double fallback) {
    try {
      double value = Double.parseDouble(clean(raw));
      return Double.isFinite(value) ? value : fallback;
    } catch (NumberFormatException ex) {
      return fallback;
    }
  }
}
