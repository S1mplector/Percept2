package com.jvn.core.vn.ui;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.assets.AssetType;

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

  public record LoadResult(
      VnUiLayoutSpec layout,
      VnUiStyleSpec style,
      List<VnUiActionButtonSpec> textBoxButtons,
      List<String> diagnostics
  ) {
    public LoadResult {
      layout = layout == null ? VnUiLayoutSpec.defaults() : layout;
      style = style == null ? VnUiStyleSpec.defaults() : style;
      textBoxButtons = textBoxButtons == null ? List.of() : List.copyOf(textBoxButtons);
      diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
  }

  private VnUiLayoutLoader() {}

  public static VnUiLayoutSpec loadFromAssets() {
    return loadFromAssetsWithDiagnostics().layout();
  }

  public static VnUiLayoutSpec loadFromAssets(AssetCatalog assets) {
    return loadFromAssetsWithDiagnostics(assets).layout();
  }

  public static VnUiStyleSpec loadStyleFromAssets() {
    return loadFromAssetsWithDiagnostics().style();
  }

  public static VnUiStyleSpec loadStyleFromAssets(AssetCatalog assets) {
    return loadFromAssetsWithDiagnostics(assets).style();
  }

  public static VnUiLayoutSpec loadFromProjectRoot(File projectRoot) {
    return loadFromProjectRootWithDiagnostics(projectRoot).layout();
  }

  public static VnUiStyleSpec loadStyleFromProjectRoot(File projectRoot) {
    return loadFromProjectRootWithDiagnostics(projectRoot).style();
  }

  public static List<VnUiActionButtonSpec> loadTextBoxButtonsFromAssets() {
    return loadFromAssetsWithDiagnostics().textBoxButtons();
  }

  public static List<VnUiActionButtonSpec> loadTextBoxButtonsFromAssets(AssetCatalog assets) {
    return loadFromAssetsWithDiagnostics(assets).textBoxButtons();
  }

  public static List<VnUiActionButtonSpec> loadTextBoxButtonsFromProjectRoot(File projectRoot) {
    return loadFromProjectRootWithDiagnostics(projectRoot).textBoxButtons();
  }

  public static LoadResult loadFromAssetsWithDiagnostics() {
    return loadFromAssetsWithDiagnostics(new AssetCatalog());
  }

  public static LoadResult loadFromAssetsWithDiagnostics(AssetCatalog assets) {
    List<String> diagnostics = new ArrayList<>();
    Properties props = loadPropertiesFromAssetsInternal(assets, diagnostics);
    return parseWithDiagnostics(props, VnUiLayoutSpec.defaults(), VnUiStyleSpec.defaults(), List.of(), diagnostics);
  }

  public static LoadResult loadFromProjectRootWithDiagnostics(File projectRoot) {
    List<String> diagnostics = new ArrayList<>();
    Properties props = loadPropertiesFromProjectRootInternal(projectRoot, diagnostics);
    return parseWithDiagnostics(props, VnUiLayoutSpec.defaults(), VnUiStyleSpec.defaults(), List.of(), diagnostics);
  }

  public static Properties loadPropertiesFromAssets() {
    return loadPropertiesFromAssets(new AssetCatalog());
  }

  public static Properties loadPropertiesFromAssets(AssetCatalog assets) {
    return loadPropertiesFromAssetsInternal(assets, null);
  }

  public static Properties loadPropertiesFromProjectRoot(File projectRoot) {
    return loadPropertiesFromProjectRootInternal(projectRoot, null);
  }

  public static VnUiLayoutSpec parse(Properties props, VnUiLayoutSpec base) {
    return parseWithDiagnostics(props, base, VnUiStyleSpec.defaults()).layout();
  }

  public static VnUiStyleSpec parseStyle(Properties props, VnUiStyleSpec base) {
    return parseWithDiagnostics(props, VnUiLayoutSpec.defaults(), base).style();
  }

  public static LoadResult parseWithDiagnostics(
      Properties props,
      VnUiLayoutSpec baseLayout,
      VnUiStyleSpec baseStyle
  ) {
    return parseWithDiagnostics(props, baseLayout, baseStyle, List.of(), new ArrayList<>());
  }

  private static LoadResult parseWithDiagnostics(
      Properties props,
      VnUiLayoutSpec baseLayout,
      VnUiStyleSpec baseStyle,
      List<VnUiActionButtonSpec> baseButtons,
      List<String> diagnostics
  ) {
    VnUiLayoutSpec bLayout = baseLayout == null ? VnUiLayoutSpec.defaults() : baseLayout;
    VnUiStyleSpec bStyle = baseStyle == null ? VnUiStyleSpec.defaults() : baseStyle;
    List<VnUiActionButtonSpec> bButtons = baseButtons == null ? List.of() : baseButtons;
    if (props == null) return new LoadResult(bLayout, bStyle, bButtons, diagnostics);

    VnUiLayoutSpec layout = new VnUiLayoutSpec(
        parseDouble(props.getProperty("textBoxX"), bLayout.textBoxX(), diagnostics, "textBoxX"),
        parseDouble(props.getProperty("textBoxY"), bLayout.textBoxY(), diagnostics, "textBoxY"),
        parseDouble(props.getProperty("textBoxWidth"), bLayout.textBoxWidth(), diagnostics, "textBoxWidth"),
        parseDouble(props.getProperty("textBoxHeight"), bLayout.textBoxHeight(), diagnostics, "textBoxHeight"),
        parseDouble(props.getProperty("textBoxPadding"), bLayout.textBoxPadding(), diagnostics, "textBoxPadding"),
        parseDouble(props.getProperty("nameBoxXOffset"), bLayout.nameBoxXOffset(), diagnostics, "nameBoxXOffset"),
        parseDouble(props.getProperty("nameBoxYOffset"), bLayout.nameBoxYOffset(), diagnostics, "nameBoxYOffset"),
        parseDouble(props.getProperty("nameBoxWidth"), bLayout.nameBoxWidth(), diagnostics, "nameBoxWidth"),
        parseDouble(props.getProperty("nameBoxHeight"), bLayout.nameBoxHeight(), diagnostics, "nameBoxHeight"),
        parseDouble(props.getProperty("nameTextXOffset"), bLayout.nameTextXOffset(), diagnostics, "nameTextXOffset"),
        parseDouble(props.getProperty("nameTextBaselineOffset"), bLayout.nameTextBaselineOffset(), diagnostics, "nameTextBaselineOffset"),
        parseDouble(props.getProperty("dialogueTextHorizontalPadding"), bLayout.dialogueTextHorizontalPadding(), diagnostics, "dialogueTextHorizontalPadding"),
        parseDouble(props.getProperty("dialogueTextTopPadding"), bLayout.dialogueTextTopPadding(), diagnostics, "dialogueTextTopPadding"),
        parseDouble(props.getProperty("choiceXCenter"), bLayout.choiceXCenter(), diagnostics, "choiceXCenter"),
        parseDouble(props.getProperty("choiceYStart"), bLayout.choiceYStart(), diagnostics, "choiceYStart"),
        parseDouble(props.getProperty("choiceWidthFactor"), bLayout.choiceWidthFactor(), diagnostics, "choiceWidthFactor"),
        parseDouble(props.getProperty("choiceHeight"), bLayout.choiceHeight(), diagnostics, "choiceHeight"),
        parseDouble(props.getProperty("choiceGap"), bLayout.choiceGap(), diagnostics, "choiceGap"),
        parseDouble(props.getProperty("choiceTextXPadding"), bLayout.choiceTextXPadding(), diagnostics, "choiceTextXPadding")
    );

    VnUiStyleSpec style = new VnUiStyleSpec(
        // Textbox
        normalize(props.getProperty("textBoxAsset"), bStyle.textBoxAssetPath()),
        normalize(props.getProperty("textBoxColor"), bStyle.textBoxColor()),
        parseOptionalDouble(props.getProperty("textBoxOpacity"), bStyle.textBoxOpacity(), diagnostics, "textBoxOpacity"),
        // Name box
        normalize(props.getProperty("nameBoxAsset"), bStyle.nameBoxAssetPath()),
        normalize(props.getProperty("nameBoxColor"), bStyle.nameBoxColor()),
        normalize(props.getProperty("nameTextColor"), bStyle.nameTextColor()),
        normalize(props.getProperty("nameTextFontFamily"), bStyle.nameTextFontFamily()),
        parseOptionalInt(props.getProperty("nameTextFontSize"), bStyle.nameTextFontSize(), diagnostics, "nameTextFontSize"),
        // Dialogue text
        normalize(props.getProperty("dialogueTextColor"), bStyle.dialogueTextColor()),
        normalize(props.getProperty("dialogueTextFontFamily"), bStyle.dialogueTextFontFamily()),
        parseOptionalInt(props.getProperty("dialogueTextFontSize"), bStyle.dialogueTextFontSize(), diagnostics, "dialogueTextFontSize"),
        // Choice button assets
        normalize(props.getProperty("choiceButtonAsset"), bStyle.choiceButtonAssetPath()),
        normalize(props.getProperty("choiceButtonHoverAsset"), bStyle.choiceButtonHoverAssetPath()),
        normalize(props.getProperty("choiceButtonSelectedAsset"), bStyle.choiceButtonSelectedAssetPath()),
        normalize(props.getProperty("choiceButtonDisabledAsset"), bStyle.choiceButtonDisabledAssetPath()),
        // Choice colors
        normalize(props.getProperty("choiceBackgroundColor"), bStyle.choiceBackgroundColor()),
        normalize(props.getProperty("choiceHoverColor"), bStyle.choiceHoverColor()),
        normalize(props.getProperty("choiceSelectedColor"), bStyle.choiceSelectedColor()),
        normalize(props.getProperty("choiceDisabledColor"), bStyle.choiceDisabledColor()),
        normalize(props.getProperty("choiceTextColor"), bStyle.choiceTextColor()),
        normalize(props.getProperty("choiceHoverTextColor"), bStyle.choiceHoverTextColor()),
        normalize(props.getProperty("choiceSelectedTextColor"), bStyle.choiceSelectedTextColor()),
        normalize(props.getProperty("choiceDisabledTextColor"), bStyle.choiceDisabledTextColor()),
        normalize(props.getProperty("choiceBorderColor"), bStyle.choiceBorderColor()),
        normalize(props.getProperty("choiceHoverBorderColor"), bStyle.choiceHoverBorderColor()),
        normalize(props.getProperty("choiceSelectedBorderColor"), bStyle.choiceSelectedBorderColor()),
        normalize(props.getProperty("choiceDisabledBorderColor"), bStyle.choiceDisabledBorderColor()),
        // Choice geometry
        parseDouble(props.getProperty("choiceCornerRadius"), bStyle.choiceCornerRadius(), diagnostics, "choiceCornerRadius"),
        parseDouble(props.getProperty("choiceBorderWidth"), bStyle.choiceBorderWidth(), diagnostics, "choiceBorderWidth"),
        parseDouble(props.getProperty("choiceTextBaselineOffset"), bStyle.choiceTextBaselineOffset(), diagnostics, "choiceTextBaselineOffset"),
        // Choice font
        normalize(props.getProperty("choiceFontFamily"), bStyle.choiceFontFamily()),
        parseOptionalInt(props.getProperty("choiceFontSize"), bStyle.choiceFontSize(), diagnostics, "choiceFontSize"),
        // Character framing
        parseOptionalDouble(props.getProperty("characterHeightFactor"), bStyle.characterHeightFactor(), diagnostics, "characterHeightFactor"),
        parseOptionalDouble(props.getProperty("characterBaselineY"), bStyle.characterBaselineY(), diagnostics, "characterBaselineY")
    );

    List<VnUiActionButtonSpec> buttons = parseTextBoxButtons(props, bButtons, diagnostics);
    return new LoadResult(layout, style, buttons, diagnostics);
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

  public static Properties toStyleProperties(VnUiStyleSpec style) {
    VnUiStyleSpec s = style == null ? VnUiStyleSpec.defaults() : style;
    Properties p = new Properties();
    setOptional(p, "textBoxAsset", s.textBoxAssetPath());
    setOptional(p, "choiceButtonAsset", s.choiceButtonAssetPath());
    setOptional(p, "choiceButtonHoverAsset", s.choiceButtonHoverAssetPath());
    setOptional(p, "choiceButtonSelectedAsset", s.choiceButtonSelectedAssetPath());
    setOptional(p, "choiceButtonDisabledAsset", s.choiceButtonDisabledAssetPath());

    setOptional(p, "choiceBackgroundColor", s.choiceBackgroundColor());
    setOptional(p, "choiceHoverColor", s.choiceHoverColor());
    setOptional(p, "choiceSelectedColor", s.choiceSelectedColor());
    setOptional(p, "choiceDisabledColor", s.choiceDisabledColor());

    setOptional(p, "choiceTextColor", s.choiceTextColor());
    setOptional(p, "choiceHoverTextColor", s.choiceHoverTextColor());
    setOptional(p, "choiceSelectedTextColor", s.choiceSelectedTextColor());
    setOptional(p, "choiceDisabledTextColor", s.choiceDisabledTextColor());

    setOptional(p, "choiceBorderColor", s.choiceBorderColor());
    setOptional(p, "choiceHoverBorderColor", s.choiceHoverBorderColor());
    setOptional(p, "choiceSelectedBorderColor", s.choiceSelectedBorderColor());
    setOptional(p, "choiceDisabledBorderColor", s.choiceDisabledBorderColor());

    p.setProperty("choiceCornerRadius", format(s.choiceCornerRadius()));
    p.setProperty("choiceBorderWidth", format(s.choiceBorderWidth()));
    p.setProperty("choiceTextBaselineOffset", format(s.choiceTextBaselineOffset()));
    setOptional(p, "characterHeightFactor", s.characterHeightFactor() == null ? null : format(s.characterHeightFactor()));
    setOptional(p, "characterBaselineY", s.characterBaselineY() == null ? null : format(s.characterBaselineY()));
    return p;
  }

  public static Properties toButtonProperties(List<VnUiActionButtonSpec> buttons) {
    Properties p = new Properties();
    if (buttons == null || buttons.isEmpty()) return p;

    List<String> ids = new ArrayList<>();
    Map<String, VnUiActionButtonSpec> unique = new LinkedHashMap<>();
    for (VnUiActionButtonSpec button : buttons) {
      if (button == null) continue;
      String id = normalize(button.id(), "");
      if (id == null || id.isBlank()) continue;
      if (!unique.containsKey(id)) ids.add(id);
      unique.put(id, button);
    }
    if (ids.isEmpty()) return p;
    p.setProperty("textBoxButton.ids", String.join(",", ids));
    for (String id : ids) {
      VnUiActionButtonSpec b = unique.get(id);
      if (b == null) continue;
      String prefix = "textBoxButton." + id + ".";
      setOptional(p, prefix + "label", b.label());
      setOptional(p, prefix + "action", b.action());
      setOptional(p, prefix + "target", b.target());
      p.setProperty(prefix + "enabled", Boolean.toString(b.enabled()));
      setOptional(p, prefix + "asset", b.assetPath());
      setOptional(p, prefix + "hoverAsset", b.hoverAssetPath());
      setOptional(p, prefix + "disabledAsset", b.disabledAssetPath());
      setOptional(p, prefix + "boundsPoints", b.boundsPoints());
      p.setProperty(prefix + "x", format(b.x()));
      p.setProperty(prefix + "y", format(b.y()));
      p.setProperty(prefix + "width", format(b.width()));
      p.setProperty(prefix + "height", format(b.height()));
    }
    return p;
  }

  public static Properties toProperties(VnUiLayoutSpec layout, VnUiStyleSpec style) {
    return toProperties(layout, style, List.of());
  }

  public static Properties toProperties(VnUiLayoutSpec layout, VnUiStyleSpec style, List<VnUiActionButtonSpec> buttons) {
    Properties merged = new Properties();
    Properties lp = toProperties(layout);
    for (String key : lp.stringPropertyNames()) {
      merged.setProperty(key, lp.getProperty(key));
    }
    Properties sp = toStyleProperties(style);
    for (String key : sp.stringPropertyNames()) {
      merged.setProperty(key, sp.getProperty(key));
    }
    Properties bp = toButtonProperties(buttons);
    for (String key : bp.stringPropertyNames()) {
      merged.setProperty(key, bp.getProperty(key));
    }
    return merged;
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

  private static Properties loadPropertiesFromAssetsInternal(AssetCatalog assets, List<String> diagnostics) {
    if (assets == null) return new Properties();
    List<String> candidates = new ArrayList<>();
    String configured = readManifestLayoutPath(assets, diagnostics);
    if (configured != null) candidates.add(configured);
    for (String path : DEFAULT_LAYOUT_PATHS) candidates.add(path);

    boolean configuredTried = false;
    for (String path : candidates) {
      if (path == null || path.isBlank()) continue;
      if (configured != null && configured.equals(path)) configuredTried = true;
      Properties p = loadFromAssets(assets, path, diagnostics);
      if (p != null) return p;
      if (configured != null && configured.equals(path) && diagnostics != null) {
        diagnostics.add("Configured dialogueLayout not found: " + path);
      }
    }
    if (configured != null && !configuredTried && diagnostics != null) {
      diagnostics.add("Configured dialogueLayout was ignored due to invalid path: " + configured);
    }
    return new Properties();
  }

  private static Properties loadPropertiesFromProjectRootInternal(File projectRoot, List<String> diagnostics) {
    if (projectRoot == null) return new Properties();
    List<String> candidates = candidatePaths(projectRoot, diagnostics);
    String configured = readManifestLayoutPath(projectRoot, diagnostics);
    for (String rel : candidates) {
      File f = new File(projectRoot, rel);
      if (!f.exists() || !f.isFile()) {
        if (configured != null && configured.equals(rel) && diagnostics != null) {
          diagnostics.add("Configured dialogueLayout file does not exist: " + rel);
        }
        continue;
      }
      Properties p = loadFromFile(f, diagnostics);
      if (p != null) return p;
    }
    return new Properties();
  }

  private static Properties loadFromAssets(AssetCatalog assets, String path, List<String> diagnostics) {
    if (path == null || path.isBlank()) return null;
    try (InputStream in = assets.open(AssetType.SCRIPT, path)) {
      if (in == null) return null;
      Properties p = new Properties();
      p.load(in);
      return p;
    } catch (Exception ex) {
      if (diagnostics != null) {
        diagnostics.add("Failed to parse layout properties '" + path + "': " + simplify(ex));
      }
      return null;
    }
  }

  private static Properties loadFromFile(File file, List<String> diagnostics) {
    try (FileInputStream fis = new FileInputStream(file)) {
      Properties p = new Properties();
      p.load(fis);
      return p;
    } catch (Exception ex) {
      if (diagnostics != null) {
        diagnostics.add("Failed to parse layout properties '" + file.getPath() + "': " + simplify(ex));
      }
      return null;
    }
  }

  private static List<String> candidatePaths(File projectRoot, List<String> diagnostics) {
    Set<String> paths = new LinkedHashSet<>();
    String configured = readManifestLayoutPath(projectRoot, diagnostics);
    if (configured != null) paths.add(configured);
    for (String path : DEFAULT_LAYOUT_PATHS) paths.add(path);
    return new ArrayList<>(paths);
  }

  private static String readManifestLayoutPath(File projectRoot, List<String> diagnostics) {
    File manifest = new File(projectRoot, "jvn.project");
    if (!manifest.exists()) return null;
    try (FileInputStream fis = new FileInputStream(manifest)) {
      Properties p = new Properties();
      p.load(fis);
      String value = p.getProperty("dialogueLayout");
      if (value == null || value.isBlank()) return null;
      return value.trim();
    } catch (Exception ex) {
      if (diagnostics != null) {
        diagnostics.add("Failed to read jvn.project for dialogueLayout: " + simplify(ex));
      }
      return null;
    }
  }

  private static String readManifestLayoutPath(AssetCatalog assets, List<String> diagnostics) {
    if (assets == null) return null;
    try (InputStream in = assets.open(AssetType.SCRIPT, "jvn.project")) {
      if (in == null) return null;
      Properties p = new Properties();
      p.load(in);
      String value = p.getProperty("dialogueLayout");
      if (value == null || value.isBlank()) return null;
      return value.trim();
    } catch (Exception ex) {
      if (diagnostics != null) {
        diagnostics.add("Failed to read jvn.project from assets for dialogueLayout: " + simplify(ex));
      }
      return null;
    }
  }

  private static Double parseOptionalDouble(String raw, Double def, List<String> diagnostics, String key) {
    if (raw == null || raw.isBlank()) return def;
    try {
      return Double.parseDouble(raw.trim());
    } catch (Exception ex) {
      if (diagnostics != null) diagnostics.add("Invalid number for '" + key + "': '" + raw + "'");
      return def;
    }
  }

  private static Integer parseOptionalInt(String raw, Integer def, List<String> diagnostics, String key) {
    if (raw == null || raw.isBlank()) return def;
    try {
      return Integer.parseInt(raw.trim());
    } catch (Exception ex) {
      if (diagnostics != null) diagnostics.add("Invalid integer for '" + key + "': '" + raw + "'");
      return def;
    }
  }

  private static double parseDouble(String raw, double def, List<String> diagnostics, String key) {
    if (raw == null || raw.isBlank()) return def;
    try {
      return Double.parseDouble(raw.trim());
    } catch (Exception ex) {
      if (diagnostics != null) {
        diagnostics.add("Invalid number for '" + key + "': '" + raw + "' (using " + format(def) + ")");
      }
      return def;
    }
  }

  private static boolean parseBoolean(String raw, boolean def, List<String> diagnostics, String key) {
    if (raw == null || raw.isBlank()) return def;
    String value = raw.trim().toLowerCase(java.util.Locale.ROOT);
    if ("true".equals(value) || "1".equals(value) || "yes".equals(value) || "on".equals(value)) return true;
    if ("false".equals(value) || "0".equals(value) || "no".equals(value) || "off".equals(value)) return false;
    if (diagnostics != null) {
      diagnostics.add("Invalid boolean for '" + key + "': '" + raw + "' (using " + def + ")");
    }
    return def;
  }

  private static List<String> parseCsv(String raw) {
    List<String> out = new ArrayList<>();
    if (raw == null || raw.isBlank()) return out;
    String[] parts = raw.split(",");
    for (String part : parts) {
      String value = normalize(part, null);
      if (value != null) out.add(value);
    }
    return out;
  }

  private static List<VnUiActionButtonSpec> parseTextBoxButtons(
      Properties props,
      List<VnUiActionButtonSpec> baseButtons,
      List<String> diagnostics
  ) {
    Map<String, VnUiActionButtonSpec> baseById = new LinkedHashMap<>();
    if (baseButtons != null) {
      for (VnUiActionButtonSpec button : baseButtons) {
        if (button == null || button.id() == null || button.id().isBlank()) continue;
        baseById.put(button.id(), button);
      }
    }

    List<String> ids = parseCsv(props.getProperty("textBoxButton.ids"));
    if (ids.isEmpty()) ids = collectTextBoxButtonIds(props);
    if (ids.isEmpty() && !baseById.isEmpty()) ids = new ArrayList<>(baseById.keySet());

    List<VnUiActionButtonSpec> result = new ArrayList<>();
    for (String idRaw : ids) {
      String id = normalize(idRaw, null);
      if (id == null) continue;
      String prefix = "textBoxButton." + id + ".";
      VnUiActionButtonSpec base = baseById.get(id);
      if (base == null) base = VnUiActionButtonSpec.defaults(id);
      String label = normalize(props.getProperty(prefix + "label"), base.label());
      String action = normalize(props.getProperty(prefix + "action"), base.action());
      String target = normalize(props.getProperty(prefix + "target"), base.target());
      boolean enabled = parseBoolean(props.getProperty(prefix + "enabled"), base.enabled(), diagnostics, prefix + "enabled");
      String asset = normalize(props.getProperty(prefix + "asset"), base.assetPath());
      String hoverAsset = normalize(props.getProperty(prefix + "hoverAsset"), base.hoverAssetPath());
      String disabledAsset = normalize(props.getProperty(prefix + "disabledAsset"), base.disabledAssetPath());
      String boundsPoints = normalize(props.getProperty(prefix + "boundsPoints"), base.boundsPoints());
      double x = parseDouble(props.getProperty(prefix + "x"), base.x(), diagnostics, prefix + "x");
      double y = parseDouble(props.getProperty(prefix + "y"), base.y(), diagnostics, prefix + "y");
      double width = parseDouble(props.getProperty(prefix + "width"), base.width(), diagnostics, prefix + "width");
      double height = parseDouble(props.getProperty(prefix + "height"), base.height(), diagnostics, prefix + "height");
      result.add(new VnUiActionButtonSpec(
          id,
          label,
          action,
          target,
          enabled,
          asset,
          hoverAsset,
          disabledAsset,
          boundsPoints,
          x,
          y,
          width,
          height
      ));
    }
    return result;
  }

  private static List<String> collectTextBoxButtonIds(Properties props) {
    Set<String> ids = new LinkedHashSet<>();
    for (String key : props.stringPropertyNames()) {
      if (!key.startsWith("textBoxButton.")) continue;
      int dot = key.indexOf('.', "textBoxButton.".length());
      if (dot <= "textBoxButton.".length()) continue;
      String id = key.substring("textBoxButton.".length(), dot).trim();
      if (!id.isEmpty() && !"ids".equalsIgnoreCase(id)) ids.add(id);
    }
    return new ArrayList<>(ids);
  }

  private static String normalize(String value, String fallback) {
    if (value == null) return fallback;
    String trimmed = value.trim();
    return trimmed.isEmpty() ? fallback : trimmed;
  }

  private static void setOptional(Properties properties, String key, String value) {
    String normalized = normalize(value, null);
    if (normalized == null) properties.remove(key);
    else properties.setProperty(key, normalized);
  }

  private static String simplify(Exception ex) {
    if (ex == null) return "unknown error";
    String message = ex.getMessage();
    if (message == null || message.isBlank()) return ex.getClass().getSimpleName();
    return ex.getClass().getSimpleName() + ": " + message;
  }
}
