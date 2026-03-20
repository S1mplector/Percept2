package com.jvn.fx.ui;

import java.io.File;
import java.util.Locale;

import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

public final class ProjectFontResolver {
  private ProjectFontResolver() {}

  public static Font resolve(File projectRoot, String fontSpec, FontWeight weight, double size, String fallbackFamily) {
    String normalizedFallback = normalizeFamily(fallbackFamily, "SansSerif");
    double resolvedSize = Double.isFinite(size) && size > 0.0 ? size : 12.0;
    String normalizedSpec = fontSpec == null ? "" : fontSpec.trim();
    if (normalizedSpec.isEmpty()) {
      return createSystemFont(normalizedFallback, weight, resolvedSize);
    }

    File fontFile = resolveFontFile(projectRoot, normalizedSpec);
    if (fontFile != null && fontFile.isFile()) {
      Font loaded = Font.loadFont(fontFile.toURI().toString(), resolvedSize);
      if (loaded != null) return loaded;
    }
    return createSystemFont(normalizeFamily(normalizedSpec, normalizedFallback), weight, resolvedSize);
  }

  private static Font createSystemFont(String family, FontWeight weight, double size) {
    return weight == null ? Font.font(family, size) : Font.font(family, weight, size);
  }

  private static String normalizeFamily(String family, String fallback) {
    if (family == null || family.isBlank()) return fallback;
    return family.trim();
  }

  private static File resolveFontFile(File projectRoot, String fontSpec) {
    String normalized = fontSpec.trim();
    if (!looksLikeFontPath(normalized)) return null;
    File file = new File(normalized);
    if (file.isAbsolute()) return file;
    return projectRoot != null ? new File(projectRoot, normalized) : file;
  }

  private static boolean looksLikeFontPath(String fontSpec) {
    String lower = fontSpec.toLowerCase(Locale.ROOT);
    return lower.endsWith(".ttf") || lower.endsWith(".otf") || lower.endsWith(".ttc");
  }
}
