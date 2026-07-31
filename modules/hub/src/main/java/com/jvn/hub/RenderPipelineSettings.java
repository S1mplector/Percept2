package com.jvn.hub;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/** Shared persistence and launch rules for the Engine Hub's Render Pipeline menu. */
final class RenderPipelineSettings {
  static final String GRAPHICS_MODE_KEY = "graphics.mode";
  static final String GRAPHICS_MODE_PROPERTY = "jvn.graphics.mode";
  static final String GRAPHICS_MODE_ENVIRONMENT = "JVN_GRAPHICS_MODE";

  enum Mode {
    AUTO(
        "auto",
        "Adaptive Selection",
        "JavaFX selects the best available renderer for the machine."),
    HARDWARE(
        "hardware",
        "GPU Preferred",
        "Prefer the native GPU pipeline while retaining a software fallback."),
    SOFTWARE(
        "software",
        "Software Compatibility",
        "Use the CPU renderer for driver troubleshooting and maximum compatibility.");

    private final String id;
    private final String displayName;
    private final String description;

    Mode(String id, String displayName, String description) {
      this.id = id;
      this.displayName = displayName;
      this.description = description;
    }

    String id() {
      return id;
    }

    String displayName() {
      return displayName;
    }

    String description() {
      return description;
    }

    String backendOrder(String operatingSystem) {
      String os = operatingSystem == null ? "" : operatingSystem.toLowerCase(Locale.ROOT);
      return switch (this) {
        case AUTO -> "JavaFX platform default";
        case HARDWARE -> os.contains("win")
            ? "Direct3D → OpenGL ES2 → software"
            : "OpenGL ES2 → software";
        case SOFTWARE -> "Software renderer only";
      };
    }

    static Mode parse(String value) {
      if (value == null || value.isBlank()) return AUTO;
      return switch (value.trim().toLowerCase(Locale.ROOT)) {
        case "gpu", "hardware", "accelerated", "prefer-gpu" -> HARDWARE;
        case "sw", "software", "compatibility" -> SOFTWARE;
        default -> AUTO;
      };
    }
  }

  private RenderPipelineSettings() {}

  static Path defaultPreferencesFile() {
    return Path.of(
        System.getProperty("user.home", "."),
        ".jvn-editor",
        "editor-preferences.properties");
  }

  static Mode load(Path preferencesFile) {
    if (preferencesFile == null || !Files.isRegularFile(preferencesFile)) return Mode.AUTO;
    Properties properties = new Properties();
    try (InputStream input = Files.newInputStream(preferencesFile)) {
      properties.load(input);
      return Mode.parse(properties.getProperty(GRAPHICS_MODE_KEY));
    } catch (IOException | IllegalArgumentException ignored) {
      return Mode.AUTO;
    }
  }

  static void save(Path preferencesFile, Mode requestedMode) throws IOException {
    if (preferencesFile == null) throw new IOException("Graphics preferences path is unavailable.");
    Mode mode = requestedMode == null ? Mode.AUTO : requestedMode;
    Properties properties = new Properties();
    if (Files.isRegularFile(preferencesFile)) {
      try (InputStream input = Files.newInputStream(preferencesFile)) {
        properties.load(input);
      } catch (IllegalArgumentException ignored) {
        // Preserve forward progress if a hand-edited preferences file contains malformed escapes.
      }
    }
    properties.setProperty(GRAPHICS_MODE_KEY, mode.id());

    Path parent = preferencesFile.toAbsolutePath().getParent();
    if (parent == null) throw new IOException("Graphics preferences folder is unavailable.");
    Files.createDirectories(parent);
    Path temporary = Files.createTempFile(parent, "editor-preferences-", ".tmp");
    try {
      try (OutputStream output = Files.newOutputStream(temporary)) {
        properties.store(output, "JVN Editor Preferences");
      }
      try {
        Files.move(
            temporary,
            preferencesFile,
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException ignored) {
        Files.move(temporary, preferencesFile, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  static boolean isManagedGradleTask(String task) {
    if (task == null) return false;
    return switch (task.trim()) {
      case ":editor:run", ":editor:runLauncher", ":runtime:run" -> true;
      default -> false;
    };
  }

  static boolean isManagedLaunchCommand(List<String> command) {
    if (command == null) return false;
    for (String token : command) {
      if (token == null) continue;
      String normalized = token.replace('\\', '/').toLowerCase(Locale.ROOT);
      if (normalized.endsWith("/scripts/launch-app.sh")
          || isManagedGradleTask(token)) {
        return true;
      }
    }
    return false;
  }

  static boolean applyLaunchEnvironment(
      Map<String, String> environment,
      List<String> command,
      Mode requestedMode) {
    if (environment == null || !isManagedLaunchCommand(command)) return false;
    Mode mode = requestedMode == null ? Mode.AUTO : requestedMode;
    environment.put(GRAPHICS_MODE_ENVIRONMENT, mode.id());
    return true;
  }
}
