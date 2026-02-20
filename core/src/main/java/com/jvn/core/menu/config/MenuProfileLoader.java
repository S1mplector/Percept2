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

  private MenuProfileLoader() {}

  public static MenuProfile loadFromAssets() {
    return load(new AssetCatalog());
  }

  public static MenuProfile load(AssetCatalog assets) {
    if (assets == null) return MenuProfile.defaults();
    MenuProfile defaults = MenuProfile.defaults();

    Map<String, MenuLayoutSpec> layouts = new LinkedHashMap<>();
    Map<String, MenuStyleSpec> styles = new LinkedHashMap<>();
    Map<String, MenuScreenSpec> screens = new LinkedHashMap<>();
    String defaultScreen = defaults.defaultScreenId();

    Properties registry = loadFirstProperties(assets, REGISTRY_PATHS);
    if (registry != null) {
      String configuredDefault = normalize(registry.getProperty("defaultMenu", registry.getProperty("defaultScreen")), null);
      if (configuredDefault != null) defaultScreen = configuredDefault;
    }

    Set<String> layoutIds = new LinkedHashSet<>();
    layoutIds.add("default");
    layoutIds.addAll(parseCsv(registry != null ? registry.getProperty("layouts") : null));
    layoutIds.addAll(discoverIds(assets, "config/menu/layouts", ".layout", ".properties"));
    layoutIds.addAll(discoverIds(assets, "config/menu", ".layout", ".properties"));

    Set<String> styleIds = new LinkedHashSet<>();
    styleIds.add("default");
    styleIds.addAll(parseCsv(registry != null ? registry.getProperty("styles") : null));
    styleIds.addAll(discoverIds(assets, "config/menu/styles", ".style", ".properties"));
    styleIds.addAll(discoverIds(assets, "config/menu", ".style", ".properties"));

    Set<String> menuIds = new LinkedHashSet<>();
    menuIds.add("main");
    menuIds.addAll(parseCsv(registry != null ? registry.getProperty("menus") : null));
    menuIds.addAll(discoverIds(assets, "config/menu/menus", ".menu", ".properties"));
    menuIds.addAll(discoverIds(assets, "config/menu", ".menu", ".properties"));

    for (String id : layoutIds) {
      MenuLayoutSpec spec = resolveLayout(id, assets, defaults.layouts(), layouts, new LinkedHashSet<>());
      if (spec != null) layouts.put(id, spec);
    }

    for (String id : styleIds) {
      MenuStyleSpec spec = resolveStyle(id, assets, defaults.styles(), styles, new LinkedHashSet<>());
      if (spec != null) styles.put(id, spec);
    }

    for (String id : menuIds) {
      MenuScreenSpec spec = resolveScreen(id, assets, defaults.screens(), screens, new LinkedHashSet<>());
      if (spec != null) screens.put(id, spec);
    }

    if (!screens.containsKey(defaultScreen)) {
      defaultScreen = screens.containsKey("main") ? "main" : screens.keySet().stream().findFirst().orElse("main");
    }

    return new MenuProfile(defaultScreen, screens, layouts, styles);
  }

  private static MenuLayoutSpec resolveLayout(
      String id,
      AssetCatalog assets,
      Map<String, MenuLayoutSpec> defaults,
      Map<String, MenuLayoutSpec> resolved,
      Set<String> visiting
  ) {
    String key = normalize(id, null);
    if (key == null) return resolved.getOrDefault("default", MenuProfile.defaultLayout());
    if (resolved.containsKey(key)) return resolved.get(key);
    if (!visiting.add(key)) return defaults.getOrDefault(key, MenuProfile.defaultLayout());

    MenuLayoutSpec base = defaults.getOrDefault(key, MenuProfile.defaultLayout());
    Properties p = loadFirstProperties(assets, layoutPaths(key));
    if (p != null) {
      String parent = normalize(p.getProperty("extends"), null);
      if (parent != null && !parent.equalsIgnoreCase(key)) {
        base = resolveLayout(parent, assets, defaults, resolved, visiting);
        if (base == null) base = MenuProfile.defaultLayout();
      }
      base = parseLayout(key, p, base);
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
      Set<String> visiting
  ) {
    String key = normalize(id, null);
    if (key == null) return resolved.getOrDefault("default", MenuProfile.defaultStyle());
    if (resolved.containsKey(key)) return resolved.get(key);
    if (!visiting.add(key)) return defaults.getOrDefault(key, MenuProfile.defaultStyle());

    MenuStyleSpec base = defaults.getOrDefault(key, MenuProfile.defaultStyle());
    Properties p = loadFirstProperties(assets, stylePaths(key));
    if (p != null) {
      String parent = normalize(p.getProperty("extends"), null);
      if (parent != null && !parent.equalsIgnoreCase(key)) {
        base = resolveStyle(parent, assets, defaults, resolved, visiting);
        if (base == null) base = MenuProfile.defaultStyle();
      }
      base = parseStyle(key, p, base);
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
      Set<String> visiting
  ) {
    String key = normalize(id, null);
    if (key == null) return resolved.get("main");
    if (resolved.containsKey(key)) return resolved.get(key);
    if (!visiting.add(key)) {
      return defaults.getOrDefault(key, "main".equals(key) ? MenuProfile.defaultMainScreen() : null);
    }

    MenuScreenSpec base = defaults.get(key);
    if (base == null && "main".equals(key)) base = MenuProfile.defaultMainScreen();

    Properties p = loadFirstProperties(assets, menuPaths(key));
    if (p == null && base == null) {
      visiting.remove(key);
      return null;
    }

    if (p != null) {
      String parent = normalize(p.getProperty("extends"), null);
      if (parent != null && !parent.equalsIgnoreCase(key)) {
        MenuScreenSpec parentScreen = resolveScreen(parent, assets, defaults, resolved, visiting);
        if (parentScreen != null) base = parentScreen;
      }
      if (base == null) {
        base = new MenuScreenSpec(key, null, null, "default", "default", true, List.of());
      }
      base = parseScreen(key, p, base);
    }

    visiting.remove(key);
    if (base != null) resolved.put(key, base);
    return base;
  }

  private static MenuLayoutSpec parseLayout(String id, Properties p, MenuLayoutSpec base) {
    return new MenuLayoutSpec(
        id,
        parseDouble(p.getProperty("listYStart"), base.listYStart()),
        parseDouble(p.getProperty("lineHeight"), base.lineHeight()),
        parseDouble(p.getProperty("listWidthFactor", p.getProperty("listWidth")), base.listWidthFactor()),
        normalize(p.getProperty("textAlign"), base.textAlign()),
        parseDouble(p.getProperty("hintsBottomMargin"), base.hintsBottomMargin()),
        parseOptionalDouble(p.getProperty("titleY"), base.titleY())
    );
  }

  private static MenuStyleSpec parseStyle(String id, Properties p, MenuStyleSpec base) {
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
        parseOptionalInt(p.getProperty("itemFontSize"), base.itemFontSize()),
        normalize(p.getProperty("buttonAsset"), base.buttonAssetPath()),
        normalize(p.getProperty("buttonSelectedAsset"), base.buttonSelectedAssetPath()),
        normalize(p.getProperty("buttonDisabledAsset"), base.buttonDisabledAssetPath()),
        parseOptionalDouble(p.getProperty("buttonTextPaddingX"), base.buttonTextPaddingX()),
        parseOptionalDouble(p.getProperty("buttonTextPaddingY"), base.buttonTextPaddingY())
    );
  }

  private static MenuScreenSpec parseScreen(String id, Properties p, MenuScreenSpec base) {
    String titleText = normalize(p.getProperty("titleText"), base == null ? null : base.titleText());
    String hintsText = normalize(p.getProperty("hintsText"), base == null ? null : base.hintsText());
    String layoutId = normalize(p.getProperty("layout", p.getProperty("layoutId")), base == null ? "default" : base.layoutId());
    String defaultStyleId = normalize(p.getProperty("defaultItemStyle"), base == null ? "default" : base.defaultStyleId());
    boolean wrapSelection = parseBoolean(p.getProperty("wrapSelection"), base == null || base.wrapSelection());

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
      MenuItemSpec bi = baseItems.get(idNorm);
      String label = normalize(p.getProperty("item." + idNorm + ".label"), bi == null ? null : bi.label());
      String styleId = normalize(p.getProperty("item." + idNorm + ".style"), bi == null ? defaultStyleId : bi.styleId());
      String icon = normalize(p.getProperty("item." + idNorm + ".icon"), bi == null ? null : bi.iconPath());
      boolean enabled = parseBoolean(p.getProperty("item." + idNorm + ".enabled"), bi == null || bi.enabled());
      String actionRaw = normalize(p.getProperty("item." + idNorm + ".action"), bi == null ? null : bi.action().type().name());
      String targetRaw = normalize(p.getProperty("item." + idNorm + ".target"), bi == null ? null : bi.action().target());
      String buttonAsset = normalize(p.getProperty("item." + idNorm + ".bgAsset"), bi == null ? null : bi.buttonAssetPath());
      String buttonSelectedAsset = normalize(p.getProperty("item." + idNorm + ".bgSelectedAsset"), bi == null ? null : bi.buttonSelectedAssetPath());
      String buttonDisabledAsset = normalize(p.getProperty("item." + idNorm + ".bgDisabledAsset"), bi == null ? null : bi.buttonDisabledAssetPath());
      Double boundsX = parseOptionalDouble(p.getProperty("item." + idNorm + ".boundsX"), bi == null ? null : bi.boundsX());
      Double boundsY = parseOptionalDouble(p.getProperty("item." + idNorm + ".boundsY"), bi == null ? null : bi.boundsY());
      Double boundsWidth = parseOptionalDouble(p.getProperty("item." + idNorm + ".boundsWidth"), bi == null ? null : bi.boundsWidth());
      Double boundsHeight = parseOptionalDouble(p.getProperty("item." + idNorm + ".boundsHeight"), bi == null ? null : bi.boundsHeight());
      MenuActionSpec action = MenuActionSpec.parse(actionRaw, targetRaw);
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

  private static Properties loadFirstProperties(AssetCatalog assets, String... paths) {
    if (assets == null || paths == null) return null;
    for (String path : paths) {
      if (path == null || path.isBlank()) continue;
      try (InputStream in = assets.open(AssetType.SCRIPT, path)) {
        if (in == null) continue;
        Properties p = new Properties();
        p.load(in);
        return p;
      } catch (Exception ignored) {
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

  private static Set<String> discoverIds(AssetCatalog assets, String directory, String... suffixes) {
    Set<String> ids = new LinkedHashSet<>();
    if (assets == null || directory == null || directory.isBlank()) return ids;
    try {
      List<String> entries = assets.list(directory);
      if (entries == null) return ids;
      for (String e : entries) {
        String name = normalize(e, null);
        if (name == null) continue;
        for (String suffix : suffixes) {
          if (suffix == null || suffix.isBlank()) continue;
          if (name.endsWith(suffix) && name.length() > suffix.length()) {
            ids.add(name.substring(0, name.length() - suffix.length()));
            break;
          }
        }
      }
    } catch (Exception ignored) {
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

  private static double parseDouble(String raw, double def) {
    if (raw == null || raw.isBlank()) return def;
    try {
      return Double.parseDouble(raw.trim());
    } catch (Exception ignored) {
      return def;
    }
  }

  private static Double parseOptionalDouble(String raw, Double def) {
    if (raw == null || raw.isBlank()) return def;
    try {
      return Double.parseDouble(raw.trim());
    } catch (Exception ignored) {
      return def;
    }
  }

  private static Integer parseOptionalInt(String raw, Integer def) {
    if (raw == null || raw.isBlank()) return def;
    try {
      return Integer.parseInt(raw.trim());
    } catch (Exception ignored) {
      return def;
    }
  }

  private static boolean parseBoolean(String raw, boolean def) {
    if (raw == null || raw.isBlank()) return def;
    String v = raw.trim().toLowerCase();
    if ("true".equals(v) || "yes".equals(v) || "1".equals(v)) return true;
    if ("false".equals(v) || "no".equals(v) || "0".equals(v)) return false;
    return def;
  }
}
