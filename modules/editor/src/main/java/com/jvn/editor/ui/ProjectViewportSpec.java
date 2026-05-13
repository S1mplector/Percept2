package com.jvn.editor.ui;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

/**
 * Resolves the runtime VN viewport size from jvn.project for 1:1 editor previews.
 */
public final class ProjectViewportSpec {
  public static final int DEFAULT_WIDTH = 1920;
  public static final int DEFAULT_HEIGHT = 1080;

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
      Integer renderWidth = firstPositiveIntNullable(
          p.getProperty("renderWidth"),
          p.getProperty("display.renderWidth"),
          p.getProperty("logicalWidth"),
          p.getProperty("display.logicalWidth")
      );
      Integer renderHeight = firstPositiveIntNullable(
          p.getProperty("renderHeight"),
          p.getProperty("display.renderHeight"),
          p.getProperty("logicalHeight"),
          p.getProperty("display.logicalHeight")
      );
      if (renderWidth != null && renderHeight != null) {
        width = renderWidth;
        height = renderHeight;
      } else {
        width = firstPositiveInt(width, p.getProperty("width"));
        height = firstPositiveInt(height, p.getProperty("height"));
      }
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
      // Fall back to defaults when project manifest can't be read.
    }

    return new Dimensions(width, height);
  }

  private static int firstPositiveInt(int fallback, String... values) {
    Integer parsed = firstPositiveIntNullable(values);
    return parsed != null ? parsed : fallback;
  }

  private static Integer firstPositiveIntNullable(String... values) {
    if (values == null) return null;
    for (String raw : values) {
      if (raw == null || raw.isBlank()) continue;
      try {
        int value = Integer.parseInt(raw.trim());
        if (value > 0) return value;
      } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
        // Keep scanning remaining candidates.
      }
    }
    return null;
  }

  public static record Dimensions(int width, int height) {
    public double aspect() {
      if (height <= 0) return DEFAULT_WIDTH / (double) DEFAULT_HEIGHT;
      return width / (double) height;
    }
  }
}
