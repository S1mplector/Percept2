package com.jvn.core.menu.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Parses and serializes {@link MenuButtonLayoutSpec} from/to a properties
 * representation.
 *
 * <h3>Property format</h3>
 * <pre>
 * menuId=main
 * resolution=1920x1080
 * menuType=main
 *
 * button.ids=new_game,load,settings,quit
 *
 * button.new_game.label=New Game
 * button.new_game.boundsX=0.25
 * button.new_game.boundsY=0.30
 * button.new_game.boundsW=0.50
 * button.new_game.boundsH=0.08
 * button.new_game.tag=primary
 * button.new_game.asset=assets/ui/btn.png
 * button.new_game.hoverAsset=assets/ui/btn_hover.png
 * button.new_game.disabledAsset=assets/ui/btn_disabled.png
 * </pre>
 */
public final class MenuButtonLayoutLoader {

  private static final Set<String> HEADER_KEYS = Set.of(
      "menuId", "resolution", "menuType", "button.ids"
  );

  private static final Set<String> BUTTON_KEYS = Set.of(
      "label", "tag", "boundsX", "boundsY", "boundsW", "boundsH",
      "asset", "hoverAsset", "disabledAsset"
  );

  private MenuButtonLayoutLoader() {}

  // ── Parsing ──────────────────────────────────────────────────

  public record ParseResult(MenuButtonLayoutSpec spec, List<String> diagnostics) {
    public ParseResult {
      spec = spec == null ? MenuButtonLayoutSpec.empty("default", "default", null) : spec;
      diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
  }

  /**
   * Parse a button layout spec from properties.
   */
  public static MenuButtonLayoutSpec parse(Properties props) {
    return parseWithDiagnostics(props).spec();
  }

  /**
   * Parse with diagnostic messages for invalid values.
   */
  public static ParseResult parseWithDiagnostics(Properties props) {
    List<String> diagnostics = new ArrayList<>();
    if (props == null) {
      diagnostics.add("Properties was null; returning empty button layout");
      return new ParseResult(MenuButtonLayoutSpec.empty("default", "default", null), diagnostics);
    }

    String menuId = normalize(props.getProperty("menuId"), "default");
    String resolution = normalize(props.getProperty("resolution"), "default");
    String menuType = normalize(props.getProperty("menuType"), null);

    List<String> ids = parseCsv(props.getProperty("button.ids"));
    if (ids.isEmpty()) {
      ids = discoverButtonIds(props);
    }

    List<MenuButtonLayoutSpec.ButtonBounds> buttons = new ArrayList<>();
    for (String rawId : ids) {
      String id = normalize(rawId, null);
      if (id == null) continue;
      String prefix = "button." + id + ".";

      String label = normalize(props.getProperty(prefix + "label"), null);
      String tag = normalize(props.getProperty(prefix + "tag"), null);
      Double boundsX = parseOptionalDouble(props.getProperty(prefix + "boundsX"), diagnostics, prefix + "boundsX");
      Double boundsY = parseOptionalDouble(props.getProperty(prefix + "boundsY"), diagnostics, prefix + "boundsY");
      Double boundsW = parseOptionalDouble(props.getProperty(prefix + "boundsW"), diagnostics, prefix + "boundsW");
      Double boundsH = parseOptionalDouble(props.getProperty(prefix + "boundsH"), diagnostics, prefix + "boundsH");
      String asset = normalize(props.getProperty(prefix + "asset"), null);
      String hoverAsset = normalize(props.getProperty(prefix + "hoverAsset"), null);
      String disabledAsset = normalize(props.getProperty(prefix + "disabledAsset"), null);

      // Collect per-button extras
      Map<String, String> extras = new LinkedHashMap<>();
      for (String key : props.stringPropertyNames()) {
        if (key.startsWith(prefix)) {
          String suffix = key.substring(prefix.length());
          if (!BUTTON_KEYS.contains(suffix)) {
            extras.put(suffix, props.getProperty(key));
          }
        }
      }

      buttons.add(new MenuButtonLayoutSpec.ButtonBounds(
          id, label, tag, boundsX, boundsY, boundsW, boundsH,
          asset, hoverAsset, disabledAsset, extras
      ));
    }

    // Collect top-level extras
    Map<String, String> extras = new LinkedHashMap<>();
    for (String key : props.stringPropertyNames()) {
      if (HEADER_KEYS.contains(key)) continue;
      if (key.startsWith("button.")) continue;
      extras.put(key, props.getProperty(key));
    }

    return new ParseResult(
        new MenuButtonLayoutSpec(menuId, resolution, menuType, buttons, extras),
        diagnostics
    );
  }

  // ── Serialization ────────────────────────────────────────────

  /**
   * Serialize a button layout spec to properties text.
   */
  public static String serialize(MenuButtonLayoutSpec spec) {
    if (spec == null) return "";
    StringBuilder out = new StringBuilder();
    out.append("# Button layout").append(System.lineSeparator());
    out.append("# bounds values <= 1 are fractions; > 1 are pixels").append(System.lineSeparator());
    out.append(System.lineSeparator());

    out.append("menuId=").append(spec.menuId()).append(System.lineSeparator());
    out.append("resolution=").append(spec.resolution()).append(System.lineSeparator());
    if (spec.menuType() != null) {
      out.append("menuType=").append(spec.menuType()).append(System.lineSeparator());
    }

    if (!spec.extras().isEmpty()) {
      for (Map.Entry<String, String> e : spec.extras().entrySet()) {
        out.append(e.getKey()).append("=").append(e.getValue()).append(System.lineSeparator());
      }
    }

    if (spec.buttons().isEmpty()) return out.toString();

    List<String> ids = new ArrayList<>();
    for (MenuButtonLayoutSpec.ButtonBounds b : spec.buttons()) {
      ids.add(b.id());
    }
    out.append(System.lineSeparator());
    out.append("button.ids=").append(String.join(",", ids)).append(System.lineSeparator());

    for (MenuButtonLayoutSpec.ButtonBounds b : spec.buttons()) {
      out.append(System.lineSeparator());
      String prefix = "button." + b.id() + ".";
      if (b.label() != null) out.append(prefix).append("label=").append(b.label()).append(System.lineSeparator());
      if (b.tag() != null) out.append(prefix).append("tag=").append(b.tag()).append(System.lineSeparator());
      if (b.boundsX() != null) out.append(prefix).append("boundsX=").append(format(b.boundsX())).append(System.lineSeparator());
      if (b.boundsY() != null) out.append(prefix).append("boundsY=").append(format(b.boundsY())).append(System.lineSeparator());
      if (b.boundsW() != null) out.append(prefix).append("boundsW=").append(format(b.boundsW())).append(System.lineSeparator());
      if (b.boundsH() != null) out.append(prefix).append("boundsH=").append(format(b.boundsH())).append(System.lineSeparator());
      if (b.assetPath() != null) out.append(prefix).append("asset=").append(b.assetPath()).append(System.lineSeparator());
      if (b.hoverAssetPath() != null) out.append(prefix).append("hoverAsset=").append(b.hoverAssetPath()).append(System.lineSeparator());
      if (b.disabledAssetPath() != null) out.append(prefix).append("disabledAsset=").append(b.disabledAssetPath()).append(System.lineSeparator());
      for (Map.Entry<String, String> e : b.extras().entrySet()) {
        out.append(prefix).append(e.getKey()).append("=").append(e.getValue()).append(System.lineSeparator());
      }
    }

    return out.toString();
  }

  /**
   * Serialize a button layout spec to a {@link Properties} object.
   */
  public static Properties toProperties(MenuButtonLayoutSpec spec) {
    Properties p = new Properties();
    if (spec == null) return p;

    p.setProperty("menuId", spec.menuId());
    p.setProperty("resolution", spec.resolution());
    if (spec.menuType() != null) p.setProperty("menuType", spec.menuType());

    for (Map.Entry<String, String> e : spec.extras().entrySet()) {
      p.setProperty(e.getKey(), e.getValue());
    }

    if (spec.buttons().isEmpty()) return p;

    List<String> ids = new ArrayList<>();
    for (MenuButtonLayoutSpec.ButtonBounds b : spec.buttons()) {
      ids.add(b.id());
    }
    p.setProperty("button.ids", String.join(",", ids));

    for (MenuButtonLayoutSpec.ButtonBounds b : spec.buttons()) {
      String prefix = "button." + b.id() + ".";
      if (b.label() != null) p.setProperty(prefix + "label", b.label());
      if (b.tag() != null) p.setProperty(prefix + "tag", b.tag());
      if (b.boundsX() != null) p.setProperty(prefix + "boundsX", format(b.boundsX()));
      if (b.boundsY() != null) p.setProperty(prefix + "boundsY", format(b.boundsY()));
      if (b.boundsW() != null) p.setProperty(prefix + "boundsW", format(b.boundsW()));
      if (b.boundsH() != null) p.setProperty(prefix + "boundsH", format(b.boundsH()));
      if (b.assetPath() != null) p.setProperty(prefix + "asset", b.assetPath());
      if (b.hoverAssetPath() != null) p.setProperty(prefix + "hoverAsset", b.hoverAssetPath());
      if (b.disabledAssetPath() != null) p.setProperty(prefix + "disabledAsset", b.disabledAssetPath());
      for (Map.Entry<String, String> e : b.extras().entrySet()) {
        p.setProperty(prefix + e.getKey(), e.getValue());
      }
    }

    return p;
  }

  // ── Utilities ────────────────────────────────────────────────

  private static List<String> discoverButtonIds(Properties props) {
    Set<String> ids = new LinkedHashSet<>();
    for (String key : props.stringPropertyNames()) {
      if (!key.startsWith("button.")) continue;
      if (key.equals("button.ids")) continue;
      String rest = key.substring("button.".length());
      int dot = rest.indexOf('.');
      if (dot > 0) {
        String id = rest.substring(0, dot).trim();
        if (!id.isEmpty()) ids.add(id);
      }
    }
    return new ArrayList<>(ids);
  }

  private static List<String> parseCsv(String value) {
    List<String> list = new ArrayList<>();
    if (value == null || value.isBlank()) return list;
    for (String part : value.split(",")) {
      String trimmed = part.trim();
      if (!trimmed.isEmpty()) list.add(trimmed);
    }
    return list;
  }

  private static String normalize(String v, String def) {
    if (v == null) return def;
    String t = v.trim();
    return t.isEmpty() ? def : t;
  }

  private static Double parseOptionalDouble(String raw, List<String> diagnostics, String key) {
    if (raw == null || raw.isBlank()) return null;
    try {
      return Double.parseDouble(raw.trim());
    } catch (NumberFormatException e) {
      if (diagnostics != null) {
        diagnostics.add("Invalid double for '" + key + "': " + raw);
      }
      return null;
    }
  }

  private static String format(double value) {
    if (Math.rint(value) == value && Math.abs(value) < 1e12) {
      return Long.toString(Math.round(value));
    }
    return String.format(Locale.ROOT, "%.4f", value)
        .replaceAll("0+$", "")
        .replaceAll("\\.$", "");
  }
}
