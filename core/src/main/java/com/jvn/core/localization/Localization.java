package com.jvn.core.localization;

import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.assets.AssetType;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Global string-table localization backed by {@code .properties} files.
 *
 * <p>Strings are loaded from the classpath under
 * {@code game/strings/<locale>.properties}. If the requested locale file
 * is missing, the loader falls back to {@code en.properties}.</p>
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

  /** Classpath base directory for string tables. */
  private static final String BASE = "game/strings/";

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
   *   <li>{@code game/strings/<locale>.properties}</li>
   *   <li>{@code game/strings/en.properties}</li>
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
    String[] candidates = new String[] {
      BASE + locale + ".properties",
      BASE + "en.properties"
    };
    for (String path : candidates) {
      try (InputStream in = open(loader, assets, path)) {
        if (in != null) {
          props.load(in);
          return;
        }
      } catch (IOException ignored) {
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

  private static InputStream open(ClassLoader loader, AssetCatalog assets, String path) throws IOException {
    if (path == null || path.isBlank()) return null;
    if (assets != null) {
      for (AssetType type : new AssetType[] {AssetType.SCRIPT, AssetType.CONFIG, AssetType.OTHER}) {
        try {
          if (assets.exists(type, path)) {
            return assets.open(type, path);
          }
        } catch (Exception ignored) {
        }
      }
    }
    return loader == null ? null : loader.getResourceAsStream(path);
  }
}
