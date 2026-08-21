package com.jvn.fx.ui;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

public final class ProjectFontResolver {
  private static final Map<FontKey, Font> FONT_CACHE = new ConcurrentHashMap<>();

  private ProjectFontResolver() {}

  public static Font resolve(File projectRoot, String fontSpec, FontWeight weight, double size, String fallbackFamily) {
    String normalizedFallback = normalizeFamily(fallbackFamily, "SansSerif");
    double resolvedSize = Double.isFinite(size) && size > 0.0 ? size : 12.0;
    String normalizedSpec = fontSpec == null ? "" : fontSpec.trim();
    File fontFile = resolveFontFile(projectRoot, normalizedSpec);
    FontKey key = createKey(fontFile, normalizedSpec, weight, resolvedSize, normalizedFallback);
    return FONT_CACHE.computeIfAbsent(
        key,
        ignored -> loadFont(fontFile, normalizedSpec, weight, resolvedSize, normalizedFallback)
    );
  }

  public static void clearCache() {
    FONT_CACHE.clear();
  }

  private static Font loadFont(File fontFile, String normalizedSpec, FontWeight weight, double size, String fallbackFamily) {
    if (fontFile != null && fontFile.isFile()) {
      try (InputStream input = Files.newInputStream(fontFile.toPath())) {
        Font loaded = Font.loadFont(input, size);
        if (loaded != null) return loaded;
      } catch (IOException | RuntimeException ignored) {
        // Fall through to the configured system font when the project font cannot be read.
      }
    }
    return createSystemFont(normalizeFamily(normalizedSpec, fallbackFamily), weight, size);
  }

  private static Font createSystemFont(String family, FontWeight weight, double size) {
    return weight == null ? Font.font(family, size) : Font.font(family, weight, size);
  }

  private static FontKey createKey(
      File fontFile,
      String fontSpec,
      FontWeight weight,
      double size,
      String fallbackFamily
  ) {
    String normalizedWeight = weight == null ? "" : weight.name();
    if (fontFile != null && fontFile.isFile()) {
      return new FontKey(
          "file",
          normalizeFilePath(fontFile),
          normalizedWeight,
          size,
          fontFile.lastModified(),
          fontFile.length(),
          fallbackFamily
      );
    }
    return new FontKey(
        "system",
        normalizeFamily(fontSpec, fallbackFamily),
        normalizedWeight,
        size,
        0L,
        0L,
        ""
    );
  }

  private static String normalizeFamily(String family, String fallback) {
    if (family == null || family.isBlank()) return fallback;
    return family.trim();
  }

  private static File resolveFontFile(File projectRoot, String fontSpec) {
    String normalized = fontSpec.trim();
    if (!looksLikeFontPath(normalized)) return null;
    File file = new File(normalized);
    File resolved = file.isAbsolute() ? file : (projectRoot != null ? new File(projectRoot, normalized) : file);
    return resolved.getAbsoluteFile();
  }

  private static boolean looksLikeFontPath(String fontSpec) {
    String lower = fontSpec.toLowerCase(Locale.ROOT);
    return lower.endsWith(".ttf") || lower.endsWith(".otf") || lower.endsWith(".ttc");
  }

  private static String normalizeFilePath(File file) {
    return file == null ? "" : file.toPath().toAbsolutePath().normalize().toString();
  }

  private record FontKey(
      String sourceType,
      String sourceValue,
      String weight,
      double size,
      long lastModified,
      long length,
      String fallbackFamily
  ) {
  }
}
