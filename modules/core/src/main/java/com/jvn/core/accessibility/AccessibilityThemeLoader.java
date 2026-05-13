package com.jvn.core.accessibility;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Loads an accessibility theme properties file from the classpath and exposes
 * individual overrides for dialogue, choice, and name-box colours and fonts.
 *
 * <p>Callers (VnRenderer, MenuRenderer) read the properties via the getter
 * methods and apply them when constructing or rendering UI elements. The loader
 * is intentionally passive — it does not mutate any shared state.</p>
 *
 * <p>Built-in theme names: {@code "highcontrast"}, {@code "opendyslexic"}.
 * {@code "none"} or any unrecognised value returns an empty/default instance.</p>
 */
public final class AccessibilityThemeLoader {

  private static final Logger log = LoggerFactory.getLogger(AccessibilityThemeLoader.class);
  private static final String THEME_RESOURCE_BASE =
      "/com/jvn/core/ui/themes/";

  private final Properties props;
  private final String themeName;

  private AccessibilityThemeLoader(String themeName, Properties props) {
    this.themeName = themeName;
    this.props = props;
  }

  /**
   * Load a theme by name. Returns a no-op instance for {@code "none"} or
   * unknown names.
   */
  public static AccessibilityThemeLoader load(String themeName) {
    if (themeName == null || themeName.isBlank() || "none".equalsIgnoreCase(themeName)) {
      return new AccessibilityThemeLoader("none", new Properties());
    }
    String resource = THEME_RESOURCE_BASE + themeName.toLowerCase(java.util.Locale.ROOT) + ".properties";
    try (InputStream in = AccessibilityThemeLoader.class.getResourceAsStream(resource)) {
      if (in == null) {
        log.warn("AccessibilityThemeLoader: theme resource not found: {}", resource);
        return new AccessibilityThemeLoader("none", new Properties());
      }
      Properties p = new Properties();
      p.load(in);
      log.debug("AccessibilityThemeLoader: loaded theme '{}'", themeName);
      return new AccessibilityThemeLoader(themeName, p);
    } catch (IOException e) {
      log.warn("AccessibilityThemeLoader: failed to load theme '{}': {}", themeName, e.getMessage());
      return new AccessibilityThemeLoader("none", new Properties());
    }
  }

  /** {@code true} if any overrides are active (i.e. theme is not {@code "none"}). */
  public boolean isActive() {
    return !props.isEmpty();
  }

  public String getThemeName() { return themeName; }

  // ── Dialogue ────────────────────────────────────────────────────────────────

  public String dialogueTextColor(String fallback)     { return get("dialogue.text.color", fallback); }
  public String dialogueTextboxColor(String fallback)  { return get("dialogue.textbox.color", fallback); }
  public Double dialogueTextboxOpacity(Double fallback){ return getDouble("dialogue.textbox.opacity", fallback); }
  public String dialogueTextFontFamily(String fallback){ return get("dialogue.text.font.family", fallback); }
  public String dialogueTextFontWeight(String fallback){ return get("dialogue.text.font.weight", fallback); }

  // ── Name box ────────────────────────────────────────────────────────────────

  public String nameBoxColor(String fallback)          { return get("name.box.color", fallback); }
  public Double nameBoxOpacity(Double fallback)        { return getDouble("name.box.opacity", fallback); }
  public String nameTextColor(String fallback)         { return get("name.text.color", fallback); }
  public String nameTextFontFamily(String fallback)    { return get("name.text.font.family", fallback); }
  public String nameTextFontWeight(String fallback)    { return get("name.text.font.weight", fallback); }

  // ── Choices ─────────────────────────────────────────────────────────────────

  public String choiceBackgroundColor(String fallback) { return get("choice.background.color", fallback); }
  public String choiceTextColor(String fallback)       { return get("choice.text.color", fallback); }
  public String choiceHoverColor(String fallback)      { return get("choice.hover.color", fallback); }
  public String choiceHoverTextColor(String fallback)  { return get("choice.hover.text.color", fallback); }
  public String choiceSelectedColor(String fallback)   { return get("choice.selected.color", fallback); }
  public String choiceSelectedTextColor(String fallback){ return get("choice.selected.text.color", fallback); }
  public String choiceBorderColor(String fallback)     { return get("choice.border.color", fallback); }
  public String choiceHoverBorderColor(String fallback){ return get("choice.hover.border.color", fallback); }
  public String choiceSelectedBorderColor(String fallback){ return get("choice.selected.border.color", fallback); }
  public Double choiceBorderWidth(Double fallback)     { return getDouble("choice.border.width", fallback); }
  public Double choiceCornerRadius(Double fallback)    { return getDouble("choice.corner.radius", fallback); }
  public String choiceFontFamily(String fallback)      { return get("choice.font.family", fallback); }
  public String choiceFontWeight(String fallback)      { return get("choice.font.weight", fallback); }

  // ── NVL ─────────────────────────────────────────────────────────────────────

  public String nvlPanelColor(String fallback)         { return get("nvl.panel.color", fallback); }
  public Double nvlPanelOpacity(Double fallback)       { return getDouble("nvl.panel.opacity", fallback); }
  public String nvlTextColor(String fallback)          { return get("nvl.text.color", fallback); }
  public String nvlSpeakerTextColor(String fallback)   { return get("nvl.speaker.text.color", fallback); }

  // ── Bubble ──────────────────────────────────────────────────────────────────

  public String bubbleColor(String fallback)           { return get("bubble.color", fallback); }
  public Double bubbleOpacity(Double fallback)         { return getDouble("bubble.opacity", fallback); }
  public String bubbleTextColor(String fallback)       { return get("bubble.text.color", fallback); }
  public String bubbleSpeakerTextColor(String fallback){ return get("bubble.speaker.text.color", fallback); }
  public String bubbleBorderColor(String fallback)     { return get("bubble.border.color", fallback); }
  public Double bubbleBorderWidth(Double fallback)     { return getDouble("bubble.border.width", fallback); }

  // ── Helpers ─────────────────────────────────────────────────────────────────

  private String get(String key, String fallback) {
    String v = props.getProperty(key);
    return (v != null && !v.isBlank()) ? v.trim() : fallback;
  }

  private Double getDouble(String key, Double fallback) {
    String v = props.getProperty(key);
    if (v == null || v.isBlank()) return fallback;
    try {
      return Double.parseDouble(v.trim());
    } catch (NumberFormatException e) {
      log.trace("AccessibilityThemeLoader: bad double for '{}': {}", key, v);
      return fallback;
    }
  }
}
