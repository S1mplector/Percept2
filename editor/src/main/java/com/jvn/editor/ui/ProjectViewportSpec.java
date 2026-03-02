package com.jvn.editor.ui;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

/**
 * Resolves the runtime VN viewport size from jvn.project for 1:1 editor previews.
 */
public final class ProjectViewportSpec {
  public static final int DEFAULT_WIDTH = 960;
  public static final int DEFAULT_HEIGHT = 540;

  private ProjectViewportSpec() {
  }

  public static Dimensions resolve(File projectRoot) {
    int width = DEFAULT_WIDTH;
    int height = DEFAULT_HEIGHT;

    if (projectRoot == null || !projectRoot.isDirectory()) {
      return new Dimensions(width, height);
    }

    File manifest = new File(projectRoot, "jvn.project");
    if (!manifest.isFile()) return new Dimensions(width, height);

    try (FileInputStream fis = new FileInputStream(manifest)) {
      Properties p = new Properties();
      p.load(fis);
      width = parsePositiveInt(p.getProperty("width"), width);
      height = parsePositiveInt(p.getProperty("height"), height);
    } catch (Exception ignored) {
      // Fall back to defaults when project manifest can't be read.
    }

    return new Dimensions(width, height);
  }

  private static int parsePositiveInt(String raw, int fallback) {
    if (raw == null || raw.isBlank()) return fallback;
    try {
      int value = Integer.parseInt(raw.trim());
      return value > 0 ? value : fallback;
    } catch (Exception ignored) {
      return fallback;
    }
  }

  public static record Dimensions(int width, int height) {
    public double aspect() {
      if (height <= 0) return DEFAULT_WIDTH / (double) DEFAULT_HEIGHT;
      return width / (double) height;
    }
  }
}
