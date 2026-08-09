package com.jvn.hub;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Persistence and Linux desktop discovery for Project Explorer icon themes. */
final class ProjectIconThemeSettings {
  static final int MIN_ICON_SIZE = 12;
  static final int MAX_ICON_SIZE = 28;

  private static final String SOURCE_KEY = "icons.source";
  private static final String THEME_KEY = "icons.theme";
  private static final String SIZE_KEY = "icons.size";
  private static final String FOLDER_VARIANTS_KEY = "icons.folderVariants";
  private static final String FILE_VARIANTS_KEY = "icons.fileTypeVariants";
  private static final String INHERIT_KEY = "icons.inheritTheme";
  private static final String BUNDLED_FALLBACK_KEY = "icons.bundledFallback";
  private static final String SMOOTH_SCALING_KEY = "icons.smoothScaling";
  private static final Pattern SIZE_COMPONENT = Pattern.compile("^(\\d+)(?:x\\d+)?(?:@\\d+)?$");
  private static final String DETECTED_DESKTOP_THEME = detectDesktopTheme();
  private static final List<Path> ICON_ROOTS = iconRoots();
  private static final Map<PreviewRequest, Optional<Path>> PREVIEW_CACHE = new ConcurrentHashMap<>();

  enum Source {
    DESKTOP("desktop", "Follow Linux Desktop", "Use the current GTK/freedesktop icon theme."),
    THEME("theme", "Installed Theme", "Lock JVN to a selected installed freedesktop theme."),
    BUNDLED(
        "jvn-defaults",
        "JVN Defaults (Bundled SVG)",
        "Use JVN's previous bundled Project Explorer SVG pack on every platform.");

    private final String id;
    private final String displayName;
    private final String description;

    Source(String id, String displayName, String description) {
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

    static Source parse(String value) {
      if (value == null || value.isBlank()) return DESKTOP;
      return switch (value.trim().toLowerCase(Locale.ROOT)) {
        case "theme", "installed", "custom" -> THEME;
        case "jvn-defaults", "default", "defaults", "bundled", "material", "svg", "jvn" -> BUNDLED;
        default -> DESKTOP;
      };
    }
  }

  record Options(
      Source source,
      String theme,
      int size,
      boolean folderVariants,
      boolean fileTypeVariants,
      boolean inheritTheme,
      boolean bundledFallback,
      boolean smoothScaling) {

    Options {
      source = source == null ? Source.DESKTOP : source;
      theme = theme == null ? "" : theme.trim();
      size = Math.max(MIN_ICON_SIZE, Math.min(MAX_ICON_SIZE, size));
    }

    static Options defaults() {
      return new Options(Source.DESKTOP, "", 18, true, true, true, true, true);
    }

    Options withSource(Source value) {
      return new Options(value, theme, size, folderVariants, fileTypeVariants,
          inheritTheme, bundledFallback, smoothScaling);
    }

    Options withTheme(String value) {
      return new Options(source, value, size, folderVariants, fileTypeVariants,
          inheritTheme, bundledFallback, smoothScaling);
    }

    Options withSize(int value) {
      return new Options(source, theme, value, folderVariants, fileTypeVariants,
          inheritTheme, bundledFallback, smoothScaling);
    }

    Options withFolderVariants(boolean value) {
      return new Options(source, theme, size, value, fileTypeVariants,
          inheritTheme, bundledFallback, smoothScaling);
    }

    Options withFileTypeVariants(boolean value) {
      return new Options(source, theme, size, folderVariants, value,
          inheritTheme, bundledFallback, smoothScaling);
    }

    Options withInheritTheme(boolean value) {
      return new Options(source, theme, size, folderVariants, fileTypeVariants,
          value, bundledFallback, smoothScaling);
    }

    Options withBundledFallback(boolean value) {
      return new Options(source, theme, size, folderVariants, fileTypeVariants,
          inheritTheme, value, smoothScaling);
    }

    Options withSmoothScaling(boolean value) {
      return new Options(source, theme, size, folderVariants, fileTypeVariants,
          inheritTheme, bundledFallback, value);
    }
  }

  private ProjectIconThemeSettings() {}

  static Path defaultFile() {
    return Path.of(
        System.getProperty("user.home", "."),
        ".jvn-editor",
        "project-icons.properties");
  }

  static Options load(Path file) {
    Options defaults = Options.defaults();
    if (file == null || !Files.isRegularFile(file)) return defaults;
    Properties properties = new Properties();
    try (InputStream input = Files.newInputStream(file)) {
      properties.load(input);
      return new Options(
          Source.parse(properties.getProperty(SOURCE_KEY)),
          properties.getProperty(THEME_KEY, ""),
          parseSize(properties.getProperty(SIZE_KEY), defaults.size()),
          readBoolean(properties, FOLDER_VARIANTS_KEY, defaults.folderVariants()),
          readBoolean(properties, FILE_VARIANTS_KEY, defaults.fileTypeVariants()),
          readBoolean(properties, INHERIT_KEY, defaults.inheritTheme()),
          readBoolean(properties, BUNDLED_FALLBACK_KEY, defaults.bundledFallback()),
          readBoolean(properties, SMOOTH_SCALING_KEY, defaults.smoothScaling()));
    } catch (IOException | IllegalArgumentException ignored) {
      return defaults;
    }
  }

  static void save(Path file, Options requested) throws IOException {
    if (file == null) throw new IOException("Project icon settings path is unavailable.");
    Options options = requested == null ? Options.defaults() : requested;
    Properties properties = new Properties();
    properties.setProperty(SOURCE_KEY, options.source().id());
    properties.setProperty(THEME_KEY, options.theme());
    properties.setProperty(SIZE_KEY, Integer.toString(options.size()));
    properties.setProperty(FOLDER_VARIANTS_KEY, Boolean.toString(options.folderVariants()));
    properties.setProperty(FILE_VARIANTS_KEY, Boolean.toString(options.fileTypeVariants()));
    properties.setProperty(INHERIT_KEY, Boolean.toString(options.inheritTheme()));
    properties.setProperty(BUNDLED_FALLBACK_KEY, Boolean.toString(options.bundledFallback()));
    properties.setProperty(SMOOTH_SCALING_KEY, Boolean.toString(options.smoothScaling()));
    writeProperties(file, properties);
  }

  static String detectedDesktopTheme() {
    return DETECTED_DESKTOP_THEME;
  }

  static String resolvedTheme(Options options) {
    Options safe = options == null ? Options.defaults() : options;
    return switch (safe.source()) {
      case BUNDLED -> "JVN Defaults";
      case THEME -> safe.theme().isBlank() ? DETECTED_DESKTOP_THEME : safe.theme();
      case DESKTOP -> DETECTED_DESKTOP_THEME;
    };
  }

  static String summary(Options options) {
    Options safe = options == null ? Options.defaults() : options;
    String theme = resolvedTheme(safe);
    if (safe.source() == Source.BUNDLED) return theme + " · " + safe.size() + " px";
    return safe.source().displayName() + " · " + theme + " · " + safe.size() + " px";
  }

  static List<String> installedThemes() {
    Set<String> themes = new LinkedHashSet<>();
    for (Path root : ICON_ROOTS) {
      try (var children = Files.list(root)) {
        children.filter(Files::isDirectory)
            .filter(directory -> Files.isRegularFile(directory.resolve("index.theme")))
            .map(directory -> directory.getFileName().toString())
            .forEach(themes::add);
      } catch (IOException ignored) {
        // An unreadable system icon root does not prevent other roots from being listed.
      }
    }
    return themes.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
  }

  static Optional<Path> previewIcon(Options options, List<String> names) {
    Options safe = options == null ? Options.defaults() : options;
    if (safe.source() == Source.BUNDLED || names == null || names.isEmpty()) return Optional.empty();
    String theme = resolvedTheme(safe);
    PreviewRequest request = new PreviewRequest(theme, safe.inheritTheme(), safe.size(), List.copyOf(names));
    return PREVIEW_CACHE.computeIfAbsent(request, ProjectIconThemeSettings::resolvePreviewIcon);
  }

  private static Optional<Path> resolvePreviewIcon(PreviewRequest request) {
    for (Path directory : themeChain(request.theme(), request.inheritTheme())) {
      for (String name : request.names()) {
        Optional<Path> closest = closestPng(directory, name, request.size());
        if (closest.isPresent()) return closest;
      }
    }
    return Optional.empty();
  }

  private static Optional<Path> closestPng(Path themeDirectory, String semanticName, int requestedSize) {
    if (!Files.isDirectory(themeDirectory) || semanticName == null || semanticName.isBlank()) {
      return Optional.empty();
    }
    String expected = semanticName.toLowerCase(Locale.ROOT) + ".png";
    try (var paths = Files.walk(themeDirectory, 5)) {
      return paths.filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).equals(expected))
          .min(Comparator
              .comparingInt((Path path) -> Math.abs(iconSize(path, requestedSize) - requestedSize))
              .thenComparing(Path::toString));
    } catch (IOException ignored) {
      return Optional.empty();
    }
  }

  private static List<Path> themeChain(String requestedTheme, boolean inherit) {
    LinkedHashSet<Path> directories = new LinkedHashSet<>();
    LinkedHashSet<String> visited = new LinkedHashSet<>();
    collectTheme(firstNonBlank(requestedTheme, "Adwaita"), inherit, directories, visited);
    if (inherit) {
      collectTheme("Adwaita", true, directories, visited);
      collectTheme("hicolor", true, directories, visited);
    }
    return List.copyOf(directories);
  }

  private static void collectTheme(
      String theme,
      boolean inherit,
      Set<Path> directories,
      Set<String> visited) {
    if (theme.isBlank() || !visited.add(theme.toLowerCase(Locale.ROOT))) return;
    List<Path> matches = ICON_ROOTS.stream()
        .map(root -> root.resolve(theme))
        .filter(Files::isDirectory)
        .toList();
    directories.addAll(matches);
    if (!inherit) return;
    for (Path directory : matches) {
      for (String inherited : inheritedThemes(directory.resolve("index.theme"))) {
        collectTheme(inherited, true, directories, visited);
      }
    }
  }

  private static List<String> inheritedThemes(Path indexFile) {
    if (!Files.isRegularFile(indexFile)) return List.of();
    try {
      for (String line : Files.readAllLines(indexFile, StandardCharsets.UTF_8)) {
        String trimmed = line.trim();
        if (!trimmed.regionMatches(true, 0, "Inherits=", 0, "Inherits=".length())) continue;
        return Arrays.stream(trimmed.substring("Inherits=".length()).split(","))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .toList();
      }
    } catch (IOException ignored) {
      // Missing inheritance metadata falls through to standard fallback themes.
    }
    return List.of();
  }

  private static int iconSize(Path path, int fallback) {
    for (Path part : path) {
      Matcher matcher = SIZE_COMPONENT.matcher(part.toString().toLowerCase(Locale.ROOT));
      if (!matcher.matches()) continue;
      try {
        return Integer.parseInt(matcher.group(1));
      } catch (NumberFormatException ignored) {
        return fallback;
      }
    }
    return fallback;
  }

  private static List<Path> iconRoots() {
    LinkedHashSet<Path> roots = new LinkedHashSet<>();
    boolean linux = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux");
    Path home = Paths.get(System.getProperty("user.home", "."));
    roots.add(home.resolve(".icons"));
    String dataHome = System.getenv("XDG_DATA_HOME");
    Path resolvedDataHome = dataHome == null || dataHome.isBlank()
        ? home.resolve(".local/share")
        : safePath(dataHome).orElse(home.resolve(".local/share"));
    roots.add(resolvedDataHome.resolve("icons"));
    String dataDirectories = System.getenv("XDG_DATA_DIRS");
    String resolved = dataDirectories == null || dataDirectories.isBlank()
        ? (linux ? "/usr/local/share:/usr/share" : "")
        : dataDirectories;
    for (String directory : splitDataDirectories(resolved)) {
      safePath(directory).ifPresent(path -> roots.add(path.resolve("icons")));
    }
    if (linux) roots.add(Paths.get("/usr/share/icons"));
    return roots.stream().filter(Files::isDirectory).toList();
  }

  static List<String> splitDataDirectories(String value) {
    if (value == null || value.isBlank()) return List.of();
    if (value.contains(";")) {
      return Arrays.stream(value.split(Pattern.quote(";")))
          .map(String::trim)
          .filter(part -> !part.isBlank())
          .toList();
    }
    if (value.contains(":")) {
      return Arrays.stream(value.split(Pattern.quote(":")))
          .map(String::trim)
          .filter(part -> !part.isBlank())
          .toList();
    }
    return List.of(value.trim());
  }

  private static Optional<Path> safePath(String value) {
    if (value == null || value.isBlank()) return Optional.empty();
    try {
      return Optional.of(Paths.get(value.trim()));
    } catch (InvalidPathException ignored) {
      return Optional.empty();
    }
  }

  private static String detectDesktopTheme() {
    String override = firstNonBlank(System.getProperty("jvn.icon.theme"), System.getenv("JVN_ICON_THEME"));
    if (!override.isBlank()) return unquote(override);

    Path home = Paths.get(System.getProperty("user.home", "."));
    for (Path settings : List.of(
        home.resolve(".config/gtk-4.0/settings.ini"),
        home.resolve(".config/gtk-3.0/settings.ini"))) {
      try {
        String theme = parseThemeSetting(Files.readString(settings, StandardCharsets.UTF_8));
        if (!theme.isBlank()) return theme;
      } catch (IOException ignored) {
        // Try the next standard desktop source.
      }
    }

    String gsettings = readGsettingsTheme();
    return gsettings.isBlank() ? "Adwaita" : gsettings;
  }

  static String parseThemeSetting(String content) {
    if (content == null || content.isBlank()) return "";
    for (String line : content.split("\\R")) {
      String trimmed = line.trim();
      int separator = trimmed.indexOf('=');
      if (separator <= 0) continue;
      if (!"gtk-icon-theme-name".equalsIgnoreCase(trimmed.substring(0, separator).trim())) continue;
      return unquote(trimmed.substring(separator + 1));
    }
    return "";
  }

  private static String readGsettingsTheme() {
    if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux")) return "";
    Process process = null;
    try {
      process = new ProcessBuilder("gsettings", "get", "org.gnome.desktop.interface", "icon-theme")
          .redirectErrorStream(true)
          .start();
      if (!process.waitFor(600, TimeUnit.MILLISECONDS)) {
        process.destroy();
        return "";
      }
      try (BufferedReader reader = new BufferedReader(
          new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
        return unquote(reader.readLine());
      }
    } catch (IOException ignored) {
      return "";
    } catch (InterruptedException ignored) {
      Thread.currentThread().interrupt();
      return "";
    } finally {
      if (process != null && process.isAlive()) process.destroy();
    }
  }

  private static int parseSize(String value, int fallback) {
    try {
      return Math.max(MIN_ICON_SIZE, Math.min(MAX_ICON_SIZE, Integer.parseInt(value)));
    } catch (NumberFormatException ignored) {
      return fallback;
    }
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

  private static void writeProperties(Path target, Properties properties) throws IOException {
    Path parent = target.toAbsolutePath().getParent();
    if (parent == null) throw new IOException("Project icon settings folder is unavailable.");
    Files.createDirectories(parent);
    Path temporary = Files.createTempFile(parent, "project-icons-", ".tmp");
    try {
      try (OutputStream output = Files.newOutputStream(temporary)) {
        properties.store(output, "JVN Project Explorer Icon Theme");
      }
      try {
        Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException ignored) {
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  private static String firstNonBlank(String first, String fallback) {
    if (first != null && !first.isBlank()) return first.trim();
    return fallback == null ? "" : fallback.trim();
  }

  private static String unquote(String value) {
    if (value == null) return "";
    String normalized = value.trim();
    if (normalized.length() >= 2) {
      char first = normalized.charAt(0);
      char last = normalized.charAt(normalized.length() - 1);
      if ((first == '\'' && last == '\'') || (first == '"' && last == '"')) {
        return normalized.substring(1, normalized.length() - 1).trim();
      }
    }
    return normalized;
  }

  private record PreviewRequest(String theme, boolean inheritTheme, int size, List<String> names) {}
}
