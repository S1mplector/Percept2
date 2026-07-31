package com.jvn.editor.ui;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/** Startup snapshot of the Project Explorer icon profile managed by the Engine Hub. */
final class ProjectIconPreferences {
  private static final int MIN_ICON_SIZE = 12;
  private static final int MAX_ICON_SIZE = 28;

  enum Source {
    DESKTOP,
    THEME,
    BUNDLED;

    static Source parse(String value) {
      if (value == null || value.isBlank()) return DESKTOP;
      return switch (value.trim().toLowerCase(Locale.ROOT)) {
        case "theme", "installed", "custom" -> THEME;
        case "jvn-defaults", "default", "defaults", "bundled", "material", "svg", "jvn" -> BUNDLED;
        default -> DESKTOP;
      };
    }
  }

  record Options(
      Source source,
      String theme,
      int size,
      boolean folderVariants,
      boolean fileTypeVariants,
      boolean inheritTheme,
      boolean bundledFallback,
      boolean smoothScaling) {

    Options {
      source = source == null ? Source.DESKTOP : source;
      theme = theme == null ? "" : theme.trim();
      size = Math.max(MIN_ICON_SIZE, Math.min(MAX_ICON_SIZE, size));
    }

    static Options defaults() {
      return new Options(Source.DESKTOP, "", 18, true, true, true, true, true);
    }
  }

  private ProjectIconPreferences() {}

  static Path defaultFile() {
    return Path.of(
        System.getProperty("user.home", "."),
        ".jvn-editor",
        "project-icons.properties");
  }

  static Options load() {
    return load(defaultFile());
  }

  static Options load(Path file) {
    Options defaults = Options.defaults();
    if (file == null || !Files.isRegularFile(file)) return defaults;
    Properties properties = new Properties();
    try (InputStream input = Files.newInputStream(file)) {
      properties.load(input);
      return new Options(
          Source.parse(properties.getProperty("icons.source")),
          properties.getProperty("icons.theme", ""),
          parseSize(properties.getProperty("icons.size"), defaults.size()),
          readBoolean(properties, "icons.folderVariants", defaults.folderVariants()),
          readBoolean(properties, "icons.fileTypeVariants", defaults.fileTypeVariants()),
          readBoolean(properties, "icons.inheritTheme", defaults.inheritTheme()),
          readBoolean(properties, "icons.bundledFallback", defaults.bundledFallback()),
          readBoolean(properties, "icons.smoothScaling", defaults.smoothScaling()));
    } catch (IOException | IllegalArgumentException ignored) {
      return defaults;
    }
  }

  private static int parseSize(String value, int fallback) {
    try {
      return Math.max(MIN_ICON_SIZE, Math.min(MAX_ICON_SIZE, Integer.parseInt(value)));
    } catch (NumberFormatException ignored) {
      return fallback;
    }
  }

  private static boolean readBoolean(Properties properties, String key, boolean fallback) {
    String value = properties.getProperty(key);
    if (value == null || value.isBlank()) return fallback;
    return switch (value.trim().toLowerCase(Locale.ROOT)) {
      case "true", "1", "yes", "on", "enabled" -> true;
      case "false", "0", "no", "off", "disabled" -> false;
      default -> fallback;
    };
  }
}
