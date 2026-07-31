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
  static final String DISABLE_GLX_RECOVERY_ENVIRONMENT = "JVN_DISABLE_GLX_FALLBACK";

  private static final String VSYNC_KEY = "render.vsync";
  private static final String DIRTY_REGIONS_KEY = "render.dirtyRegions";
  private static final String OCCLUSION_CULLING_KEY = "render.occlusionCulling";
  private static final String SHAPE_CACHE_KEY = "render.shapeCache";
  private static final String VERBOSE_KEY = "diagnostics.verbose";
  private static final String SHOW_DIRTY_REGIONS_KEY = "diagnostics.showDirtyRegions";
  private static final String SHOW_OVERDRAW_KEY = "diagnostics.showOverdraw";
  private static final String PRINT_RENDER_GRAPH_KEY = "diagnostics.printRenderGraph";
  private static final String LINUX_GLX_RECOVERY_KEY = "linux.glxRecovery";

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

  enum ShapeCache {
    COMPLEX("complex", "Complex Shapes (Recommended)"),
    ALL("all", "All Shapes"),
    OFF("false", "Disabled");

    private final String id;
    private final String displayName;

    ShapeCache(String id, String displayName) {
      this.id = id;
      this.displayName = displayName;
    }

    String id() {
      return id;
    }

    String displayName() {
      return displayName;
    }

    static ShapeCache parse(String value) {
      if (value == null || value.isBlank()) return COMPLEX;
      return switch (value.trim().toLowerCase(Locale.ROOT)) {
        case "all", "true" -> ALL;
        case "off", "false", "none", "disabled" -> OFF;
        default -> COMPLEX;
      };
    }
  }

  record Options(
      boolean vsync,
      boolean dirtyRegions,
      boolean occlusionCulling,
      ShapeCache shapeCache,
      boolean verbose,
      boolean showDirtyRegions,
      boolean showOverdraw,
      boolean printRenderGraph,
      boolean linuxGlxRecovery) {

    Options {
      shapeCache = shapeCache == null ? ShapeCache.COMPLEX : shapeCache;
      // Prism builds its printable render tree from dirty-region roots.
      if (printRenderGraph) dirtyRegions = true;
    }

    static Options defaults() {
      return new Options(true, true, true, ShapeCache.COMPLEX, false, false, false, false, true);
    }

    Options withVsync(boolean value) {
      return new Options(value, dirtyRegions, occlusionCulling, shapeCache,
          verbose, showDirtyRegions, showOverdraw, printRenderGraph, linuxGlxRecovery);
    }

    Options withDirtyRegions(boolean value) {
      return new Options(vsync, value, occlusionCulling, shapeCache,
          verbose, showDirtyRegions, showOverdraw, printRenderGraph, linuxGlxRecovery);
    }

    Options withOcclusionCulling(boolean value) {
      return new Options(vsync, dirtyRegions, value, shapeCache,
          verbose, showDirtyRegions, showOverdraw, printRenderGraph, linuxGlxRecovery);
    }

    Options withShapeCache(ShapeCache value) {
      return new Options(vsync, dirtyRegions, occlusionCulling, value,
          verbose, showDirtyRegions, showOverdraw, printRenderGraph, linuxGlxRecovery);
    }

    Options withVerbose(boolean value) {
      return new Options(vsync, dirtyRegions, occlusionCulling, shapeCache,
          value, showDirtyRegions, showOverdraw, printRenderGraph, linuxGlxRecovery);
    }

    Options withShowDirtyRegions(boolean value) {
      return new Options(vsync, dirtyRegions, occlusionCulling, shapeCache,
          verbose, value, showOverdraw, printRenderGraph, linuxGlxRecovery);
    }

    Options withShowOverdraw(boolean value) {
      return new Options(vsync, dirtyRegions, occlusionCulling, shapeCache,
          verbose, showDirtyRegions, value, printRenderGraph, linuxGlxRecovery);
    }

    Options withPrintRenderGraph(boolean value) {
      return new Options(vsync, dirtyRegions, occlusionCulling, shapeCache,
          verbose, showDirtyRegions, showOverdraw, value, linuxGlxRecovery);
    }

    Options withLinuxGlxRecovery(boolean value) {
      return new Options(vsync, dirtyRegions, occlusionCulling, shapeCache,
          verbose, showDirtyRegions, showOverdraw, printRenderGraph, value);
    }

    boolean diagnosticsEnabled() {
      return verbose || showDirtyRegions || showOverdraw || printRenderGraph;
    }
  }

  private RenderPipelineSettings() {}

  static Path defaultPreferencesFile() {
    return Path.of(
        System.getProperty("user.home", "."),
        ".jvn-editor",
        "editor-preferences.properties");
  }

  static Path defaultTuningFile() {
    return Path.of(
        System.getProperty("user.home", "."),
        ".jvn-editor",
        "render-pipeline.properties");
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

    writeProperties(preferencesFile, properties, "JVN Editor Preferences");
  }

  static Options loadOptions(Path tuningFile) {
    Options defaults = Options.defaults();
    if (tuningFile == null || !Files.isRegularFile(tuningFile)) return defaults;
    Properties properties = new Properties();
    try (InputStream input = Files.newInputStream(tuningFile)) {
      properties.load(input);
      return new Options(
          readBoolean(properties, VSYNC_KEY, defaults.vsync()),
          readBoolean(properties, DIRTY_REGIONS_KEY, defaults.dirtyRegions()),
          readBoolean(properties, OCCLUSION_CULLING_KEY, defaults.occlusionCulling()),
          ShapeCache.parse(properties.getProperty(SHAPE_CACHE_KEY)),
          readBoolean(properties, VERBOSE_KEY, defaults.verbose()),
          readBoolean(properties, SHOW_DIRTY_REGIONS_KEY, defaults.showDirtyRegions()),
          readBoolean(properties, SHOW_OVERDRAW_KEY, defaults.showOverdraw()),
          readBoolean(properties, PRINT_RENDER_GRAPH_KEY, defaults.printRenderGraph()),
          readBoolean(properties, LINUX_GLX_RECOVERY_KEY, defaults.linuxGlxRecovery()));
    } catch (IOException | IllegalArgumentException ignored) {
      return defaults;
    }
  }

  static void saveOptions(Path tuningFile, Options requestedOptions) throws IOException {
    if (tuningFile == null) throw new IOException("Render Pipeline settings path is unavailable.");
    Options options = requestedOptions == null ? Options.defaults() : requestedOptions;
    Properties properties = new Properties();
    properties.setProperty(VSYNC_KEY, Boolean.toString(options.vsync()));
    properties.setProperty(DIRTY_REGIONS_KEY, Boolean.toString(options.dirtyRegions()));
    properties.setProperty(OCCLUSION_CULLING_KEY, Boolean.toString(options.occlusionCulling()));
    properties.setProperty(SHAPE_CACHE_KEY, options.shapeCache().id());
    properties.setProperty(VERBOSE_KEY, Boolean.toString(options.verbose()));
    properties.setProperty(SHOW_DIRTY_REGIONS_KEY, Boolean.toString(options.showDirtyRegions()));
    properties.setProperty(SHOW_OVERDRAW_KEY, Boolean.toString(options.showOverdraw()));
    properties.setProperty(PRINT_RENDER_GRAPH_KEY, Boolean.toString(options.printRenderGraph()));
    properties.setProperty(LINUX_GLX_RECOVERY_KEY, Boolean.toString(options.linuxGlxRecovery()));
    writeProperties(tuningFile, properties, "JVN Render Pipeline Settings");
  }

  private static boolean readBoolean(Properties properties, String key, boolean fallback) {
    String value = properties.getProperty(key);
    if (value == null || value.isBlank()) return fallback;
    return switch (value.trim().toLowerCase(Locale.ROOT)) {
      case "true", "1", "yes", "on", "enabled" -> true;
      case "false", "0", "no", "off", "disabled" -> false;
      default -> fallback;
    };
  }

  private static void writeProperties(Path target, Properties properties, String comment)
      throws IOException {
    Path parent = target.toAbsolutePath().getParent();
    if (parent == null) throw new IOException("Graphics preferences folder is unavailable.");
    Files.createDirectories(parent);
    Path temporary = Files.createTempFile(parent, "render-preferences-", ".tmp");
    try {
      try (OutputStream output = Files.newOutputStream(temporary)) {
        properties.store(output, comment);
      }
      try {
        Files.move(
            temporary,
            target,
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException ignored) {
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
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
      Mode requestedMode,
      Options requestedOptions) {
    if (environment == null || !isManagedLaunchCommand(command)) return false;
    Mode mode = requestedMode == null ? Mode.AUTO : requestedMode;
    Options options = requestedOptions == null ? Options.defaults() : requestedOptions;
    environment.put(GRAPHICS_MODE_ENVIRONMENT, mode.id());
    if (options.linuxGlxRecovery()) {
      environment.remove(DISABLE_GLX_RECOVERY_ENVIRONMENT);
    } else {
      environment.put(DISABLE_GLX_RECOVERY_ENVIRONMENT, "1");
    }
    return true;
  }
}
