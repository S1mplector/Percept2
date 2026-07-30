package com.jvn.core.diagnostics;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/**
 * Configures the JavaFX Prism rendering preference before the JavaFX toolkit starts.
 *
 * <p>Set {@code -Djvn.graphics.mode=auto|hardware|software} or the
 * {@code JVN_GRAPHICS_MODE} environment variable. Hardware mode keeps the software pipeline as a
 * final fallback so a driver problem cannot make JVN unusable.</p>
 */
public final class GraphicsPipeline {
  public static final String MODE_PROPERTY = "jvn.graphics.mode";
  public static final String MODE_ENVIRONMENT = "JVN_GRAPHICS_MODE";
  static final String PRISM_ORDER_PROPERTY = "prism.order";

  public enum Mode {
    AUTO,
    HARDWARE,
    SOFTWARE;

    public static Mode parse(String value) {
      if (value == null || value.isBlank()) return AUTO;
      return switch (value.trim().toLowerCase(Locale.ROOT)) {
        case "gpu", "hardware", "accelerated", "prefer-gpu" -> HARDWARE;
        case "sw", "software", "compatibility" -> SOFTWARE;
        default -> AUTO;
      };
    }
  }

  private GraphicsPipeline() {
  }

  /**
   * Apply the requested pipeline preference. Call this before {@code Application.launch()}.
   *
   * @return the normalized requested mode
   */
  public static Mode configure() {
    Mode mode = requestedMode();
    if (System.getProperty(PRISM_ORDER_PROPERTY) != null) return mode;

    switch (mode) {
      case HARDWARE -> System.setProperty(PRISM_ORDER_PROPERTY, preferredHardwareOrder());
      case SOFTWARE -> System.setProperty(PRISM_ORDER_PROPERTY, "sw");
      case AUTO -> {
        // Leave Prism untouched so JavaFX can select its platform default.
      }
    }
    return mode;
  }

  public static Mode requestedMode() {
    String property = System.getProperty(MODE_PROPERTY);
    if (property != null && !property.isBlank()) return Mode.parse(property);
    String environment = System.getenv(MODE_ENVIRONMENT);
    if (environment != null && !environment.isBlank()) return Mode.parse(environment);
    return Mode.parse(readUserPreference());
  }

  /** A compact description suitable for logs and the runtime performance HUD. */
  public static String statusText() {
    Mode mode = requestedMode();
    String order = System.getProperty(PRISM_ORDER_PROPERTY);
    return switch (mode) {
      case HARDWARE -> "GPU preferred (" + cleanOrder(order, preferredHardwareOrder()) + ")";
      case SOFTWARE -> "Software (" + cleanOrder(order, "sw") + ")";
      case AUTO -> order == null || order.isBlank()
          ? "Auto (JavaFX)"
          : "Auto (" + order.trim() + ")";
    };
  }

  static String preferredHardwareOrder() {
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    return os.contains("win") ? "d3d,es2,sw" : "es2,sw";
  }

  private static String cleanOrder(String order, String fallback) {
    return order == null || order.isBlank() ? fallback : order.trim();
  }

  private static String readUserPreference() {
    Path file = Path.of(
        System.getProperty("user.home", "."),
        ".jvn-editor",
        "editor-preferences.properties");
    if (!Files.isRegularFile(file)) return "";
    Properties properties = new Properties();
    try (InputStream input = Files.newInputStream(file)) {
      properties.load(input);
      return properties.getProperty("graphics.mode", "");
    } catch (Exception ignored) {
      return "";
    }
  }
}
