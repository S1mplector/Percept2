package com.jvn.core.localization;

import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.assets.AssetType;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Global string-table localization backed by {@code .properties} files.
 *
 * <p>Strings are loaded from the classpath under
 * {@code config/locales/<locale>.properties} or
 * {@code game/strings/<locale>.properties}. If the requested locale file is
 * missing, the loader falls back to {@code en.properties}.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * Localization.init("ja", getClass().getClassLoader());
 * String label = Localization.t("menu.start"); // returns localised value or key
 * }</pre>
 *
 * @see LocalizedScriptLoader
 */
public final class Localization {

  /** Preferred classpath/project directories for string tables. */
  private static final String[] BASES = new String[] {
      "config/locales/",
      "game/strings/",
      "strings/"
  };

  /** Currently loaded string properties. */
  private static Properties props = new Properties();

  /** Active locale code (e.g. "en", "ja"). */
  private static String currentLocale = "en";

  /** Non-instantiable utility class. */
  private Localization() {}

  /**
   * Load the string table for the given locale with fallback to English.
   *
   * <p>Resolution order:</p>
   * <ol>
   *   <li>{@code config/locales/<locale>.properties}</li>
   *   <li>{@code game/strings/<locale>.properties}</li>
   *   <li>{@code strings/<locale>.properties}</li>
   *   <li>the same directories for {@code en.properties}</li>
   * </ol>
   *
   * @param locale the target locale code (defaults to "en" if blank)
   * @param loader the classloader used to find the properties resource
   */
  public static void init(String locale, ClassLoader loader) {
    if (locale == null || locale.isBlank()) locale = "en";
    currentLocale = locale;
    props = new Properties();
    AssetCatalog assets = new AssetCatalog();
    for (String path : candidateLocalePaths(locale)) {
      try (InputStream in = open(loader, assets, path)) {
        if (in != null) {
          props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
          return;
        }
      } catch (IOException ignored) {
        // reason: trying next candidate (locale fallback); caller handles missing table gracefully
      }
    }
  }

  /** @return the currently active locale code */
  public static String locale() { return currentLocale; }

  /**
   * Translate a key to its localised string value.
   *
   * @param key the string-table key
   * @return the localised value, or the key itself if no mapping exists
   */
  public static String t(String key) {
    if (key == null) return "";
    return props.getProperty(key, key);
  }

  /**
   * Translate literal source text by its generated source-text key.
   *
   * <p>This supports Ren'Py-style catalog updates for existing VNS dialogue,
   * choices, and literal menu labels without requiring authors to rewrite every
   * line as an explicit {@code i18n:...} lookup.</p>
   *
   * @param sourceText literal source text from a script or UI file
   * @return translated text, or {@code sourceText} when the catalog has no entry
   */
  public static String tSource(String sourceText) {
    if (sourceText == null) return "";
    String key = sourceKey(sourceText);
    return props.getProperty(key, sourceText);
  }

  /**
   * Resolve either an explicit {@code i18n:key} reference or a literal string.
   *
   * @param raw configured text value
   * @return translated text, or the original literal when no source catalog
   *         entry exists
   */
  public static String translateText(String raw) {
    if (raw == null) return "";
    String value = raw.trim();
    if (value.startsWith("i18n:")) {
      String key = value.substring("i18n:".length()).trim();
      if (!key.isEmpty()) return t(key);
    }
    return tSource(raw);
  }

  /**
   * Stable generated key for literal source text.
   */
  public static String sourceKey(String sourceText) {
    String normalized = normalizeSourceText(sourceText);
    return "source." + shortSha1(normalized);
  }

  private static InputStream open(ClassLoader loader, AssetCatalog assets, String path) throws IOException {
    if (path == null || path.isBlank()) return null;
    if (assets != null) {
      for (AssetType type : new AssetType[] {AssetType.SCRIPT, AssetType.CONFIG, AssetType.OTHER}) {
        try {
          if (assets.exists(type, path)) {
            return assets.open(type, path);
          }
        } catch (Exception ignored) {
          // reason: AssetCatalog.exists/open may throw on unknown asset types; classpath fallback follows
        }
      }
    }
    return loader == null ? null : loader.getResourceAsStream(path);
  }

  private static String[] candidateLocalePaths(String locale) {
    String resolved = (locale == null || locale.isBlank()) ? "en" : locale.trim();
    java.util.LinkedHashSet<String> paths = new java.util.LinkedHashSet<>();
    for (String base : BASES) {
      paths.add(base + resolved + ".properties");
    }
    if (!"en".equalsIgnoreCase(resolved)) {
      for (String base : BASES) {
        paths.add(base + "en.properties");
      }
    }
    return paths.toArray(String[]::new);
  }

  private static String normalizeSourceText(String sourceText) {
    if (sourceText == null) return "";
    return sourceText.replace("\r\n", "\n").replace('\r', '\n').trim();
  }

  private static String shortSha1(String value) {
    // Non-cryptographic: this produces a stable lookup-key suffix, not a
    // security hash. Uses String.hashCode() (the same algorithm the old
    // MessageDigest path already fell back to on NoSuchAlgorithmException),
    // formatted as unsigned hex, unconditionally on every platform.
    return Integer.toHexString(value.hashCode());
  }
}
