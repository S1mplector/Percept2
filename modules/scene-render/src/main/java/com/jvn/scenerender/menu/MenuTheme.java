package com.jvn.scenerender.menu;

import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.assets.AssetType;
import com.jvn.core.menu.config.MenuActionType;
import com.jvn.core.menu.config.MenuItemSpec;
import com.jvn.core.menu.config.MenuLayoutSpec;
import com.jvn.core.menu.config.MenuProfile;
import com.jvn.core.menu.config.MenuProfileLoader;
import com.jvn.core.menu.config.MenuScreenSpec;
import com.jvn.core.menu.config.MenuStyleSpec;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Theme configuration for menus (Main, Settings, Load, Save).
 * Values are loaded from config/menu/menu.theme when available, with
 * legacy fallbacks for older projects.
 */
public class MenuTheme {

  /** Normalised RGBA colour (each channel in [0.0, 1.0]); replaces the old FX paint-based Color type. */
  public record ColorSpec(double r, double g, double b, double a) {
    static ColorSpec rgb255(int r, int g, int b) { return new ColorSpec(r / 255.0, g / 255.0, b / 255.0, 1.0); }
    static ColorSpec rgb255(int r, int g, int b, double a) { return new ColorSpec(r / 255.0, g / 255.0, b / 255.0, a); }
  }

  /** Font description consumed directly by Blitter2D.setFont; replaces the old FX text-based Font type. */
  public record FontSpec(String family, double size, boolean bold) {}

  // Colors
  private ColorSpec backgroundColor = ColorSpec.rgb255(10, 12, 18);
  private ColorSpec titleColor = new ColorSpec(1.0, 1.0, 1.0, 1.0);
  private ColorSpec itemColor = ColorSpec.rgb255(211, 211, 211);
  private ColorSpec itemSelectedColor = new ColorSpec(1.0, 1.0, 0.0, 1.0);
  private ColorSpec hintColor = ColorSpec.rgb255(200, 200, 200, 0.8);
  private ColorSpec accentColor = new ColorSpec(1.0, 1.0, 0.0, 1.0);

  // Fonts
  private String titleFontFamily = "Arial";
  private boolean titleFontBold = true;
  private int titleFontSize = 32;

  private String itemFontFamily = "Arial";
  private boolean itemFontBold = false;
  private int itemFontSize = 20;

  private String hintFontFamily = "Arial";
  private boolean hintFontBold = false;
  private int hintFontSize = 14;

  // Layout and formatting
  private double titleY = 60.0; // if <1 treat as fraction of height
  private double listYStart = 0.35; // fraction of height
  private double lineHeight = 40.0; // pixels
  // Item prefixes
  private String itemPrefix = "  ";
  private String itemSelectedPrefix = "> ";
  // Hints (main menu only)
  private String mainHintsText = null;

  // Labels
  private String titleText = null; // default: Localization t("app.title")
  private String labelNewGame = null; // default localization
  private String labelLoad = null;
  private String labelSettings = null;
  private String labelQuit = null;

  // Title screen assets
  private String backgroundImagePath = null; // path to background image
  private String logoImagePath = null; // path to logo/title image
  private String bgmPath = null; // path to background music
  private double logoX = 0.5; // logo X position (fraction of width, 0.5 = centered)
  private double logoY = 0.15; // logo Y position (fraction of height)
  private double logoScale = 1.0; // logo scale factor
  private double bgmVolume = 0.7; // title screen BGM volume
  private boolean logoShadow = true; // draw shadow behind logo

  // In-game menu overlay assets
  private ColorSpec gameplayDimColor = new ColorSpec(0.0, 0.0, 0.0, 0.50);
  private String gameplayPanelImagePath = null;
  private String gameplayLogoImagePath = null;
  private double gameplayPanelScale = 1.0;
  private double gameplayLogoScale = 0.25;

  public static MenuTheme defaults() { return new MenuTheme(); }

  public static MenuTheme fromAssets() {
    MenuTheme t = fromMenuProfile(MenuProfileLoader.loadFromAssets());
    AssetCatalog cat = new AssetCatalog();
    String[] candidates = new String[] {
        "config/menu/theme/menu.theme",
        "config/menu/menu.theme",
        "config/menu.theme",
        "menu.theme"
    };
    for (String candidate : candidates) {
      try (InputStream in = cat.open(AssetType.SCRIPT, candidate)) {
        if (in == null) continue;
        Properties p = new Properties();
        p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        t.apply(p);
        break;
      } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
            }
    }
    return t;
  }

  private static MenuTheme fromMenuProfile(MenuProfile profile) {
    MenuTheme out = new MenuTheme();
    if (profile == null) return out;

    MenuScreenSpec screen = profile.screen(profile.defaultScreenId());
    if (screen == null) return out;
    MenuLayoutSpec layout = profile.layout(screen.layoutId());
    MenuStyleSpec style = profile.style(screen.defaultStyleId());

    if (layout != null) {
      out.listYStart = layout.listYStart();
      out.lineHeight = layout.lineHeight();
      if (layout.titleY() != null) out.titleY = layout.titleY();
    }

    if (style != null) {
      out.itemColor = parseColor(style.itemColor(), out.itemColor);
      out.itemSelectedColor = parseColor(style.itemSelectedColor(), out.itemSelectedColor);
      out.itemPrefix = valueOr(style.itemPrefix(), out.itemPrefix);
      out.itemSelectedPrefix = valueOr(style.itemSelectedPrefix(), out.itemSelectedPrefix);
      out.itemFontFamily = valueOr(style.itemFontFamily(), out.itemFontFamily);
      out.itemFontBold = parseBold(style.itemFontWeight(), out.itemFontBold);
      if (style.itemFontSize() != null && style.itemFontSize() > 0) out.itemFontSize = style.itemFontSize();
    }

    out.titleText = valueOr(screen.titleText(), out.titleText);
    out.mainHintsText = valueOr(screen.hintsText(), out.mainHintsText);
    for (MenuItemSpec item : screen.items()) {
      if (item == null || item.label() == null || item.label().isBlank()) continue;
      if (item.action().type() == MenuActionType.NEW_GAME) out.labelNewGame = item.label();
      if (item.action().type() == MenuActionType.LOAD_MENU) out.labelLoad = item.label();
      if (item.action().type() == MenuActionType.SETTINGS_MENU) out.labelSettings = item.label();
      if (item.action().type() == MenuActionType.QUIT) out.labelQuit = item.label();
    }
    return out;
  }

  private static String valueOr(String value, String fallback) {
    if (value == null || value.isBlank()) return fallback;
    return value;
  }

  public void apply(Properties p) {
    if (p == null) return;
    // Colors
    backgroundColor = parseColor(p.getProperty("backgroundColor"), backgroundColor);
    titleColor = parseColor(p.getProperty("titleColor"), titleColor);
    itemColor = parseColor(p.getProperty("itemColor"), itemColor);
    itemSelectedColor = parseColor(p.getProperty("itemSelectedColor"), itemSelectedColor);
    hintColor = parseColor(p.getProperty("hintColor"), hintColor);
    accentColor = parseColor(p.getProperty("accentColor"), accentColor);

    // Fonts
    titleFontFamily = p.getProperty("titleFontFamily", titleFontFamily);
    titleFontBold = parseBold(p.getProperty("titleFontWeight"), titleFontBold);
    titleFontSize = parseInt(p.getProperty("titleFontSize"), titleFontSize);

    itemFontFamily = p.getProperty("itemFontFamily", itemFontFamily);
    itemFontBold = parseBold(p.getProperty("itemFontWeight"), itemFontBold);
    itemFontSize = parseInt(p.getProperty("itemFontSize"), itemFontSize);

    hintFontFamily = p.getProperty("hintFontFamily", hintFontFamily);
    hintFontBold = parseBold(p.getProperty("hintFontWeight"), hintFontBold);
    hintFontSize = parseInt(p.getProperty("hintFontSize"), hintFontSize);

    // Layout and formatting
    titleY = parseDouble(p.getProperty("titleY"), titleY);
    listYStart = parseDouble(p.getProperty("listYStart"), listYStart);
    lineHeight = parseDouble(p.getProperty("lineHeight"), lineHeight);
    itemPrefix = p.getProperty("itemPrefix", itemPrefix);
    itemSelectedPrefix = p.getProperty("itemSelectedPrefix", itemSelectedPrefix);
    mainHintsText = emptyToNull(p.getProperty("hintsText"));

    // Labels
    titleText = emptyToNull(p.getProperty("titleText"));
    labelNewGame = emptyToNull(p.getProperty("label.new"));
    labelLoad = emptyToNull(p.getProperty("label.load"));
    labelSettings = emptyToNull(p.getProperty("label.settings"));
    labelQuit = emptyToNull(p.getProperty("label.quit"));

    // Title screen assets
    backgroundImagePath = emptyToNull(p.getProperty("backgroundImage"));
    logoImagePath = emptyToNull(p.getProperty("logoImage"));
    bgmPath = emptyToNull(p.getProperty("bgm"));
    logoX = parseDouble(p.getProperty("logoX"), logoX);
    logoY = parseDouble(p.getProperty("logoY"), logoY);
    logoScale = parseDouble(p.getProperty("logoScale"), logoScale);
    bgmVolume = parseDouble(p.getProperty("bgmVolume"), bgmVolume);
    logoShadow = parseBool(p.getProperty("logoShadow"), logoShadow);

    gameplayDimColor = parseColor(p.getProperty("gameplayDimColor"), gameplayDimColor);
    gameplayPanelImagePath = emptyToNull(p.getProperty("gameplayPanelImage"));
    gameplayLogoImagePath = emptyToNull(p.getProperty("gameplayLogoImage"));
    gameplayPanelScale = Math.max(0.05, parseDouble(p.getProperty("gameplayPanelScale"), gameplayPanelScale));
    gameplayLogoScale = Math.max(0.05, parseDouble(p.getProperty("gameplayLogoScale"), gameplayLogoScale));
  }

  private static boolean parseBool(String s, boolean def) {
    if (s == null || s.isBlank()) return def;
    String t = s.trim().toLowerCase();
    if ("true".equals(t) || "yes".equals(t) || "1".equals(t)) return true;
    if ("false".equals(t) || "no".equals(t) || "0".equals(t)) return false;
    return def;
  }

  private static String emptyToNull(String s) { return (s == null || s.trim().isEmpty()) ? null : s; }

  private static int parseInt(String s, int def) {
    try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
  }

  private static double parseDouble(String s, double def) {
    try { return Double.parseDouble(s.trim()); } catch (Exception e) { return def; }
  }

  /**
   * Reduces a JavaFX-style font-weight name (e.g. "BOLD", "SEMI_BOLD", "BLACK") to the
   * bold/non-bold distinction that Blitter2D.setFont actually exposes.
   */
  private static boolean parseBold(String s, boolean def) {
    if (s == null || s.isBlank()) return def;
    String t = s.trim().toUpperCase();
    if ("NORMAL".equals(t) || "THIN".equals(t) || "LIGHT".equals(t) || "MEDIUM".equals(t)) return false;
    if (t.contains("BOLD") || "BLACK".equals(t) || "EXTRA_BOLD".equals(t) || "HEAVY".equals(t)) return true;
    return def;
  }

  private static ColorSpec parseColor(String s, ColorSpec def) {
    if (s == null || s.isBlank()) return def;
    String t = s.trim();
    try {
      if (t.startsWith("#")) {
        String hex = t.substring(1);
        if (hex.length() == 6) {
          int r = Integer.parseInt(hex.substring(0,2), 16);
          int g = Integer.parseInt(hex.substring(2,4), 16);
          int b = Integer.parseInt(hex.substring(4,6), 16);
          return ColorSpec.rgb255(r, g, b);
        } else if (hex.length() == 8) {
          int a = Integer.parseInt(hex.substring(0,2), 16);
          int r = Integer.parseInt(hex.substring(2,4), 16);
          int g = Integer.parseInt(hex.substring(4,6), 16);
          int b = Integer.parseInt(hex.substring(6,8), 16);
          return ColorSpec.rgb255(r, g, b, a / 255.0);
        }
      } else if (t.toLowerCase().startsWith("rgb")) {
        // rgb(r,g,b) or rgba(r,g,b,a) in 0..1 floats
        int lp = t.indexOf('(');
        int rp = t.indexOf(')');
        if (lp >= 0 && rp > lp) {
          String[] parts = t.substring(lp+1, rp).split(",");
          double r = Double.parseDouble(parts[0].trim());
          double g = Double.parseDouble(parts[1].trim());
          double b = Double.parseDouble(parts[2].trim());
          double a = parts.length >= 4 ? Double.parseDouble(parts[3].trim()) : 1.0;
          if (r > 1 || g > 1 || b > 1 || a > 1) {
            // interpret as 0..255 if >1
            return ColorSpec.rgb255((int) r, (int) g, (int) b, a > 1 ? (a / 255.0) : a);
          }
          return new ColorSpec(r, g, b, a);
        }
      }
    } catch (Exception ignored) {
      // reason: malformed color string in theme properties; return default color
    }
    return def;
  }

  public ColorSpec getBackgroundColor() { return backgroundColor; }
  public ColorSpec getTitleColor() { return titleColor; }
  public ColorSpec getItemColor() { return itemColor; }
  public ColorSpec getItemSelectedColor() { return itemSelectedColor; }
  public ColorSpec getHintColor() { return hintColor; }
  public ColorSpec getAccentColor() { return accentColor; }

  public FontSpec getTitleFontSpec() { return new FontSpec(titleFontFamily, titleFontSize, titleFontBold); }
  public FontSpec getItemFontSpec() { return new FontSpec(itemFontFamily, itemFontSize, itemFontBold); }
  public FontSpec getHintFontSpec() { return new FontSpec(hintFontFamily, hintFontSize, hintFontBold); }

  public double getTitleY() { return titleY; }
  public double getListYStart() { return listYStart; }
  public double getLineHeight() { return lineHeight; }
  public String getItemPrefix() { return itemPrefix; }
  public String getItemSelectedPrefix() { return itemSelectedPrefix; }
  public String getMainHintsText() { return mainHintsText; }

  public String getTitleText() { return titleText; }
  public String getLabelNewGame() { return labelNewGame; }
  public String getLabelLoad() { return labelLoad; }
  public String getLabelSettings() { return labelSettings; }
  public String getLabelQuit() { return labelQuit; }

  // Title screen asset getters
  public String getBackgroundImagePath() { return backgroundImagePath; }
  public String getLogoImagePath() { return logoImagePath; }
  public String getBgmPath() { return bgmPath; }
  public double getLogoX() { return logoX; }
  public double getLogoY() { return logoY; }
  public double getLogoScale() { return logoScale; }
  public double getBgmVolume() { return bgmVolume; }
  public boolean isLogoShadow() { return logoShadow; }
  public ColorSpec getGameplayDimColor() { return gameplayDimColor; }
  public String getGameplayPanelImagePath() { return gameplayPanelImagePath; }
  public String getGameplayLogoImagePath() { return gameplayLogoImagePath; }
  public double getGameplayPanelScale() { return gameplayPanelScale; }
  public double getGameplayLogoScale() { return gameplayLogoScale; }
}
