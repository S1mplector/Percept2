package com.jvn.core.vn.ui;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.assets.AssetType;

/**
 * Loads optional runtime cursor configuration from VN settings files.
 *
 * <p>Supported locations (first match wins):
 * <ul>
 *   <li>configured by {@code settingsFile} in {@code jvn.project}</li>
 *   <li>{@code config/settings/vn.settings}</li>
 *   <li>{@code config/vn.settings}</li>
 *   <li>{@code vn.settings}</li>
 * </ul>
 *
 * <p>Supported keys:
 * <ul>
 *   <li>{@code cursorAsset} (preferred), {@code cursor.asset}, {@code cursorPath}, {@code cursor.path}</li>
 *   <li>{@code cursorHotspotX} / {@code cursorHotspotY}
 *       (aliases: {@code cursor.hotspotX}, {@code cursor.hotspotY})</li>
 * </ul>
 */
public final class VnCursorConfigLoader {
  private static final String[] DEFAULT_SETTINGS_PATHS = new String[] {
      "config/settings/vn.settings",
      "config/vn.settings",
      "vn.settings"
  };

  public record VnCursorConfig(String assetPath, double hotspotX, double hotspotY) {
    public VnCursorConfig {
      assetPath = normalize(assetPath);
      hotspotX = finiteOrZero(hotspotX);
      hotspotY = finiteOrZero(hotspotY);
    }
  }

  public record LoadResult(VnCursorConfig config, List<String> diagnostics) {
    public LoadResult {
      diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
  }

  private VnCursorConfigLoader() {}

  public static VnCursorConfig loadFromAssets() {
    return loadFromAssetsWithDiagnostics().config();
  }

  public static VnCursorConfig loadFromProjectRoot(File projectRoot) {
    return loadFromProjectRootWithDiagnostics(projectRoot).config();
  }

  public static LoadResult loadFromAssetsWithDiagnostics() {
    return loadFromAssetsWithDiagnostics(new AssetCatalog());
  }

  public static LoadResult loadFromAssetsWithDiagnostics(AssetCatalog assets) {
    List<String> diagnostics = new ArrayList<>();
    Properties props = loadPropertiesFromAssetsInternal(assets, diagnostics);
    VnCursorConfig cfg = parseConfig(props, diagnostics);
    return new LoadResult(cfg, diagnostics);
  }

  public static LoadResult loadFromProjectRootWithDiagnostics(File projectRoot) {
    List<String> diagnostics = new ArrayList<>();
    Properties props = loadPropertiesFromProjectRootInternal(projectRoot, diagnostics);
    VnCursorConfig cfg = parseConfig(props, diagnostics);
    return new LoadResult(cfg, diagnostics);
  }

  private static Properties loadPropertiesFromAssetsInternal(AssetCatalog assets, List<String> diagnostics) {
    if (assets == null) return null;
    for (String path : candidatePathsFromAssets(assets, diagnostics)) {
      try (InputStream in = assets.open(AssetType.SCRIPT, path)) {
        if (in == null) continue;
        Properties p = new Properties();
        p.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
        return p;
      } catch (Exception ex) {
        if (diagnostics != null) {
          diagnostics.add("Failed to parse settings at '" + path + "': " + simplify(ex));
        }
      }
    }
    return null;
  }

  private static Properties loadPropertiesFromProjectRootInternal(File projectRoot, List<String> diagnostics) {
    if (projectRoot == null) return null;
    for (String rel : candidatePathsFromProjectRoot(projectRoot, diagnostics)) {
      File file = new File(projectRoot, rel);
      if (!file.exists() || !file.isFile()) continue;
      try (FileInputStream fis = new FileInputStream(file)) {
        Properties p = new Properties();
        p.load(new java.io.InputStreamReader(fis, StandardCharsets.UTF_8));
        return p;
      } catch (Exception ex) {
        if (diagnostics != null) {
          diagnostics.add("Failed to parse settings file '" + file.getPath() + "': " + simplify(ex));
        }
      }
    }
    return null;
  }

  private static List<String> candidatePathsFromAssets(AssetCatalog assets, List<String> diagnostics) {
    Set<String> out = new LinkedHashSet<>();
    String configured = readManifestSettingsPath(assets, diagnostics);
    if (configured != null) out.add(configured);
    for (String path : DEFAULT_SETTINGS_PATHS) out.add(path);
    return new ArrayList<>(out);
  }

  private static List<String> candidatePathsFromProjectRoot(File projectRoot, List<String> diagnostics) {
    Set<String> out = new LinkedHashSet<>();
    String configured = readManifestSettingsPath(projectRoot, diagnostics);
    if (configured != null) out.add(configured);
    for (String path : DEFAULT_SETTINGS_PATHS) out.add(path);
    return new ArrayList<>(out);
  }

  private static String readManifestSettingsPath(AssetCatalog assets, List<String> diagnostics) {
    if (assets == null) return null;
    try (InputStream in = assets.open(AssetType.SCRIPT, "jvn.project")) {
      if (in == null) return null;
      Properties p = new Properties();
      p.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
      return normalize(p.getProperty("settingsFile"));
    } catch (Exception ex) {
      if (diagnostics != null) {
        diagnostics.add("Failed to read jvn.project from assets for settingsFile: " + simplify(ex));
      }
      return null;
    }
  }

  private static String readManifestSettingsPath(File projectRoot, List<String> diagnostics) {
    File manifest = new File(projectRoot, "jvn.project");
    if (!manifest.exists() || !manifest.isFile()) return null;
    try (FileInputStream fis = new FileInputStream(manifest)) {
      Properties p = new Properties();
      p.load(new java.io.InputStreamReader(fis, StandardCharsets.UTF_8));
      return normalize(p.getProperty("settingsFile"));
    } catch (Exception ex) {
      if (diagnostics != null) {
        diagnostics.add("Failed to read jvn.project for settingsFile: " + simplify(ex));
      }
      return null;
    }
  }

  private static VnCursorConfig parseConfig(Properties props, List<String> diagnostics) {
    if (props == null) return null;

    String assetPath = firstNonBlank(
        props.getProperty("cursorAsset"),
        props.getProperty("cursor.asset"),
        props.getProperty("cursorPath"),
        props.getProperty("cursor.path"));
    if (assetPath == null) return null;

    double hotspotX = parseDouble(props, diagnostics, 0.0, "cursorHotspotX", "cursor.hotspotX");
    double hotspotY = parseDouble(props, diagnostics, 0.0, "cursorHotspotY", "cursor.hotspotY");

    return new VnCursorConfig(assetPath, hotspotX, hotspotY);
  }

  private static double parseDouble(
      Properties props,
      List<String> diagnostics,
      double fallback,
      String key,
      String alias
  ) {
    String raw = firstNonBlank(
        props.getProperty(key),
        alias == null ? null : props.getProperty(alias));
    if (raw == null) return fallback;
    try {
      double parsed = Double.parseDouble(raw.trim());
      return Double.isFinite(parsed) ? parsed : fallback;
    } catch (Exception ex) {
      if (diagnostics != null) {
        diagnostics.add("Invalid number for '" + key + "': '" + raw + "'; using " + fallback);
      }
      return fallback;
    }
  }

  private static String firstNonBlank(String... values) {
    if (values == null) return null;
    for (String value : values) {
      String normalized = normalize(value);
      if (normalized != null) return normalized;
    }
    return null;
  }

  private static String normalize(String value) {
    if (value == null) return null;
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static double finiteOrZero(double value) {
    return Double.isFinite(value) ? value : 0.0;
  }

  private static String simplify(Exception ex) {
    if (ex == null) return "unknown";
    String msg = ex.getMessage();
    return (msg == null || msg.isBlank()) ? ex.getClass().getSimpleName() : msg;
  }
}
