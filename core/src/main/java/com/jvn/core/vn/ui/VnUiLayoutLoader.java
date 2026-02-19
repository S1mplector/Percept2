package com.jvn.core.vn.ui;

import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.assets.AssetType;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * Loader for dialogue layout configuration.
 *
 * <p>Supported locations (first match wins):
 * <ul>
 *   <li>configured by {@code dialogueLayout} in {@code jvn.project}</li>
 *   <li>{@code config/ui/dialogue.layout}</li>
 *   <li>{@code config/vn/dialogue.layout}</li>
 *   <li>{@code dialogue.layout}</li>
 * </ul>
 */
public final class VnUiLayoutLoader {
  private static final String[] DEFAULT_LAYOUT_PATHS = new String[] {
      "config/ui/dialogue.layout",
      "config/vn/dialogue.layout",
      "dialogue.layout"
  };

  private VnUiLayoutLoader() {}

  public static VnUiLayoutSpec loadFromAssets() {
    return loadFromAssets(new AssetCatalog());
  }

  public static VnUiLayoutSpec loadFromAssets(AssetCatalog assets) {
    if (assets == null) return VnUiLayoutSpec.defaults();
    List<String> candidates = new ArrayList<>();
    String configured = readManifestLayoutPath(assets);
    if (configured != null) candidates.add(configured);
    for (String path : DEFAULT_LAYOUT_PATHS) candidates.add(path);
    for (String path : candidates) {
      Properties p = loadFromAssets(assets, path);
      if (p != null) return parse(p, VnUiLayoutSpec.defaults());
    }
    return VnUiLayoutSpec.defaults();
  }

  public static VnUiLayoutSpec loadFromProjectRoot(File projectRoot) {
    if (projectRoot == null) return VnUiLayoutSpec.defaults();
    List<String> candidates = candidatePaths(projectRoot);
    for (String rel : candidates) {
      File f = new File(projectRoot, rel);
      if (!f.exists() || !f.isFile()) continue;
      Properties p = loadFromFile(f);
      if (p != null) return parse(p, VnUiLayoutSpec.defaults());
    }
    return VnUiLayoutSpec.defaults();
  }

  public static VnUiLayoutSpec parse(Properties props, VnUiLayoutSpec base) {
    VnUiLayoutSpec b = base == null ? VnUiLayoutSpec.defaults() : base;
    if (props == null) return b;
    return new VnUiLayoutSpec(
        parseDouble(props.getProperty("textBoxX"), b.textBoxX()),
        parseDouble(props.getProperty("textBoxY"), b.textBoxY()),
        parseDouble(props.getProperty("textBoxWidth"), b.textBoxWidth()),
        parseDouble(props.getProperty("textBoxHeight"), b.textBoxHeight()),
        parseDouble(props.getProperty("textBoxPadding"), b.textBoxPadding()),
        parseDouble(props.getProperty("nameBoxXOffset"), b.nameBoxXOffset()),
        parseDouble(props.getProperty("nameBoxYOffset"), b.nameBoxYOffset()),
        parseDouble(props.getProperty("nameBoxWidth"), b.nameBoxWidth()),
        parseDouble(props.getProperty("nameBoxHeight"), b.nameBoxHeight()),
        parseDouble(props.getProperty("nameTextXOffset"), b.nameTextXOffset()),
        parseDouble(props.getProperty("nameTextBaselineOffset"), b.nameTextBaselineOffset()),
        parseDouble(props.getProperty("dialogueTextHorizontalPadding"), b.dialogueTextHorizontalPadding()),
        parseDouble(props.getProperty("dialogueTextTopPadding"), b.dialogueTextTopPadding()),
        parseDouble(props.getProperty("choiceXCenter"), b.choiceXCenter()),
        parseDouble(props.getProperty("choiceYStart"), b.choiceYStart()),
        parseDouble(props.getProperty("choiceWidthFactor"), b.choiceWidthFactor()),
        parseDouble(props.getProperty("choiceHeight"), b.choiceHeight()),
        parseDouble(props.getProperty("choiceGap"), b.choiceGap()),
        parseDouble(props.getProperty("choiceTextXPadding"), b.choiceTextXPadding())
    );
  }

  public static Properties toProperties(VnUiLayoutSpec spec) {
    VnUiLayoutSpec s = spec == null ? VnUiLayoutSpec.defaults() : spec;
    Properties p = new Properties();
    p.setProperty("textBoxX", format(s.textBoxX()));
    p.setProperty("textBoxY", format(s.textBoxY()));
    p.setProperty("textBoxWidth", format(s.textBoxWidth()));
    p.setProperty("textBoxHeight", format(s.textBoxHeight()));
    p.setProperty("textBoxPadding", format(s.textBoxPadding()));
    p.setProperty("nameBoxXOffset", format(s.nameBoxXOffset()));
    p.setProperty("nameBoxYOffset", format(s.nameBoxYOffset()));
    p.setProperty("nameBoxWidth", format(s.nameBoxWidth()));
    p.setProperty("nameBoxHeight", format(s.nameBoxHeight()));
    p.setProperty("nameTextXOffset", format(s.nameTextXOffset()));
    p.setProperty("nameTextBaselineOffset", format(s.nameTextBaselineOffset()));
    p.setProperty("dialogueTextHorizontalPadding", format(s.dialogueTextHorizontalPadding()));
    p.setProperty("dialogueTextTopPadding", format(s.dialogueTextTopPadding()));
    p.setProperty("choiceXCenter", format(s.choiceXCenter()));
    p.setProperty("choiceYStart", format(s.choiceYStart()));
    p.setProperty("choiceWidthFactor", format(s.choiceWidthFactor()));
    p.setProperty("choiceHeight", format(s.choiceHeight()));
    p.setProperty("choiceGap", format(s.choiceGap()));
    p.setProperty("choiceTextXPadding", format(s.choiceTextXPadding()));
    return p;
  }

  public static String defaultProjectRelativePath() {
    return DEFAULT_LAYOUT_PATHS[0];
  }

  private static String format(double value) {
    if (Math.rint(value) == value) return Long.toString((long) value);
    return String.format(java.util.Locale.ROOT, "%.4f", value)
        .replaceAll("0+$", "")
        .replaceAll("\\.$", "");
  }

  private static Properties loadFromAssets(AssetCatalog assets, String path) {
    if (path == null || path.isBlank()) return null;
    try (InputStream in = assets.open(AssetType.SCRIPT, path)) {
      if (in == null) return null;
      Properties p = new Properties();
      p.load(in);
      return p;
    } catch (Exception ignored) {
      return null;
    }
  }

  private static Properties loadFromFile(File file) {
    try (FileInputStream fis = new FileInputStream(file)) {
      Properties p = new Properties();
      p.load(fis);
      return p;
    } catch (Exception ignored) {
      return null;
    }
  }

  private static List<String> candidatePaths(File projectRoot) {
    Set<String> paths = new LinkedHashSet<>();
    String configured = readManifestLayoutPath(projectRoot);
    if (configured != null) paths.add(configured);
    for (String path : DEFAULT_LAYOUT_PATHS) paths.add(path);
    return new ArrayList<>(paths);
  }

  private static String readManifestLayoutPath(File projectRoot) {
    File manifest = new File(projectRoot, "jvn.project");
    if (!manifest.exists()) return null;
    try (FileInputStream fis = new FileInputStream(manifest)) {
      Properties p = new Properties();
      p.load(fis);
      String value = p.getProperty("dialogueLayout");
      if (value == null || value.isBlank()) return null;
      return value.trim();
    } catch (Exception ignored) {
      return null;
    }
  }

  private static String readManifestLayoutPath(AssetCatalog assets) {
    if (assets == null) return null;
    try (InputStream in = assets.open(AssetType.SCRIPT, "jvn.project")) {
      if (in == null) return null;
      Properties p = new Properties();
      p.load(in);
      String value = p.getProperty("dialogueLayout");
      if (value == null || value.isBlank()) return null;
      return value.trim();
    } catch (Exception ignored) {
      return null;
    }
  }

  private static double parseDouble(String raw, double def) {
    if (raw == null || raw.isBlank()) return def;
    try {
      return Double.parseDouble(raw.trim());
    } catch (Exception ignored) {
      return def;
    }
  }
}
