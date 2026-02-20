package com.jvn.core.menu.config;

import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.assets.AssetType;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public final class MenuProfileLoader {
  private static final String[] REGISTRY_PATHS = new String[] {
      "config/menu/registry/menu.registry",
      "config/menu/menu.registry",
      "config/menu/registry.properties",
      "menu.registry"
  };

  public record LoadResult(MenuProfile profile, List<String> diagnostics) {
    public LoadResult {
      profile = profile == null ? MenuProfile.defaults() : profile;
      diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
  }

  private record LoadedProperties(String path, Properties properties) {}

  private MenuProfileLoader() {}

  public static MenuProfile loadFromAssets() {
    return load(new AssetCatalog());
  }

  public static LoadResult loadWithDiagnostics() {
    return loadWithDiagnostics(new AssetCatalog());
  }

  public static MenuProfile load(AssetCatalog assets) {
    return loadWithDiagnostics(assets).profile();
  }

  public static LoadResult loadWithDiagnostics(AssetCatalog assets) {
    List<String> diagnostics = new ArrayList<>();
    if (assets == null) {
      diagnostics.add("AssetCatalog was null; using default menu profile");
      return new LoadResult(MenuProfile.defaults(), diagnostics);
    }

    MenuProfile defaults = MenuProfile.defaults();

    Map<String, MenuLayoutSpec> layouts = new LinkedHashMap<>();
    Map<String, MenuStyleSpec> styles = new LinkedHashMap<>();
    Map<String, MenuScreenSpec> screens = new LinkedHashMap<>();
    String defaultScreen = defaults.defaultScreenId();

    LoadedProperties registryLoad = loadFirstProperties(assets, diagnostics, "menu registry", REGISTRY_PATHS);
    Properties registry = registryLoad == null ? null : registryLoad.properties();
    if (registry != null) {
      String configuredDefault = normalize(registry.getProperty("defaultMenu", registry.getProperty("defaultScreen")), null);
      if (configuredDefault != null) defaultScreen = configuredDefault;
    }

    Set<String> layoutIds = new LinkedHashSet<>();
    layoutIds.add("default");
    layoutIds.addAll(parseCsv(registry != null ? registry.getProperty("layouts") : null));
    layoutIds.addAll(discoverIds(assets, diagnostics, "config/menu/layouts", ".layout", ".properties"));
    layoutIds.addAll(discoverIds(assets, diagnostics, "config/menu", ".layout", ".properties"));

    Set<String> styleIds = new LinkedHashSet<>();
    styleIds.add("default");
    styleIds.addAll(parseCsv(registry != null ? registry.getProperty("styles") : null));
    styleIds.addAll(discoverIds(assets, diagnostics, "config/menu/styles", ".style", ".properties"));
    styleIds.addAll(discoverIds(assets, diagnostics, "config/menu", ".style", ".properties"));

    Set<String> menuIds = new LinkedHashSet<>();
    menuIds.add("main");
    menuIds.addAll(parseCsv(registry != null ? registry.getProperty("menus") : null));
    menuIds.addAll(discoverIds(assets, diagnostics, "config/menu/menus", ".menu", ".properties"));
    menuIds.addAll(discoverIds(assets, diagnostics, "config/menu", ".menu", ".properties"));

    for (String id : layoutIds) {
      MenuLayoutSpec spec = resolveLayout(id, assets, defaults.layouts(), layouts, new LinkedHashSet<>(), diagnostics);
      if (spec != null) layouts.put(id, spec);
    }

    for (String id : styleIds) {
      MenuStyleSpec spec = resolveStyle(id, assets, defaults.styles(), styles, new LinkedHashSet<>(), diagnostics);
      if (spec != null) styles.put(id, spec);
    }

    for (String id : menuIds) {
      MenuScreenSpec spec = resolveScreen(id, assets, defaults.screens(), screens, new LinkedHashSet<>(), diagnostics);
      if (spec != null) screens.put(id, spec);
    }

    if (!screens.containsKey(defaultScreen)) {
      diagnostics.add("Configured default menu '" + defaultScreen + "' is undefined; using fallback");
      defaultScreen = screens.containsKey("main") ? "main" : screens.keySet().stream().findFirst().orElse("main");
    }

    MenuProfile profile = new MenuProfile(defaultScreen, screens, layouts, styles);
    diagnostics.addAll(MenuProfileValidator.validate(profile));
    return new LoadResult(profile, diagnostics);
  }

  private static MenuLayoutSpec resolveLayout(
      String id,
      AssetCatalog assets,
      Map<String, MenuLayoutSpec> defaults,
      Map<String, MenuLayoutSpec> resolved,
      Set<String> visiting,
      List<String> diagnostics
  ) {
    String key = normalize(id, null);
    if (key == null) return resolved.getOrDefault("default", MenuProfile.defaultLayout());
    if (resolved.containsKey(key)) return resolved.get(key);
    if (!visiting.add(key)) {
      diagnostics.add("Circular layout inheritance detected at '" + key + "'");
      return defaults.getOrDefault(key, MenuProfile.defaultLayout());
    }

    MenuLayoutSpec base = defaults.getOrDefault(key, MenuProfile.defaultLayout());
    LoadedProperties loaded = loadFirstProperties(assets, diagnostics, "layout '" + key + "'", layoutPaths(key));
    if (loaded != null) {
      Properties p = loaded.properties();
      String parent = normalize(p.getProperty("extends"), null);
      if (parent != null && !parent.equalsIgnoreCase(key)) {
        MenuLayoutSpec parentLayout = resolveLayout(parent, assets, defaults, resolved, visiting, diagnostics);
        if (parentLayout != null) {
          base = parentLayout;
        } else {
          diagnostics.add("Layout '" + key + "' extends missing layout '" + parent + "'");
        }
      }
      base = parseLayout(key, p, base, diagnostics, loaded.path());
    }

    visiting.remove(key);
    resolved.put(key, base);
    return base;
  }

  private static MenuStyleSpec resolveStyle(
      String id,
      AssetCatalog assets,
      Map<String, MenuStyleSpec> defaults,
      Map<String, MenuStyleSpec> resolved,
      Set<String> visiting,
      List<String> diagnostics
  ) {
    String key = normalize(id, null);
    if (key == null) return resolved.getOrDefault("default", MenuProfile.defaultStyle());
    if (resolved.containsKey(key)) return resolved.get(key);
    if (!visiting.add(key)) {
      diagnostics.add("Circular style inheritance detected at '" + key + "'");
      return defaults.getOrDefault(key, MenuProfile.defaultStyle());
    }

    MenuStyleSpec base = defaults.getOrDefault(key, MenuProfile.defaultStyle());
    LoadedProperties loaded = loadFirstProperties(assets, diagnostics, "style '" + key + "'", stylePaths(key));
    if (loaded != null) {
      Properties p = loaded.properties();
      String parent = normalize(p.getProperty("extends"), null);
      if (parent != null && !parent.equalsIgnoreCase(key)) {
        MenuStyleSpec parentStyle = resolveStyle(parent, assets, defaults, resolved, visiting, diagnostics);
        if (parentStyle != null) {
          base = parentStyle;
        } else {
          diagnostics.add("Style '" + key + "' extends missing style '" + parent + "'");
        }
      }
      base = parseStyle(key, p, base, diagnostics, loaded.path());
    }

    visiting.remove(key);
    resolved.put(key, base);
    return base;
  }

  private static MenuScreenSpec resolveScreen(
      String id,
      AssetCatalog assets,
      Map<String, MenuScreenSpec> defaults,
      Map<String, MenuScreenSpec> resolved,
      Set<String> visiting,
      List<String> diagnostics
  ) {
    String key = normalize(id, null);
    if (key == null) return resolved.get("main");
    if (resolved.containsKey(key)) return resolved.get(key);
    if (!visiting.add(key)) {
      diagnostics.add("Circular menu inheritance detected at screen '" + key + "'");
      return defaults.getOrDefault(key, "main".equals(key) ? MenuProfile.defaultMainScreen() : null);
    }

    MenuScreenSpec base = defaults.get(key);
    if (base == null && "main".equals(key)) base = MenuProfile.defaultMainScreen();

    LoadedProperties loaded = loadFirstProperties(assets, diagnostics, "menu screen '" + key + "'", menuPaths(key));
    if (loaded == null && base == null) {
      visiting.remove(key);
      return null;
    }

    if (loaded != null) {
      Properties p = loaded.properties();
      String parent = normalize(p.getProperty("extends"), null);
      if (parent != null && !parent.equalsIgnoreCase(key)) {
        MenuScreenSpec parentScreen = resolveScreen(parent, assets, defaults, resolved, visiting, diagnostics);
        if (parentScreen != null) {
          base = parentScreen;
        } else {
          diagnostics.add("Menu screen '" + key + "' extends missing menu '" + parent + "'");
        }
      }
      if (base == null) {
        base = new MenuScreenSpec(key, null, null, "default", "default", true, List.of());
      }
      base = parseScreen(key, p, base, diagnostics, loaded.path());
    }

    visiting.remove(key);
    if (base != null) resolved.put(key, base);
    return base;
  }

  private static MenuLayoutSpec parseLayout(String id, Properties p, MenuLayoutSpec base, List<String> diagnostics, String sourcePath) {
    return new MenuLayoutSpec(
        id,
        parseDouble(p.getProperty("listYStart"), base.listYStart(), diagnostics, sourcePath, "listYStart"),
        parseDouble(p.getProperty("lineHeight"), base.lineHeight(), diagnostics, sourcePath, "lineHeight"),
        parseDouble(p.getProperty("listWidthFactor", p.getProperty("listWidth")), base.listWidthFactor(), diagnostics, sourcePath, "listWidthFactor"),
        normalize(p.getProperty("textAlign"), base.textAlign()),
        parseDouble(p.getProperty("hintsBottomMargin"), base.hintsBottomMargin(), diagnostics, sourcePath, "hintsBottomMargin"),
        parseOptionalDouble(p.getProperty("titleY"), base.titleY(), diagnostics, sourcePath, "titleY")
    );
  }

  private static MenuStyleSpec parseStyle(String id, Properties p, MenuStyleSpec base, List<String> diagnostics, String sourcePath) {
    return new MenuStyleSpec(
        id,
        normalize(p.getProperty("itemColor"), base.itemColor()),
        normalize(p.getProperty("itemSelectedColor"), base.itemSelectedColor()),
        normalize(p.getProperty("itemDisabledColor"), base.itemDisabledColor()),
        normalize(p.getProperty("itemPrefix"), base.itemPrefix()),
        normalize(p.getProperty("itemSelectedPrefix"), base.itemSelectedPrefix()),
        normalize(p.getProperty("itemDisabledPrefix"), base.itemDisabledPrefix()),
        normalize(p.getProperty("itemFontFamily"), base.itemFontFamily()),
        normalize(p.getProperty("itemFontWeight"), base.itemFontWeight()),
        parseOptionalInt(p.getProperty("itemFontSize"), base.itemFontSize(), diagnostics, sourcePath, "itemFontSize"),
        normalize(p.getProperty("buttonAsset"), base.buttonAssetPath()),
        normalize(p.getProperty("buttonSelectedAsset"), base.buttonSelectedAssetPath()),
        normalize(p.getProperty("buttonDisabledAsset"), base.buttonDisabledAssetPath()),
        parseOptionalDouble(p.getProperty("buttonTextPaddingX"), base.buttonTextPaddingX(), diagnostics, sourcePath, "buttonTextPaddingX"),
        parseOptionalDouble(p.getProperty("buttonTextPaddingY"), base.buttonTextPaddingY(), diagnostics, sourcePath, "buttonTextPaddingY")
    );
  }

  private static MenuScreenSpec parseScreen(String id, Properties p, MenuScreenSpec base, List<String> diagnostics, String sourcePath) {
    String titleText = normalize(p.getProperty("titleText"), base == null ? null : base.titleText());
    String hintsText = normalize(p.getProperty("hintsText"), base == null ? null : base.hintsText());
    String layoutId = normalize(p.getProperty("layout", p.getProperty("layoutId")), base == null ? "default" : base.layoutId());
    String defaultStyleId = normalize(p.getProperty("defaultItemStyle"), base == null ? "default" : base.defaultStyleId());
    boolean wrapSelection = parseBoolean(p.getProperty("wrapSelection"), base == null || base.wrapSelection(), diagnostics, sourcePath, "wrapSelection");

    List<String> itemIds = parseCsv(p.getProperty("items"));
    if (itemIds.isEmpty()) itemIds = collectItemIdsFromProperties(p);
    if (itemIds.isEmpty() && base != null && !base.items().isEmpty()) {
      for (MenuItemSpec i : base.items()) itemIds.add(i.id());
    }

    Map<String, MenuItemSpec> baseItems = new LinkedHashMap<>();
    if (base != null) {
      for (MenuItemSpec i : base.items()) baseItems.put(i.id(), i);
    }

    List<MenuItemSpec> items = new ArrayList<>();
    for (String itemId : itemIds) {
      String idNorm = normalize(itemId, null);
      if (idNorm == null) continue;
      String itemPrefix = "item." + idNorm + ".";
      MenuItemSpec bi = baseItems.get(idNorm);
      String label = normalize(p.getProperty(itemPrefix + "label"), bi == null ? null : bi.label());
      String styleId = normalize(p.getProperty(itemPrefix + "style"), bi == null ? defaultStyleId : bi.styleId());
      String icon = normalize(p.getProperty(itemPrefix + "icon"), bi == null ? null : bi.iconPath());
      boolean enabled = parseBoolean(p.getProperty(itemPrefix + "enabled"), bi == null || bi.enabled(), diagnostics, sourcePath, itemPrefix + "enabled");
      String actionRaw = normalize(p.getProperty(itemPrefix + "action"), bi == null ? null : bi.action().type().name());
      String targetRaw = normalize(p.getProperty(itemPrefix + "target"), bi == null ? null : bi.action().target());
      String buttonAsset = normalize(p.getProperty(itemPrefix + "bgAsset"), bi == null ? null : bi.buttonAssetPath());
      String buttonSelectedAsset = normalize(p.getProperty(itemPrefix + "bgSelectedAsset"), bi == null ? null : bi.buttonSelectedAssetPath());
      String buttonDisabledAsset = normalize(p.getProperty(itemPrefix + "bgDisabledAsset"), bi == null ? null : bi.buttonDisabledAssetPath());
      Double boundsX = parseOptionalDouble(p.getProperty(itemPrefix + "boundsX"), bi == null ? null : bi.boundsX(), diagnostics, sourcePath, itemPrefix + "boundsX");
      Double boundsY = parseOptionalDouble(p.getProperty(itemPrefix + "boundsY"), bi == null ? null : bi.boundsY(), diagnostics, sourcePath, itemPrefix + "boundsY");
      Double boundsWidth = parseOptionalDouble(p.getProperty(itemPrefix + "boundsWidth"), bi == null ? null : bi.boundsWidth(), diagnostics, sourcePath, itemPrefix + "boundsWidth");
      Double boundsHeight = parseOptionalDouble(p.getProperty(itemPrefix + "boundsHeight"), bi == null ? null : bi.boundsHeight(), diagnostics, sourcePath, itemPrefix + "boundsHeight");
      MenuActionSpec action = parseActionWithDiagnostics(actionRaw, targetRaw, diagnostics, sourcePath, itemPrefix + "action");
      items.add(new MenuItemSpec(
          idNorm,
          label,
          styleId,
          icon,
          enabled,
          action,
          buttonAsset,
          buttonSelectedAsset,
          buttonDisabledAsset,
          boundsX,
          boundsY,
          boundsWidth,
          boundsHeight
      ));
    }

    return new MenuScreenSpec(id, titleText, hintsText, layoutId, defaultStyleId, wrapSelection, items);
  }

  private static MenuActionSpec parseActionWithDiagnostics(
      String rawAction,
      String rawTarget,
      List<String> diagnostics,
      String sourcePath,
      String property
  ) {
    String action = rawAction == null ? "" : rawAction.trim();
    String target = normalize(rawTarget, null);
    if (!action.isEmpty() && target == null) {
      int colon = action.indexOf(':');
      if (colon > 0 && colon < action.length() - 1) {
        target = normalize(action.substring(colon + 1), null);
        action = action.substring(0, colon);
      }
    }
    MenuActionType type = MenuActionType.parse(action);
    if (type == MenuActionType.NOOP && !action.isBlank() && !isNoopAction(action)) {
      diagnostics.add("Unknown menu action '" + action + "' in " + sourcePath + " (" + property + "); falling back to noop");
    }
    return new MenuActionSpec(type, target);
  }

  private static boolean isNoopAction(String raw) {
    String value = raw.trim().toLowerCase().replace('-', '_');
    return "noop".equals(value) || "no_op".equals(value) || "none".equals(value);
  }

  private static List<String> collectItemIdsFromProperties(Properties p) {
    Set<String> ids = new LinkedHashSet<>();
    for (String key : p.stringPropertyNames()) {
      if (!key.startsWith("item.")) continue;
      int dot = key.indexOf('.', 5);
      if (dot <= 5) continue;
      String id = key.substring(5, dot).trim();
      if (!id.isEmpty()) ids.add(id);
    }
    return new ArrayList<>(ids);
  }

  private static LoadedProperties loadFirstProperties(AssetCatalog assets, List<String> diagnostics, String purpose, String... paths) {
    if (assets == null || paths == null) return null;
    for (String path : paths) {
      if (path == null || path.isBlank()) continue;
      try (InputStream in = assets.open(AssetType.SCRIPT, path)) {
        if (in == null) continue;
        Properties p = new Properties();
        p.load(in);
        return new LoadedProperties(path, p);
      } catch (Exception ex) {
        diagnostics.add("Failed to parse " + purpose + " at '" + path + "': " + simplify(ex));
      }
    }
    return null;
  }

  private static String[] menuPaths(String id) {
    return new String[] {
        "config/menu/menus/" + id + ".menu",
        "config/menu/menus/" + id + ".properties",
        "config/menu/" + id + ".menu",
        "config/menu/" + id + ".properties",
        id + ".menu"
    };
  }

  private static String[] stylePaths(String id) {
    return new String[] {
        "config/menu/styles/" + id + ".style",
        "config/menu/styles/" + id + ".properties",
        "config/menu/" + id + ".style",
        id + ".style"
    };
  }

  private static String[] layoutPaths(String id) {
    return new String[] {
        "config/menu/layouts/" + id + ".layout",
        "config/menu/layouts/" + id + ".properties",
        "config/menu/" + id + ".layout",
        id + ".layout"
    };
  }

  private static Set<String> discoverIds(AssetCatalog assets, List<String> diagnostics, String directory, String... suffixes) {
    Set<String> ids = new LinkedHashSet<>();
    if (assets == null || directory == null || directory.isBlank()) return ids;
    try {
      List<String> entries = assets.list(directory);
      if (entries == null) return ids;
      for (String entry : entries) {
        String name = normalize(entry, null);
        if (name == null) continue;
        for (String suffix : suffixes) {
          if (suffix == null || suffix.isBlank()) continue;
          if (name.endsWith(suffix) && name.length() > suffix.length()) {
            ids.add(name.substring(0, name.length() - suffix.length()));
            break;
          }
        }
      }
    } catch (Exception ex) {
      diagnostics.add("Failed to discover menu files in '" + directory + "': " + simplify(ex));
    }
    return ids;
  }

  private static List<String> parseCsv(String raw) {
    if (raw == null || raw.isBlank()) return new ArrayList<>();
    List<String> out = new ArrayList<>();
    String[] parts = raw.split(",");
    for (String part : parts) {
      String t = part == null ? "" : part.trim();
      if (!t.isEmpty()) out.add(t);
    }
    return out;
  }

  private static String normalize(String v, String def) {
    if (v == null) return def;
    String t = v.trim();
    return t.isEmpty() ? def : t;
  }

  private static double parseDouble(String raw, double def, List<String> diagnostics, String sourcePath, String key) {
    if (raw == null || raw.isBlank()) return def;
    try {
      return Double.parseDouble(raw.trim());
    } catch (Exception ex) {
      diagnostics.add("Invalid number for '" + key + "' in " + sourcePath + ": '" + raw + "' (using " + def + ")");
      return def;
    }
  }

  private static Double parseOptionalDouble(String raw, Double def, List<String> diagnostics, String sourcePath, String key) {
    if (raw == null || raw.isBlank()) return def;
    try {
      return Double.parseDouble(raw.trim());
    } catch (Exception ex) {
      diagnostics.add("Invalid number for '" + key + "' in " + sourcePath + ": '" + raw + "' (using " + def + ")");
      return def;
    }
  }

  private static Integer parseOptionalInt(String raw, Integer def, List<String> diagnostics, String sourcePath, String key) {
    if (raw == null || raw.isBlank()) return def;
    try {
      return Integer.parseInt(raw.trim());
    } catch (Exception ex) {
      diagnostics.add("Invalid integer for '" + key + "' in " + sourcePath + ": '" + raw + "' (using " + def + ")");
      return def;
    }
  }

  private static boolean parseBoolean(String raw, boolean def, List<String> diagnostics, String sourcePath, String key) {
    if (raw == null || raw.isBlank()) return def;
    String v = raw.trim().toLowerCase();
    if ("true".equals(v) || "yes".equals(v) || "1".equals(v)) return true;
    if ("false".equals(v) || "no".equals(v) || "0".equals(v)) return false;
    diagnostics.add("Invalid boolean for '" + key + "' in " + sourcePath + ": '" + raw + "' (using " + def + ")");
    return def;
  }

  private static String simplify(Exception ex) {
    if (ex == null) return "unknown error";
    String message = ex.getMessage();
    if (message == null || message.isBlank()) return ex.getClass().getSimpleName();
    return ex.getClass().getSimpleName() + ": " + message;
  }
}
