package com.jvn.fx.menu;

import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.assets.AssetType;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.io.InputStream;
import java.util.Properties;

/**
 * Theme configuration for menus (Main, Settings, Load, Save).
 * Values are loaded from game/scripts/menu.theme if present; otherwise defaults are used.
 */
public class MenuTheme {
  // Colors
  private Color backgroundColor = Color.rgb(10, 12, 18);
  private Color titleColor = Color.WHITE;
  private Color itemColor = Color.LIGHTGRAY;
  private Color itemSelectedColor = Color.YELLOW;
  private Color hintColor = Color.rgb(200, 200, 200, 0.8);
  private Color accentColor = Color.YELLOW;

  // Fonts
  private String titleFontFamily = "Arial";
  private FontWeight titleFontWeight = FontWeight.BOLD;
  private int titleFontSize = 32;

  private String itemFontFamily = "Arial";
  private FontWeight itemFontWeight = FontWeight.NORMAL;
  private int itemFontSize = 20;

  private String hintFontFamily = "Arial";
  private FontWeight hintFontWeight = FontWeight.NORMAL;
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

  public static MenuTheme defaults() { return new MenuTheme(); }

  public static MenuTheme fromAssets() {
    MenuTheme t = new MenuTheme();
    try {
      AssetCatalog cat = new AssetCatalog();
      try (InputStream in = cat.open(AssetType.SCRIPT, "menu.theme")) {
        if (in != null) {
          Properties p = new Properties();
          p.load(in);
          t.apply(p);
        }
      }
    } catch (Exception ignored) {}
    return t;
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
    titleFontWeight = parseWeight(p.getProperty("titleFontWeight"), titleFontWeight);
    titleFontSize = parseInt(p.getProperty("titleFontSize"), titleFontSize);

    itemFontFamily = p.getProperty("itemFontFamily", itemFontFamily);
    itemFontWeight = parseWeight(p.getProperty("itemFontWeight"), itemFontWeight);
    itemFontSize = parseInt(p.getProperty("itemFontSize"), itemFontSize);

    hintFontFamily = p.getProperty("hintFontFamily", hintFontFamily);
    hintFontWeight = parseWeight(p.getProperty("hintFontWeight"), hintFontWeight);
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

  private static FontWeight parseWeight(String s, FontWeight def) {
    if (s == null) return def;
    try {
      return FontWeight.valueOf(s.trim().toUpperCase());
    } catch (Exception e) {
      return def;
    }
  }

  private static Color parseColor(String s, Color def) {
    if (s == null || s.isBlank()) return def;
    String t = s.trim();
    try {
      if (t.startsWith("#")) {
        String hex = t.substring(1);
        if (hex.length() == 6) {
          int r = Integer.parseInt(hex.substring(0,2), 16);
          int g = Integer.parseInt(hex.substring(2,4), 16);
          int b = Integer.parseInt(hex.substring(4,6), 16);
          return Color.rgb(r, g, b);
        } else if (hex.length() == 8) {
          int a = Integer.parseInt(hex.substring(0,2), 16);
          int r = Integer.parseInt(hex.substring(2,4), 16);
          int g = Integer.parseInt(hex.substring(4,6), 16);
          int b = Integer.parseInt(hex.substring(6,8), 16);
          return Color.rgb(r, g, b, a / 255.0);
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
            return Color.rgb((int)r, (int)g, (int)b, a > 1 ? (a/255.0) : a);
          }
          return Color.color(r, g, b, a);
        }
      }
    } catch (Exception ignored) { }
    return def;
  }

  public Color getBackgroundColor() { return backgroundColor; }
  public Color getTitleColor() { return titleColor; }
  public Color getItemColor() { return itemColor; }
  public Color getItemSelectedColor() { return itemSelectedColor; }
  public Color getHintColor() { return hintColor; }
  public Color getAccentColor() { return accentColor; }

  public Font getTitleFont() { return Font.font(titleFontFamily, titleFontWeight, titleFontSize); }
  public Font getItemFont() { return Font.font(itemFontFamily, itemFontWeight, itemFontSize); }
  public Font getHintFont() { return Font.font(hintFontFamily, hintFontWeight, hintFontSize); }

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
}
