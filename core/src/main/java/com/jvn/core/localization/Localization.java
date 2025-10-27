package com.jvn.core.localization;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class Localization {
  private static final String BASE = "game/strings/";
  private static Properties props = new Properties();
  private static String currentLocale = "en";

  private Localization() {}

  public static void init(String locale, ClassLoader loader) {
    if (locale == null || locale.isBlank()) locale = "en";
    currentLocale = locale;
    props = new Properties();
    String[] candidates = new String[] {
      BASE + locale + ".properties",
      BASE + "en.properties"
    };
    for (String path : candidates) {
      try (InputStream in = loader.getResourceAsStream(path)) {
        if (in != null) {
          props.load(in);
          return;
        }
      } catch (IOException ignored) {
      }
    }
  }

  public static String locale() { return currentLocale; }

  public static String t(String key) {
    if (key == null) return "";
    return props.getProperty(key, key);
  }
}
